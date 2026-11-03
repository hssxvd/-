package com.project.groupbuy.modules.supply.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 供应计划表（约束文档 6.1 供应与库存协同）
 * 预测结果 → forecast_qty → 商家调整 → plan_qty → 到货驱动库存
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("supply_plan")
public class SupplyPlan extends BaseEntity {

    private String planNo;
    private Long skuId;
    private Long merchantId;
    private Long communityId;
    /** 计划日期/截单日 */
    private LocalDate planDate;
    /** 关联需求预测值（forecast_result） */
    private BigDecimal forecastQty;
    /** 计划采购量 */
    private Integer planQty;
    /** 调整后数量 */
    private Integer adjustedQty;
    /** 调整原因 */
    private String adjustReason;
    /** DRAFT / CONFIRMED / ARRIVED / CANCELLED */
    private String status;
    private LocalDateTime arriveTime;
    private Long operatorId;
    private String remark;

    // ========== 展示层 enrich 字段（不持久化）==========
    @TableField(exist = false) private String productName;
    @TableField(exist = false) private String skuName;
    @TableField(exist = false) private String spec;
    @TableField(exist = false) private String mainImage;
    @TableField(exist = false) private String skuImage;
    @TableField(exist = false) private String communityName;
}
