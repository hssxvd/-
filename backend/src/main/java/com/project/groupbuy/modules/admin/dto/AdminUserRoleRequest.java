package com.project.groupbuy.modules.admin.dto;
import lombok.Data;
import java.util.List;

@Data
public class AdminUserRoleRequest {
    private List<Long> roleIds;
}