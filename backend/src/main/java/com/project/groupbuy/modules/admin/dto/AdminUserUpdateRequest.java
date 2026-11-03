package com.project.groupbuy.modules.admin.dto;
import lombok.Data;

@Data
public class AdminUserUpdateRequest {
    private String nickname;
    private String phone;
    private String status;
    private Long merchantId;
    private Long communityId;
}