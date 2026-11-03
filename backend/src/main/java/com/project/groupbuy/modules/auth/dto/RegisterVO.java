package com.project.groupbuy.modules.auth.dto;

import lombok.Data;

/**
 * 注册响应 VO — 默认注册即自动登录
 */
@Data
public class RegisterVO {

    private Long userId;
    private String username;
    private String userType;
    private String token;
    private String redirect; // 前端自动跳转路径
}
