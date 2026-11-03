package com.project.groupbuy.modules.supply.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.constant.StockWarnStatusConstant;
import com.project.groupbuy.modules.supply.dto.StockListQueryRequest;
import com.project.groupbuy.modules.supply.entity.Stock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 库存 Mapper（约束文档 6 条件更新防超卖 SQL）
 */
@Mapper
public interface StockMapper extends BaseMapper<Stock> {

    /**
     * 预警状态判定口径（约束文档 11：统计口径后端统一，前端禁止二次计算）
     * 取值引用 StockWarnStatusConstant，禁止在 SQL 里写裸字符串。
     * 注意：注解 SQL 走 XML 解析，比较符号必须转义（&lt; / &gt;）。
     * 列表 tag、预警 Tab 计数、总览统计卡三处共用本表达式，保证口径完全一致。
     */
    /**
     * 预警状态判定口径（约束文档 11：统计口径后端统一，前端禁止二次计算）
     * EMPTY 优先于 WARN：available_qty = 0 不管 warn_qty 多少都是空仓（先判）；
     *                   warn_qty IS NULL 也只能是空仓（没有阈值 + 没货 = 空仓）。
     * WARN 仅在 available > 0 且 <= warn_qty 时触发。
     * 取值引用 StockWarnStatusConstant，禁止在 SQL 里写裸字符串。
     * 列表 tag、预警 Tab 计数、总览统计卡三处共用本表达式，保证口径完全一致。
     */
    String WARN_STATUS_CASE =
            "CASE WHEN s.available_qty = 0 THEN '" + StockWarnStatusConstant.EMPTY + "' " +
            "WHEN s.warn_qty IS NOT NULL AND s.available_qty &lt;= s.warn_qty THEN '" + StockWarnStatusConstant.WARN + "' " +
            "ELSE '" + StockWarnStatusConstant.NORMAL + "' END";

    /**
     * 约束文档 §6 库存不变量：available = total - locked
     * 所有 UPDATE 必须同时维护三个字段，任一遗漏都会造成数据漂移。
     * 修复历史 bug：lockStock/unlockStock 原来只改 locked_qty，不改 available_qty。
     *
     * MySQL SET 子句用原始值计算，所以 delta 写法（available ± qty）比重算（total - locked）更直接。
     */
    @Update("UPDATE stock SET locked_qty = locked_qty + #{qty}, " +
            "available_qty = available_qty - #{qty}, version = version + 1 " +
            "WHERE id = #{id} AND available_qty >= #{qty} AND version = #{version}")
    int lockStock(@Param("id") Long id, @Param("qty") Integer qty, @Param("version") Integer version);

    @Update("UPDATE stock SET locked_qty = locked_qty - #{qty}, " +
            "available_qty = available_qty + #{qty}, version = version + 1 " +
            "WHERE id = #{id} AND locked_qty >= #{qty} AND version = #{version}")
    int unlockStock(@Param("id") Long id, @Param("qty") Integer qty, @Param("version") Integer version);

    /**
     * 预占转实扣：locked -= qty, total -= qty, available 不变（total - locked 差值抵消）
     * 用 available_qty = total_qty - locked_qty 重算保证不变量
     */
    @Update("UPDATE stock SET locked_qty = locked_qty - #{qty}, " +
            "total_qty = total_qty - #{qty}, " +
            "available_qty = GREATEST(total_qty - locked_qty, 0), " +
            "version = version + 1 " +
            "WHERE id = #{id} AND total_qty >= #{qty} AND locked_qty >= #{qty} AND version = #{version}")
    int deductStock(@Param("id") Long id, @Param("qty") Integer qty, @Param("version") Integer version);

    /**
     * 降级实扣（幂等兜底）：locked_qty < qty 时说明预占已被消耗过，
     * 只扣 total_qty，不再碰 locked_qty，保证账实对齐。
     * 触发场景：订单曾在其他路径走过 COMPLETED → 库存已扣，后状态回拉 → 再次核销。
     */
    @Update("UPDATE stock SET total_qty = total_qty - #{qty}, " +
            "available_qty = GREATEST(total_qty - locked_qty, 0), " +
            "version = version + 1 " +
            "WHERE id = #{id} AND total_qty >= #{qty} AND version = #{version}")
    int deductStockNet(@Param("id") Long id, @Param("qty") Integer qty, @Param("version") Integer version);

    /**
     * 供应到货入库：total += qty, available += qty, locked 不变
     */
    @Update("UPDATE stock SET total_qty = total_qty + #{qty}, " +
            "available_qty = available_qty + #{qty}, " +
            "version = version + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int supplyIn(@Param("id") Long id, @Param("qty") Integer qty, @Param("version") Integer version);

    /**
     * 人工调整（商家端）：total += qty, available += qty
     * 调整后用 GREATEST 重算 available 兜底（防止绕过 WHERE 约束）
     */
    @Update("UPDATE stock SET total_qty = total_qty + #{qty}, " +
            "available_qty = GREATEST(total_qty - locked_qty, 0), " +
            "version = version + 1 " +
            "WHERE id = #{id} AND version = #{version} " +
            "AND total_qty + #{qty} >= 0 " +
            "AND available_qty + #{qty} >= 0 " +
            "AND available_qty + #{qty} >= locked_qty")
    int inboundAdjust(@Param("id") Long id, @Param("qty") Integer qty, @Param("version") Integer version);

    /**
     * 售后回退：total += qty, available += qty, locked 不变
     */
    @Update("UPDATE stock SET total_qty = total_qty + #{qty}, " +
            "available_qty = available_qty + #{qty}, " +
            "version = version + 1 " +
            "WHERE id = #{id} AND version = #{version}")
    int rollback(@Param("id") Long id, @Param("qty") Integer qty, @Param("version") Integer version);

    /**
     * 商家端库存列表（分页 + 筛选）—— JOIN product_sku + product 做数据隔离
     * 约束文档 584：stock 表没有 merchant_id，必须三层 JOIN
     * 约束文档 11：列表接口必须分页，禁止 select * 后内存分页
     */
    @Select("<script>SELECT s.id, s.sku_id, s.community_id, s.pickup_point_id, " +
            "s.total_qty, s.locked_qty, s.available_qty, s.warn_qty, s.version, " +
            "s.create_time, s.update_time, " +
            "sku.sku_code, sku.sku_name, sku.spec, sku.price, sku.image AS sku_image, " +
            "p.product_name, p.main_image, p.category_id, p.merchant_id, " +
            "c.category_name AS category_name, com.community_name AS community_name, " +
            WARN_STATUS_CASE + " AS warn_status " +
            "FROM stock s " +
            "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 " +
            "LEFT JOIN category c ON p.category_id = c.id " +
            "LEFT JOIN community com ON s.community_id = com.id " +
            "WHERE p.merchant_id = #{merchantId} " +
            "<if test='q.communityId != null'> AND s.community_id = #{q.communityId} </if>" +
            "<if test='q.keyword != null and q.keyword != \"\"'> AND (p.product_name LIKE CONCAT('%', #{q.keyword}, '%') " +
            "  OR sku.sku_name LIKE CONCAT('%', #{q.keyword}, '%') " +
            "  OR sku.sku_code LIKE CONCAT('%', #{q.keyword}, '%')) </if>" +
            "<if test='q.warnStatus != null and q.warnStatus != \"\"'> AND (" + WARN_STATUS_CASE + ") = #{q.warnStatus} </if>" +
            "<if test='q.lockedOnly != null and q.lockedOnly'> AND s.locked_qty &gt; 0 </if>" +
            "ORDER BY s.available_qty ASC, s.id DESC</script>")
    IPage<Map<String, Object>> pageMineForMerchant(Page<Map<String, Object>> page,
                                                   @Param("q") StockListQueryRequest q,
                                                   @Param("merchantId") Long merchantId);

    /**
     * 商家端库存总览统计（约束文档 11：可视化数据必须由后端统计口径提供）
     * 口径：
     *   skuCount / totalQty / lockedQty / availableQty = 库存记录数与数量汇总
     *   warnCount  = warn_status = WARN  的记录数（与列表 warnStatus=WARN 筛选一致）
     *   emptyCount = warn_status = EMPTY 的记录数（与列表 warnStatus=EMPTY 筛选一致）
     *   lockedCount= locked_qty &gt; 0 的记录数（与列表 lockedOnly=true 筛选一致）
     */
    @Select("<script>SELECT COUNT(1) AS sku_count, " +
            "COALESCE(SUM(s.total_qty), 0) AS total_qty, " +
            "COALESCE(SUM(s.locked_qty), 0) AS locked_qty, " +
            "COALESCE(SUM(s.available_qty), 0) AS available_qty, " +
            "COALESCE(SUM(CASE WHEN (" + WARN_STATUS_CASE + ") = '" + StockWarnStatusConstant.WARN + "' THEN 1 ELSE 0 END), 0) AS warn_count, " +
            "COALESCE(SUM(CASE WHEN (" + WARN_STATUS_CASE + ") = '" + StockWarnStatusConstant.EMPTY + "' THEN 1 ELSE 0 END), 0) AS empty_count, " +
            "COALESCE(SUM(CASE WHEN s.locked_qty &gt; 0 THEN 1 ELSE 0 END), 0) AS locked_count " +
            "FROM stock s " +
            "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 " +
            "WHERE p.merchant_id = #{merchantId} " +
            "<if test='communityId != null'> AND s.community_id = #{communityId} </if>" +
            "</script>")
    Map<String, Object> overviewForMerchant(@Param("merchantId") Long merchantId,
                                            @Param("communityId") Long communityId);

    /**
     * 归属校验：某 stock 是否属于指定 merchant
     * 约束文档 584 数据隔离守卫
     */
    @Select("SELECT COUNT(1) FROM stock s " +
            "JOIN product_sku sku ON s.sku_id = sku.id " +
            "JOIN product p ON sku.product_id = p.id " +
            "WHERE s.id = #{stockId} AND p.merchant_id = #{merchantId} AND p.is_deleted = 0")
    Integer countStockByMerchant(@Param("stockId") Long stockId, @Param("merchantId") Long merchantId);

    /**
     * 商家端库存列表（不分页，内部统计/导出/建议用）
     * 约束文档 11：聚合口径不丢失商品/社区上下文
     */
    @Select("SELECT s.id, s.sku_id, s.community_id, s.total_qty, s.locked_qty, s.available_qty, s.warn_qty, " +
            "sku.sku_code, sku.sku_name, sku.spec, sku.image AS sku_image, " +
            "p.product_name, p.main_image, " +
            "com.community_name " +
            "FROM stock s " +
            "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 " +
            "LEFT JOIN community com ON s.community_id = com.id " +
            "WHERE p.merchant_id = #{merchantId} AND s.is_deleted = 0")
    List<Map<String, Object>> listMineForMerchant(@Param("merchantId") Long merchantId);

    // ==================== Admin 端（全商家视角） ====================

    /**
     * Admin 库存列表（分页 + 筛选，全商家或指定商家）
     * 约束文档 11：列表必须分页；预警口径后端统一计算
     * 与 pageMineForMerchant 共用 WARN_STATUS_CASE，保证口径一致
     */
    @Select("<script>SELECT s.id, s.sku_id, s.community_id, s.pickup_point_id, " +
            "s.total_qty, s.locked_qty, s.available_qty, s.warn_qty, s.version, " +
            "s.create_time, s.update_time, " +
            "sku.sku_code, sku.sku_name, sku.spec, sku.price, sku.image AS sku_image, " +
            "p.product_name, p.main_image, p.category_id, p.merchant_id, " +
            "m.merchant_name, " +
            "c.category_name AS category_name, com.community_name AS community_name, " +
            WARN_STATUS_CASE + " AS warn_status " +
            "FROM stock s " +
            "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 " +
            "LEFT JOIN merchant m ON p.merchant_id = m.id AND m.is_deleted = 0 " +
            "LEFT JOIN category c ON p.category_id = c.id " +
            "LEFT JOIN community com ON s.community_id = com.id " +
            "<where>" +
            "<if test='merchantId != null'> AND p.merchant_id = #{merchantId} </if>" +
            "<if test='q.communityId != null'> AND s.community_id = #{q.communityId} </if>" +
            "<if test='q.keyword != null and q.keyword != \"\"'> AND (p.product_name LIKE CONCAT('%', #{q.keyword}, '%') " +
            "  OR sku.sku_name LIKE CONCAT('%', #{q.keyword}, '%') " +
            "  OR sku.sku_code LIKE CONCAT('%', #{q.keyword}, '%')) </if>" +
            "<if test='q.warnStatus != null and q.warnStatus != \"\"'> AND (" + WARN_STATUS_CASE + ") = #{q.warnStatus} </if>" +
            "<if test='q.lockedOnly != null and q.lockedOnly'> AND s.locked_qty &gt; 0 </if>" +
            "</where>" +
            "ORDER BY s.available_qty ASC, s.id DESC</script>")
    IPage<Map<String, Object>> pageAllForAdmin(Page<Map<String, Object>> page,
                                               @Param("q") StockListQueryRequest q,
                                               @Param("merchantId") Long merchantId);

    /**
     * Admin 库存总览统计（全商家或指定商家）
     * 口径与 overviewForMerchant 完全一致，仅 WHERE 条件改为可选 merchantId
     */
    @Select("<script>SELECT COUNT(1) AS sku_count, " +
            "COALESCE(SUM(s.total_qty), 0) AS total_qty, " +
            "COALESCE(SUM(s.locked_qty), 0) AS locked_qty, " +
            "COALESCE(SUM(s.available_qty), 0) AS available_qty, " +
            "COALESCE(SUM(CASE WHEN (" + WARN_STATUS_CASE + ") = '" + StockWarnStatusConstant.WARN + "' THEN 1 ELSE 0 END), 0) AS warn_count, " +
            "COALESCE(SUM(CASE WHEN (" + WARN_STATUS_CASE + ") = '" + StockWarnStatusConstant.EMPTY + "' THEN 1 ELSE 0 END), 0) AS empty_count, " +
            "COALESCE(SUM(CASE WHEN s.locked_qty &gt; 0 THEN 1 ELSE 0 END), 0) AS locked_count " +
            "FROM stock s " +
            "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
            "JOIN product p ON sku.product_id = p.id AND p.is_deleted = 0 " +
            "<where>" +
            "<if test='merchantId != null'> AND p.merchant_id = #{merchantId} </if>" +
            "<if test='communityId != null'> AND s.community_id = #{communityId} </if>" +
            "</where></script>")
    Map<String, Object> overviewForAdmin(@Param("merchantId") Long merchantId,
                                          @Param("communityId") Long communityId);

    /**
     * 商家端库存列表 —— 按 Product × Community 归组视图（方案 B）
     *
     * 业务背景：约束文档 §4.4 规定库存粒度是 SKU×社区×自提点，但商家希望看到"同一商品 500g / 1kg
     * 两个规格堆在一起像重复"——这个方法在不改变存储粒度的前提下，用 GROUP BY 按 product 归组显示。
     *
     * 口径（v1.13 修：按 product 聚合跨社区）：
     *   - 每一行 = 一个 product（商品跨所有社区的汇总库存）
     *   - total_qty / locked_qty / available_qty = SUM(该 product 所有社区所有 SKU)
     *   - sku_count = 该 product 总 SKU 数；community_count = 跨几个社区
     *   - warn_status = 任一社区空仓 → EMPTY（取最严重级别）
     *   - sku_list = JSON：每个社区 × 每个 SKU 的明细（前端展开行用）
     *   - community_list = JSON：每个社区汇总（前端主行 tag 用）
     *
     * 约束文档合规：
     *   - 存储层仍严格 SKU 粒度，不改变 §4.4 唯一索引 uk_sku_community_pickup
     *   - §6 扣减/锁库 SQL 不动，只有展示层做了聚合
     */
    @Select("<script>" +
            "SELECT " +
            "  p.id AS product_id, " +
            "  p.product_name, " +
            "  p.main_image, " +
            "  p.merchant_id, " +
            "  p.category_id, " +
            "  c.category_name, " +
            "  SUM(s.total_qty)    AS total_qty, " +
            "  SUM(s.locked_qty)   AS locked_qty, " +
            "  SUM(s.available_qty) AS available_qty, " +
            "  COUNT(DISTINCT s.sku_id) AS sku_count, " +
            "  COUNT(DISTINCT s.community_id) AS community_count, " +
            "  (SELECT JSON_ARRAYAGG(JSON_OBJECT(" +
             "      'community_id', sub_s.community_id, " +
             "      'community_name', sub_com.community_name, " +
             "      'stock_id', sub_s.id, " +
             "      'sku_id',  sub_s.sku_id, " +
             "      'sku_name', sub_sku.sku_name, " +
             "      'sku_code', sub_sku.sku_code, " +
             "      'price',   sub_sku.price, " +
             "      'total_qty',    sub_s.total_qty, " +
             "      'locked_qty',   sub_s.locked_qty, " +
             "      'available_qty', sub_s.available_qty, " +
             "      'warn_qty',     sub_s.warn_qty, " +
             "      'warn_status', (" +
               "CASE WHEN sub_s.available_qty = 0 THEN '" + StockWarnStatusConstant.EMPTY + "' " +
               "WHEN sub_s.warn_qty IS NOT NULL AND sub_s.available_qty &lt;= sub_s.warn_qty THEN '" + StockWarnStatusConstant.WARN + "' " +
               "ELSE '" + StockWarnStatusConstant.NORMAL + "' END" +
             ") " +
             "  )) " +
              "   FROM stock sub_s " +
              "   JOIN product_sku sub_sku ON sub_s.sku_id = sub_sku.id AND sub_sku.is_deleted = 0 " +
              "   LEFT JOIN community sub_com ON sub_s.community_id = sub_com.id " +
              "   WHERE sub_sku.product_id = p.id " +
              "     AND sub_s.is_deleted = 0 " +
              "   <if test='q.communityId != null'> AND sub_s.community_id = #{q.communityId} </if>" +
              "   ORDER BY sub_s.community_id, sub_s.available_qty ASC" +
              "  ) AS sku_list, " +
             "  SUM(CASE WHEN (" + WARN_STATUS_CASE + ") = '" + StockWarnStatusConstant.EMPTY + "' THEN 1 ELSE 0 END) AS empty_sku_count, " +
             "  SUM(CASE WHEN (" + WARN_STATUS_CASE + ") = '" + StockWarnStatusConstant.WARN + "'  THEN 1 ELSE 0 END) AS warn_sku_count, " +
             "  COUNT(*) AS total_sku_count, " +
             "  CASE " +
             "    WHEN SUM(CASE WHEN (" + WARN_STATUS_CASE + ") = '" + StockWarnStatusConstant.EMPTY + "' THEN 1 ELSE 0 END) = COUNT(*) THEN '" + StockWarnStatusConstant.EMPTY + "' " +
             "    WHEN SUM(CASE WHEN (" + WARN_STATUS_CASE + ") = '" + StockWarnStatusConstant.EMPTY + "' THEN 1 ELSE 0 END) > 0 THEN '" + StockWarnStatusConstant.WARN + "' " +
             "    WHEN SUM(CASE WHEN (" + WARN_STATUS_CASE + ") = '" + StockWarnStatusConstant.WARN + "'  THEN 1 ELSE 0 END) > 0 THEN '" + StockWarnStatusConstant.WARN + "' " +
             "    ELSE '" + StockWarnStatusConstant.NORMAL + "' END AS warn_status " +
              "FROM stock s " +
             "JOIN product_sku sku ON s.sku_id = sku.id AND sku.is_deleted = 0 " +
             "JOIN product p       ON sku.product_id = p.id AND p.is_deleted = 0 " +
             "LEFT JOIN category c ON p.category_id = c.id " +
             "WHERE p.merchant_id = #{merchantId} " +
             "  AND s.is_deleted = 0 " +
             "<if test='q.communityId != null'> AND s.community_id = #{q.communityId} </if>" +
             "<if test='q.keyword != null and q.keyword != \"\"'> AND (p.product_name LIKE CONCAT('%', #{q.keyword}, '%') " +
             "  OR sku.sku_name LIKE CONCAT('%', #{q.keyword}, '%') " +
             "  OR sku.sku_code LIKE CONCAT('%', #{q.keyword}, '%')) </if>" +
             "GROUP BY p.id, p.product_name, p.main_image, p.merchant_id, p.category_id, " +
             "         c.category_name " +
             "ORDER BY available_qty ASC, p.id DESC" +
            "</script>")
    IPage<Map<String, Object>> pageMineGroupByProduct(Page<Map<String, Object>> page,
                                                      @Param("q") StockListQueryRequest q,
                                                      @Param("merchantId") Long merchantId);
}
