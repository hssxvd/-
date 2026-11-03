package com.project.groupbuy.modules.fulfillment.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 履约表（约束文档 5.4 履约状态机）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fulfillment")
public class Fulfillment extends BaseEntity {

    private Long orderId;
    private String orderNo;
    /** PICKUP / DELIVERY */
    private String fulfillmentType;
    private Long pickupPointId;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    /** PENDING_ASSIGN / PENDING_PICKUP / DELIVERING / DELIVERED / PICKED_UP / EXCEPTION */
    private String fulfillmentStatus;
    private LocalDateTime assignTime;
    private LocalDateTime pickupTime;
    private LocalDateTime deliveryTime;
    private LocalDateTime completeTime;
    private String exceptionReason;

    /** V5 核销员端 / 商家端用 —— 补订单快照字段（不从 fulfillment 表读）*/
    @TableField(exist = false)
    private String pickupCode;
    @TableField(exist = false)
    private List<Map<String, Object>> items;
    @TableField(exist = false)
    private Integer totalQty;
    @TableField(exist = false)
    private String orderStatus;
    /** V1.8 审计痕迹：最近一次操作人信息（从 fulfillment_record JOIN user）*/
    @TableField(exist = false)
    private String lastOperatorName;
    @TableField(exist = false)
    private String lastOperatorRole;
    @TableField(exist = false)
    private LocalDateTime lastOpTime;
}
