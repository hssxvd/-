package com.project.groupbuy.modules.supply.dto;

import lombok.Data;
import javax.validation.constraints.NotNull;

@Data
public class SupplyPlanIdRequest {
    @NotNull(message = "planId 不能为空")
    private Long planId;
}