package com.project.groupbuy.modules.aftersale.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * Admin 强制退款请求（v1.14 售后管理 · Admin 强制介入）
 *
 * 场景：商家长期不处理或已驳回但消费者投诉成立时，Admin 跳过商家审核直接退款。
 * 仅允许对 APPLYING / REJECTED 的售后单执行；已退款单拒绝重复退款（幂等守卫）。
 */
@Data
public class AftersaleForceRefundRequest {

    @NotBlank(message = "强制退款原因不能为空")
    private String reason;
}
