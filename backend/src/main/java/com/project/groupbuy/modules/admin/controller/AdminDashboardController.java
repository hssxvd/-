package com.project.groupbuy.modules.admin.controller;

import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理员端全局概览 Dashboard
 *
 * 端点：
 *   GET /admin/dashboard/overview  — 单端点返回全部图表数据（卡片+折线+饼图+柱状+排行榜）
 *
 * 参数：
 *   days  — GMV 趋势天数，默认 30，可选 7/30/90
 *   limit — Top N 排行榜上限，默认 10
 *
 * 权限：复用全局 SecurityConfig /auth/** permitAll，/admin/** 走 ADMIN 角色
 *      （AdminAftersaleController 同模式，SecurityContext 前置）
 *
 * 约束文档合规：
 *   §23 管理员职责含"全局统计" → 本端点就是为此设计
 *   §11 统计口径统一 → 所有聚合 SQL 在 AdminDashboardMapper，前端不做二次计算
 *   §8 数据隔离     → 管理员端跨商家聚合，不走 merchant_id 过滤
 */
@Slf4j
@RestController
@RequestMapping("/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    /**
     * GET /admin/dashboard/overview — 全平台概览（9 组数据一次返回）
     */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Integer limit) {
        return R.ok(adminDashboardService.overview(days, limit));
    }
}
