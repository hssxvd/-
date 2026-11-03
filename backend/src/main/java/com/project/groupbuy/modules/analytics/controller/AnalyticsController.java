package com.project.groupbuy.modules.analytics.controller;

import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.analytics.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import com.project.groupbuy.common.constant.UserTypeConstant;

/**
 * 经营分析控制器（约束文档 11 + 课题"经营分析"）
 */
@Slf4j
@RestController
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    /**
     * 商家仪表盘统计卡片
     */
    @GetMapping("/merchant/summary")
    public R<Map<String, Object>> merchantSummary() {
        return R.ok(analyticsService.merchantSummary());
    }

    /**
     * GMV/订单量趋势（近 N 天）
     */
    @GetMapping("/merchant/gmv-trend")
    public R<List<Map<String, Object>>> gmvTrend(@RequestParam(defaultValue = "7") int days) {
        Long merchantId = UserTypeConstant.MERCHANT.equals(SecurityContext.getUserType()) ? SecurityContext.getMerchantId() : null;
        return R.ok(analyticsService.gmvTrend(merchantId, days));
    }

    /**
     * 热销商品 Top N
     */
    @GetMapping("/merchant/top-products")
    public R<List<Map<String, Object>>> topProducts(@RequestParam(defaultValue = "10") int n) {
        Long merchantId = UserTypeConstant.MERCHANT.equals(SecurityContext.getUserType()) ? SecurityContext.getMerchantId() : null;
        return R.ok(analyticsService.topProducts(merchantId, n));
    }

    /**
     * 预测 vs 实际（MAE vs 基线）
     */
    @GetMapping("/merchant/forecast-vs-actual")
    public R<List<Map<String, Object>>> forecastVsActual() {
        Long merchantId = UserTypeConstant.MERCHANT.equals(SecurityContext.getUserType()) ? SecurityContext.getMerchantId() : null;
        return R.ok(analyticsService.forecastVsActual(merchantId));
    }
}
