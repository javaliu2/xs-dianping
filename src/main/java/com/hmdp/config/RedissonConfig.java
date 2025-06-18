package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.redisson.config.Config;

@Configuration
public class RedissonConfig {

    @Bean
    RedissonClient getRedissonClient() {
        // redisson config class
        Config config = new Config();
        // 配置单机服务，设置address和password
        config.useSingleServer().setAddress("redis://127.0.0.1:6379").setPassword("123321");
        // 返回client对象
        return Redisson.create(config);
    }
}
