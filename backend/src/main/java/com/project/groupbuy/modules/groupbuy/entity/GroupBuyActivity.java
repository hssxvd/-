package com.project.groupbuy.modules.groupbuy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("group_buy_activity")
public class GroupBuyActivity {
    @TableId(type = IdType.AUTO) private Long id;
    private String activityNo;
    private String title;
    private Long productId;
    private Long skuId;
    private Long merchantId;
    private Long communityId;
    private Integer minGroupQty;
    private Integer maxGroupQty;
    /** P0: 成团最小人数（不同用户数，默认 2） */
    private Integer minGroupMembers;
    /** P0: 每人累计限购件数，默认不超过 maxGroupQty */
    private Integer maxQtyPerPerson;
    private BigDecimal groupPrice;
    private BigDecimal originalPrice;
    private BigDecimal discountPercent;
    private LocalDateTime cycleStart;
    private LocalDateTime cycleEnd;
    private LocalDateTime expectedArrive;
    private Long pickupPointId;
    private String status;
    private Integer autoCutoff;
    private Long createBy;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;

    /** transient：列表接口填充的实时进度（从 member 聚合 + progress 表） */
    @TableField(exist = false)
    private Integer joinedQty;
    @TableField(exist = false)
    private Integer joinedMembers;
    @TableField(exist = false)
    private Integer isFormed;

    /** transient：列表接口填充的商品名称和图片（JOIN product 表） */
    @TableField(exist = false)
    private String productName;
    @TableField(exist = false)
    private String productImage;
}
