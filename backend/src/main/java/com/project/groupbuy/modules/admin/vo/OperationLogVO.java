package com.project.groupbuy.modules.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作日志 VO（返回给前端的全中文可读版本）
 * 约束文档 2.1：DTO 对外、Entity 不直出
 */
@Data
public class OperationLogVO {
    private Long id;
    private String module;               // 模块中文名（商品/库存/供应/订单/售后/权限）

    // ===== 操作人 =====
    private String operatorName;         // 用户名，如 "张老板"
    private String operatorRole;         // 角色中文名，如 "商家" / "管理员" / "核销员" / "消费者" / "系统"

    // ===== 操作（已翻译）=====
    private String actionLabel;          // 人类可读："➕ 创建" / "🔄 状态变更" / "✏️ 更新"

    // ===== 目标对象（已翻译）=====
    private String targetDesc;           // "供应计划 #39" / "商品 #1" 等

    // ===== 状态变更（已翻译）=====
    private String beforeLabel;          // "草稿" / "已确认" / null（无变更时）
    private String afterLabel;           // "已确认" / "已到货" / null

    // ===== 结果 =====
    private String resultLabel;          // "✅ 成功" / "❌ 失败"
    private String resultClass;          // "success" / "danger"（给前端 tag type 用）

    // ===== 一句话摘要（最重要）=====
    private String summary;              // "张老板 对 供应计划 #39 执行了 状态变更（草稿 → 已确认）"

    // ===== 详情 =====
    private String detailHint;           // 从 request_params 解析的人话提示，可 null
    private String rawParams;            // 原始 request_params JSON（技术排障用，折叠显示）
    private String errorMsg;             // 失败时的错误信息

    // ===== 审计 =====
    private String ip;
    private String traceId;
    private LocalDateTime createTime;
}
