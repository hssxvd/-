package com.project.groupbuy.modules.auth.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录响应（约束文档 8：返回 token + 用户信息 + 权限列表）
 */
@Data
public class LoginVO {

    private String token;
    private Long userId;
    private String username;
    private String nickname;
    private String userType;
    private Long merchantId;
    private Long communityId;
    /** 自提点核销员：自己绑定的自提点 ID（V5 新增）*/
    private Long pickupPointId;
    /** 自提点核销员：取货点名称 */
    private String pickupPointName;
    /** 自提点核销员：取货点详细地址 */
    private String pickupPointAddress;
    /** 自提点核销员：取货点所属社区名 */
    private String pickupCommunityName;
    /** 角色编码列表 */
    private List<String> roles;
    /** 权限编码列表 */
    private List<String> permissions;
}
