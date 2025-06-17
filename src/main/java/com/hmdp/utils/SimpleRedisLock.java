package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;  // 业务名称，这样所有业务都可以使用，就是在使用的时候，指定其业务名称
    private static final String KEY_PREFIX = "distribution_lock:";

    private StringRedisTemplate stringRedisTemplate;
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String key = KEY_PREFIX + name;
        String value = "thread" + String.valueOf(Thread.currentThread().getId());
        // 获取锁
        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isSuccess);  // 涉及到auto unbox，如果isSuccess为null，也不至于出错，鲁棒性好
    }

    @Override
    public void unlock() {
        stringRedisTemplate.delete(KEY_PREFIX + name);
    }
}
