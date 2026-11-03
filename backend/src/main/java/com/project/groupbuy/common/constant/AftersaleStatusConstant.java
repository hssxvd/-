package com.project.groupbuy.common.constant;

import java.util.Set;

/**
 * 售后常量（约束文档 5.5 售后状态机 · v1.14 售后管理）
 *
 * 禁止在 Service / Controller / SQL 里直接写字符串 "APPLYING"/"REFUNDED" 等，
 * 必须 import 本类常量。
 *
 * 一、售后单据状态机（aftersale.aftersale_status）：
 *   APPLYING → APPROVED | REJECTED
 *   APPROVED → REFUNDING → REFUNDED
 *
 * 二、订单售后聚合状态（orders.aftersale_status，表达订单维度的售后进度）：
 *   NONE → APPLYING → REFUNDING → REFUNDED
 *   APPLYING → NONE（商家驳回后回落）
 *   注：聚合状态只表达"进度"，不改变 order_status 主状态；
 *       COMPLETED 订单发生售后时 order_status 仍保持 COMPLETED（约束 5.2）。
 *
 * 三、售后类型（aftersale.aftersale_type）：
 *   REFUND_ONLY 仅退款（不回库）
 *   RETURN      退货退款（审核通过后库存回库 INBOUND）
 */
public final class AftersaleStatusConstant {

    private AftersaleStatusConstant() {}

    // ==================== 售后单据状态 ====================

    /** 申请中（消费者已提交，待商家审核）*/
    public static final String APPLYING  = "APPLYING";
    /** 审核通过（待退款）*/
    public static final String APPROVED  = "APPROVED";
    /** 已驳回（终态）*/
    public static final String REJECTED  = "REJECTED";
    /** 退款中 */
    public static final String REFUNDING = "REFUNDING";
    /** 已退款（终态）*/
    public static final String REFUNDED  = "REFUNDED";

    // ==================== 订单售后聚合状态 ====================

    /** 无售后（订单默认值）*/
    public static final String ORDER_NONE = "NONE";
    /** 订单售后聚合：申请中 */
    public static final String ORDER_APPLYING = APPLYING;
    /** 订单售后聚合：退款中（与单据状态同值）*/
    public static final String ORDER_REFUNDING = REFUNDING;
    /** 订单售后聚合：已退款（与单据状态同值）*/
    public static final String ORDER_REFUNDED = REFUNDED;

    // ==================== 售后类型 ====================

    /** 仅退款 */
    public static final String TYPE_REFUND_ONLY = "REFUND_ONLY";
    /** 退货退款 */
    public static final String TYPE_RETURN      = "RETURN";

    /** 有效（占用售后次数配额）的单据状态集合：申请中/通过/退款中/已退款 */
    public static final Set<String> ACTIVE_STATUS = Set.of(APPLYING, APPROVED, REFUNDING, REFUNDED);

    /** 合法售后类型白名单（约束 7.3 参数合法性校验）*/
    public static final Set<String> VALID_TYPES = Set.of(TYPE_REFUND_ONLY, TYPE_RETURN);

    /** 合法单据状态白名单 */
    public static final Set<String> VALID_STATUS = Set.of(APPLYING, APPROVED, REJECTED, REFUNDING, REFUNDED);

    /** 售后单据状态 → 中文标签（约束 29 禁止前端硬编码）*/
    public static String label(String status) {
        if (status == null) return "-";
        switch (status) {
            case APPLYING:  return "待审核";
            case APPROVED:  return "待退款";
            case REJECTED:  return "已驳回";
            case REFUNDING: return "退款中";
            case REFUNDED:  return "已退款";
            case ORDER_NONE: return "无售后";
            default: return status;
        }
    }

    /** 售后类型 → 中文标签 */
    public static String typeLabel(String type) {
        if (type == null) return "-";
        switch (type) {
            case TYPE_REFUND_ONLY: return "仅退款";
            case TYPE_RETURN:      return "退货退款";
            default: return type;
        }
    }

    /** 单据状态是否为终态（已驳回 / 已退款）*/
    public static boolean isTerminal(String status) {
        return REJECTED.equals(status) || REFUNDED.equals(status);
    }
}
