package com.project.groupbuy.modules.supply.dto;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.time.LocalDate;

@Data
public class SupplyPlanCreateRequest {
    @NotNull(message = "skuId 不能为空")
    private Long skuId;
    @NotNull(message = "communityId 不能为空")
    private Long communityId;
    @NotNull(message = "planDate 不能为空")
    private LocalDate planDate;
    @NotNull(message = "planQty 不能为空")
    @Min(value = 1, message = "planQty 必须 >= 1")
    private Integer planQty;
    private String adjustReason;
}