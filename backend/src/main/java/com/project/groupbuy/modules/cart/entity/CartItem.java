package com.project.groupbuy.modules.cart.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 购物车表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("cart_item")
public class CartItem extends BaseEntity {

    private Long userId;
    private Long skuId;
    private Long productId;
    private Long communityId;
    private Integer quantity;
    /** 加入购物车时的价格快照 */
    private BigDecimal priceSnapshot;
    /** 是否选中，0/1 */
    private Integer selected;
}
