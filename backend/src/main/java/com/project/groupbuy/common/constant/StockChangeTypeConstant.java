package com.project.groupbuy.common.constant;

/**
 * 库存流水变更类型常量（约束文档 6 · 六种变更类型 · v1.13 重构）
 *
 * 每种类型的含义与影响：
 *   LOCK       下单预占      locked_qty  += qty
 *   UNLOCK     释放预占      locked_qty  -= qty
 *   DEDUCT     支付实扣      locked_qty -= qty, total_qty -= qty
 *   ROLLBACK   异常回滚      total_qty   += qty, available_qty += qty
 *   INBOUND    人工入库      total_qty   += qty, available_qty += qty
 *   SUPPLY_IN  供应到货入库  total_qty   += qty, available_qty += qty（约束 6.1）
 *
 * 禁止在 StockService / SupplyPlanService 里写 "LOCK"/"SUPPLY_IN" 等裸字符串，
 * 必须 import 本类常量。
 */
public final class StockChangeTypeConstant {

    private StockChangeTypeConstant() {}

    /** 预占（下单时）*/
    public static final String LOCK       = "LOCK";
    /** 释放预占（取消/超时）*/
    public static final String UNLOCK     = "UNLOCK";
    /** 实扣（支付后）*/
    public static final String DEDUCT     = "DEDUCT";
    /** 回滚（异常回滚、供应到货后计划取消等）*/
    public static final String ROLLBACK   = "ROLLBACK";
    /** 人工入库/调拨 */
    public static final String INBOUND    = "INBOUND";
    /** 供应到货入库（约束文档 6.1）*/
    public static final String SUPPLY_IN  = "SUPPLY_IN";

    /** 中文标签 */
    public static String label(String type) {
        if (type == null) return "-";
        switch (type) {
            case LOCK:      return "预占";
            case UNLOCK:    return "释放预占";
            case DEDUCT:    return "实扣";
            case ROLLBACK:  return "回滚";
            case INBOUND:   return "人工入库";
            case SUPPLY_IN: return "供应到货";
            default: return type;
        }
    }
}
