package com.project.groupbuy.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.groupbuy.common.util.OpLogTranslator;
import com.project.groupbuy.modules.admin.vo.OperationLogVO;
import com.project.groupbuy.modules.aftersale.entity.Aftersale;
import com.project.groupbuy.modules.aftersale.mapper.AftersaleMapper;
import com.project.groupbuy.modules.auth.entity.OperationLog;
import com.project.groupbuy.modules.auth.entity.User;
import com.project.groupbuy.modules.auth.mapper.OperationLogMapper;
import com.project.groupbuy.modules.auth.mapper.UserMapper;
import com.project.groupbuy.modules.order.entity.Orders;
import com.project.groupbuy.modules.order.mapper.OrdersMapper;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.entity.SupplyPlan;
import com.project.groupbuy.modules.supply.mapper.StockMapper;
import com.project.groupbuy.modules.supply.mapper.SupplyPlanMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 管理员端 · 操作日志查询
 * 约束文档 10：敏感操作自动写 operation_log，管理员可分页审计
 * 约束文档 2.1：DTO 对外、Entity 不直出（返回 OperationLogVO）
 *
 * 2026-10-10 新增 TargetEnrich 层：从 request_params 反解业务 ID → 批量查业务表 →
 * 补全 "某对象" / "供应计划" 为具体可读字段（商家名、SKU 规格、数量、状态等）
 */
@Slf4j
@Service
public class AdminLogService {

    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;

    // ---- enrichment 所需 ----
    private final SupplyPlanMapper supplyPlanMapper;
    private final OrdersMapper ordersMapper;
    private final StockMapper stockMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final AftersaleMapper aftersaleMapper;
    private final MerchantMapper merchantMapper;
    private final ObjectMapper objectMapper;

    public AdminLogService(OperationLogMapper operationLogMapper,
                           UserMapper userMapper,
                           SupplyPlanMapper supplyPlanMapper,
                           OrdersMapper ordersMapper,
                           StockMapper stockMapper,
                           ProductMapper productMapper,
                           ProductSkuMapper productSkuMapper,
                           AftersaleMapper aftersaleMapper,
                           MerchantMapper merchantMapper,
                           ObjectMapper objectMapper) {
        this.operationLogMapper = operationLogMapper;
        this.userMapper = userMapper;
        this.supplyPlanMapper = supplyPlanMapper;
        this.ordersMapper = ordersMapper;
        this.stockMapper = stockMapper;
        this.productMapper = productMapper;
        this.productSkuMapper = productSkuMapper;
        this.aftersaleMapper = aftersaleMapper;
        this.merchantMapper = merchantMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 分页查询（翻译 + 补全操作人 + 补全目标详情）
     */
    public IPage<OperationLogVO> page(int pageNum, int pageSize,
                                      String module, String operationType, String result,
                                      Long userId, String targetType, Long targetId,
                                      String keyword,
                                      LocalDateTime startTime, LocalDateTime endTime) {
        Page<OperationLog> page = new Page<>(Math.max(1, pageNum), Math.min(100, pageSize));

        LambdaQueryWrapper<OperationLog> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(module))        w.eq(OperationLog::getModule, module);
        if (StringUtils.hasText(operationType)) w.eq(OperationLog::getOperationType, operationType);
        if (StringUtils.hasText(result))       w.eq(OperationLog::getResult, result);
        if (userId != null)                    w.eq(OperationLog::getUserId, userId);
        if (StringUtils.hasText(targetType))   w.eq(OperationLog::getTargetType, targetType);
        if (targetId != null)                  w.eq(OperationLog::getTargetId, targetId);
        if (startTime != null)                 w.ge(OperationLog::getCreateTime, startTime);
        if (endTime != null)                   w.le(OperationLog::getCreateTime, endTime);
        if (StringUtils.hasText(keyword)) {
            w.and(q -> q.like(OperationLog::getRequestParams, keyword)
                    .or().like(OperationLog::getErrorMsg, keyword)
                    .or().like(OperationLog::getTraceId, keyword)
                    .or().like(OperationLog::getTargetType, keyword));
        }
        w.orderByDesc(OperationLog::getCreateTime);

        IPage<OperationLog> raw = operationLogMapper.selectPage(page, w);

        // 批量查 user 姓名
        Set<Long> userIds = raw.getRecords().stream()
                .map(OperationLog::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> userIdToName = userIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername(),
                                (a, b) -> a));

        // ★ enrichment：批量查业务实体 → Map<logId, TargetEnrichInfo>
        Map<Long, TargetEnrichInfo> enrichMap = enrichTargets(raw.getRecords());

        // 翻译 + enrich
        List<OperationLogVO> voRecords = raw.getRecords().stream()
                .map(log -> {
                    OperationLogVO vo = OpLogTranslator.translate(log, userIdToName);
                    TargetEnrichInfo enrich = enrichMap.get(log.getId());
                    if (enrich != null) {
                        applyEnrich(vo, enrich);
                    }
                    return vo;
                }).collect(Collectors.toList());

        Page<OperationLogVO> voPage = new Page<>();
        voPage.setCurrent(raw.getCurrent());
        voPage.setSize(raw.getSize());
        Long t = raw.getTotal();
        voPage.setTotal(t != null ? t : 0L);
        voPage.setRecords(voRecords);
        return voPage;
    }

    public OperationLog getRawById(Long id) {
        OperationLog log = operationLogMapper.selectById(id);
        if (log == null) throw new RuntimeException("日志不存在: " + id);
        return log;
    }

    public OperationLogVO getTranslatedById(Long id) {
        OperationLog log = getRawById(id);
        Map<Long, String> userIdToName = Collections.emptyMap();
        if (log.getUserId() != null) {
            User u = userMapper.selectById(log.getUserId());
            if (u != null) {
                String name = StringUtils.hasText(u.getNickname()) ? u.getNickname() : u.getUsername();
                userIdToName = Map.of(log.getUserId(), name);
            }
        }
        OperationLogVO vo = OpLogTranslator.translate(log, userIdToName);
        Map<Long, TargetEnrichInfo> enrichMap = enrichTargets(List.of(log));
        TargetEnrichInfo enrich = enrichMap.get(log.getId());
        if (enrich != null) applyEnrich(vo, enrich);
        return vo;
    }

    public List<String> listDistinctModules() {
        return operationLogMapper.selectList(
                new LambdaQueryWrapper<OperationLog>()
                        .select(OperationLog::getModule)
                        .isNotNull(OperationLog::getModule)
                        .groupBy(OperationLog::getModule)
        ).stream().map(OperationLog::getModule).distinct().toList();
    }

    public Map<String, Object> statsToday() {
        LocalDateTime start = LocalDateTime.now().minusDays(7);
        LocalDateTime end = LocalDateTime.now();
        LambdaQueryWrapper<OperationLog> range = new LambdaQueryWrapper<>();
        range.ge(OperationLog::getCreateTime, start).le(OperationLog::getCreateTime, end);
        List<OperationLog> all = operationLogMapper.selectList(range);

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("periodDays", 7);
        r.put("total", (long) all.size());
        r.put("failedCount", all.stream().filter(l -> "FAIL".equals(l.getResult())).count());
        r.put("uniqueUsers", all.stream().map(OperationLog::getUserId).filter(Objects::nonNull).distinct().count());
        r.put("auditCount", all.stream().filter(l -> "AUDIT".equals(l.getOperationType())).count());
        r.put("transitionCount", all.stream().filter(l -> "TRANSITION".equals(l.getOperationType())).count());
        return r;
    }

    // ============ enrichment 内部 ============

    /**
     * 从 request_params / target_id 提取业务主键 → 按 target_type 批量查业务表 → 拼可读描述
     */
    private Map<Long, TargetEnrichInfo> enrichTargets(List<OperationLog> logs) {
        // 1. 按 targetType 分组 + 提取可能的业务 ID
        Map<String, List<OperationLog>> byType = new LinkedHashMap<>();
        for (OperationLog log : logs) {
            String tt = log.getTargetType();
            if (!StringUtils.hasText(tt)) continue;
            byType.computeIfAbsent(tt, k -> new ArrayList<>()).add(log);
        }

        // 2. 收集所有可能 ID（log.targetId 优先；否则从 request_params 解析）
        //    输出 Map<Long /*logId*/, Pair<String /*targetType*/, Long /*bizId*/>>
        Map<Long, BizIdEntry> logToBizId = new LinkedHashMap<>();
        for (Map.Entry<String, List<OperationLog>> e : byType.entrySet()) {
            String targetType = e.getKey();
            for (OperationLog log : e.getValue()) {
                Long bizId = log.getTargetId();
                if (bizId == null) bizId = extractFirstId(log.getRequestParams());
                if (bizId != null) {
                    logToBizId.put(log.getId(), new BizIdEntry(targetType, bizId));
                }
            }
        }

        if (logToBizId.isEmpty()) return Collections.emptyMap();

        // 3. 按 target_type 分批 selectBatchIds
        Map<String, Set<Long>> idsByType = new LinkedHashMap<>();
        for (BizIdEntry e : logToBizId.values()) {
            idsByType.computeIfAbsent(e.getTargetType(), k -> new HashSet<>()).add(e.getBizId());
        }

        // 4. 查 + 组装
        Map<String, Map<Long, String>> nameCache = new HashMap<>(); // targetType.bizId -> 可读名称
        for (Map.Entry<String, Set<Long>> e : idsByType.entrySet()) {
            String tt = e.getKey();
            Set<Long> ids = e.getValue();
            if (ids.isEmpty()) continue;
            switch (tt) {
                case "SupplyPlan" -> enrichSupplyPlan(ids, nameCache);
                case "Orders", "Order" -> enrichOrders(ids, nameCache);
                case "Stock" -> enrichStock(ids, nameCache);
                case "Product" -> enrichProduct(ids, nameCache);
                case "Aftersale" -> enrichAftersale(ids, nameCache);
                default -> { /* 未知类型跳过 */ }
            }
        }

        // 5. 回填 Map<logId, TargetEnrichInfo>
        Map<Long, TargetEnrichInfo> result = new LinkedHashMap<>();
        for (Map.Entry<Long, BizIdEntry> e : logToBizId.entrySet()) {
            Long logId = e.getKey();
            BizIdEntry entry = e.getValue();
            Map<Long, String> typeInner = nameCache.get(entry.getTargetType());
            if (typeInner == null) continue;
            String detail = typeInner.get(entry.getBizId());
            if (detail != null) {
                TargetEnrichInfo info = new TargetEnrichInfo();
                info.bizId = entry.getBizId();
                info.detail = detail;
                result.put(logId, info);
            }
        }
        return result;
    }

    /** 从 request_params 解析第一个可能的业务 ID（数字） */
    private static final Pattern FIRST_ID_PATTERN = Pattern.compile("(\\d+)");
    private Long extractFirstId(String requestParams) {
        if (!StringUtils.hasText(requestParams)) return null;
        // 先试 JSON 数组第一个元素 [123, null]
        try {
            JsonNode node = objectMapper.readTree(requestParams);
            if (node.isArray() && node.size() > 0 && node.get(0).isNumber()) {
                return node.get(0).asLong();
            }
            // JSON object 里的 id/targetId/skuId/orderId 等
            if (node.isObject()) {
                for (String key : new String[]{"id", "targetId", "target_id", "skuId", "sku_id",
                        "orderId", "order_id", "planId", "plan_id", "stockId", "stock_id"}) {
                    JsonNode v = node.get(key);
                    if (v != null && v.isNumber()) return v.asLong();
                }
            }
        } catch (Exception ignored) {}
        // 正则兜底
        Matcher m = FIRST_ID_PATTERN.matcher(requestParams);
        if (m.find()) {
            try { return Long.parseLong(m.group(1)); } catch (NumberFormatException ignored) {}
        }
        return null;
    }

    // ---- 各类 enrich ----

    private void enrichSupplyPlan(Set<Long> ids, Map<String, Map<Long, String>> cache) {
        List<SupplyPlan> plans = supplyPlanMapper.selectBatchIds(ids);
        if (plans.isEmpty()) return;

        // 批量查 merchant + sku
        Set<Long> merchantIds = plans.stream().map(SupplyPlan::getMerchantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> skuIds = plans.stream().map(SupplyPlan::getSkuId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> merchantMap = merchantIds.isEmpty() ? Collections.emptyMap()
                : merchantMapper.selectBatchIds(merchantIds).stream()
                        .collect(Collectors.toMap(Merchant::getId, Merchant::getMerchantName, (a, b) -> a));
        Map<Long, String> skuMap = skuIds.isEmpty() ? Collections.emptyMap()
                : productSkuMapper.selectBatchIds(skuIds).stream()
                        .collect(Collectors.toMap(ProductSku::getId, ProductSku::getSkuName, (a, b) -> a));

        Map<String, String> statusMap = Map.of(
                "DRAFT", "草稿", "CONFIRMED", "已确认", "ARRIVED", "已到货", "CANCELLED", "已取消"
        );

        Map<Long, String> inner = new HashMap<>();
        for (SupplyPlan p : plans) {
            StringBuilder sb = new StringBuilder();
            String merchant = merchantMap.getOrDefault(p.getMerchantId(), "商家#" + p.getMerchantId());
            String sku = skuMap.getOrDefault(p.getSkuId(), "SKU#" + p.getSkuId());
            String status = statusMap.getOrDefault(p.getStatus(), p.getStatus());
            sb.append("商家：").append(merchant)
              .append(" · SKU：").append(sku)
              .append(" · 计划量：").append(p.getPlanQty());
            if (status != null) sb.append(" · 状态：").append(status);
            inner.put(p.getId(), sb.toString());
        }
        cache.put("SupplyPlan", inner);
    }

    private void enrichOrders(Set<Long> ids, Map<String, Map<Long, String>> cache) {
        List<Orders> list = ordersMapper.selectBatchIds(ids);
        if (list.isEmpty()) return;
        Map<Long, String> merchantMap = list.stream().map(Orders::getMerchantId).filter(Objects::nonNull)
                .collect(Collectors.collectingAndThen(Collectors.toSet(), s ->
                        s.isEmpty() ? Collections.<Long, String>emptyMap()
                                : merchantMapper.selectBatchIds(s).stream()
                                        .collect(Collectors.toMap(Merchant::getId, Merchant::getMerchantName, (a, b) -> a))));

        Map<String, String> orderStatusMap = Map.of(
                "PENDING_PAY", "待支付", "PAID", "已支付", "PROCESSING", "处理中",
                "PENDING_PICKUP", "待自提", "COMPLETED", "已完成", "CANCELLED", "已取消",
                "SPLIT_SOURCE", "拆单源", "SPLIT_CHILD", "拆单子"
        );

        Map<Long, String> inner = new HashMap<>();
        for (Orders o : list) {
            StringBuilder sb = new StringBuilder();
            String merchant = merchantMap.getOrDefault(o.getMerchantId(), "");
            if (StringUtils.hasText(merchant)) sb.append("商家：").append(merchant).append(" · ");
            sb.append("订单号：").append(o.getOrderNo());
            sb.append(" · 实付：¥").append(o.getPayAmount());
            String st = orderStatusMap.getOrDefault(o.getOrderStatus(), o.getOrderStatus());
            if (st != null) sb.append(" · 状态：").append(st);
            inner.put(o.getId(), sb.toString());
        }
        cache.put("Orders", inner);
        cache.put("Order", inner); // 兼容 Order 别名
    }

    private void enrichStock(Set<Long> ids, Map<String, Map<Long, String>> cache) {
        List<Stock> list = stockMapper.selectBatchIds(ids);
        if (list.isEmpty()) return;
        Set<Long> skuIds = list.stream().map(Stock::getSkuId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> skuMap = skuIds.isEmpty() ? Collections.emptyMap()
                : productSkuMapper.selectBatchIds(skuIds).stream()
                        .collect(Collectors.toMap(ProductSku::getId, ProductSku::getSkuName, (a, b) -> a));

        Map<Long, String> inner = new HashMap<>();
        for (Stock s : list) {
            String sku = skuMap.getOrDefault(s.getSkuId(), "SKU#" + s.getSkuId());
            inner.put(s.getId(), "SKU：" + sku + " · 可用：" + s.getAvailableQty() + " / 总计：" + s.getTotalQty());
        }
        cache.put("Stock", inner);
    }

    private void enrichProduct(Set<Long> ids, Map<String, Map<Long, String>> cache) {
        List<Product> list = productMapper.selectBatchIds(ids);
        if (list.isEmpty()) return;
        Set<Long> merchantIds = list.stream().map(Product::getMerchantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> merchantMap = merchantIds.isEmpty() ? Collections.emptyMap()
                : merchantMapper.selectBatchIds(merchantIds).stream()
                        .collect(Collectors.toMap(Merchant::getId, Merchant::getMerchantName, (a, b) -> a));

        Map<Long, String> inner = new HashMap<>();
        for (Product p : list) {
            String merchant = merchantMap.getOrDefault(p.getMerchantId(), "");
            StringBuilder sb = new StringBuilder();
            if (StringUtils.hasText(merchant)) sb.append("商家：").append(merchant).append(" · ");
            sb.append(p.getProductName());
            if (StringUtils.hasText(p.getUnit())) sb.append("（").append(p.getUnit()).append("）");
            inner.put(p.getId(), sb.toString());
        }
        cache.put("Product", inner);
    }

    private void enrichAftersale(Set<Long> ids, Map<String, Map<Long, String>> cache) {
        List<Aftersale> list = aftersaleMapper.selectBatchIds(ids);
        if (list.isEmpty()) return;

        Map<String, String> typeMap = Map.of("REFUND_ONLY", "仅退款", "REFUND_RETURN", "退货退款");
        Map<String, String> statusMap = Map.of(
                "APPLYING", "待审核", "APPROVED", "已通过", "REJECTED", "已驳回",
                "PROCESSING", "处理中", "COMPLETED", "已完成", "CANCELLED", "已取消"
        );

        Map<Long, String> inner = new HashMap<>();
        for (Aftersale a : list) {
            StringBuilder sb = new StringBuilder();
            String type = typeMap.getOrDefault(a.getAftersaleType(), a.getAftersaleType());
            String st = statusMap.getOrDefault(a.getAftersaleStatus(), a.getAftersaleStatus());
            sb.append("类型：").append(type != null ? type : "—");
            sb.append(" · 申请金额：¥").append(a.getApplyAmount());
            sb.append(" · 状态：").append(st != null ? st : "—");
            if (a.getOrderId() != null) sb.append(" · 订单#").append(a.getOrderId());
            inner.put(a.getId(), sb.toString());
        }
        cache.put("Aftersale", inner);
    }

    /** 把 enrich 信息应用到 VO — 丰富 targetDesc + 补 detailHint 业务字段 */
    private void applyEnrich(OperationLogVO vo, TargetEnrichInfo enrich) {
        // 1) 补 #编号 — OpLogTranslator.translate() 里 targetId=null 时不会拼 "#id"
        //    但 enrichTargets 已经从 request_params 反解出 bizId 了，这里把它填上
        String oldDesc = vo.getTargetDesc();
        if (StringUtils.hasText(oldDesc)
                && enrich.bizId != null
                && !oldDesc.contains("#")) {
            oldDesc = oldDesc + " #" + enrich.bizId;
            vo.setTargetDesc(oldDesc);
        }

        // 2) 增强 targetDesc："供应计划 #39" → "供应计划 #39（商家：... · SKU：... · 状态：...）"
        if (StringUtils.hasText(oldDesc) && StringUtils.hasText(enrich.detail)) {
            String newDesc = oldDesc + "（" + enrich.detail + "）";
            vo.setTargetDesc(truncate(newDesc, 120));
        }

        // 3) 补 detailHint — 如果已有就拼上
        if (StringUtils.hasText(enrich.detail)) {
            String existing = vo.getDetailHint();
            if (StringUtils.hasText(existing)) {
                vo.setDetailHint(enrich.detail + " · " + existing);
            } else {
                vo.setDetailHint(enrich.detail);
            }
        }

        // 4) summary 同步更新
        String oldSummary = vo.getSummary();
        if (StringUtils.hasText(oldSummary) && StringUtils.hasText(vo.getTargetDesc())
                && !Objects.equals(oldDesc, vo.getTargetDesc())) {
            vo.setSummary(oldSummary.replace(oldDesc, vo.getTargetDesc()));
        }
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }

    // ---- 内部 DTO ----

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    private static class BizIdEntry {
        String targetType;
        Long bizId;
    }

    @Data
    private static class TargetEnrichInfo {
        Long bizId;
        String detail; // 可读描述，如 "商家：李记日用 · SKU：土豆500g · 计划量：10 · 状态：已到货"
    }
}
