package com.project.groupbuy.modules.aftersale.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 售后列表行视图（v1.14 售后管理）
 *
 * 由 AftersaleMapper 一次 JOIN 带出订单 / 明细 / 用户 / 商家快照，
 * 避免前端为每行再发请求（约束 7.2.3 前后端字段必须对齐）。
 * 状态与类型的中文标签由 Service 从常量统一回填（约束 29 禁止前端硬编码）。
 */
@Data
public class AftersaleVO implements Serializable {

    private Long id;
    private String aftersaleNo;
    private Long orderId;
    private String orderNo;
    private Long orderItemId;
    private Long userId;
    /** 消费者昵称（JOIN user）*/
    private String userNickname;
    private Long merchantId;
    /** 商家名称（JOIN merchant，Admin 端展示）*/
    private String merchantName;

    private String aftersaleType;
    private String aftersaleTypeLabel;
    private String reason;
    private String description;
    private String images;

    private BigDecimal applyAmount;
    private BigDecimal approvedAmount;

    private String aftersaleStatus;
    private String aftersaleStatusLabel;

    private Long auditOperatorId;
    private LocalDateTime auditTime;
    private String auditRemark;
    private String refundNo;
    private LocalDateTime refundTime;
    private LocalDateTime createTime;

    // ---------- 订单快照 ----------
    private String orderStatus;
    private String orderStatusLabel;
    private String orderAftersaleStatus;
    private BigDecimal payAmount;
    private BigDecimal orderRefundedAmount;

    // ---------- 明细快照 ----------
    private String productName;
    private String skuName;
    private String spec;
    private String image;
    private Integer quantity;
    private BigDecimal subtotal;
    private Integer refundedQty;
    private BigDecimal itemRefundedAmount;
}
