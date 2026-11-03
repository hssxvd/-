package com.project.groupbuy.modules.admin.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 管理员端全局概览 — 纯读聚合 SQL
 *
 * 全部 SQL 遵循约束文档 11：统计口径后端统一，前端禁止二次计算。
 * 所有查询加 is_deleted=0，LEFT JOIN 保证商家/商品被删除后订单仍可聚合。
 */
@Mapper
public interface AdminDashboardMapper {

    /** ① 核心指标卡（1 条 SQL 返回 9 个指标） */
    @Select("SELECT " +
            "  COUNT(*) AS orders_total, " +
            "  SUM(CASE WHEN DATE(o.create_time) = CURDATE() THEN 1 ELSE 0 END) AS orders_today, " +
            "  ROUND(SUM(CASE WHEN o.pay_status = 'SUCCESS' THEN o.pay_amount ELSE 0 END), 2) AS gmv_total, " +
            "  ROUND(SUM(CASE WHEN o.pay_status = 'SUCCESS' AND DATE(o.create_time) = CURDATE() THEN o.pay_amount ELSE 0 END), 2) AS gmv_today, " +
            "  (SELECT COUNT(*) FROM user WHERE is_deleted=0 AND user_type='CONSUMER') AS users, " +
            "  (SELECT COUNT(*) FROM merchant WHERE is_deleted=0) AS merchants, " +
            "  (SELECT COUNT(*) FROM product WHERE is_deleted=0) AS products, " +
            "  (SELECT COUNT(*) FROM community WHERE is_deleted=0) AS communities, " +
            "  (SELECT COUNT(*) FROM aftersale WHERE is_deleted=0 AND aftersale_status IN ('APPLYING','APPROVING')) AS aftersales_pending " +
            "FROM orders o WHERE o.is_deleted = 0")
    Map<String, Object> metrics();

    /** ② 订单状态分布（饼图） */
    @Select("SELECT order_status, COUNT(*) AS cnt FROM orders WHERE is_deleted = 0 GROUP BY order_status")
    List<Map<String, Object>> orderStatus();

    /** ③ GMV 趋势（折线图）— 近 N 天 */
    @Select("SELECT DATE(create_time) AS d, COUNT(*) AS orders, ROUND(SUM(pay_amount), 2) AS gmv " +
            "FROM orders WHERE is_deleted = 0 AND pay_status = 'SUCCESS' " +
            "AND create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE(create_time) ORDER BY d")
    List<Map<String, Object>> gmvTrend(@Param("days") int days);

    /** ④ 社区聚合（柱状图） */
    @Select("SELECT o.community_id, c.community_name, COUNT(*) AS orders, " +
            "ROUND(SUM(CASE WHEN o.pay_status='SUCCESS' THEN o.pay_amount ELSE 0 END), 2) AS gmv " +
            "FROM orders o LEFT JOIN community c ON o.community_id = c.id AND c.is_deleted = 0 " +
            "WHERE o.is_deleted = 0 GROUP BY o.community_id, c.community_name ORDER BY gmv DESC")
    List<Map<String, Object>> communityStats();

    /** ⑤ Top 商家 GMV（条形图） */
    @Select("SELECT p.merchant_id, m.merchant_name, COUNT(DISTINCT o.id) AS orders, " +
            "ROUND(SUM(CASE WHEN o.pay_status='SUCCESS' THEN o.pay_amount ELSE 0 END), 2) AS gmv " +
            "FROM orders o JOIN order_item oi ON oi.order_id = o.id AND oi.is_deleted = 0 " +
            "JOIN product p ON oi.product_id = p.id AND p.is_deleted = 0 " +
            "LEFT JOIN merchant m ON p.merchant_id = m.id AND m.is_deleted = 0 " +
            "WHERE o.is_deleted = 0 GROUP BY p.merchant_id, m.merchant_name " +
            "ORDER BY gmv DESC LIMIT #{limit}")
    List<Map<String, Object>> topMerchants(@Param("limit") int limit);

    /** ⑥ Top 热销商品（横条） */
    @Select("SELECT p.id AS product_id, p.product_name, " +
            "COUNT(DISTINCT oi.order_id) AS orders, SUM(oi.quantity) AS qty " +
            "FROM order_item oi JOIN product p ON oi.product_id = p.id AND p.is_deleted = 0 " +
            "WHERE oi.is_deleted = 0 " +
            "GROUP BY p.id, p.product_name ORDER BY qty DESC LIMIT #{limit}")
    List<Map<String, Object>> topProducts(@Param("limit") int limit);

    /** ⑦ 库存预警汇总（全局维度） */
    @Select("SELECT " +
            "  COUNT(*) AS total_stock_rows, " +
            "  SUM(CASE WHEN warn_qty IS NOT NULL AND available_qty <= warn_qty THEN 1 ELSE 0 END) AS warn_count, " +
            "  SUM(CASE WHEN warn_qty IS NULL AND available_qty = 0 THEN 1 ELSE 0 END) AS empty_count " +
            "FROM stock WHERE is_deleted = 0")
    Map<String, Object> stockAlerts();

    /** ⑧ 近期售后（右侧面板） */
    @Select("SELECT a.id AS aftersale_id, a.order_id, a.aftersale_type AS type, a.reason, a.aftersale_status AS status, a.create_time AS apply_time, " +
            "  o.order_no FROM aftersale a LEFT JOIN orders o ON a.order_id = o.id " +
            "WHERE a.is_deleted = 0 ORDER BY a.create_time DESC LIMIT #{limit}")
    List<Map<String, Object>> recentAftersales(@Param("limit") int limit);
}
