package com.project.groupbuy.modules.fulfillment.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 履约记录表
 */
@Data
@TableName("fulfillment_record")
public class FulfillmentRecord implements Serializable {

    private Long id;
    private Long fulfillmentId;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorRole;
    private String remark;
    private LocalDateTime createTime;
}
