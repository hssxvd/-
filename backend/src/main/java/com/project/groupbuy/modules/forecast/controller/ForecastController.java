package com.project.groupbuy.modules.forecast.controller;

import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.forecast.entity.ForecastMetric;
import com.project.groupbuy.modules.forecast.entity.ForecastResult;
import com.project.groupbuy.modules.forecast.entity.ForecastTask;
import com.project.groupbuy.modules.forecast.service.ForecastService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * 预测控制器（约束文档 12 课题核心智能化功能）
 */
@Slf4j
@RestController
@RequestMapping("/forecast")
@RequiredArgsConstructor
public class ForecastController {

    private final ForecastService forecastService;

    /**
     * 触发预测（管理端）
     */
    @PostMapping("/tasks")
    public R<ForecastTask> trigger(@RequestParam String modelType,
                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate forecastDate,
                                    @RequestParam(required = false) Long communityId,
                                    @RequestParam(required = false) Long categoryId) {
        return R.ok(forecastService.trigger(modelType, forecastDate, communityId, categoryId));
    }

    /**
     * 查询任务
     */
    @GetMapping("/tasks/{id}")
    public R<ForecastTask> getTask(@PathVariable Long id) {
        return R.ok(forecastService.getTask(id));
    }

    /**
     * 查询预测结果
     */
    @GetMapping("/results")
    public R<List<ForecastResult>> listResults(@RequestParam(required = false) Long taskId,
                                                @RequestParam(required = false) Long skuId,
                                                @RequestParam(required = false)
                                                @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate forecastDate) {
        return R.ok(forecastService.listResults(taskId, skuId, forecastDate));
    }

    /**
     * 查询评估指标（课题核心：MAE vs 基线对比）
     * 答辩演示：模型 MAE 必须优于所有基线
     */
    @GetMapping("/metrics")
    public R<List<ForecastMetric>> listMetrics(@RequestParam(required = false) Long taskId,
                                                 @RequestParam(required = false) String modelType) {
        return R.ok(forecastService.listMetrics(taskId, modelType));
    }
}
