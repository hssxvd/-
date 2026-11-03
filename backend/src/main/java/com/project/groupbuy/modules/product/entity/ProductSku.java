package com.project.groupbuy.modules.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import java.util.List;
import lombok.Data;
import com.baomidou.mybatisplus.annotation.TableField;
import java.util.List;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * SKU 表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku")
public class ProductSku extends BaseEntity {

    private Long productId;
    private String skuCode;
    private String skuName;
    private String spec;
    private BigDecimal price;
    private BigDecimal originalPrice;
    private BigDecimal costPrice;
    private String image;
    /** ACTIVE / DISABLED */
    private String status;

    /** 非持久化字段：商品详情页动态填充（按 communityId 聚合） */
    @TableField(exist = false)
    private Integer availableQty;

    @TableField(exist = false)
    private Integer warnQty;

    @TableField(exist = false)
    private List<ProductSkuAttribute> skuAttrs;
}
