package com.project.groupbuy.modules.auth.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 注册请求 DTO — 统一入口，role 由 userType 决定
 *
 * 消费者必填：username, password, userType, communityId
 * 商家必填：  username, password, userType(MERCHANT), communityId, merchantName, merchantCode
 * 核销员必填：username, password, userType(PICKUP_STAFF), pickupPointId
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 32, message = "用户名 3-32 字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能含字母/数字/下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码至少 6 位")
    private String password;

    private String phone;          // 选填
    private String nickname;      // 选填

    @NotBlank(message = "必须选择注册角色")
    private String userType;      // CONSUMER | MERCHANT | PICKUP_STAFF

    private Long communityId;

    // 商家字段
    private String merchantName;
    private String merchantCode;
    private String businessScope;
    private String contactName;
    private String contactPhone;

    // 核销员字段
    private Long pickupPointId;
}
