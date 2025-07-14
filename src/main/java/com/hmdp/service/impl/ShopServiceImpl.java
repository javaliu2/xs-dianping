package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constant.MessageConstant;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
@Slf4j
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

    /**
     * 黑马实现
     */
    @Override
    public Object queryShopByType_hm(Integer typeId, Integer current, Double x, Double y, List<String> sortBy, Integer distance) {
        // 1、判断前端是否传递了x和y
        if (x == null || y == null) {
            // 1.1 没有传递，就是普通的分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return page.getRecords();
        }
        // 1.2 否则是带有地理坐标的查询
        // 2、使用redis命令，georadius key longitude latitude radius m|km|ft|mi WITHDIST COUNT ?
        // 2.1 需要自己实现计算分页的参数，分别是start和end
        // georadius不支持区间查询，故查询end条数据，跳过前start，以达到区间查询的目的
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        // 查询key为键的地理集合中，Circle确定的圆心为Point，距离小于Distance的地理entry
        // 返回结果包括距离信息，且只查询前end条记录
        double dist = distance;
        if (distance == 0) {
            dist = Double.MAX_VALUE;
        }
        log.info("dist: {}", dist);
        GeoResults<RedisGeoCommands.GeoLocation<String>> shopGeo = stringRedisTemplate.opsForGeo().radius(key, new Circle(new Point(x, y), new Distance(dist)), RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end));
        if (shopGeo == null) {
            return null;
        }
        // RedisGeoCommands.GeoLocation<String>就是(member, longitude, latitude)
        // GeoResult除了以上content，还包括distance
        // 存在多个符合条件的entry，故是List
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> contents = shopGeo.getContent();
        // 跳过start个元素，就没有数据了，故返回null
        if (contents.size() <= start) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        Map<Long, Double> distanceMap = new HashMap<>();
        contents.stream().skip(start).forEach(result -> {
            RedisGeoCommands.GeoLocation<String> content = result.getContent();
            Long shopId = Long.valueOf(content.getName());
            ids.add(shopId);  // 商铺id
            double value = result.getDistance().getValue();  // 与(x, y)的距离
            distanceMap.put(shopId, value);
        }
        );
        // 3、查询数据库获得以上商铺的全部信息
        String idStr = StrUtil.join(",", ids);
//        System.out.println(idStr);  // 2,9,8,3,6
        // 使用order by field(field_name)保证数据库查询出的结果和ids顺序一致
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
        // 3.1 填充shop的distance属性
        shops.forEach(shop -> shop.setDistance(distanceMap.get(shop.getId())));
        return shops;
    }

    @Override
    public Object queryShopByType(Integer typeId, Integer current, Double x, Double y, List<String> sortBy, Integer distance) {
        // 1、判断前端是否传递了x和y
        if (x == null || y == null) {
            // 1.1 没有传递，就是普通的分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return page.getRecords();
        }

        String key = RedisConstants.SHOP_GEO_KEY + typeId;
        double dist = distance;
        if (distance == 0) {
            dist = 50000;  // 50km, 查询所有数据
        }
        log.info("dist: {}", dist);
        GeoResults<RedisGeoCommands.GeoLocation<String>> shopGeo = stringRedisTemplate.opsForGeo().radius(key, new Circle(new Point(x, y), new Distance(dist)), RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance());
        if (shopGeo == null) {
            return null;
        }
        // RedisGeoCommands.GeoLocation<String>就是(member, longitude, latitude)
        // GeoResult除了以上content，还包括distance
        // 存在多个符合条件的entry，故是List
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> contents = shopGeo.getContent();

        List<Long> ids = new ArrayList<>();
        Map<Long, Double> distanceMap = new HashMap<>();
        contents.forEach(result -> {
                    RedisGeoCommands.GeoLocation<String> content = result.getContent();
                    Long shopId = Long.valueOf(content.getName());
                    ids.add(shopId);  // 商铺id
                    double value = result.getDistance().getValue();  // 与(x, y)的距离
                    distanceMap.put(shopId, value);
                }
        );
        // 3、查询数据库获得以上商铺的全部信息
        List<Shop> shops = query().in("id", ids).list();
        // 3.1 填充shop的distance属性
        shops.forEach(shop -> shop.setDistance(distanceMap.get(shop.getId())));
        for (Shop shop : shops) {
            System.out.println(shop);
        }
        // 查询所有的数据，然后截取[start, end)的列表数据
        int start = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        return shops;
        // 查询所有商铺数据，对商铺进行打分，然后排序，截取分页数据，返回前端。
        // 判断sortBy中条件的个数，如果是1个，那么权重就是1.0；如果是2个，那么权重就是0.7，0.3；如果是三个，那么就是0.5，0.3，0.2；
        // 对每一个字段进行归一化处理，以距离为例，distance_norm=(distance-distance_min)/(distance_max-dist_min+1e6)
        // 以三个字段为例，得分 = distance_norm*0.5 + score_norm*0.3 + comments*0.2
    }
}
