package com.project.groupbuy.modules.admin.dto;
import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class AdminUserCreateRequest {
    @NotBlank(message = "用户名必填")
    @Size(min = 3, max = 32, message = "用户名 3-32 位")
    private String username;
    @NotBlank(message = "密码必填")
    @Size(min = 6, max = 32, message = "密码 6-32 位")
    private String password;
    @NotBlank(message = "用户类型必填")
    private String userType;
    private String nickname;
    private String phone;
    private String status;
    private Long merchantId;
    private Long communityId;
    private List<Long> roleIds;
}