package com.project.groupbuy.modules.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单状态日志表（约束文档 5.2：每次状态变更必须写日志）
 */
@Data
@TableName("order_status_log")
public class OrderStatusLog implements Serializable {

    private Long id;
    private Long orderId;
    private String orderNo;
    private String fromStatus;
    private String toStatus;
    /** ORDER / PAY / FULFILLMENT / AFTERSALE */
    private String statusType;
    private Long operatorId;
    private String operatorRole;
    private String reason;
    private String traceId;
    private LocalDateTime createTime;
}
