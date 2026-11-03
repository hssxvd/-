package com.project.groupbuy.modules.community.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 自提点表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pickup_point")
public class PickupPoint extends BaseEntity {

    private Long communityId;
    /** V5: 绑定的自提点核销员用户 ID（users.user_id）*/
    private Long staffUserId;
    private String pointName;
    private String address;
    private String contactName;
    private String contactPhone;
    private String openTime;
    /** ACTIVE / DISABLED */
    private String status;
}
