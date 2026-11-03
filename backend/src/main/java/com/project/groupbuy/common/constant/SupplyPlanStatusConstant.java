package com.project.groupbuy.common.constant;

/**
 * 供应计划状态常量（约束文档 6.1 供应与库存协同 · v1.13 重构）
 *
 * 状态机完整流转：
 *   DRAFT → CONFIRMED → ARRIVED（自动驱动库存 SUPPLY_IN）
 *   DRAFT → CANCELLED（草稿取消，不动库存）
 *   CONFIRMED → CANCELLED（已确认取消，不动库存）
 *   ARRIVED → CANCELLED（已到货取消，必须 ROLLBACK 回滚库存）
 *
 * 禁止在 Service/Controller 里直接写字符串 "DRAFT"/"CONFIRMED"/"ARRIVED"/"CANCELLED"，
 * 必须 import 本类常量。
 */
public final class SupplyPlanStatusConstant {

    private SupplyPlanStatusConstant() {}

    /** 草稿（商家刚创建，还没确认）*/
    public static final String DRAFT     = "DRAFT";
    /** 已确认（商家已确认，等待到货）*/
    public static final String CONFIRMED = "CONFIRMED";
    /** 已到货（自动触发库存 SUPPLY_IN）*/
    public static final String ARRIVED   = "ARRIVED";
    /** 已取消 */
    public static final String CANCELLED  = "CANCELLED";

    /** 是否可以执行某动作的守卫 */
    public static boolean canConfirm(String status) {
        return DRAFT.equals(status);
    }
    public static boolean canArrive(String status) {
        return CONFIRMED.equals(status);
    }
    public static boolean canCancel(String status) {
        return DRAFT.equals(status) || CONFIRMED.equals(status) || ARRIVED.equals(status);
    }
    /** ARRIVED 状态取消需要回滚库存 */
    public static boolean needRollbackOnCancel(String status) {
        return ARRIVED.equals(status);
    }
    /** 可调整状态：DRAFT / CONFIRMED 都允许调整 */
    public static boolean canAdjust(String status) {
        return DRAFT.equals(status) || CONFIRMED.equals(status);
    }

    /** 状态 → 中文标签（约束 29 禁止前端硬编码）*/
    public static String label(String status) {
        if (status == null) return "-";
        switch (status) {
            case DRAFT:     return "草稿";
            case CONFIRMED: return "已确认";
            case ARRIVED:   return "已到货";
            case CANCELLED: return "已取消";
            default: return status;
        }
    }
}
