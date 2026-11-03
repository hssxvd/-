package com.project.groupbuy.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色权限关联表
 */
@Data
@TableName("role_permission")
public class RolePermission implements Serializable {

    private Long id;
    private Long roleId;
    private Long permissionId;
    private LocalDateTime createTime;
}
