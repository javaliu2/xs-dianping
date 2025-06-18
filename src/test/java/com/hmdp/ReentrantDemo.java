package com.hmdp;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.PostConstruct;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class ReentrantDemo {

    @Autowired
    @Qualifier("redissonClient")
    private RedissonClient redissonClient;

    @Autowired
    @Qualifier("redissonClient2")
    private RedissonClient redissonClient2;

    @Autowired
    @Qualifier("redissonClient3")
    private RedissonClient redissonClient3;
    private RLock lock = null;
    @PostConstruct
    public void init() {
        RLock lock1 = redissonClient.getLock("lock");
        RLock lock2 = redissonClient2.getLock("lock");
        RLock lock3 = redissonClient3.getLock("lock");
        // 创建联锁 multiLock
        this.lock = redissonClient.getMultiLock(lock1, lock2, lock3);
    }
    @Test
    void testReentrant() {
        boolean success = lock.tryLock();
        if (!success) {
            System.out.println("获取锁失败，1");
            return;
        }
        try{
            System.out.println("获取锁成功，1");
            method2();
        } finally {
            System.out.println("释放锁，1");
            lock.unlock();
        }
    }
    void method2() {
        boolean success = lock.tryLock();
        if (!success) {
            System.out.println("获取锁失败，2");
            return;
        }
        try{
            System.out.println("获取锁成功，2");
        } finally {
            System.out.println("释放锁，2");
            lock.unlock();
        }
    }

    @Test
    void testRetry() throws InterruptedException {
        lock = redissonClient.getLock("lock");
        lock.tryLock(1, 30, TimeUnit.SECONDS);
    }
}
