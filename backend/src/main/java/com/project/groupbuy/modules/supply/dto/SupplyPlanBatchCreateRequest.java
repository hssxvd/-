package com.project.groupbuy.modules.supply.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * 批量创建供应计划请求（P1-1）
 * 从 forecast_result 批量生成，planQty 默认 = forecastValue.floor()
 */
@Data
public class SupplyPlanBatchCreateRequest {
    @NotNull(message = "communityId 不能为空")
    private Long communityId;

    @NotNull(message = "planDate 不能为空")
    private LocalDate planDate;

    /** 可选：指定 SKU 子集；不传 = 给该社区当日所有有 forecast 的 SKU 都建 */
    private List<Long> skuIds;

    /** 默认补货倍数（默认 1.0 = forecastQty 原样；>1 商家加码）*/
    private Double multiplier = 1.0;
}