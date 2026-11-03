package com.project.groupbuy.modules.supply.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDate;

/**
 * 库存流水分页查询参数（约束文档 7.2 分页规范 + 11 分页查询与统计约束）
 *
 * 统计口径：change_qty 正数为入库、负数为出库（stock_record DDL 定义）
 * 数据隔离：merchantId 由 SecurityContext 注入，不通过参数传入（约束文档 8）
 *
 * 日期格式：GET 查询串必须为 yyyy-MM-dd（与前端 el-date-picker 的 value-format 一致），
 * 故 LocalDate 字段显式声明 ISO.DATE，否则 Spring 默认转换器会解析失败（1001）。
 */
@Data
public class StockRecordQueryRequest {

    /** 库存记录 ID（下钻单个 SKU 的流水） */
    private Long stockId;

    /** SKU ID */
    private Long skuId;

    /** 社区 ID */
    private Long communityId;

    /** 变更类型，取值见 StockChangeTypeConstant（6 种） */
    private String changeType;

    /** 关联单号（订单号下钻） */
    private String orderNo;

    /** 起始日期（含），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 结束日期（含），格式 yyyy-MM-dd */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    /** 分页：默认 1 / 10，最大 100（约束文档 7.2） */
    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private Integer pageSize = 10;
}
