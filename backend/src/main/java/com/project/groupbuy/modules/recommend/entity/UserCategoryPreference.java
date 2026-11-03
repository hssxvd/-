package com.project.groupbuy.modules.recommend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_category_preference")
public class UserCategoryPreference {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private Long categoryId;
    private BigDecimal browseWeight;
    private BigDecimal cartWeight;
    private BigDecimal buyWeight;
    private BigDecimal totalWeight;
    private LocalDateTime lastVisitAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
