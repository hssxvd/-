package com.project.groupbuy.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 库存流水表（约束文档 6：六变更类型含 SUPPLY_IN）
 */
@Data
@TableName("stock_record")
public class StockRecord implements Serializable {

    private Long id;
    private Long stockId;
    private Long skuId;
    private Long communityId;
    /** LOCK / UNLOCK / DEDUCT / ROLLBACK / INBOUND / SUPPLY_IN */
    private String changeType;
    /** 变更数量，正数增加，负数减少 */
    private Integer changeQty;
    private Integer beforeTotal;
    private Integer afterTotal;
    private Integer beforeLocked;
    private Integer afterLocked;
    private String orderNo;
    private String remark;
    private Long operatorId;
    private LocalDateTime createTime;
}
