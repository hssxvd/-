package com.project.groupbuy.modules.product.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 商家主表（V6 migration 新建）
 * 约束文档 3.1 多商家加盟模型正式落地
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("merchant")
public class Merchant extends BaseEntity {

    private String merchantName;
    private String merchantCode;
    private String contactName;
    private String contactPhone;
    private Long communityId;
    private String businessScope;
    private String status;          // ACTIVE / DISABLED
    private String remark;
}
