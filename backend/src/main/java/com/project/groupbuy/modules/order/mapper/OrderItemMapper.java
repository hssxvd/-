package com.project.groupbuy.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.project.groupbuy.modules.order.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * OrderItem Mapper
 */
@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
}
