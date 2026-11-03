package com.project.groupbuy.modules.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * SKU 固有属性表（约束文档 4.5）
 * 存 SKU 固有属性（定义 SKU 差异的维度），如颜色=红/蓝、尺寸=120ml/200ml
 * 商品公共特色属性见 ProductAttribute
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_sku_attribute")
public class ProductSkuAttribute extends BaseEntity {

    private Long skuId;
    /** 属性名，如 颜色/尺寸 */
    private String attrName;
    /** 属性值，如 红/120ml */
    private String attrValue;
}
