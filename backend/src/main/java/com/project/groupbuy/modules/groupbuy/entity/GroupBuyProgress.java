package com.project.groupbuy.modules.groupbuy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("group_buy_progress")
public class GroupBuyProgress {
    @TableId(type = IdType.INPUT) private Long activityId;
    private Integer joinedQty;
    private Integer joinedCount;
    private Integer progressPct;
    private Integer isFormed;
    private LocalDateTime updateTime;
}
