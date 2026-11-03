package com.project.groupbuy.modules.favorite.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 商品收藏（消费者端）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("product_favorite")
public class ProductFavorite extends BaseEntity {

    private Long userId;
    private Long productId;
}
