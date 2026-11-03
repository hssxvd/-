package com.project.groupbuy.modules.forecast.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预测结果表（约束文档 12：预测值 + 预测区间）
 * forecast_value → supply_plan.forecast_qty → 商家调整 → plan_qty
 */
@Data
@TableName("forecast_result")
public class ForecastResult implements Serializable {

    private Long id;
    private Long taskId;
    private Long skuId;
    private Long communityId;
    private LocalDate forecastDate;
    private BigDecimal forecastValue;
    /** 预测区间下界 */
    private BigDecimal lowerBound;
    /** 预测区间上界 */
    private BigDecimal upperBound;
    private String modelVersion;
    private LocalDateTime createTime;
}
