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
import org.springframework.aop.framework.AopContext;
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

    /**
     * 在方法上加 synchronized 关键字，这样的话，锁的范围是当前 ServiceImpl 对象。
     * 那么所有用户的并发请求都会被上锁，也就是串行，效率低下。且没有必要。
     *
     * @param voucherId
     * @return
     */
    @Override
    @Transactional
    public synchronized Result seckillVoucher_one_user_one_order(Long voucherId) {
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
        // 5、一人一单逻辑
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过一次");
        }
        // 6、扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // 数据库判断
                .update();
        if (!success) {
            return Result.fail("库存扣减失败");
        }
        // 7、创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);  // 订单id
        order.setVoucherId(voucherId);  // 秒杀券id
        order.setUserId(userId);  // 用户id
        // 保存订单
        save(order);
        return Result.ok(orderId);
    }

    @Transactional
    public Result seckillVoucher_one_user_one_order_syn_block(Long voucherId) {
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
        return createVoucherOrder(voucherId);
    }

    /**
     * 这种方式也存在问题，因为事务是由 Spring 管理的，退出锁之后，才进行事务的提交。
     * 如果事务还未提交，有新的请求进来。还是会存在一个用户多个订单现象的出现。
     *
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        // 5、一人一单逻辑
        Long userId = UserHolder.getUser().getId();
        // 锁的是值，而不是对象引用。userId.toString()虽然字符串值一样，但是引用不一样。调用intern返回字符串常量池中的唯一引用
        synchronized (userId.toString().intern()) {
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
            if (count > 0) {
                return Result.fail("用户已经购买过一次");
            }
            // 6、扣减库存
            boolean success = seckillVoucherService.update()
                    .setSql("stock = stock - 1")
                    .eq("voucher_id", voucherId)
                    .gt("stock", 0)  // 数据库判断
                    .update();
            if (!success) {
                return Result.fail("库存扣减失败");
            }
            // 7、创建订单
            VoucherOrder order = new VoucherOrder();
            long orderId = redisIdWorker.nextId("order");
            order.setId(orderId);  // 订单id
            order.setVoucherId(voucherId);  // 秒杀券id
            order.setUserId(userId);  // 用户id
            // 保存订单
            save(order);
            return Result.ok(orderId);
        }
    }

    @Transactional
    public Result createVoucherOrder_final(Long voucherId) {
        // 5、一人一单逻辑
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过一次");
        }
        // 6、扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)  // 数据库判断
                .update();
        if (!success) {
            return Result.fail("库存扣减失败");
        }
        // 7、创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        order.setId(orderId);  // 订单id
        order.setVoucherId(voucherId);  // 秒杀券id
        order.setUserId(userId);  // 用户id
        // 保存订单
        save(order);
        return Result.ok(orderId);
    }

    @Override
    public Result seckillVoucher_one_user_one_order_syn_block_final(Long voucherId) {
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
        // 对带有transactional注解的方法加synchronized，确保事务执行完成之后再释放锁
        // 需要使用Spring事务的代理对象来调用方法
        Long userId = UserHolder.getUser().getId();
        synchronized (userId.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
            return proxy.createVoucherOrder_final(voucherId);
        }
    }
}
