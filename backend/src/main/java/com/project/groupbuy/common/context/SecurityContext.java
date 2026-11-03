package com.project.groupbuy.common.context;

/**
 * 安全上下文：存储当前登录用户信息（约束文档 8）
 * 使用 ThreadLocal，请求结束后必须清理
 *
 * V6 新增 merchantId / pickupPointId（多商家 + 自提点核销员角色绑定）
 */
public class SecurityContext {

    private static final ThreadLocal<SecurityUser> CONTEXT = new ThreadLocal<>();

    /** 老接口兼容 —— 内部会查 DB 补 merchantId/pickupPointId */
    public static void set(Long userId, String userType) {
        CONTEXT.set(new SecurityUser(userId, userType, null, null));
    }

    /** V6 新接口 —— 登录时已知 merchantId/pickupPointId 就直接塞 */
    public static void set(Long userId, String userType, Long merchantId, Long pickupPointId) {
        CONTEXT.set(new SecurityUser(userId, userType, merchantId, pickupPointId));
    }

    public static SecurityUser get() {
        return CONTEXT.get();
    }

    public static Long getUserId() {
        SecurityUser u = CONTEXT.get();
        return u == null ? null : u.userId;
    }

    public static String getUserType() {
        SecurityUser u = CONTEXT.get();
        return u == null ? null : u.userType;
    }

    /** V6 新：当前登录商家关联的 merchant_id（MERCHANT 角色专用）*/
    public static Long getMerchantId() {
        SecurityUser u = CONTEXT.get();
        return u == null ? null : u.merchantId;
    }

    /** V5 新：当前核销员绑定的 pickup_point_id（PICKUP_STAFF 角色专用）*/
    public static Long getPickupPointId() {
        SecurityUser u = CONTEXT.get();
        return u == null ? null : u.pickupPointId;
    }

    public static boolean isLoggedIn() {
        return CONTEXT.get() != null;
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static class SecurityUser {
        public final Long userId;
        public final String userType;
        /** V6 商家绑定：仅 MERCHANT 角色有值，其他为 null */
        public final Long merchantId;
        /** V5 自提点绑定：仅 PICKUP_STAFF 角色有值，其他为 null */
        public final Long pickupPointId;

        public SecurityUser(Long userId, String userType, Long merchantId, Long pickupPointId) {
            this.userId = userId;
            this.userType = userType;
            this.merchantId = merchantId;
            this.pickupPointId = pickupPointId;
        }
    }
}
