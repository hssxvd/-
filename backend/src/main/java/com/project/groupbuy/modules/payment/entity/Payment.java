package com.project.groupbuy.modules.payment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 支付表（约束文档 5.3 支付状态机 + 幂等）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("payment")
public class Payment extends BaseEntity {

    private String paymentNo;
    private Long orderId;
    private String orderNo;
    private Long userId;
    private BigDecimal amount;
    /** MOCK / WECHAT / ALIPAY */
    private String payType;
    /** PENDING / SUCCESS / FAILED / TIMEOUT / REFUNDING / REFUNDED */
    private String payStatus;
    private LocalDateTime callbackTime;
    private BigDecimal refundAmount;
    private LocalDateTime refundTime;
}
