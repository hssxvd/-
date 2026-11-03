package com.project.groupbuy.modules.payment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.project.groupbuy.modules.payment.entity.Payment;
import org.apache.ibatis.annotations.Mapper;

/**
 * Payment Mapper
 */
@Mapper
public interface PaymentMapper extends BaseMapper<Payment> {
}
