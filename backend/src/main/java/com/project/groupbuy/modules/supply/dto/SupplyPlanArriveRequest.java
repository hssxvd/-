package com.project.groupbuy.modules.supply.dto;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/**
 * 到货请求（P1-3 到货差异校验：actualQty 可选）
 * - actualQty=null -> 默认用 adjustedQty，无差异
 * - actualQty 传值  -> 与计划量对比，差异写 SUPPLY_IN 流水 remark（约束 6.1 必须 5）
 */
@Data
public class SupplyPlanArriveRequest {
    @NotNull(message = "planId 不能为空")
    private Long planId;

    /** 实际到货量（可选，不传 = 与计划一致）*/
    @Min(value = 0, message = "actualQty 不能为负")
    private Integer actualQty;
}