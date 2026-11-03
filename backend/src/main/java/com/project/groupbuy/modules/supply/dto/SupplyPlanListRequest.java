package com.project.groupbuy.modules.supply.dto;

import lombok.Data;
import java.time.LocalDate;

@Data
public class SupplyPlanListRequest {
    private String status;
    private LocalDate startDate;
    private LocalDate endDate;
}