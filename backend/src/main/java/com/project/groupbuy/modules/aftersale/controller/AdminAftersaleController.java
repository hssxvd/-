package com.project.groupbuy.modules.aftersale.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.aftersale.dto.AftersaleAuditRequest;
import com.project.groupbuy.modules.aftersale.dto.AftersaleForceRefundRequest;
import com.project.groupbuy.modules.aftersale.dto.AftersaleQueryRequest;
import com.project.groupbuy.modules.aftersale.service.AftersaleService;
import com.project.groupbuy.modules.aftersale.vo.AftersaleDetailVO;
import com.project.groupbuy.modules.aftersale.vo.AftersaleVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * Admin 端售后管理（v1.14 · 只读监控 + 可强制介入）
 *
 * 端点：
 *   GET  /admin/aftersales                      — 全平台售后列表（分页 + 可选商家下钻）
 *   GET  /admin/aftersales/stats                — 全平台售后统计卡
 *   POST /admin/aftersales/{id:\d+}/audit       — 强制审核（body）
 *   POST /admin/aftersales/{id:\d+}/force-refund— 强制退款（body）
 *
 * 权限分级（约束 8）：Admin 默认只读监控；强制介入为显式二次操作，均写 operation_log。
 */
@Slf4j
@RestController
@RequestMapping("/admin/aftersales")
@RequiredArgsConstructor
public class AdminAftersaleController {

    private final AftersaleService aftersaleService;

    /**
     * GET /admin/aftersales — 全平台售后列表（分页）
     */
    @GetMapping
    public R<IPage<AftersaleVO>> list(@Valid AftersaleQueryRequest req) {
        return R.ok(aftersaleService.pageForAdmin(req));
    }

    /**
     * GET /admin/aftersales/stats — 全平台售后统计卡（可选 merchantId 下钻）
     */
    @GetMapping("/stats")
    public R<Map<String, Object>> stats(@RequestParam(required = false) Long merchantId) {
        return R.ok(aftersaleService.statsForAdmin(merchantId));
    }

    /**
     * POST /admin/aftersales/{id:\d+}/audit — 强制审核（通过 / 驳回）
     */
    @PostMapping("/{id:\\d+}/audit")
    public R<AftersaleDetailVO> audit(@PathVariable Long id,
                                      @Valid @RequestBody AftersaleAuditRequest req) {
        return R.ok(aftersaleService.adminAudit(id, req.getApprove(), req.getRemark()));
    }

    /**
     * POST /admin/aftersales/{id:\d+}/force-refund — 强制退款
     */
    @PostMapping("/{id:\\d+}/force-refund")
    public R<AftersaleDetailVO> forceRefund(@PathVariable Long id,
                                            @Valid @RequestBody AftersaleForceRefundRequest req) {
        return R.ok(aftersaleService.forceRefund(id, req.getReason()));
    }
}
