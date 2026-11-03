package com.project.groupbuy.modules.product.dto;

import lombok.Data;

import java.util.List;

/**
 * 分类检索查询参数（约束文档 15.1 课题核心功能）
 */
@Data
public class ProductQuery {

    /** 社区 ID（必选） */
    private Long communityId;

    /** 分类 ID */
    private Long categoryId;

    /** 关键词搜索 */
    private String keyword;

    /** 特色属性筛选，如 [{"key":"产地","value":"崇明岛"}] */
    private List<AttrFilter> attrFilters;

    /** 最低价格 */
    private Double priceMin;

    /** 最高价格 */
    private Double priceMax;

    /** 排序：DEFAULT / SALES / PRICE_ASC / PRICE_DESC / TIME / TAG_PRIORITY */
    private String sortBy = "DEFAULT";

    /** V1.11 商家端状态筛选：DRAFT / ON_SHELF / OFF_SHELF（仅 MERCHANT 角色生效）*/
    private String status;

    /** 个性化排序开关（约束文档 15.1：可关闭）*/
    private Boolean personalized = true;

    /** 分页 */
    private Integer pageNum = 1;
    private Integer pageSize = 10;

    @Data
    public static class AttrFilter {
        private String key;
        private String value;
    }
}
