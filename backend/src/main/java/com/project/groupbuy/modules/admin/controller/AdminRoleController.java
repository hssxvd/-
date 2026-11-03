package com.project.groupbuy.modules.admin.controller;

import com.project.groupbuy.common.annotation.RequirePermission;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.admin.dto.AdminRolePermissionRequest;
import com.project.groupbuy.modules.admin.service.AdminRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 管理员端角色权限管理
 */
@Slf4j
@RestController
@RequestMapping("/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    /** 角色列表（固定 5 个种子） */
    @GetMapping
    @RequirePermission("role:view")
    public R<List<Map<String, Object>>> list() {
        return R.ok(adminRoleService.list());
    }

    /** 角色详情 + 权限矩阵（按 module 分组） */
    @GetMapping("/{id}")
    @RequirePermission("role:view")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(adminRoleService.detail(id));
    }

    /** 权限全量覆盖（勾选式保存） */
    @PutMapping("/{id}/permissions")
    @RequirePermission(value = "role:edit", requireAdmin = true)
    public R<Void> updatePermissions(@PathVariable Long id,
                                     @Valid @RequestBody AdminRolePermissionRequest req) {
        adminRoleService.updatePermissions(id, req);
        return R.ok();
    }
}
