package com.project.groupbuy.modules.forecast.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预测任务表（约束文档 12 需求预测核心智能化功能）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("forecast_task")
public class ForecastTask extends BaseEntity {

    private String taskNo;
    /** MA / LR / RF / XGB */
    private String modelType;
    private String modelVersion;
    private LocalDate forecastDate;
    private Long communityId;
    private Long categoryId;
    /** PENDING / RUNNING / SUCCESS / FAILED */
    private String status;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMsg;
}
