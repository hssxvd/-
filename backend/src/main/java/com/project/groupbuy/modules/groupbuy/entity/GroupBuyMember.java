package com.project.groupbuy.modules.groupbuy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("group_buy_member")
public class GroupBuyMember {
    @TableId(type = IdType.AUTO) private Long id;
    private Long activityId;
    private Long userId;
    private Long orderId;
    private Integer quantity;
    private Long pickupPointId;
    private BigDecimal groupPrice;
    private Integer isLeader;
    private LocalDateTime joinTime;
    private String payStatus;
    private LocalDateTime createTime;
}
