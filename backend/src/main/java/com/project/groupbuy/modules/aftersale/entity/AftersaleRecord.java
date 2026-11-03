package com.project.groupbuy.modules.aftersale.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 售后记录表
 */
@Data
@TableName("aftersale_record")
public class AftersaleRecord implements Serializable {

    private Long id;
    private Long aftersaleId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorRole;
    private String remark;
    private LocalDateTime createTime;
}
