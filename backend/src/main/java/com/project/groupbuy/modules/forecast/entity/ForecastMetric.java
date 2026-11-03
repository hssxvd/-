package com.project.groupbuy.modules.forecast.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 预测指标表（约束文档 12：MAE/RMSE/SMAPE vs 基线对比）
 * 核心模型至少一个指标优于所有基线
 */
@Data
@TableName("forecast_metric")
public class ForecastMetric implements Serializable {

    private Long id;
    private Long taskId;
    /** MA / LR / RF / XGB */
    private String modelType;
    private String modelVersion;
    /** 基线类型：YESTERDAY / SAME_DAY_LAST_WEEK / MA7，NULL 表示模型自身 */
    private String baselineType;
    private BigDecimal mae;
    private BigDecimal rmse;
    private BigDecimal smape;
    private Integer sampleCount;
    private LocalDateTime createTime;
}
