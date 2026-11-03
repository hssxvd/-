package com.project.groupbuy.modules.supply.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 库存总览查询参数（约束文档 11：可视化图表数据必须由后端统计接口提供）
 */
@Data
public class StockOverviewQueryRequest {

    /** 社区 ID（为空表示全部社区） */
    private Long communityId;

    /** 趋势天数，默认 30，范围 1~90 */
    @Min(value = 1, message = "days 最小为 1")
    @Max(value = 90, message = "days 最大为 90")
    private Integer days = 30;
}
