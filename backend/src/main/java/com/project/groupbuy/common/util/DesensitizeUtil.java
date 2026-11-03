package com.project.groupbuy.common.util;

/**
 * 脱敏工具（约束文档 10 / 14）
 */
public class DesensitizeUtil {

    /**
     * 手机号脱敏：138****1001
     */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /**
     * 身份证脱敏：3101********1234
     */
    public static String idCard(String id) {
        if (id == null || id.length() < 8) return id;
        return id.substring(0, 4) + "********" + id.substring(id.length() - 4);
    }

    /**
     * token 脱敏：只保留前 8 位
     */
    public static String token(String token) {
        if (token == null) return null;
        return token.substring(0, Math.min(8, token.length())) + "...";
    }

    /**
     * 姓名脱敏：张*
     */
    public static String name(String name) {
        if (name == null || name.length() <= 1) return name;
        return name.charAt(0) + "*" + (name.length() > 2 ? name.substring(name.length() - 1) : "");
    }
}
