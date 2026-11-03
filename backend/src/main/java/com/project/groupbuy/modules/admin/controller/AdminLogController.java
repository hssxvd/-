package com.project.groupbuy.modules.admin.controller;

import lombok.extern.slf4j.Slf4j;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.groupbuy.common.annotation.RequirePermission;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.admin.service.AdminLogService;
import com.project.groupbuy.modules.admin.vo.OperationLogVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理员端 · 操作日志查询
 * 约束文档 10：审计留痕 + 约束 8 后端强制权限
 * 约束文档 2.1：返回 VO（全中文可读），不直出 Entity
 */
@Slf4j
@RestController
@RequestMapping("/admin/logs")
public class AdminLogController {

    private final AdminLogService adminLogService;

    public AdminLogController(AdminLogService adminLogService) {
        this.adminLogService = adminLogService;
    }

    @GetMapping
    @RequirePermission("log:view")
    public R<IPage<OperationLogVO>> list(
            @RequestParam(defaultValue = "1")  int pageNum,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String targetType,
            @RequestParam(required = false) Long targetId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return R.ok(adminLogService.page(pageNum, pageSize, module, operationType, result, userId,
                targetType, targetId, keyword, startTime, endTime));
    }

    /** 详情 —— 返回全中文可读版本 */
    @GetMapping("/{id:\\d+}")
    @RequirePermission("log:view")
    public R<OperationLogVO> detail(@PathVariable Long id) {
        return R.ok(adminLogService.getTranslatedById(id));
    }

    /** 模块下拉选项 */
    @GetMapping("/modules")
    @RequirePermission("log:view")
    public R<List<String>> modules() {
        return R.ok(adminLogService.listDistinctModules());
    }

    /**
     * 最近 7 天统计（顶部 4 个卡片用）
     * 返回 Map：periodDays / total / failedCount / uniqueUsers / auditCount / transitionCount
     */
    @GetMapping("/stats-today")
    @RequirePermission("log:view")
    public R<Map<String, Object>> statsToday() {
        return R.ok(adminLogService.statsToday());
    }
}
