package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class HmDianPingApplicationTests {

    @Autowired
    private ShopServiceImpl shopService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testShop2Redis() throws InterruptedException {
        for (long i = 1; i <= 14; ++i) {
            shopService.saveShop2Redis(i, 10L);
        }
    }
    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    public void testRedisIdWorker() throws InterruptedException {
        final int thread_num = 300;
        CountDownLatch latch = new CountDownLatch(thread_num);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id: " + id);
            }
            latch.countDown();
        };

        long begin = System.currentTimeMillis();
        for (int i = 0; i < thread_num; i++) {
            es.submit(task);
        }
        latch.await();  // 主线程阻塞直到 thread_num 个异步线程全部执行完毕
        long end = System.currentTimeMillis();
        System.out.println("consume time:" + (end-begin));
    }

    @Test
    public void saveGEOInfoOfShop2Redis() {
        // question: StringRedisTemplate和RedisTemplate区别
        // 1、从数据库查询店铺数据
        List<Shop> shops = shopService.list();  // 查询所有，select * from tb_shop
        // 2、将数据按照typeId分组
        // My Implementation
//        Map<Long, List<Shop>> groups = new HashMap<>();
//        for (Shop shop : shops) {
//            Long typeId = shop.getTypeId();
//            if (groups.get(typeId) == null) {
//                List<Shop> arr = new ArrayList<>();
//                arr.add(shop);
//                groups.put(typeId, arr);
//            } else {
//                groups.get(typeId).add(shop);
//            }
//        }
//        System.out.println(groups);
        Map<Long, List<Shop>> collect = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 3、构造写入对象
        for (Map.Entry<Long, List<Shop>> coll : collect.entrySet()){
            Long typeId = coll.getKey();
            List<Shop> shopList = coll.getValue();
            String key = RedisConstants.SHOP_GEO_KEY + typeId;
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            for (Shop shop : shopList) {
                locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            // 4、按照typeId批量写入
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    /**
     * 向redis插入100万条用户id数据，检查其统计结果以及内存占用率
     * 统计结果：997593
     * 未插入之前，redis memory使用：915864 Bytes
     * 插入之后：930256 Bytes
     * 930256 - 915864 = 14.05 KBytes
     */
    @Test
    public void testHyperLogLog() {
        String[] users = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            users[j] = "user_" + i;
            if (j == 999) {
                j = 0;
                stringRedisTemplate.opsForHyperLogLog().add("hll1", users);
            }
        }
        Long size = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println(size);
    }
}
