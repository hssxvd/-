package com.project.groupbuy.modules.aftersale.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import java.time.LocalDate;

/**
 * 售后列表查询参数（约束文档 11：列表接口必须分页，禁止全量返回）
 *
 * 商家端 / Admin 端 / 消费者端共用：
 *   商家端 —— merchantId 由 SecurityContext 强制注入，请求里的值被忽略
 *   Admin 端 —— merchantId 可选，用于按商家下钻
 *   消费者端 —— 只查自己的售后单
 *
 * 注意：LocalDate 必须标注 @DateTimeFormat，否则 GET 查询串日期绑定会抛 1001（V9 踩坑）。
 */
@Data
public class AftersaleQueryRequest {

    /** 商家 ID（仅 Admin 端可选筛选）*/
    private Long merchantId;

    /** 订单 ID（可选）*/
    private Long orderId;

    /** 售后单据状态，取值见 AftersaleStatusConstant */
    private String aftersaleStatus;

    /** 售后类型，取值见 AftersaleStatusConstant */
    private String aftersaleType;

    /** 关键词：售后单号 / 订单号 */
    private String keyword;

    /** 申请时间区间-起（含）*/
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    /** 申请时间区间-止（含）*/
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;

    @Min(value = 1, message = "pageNum 最小为 1")
    private Integer pageNum = 1;

    @Min(value = 1, message = "pageSize 最小为 1")
    @Max(value = 100, message = "pageSize 最大为 100")
    private Integer pageSize = 10;
}
