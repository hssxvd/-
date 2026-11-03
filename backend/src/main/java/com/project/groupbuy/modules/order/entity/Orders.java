package com.project.groupbuy.modules.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单表（约束文档 5.2 订单状态机）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("orders")
public class Orders extends BaseEntity {

    private String orderNo;
    private Long userId;
    private Long merchantId;
    private Long communityId;
    private Long pickupPointId;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal freightAmount;
    private BigDecimal payAmount;
    private BigDecimal refundedAmount;
    /** 订单状态：PENDING_CONFIRM / PENDING_PAY / PAID / FULFILLING / COMPLETED / CANCELLED / REFUNDING / REFUNDED */
    private String orderStatus;
    /** 支付状态：PENDING / SUCCESS / FAILED / TIMEOUT / REFUNDING / REFUNDED */
    private String payStatus;
    /** 履约状态：PENDING_ASSIGN / PENDING_PICKUP / DELIVERING / DELIVERED / PICKED_UP / EXCEPTION */
    private String fulfillmentStatus;
    /** 售后状态：NONE / APPLYING / APPROVED / REJECTED / REFUNDING / REFUNDED */
    private String aftersaleStatus;
    /** 自提核销码（自提订单支付成功后生成，约束 5.2 PENDING_PICKUP 配套）*/
    private String pickupCode;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String remark;
    private LocalDateTime expireTime;
    private LocalDateTime payTime;
    private LocalDateTime finishTime;
    private LocalDateTime cancelTime;
    private String cancelReason;
    /** 订单来源：NORMAL=普通购物车, GROUP_BUY=团购自动生成, SECKILL=秒杀 */
    private String source;
}
