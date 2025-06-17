package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock{
    private String name;  // 业务名称，这样所有业务都可以使用，就是在使用的时候，指定其业务名称
    private static final String KEY_PREFIX = "distribution_lock:";

    private StringRedisTemplate stringRedisTemplate;

    // 推荐static，这样可以唯一标识一台JVM，在日志追踪或者debug的时候也好检查。没必要非static，实例范围
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        String key = KEY_PREFIX + name;
        // 线程标识
        String value = ID_PREFIX + Thread.currentThread().getId();
        // 获取锁
        Boolean isSuccess = stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(isSuccess);  // 涉及到auto unbox，如果isSuccess为null，也不至于出错，鲁棒性好
    }

    @Override
    public void unlock() {
        String target_id = ID_PREFIX + Thread.currentThread().getId();
        String key = KEY_PREFIX + name;
        String redis_id = stringRedisTemplate.opsForValue().get(key);
        if (target_id.equals(redis_id)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }
}
