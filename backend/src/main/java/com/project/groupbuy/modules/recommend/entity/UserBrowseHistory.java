package com.project.groupbuy.modules.recommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户浏览历史 —— 约束 L759/L760：只存 user_id/product_id/category_id/community_id + 时间，不含任何隐私字段
 */
@Data
@TableName("user_browse_history")
public class UserBrowseHistory {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long productId;
    private Long categoryId;
    private Long communityId;
    private Integer durationMs;
    private LocalDateTime createTime;
}
