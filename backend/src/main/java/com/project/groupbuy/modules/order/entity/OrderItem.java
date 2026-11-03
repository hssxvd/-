package com.project.groupbuy.modules.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细表（约束文档 4.3 订单快照 + 特色属性快照）
 */
@Data
@TableName("order_item")
public class OrderItem implements Serializable {

    private Long id;
    private Long orderId;
    private String orderNo;
    private Long productId;
    private Long skuId;
    /** 商品名快照 */
    private String productName;
    /** SKU 名快照 */
    private String skuName;
    /** 规格快照 */
    private String spec;
    /** 图片快照 */
    private String image;
    private Long categoryId;
    /** 单价快照 */
    private BigDecimal price;
    private Integer quantity;
    private BigDecimal subtotal;
    /** 特色属性快照 JSON，如 [{"key":"产地","value":"崇明岛"}] */
    private String attrSnapshot;
    private Integer refundedQty;
    private BigDecimal refundedAmount;
    /** 非持久化：是否已评价（由 listMine 聚合填入） */
    @TableField(exist = false)
    private Boolean reviewed;
    private LocalDateTime createTime;
}
