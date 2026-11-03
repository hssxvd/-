package com.project.groupbuy.modules.supply.controller;

import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.supply.dto.*;
import com.project.groupbuy.modules.supply.entity.SupplyPlan;
import com.project.groupbuy.modules.supply.service.SupplyPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/supply-plans")
@RequiredArgsConstructor
public class SupplyPlanController {

    private final SupplyPlanService supplyPlanService;

    // ---- P0 ----
    @PostMapping
    public R<SupplyPlan> create(@Valid @RequestBody SupplyPlanCreateRequest req) {
        return R.ok(supplyPlanService.create(req.getSkuId(), req.getCommunityId(),
                req.getPlanDate(), req.getPlanQty(), req.getAdjustReason()));
    }
    @PostMapping("/adjust")
    public R<SupplyPlan> adjust(@Valid @RequestBody SupplyPlanAdjustRequest req) {
        return R.ok(supplyPlanService.adjust(req.getPlanId(), req.getNewQty(), req.getReason()));
    }
    @PostMapping("/confirm")
    public R<SupplyPlan> confirm(@Valid @RequestBody SupplyPlanIdRequest req) {
        return R.ok(supplyPlanService.confirm(req.getPlanId()));
    }
    @PostMapping("/arrive")
    public R<SupplyPlan> arrive(@Valid @RequestBody SupplyPlanArriveRequest req) {
        return R.ok(supplyPlanService.arrive(req.getPlanId(), req.getActualQty()));
    }
    @PostMapping("/cancel")
    public R<SupplyPlan> cancel(@Valid @RequestBody SupplyPlanCancelRequest req) {
        return R.ok(supplyPlanService.cancel(req.getPlanId(), req.getReason()));
    }
    @PostMapping("/mine")
    public R<List<SupplyPlan>> mine(@RequestBody(required = false) SupplyPlanListRequest req) {
        return R.ok(supplyPlanService.listMine(
                req != null ? req.getStatus() : null,
                req != null ? req.getStartDate() : null,
                req != null ? req.getEndDate() : null));
    }

    // ---- P1 ----
    @PostMapping("/batch-create")
    public R<Map<String, Object>> batchCreate(@Valid @RequestBody SupplyPlanBatchCreateRequest req) {
        return R.ok(supplyPlanService.batchCreate(
                req.getCommunityId(), req.getPlanDate(), req.getSkuIds(), req.getMultiplier()));
    }
    @PostMapping("/suggest")
    public R<List<Map<String, Object>>> suggest(@RequestBody(required = false) SupplyPlanListRequest req) {
        LocalDate targetDate = req != null && req.getEndDate() != null
                ? req.getEndDate() : LocalDate.now().plusDays(1);
        return R.ok(supplyPlanService.suggestFromLowStockForMerchant(
                SecurityContext.getMerchantId(), targetDate));
    }
}