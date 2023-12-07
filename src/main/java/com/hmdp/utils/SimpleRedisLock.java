package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{

    private String name;
    private StringRedisTemplate stringRedisTemplate;

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";

    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        String key = KEY_PREFIX+name;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, threadId, timeoutSec, TimeUnit.SECONDS);
//        避免空指针，不直接返回success
        return Boolean.TRUE.equals(success);
    }

    @Override
    public void unLock() {
//        获取线程标识
        String threadId = ID_PREFIX+Thread.currentThread().getId();
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX+name);
        if (threadId.equals(id)){
            stringRedisTemplate.delete(KEY_PREFIX+name);
        }
    }
}
