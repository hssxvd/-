package com.project.groupbuy.modules.aftersale.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后表（约束文档 5.5 售后状态机 + 退款联动）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("aftersale")
public class Aftersale extends BaseEntity {

    private String aftersaleNo;
    private Long orderId;
    private String orderNo;
    private Long orderItemId;
    private Long userId;
    private Long merchantId;
    /** REFUND_ONLY / RETURN */
    private String aftersaleType;
    private String reason;
    private String description;
    private String images;
    private BigDecimal applyAmount;
    private BigDecimal approvedAmount;
    /** APPLYING / APPROVED / REJECTED / REFUNDING / REFUNDED */
    private String aftersaleStatus;
    private Long auditOperatorId;
    private LocalDateTime auditTime;
    private String auditRemark;
    private LocalDateTime refundTime;
    private String refundNo;
}
