package com.project.groupbuy.modules.admin.controller;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.groupbuy.common.annotation.RequirePermission;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.admin.service.AdminOrderService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.List;
import java.util.Map;

/**
 * 管理员端 · 全局订单管理
 * 约束 §23 全局审计 / L602 RBAC / §5.2 状态机
 */
@Slf4j
@RestController
@RequestMapping("/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @GetMapping
    @RequirePermission("order:view")
    public R<IPage<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1")  int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String orderStatus,
            @RequestParam(required = false) String payStatus,
            @RequestParam(required = false) String fulfillmentStatus,
            @RequestParam(required = false) String aftersaleStatus,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long merchantId,
            @RequestParam(required = false) Long communityId,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(adminOrderService.page(pageNum, pageSize, orderStatus, payStatus,
                fulfillmentStatus, aftersaleStatus, orderNo, userId, merchantId, communityId,
                minAmount, maxAmount, startTime, endTime));
    }

    @GetMapping("/{id}")
    @RequirePermission("order:view")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(adminOrderService.detail(id));
    }

    @PostMapping("/{id}/cancel")
    @RequirePermission("order:admin")
    public R<Void> adminCancel(@PathVariable Long id,
                               @RequestParam(required = false, defaultValue = "管理员强制取消") String reason) {
        adminOrderService.adminCancel(id, reason);
        return R.ok();
    }

    @PostMapping("/{id}/complete")
    @RequirePermission("order:admin")
    public R<Void> adminComplete(@PathVariable Long id,
                                 @RequestParam(required = false) String reason) {
        adminOrderService.adminComplete(id, reason);
        return R.ok();
    }

    /** 订单状态下拉常量（前端筛选用） */

    @GetMapping("/{id}/split-preview")
    @RequirePermission("order:admin")
    public R<Map<String, Object>> splitPreview(@PathVariable Long id) {
        return R.ok(adminOrderService.splitPreview(id));
    }

    @PostMapping("/{id}/split")
    @RequirePermission("order:admin")
    public R<List<Long>> split(@PathVariable Long id,
                               @RequestParam(required = false) String reason) {
        return R.ok(adminOrderService.split(id, reason));
    }

    @GetMapping("/status-options")
    @RequirePermission("order:view")
    public R<Map<String, List<String>>> statusOptions() {
        Map<String, List<String>> m = new java.util.LinkedHashMap<>();
        m.put("orderStatus",       List.of("PENDING_CONFIRM","PENDING_PAY","PAID","FULFILLING","PENDING_PICKUP","DELIVERING","DELIVERED","PICKED_UP","COMPLETED","CANCELLED","REFUNDING","REFUNDED","EXCEPTION"));
        m.put("payStatus",         List.of("PENDING","SUCCESS","FAILED","TIMEOUT","REFUNDING","REFUNDED"));
        m.put("fulfillmentStatus", List.of("PENDING_ASSIGN","PENDING_PICKUP","DELIVERING","DELIVERED","PICKED_UP","EXCEPTION"));
        m.put("aftersaleStatus",   List.of("NONE","APPLYING","APPROVED","REJECTED","REFUNDING","REFUNDED"));
        return R.ok(m);
    }
}