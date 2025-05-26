package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.constant.MessageConstant;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
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

    /**
     * using redis as cache
     *
     * @param id
     * @return
     */
    @Override
    public Object queryById(Long id) {
        // 1、查询Redis
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // 使用JSON字符串保存对象
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 2、查询结果
        // 2.1 命中，直接返回result
        if (StrUtil.isNotBlank(shopJson)) {
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
        return null;
    }

    @Override
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
