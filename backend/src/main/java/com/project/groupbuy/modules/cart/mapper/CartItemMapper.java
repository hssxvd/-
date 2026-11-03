package com.project.groupbuy.modules.cart.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.project.groupbuy.modules.cart.entity.CartItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * CartItem Mapper
 * 约束 28.19: clearSelected 必须硬删除，不能用 MyBatis-Plus delete()（会被 @TableLogic 转成软删除撞唯一索引）
 */
@Mapper
public interface CartItemMapper extends BaseMapper<CartItem> {

    /**
     * 硬删除选中项（绕过 MyBatis-Plus @TableLogic 软删除拦截）
     * 购物车清空是确定性操作，审计由 order_item 商品快照承担（约束 4.3）
     */
    @Delete("DELETE FROM cart_item WHERE user_id = #{userId} AND selected = 1 AND is_deleted = 0")
    int hardDeleteSelected(@Param("userId") Long userId);
}
