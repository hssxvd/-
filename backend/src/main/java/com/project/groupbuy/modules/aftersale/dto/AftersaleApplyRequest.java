package com.project.groupbuy.modules.aftersale.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 消费者申请售后请求（约束文档 5.5 售后状态机 + 7.3 参数校验）
 *
 * 校验规则：
 *   applyAmount ≤ order_item.subtotal − order_item.refunded_amount
 *   累计申请金额 ≤ orders.pay_amount（由 Service 二次校验，DTO 只保证 > 0）
 *   同一 order_item 有效售后单 ≤ 2 张（Service 校验）
 */
@Data
public class AftersaleApplyRequest {

    @NotNull(message = "订单 ID 不能为空")
    private Long orderId;

    @NotNull(message = "订单明细 ID 不能为空")
    private Long orderItemId;

    /** 售后类型：REFUND_ONLY（仅退款）/ RETURN（退货退款），取值见 AftersaleStatusConstant */
    @NotBlank(message = "售后类型不能为空")
    private String type;

    @NotBlank(message = "售后原因不能为空")
    private String reason;

    /** 问题描述（可选）*/
    private String description;

    /** 凭证图片，多张以英文逗号分隔（可选）*/
    private String images;

    @NotNull(message = "申请退款金额不能为空")
    @DecimalMin(value = "0.01", message = "申请退款金额必须大于 0")
    private BigDecimal applyAmount;
}
