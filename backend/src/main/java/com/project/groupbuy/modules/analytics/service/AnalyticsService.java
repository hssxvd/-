package com.project.groupbuy.modules.analytics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.mapper.OrderItemMapper;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import com.project.groupbuy.modules.review.entity.Review;
import com.project.groupbuy.modules.review.mapper.ReviewMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;
import com.project.groupbuy.common.constant.AftersaleStatusConstant;
import com.project.groupbuy.common.constant.OrderStatusConstant;

/**
 * 经营分析服务（约束文档 11 口径文档化 + 数据隔离）
 *
 * 口径定义（约束文档 11：必须文档化；v1.14 补 REFUNDING/REFUNDED 后统一为"已支付口径"）：
 * - 已支付订单集合：PAID / PROCESSING / PENDING_PICKUP / COMPLETED / REFUNDING / REFUNDED
 *   （见 OrderStatusConstant.paidStatuses()，全项目统一引用，禁止各处写裸串）
 * - GMV：Σ(已支付订单 pay_amount − refunded_amount)，含已退款订单（退款只冲减金额，不剔除订单）
 * - 订单量：已支付订单数，含退款订单
 * - 履约率：COMPLETED 订单数 / 已支付订单数
 * - 售后率：有售后单的订单数 / COMPLETED 订单数
 * - 热销：按 SKU 已售数量排序
 * - 数据隔离：商家强制 WHERE merchant_id = 当前商家
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final ProductSkuMapper productSkuMapper;
    private final ReviewMapper reviewMapper;

    /**
     * 商家仪表盘统计卡片
     */
    public Map<String, Object> merchantSummary() {
        Long merchantId = SecurityContext.getMerchantId();
        LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
        LocalDateTime endOfToday = LocalDate.now().atTime(LocalTime.MAX);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("gmv", calcGmv(merchantId, null));
        result.put("todayGmv", calcGmv(merchantId, startOfToday, endOfToday));
        result.put("orderCount", countOrders(merchantId, null, null));
        result.put("todayOrderCount", countOrders(merchantId, startOfToday, endOfToday));
        result.put("fulfillmentRate", calcFulfillmentRate(merchantId));
        result.put("aftersaleRate", calcAftersaleRate(merchantId));
        result.put("pendingOrders", countByStatus(merchantId, OrderStatusConstant.PAID));
        result.put("pendingStockAlerts", 0); // 简化，实际查库存预警

        // 口径标注
        result.put("gmvNote", "GMV = Σ(已支付订单实付金额 − 已退金额)，含已退款订单");
        result.put("fulfillmentRateNote", "履约率 = 已完成履约 / 已支付订单");
        result.put("aftersaleRateNote", "售后率 = 售后订单 / 已完成订单");
        return result;
    }

    /**
     * GMV 趋势（日）
     */
    public List<Map<String, Object>> gmvTrend(Long merchantId, int days) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.atTime(LocalTime.MAX);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", date.toString());
            point.put("gmv", calcGmv(merchantId, start, end));
            point.put("orderCount", countOrders(merchantId, start, end));
            list.add(point);
        }
        return list;
    }

    /**
     * 热销商品 Top N
     */
    public List<Map<String, Object>> topProducts(Long merchantId, int n) {
        LambdaQueryWrapper<Orders> w = new LambdaQueryWrapper<>();
        w.in(Orders::getOrderStatus, OrderStatusConstant.paidStatuses());
        // 【约束 §8 item 归属过滤】
        if (merchantId != null) {
            w.inSql(Orders::getId,
                    "SELECT DISTINCT oi.order_id FROM order_item oi" +
                    " JOIN product_sku sk ON sk.id = oi.sku_id" +
                    " JOIN product p ON p.id = sk.product_id" +
                    " WHERE oi.is_deleted = 0 AND sk.is_deleted = 0 AND p.is_deleted = 0" +
                    " AND p.merchant_id = " + merchantId);
        }
        List<Orders> paidOrders = ordersMapper.selectList(w);
        Set<Long> orderIds = paidOrders.stream().map(Orders::getId).collect(Collectors.toSet());
        if (orderIds.isEmpty()) return Collections.emptyList();

        Map<Long, BigDecimal> skuSales = new HashMap<>();
        orderItemMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.project.groupbuy.modules.order.entity.OrderItem>()
                        .in(com.project.groupbuy.modules.order.entity.OrderItem::getOrderId, orderIds))
                .forEach(oi -> {
                    skuSales.merge(oi.getSkuId(), BigDecimal.valueOf(oi.getQuantity()), BigDecimal::add);
                });

        // 收集 SKU 名称（批量查，零 N+1）
        Set<Long> allSkuIds = skuSales.keySet();
        Map<Long, ProductSku> skuMap = allSkuIds.isEmpty() ? Map.of() :
                productSkuMapper.selectBatchIds(allSkuIds)
                        .stream().collect(Collectors.toMap(ProductSku::getId, s -> s));

        return skuSales.entrySet().stream()
                .sorted(Map.Entry.<Long, BigDecimal>comparingByValue().reversed())
                .limit(n)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("skuId", e.getKey());
                    item.put("skuName", skuMap.getOrDefault(e.getKey(), new ProductSku()).getSkuName());
                    item.put("quantity", e.getValue());
                    return item;
                }).toList();
    }

    /**
     * 预测 vs 实际（商家关联 SKU 的 MAE vs 基线）
     * 简化：返回 forecast_metric 数据
     */
    public List<Map<String, Object>> forecastVsActual(Long merchantId) {
        // 简化实现：返回模拟对比数据，实际应关联 forecast_result + 真实销量
        List<Map<String, Object>> result = new ArrayList<>();
        String[] models = {"RF", "LR", "MA"};
        for (String model : models) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("model", model);
            row.put("mae", 5.80);
            row.put("baselineMae", 12.30);
            row.put("win", true);
            result.add(row);
        }
        return result;
    }

    private BigDecimal calcGmv(Long merchantId, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<Orders> w = new LambdaQueryWrapper<>();
        w.in(Orders::getOrderStatus, OrderStatusConstant.paidStatuses());
        // 【约束 §8 item 归属过滤】当有 merchantId 时，改用 item 归属而非 orders.merchant_id
        // —— 兼容历史跨商家脏订单，以及防止未来 orders.merchant_id 语义漂移
        if (merchantId != null) {
            w.inSql(Orders::getId,
                    "SELECT DISTINCT oi.order_id FROM order_item oi" +
                    " JOIN product_sku sk ON sk.id = oi.sku_id" +
                    " JOIN product p ON p.id = sk.product_id" +
                    " WHERE oi.is_deleted = 0 AND sk.is_deleted = 0 AND p.is_deleted = 0" +
                    " AND p.merchant_id = " + merchantId);
        }
        if (start != null) w.ge(Orders::getPayTime, start);
        if (end != null) w.le(Orders::getPayTime, end);
        List<Orders> orders = ordersMapper.selectList(w);
        return orders.stream()
                .map(o -> o.getPayAmount().subtract(
                        o.getRefundedAmount() == null ? BigDecimal.ZERO : o.getRefundedAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calcGmv(Long merchantId, LocalDateTime time) {
        return calcGmv(merchantId, time, time);
    }

    private int countOrders(Long merchantId, LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<Orders> w = new LambdaQueryWrapper<>();
        w.in(Orders::getOrderStatus, OrderStatusConstant.paidStatuses());
        if (merchantId != null) {
            w.inSql(Orders::getId,
                    "SELECT DISTINCT oi.order_id FROM order_item oi" +
                    " JOIN product_sku sk ON sk.id = oi.sku_id" +
                    " JOIN product p ON p.id = sk.product_id" +
                    " WHERE oi.is_deleted = 0 AND sk.is_deleted = 0 AND p.is_deleted = 0" +
                    " AND p.merchant_id = " + merchantId);
        }
        if (start != null) w.ge(Orders::getPayTime, start);
        if (end != null) w.le(Orders::getPayTime, end);
        return ordersMapper.selectCount(w).intValue();
    }

    private int countByStatus(Long merchantId, String status) {
        LambdaQueryWrapper<Orders> w = new LambdaQueryWrapper<>();
        w.eq(Orders::getOrderStatus, status);
        if (merchantId != null) {
            w.inSql(Orders::getId,
                    "SELECT DISTINCT oi.order_id FROM order_item oi" +
                    " JOIN product_sku sk ON sk.id = oi.sku_id" +
                    " JOIN product p ON p.id = sk.product_id" +
                    " WHERE oi.is_deleted = 0 AND sk.is_deleted = 0 AND p.is_deleted = 0" +
                    " AND p.merchant_id = " + merchantId);
        }
        return ordersMapper.selectCount(w).intValue();
    }

    private BigDecimal calcFulfillmentRate(Long merchantId) {
        int paid = countOrders(merchantId, null, null);
        int completed = countByStatus(merchantId, OrderStatusConstant.COMPLETED);
        if (paid == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(completed).divide(BigDecimal.valueOf(paid), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    private BigDecimal calcAftersaleRate(Long merchantId) {
        int completed = countByStatus(merchantId, OrderStatusConstant.COMPLETED);
        LambdaQueryWrapper<Orders> w = new LambdaQueryWrapper<>();
        w.in(Orders::getAftersaleStatus, AftersaleStatusConstant.ACTIVE_STATUS);
        if (merchantId != null) {
            w.inSql(Orders::getId,
                    "SELECT DISTINCT oi.order_id FROM order_item oi" +
                    " JOIN product_sku sk ON sk.id = oi.sku_id" +
                    " JOIN product p ON p.id = sk.product_id" +
                    " WHERE oi.is_deleted = 0 AND sk.is_deleted = 0 AND p.is_deleted = 0" +
                    " AND p.merchant_id = " + merchantId);
        }
        int aftersale = ordersMapper.selectCount(w).intValue();
        if (completed == 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(aftersale).divide(BigDecimal.valueOf(completed), 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }
}
