package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.constant.MessageConstant;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * using redis as cache
     *
     * @param id
     * @return
     */
    @Override
    public Object queryById(Long id) {
        // 1、解决 缓存穿透 问题的方案
//        return cachePenetrationSolution(id);
        // 2、解决 缓存击穿 问题的方案1，使用互斥锁
//        return cacheBreakdownSolutionWithMutexLock(id);
        // 3、解决 缓存击穿 问题的方案2，使用逻辑过期
        return cacheBreakdownSolutionWithLogicalExpire(id);
    }

    /**
     * 使用逻辑过期解决缓存击穿问题
     *
     * @param id
     * @return
     */
    private Object cacheBreakdownSolutionWithLogicalExpire(Long id) {
        // 1、查询缓存
        String shopKey = RedisConstants.CACHE_SHOP_KEY + id;
        String redisDataJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2、未命中，直接返回null，因为有一个假设，提前将热点数据加入缓存，如果查询不到，证明其不是热点数据
        if (StrUtil.isBlank(redisDataJson)) {
            return null;
        }
        // 3、命中，从json字符串中解析对象
        RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 4、判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 4.1、未过期，直接返回shop
            return shop;
        }
        // 4.2、已过期，进行缓存重建
        // 5、尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean getLock = tryLock(lockKey);
        // 5.1、获取锁失败，返回旧的数据
        if (!getLock) {
            return shop;
        }
        // 5.2、获取锁成功，开启独立线程进行缓存重建，另外返回的也是旧数据
        CACHE_REBUILD_EXECUTOR.submit(() -> {
            try {
                // 6、调用saveShop2Redis
                saveShop2Redis(id, 10L);
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                // 7、释放互斥锁
                unlock(lockKey);
            }
        });
        // 本线程也是，直接返回旧数据
        return shop;
    }

    /**
     * 缓存预热
     *
     * @param id
     * @param expireSeconds
     */
    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
//        Thread.sleep(200);
        // 1. 获取shop对象
        Shop shop = getById(id);
        // 2. 封装成RedisData对象
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入redis，没有设置过期时间，数据项会一直存在
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 使用互斥锁解决缓存击穿问题
     *
     * @param id
     * @return
     */
    private Object cacheBreakdownSolutionWithMutexLock(Long id) {
        // 1、查询Redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 使用JSON字符串保存对象
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、查询结果
        // 2.1 之前缓存的空对象
        if (RedisConstants.CACHE_NULL_KEY.equals(shopJson)) {
            return null;
        } else if (StrUtil.isNotBlank(shopJson)) {
            // 2.2 命中，直接返回result
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 2.2 未命中，顺序执行
        // 3、尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean getLock = tryLock(lockKey);
            if (!getLock) {
                Thread.sleep(400);  // 是业务操作时间的几倍，小于业务时间会造成不必要的内存消耗
                return cacheBreakdownSolutionWithMutexLock(id);
            }
            // 4、拿到互斥锁，查询数据库
            shop = getById(id);
            Thread.sleep(100);  // 模拟数据库操作延迟
            // 4.1 查询到结果
            if (shop != null) {
                // 缓存数据
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            } else {
                // 4.2 未查询到商户信息，表明该商户不存在，返回null
                // 补丁：采取缓存空对象的方式避免缓存穿透，设置较小的TTL，一方面避免内存溢出，另一方面最大地减少数据库和缓存的不一致性
                stringRedisTemplate.opsForValue().set(key, RedisConstants.CACHE_NULL_KEY, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            // 5、释放互斥锁
            unlock(lockKey);
        }
        return shop;
    }

    private boolean tryLock(String key) {
        // 设置过期时间，防止死锁
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 缓存空对象来解决缓存穿透问题
     *
     * @param id
     * @return
     */
    private Object cachePenetrationSolution(Long id) {
        // 1、查询Redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 使用JSON字符串保存对象
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、查询结果
        // 2.1 之前缓存的空对象
        if (RedisConstants.CACHE_NULL_KEY.equals(shopJson)) {
            return null;
        } else if (StrUtil.isNotBlank(shopJson)) {
            // 2.2 命中，直接返回result
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 2.2 未命中，顺序执行
        // 3、查询数据库
        Shop shop = getById(id);
        // 3.1 查询到result
        if (shop != null) {
            // 1）缓存数据
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
            // 2）返回数据
            return shop;
        }
        // 3.2 未查询到商户信息，表明该商户不存在，返回null
        // 补丁：采取缓存空对象的方式避免缓存穿透，设置较小的TTL，一方面避免内存溢出，另一方面最大地减少数据库和缓存的不一致性
        stringRedisTemplate.opsForValue().set(key, RedisConstants.CACHE_NULL_KEY, RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        return null;
    }

    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        // 写入数据库时，首先更新数据库，然后删除缓存
        Long id = shop.getId();
        if (id == null) {
            return Result.fail(MessageConstant.ERROR_SHOP_ID_IS_NULL);
        }
        // 1、更新数据库
        updateById(shop);
        // 2、删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
