package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    private final long BEGIN_TIMESTAMP = 1748563200;
    private final int SEQ_BIT = 32;  // 序列号位
    private StringRedisTemplate stringRedisTemplate;
    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    /**
     * 使用redis完成序列号的构建
     * @param keyPrefix 业务名作为前缀，以区分不同的变量
     * @return
     */
    public long nextId(String keyPrefix) {
        // 1. 调用该方法时的时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowEpochSecond - BEGIN_TIMESTAMP;
        // 2. 序列号
        // 2.1 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));  // 加':'，redis分级存储，方便统计年或月或日的订单量
        // 2.2 自增长
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);  // unbox时不会报空指针，因此如果该key不存在，redis会创建它，置为0，然后加1返回
        // 3. 拼接时间戳和序列号，采用位运算符
        return timestamp << SEQ_BIT | count;  // << 优先级高于 |
    }

    public static void main(String[] args) {
        // 获取2025.5.30的时间戳
        LocalDateTime time = LocalDateTime.of(2025, 5, 30, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);  // 1748563200
    }
}
