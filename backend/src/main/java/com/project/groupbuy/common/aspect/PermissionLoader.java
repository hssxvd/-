package com.project.groupbuy.common.aspect;

import com.project.groupbuy.modules.auth.mapper.PermQueryMapper;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 权限加载器 —— 根据 userId 查 role + permission 集合
 */
@Component
public class PermissionLoader {

    private final PermQueryMapper mapper;

    public PermissionLoader(PermQueryMapper mapper) {
        this.mapper = mapper;
    }

    public Set<String> loadRoles(Long userId) {
        if (userId == null) return Set.of();
        return new HashSet<>(mapper.findRoleCodesByUserId(userId));
    }

    public Set<String> loadPermissions(Long userId) {
        if (userId == null) return Set.of();
        return new HashSet<>(mapper.findPermissionCodesByUserId(userId));
    }
}