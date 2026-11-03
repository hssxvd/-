package com.project.groupbuy.modules.aftersale.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 售后详情视图（列表行 + 处理轨迹时间线）
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AftersaleDetailVO extends AftersaleVO {

    /** 处理轨迹时间线（按时间正序）*/
    private List<AftersaleRecordVO> timeline;
}
