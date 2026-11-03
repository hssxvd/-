package com.project.groupbuy.modules.supply.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 商家端库存列表查询参数（约束文档 11：列表接口必须分页，禁止全量返回）
 *
 * warnStatus 取值见 StockWarnStatusConstant；为 null 表示不筛选。
 * lockedOnly=true 表示只查"存在预占库存(locked_qty>0)"的记录，与总览 lockedCount 口径一致。
 * keyword 匹配商品名 / SKU 名称 / SKU 编码。
 */
@Data
public class StockListQueryRequest {

    /** 社区 ID */
    private Long communityId;

    /** 关键词：商品名 / SKU 名称 / SKU 编码 */
    private String keyword;

    /** 预警状态筛选，取值见 StockWarnStatusConstant */
    private String warnStatus;

    /** 仅看有预占库存的记录（locked_qty > 0） */
    private Boolean lockedOnly;

    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private Integer pageSize = 10;
}
