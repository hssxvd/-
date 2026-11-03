package com.project.groupbuy.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 权限注解 —— 方法级 RBAC 权限校验
 *
 * 约束文档 L602 / L611 / L749：
 *   - 禁止仅靠前端隐藏菜单实现权限控制
 *   - 后端 Service 层必须校验角色权限 + 数据范围
 *   - 越权返回统一 403，不得泄露资源是否存在
 *
 * 用法：
 *   @RequirePermission("user:view")    —— 单一权限
 *   @RequirePermission({"user:view", "user:add"})  —— 任一满足
 *   @RequirePermission(value = "role:edit", requireAdmin = true)  —— 必须 ADMIN 身份
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequirePermission {
    /** 需要的权限码，多个 OR */
    String[] value();

    /** 是否要求 ADMIN 身份（user_type = ADMIN），默认 false */
    boolean requireAdmin() default false;
}
