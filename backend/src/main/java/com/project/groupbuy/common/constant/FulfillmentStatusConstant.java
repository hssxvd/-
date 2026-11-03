package com.project.groupbuy.common.constant;

/**
 * 履约状态常量（约束文档 5.4 履约状态机）
 *
 * 禁止在 Service 里直接写 "PENDING_PICKUP"/"DELIVERING" 等字符串，
 * 必须 import 本类常量。
 */
public final class FulfillmentStatusConstant {

    private FulfillmentStatusConstant() {}

    /** 待分配 */
    public static final String PENDING_ASSIGN = "PENDING_ASSIGN";
    /** 待自提（备货完成，已到自提点）*/
    public static final String PENDING_PICKUP = "PENDING_PICKUP";
    /** 配送中 */
    public static final String DELIVERING     = "DELIVERING";
    /** 已送达 */
    public static final String DELIVERED      = "DELIVERED";
    /** 已自提 */
    public static final String PICKED_UP      = "PICKED_UP";
    /** 已完成 */
    public static final String COMPLETED      = "COMPLETED";
    /** 异常 */
    public static final String EXCEPTION      = "EXCEPTION";

    /** 履约类型 */
    public static final String TYPE_PICKUP   = "PICKUP";
    public static final String TYPE_DELIVERY = "DELIVERY";

    /** 中文标签映射 */
    public static String label(String status) {
        if (status == null) return "-";
        switch (status) {
            case PENDING_ASSIGN: return "待分配";
            case PENDING_PICKUP: return "待自提";
            case DELIVERING:     return "配送中";
            case DELIVERED:      return "已送达";
            case PICKED_UP:      return "已自提";
            case COMPLETED:      return "已完成";
            case EXCEPTION:      return "异常";
            default: return status;
        }
    }
}
