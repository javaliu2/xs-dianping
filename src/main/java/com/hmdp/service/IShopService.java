package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    Object queryById(Long id);

    Result updateShop(Shop shop);

    Object queryShopByType(Integer typeId, Integer current, Double x, Double y, List<String> sortBy, Integer distance);

    Object queryShopByType_hm(Integer typeId, Integer current, Double x, Double y, List<String> sortBy, Integer distance);
}
