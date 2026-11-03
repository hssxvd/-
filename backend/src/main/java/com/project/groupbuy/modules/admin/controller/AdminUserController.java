package com.project.groupbuy.modules.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.groupbuy.common.annotation.RequirePermission;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.admin.dto.AdminUserCreateRequest;
import com.project.groupbuy.modules.admin.dto.AdminUserRoleRequest;
import com.project.groupbuy.modules.admin.dto.AdminUserUpdateRequest;
import com.project.groupbuy.modules.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

/**
 * 管理员端用户管理
 *
 * 约束文档合规：
 *   L602 RBAC
 *   L611 后端必须校验，不能只靠前端隐藏
 *   L736 权限变更最小必要
 */
@Slf4j
@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 分页查询 */
    @GetMapping
    @RequirePermission("user:view")
    public R<IPage<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword) {
        return R.ok(adminUserService.page(pageNum, pageSize, userType, status, keyword));
    }

    /** 详情 */
    @GetMapping("/{id}")
    @RequirePermission("user:view")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(adminUserService.detail(id));
    }

    /** 创建 */
    @PostMapping
    @RequirePermission("user:add")
    public R<Long> create(@Valid @RequestBody AdminUserCreateRequest req) {
        return R.ok(adminUserService.create(req));
    }

    /** 编辑（不含密码） */
    @PutMapping("/{id}")
    @RequirePermission("user:edit")
    public R<Void> update(@PathVariable Long id, @Valid @RequestBody AdminUserUpdateRequest req) {
        adminUserService.update(id, req);
        return R.ok();
    }

    /** 启用/禁用 */
    @PutMapping("/{id}/status")
    @RequirePermission("user:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam String status) {
        adminUserService.updateStatus(id, status);
        return R.ok();
    }

    /** 角色绑定调整 */
    @PutMapping("/{id}/roles")
    @RequirePermission("role:edit")
    public R<Void> updateRoles(@PathVariable Long id, @RequestBody AdminUserRoleRequest req) {
        adminUserService.updateRoles(id, req);
        return R.ok();
    }

    /** 重置密码（返回临时 8 位随机码，仅本次显示） */
    @PostMapping("/{id}/reset-password")
    @RequirePermission(value = "user:delete", requireAdmin = true)
    public R<Map<String, String>> resetPassword(@PathVariable Long id) {
        String random = adminUserService.resetPassword(id);
        return R.ok(Map.of("newPassword", random));
    }

    /** 辅助：角色下拉（创建/编辑时前端需要） */
    @GetMapping("/roles/options")
    @RequirePermission("user:view")
    public R<List<Map<String, Object>>> roleOptions() {
        return R.ok(adminUserService.listRoles());
    }
}
