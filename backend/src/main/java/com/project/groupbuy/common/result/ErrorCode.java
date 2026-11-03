package com.project.groupbuy.common.result;

/**
 * 错误码枚举（约束文档 7.1）
 * 业务错误码统一定义，禁止硬编码字符串
 */
public enum ErrorCode {

    SUCCESS(0, "success"),

    // ===== 通用错误 1xxx =====
    PARAM_ERROR(1001, "参数错误"),
    UNAUTHORIZED(401, "未登录或 token 过期"),
    FORBIDDEN(403, "无权限"),
    DATA_NOT_FOUND(1004, "数据不存在"),
    DATA_CONFLICT(1005, "数据冲突"),

    // ===== 业务错误 2xxx =====
    // 库存
    STOCK_NOT_ENOUGH(2001, "库存不足"),
    STOCK_LOCK_FAILED(2002, "库存预占失败"),
    STOCK_ROLLBACK_FAILED(2003, "库存回滚失败"),

    // 订单
    ORDER_INVALID_STATUS(2101, "订单状态不允许此操作"),
    ORDER_HAS_PRODUCT_SOLD_OUT(2102, "商品已下架或库存不足"),

    // 支付
    PAY_ALREADY_PAID(2201, "订单已支付"),
    PAY_TIMEOUT(2202, "支付超时"),
    PAY_DUPLICATE(2203, "重复支付回调"),
    REFUND_EXCEED(2204, "退款金额超过实付金额"),

    // 商品
    PRODUCT_OFF_SHELF(2301, "商品已下架"),
    PRODUCT_HAS_ORDER(2302, "商品有未完成订单，无法下架"),
    PRODUCT_NOT_FOUND(2303, "商品不存在"),

    // 供应计划
    SUPPLY_PLAN_INVALID(2401, "供应计划状态不允许此操作"),
    SUPPLY_PLAN_NOT_FOUND(2402, "供应计划不存在"),
    SUPPLY_PLAN_DUPLICATE(2403, "同一SKU同一社区同日已存在供应计划"),

    // 售后
    AFTERSALE_INVALID(2501, "售后状态不允许此操作"),
    AFTERSALE_COUNT_EXCEED(2502, "同一订单同一明细售后次数已达上限"),
    AFTERSALE_NOT_FOUND(2503, "售后单不存在"),
    AFTERSALE_AMOUNT_INVALID(2504, "售后申请金额非法"),
    AFTERSALE_TYPE_INVALID(2505, "售后类型非法"),

    // 预测
    FORECAST_INVALID(2601, "预测任务状态不允许此操作"),

    // ===== 系统错误 5xxx =====
    SYSTEM_ERROR(5000, "系统异常"),
    DB_ERROR(5001, "数据库异常");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
