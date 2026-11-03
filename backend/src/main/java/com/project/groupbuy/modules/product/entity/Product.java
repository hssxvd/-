package com.project.groupbuy.modules.product.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品表（约束文档 4.5：禁止冗余 origin/shelf_life/tags，特色属性走 product_attribute）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product")
public class Product extends BaseEntity {

    private String productCode;
    private String productName;
    private Long categoryId;
    private Long merchantId;
    private Long communityId;
    private String mainImage;
    /** 图片 JSON 数组 */
    private String images;
    private String description;
    private String unit;
    /** DRAFT / ON_SHELF / OFF_SHELF / SOLD_OUT */
    private String status;
    private LocalDateTime deadlineTime;

    /** transient：列表接口填充的默认 SKU 价格（价格在 product_sku 表，不冗余到 product） */
    @TableField(exist = false)
    private BigDecimal price;
    @TableField(exist = false)
    private Long defaultSkuId;

    /** V1.11 transient：列表接口 JOIN merchant 表填充商家名（消费者首页/分类页展示"来自老张蔬果"）*/
    @TableField(exist = false)
    private String merchantName;

    /** V1.11 transient：列表接口 JOIN category 表填充分类名 */
    @TableField(exist = false)
    private String categoryName;

    /** transient：列表接口填充可用库存（约束文档 L1117：商品列表必须突出库存状态） */
    @TableField(exist = false)
    private Integer availableQty;

    /** transient：列表接口填充库存状态 EMPTY / WARN / NORMAL */
    @TableField(exist = false)
    private String warnStatus;
}
