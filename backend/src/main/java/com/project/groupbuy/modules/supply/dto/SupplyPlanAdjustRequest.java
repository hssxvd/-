package com.project.groupbuy.modules.supply.dto;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class SupplyPlanAdjustRequest {
    @NotNull(message = "planId 不能为空")
    private Long planId;
    @NotNull(message = "newQty 不能为空")
    @Min(value = 1, message = "newQty 必须 >= 1")
    private Integer newQty;
    private String reason;
}