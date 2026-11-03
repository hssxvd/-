package com.project.groupbuy.modules.auth.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 权限加载辅助 Mapper（供 PermissionLoader 使用）
 * 约束文档 L602 RBAC 用户-角色-权限查询
 */
@Mapper
public interface PermQueryMapper {
    @Select("SELECT r.role_code FROM user_role ur " +
            "JOIN role r ON ur.role_id = r.id AND r.is_deleted = 0 " +
            "WHERE ur.user_id = #{userId}")
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);

    @Select("SELECT DISTINCT p.permission_code FROM user_role ur " +
            "JOIN role_permission rp ON ur.role_id = rp.role_id " +
            "JOIN permission p ON rp.permission_id = p.id AND p.is_deleted = 0 " +
            "WHERE ur.user_id = #{userId}")
    List<String> findPermissionCodesByUserId(@Param("userId") Long userId);
}