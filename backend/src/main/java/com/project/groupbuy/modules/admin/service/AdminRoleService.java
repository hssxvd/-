package com.project.groupbuy.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.admin.dto.AdminRolePermissionRequest;
import com.project.groupbuy.modules.auth.entity.*;
import com.project.groupbuy.modules.auth.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.project.groupbuy.common.aspect.OpLog;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员端角色权限 Service
 *
 * 角色数量固定 5 个（种子数据）：不开放增删。
 * 权限调整 = role_permission 表的增删改。
 *
 * 约束文档合规：
 *   L610 核销员只分配 5 个权限（最小必要原则）
 *   L736 角色权限变更最小必要
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;

    /** 角色列表（固定 5 个） */
    public List<Map<String, Object>> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getIsDeleted, 0))
                .stream().map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("roleCode", r.getRoleCode());
                    m.put("roleName", r.getRoleName());
                    m.put("description", r.getDescription());
                    m.put("createTime", r.getCreateTime());
                    return m;
                }).collect(Collectors.toList());
    }

    /**
     * 角色详情 + 已分配权限（按 module 分组返回）
     * 前端权限矩阵直接用 groupedPerms 渲染
     */
    public Map<String, Object> detail(Long roleId) {
        Role r = roleMapper.selectById(roleId);
        if (r == null || r.getIsDeleted() == 1) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "角色不存在");

        // 已分配的 permission_id 集合
        Set<Long> assignedIds = rolePermissionMapper.selectList(
                        new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId))
                .stream().map(RolePermission::getPermissionId).collect(Collectors.toSet());

        // 全量权限（按 module 分组）
        List<Permission> allPerms = permissionMapper.selectList(
                new LambdaQueryWrapper<Permission>().eq(Permission::getIsDeleted, 0)
                        .orderByAsc(Permission::getModule).orderByAsc(Permission::getPermissionCode));

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (Permission p : allPerms) {
            String mod = p.getModule() == null ? "system" : p.getModule();
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("id", p.getId());
            pm.put("code", p.getPermissionCode());
            pm.put("name", p.getPermissionName());
            pm.put("checked", assignedIds.contains(p.getId()));
            grouped.computeIfAbsent(mod, k -> new ArrayList<>()).add(pm);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", r);
        result.put("assignedIds", assignedIds);
        result.put("groupedPerms", grouped);
        return result;
    }

    /**
     * 权限调整 —— 全量覆盖 role_permission
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePermissions(Long roleId, AdminRolePermissionRequest req) {
        Role r = roleMapper.selectById(roleId);
        if (r == null || r.getIsDeleted() == 1) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "角色不存在");

        // 先删后插
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleId, roleId));
        Set<Long> ids = req.getPermissionIds() == null ? Set.of() : new HashSet<>(req.getPermissionIds());
        for (Long pid : ids) {
            RolePermission rp = new RolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            rolePermissionMapper.insert(rp);
        }
        log.info("[AdminRole] roleId={} 权限更新: {} 条", roleId, ids.size());
    }

    /** 权限列表（供权限矩阵辅助，所有 module 的 code+name） */
    public List<Map<String, Object>> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<Permission>()
                        .eq(Permission::getIsDeleted, 0)
                        .orderByAsc(Permission::getModule).orderByAsc(Permission::getPermissionCode))
                .stream().map(p -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", p.getId());
                    m.put("code", p.getPermissionCode());
                    m.put("name", p.getPermissionName());
                    m.put("module", p.getModule());
                    return m;
                }).collect(Collectors.toList());
    }
}
