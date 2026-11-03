package com.project.groupbuy.common.constant;

/**
 * 库存预警状态常量（约束文档 6 库存 + 11 统计口径统一 · v1.13 库存可视化）
 *
 * 判定口径（后端统一计算，前端禁止二次计算，见约束文档 11）：
 *   WARN   预警   warn_qty 不为空 且 available_qty <= warn_qty
 *   EMPTY  空仓   warn_qty 为空   且 available_qty = 0
 *   NORMAL 正常   其余情况
 *
 * 注意：预警状态是"是否触发安全阈值"的单一口径，不包含预占维度。
 * "锁定中（locked_qty > 0）"是列表的独立筛选维度（StockListQueryRequest.lockedOnly），
 * 与总览统计的 locked_count 口径一致，二者不可混为一谈。
 *
 * StockMapper.pageMineForMerchant / overviewForMerchant 的 CASE 表达式
 * 直接引用本类常量拼接，禁止在 SQL / Service / 前端组件里写裸字符串。
 */
public final class StockWarnStatusConstant {

    private StockWarnStatusConstant() {}

    /** 预警：低于安全阈值 */
    public static final String WARN   = "WARN";
    /** 空仓：无预警阈值且可用为 0 */
    public static final String EMPTY  = "EMPTY";
    /** 正常 */
    public static final String NORMAL = "NORMAL";

    /** 中文标签 */
    public static String label(String status) {
        if (status == null) return "-";
        switch (status) {
            case WARN:   return "预警";
            case EMPTY:  return "空仓";
            case NORMAL: return "正常";
            default: return status;
        }
    }
}
