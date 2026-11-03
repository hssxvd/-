package com.project.groupbuy.modules.supply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.project.groupbuy.modules.supply.entity.SupplyPlan;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;

/**
 * SupplyPlan Mapper
 */
@Mapper
public interface SupplyPlanMapper extends BaseMapper<SupplyPlan> {

    /**
     * 同一商家 + SKU + 社区 + 截单日 是否已存在未取消的供应计划
     * 用于防止商家同日同一 SKU 重复创建计划（约束 6.1 业务去重）
     */
    @Select("SELECT COUNT(1) FROM supply_plan " +
            "WHERE merchant_id = #{merchantId} AND sku_id = #{skuId} " +
            "  AND community_id = #{communityId} AND plan_date = #{planDate} " +
            "  AND is_deleted = 0 AND status <> 'CANCELLED'")
    int countActiveBySkuCommunityDate(Long merchantId, Long skuId, Long communityId, LocalDate planDate);
}