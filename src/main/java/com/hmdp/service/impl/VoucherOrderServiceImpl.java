package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1、查询秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 2、判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        // 3、判断秒杀是否结束
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        // 4、判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock <= 0) {
            return Result.fail("库存不足");
        }
        // 5、扣减库存
//        seckillVoucher.setStock(stock - 1);
        // 5.1、更新秒杀券信息
//        seckillVoucherService.save(seckillVoucher);
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1").eq("voucher_id", voucherId).update();
        if (!success) {
            return Result.fail("库存扣减失败");
        }
        // 6、创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);  // 订单id
        order.setVoucherId(voucherId);  // 秒杀券id
        Long userId = UserHolder.getUser().getId();
        order.setUserId(userId);  // 用户id
        // 保存订单
        save(order);
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public Result seckillVoucher_pessimistic_lock(Long voucherId) {
        // 1、查询秒杀券 (查询的时候就加行锁)
        // 生成的sql：SELECT voucher_id,stock,create_time,begin_time,end_time,update_time FROM tb_seckill_voucher
        // WHERE (voucher_id = ?) FOR UPDATE
        SeckillVoucher seckillVoucher = seckillVoucherService.query()
                .eq("voucher_id", voucherId)
                .last("FOR UPDATE")
                .one();
        // 2、判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        // 3、判断秒杀是否结束
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        // 4、判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock <= 0) {
            return Result.fail("库存不足");
        }
        // 5、扣减库存
        seckillVoucher.setStock(stock - 1);
        // 5.1、更新秒杀券信息
        boolean success = seckillVoucherService.updateById(seckillVoucher);
        if (!success) {
            return Result.fail("库存扣减失败");
        }
        // 6、创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);  // 订单id
        order.setVoucherId(voucherId);  // 秒杀券id
        Long userId = UserHolder.getUser().getId();
        order.setUserId(userId);  // 用户id
        // 保存订单
        save(order);
        return Result.ok(orderId);
    }
    @Override
    public Result seckillVoucher_optimistic_lock(Long voucherId) {
        // 1、查询秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        // 2、判断秒杀是否开始
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        if (beginTime.isAfter(LocalDateTime.now())) {
            return Result.fail("秒杀未开始");
        }
        // 3、判断秒杀是否结束
        LocalDateTime endTime = seckillVoucher.getEndTime();
        if (endTime.isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已结束");
        }
        // 4、判断库存是否充足
        Integer stock = seckillVoucher.getStock();
        if (stock <= 0) {
            return Result.fail("库存不足");
        }
        // 5、扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
//                .eq("stock", stock)  // CAS乐观锁
                .gt("stock", 0)  // 数据库判断
                .update();
        if (!success) {
            return Result.fail("库存扣减失败");
        }
        // 6、创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);  // 订单id
        order.setVoucherId(voucherId);  // 秒杀券id
        Long userId = UserHolder.getUser().getId();
        order.setUserId(userId);  // 用户id
        // 保存订单
        save(order);
        return Result.ok(orderId);
    }
}
