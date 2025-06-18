package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.IVoucherService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/voucher-order")
@Slf4j
public class VoucherOrderController {

    @Autowired
    private IVoucherOrderService voucherOrderService;
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        log.info("秒杀券下单【controller】【begin】");
        // 1、使用数据库行级锁（悲观锁）解决并发造成的数据不一致问题
//        Result result = voucherOrderService.seckillVoucher_pessimistic_lock(voucherId);
        // 2、使用CAS（乐观锁）
//        Result result = voucherOrderService.seckillVoucher_optimistic_lock(voucherId);
        // 3、一人一单功能(synchronized锁)
//        Result result = voucherOrderService.seckillVoucher_one_user_one_order_syn_block_final(voucherId);
        // 4、分布式锁（redis实现）
//        Result result = voucherOrderService.seckillVoucher_redis_lock(voucherId);
        // 5、分布式锁（redisson提供）
        Result result = voucherOrderService.seckillVoucher_redisson(voucherId);
        log.info("秒杀券下单【controller】【end】");
        return result;
    }
}
