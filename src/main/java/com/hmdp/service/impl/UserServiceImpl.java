package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
//        不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
//        符合生成验证码
        String code = RandomUtil.randomNumbers(6);
////        保存验证码到session
//        session.setAttribute("code", code);
//        保存验证码到redis，120s有效
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
//        发送验证码（模拟）
        log.info("发送短信验证码成功， 验证码：{}", code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        校验手机号
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
//        不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
//        校验验证码
//        Object cachecode = session.getAttribute("code");
        String cachecode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+phone);
        String code = loginForm.getCode();
        if(cachecode==null||!cachecode.equals(code)){
//        不一致，报错
            return Result.fail("验证码错误");
        }

//        一致，根据手机号查询用户 select * from tb_user where phone = ?
        User user = query().eq("phone", phone).one();
//        判断用户是否存在
        if(user==null){
//        不存在，注册
            user = createUserWithPhone(phone);
        }
//        存在
//        UserDTO userDTO = new UserDTO();
//        BeanUtils.copyProperties(user, userDTO);
//        session.setAttribute("user", userDTO);
//        保存在redis
//        随机生成token作为登录令牌
        String token = UUID.randomUUID().toString(true);
//        转为hash存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldname, fieldValue)->fieldValue.toString()));
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
//        设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);
//        返回token
        return Result.ok(token);
    }


    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        user.setCreateTime(LocalDateTime.now());
        save(user);
        return user;
    }


    @Override
    public Result sign() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        String key = USER_SIGN_KEY +userId+keySuffix;
        int dayOfMonth = now.getDayOfMonth();
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth-1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyy:MM"));
        String key = USER_SIGN_KEY +userId+keySuffix;
        int dayOfMonth = now.getDayOfMonth();

        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result==null||result.isEmpty()){
            return Result.ok(0);
        }
        Long num = result.get(0);
        if(num==null||num==0){
            return Result.ok(0);
        }
        int count = 0;
        while(true){
            if ((num&1)==0) {
                break;
            } else {
                count++;
            }
            num>>>=1;
        }

        return Result.ok(count);
    }
}
