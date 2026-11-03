package com.project.groupbuy.modules.aftersale.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 售后审核请求（约束文档 5.5：APPLYING → APPROVED | REJECTED）
 *
 * 商家端与 Admin 端共用；approve=true 时 Service 在同一事务内完成
 * 审核通过 → 退款 → 明细回填 → 订单聚合 → 退货回库 → 轨迹落库。
 */
@Data
public class AftersaleAuditRequest {

    @NotNull(message = "审核结论不能为空")
    private Boolean approve;

    /** 审核备注（驳回时建议填写理由）*/
    private String remark;
}
