package com.project.groupbuy.modules.forecast.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.project.groupbuy.modules.forecast.entity.ForecastResult;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * ForecastResult Mapper —— 核心：从 orders/order_item 聚合历史销量
 * 约束 12.1：WHERE 严格卡有效订单（pay_status=SUCCESS 且 status NOT IN CANCELLED/REFUNDED）
 *            禁止把退款/取消订单算进训练数据（会造成负销量和预测过低）
 */
@Mapper
public interface ForecastResultMapper extends BaseMapper<ForecastResult> {

    /**
     * 按 (sku_id, community_id, DATE) 聚合日销量
     * 返回列：sku_id | community_id | dt | qty  | sku_name | product_name | category_id | merchant_id
     * dt 为 java.sql.Date → Java 侧映射为 java.time.LocalDate
     *
     * 时间范围由 Service 层传入，必须 ≤ forecast_date - 1（约束 12.2 禁止数据泄漏）
     */
    @Select("<script>" +
            "SELECT oi.sku_id AS sku_id, o.community_id AS community_id, " +
            "       DATE(o.create_time) AS dt, " +
            "       SUM(oi.quantity) AS qty, " +
            "       sku.sku_name AS sku_name, " +
            "       p.product_name AS product_name, " +
            "       p.category_id AS category_id, " +
            "       p.merchant_id AS merchant_id " +
            "FROM order_item oi " +
            "JOIN orders o ON oi.order_id = o.id " +
            "JOIN product_sku sku ON oi.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 AND p.status = 'ON_SHELF' " +
            "WHERE o.is_deleted = 0 " +
            "  AND oi.is_deleted = 0 " +
            "  AND o.pay_status = 'SUCCESS' " +
            "  AND o.order_status NOT IN ('CANCELLED', 'REFUNDED') " +
            "<if test='communityId != null'> AND o.community_id = #{communityId} </if>" +
            "<if test='categoryId != null'> AND p.category_id = #{categoryId} </if>" +
            "<if test='startDate != null'> AND DATE(o.create_time) &gt;= #{startDate} </if>" +
            "<if test='endDate != null'> AND DATE(o.create_time) &lt;= #{endDate} </if>" +
            "GROUP BY oi.sku_id, o.community_id, DATE(o.create_time) " +
            "ORDER BY oi.sku_id, o.community_id, dt" +
            "</script>")
    List<Map<String, Object>> aggregateDailySales(@Param("communityId") Long communityId,
                                                   @Param("categoryId") Long categoryId,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);

    /**
     * 取 ON_SHELF SKU 清单（去重，用于确定预测维度）
     * 只返回与 aggregateDailySales 同一个 JOIN 的结果，避免 SKU 不一致
     */
    @Select("<script>" +
            "SELECT DISTINCT sku.id AS sku_id, sku.sku_name AS sku_name, " +
            "       sku.price AS price, p.category_id AS category_id, " +
            "       p.merchant_id AS merchant_id, p.product_name AS product_name " +
            "FROM product_sku sku " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 AND p.status = 'ON_SHELF' " +
            "WHERE sku.is_deleted = 0 " +
            "<if test='categoryId != null'> AND p.category_id = #{categoryId} </if>" +
            "ORDER BY sku.id" +
            "</script>")
    List<Map<String, Object>> listForecastSkus(@Param("categoryId") Long categoryId);

    /**
     * 某 task_id 下已生成的 forecast_result 数量
     * 用于判断任务是否跑完（避免重复触发）
     */
    @Select("SELECT COUNT(1) FROM forecast_result WHERE task_id = #{taskId}")
    int countByTaskId(@Param("taskId") Long taskId);
}
