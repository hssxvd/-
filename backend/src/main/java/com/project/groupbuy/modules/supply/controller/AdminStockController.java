package com.project.groupbuy.modules.supply.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.supply.dto.StockListQueryRequest;
import com.project.groupbuy.modules.supply.dto.StockOverviewQueryRequest;
import com.project.groupbuy.modules.supply.dto.StockRecordQueryRequest;
import com.project.groupbuy.modules.supply.service.StockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

/**
 * Admin 端库存管理（v1.14 新增 · 约束文档 11 统计口径统一）
 *
 * 端点：
 *   GET /admin/stocks           — 全商家库存列表（分页 + 预警状态筛选 + 可选商家筛选）
 *   GET /admin/stocks/overview  — 全局库存总览（统计卡 + 近 N 天出入库趋势）
 *   GET /admin/stocks/records   — 全局库存流水（分页 + 多维度筛选/下钻）
 *
 * Admin 只读监控，不允许调整库存（约束文档 8 权限分级）。
 * 口径与商家端 StockController 完全一致，仅 WHERE 条件改为可选 merchantId。
 */
@Slf4j
@RestController
@RequestMapping("/admin/stocks")
@RequiredArgsConstructor
public class AdminStockController {

    private final StockService stockService;

    /**
     * GET /admin/stocks — 全商家库存列表（分页）
     * 可选参数 merchantId 筛选指定商家
     */
    @GetMapping
    public R<IPage<Map<String, Object>>> list(@Valid StockListQueryRequest req,
                                               @RequestParam(required = false) Long merchantId) {
        return R.ok(stockService.pageAllForAdmin(merchantId, req));
    }

    /**
     * GET /admin/stocks/overview — 全局库存总览 + 趋势
     * 可选参数 merchantId 筛选指定商家
     */
    @GetMapping("/overview")
    public R<Map<String, Object>> overview(@Valid StockOverviewQueryRequest req,
                                            @RequestParam(required = false) Long merchantId) {
        return R.ok(stockService.overviewForAdmin(merchantId, req));
    }

    /**
     * GET /admin/stocks/records — 全局库存流水（分页）
     * 可选参数 merchantId 筛选指定商家
     */
    @GetMapping("/records")
    public R<IPage<Map<String, Object>>> records(@Valid StockRecordQueryRequest req,
                                                   @RequestParam(required = false) Long merchantId) {
        return R.ok(stockService.pageRecordsForAdmin(merchantId, req));
    }
}
