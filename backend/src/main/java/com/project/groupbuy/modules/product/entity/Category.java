package com.project.groupbuy.modules.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 分类表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("category")
public class Category extends BaseEntity {

    private Long parentId;
    private String categoryName;
    private String categoryCode;
    private Integer sortOrder;
    private String icon;
    /** ACTIVE / DISABLED */
    private String status;
}
