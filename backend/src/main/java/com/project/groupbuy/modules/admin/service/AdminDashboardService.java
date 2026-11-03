package com.project.groupbuy.modules.admin.service;

import com.project.groupbuy.modules.admin.mapper.AdminDashboardMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 管理员端全局概览服务
 *
 * 职责：调用 AdminDashboardMapper 的 8 组聚合 SQL，
 *      组装成一个完整的 overview Map 返回给前端单端点。
 *
 * 约束文档合规：
 *   §11 统计口径统一 —— 所有计算在此服务内完成，前端只做展示
 *   §8 数据隔离     —— 本服务面向 ADMIN 端，无商家身份过滤，跨商家聚合
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final AdminDashboardMapper mapper;

    /**
     * GET /admin/dashboard/overview — 全平台概览（单端点返回全部图表数据）
     *
     * @param days GMV 趋势天数，默认 30，范围 7/30/90
     * @param limit Top N 排行榜上限，默认 10
     */
    public Map<String, Object> overview(Integer days, Integer limit) {
        int d = (days != null && (days == 7 || days == 30 || days == 90)) ? days : 30;
        int l = (limit != null && limit > 0 && limit <= 50) ? limit : 10;

        Map<String, Object> result = new LinkedHashMap<>();
        long t0 = System.currentTimeMillis();

        // ① 核心指标卡
        Map<String, Object> metrics = mapper.metrics();
        result.put("metrics", metrics);

        // ② 订单状态分布
        result.put("orderStatus", enrichOrderStatus(mapper.orderStatus()));

        // ③ GMV 趋势
        result.put("gmvTrend", mapper.gmvTrend(d));

        // ④ 社区聚合
        result.put("communityStats", mapper.communityStats());

        // ⑤ Top 商家
        result.put("topMerchants", mapper.topMerchants(l));

        // ⑥ Top 商品
        result.put("topProducts", mapper.topProducts(l));

        // ⑦ 库存预警
        result.put("stockAlerts", mapper.stockAlerts());

        // ⑧ 近期售后
        result.put("recentAftersales", mapper.recentAftersales(5));

        // ⑨ 系统概况（版本 + 数据快照时间）
        Map<String, Object> sys = new LinkedHashMap<>();
        sys.put("version", "v1.17");
        sys.put("dbDate", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.put("system", sys);

        log.info("[AdminDashboard] overview 耗时 {}ms, days={}, limit={}",
                System.currentTimeMillis() - t0, d, l);
        return result;
    }

    /**
     * 把 DB 状态码转前端可读 label（约束 11 统一口径）
     */
    private List<Map<String, Object>> enrichOrderStatus(List<Map<String, Object>> raw) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("PENDING", "待支付");
        labels.put("PAID", "已支付待处理");
        labels.put("PROCESSING", "处理中");
        labels.put("PENDING_PICKUP", "待取货");
        labels.put("COMPLETED", "已完成");
        labels.put("CANCELLED", "已取消");
        labels.put("REFUNDED", "已退款");

        // 先放全量状态，无数据的给 0（饼图前端好画）
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map.Entry<String, String> e : labels.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("status", e.getKey());
            row.put("label", e.getValue());
            row.put("count", 0L);
            enriched.add(row);
        }
        for (Map<String, Object> r : raw) {
            String s = (String) r.get("order_status");
            enriched.stream().filter(m -> m.get("status").equals(s)).findFirst()
                    .ifPresent(m -> m.put("count", r.get("cnt")));
        }
        return enriched;
    }
}
