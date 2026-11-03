package com.project.groupbuy.common.aspect;

import com.project.groupbuy.common.context.SecurityContext;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 操作日志注解（约束文档 10：所有敏感操作必须写 operation_log）
 * 标记在 Service 方法上，AOP 自动拦截写入 operation_log
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OpLog {

    /** 模块名：商品/库存/订单/支付/履约/售后/供应/权限/预测 */
    String module();

    /** 操作类型：CREATE / UPDATE / DELETE / AUDIT / ASSIGN / TRANSITION / CALLBACK */
    String operationType();

    /** 目标类型（可选） */
    String targetType() default "";

    /** 敏感参数脱敏（默认 true） */
    boolean desensitize() default true;
}
