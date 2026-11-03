package com.project.groupbuy.modules.supply.service;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.aspect.OpLog;
import com.project.groupbuy.common.constant.SupplyPlanStatusConstant;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.forecast.entity.ForecastResult;
import com.project.groupbuy.modules.forecast.mapper.ForecastResultMapper;
import com.project.groupbuy.modules.product.entity.Product;
import com.project.groupbuy.modules.product.entity.ProductSku;
import com.project.groupbuy.modules.product.mapper.ProductMapper;
import com.project.groupbuy.modules.product.mapper.ProductSkuMapper;
import com.project.groupbuy.modules.community.entity.Community;
import com.project.groupbuy.modules.community.mapper.CommunityMapper;
import com.project.groupbuy.modules.supply.entity.Stock;
import com.project.groupbuy.modules.supply.entity.SupplyPlan;
import com.project.groupbuy.modules.supply.mapper.SupplyPlanMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 供应计划服务（约束 6.1 · v1.13 合规重写 + P1 增强）
 *
 * 状态机: DRAFT -> CONFIRMED -> ARRIVED
 *         DRAFT / CONFIRMED / ARRIVED -> CANCELLED
 *         ARRIVED 取消必须 ROLLBACK 回滚库存
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SupplyPlanService {

    private final SupplyPlanMapper supplyPlanMapper;
    private final StockService stockService;
    private final ForecastResultMapper forecastResultMapper;
    private final ProductMapper productMapper;
    private final ProductSkuMapper productSkuMapper;
    private final CommunityMapper communityMapper;

    private Integer resolvePlanQty(SupplyPlan plan) {
        return plan.getAdjustedQty() != null ? plan.getAdjustedQty() : plan.getPlanQty();
    }

    /**
     * 批量 enrich：把 product/sku/community 名字和图片填到 SupplyPlan transient 字段
     * 避免每个 plan 单独查 DB（N+1 问题）
     */
    private void enrich(List<SupplyPlan> plans) {
        if (plans == null || plans.isEmpty()) return;
        Set<Long> skuIds   = plans.stream().map(SupplyPlan::getSkuId).collect(Collectors.toSet());
        Set<Long> commIds  = plans.stream().map(SupplyPlan::getCommunityId).collect(Collectors.toSet());
        List<ProductSku> skus    = productSkuMapper.selectBatchIds(skuIds);
        Map<Long, ProductSku> skuMap = skus.stream().collect(Collectors.toMap(ProductSku::getId, s -> s, (a, b) -> a));
        Set<Long> productIds = skus.stream().map(ProductSku::getProductId).collect(Collectors.toSet());
        Map<Long, Product> productMap = productMapper.selectBatchIds(productIds).stream()
                .collect(Collectors.toMap(Product::getId, p -> p, (a, b) -> a));
        Map<Long, Community> commMap = communityMapper.selectBatchIds(commIds).stream()
                .collect(Collectors.toMap(Community::getId, c -> c, (a, b) -> a));

        for (SupplyPlan p : plans) {
            ProductSku sku = skuMap.get(p.getSkuId());
            if (sku != null) {
                p.setSkuName(sku.getSkuName());
                p.setSpec(sku.getSpec());
                p.setSkuImage(sku.getImage());
                Product product = productMap.get(sku.getProductId());
                if (product != null) {
                    p.setProductName(product.getProductName());
                    p.setMainImage(product.getMainImage());
                }
            }
            Community c = commMap.get(p.getCommunityId());
            if (c != null) p.setCommunityName(c.getCommunityName());
        }
    }

    // ========== P0 ==========

    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "供应", operationType = "CREATE", targetType = "SupplyPlan")
    public SupplyPlan create(Long skuId, Long communityId, LocalDate planDate, Integer planQty, String adjustReason) {
        Long merchantId = SecurityContext.getMerchantId();
        int dup = supplyPlanMapper.countActiveBySkuCommunityDate(merchantId, skuId, communityId, planDate);
        if (dup > 0) {
            throw new BusinessException(ErrorCode.SUPPLY_PLAN_DUPLICATE,
                    String.format("SKU=%d 在社区=%d 的 %s 已存在未取消供应计划", skuId, communityId, planDate));
        }
        SupplyPlan plan = new SupplyPlan();
        plan.setPlanNo("SP" + IdUtil.getSnowflakeNextIdStr());
        plan.setSkuId(skuId); plan.setMerchantId(merchantId); plan.setCommunityId(communityId);
        plan.setPlanDate(planDate);
        plan.setPlanQty(planQty); plan.setAdjustedQty(planQty); plan.setAdjustReason(adjustReason);
        plan.setStatus(SupplyPlanStatusConstant.DRAFT);
        plan.setOperatorId(SecurityContext.getUserId());

        ForecastResult forecast = forecastResultMapper.selectOne(
                new LambdaQueryWrapper<ForecastResult>()
                        .eq(ForecastResult::getSkuId, skuId).eq(ForecastResult::getCommunityId, communityId)
                        .eq(ForecastResult::getForecastDate, planDate).last("LIMIT 1"));
        if (forecast != null) plan.setForecastQty(forecast.getForecastValue());

        supplyPlanMapper.insert(plan);
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "供应", operationType = "UPDATE", targetType = "SupplyPlan")
    public SupplyPlan adjust(Long planId, Integer newQty, String reason) {
        SupplyPlan plan = supplyPlanMapper.selectById(planId);
        if (plan == null) throw new BusinessException(ErrorCode.SUPPLY_PLAN_NOT_FOUND);
        checkMerchantOwns(plan);
        if (!SupplyPlanStatusConstant.canAdjust(plan.getStatus())) {
            throw new BusinessException(ErrorCode.SUPPLY_PLAN_INVALID, "已到货/已取消的计划不可调整");
        }
        Integer oldQty = resolvePlanQty(plan);
        plan.setAdjustedQty(newQty); plan.setAdjustReason(reason); plan.setOperatorId(SecurityContext.getUserId());
        supplyPlanMapper.updateById(plan);
        log.info("[供应调整] planNo={}, {}->{}, forecast={}", plan.getPlanNo(), oldQty, newQty, plan.getForecastQty());
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "供应", operationType = "TRANSITION", targetType = "SupplyPlan")
    public SupplyPlan confirm(Long planId) {
        SupplyPlan plan = supplyPlanMapper.selectById(planId);
        if (plan == null) throw new BusinessException(ErrorCode.SUPPLY_PLAN_NOT_FOUND);
        checkMerchantOwns(plan);
        if (!SupplyPlanStatusConstant.canConfirm(plan.getStatus())) {
            throw new BusinessException(ErrorCode.SUPPLY_PLAN_INVALID, "只有草稿状态可确认");
        }
        plan.setStatus(SupplyPlanStatusConstant.CONFIRMED);
        supplyPlanMapper.updateById(plan);
        return plan;
    }

    /**
     * 标记到货 · P1-3 actualQty 可选差异校验
     */
    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "供应", operationType = "TRANSITION", targetType = "SupplyPlan")
    public SupplyPlan arrive(Long planId, Integer actualQty) {
        SupplyPlan plan = supplyPlanMapper.selectById(planId);
        if (plan == null) throw new BusinessException(ErrorCode.SUPPLY_PLAN_NOT_FOUND);
        checkMerchantOwns(plan);
        if (!SupplyPlanStatusConstant.canArrive(plan.getStatus())) {
            throw new BusinessException(ErrorCode.SUPPLY_PLAN_INVALID, "只有已确认状态可标记到货");
        }
        Integer plannedQty = resolvePlanQty(plan);
        Integer qty = actualQty != null ? actualQty : plannedQty;
        boolean hasDiff = actualQty != null && !actualQty.equals(plannedQty);

        plan.setStatus(SupplyPlanStatusConstant.ARRIVED);
        plan.setArriveTime(LocalDateTime.now());
        supplyPlanMapper.updateById(plan);

        String diffRemark = hasDiff
                ? String.format("差异: 计划%d vs 实际%d (差%+d)", plannedQty, actualQty, actualQty - plannedQty)
                : null;
        stockService.supplyInOrCreate(plan.getSkuId(), plan.getCommunityId(), null,
                qty, plan.getPlanNo(), SecurityContext.getUserId(), diffRemark);

        log.info("[供应到货] planNo={}, planned={}, actual={}, forecast={}, diff={}",
                plan.getPlanNo(), plannedQty, qty, plan.getForecastQty(), hasDiff ? (actualQty - plannedQty) : "无");
        return plan;
    }

    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "供应", operationType = "TRANSITION", targetType = "SupplyPlan")
    public SupplyPlan cancel(Long planId, String reason) {
        SupplyPlan plan = supplyPlanMapper.selectById(planId);
        if (plan == null) throw new BusinessException(ErrorCode.SUPPLY_PLAN_NOT_FOUND);
        checkMerchantOwns(plan);
        if (SupplyPlanStatusConstant.CANCELLED.equals(plan.getStatus())) {
            throw new BusinessException(ErrorCode.SUPPLY_PLAN_INVALID, "计划已取消");
        }
        if (!SupplyPlanStatusConstant.canCancel(plan.getStatus())) {
            throw new BusinessException(ErrorCode.SUPPLY_PLAN_INVALID, "当前状态不允许取消");
        }
        if (SupplyPlanStatusConstant.needRollbackOnCancel(plan.getStatus())) {
            Integer qty = resolvePlanQty(plan);
            Stock stock = stockService.getBySkuAndCommunity(plan.getSkuId(), plan.getCommunityId(), null);
            if (stock != null) {
                stockService.rollback(stock.getId(), qty, SecurityContext.getUserId(),
                        plan.getPlanNo(), "供应计划取消回滚（原SUPPLY_IN qty=" + qty + "）");
            }
        }
        plan.setStatus(SupplyPlanStatusConstant.CANCELLED); plan.setRemark(reason); plan.setOperatorId(SecurityContext.getUserId());
        supplyPlanMapper.updateById(plan);
        return plan;
    }

    public List<SupplyPlan> listMine(String status, LocalDate startDate, LocalDate endDate) {
        Long merchantId = SecurityContext.getMerchantId();
        LambdaQueryWrapper<SupplyPlan> w = new LambdaQueryWrapper<>();
        w.eq(SupplyPlan::getMerchantId, merchantId);
        w.eq(status != null, SupplyPlan::getStatus, status);
        w.ge(startDate != null, SupplyPlan::getPlanDate, startDate);
        w.le(endDate != null, SupplyPlan::getPlanDate, endDate);
        w.orderByDesc(SupplyPlan::getPlanDate);
        List<SupplyPlan> list = supplyPlanMapper.selectList(w);
        enrich(list);
        return list;
    }

    // ========== P1-1 批量创建 ==========

    @Transactional(rollbackFor = Exception.class)
    @OpLog(module = "供应", operationType = "CREATE", targetType = "SupplyPlan")
    public Map<String, Object> batchCreate(Long communityId, LocalDate planDate,
                                            List<Long> skuIds, Double multiplier) {
        Long merchantId = SecurityContext.getMerchantId();
        double m = multiplier != null ? multiplier : 1.0;

        LambdaQueryWrapper<ForecastResult> fw = new LambdaQueryWrapper<>();
        fw.eq(ForecastResult::getCommunityId, communityId)
          .eq(ForecastResult::getForecastDate, planDate);
        if (skuIds != null && !skuIds.isEmpty()) fw.in(ForecastResult::getSkuId, skuIds);
        List<ForecastResult> forecasts = forecastResultMapper.selectList(fw);

        // 同 SKU 最新 task 去重
        Map<Long, ForecastResult> latest = new HashMap<>();
        for (ForecastResult fr : forecasts) {
            ForecastResult prev = latest.get(fr.getSkuId());
            if (prev == null || fr.getId() > prev.getId()) latest.put(fr.getSkuId(), fr);
        }

        int created = 0, skippedDup = 0;
        List<Map<String, Object>> createdList = new ArrayList<>();
        List<String> skipReasons = new ArrayList<>();

        for (ForecastResult fr : latest.values()) {
            if (supplyPlanMapper.countActiveBySkuCommunityDate(merchantId, fr.getSkuId(), communityId, planDate) > 0) {
                skippedDup++; continue;
            }
            int planQty = (int) Math.max(1, Math.floor(fr.getForecastValue().doubleValue() * m));
            SupplyPlan p = new SupplyPlan();
            p.setPlanNo("SP" + IdUtil.getSnowflakeNextIdStr());
            p.setSkuId(fr.getSkuId()); p.setMerchantId(merchantId); p.setCommunityId(communityId);
            p.setPlanDate(planDate); p.setPlanQty(planQty); p.setAdjustedQty(planQty);
            p.setForecastQty(fr.getForecastValue());
            p.setStatus(SupplyPlanStatusConstant.DRAFT);
            p.setOperatorId(SecurityContext.getUserId());
            p.setAdjustReason("批量生成（forecast=" + fr.getForecastValue() + " * " + m + "）");
            supplyPlanMapper.insert(p);
            created++;

            Map<String, Object> one = new LinkedHashMap<>();
            one.put("planId", p.getId()); one.put("skuId", fr.getSkuId());
            one.put("planQty", planQty); one.put("forecastQty", fr.getForecastValue());
            createdList.add(one);
        }
        if (skippedDup > 0) skipReasons.add("已有未取消供应计划（" + skippedDup + " 条）");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("createdCount", created); result.put("skippedCount", skippedDup);
        result.put("created", createdList); result.put("skipReasons", skipReasons);
        return result;
    }

    // ========== P1-2 库存预警 → 供应建议 ==========

    /**
     * 低库存触发供应建议（约束 6.1 必须 6：只建议，不自动创建）
     * available < COALESCE(warn_qty, total*0.1) → 触发
     * suggestedQty = max(gap, forecast)
     */
    public List<Map<String, Object>> suggestFromLowStockForMerchant(Long merchantId, LocalDate targetDate) {
        List<Map<String, Object>> all = stockService.listMine(merchantId);
        LocalDate date = targetDate != null ? targetDate : LocalDate.now().plusDays(1);
        List<Map<String, Object>> result = new ArrayList<>();

        for (Map<String, Object> m : all) {
            Long skuId = ((Number) m.get("sku_id")).longValue();
            Long communityId = ((Number) m.get("community_id")).longValue();
            Integer available = ((Number) m.get("available_qty")).intValue();
            Long stockId = ((Number) m.get("id")).longValue();
            Integer total = ((Number) m.get("total_qty")).intValue();
            Integer warn = m.get("warn_qty") != null ? ((Number) m.get("warn_qty")).intValue() : (int) Math.max(1, Math.round(total * 0.1));
            if (available >= warn) continue;

            int gap = warn - available;
            BigDecimal fq = forecastResultMapper.selectOne(
                    new LambdaQueryWrapper<ForecastResult>()
                            .eq(ForecastResult::getSkuId, skuId)
                            .eq(ForecastResult::getCommunityId, communityId)
                            .eq(ForecastResult::getForecastDate, date).last("LIMIT 1")) != null
                    ? forecastResultMapper.selectOne(
                            new LambdaQueryWrapper<ForecastResult>()
                                    .eq(ForecastResult::getSkuId, skuId)
                                    .eq(ForecastResult::getCommunityId, communityId)
                                    .eq(ForecastResult::getForecastDate, date).last("LIMIT 1")).getForecastValue()
                    : null;
            int suggested = gap;
            if (fq != null && fq.intValue() > suggested) suggested = fq.intValue();

            Map<String, Object> one = new LinkedHashMap<>();
            one.put("skuId", skuId); one.put("stockId", stockId);
            one.put("availableQty", available); one.put("warnQty", warn); one.put("gapQty", gap);
            one.put("forecastQty", fq); one.put("suggestedQty", suggested);
            one.put("reason", suggested > gap ? "预测驱动" : "安全库存驱动");
            // 展示 enrich（stock listMine SQL 已 JOIN product/sku/community，直接取）
            one.put("communityId", ((Number) m.get("community_id")).longValue());
            one.put("productName", m.get("product_name"));
            one.put("skuName", m.get("sku_name"));
            one.put("spec", m.get("spec"));
            one.put("mainImage", m.get("main_image"));
            one.put("skuImage", m.get("sku_image"));
            one.put("communityName", m.get("community_name"));
            result.add(one);
        }
        return result;
    }

    private void checkMerchantOwns(SupplyPlan plan) {
        Long merchantId = SecurityContext.getMerchantId();
        if (!merchantId.equals(plan.getMerchantId())) throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    // ========== Admin 端 ==========

    /** Admin：全商家供应计划列表（支持 merchantId / status / 日期筛选）*/
    public List<SupplyPlan> listAllForAdmin(Long merchantId, String status, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<SupplyPlan> w = new LambdaQueryWrapper<>();
        w.eq(merchantId != null, SupplyPlan::getMerchantId, merchantId);
        w.eq(status != null, SupplyPlan::getStatus, status);
        w.ge(startDate != null, SupplyPlan::getPlanDate, startDate);
        w.le(endDate != null, SupplyPlan::getPlanDate, endDate);
        w.orderByDesc(SupplyPlan::getPlanDate);
        return supplyPlanMapper.selectList(w);
    }

    /** Admin：供应计划状态聚合统计 */
    public Map<String, Long> supplyStats() {
        List<SupplyPlan> all = supplyPlanMapper.selectList(null);
        Map<String, Long> m = new HashMap<>();
        all.forEach(p -> m.merge(p.getStatus(), 1L, Long::sum));
        return m;
    }
}