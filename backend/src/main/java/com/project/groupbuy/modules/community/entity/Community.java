package com.project.groupbuy.modules.community.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 社区表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("community")
public class Community extends BaseEntity {

    private String communityName;
    private String communityCode;
    private String address;
    private String contactName;
    private String contactPhone;
    /** ACTIVE / DISABLED */
    private String status;
}
