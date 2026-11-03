package com.project.groupbuy.modules.community.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.modules.community.entity.PickupPoint;
import com.project.groupbuy.modules.community.mapper.PickupPointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 自提点 Service（约束 28.13 + CRUD 完整）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PickupPointService {

    private final PickupPointMapper pickupPointMapper;

    /** 按社区查询（约束 28.15：active 状态） */
    public List<PickupPoint> listByCommunity(Long communityId) {
        return pickupPointMapper.selectList(
                new LambdaQueryWrapper<PickupPoint>()
                        .eq(communityId != null, PickupPoint::getCommunityId, communityId)
                        .eq(PickupPoint::getStatus, "ACTIVE")
                        .orderByAsc(PickupPoint::getPointName));
    }

    /** 全部分页查询（admin 管理用，不过滤状态） */
    public List<PickupPoint> listAll(Long communityId) {
        return pickupPointMapper.selectList(
                new LambdaQueryWrapper<PickupPoint>()
                        .eq(communityId != null, PickupPoint::getCommunityId, communityId)
                        .orderByAsc(PickupPoint::getCommunityId)
                        .orderByAsc(PickupPoint::getPointName));
    }

    /** 详情 */
    public PickupPoint getById(Long id) {
        PickupPoint p = pickupPointMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(com.project.groupbuy.common.result.ErrorCode.DATA_NOT_FOUND, "自提点不存在");
        }
        return p;
    }

    /** 新增（约束 7.2：RESTful POST 复数路径） */
    public PickupPoint create(PickupPoint point) {
        point.setId(null);
        if (point.getStatus() == null) point.setStatus("ACTIVE");
        pickupPointMapper.insert(point);
        return point;
    }

    /** 修改 */
    public PickupPoint update(Long id, PickupPoint point) {
        PickupPoint existing = pickupPointMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(com.project.groupbuy.common.result.ErrorCode.DATA_NOT_FOUND, "自提点不存在");
        }
        point.setId(id);
        pickupPointMapper.updateById(point);
        return pickupPointMapper.selectById(id);
    }

    /** 逻辑删除（约束 4.1：软删除） */
    public void delete(Long id) {
        PickupPoint existing = pickupPointMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException(com.project.groupbuy.common.result.ErrorCode.DATA_NOT_FOUND, "自提点不存在");
        }
        pickupPointMapper.deleteById(id);
    }
}
