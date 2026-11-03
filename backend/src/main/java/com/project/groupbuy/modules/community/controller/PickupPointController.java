package com.project.groupbuy.modules.community.controller;

import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.auth.entity.User;
import com.project.groupbuy.modules.auth.mapper.UserMapper;
import com.project.groupbuy.modules.community.entity.PickupPoint;
import com.project.groupbuy.modules.community.service.PickupPointService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 自提点控制器（约束 7.2 RESTful 规范 + 28.15 后端兜底）
 */
@Slf4j
@RestController
@RequestMapping("/pickup-points")
@RequiredArgsConstructor
public class PickupPointController {

    private final PickupPointService pickupPointService;
    private final UserMapper userMapper;

    /** 列表：按社区过滤（约束 28.15 双保险：前端传 + 后端兜底从 SecurityContext 拿） */
    @GetMapping
    public R<List<PickupPoint>> list(@RequestParam(required = false) Long communityId,
                                     @RequestParam(required = false) Boolean all) {
        if (all != null && all) {
            // admin/merchant 管理模式：返回当前社区全部自提点（含已删除过滤由 MyBatis-Plus 自动做）
            if (communityId == null && SecurityContext.isLoggedIn()) {
                User u = userMapper.selectById(SecurityContext.getUserId());
                if (u != null) communityId = u.getCommunityId();
            }
            return R.ok(pickupPointService.listAll(communityId));
        }
        // 普通用户模式：只返回 ACTIVE 状态
        if (communityId == null && SecurityContext.isLoggedIn()) {
            User u = userMapper.selectById(SecurityContext.getUserId());
            if (u != null) communityId = u.getCommunityId();
        }
        return R.ok(pickupPointService.listByCommunity(communityId));
    }

    /** 详情 */
    @GetMapping("/{id:\\d+}")
    public R<PickupPoint> detail(@PathVariable Long id) {
        return R.ok(pickupPointService.getById(id));
    }

    /** 新增（约束 7.2：POST /pickup-points） */
    @PostMapping
    public R<PickupPoint> create(@RequestBody PickupPoint point) {
        try {
            return R.ok(pickupPointService.create(point));
        } catch (Exception e) {
            e.printStackTrace();
            return R.fail(5000, "自提点新增异常: " + e.getClass().getSimpleName() + " -> " + e.getMessage());
        }
    }

    /** 修改（约束 7.2：PUT /pickup-points/{id}） */
    @PutMapping("/{id:\\d+}")
    public R<PickupPoint> update(@PathVariable Long id, @RequestBody PickupPoint point) {
        return R.ok(pickupPointService.update(id, point));
    }

    /** 逻辑删除（约束 7.2：DELETE /pickup-points/{id}） */
    @DeleteMapping("/{id:\\d+}")
    public R<Void> delete(@PathVariable Long id) {
        pickupPointService.delete(id);
        return R.ok();
    }
}
