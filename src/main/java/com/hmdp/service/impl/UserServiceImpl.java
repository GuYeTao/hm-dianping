package com.hmdp.service.impl;

import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
//        校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
//        不符合，返回错误信息
            return Result.fail("手机号格式错误");
        }
//        符合生成验证码
        String code = RandomUtil.randomNumbers(6);
//        保存验证码到session
        session.setAttribute("code", code);
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
        Object cachecode = session.getAttribute("code");
        String code = loginForm.getCode();
        if(cachecode==null||!cachecode.toString().equals(code)){
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
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        session.setAttribute("user", userDTO);
        return Result.ok();
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX+RandomUtil.randomString(10));
        user.setCreateTime(LocalDateTime.now());
        save(user);
        return user;
    }
}
