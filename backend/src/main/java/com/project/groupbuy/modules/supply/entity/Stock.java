package com.project.groupbuy.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 库存表（约束文档 6 条件更新防超卖）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("stock")
public class Stock extends BaseEntity {

    private Long skuId;
    private Long communityId;
    /** 自提点，NULL 表示社区级库存 */
    private Long pickupPointId;
    private Integer totalQty;
    private Integer lockedQty;
    private Integer availableQty;
    private Integer warnQty;

    @Version
    private Integer version;
}
