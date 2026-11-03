package com.project.groupbuy.common.constant;

/**
 * 订单状态常量（约束文档 5.2 订单状态机 · v1.13 重构）
 *
 * 禁止在 Service/Controller 里直接写字符串 "PENDING_PAY"/"COMPLETED" 等，
 * 必须 import 本类常量。
 *
 * 状态机完整流转（v1.13 统一为 PROCESSING 替代语义空洞的 FULFILLING）：
 *   PENDING_PAY → PAID → PROCESSING → PENDING_PICKUP → COMPLETED（自提支线）
 *                        └→ COMPLETED（配送支线）
 *   PENDING_PAY / PAID / PROCESSING / PENDING_PICKUP → CANCELLED
 *
 * 【v1.14 售后联动】全额退款支线（约束 5.5 + 5.2）：
 *   订单原状态为 PAID / PROCESSING / PENDING_PICKUP 且全额退款 → REFUNDING → REFUNDED
 *   订单原状态为 COMPLETED 的售后 → 保持 COMPLETED（售后进度由 orders.aftersale_status 表达）
 *   部分退款不改变 order_status
 *   REFUNDING / REFUNDED 为终态，禁止再取消 / 履约 / 评价
 *
 * 【v1.13 兼容说明】FULFILLING 已废弃（语义模糊，商家/消费者看到"履约中"不知所云），
 *   统一用 PROCESSING（处理中）。DB 历史值 FULFILLING 已通过 Flyway V4 迁移为 PROCESSING。
 *   label() 仍兼容 FULFILLING 旧值（返回"处理中"），防止边缘 case 标签错位。
 */
public final class OrderStatusConstant {

    private OrderStatusConstant() {}

    /** 待支付 */
    public static final String PENDING_PAY  = "PENDING_PAY";
    /** 已支付 */
    public static final String PAID         = "PAID";
    /** 处理中（v1.13 替代 FULFILLING：商家已分配履约，正在备货/发货/运输）*/
    public static final String PROCESSING   = "PROCESSING";
    /** 废弃：履约中 → 请改用 PROCESSING（仅用于兼容 DB 历史值，业务代码禁止使用）*/
    @Deprecated
    public static final String FULFILLING   = "FULFILLING";
    /** 待自提（货到自提点）*/
    public static final String PENDING_PICKUP = "PENDING_PICKUP";
    /** 已完成 */
    public static final String COMPLETED    = "COMPLETED";
    /** 已取消 */
    public static final String CANCELLED    = "CANCELLED";
    /** 管理员手动拆单后的原订单终态 */
    public static final String SPLIT_SOURCE = "SPLIT_SOURCE";
    /** 退款中（v1.14 售后：全额退款，约束 5.2）*/
    public static final String REFUNDING    = "REFUNDING";
    /** 已退款（v1.14 售后：全额退款完成，终态，约束 5.2）*/
    public static final String REFUNDED     = "REFUNDED";

    /** 角色类型 → 中文标签（约束 29 禁止前端硬编码）*/
    public static String label(String status) {
        if (status == null) return "-";
        switch (status) {
            case PENDING_PAY:     return "待支付";
            case PAID:            return "待处理";   // v1.13 改：暗示商家还没干活
            case PROCESSING:
            case FULFILLING:      return "处理中";   // v1.13 统一：FULFILLING 旧值也显示"处理中"
            case PENDING_PICKUP:  return "待自提";
            case COMPLETED:       return "已完成";
            case CANCELLED:       return "已取消";
        case SPLIT_SOURCE:   return "已拆单";
            case REFUNDING:       return "退款中";
            case REFUNDED:        return "已退款";
            default: return status;
        }
    }

    /**
     * 终态判定（v1.14）：已取消 / 退款中 / 已退款
     * 终态订单禁止再取消、再履约、再评价、再申请售后（约束 5.2 + 5.5）
     */
    public static boolean isTerminal(String status) {
        return CANCELLED.equals(status) || REFUNDING.equals(status) || REFUNDED.equals(status) || SPLIT_SOURCE.equals(status);
    }

    /**
     * 已支付口径（约束 11 统计口径统一）：PAID / PROCESSING / PENDING_PICKUP / COMPLETED / REFUNDING / REFUNDED
     * 即"钱已收"的订单集合，GMV / 订单量 / 履约率分母统一引用本方法，禁止各处写裸串。
     */
    public static String[] paidStatuses() {
        return new String[]{PAID, PROCESSING, PENDING_PICKUP, COMPLETED, REFUNDING, REFUNDED};
    }
}
