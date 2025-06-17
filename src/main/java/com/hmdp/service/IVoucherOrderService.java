package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result seckillVoucher_pessimistic_lock(Long voucherId);
    Result seckillVoucher_optimistic_lock(Long voucherId);
    Result seckillVoucher(Long voucherId);
    Result seckillVoucher_one_user_one_order(Long voucherId);

    Result createVoucherOrder_final(Long voucherId);

    Result seckillVoucher_one_user_one_order_syn_block_final(Long voucherId);

    Result seckillVoucher_redis_lock(Long voucherId);
}
