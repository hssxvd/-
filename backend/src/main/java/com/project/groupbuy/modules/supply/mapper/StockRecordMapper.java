package com.project.groupbuy.modules.supply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.modules.supply.dto.StockRecordQueryRequest;
import com.project.groupbuy.modules.supply.entity.StockRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * StockRecord Mapper（库存流水 · 约束文档 6 六变更类型）
 */
@Mapper
public interface StockRecordMapper extends BaseMapper<StockRecord> {

    /**
     * 商家端库存流水分页查询（约束文档 11：必须分页 + 走索引）
     * 数据隔离：stock_record → stock → product_sku → product.merchant_id（约束文档 8）
     * 下钻维度：stockId / skuId / communityId / changeType / orderNo / 日期区间
     */
    @Select("<script>SELECT r.id, r.stock_id, r.sku_id, r.community_id, r.change_type, r.change_qty, " +
            "r.before_total, r.after_total, r.before_locked, r.after_locked, " +
            "r.order_no, r.remark, r.operator_id, r.create_time, " +
            "sku.sku_code, sku.sku_name, sku.spec, sku.image AS sku_image, " +
            "p.product_name, p.main_image, com.community_name AS community_name " +
            "FROM stock_record r " +
            "JOIN stock s ON r.stock_id = s.id " +
            "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 " +
            "LEFT JOIN community com ON r.community_id = com.id " +
            "WHERE p.merchant_id = #{merchantId} " +
            "<if test='q.stockId != null'> AND r.stock_id = #{q.stockId} </if>" +
            "<if test='q.skuId != null'> AND r.sku_id = #{q.skuId} </if>" +
            "<if test='q.communityId != null'> AND r.community_id = #{q.communityId} </if>" +
            "<if test='q.changeType != null and q.changeType != \"\"'> AND r.change_type = #{q.changeType} </if>" +
            "<if test='q.orderNo != null and q.orderNo != \"\"'> AND r.order_no = #{q.orderNo} </if>" +
            "<if test='q.startDate != null'> AND r.create_time &gt;= #{q.startDate} </if>" +
            "<if test='q.endDate != null'> AND r.create_time &lt; DATE_ADD(#{q.endDate}, INTERVAL 1 DAY) </if>" +
            "ORDER BY r.create_time DESC, r.id DESC</script>")
    IPage<Map<String, Object>> pageRecordsForMerchant(Page<Map<String, Object>> page,
                                                      @Param("q") StockRecordQueryRequest q,
                                                      @Param("merchantId") Long merchantId);

    /**
     * 近 N 天出入库趋势聚合（约束文档 11：可视化数据必须由后端统计接口提供）
     *
     * 统计口径（文档化）：
     *   入库 in_qty  = SUM(change_qty) WHERE change_qty > 0
     *   出库 out_qty = SUM(-change_qty) WHERE change_qty < 0
     * 由 Service 层补齐无数据日期为 0，保证折线图 X 轴连续。
     */
    @Select("<script>SELECT DATE(r.create_time) AS stat_date, " +
            "COALESCE(SUM(CASE WHEN r.change_qty &gt; 0 THEN r.change_qty ELSE 0 END), 0) AS in_qty, " +
            "COALESCE(SUM(CASE WHEN r.change_qty &lt; 0 THEN -r.change_qty ELSE 0 END), 0) AS out_qty " +
            "FROM stock_record r " +
            "JOIN stock s ON r.stock_id = s.id " +
            "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 " +
            "WHERE p.merchant_id = #{merchantId} AND r.create_time &gt;= #{startTime} " +
            "<if test='communityId != null'> AND r.community_id = #{communityId} </if>" +
            "GROUP BY DATE(r.create_time) ORDER BY stat_date ASC</script>")
    List<Map<String, Object>> dailyFlowForMerchant(@Param("merchantId") Long merchantId,
                                                   @Param("communityId") Long communityId,
                                                   @Param("startTime") LocalDateTime startTime);

    // ==================== Admin 端（全商家视角） ====================

    /**
     * Admin 库存流水分页查询（全商家或指定商家）
     * 约束文档 11：必须分页；约束文档 8：Admin 可查全量
     */
    @Select("<script>SELECT r.id, r.stock_id, r.sku_id, r.community_id, r.change_type, r.change_qty, " +
            "r.before_total, r.after_total, r.before_locked, r.after_locked, " +
            "r.order_no, r.remark, r.operator_id, r.create_time, " +
            "sku.sku_code, sku.sku_name, sku.spec, sku.image AS sku_image, " +
            "p.product_name, p.main_image, p.merchant_id, " +
            "m.merchant_name, " +
            "com.community_name AS community_name " +
            "FROM stock_record r " +
            "JOIN stock s ON r.stock_id = s.id " +
            "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 " +
            "LEFT JOIN merchant m ON p.merchant_id = m.id AND m.is_deleted = 0 " +
            "LEFT JOIN community com ON r.community_id = com.id " +
            "<where>" +
            "<if test='merchantId != null'> AND p.merchant_id = #{merchantId} </if>" +
            "<if test='q.stockId != null'> AND r.stock_id = #{q.stockId} </if>" +
            "<if test='q.skuId != null'> AND r.sku_id = #{q.skuId} </if>" +
            "<if test='q.communityId != null'> AND r.community_id = #{q.communityId} </if>" +
            "<if test='q.changeType != null and q.changeType != \"\"'> AND r.change_type = #{q.changeType} </if>" +
            "<if test='q.orderNo != null and q.orderNo != \"\"'> AND r.order_no = #{q.orderNo} </if>" +
            "<if test='q.startDate != null'> AND r.create_time &gt;= #{q.startDate} </if>" +
            "<if test='q.endDate != null'> AND r.create_time &lt; DATE_ADD(#{q.endDate}, INTERVAL 1 DAY) </if>" +
            "</where>" +
            "ORDER BY r.create_time DESC, r.id DESC</script>")
    IPage<Map<String, Object>> pageRecordsForAdmin(Page<Map<String, Object>> page,
                                                    @Param("q") StockRecordQueryRequest q,
                                                    @Param("merchantId") Long merchantId);

    /**
     * Admin 近 N 天出入库趋势聚合（全商家或指定商家）
     * 口径与 dailyFlowForMerchant 完全一致
     */
    @Select("<script>SELECT DATE(r.create_time) AS stat_date, " +
            "COALESCE(SUM(CASE WHEN r.change_qty &gt; 0 THEN r.change_qty ELSE 0 END), 0) AS in_qty, " +
            "COALESCE(SUM(CASE WHEN r.change_qty &lt; 0 THEN -r.change_qty ELSE 0 END), 0) AS out_qty " +
            "FROM stock_record r " +
            "JOIN stock s ON r.stock_id = s.id " +
            "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 " +
            "<where>" +
            "r.create_time &gt;= #{startTime}" +
            "<if test='merchantId != null'> AND p.merchant_id = #{merchantId} </if>" +
            "<if test='communityId != null'> AND r.community_id = #{communityId} </if>" +
            "</where>" +
            "GROUP BY DATE(r.create_time) ORDER BY stat_date ASC</script>")
    List<Map<String, Object>> dailyFlowForAdmin(@Param("merchantId") Long merchantId,
                                                @Param("communityId") Long communityId,
                                                @Param("startTime") LocalDateTime startTime);
}
