package com.project.groupbuy.modules.aftersale.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 售后轨迹视图（aftersale_record + 中文标签）
 */
@Data
public class AftersaleRecordVO implements Serializable {

    private Long id;
    private Long aftersaleId;
    private String fromStatus;
    private String fromStatusLabel;
    private String toStatus;
    private String toStatusLabel;
    private Long operatorId;
    /** CONSUMER / MERCHANT / ADMIN / SYSTEM */
    private String operatorRole;
    private String operatorRoleLabel;
    private String remark;
    private LocalDateTime createTime;
}
