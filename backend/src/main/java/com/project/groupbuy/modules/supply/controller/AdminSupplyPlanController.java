package com.project.groupbuy.modules.supply.controller;

import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.supply.entity.SupplyPlan;
import com.project.groupbuy.modules.supply.service.SupplyPlanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Admin 端供应计划管理（v1.13 P2 新增）
 * 查看全商家供应计划 + 统计 + 预警总览
 */
@Slf4j
@RestController
@RequestMapping("/admin/supply-plans")
@RequiredArgsConstructor
public class AdminSupplyPlanController {

    private final SupplyPlanService supplyPlanService;

    @GetMapping
    public R<List<SupplyPlan>> listAll(
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        LocalDate s = startDate != null ? LocalDate.parse(startDate) : null;
        LocalDate e = endDate != null ? LocalDate.parse(endDate) : null;
        return R.ok(supplyPlanService.listAllForAdmin(merchantId, status, s, e));
    }

    @GetMapping("/stats")
    public R<Map<String, Long>> stats() {
        return R.ok(supplyPlanService.supplyStats());
    }
}