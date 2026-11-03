package com.project.groupbuy.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户表（约束文档 8）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    private String username;
    private String password;
    private String nickname;
    private String phone;
    private String avatar;
    /** CONSUMER / MERCHANT / ADMIN */
    private String userType;
    private Long merchantId;
    private Long communityId;
    /** ACTIVE / DISABLED */
    private String status;
    private java.time.LocalDateTime lastLoginTime;
}
