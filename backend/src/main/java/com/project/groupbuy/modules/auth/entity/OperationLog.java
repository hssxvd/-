package com.project.groupbuy.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 操作日志表（约束文档 10）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_log")
public class OperationLog extends BaseEntity {

    private Long userId;
    private String roleCode;
    private String module;
    private String operationType;
    private Long targetId;
    private String targetType;
    private String beforeStatus;
    private String afterStatus;
    private String requestParams;
    private String result;
    private String errorMsg;
    private String ip;
    private String traceId;
}
