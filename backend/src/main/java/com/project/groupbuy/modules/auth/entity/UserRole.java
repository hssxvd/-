package com.project.groupbuy.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户角色关联表
 */
@Data
@TableName("user_role")
public class UserRole implements Serializable {

    private Long id;
    private Long userId;
    private Long roleId;
    private LocalDateTime createTime;
}
