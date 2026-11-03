package com.project.groupbuy.modules.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.project.groupbuy.modules.order.entity.Orders;
import org.apache.ibatis.annotations.Mapper;

/**
 * Orders Mapper
 */
@Mapper
public interface OrdersMapper extends BaseMapper<Orders> {
}
