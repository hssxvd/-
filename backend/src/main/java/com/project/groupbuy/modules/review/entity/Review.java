package com.project.groupbuy.modules.review.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.project.groupbuy.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 评价表（约束文档：同一明细只能评一次）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review")
public class Review extends BaseEntity {

    private Long orderId;
    private Long orderItemId;
    private Long userId;
    private Long skuId;
    private Long merchantId;
    /** 1-5 星 */
    private Integer rating;
    private String content;
    private String images;
    private Integer isAnonymous;
    private String replyContent;
    private LocalDateTime replyTime;

    // ===== 非持久化 transient 字段（查询后 fill，前端展示用）=====
    @TableField(exist = false)
    private String skuName;       // SKU 规格名，如"农夫山泉天然水 550ml*24瓶"
    @TableField(exist = false)
    private String productName;   // 商品主名，如"农夫山泉天然水"
    @TableField(exist = false)
    private String productMainImage; // 商品主图（商家端评价列表可显示缩略图）
}
