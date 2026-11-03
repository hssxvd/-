package com.project.groupbuy.common.constant;

/**
 * 用户角色常量（约束文档 3.1 用户角色映射）
 *
 * 禁止在 Service/Security/前端里直接写 "PICKUP_STAFF"/"MERCHANT" 等字符串，
 * 必须 import 本类常量。
 *
 * user_type 合法值共 5 个（user 表的 user_type 字段）。
 */
public final class UserTypeConstant {

    private UserTypeConstant() {}

    public static final String CONSUMER    = "CONSUMER";
    public static final String MERCHANT    = "MERCHANT";
    public static final String PICKUP_STAFF = "PICKUP_STAFF";
    public static final String ADMIN       = "ADMIN";
    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    /** 判断是否管理员（ADMIN / SUPER_ADMIN）*/
    public static boolean isAdmin(String t) {
        return ADMIN.equals(t) || SUPER_ADMIN.equals(t);
    }

    /** 中文标签映射 */
    public static String label(String t) {
        if (t == null) return "-";
        switch (t) {
            case CONSUMER:     return "消费者";
            case MERCHANT:     return "商家";
            case PICKUP_STAFF: return "核销员";
            case ADMIN:
            case SUPER_ADMIN:  return "管理员";
            default: return t;
        }
    }
}
