package com.project.groupbuy.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.groupbuy.modules.admin.vo.OperationLogVO;
import com.project.groupbuy.modules.auth.entity.OperationLog;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 操作日志翻译器：把机器码转成人类可读中文
 * 约束文档 29.1：禁止硬编码魔法值，所有翻译集中在一个工具类
 * <p>
 * 所有 Map 用 HashMap 包装 — Map.of / Map.ofEntries 产生的不可变 Map
 * 拒绝 null key 查询（get(null) 会 NPE），而历史日志里 roleCode /
 * operationType / targetType / beforeStatus / afterStatus 都可能为 null。
 */
public final class OpLogTranslator {

    private OpLogTranslator() {}

    // ===== 操作类型 → 中文 + emoji =====
    private static final Map<String, String> OP_LABEL = hashMap(
            "CREATE",     "➕ 创建",
            "UPDATE",     "✏️ 更新",
            "DELETE",     "🗑️ 删除",
            "TRANSITION", "🔄 状态变更",
            "AUDIT",      "📝 审核",
            "CALLBACK",   "📡 回调",
            "REFUND",     "💰 退款",
            "ASSIGN",     "📋 分配",
            "LOGIN",      "🔐 登录",
            "LOGOUT",     "🚪 登出",
            "RESET",      "🔁 重置"
    );

    // ===== 目标类型 → 中文实体名 =====
    private static final Map<String, String> TARGET_LABEL = hashMap(
            "SupplyPlan",   "供应计划",
            "Stock",        "库存记录",
            "Orders",       "订单",
            "Order",        "订单",
            "OrderItem",    "订单明细",
            "Fulfillment",  "履约单",
            "Aftersale",    "售后单",
            "Product",      "商品",
            "ProductSku",   "SKU",
            "Permission",   "权限",
            "Role",         "角色",
            "User",         "用户",
            "Community",    "社区",
            "PickupPoint",  "自提点",
            "ForecastTask", "预测任务"
    );

    // ===== 角色编码 → 中文 =====
    private static final Map<String, String> ROLE_LABEL = hashMap(
            "CONSUMER",     "消费者",
            "MERCHANT",     "商家",
            "PICKUP_STAFF", "核销员",
            "ADMIN",        "管理员",
            "SUPER_ADMIN",  "超级管理员"
    );

    // ===== 通用状态码 → 中文（跨模块复用）=====
    private static final Map<String, String> STATUS_LABEL = hashMap(
            // SupplyPlan
            "DRAFT",     "草稿",
            "CONFIRMED", "已确认",
            "ARRIVED",   "已到货",
            "CANCELLED", "已取消",
            // Orders
            "PENDING_PAY",    "待支付",
            "PAID",           "已支付",
            "PROCESSING",     "处理中",
            "PENDING_PICKUP", "待自提",
            "COMPLETED",      "已完成",
            "REFUNDING",      "退款中",
            "REFUNDED",       "已退款",
            // Aftersale
            "APPLYING", "待审核",
            "APPROVED", "已通过",
            "REJECTED", "已驳回",
            // Stock
            "NORMAL", "正常",
            "WARN",   "预警",
            "EMPTY",  "空仓",
            // 通用
            "SUCCESS", "成功",
            "FAIL",    "失败"
    );

    // ===== 操作类型对应的 tag type 颜色 =====
    private static final Map<String, String> OP_TYPE_TAG = hashMap(
            "CREATE",     "primary",
            "UPDATE",     "warning",
            "DELETE",     "danger",
            "TRANSITION", "",
            "AUDIT",      "success",
            "CALLBACK",   "info",
            "REFUND",     "danger",
            "ASSIGN",     "warning",
            "LOGIN",      "info",
            "LOGOUT",     "info",
            "RESET",      "danger"
    );

    private static final Map<String, String> KEY_CN = hashMap(
            "skuId",      "SKU",
            "sku_id",     "SKU",
            "communityId",  "社区",
            "community_id", "社区",
            "planQty",    "计划量",
            "plan_qty",   "计划量",
            "qty",        "数量",
            "targetId",   "目标ID",
            "target_id",  "目标ID",
            "stockId",    "库存ID",
            "stock_id",   "库存ID",
            "orderId",    "订单ID",
            "order_id",   "订单ID"
    );

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper OM = new ObjectMapper();

    /**
     * 翻译单条日志
     *
     * @param log         原始日志实体（DB 层）
     * @param userIdToName 用户 ID → 用户名 映射（Service 层批量查询后传入）
     * @return 全中文可读的 VO
     */
    public static OperationLogVO translate(OperationLog log,
                                            Map<Long, String> userIdToName) {
        if (log == null) return null;

        OperationLogVO vo = new OperationLogVO();
        vo.setId(log.getId());
        vo.setModule(log.getModule());
        vo.setCreateTime(log.getCreateTime());
        vo.setIp(log.getIp());
        vo.setTraceId(log.getTraceId());

        // === 操作人 ===
        Long uid = log.getUserId();
        if (uid == null) {
            vo.setOperatorName("系统");
            vo.setOperatorRole("—");
        } else {
            vo.setOperatorName(userIdToName == null ? "u#" + uid
                    : userIdToName.getOrDefault(uid, "u#" + uid));
            vo.setOperatorRole(safeGet(ROLE_LABEL, log.getRoleCode(), ""));
        }

        // === 操作类型 ===
        String opType = log.getOperationType();
        vo.setActionLabel(safeGet(OP_LABEL, opType, opType));

        // === 目标对象 ===
        String targetType = log.getTargetType();
        String targetCn = safeGet(TARGET_LABEL, targetType, targetType);
        if (log.getTargetId() != null) {
            vo.setTargetDesc(targetCn + " #" + log.getTargetId());
        } else {
            vo.setTargetDesc(targetCn == null ? "" : targetCn);
        }

        // === 前后状态 ===
        vo.setBeforeLabel(safeGet(STATUS_LABEL, log.getBeforeStatus(), log.getBeforeStatus()));
        vo.setAfterLabel(safeGet(STATUS_LABEL, log.getAfterStatus(), log.getAfterStatus()));

        // === 结果 ===
        if ("SUCCESS".equals(log.getResult())) {
            vo.setResultLabel("✅ 成功");
            vo.setResultClass("success");
        } else if ("FAIL".equals(log.getResult())) {
            vo.setResultLabel("❌ 失败");
            vo.setResultClass("danger");
        } else {
            vo.setResultLabel(log.getResult() == null ? "" : log.getResult());
            vo.setResultClass("info");
        }

        // === 一句话摘要 ===
        vo.setSummary(buildSummary(vo, log));

        // === 详情提示 ===
        vo.setDetailHint(parseParamsHint(log.getRequestParams()));
        vo.setRawParams(log.getRequestParams());

        // === 错误 ===
        vo.setErrorMsg(log.getErrorMsg());

        return vo;
    }

    /** 给前端用的 tag type 映射 */
    public static String opTagType(String opType) {
        return safeGet(OP_TYPE_TAG, opType, "info");
    }

    // === 内部工具 ===

    /**
     * 安全查询 Map — key 为 null 时直接返回 defaultValue（避免 ImmutableCollections NPE）
     */
    private static <K, V> V safeGet(Map<K, V> map, K key, V defaultValue) {
        if (key == null) return defaultValue;
        V v = map.get(key);
        return v != null ? v : defaultValue;
    }

    /**
     * 用 varargs 构造 HashMap（成对出现：key, value, key, value, ...）
     * 不使用 Map.of / Map.ofEntries — 它们的不可变 Map 不接受 null key 查询。
     */
    private static Map<String, String> hashMap(String... kvPairs) {
        if (kvPairs.length % 2 != 0) {
            throw new IllegalArgumentException("kvPairs 必须是偶数个（key,value 成对）");
        }
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            m.put(kvPairs[i], kvPairs[i + 1]);
        }
        return m;
    }

    private static String buildSummary(OperationLogVO vo, OperationLog log) {
        StringBuilder sb = new StringBuilder();
        sb.append(vo.getOperatorName())
          .append(" 对 ")
          .append(vo.getTargetDesc() == null || vo.getTargetDesc().isBlank()
                  ? "某对象" : vo.getTargetDesc())
          .append(" 执行了 ")
          .append(vo.getActionLabel() == null ? "未知操作" : vo.getActionLabel());

        // 状态变更类 + 有前后状态时补充
        if (vo.getBeforeLabel() != null && vo.getAfterLabel() != null
                && !vo.getBeforeLabel().equals(vo.getAfterLabel())) {
            sb.append("（").append(vo.getBeforeLabel())
              .append(" → ").append(vo.getAfterLabel()).append("）");
        }
        return sb.toString();
    }

    /**
     * 从 request_params 里提取人话提示
     * 纯数字数组跳过（[39, null] 这种没意义）
     */
    private static String parseParamsHint(String params) {
        if (params == null || params.isBlank()) return null;

        // 纯数字 + null 数组 → 没意义，跳过
        if (params.matches("^\\[\\d+(,\\s*null)*\\]$")) {
            return null;
        }
        if (params.matches("^\\[null(,\\s*null)*\\]$")) {
            return null;
        }

        try {
            String hint = extractKeyInfo(params);
            if (hint != null && !hint.isBlank()) return hint;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 用正则提取常见业务参数：skuId=?, communityId=?, qty=? */
    private static String extractKeyInfo(String json) {
        StringBuilder sb = new StringBuilder();
        Pattern p = Pattern.compile(
                "\"(skuId|sku_id|communityId|community_id|planQty|plan_qty" +
                "|qty|targetId|target_id|stockId|stock_id|orderId|order_id)\"" +
                "\\s*:\\s*\"?([^,}\\]]+)\"?");
        Matcher m = p.matcher(json);
        while (m.find()) {
            String key = m.group(1);
            String val = m.group(2).trim();
            if (val.isEmpty() || "null".equalsIgnoreCase(val)) continue;
            String cnKey = safeGet(KEY_CN, key, key);
            if (sb.length() > 0) sb.append(" · ");
            sb.append(cnKey).append("=").append(val);
        }
        return sb.toString();
    }
}
