package com.project.groupbuy.modules.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品特色属性表（约束文档 4.5）
 * 存商品公共特色属性（同 SKU 变体共享），如产地、品牌、保质期
 * SKU 固有属性（规格/颜色/尺寸差异维度）见 ProductSkuAttribute
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_attribute")
public class ProductAttribute extends BaseEntity {

    private Long productId;
    /** 属性键，预定义字典：产地/品牌/保质期/规格/重量/适用人群/特色标签 */
    private String attrKey;
    private String attrValue;
    private String attrUnit;
    private String iconUrl;
    /** 是否用于分类检索/筛选 */
    private Integer isFilterable;
    /** 是否在商品卡片/详情页高亮 */
    private Integer isHighlight;
    private Integer sortOrder;
}
