package com.project.groupbuy.modules.auth.dto;

import lombok.Data;

/**
 * 完整个人资料 VO — JOIN community/merchant
 */
@Data
public class UserProfileVO {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String avatar;
    private String userType;
    private String status;
    private String lastLoginTime;
    private String createTime;

    /** 关联 */
    private Long communityId;
    private String communityName;
    private Long merchantId;
    private String merchantName;
}
