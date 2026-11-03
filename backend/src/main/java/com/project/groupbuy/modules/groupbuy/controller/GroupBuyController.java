package com.project.groupbuy.modules.groupbuy.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.groupbuy.entity.GroupBuyActivity;
import com.project.groupbuy.modules.groupbuy.entity.GroupBuyMember;
import com.project.groupbuy.modules.groupbuy.entity.GroupBuyProgress;
import com.project.groupbuy.modules.groupbuy.mapper.GroupBuyActivityMapper;
import com.project.groupbuy.modules.groupbuy.mapper.GroupBuyMemberMapper;
import com.project.groupbuy.modules.groupbuy.service.GroupBuyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/group-buys")
@RequiredArgsConstructor
public class GroupBuyController {

    private final GroupBuyService groupBuyService;
    private final GroupBuyMemberMapper memberMapper;
    private final GroupBuyActivityMapper activityMapper;

    @GetMapping
    public R<List<GroupBuyActivity>> list(@RequestParam(required = false) Long communityId) {
        return R.ok(groupBuyService.listOpen(communityId));
    }

    @GetMapping("/all")
    public R<List<GroupBuyActivity>> listAll(@RequestParam(required = false) Long communityId) {
        LambdaQueryWrapper<GroupBuyActivity> q = new LambdaQueryWrapper<GroupBuyActivity>()
                .orderByDesc(GroupBuyActivity::getCreateTime);
        if (communityId != null) q.eq(GroupBuyActivity::getCommunityId, communityId);
        List<GroupBuyActivity> list = activityMapper.selectList(q);
        groupBuyService.fillTransientsInternal(list); // 填充进度和商品
        return R.ok(list);
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        GroupBuyActivity a = groupBuyService.detail(id);
        GroupBuyProgress p = groupBuyService.getProgress(id);
        // 附带 member 列表（包含 orderId / payStatus）
        List<GroupBuyMember> members = memberMapper.selectList(
                new LambdaQueryWrapper<GroupBuyMember>()
                        .eq(GroupBuyMember::getActivityId, id)
                        .orderByDesc(GroupBuyMember::getIsLeader));
        Map<String, Object> result = new HashMap<>();
        result.put("activity", a);
        result.put("progress", p);
        result.put("members", members);
        return R.ok(result);
    }

    @PostMapping("/{id}/join")
    public R<GroupBuyMember> join(@PathVariable Long id,
                                  @RequestBody Map<String, Object> body) {
        Long userId = SecurityContext.getUserId();
        int qty = Integer.parseInt(String.valueOf(body.getOrDefault("quantity", 1)));
        Long pickupPointId = null;
        if (body.containsKey("pickupPointId") && body.get("pickupPointId") != null)
            pickupPointId = Long.valueOf(String.valueOf(body.get("pickupPointId")));
        return R.ok(groupBuyService.join(id, userId, qty, pickupPointId));
    }

    @GetMapping("/mine")
    public R<List<Map<String, Object>>> mine() {
        Long userId = SecurityContext.getUserId();
        List<GroupBuyMember> myMembers = memberMapper.selectList(
                new LambdaQueryWrapper<GroupBuyMember>()
                        .eq(GroupBuyMember::getUserId, userId)
                        .orderByDesc(GroupBuyMember::getJoinTime));

        // 组装：member + activity + orderId
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (GroupBuyMember m : myMembers) {
            Map<String, Object> item = new HashMap<>();
            item.put("member", m);
            GroupBuyActivity a = activityMapper.selectById(m.getActivityId());
            if (a != null) {
                groupBuyService.fillTransientsInternal(java.util.Collections.singletonList(a));
            }
            item.put("activity", a);
            result.add(item);
        }
        return R.ok(result);
    }

    /** 手动触发窗口结算（测试接口）：把 cycle_end 已过期的活动批量处理 */
    @PostMapping("/trigger-cutoff")
    public R<Map<String, Object>> triggerCutoff() {
        groupBuyService.settleWindowEnd();
        return R.ok(Map.of("msg", "settle triggered at " + LocalDateTime.now()));
    }

    /** 手动强制某个活动成团（测试接口，跳过时间窗口） */
    @PostMapping("/{id}/force-fulfill")
    public R<Map<String, Object>> forceFulfill(@PathVariable Long id) {
        GroupBuyActivity a = activityMapper.selectById(id);
        if (a == null) return R.fail(404, "活动不存在");
        a.setStatus("FULFILLED");
        a.setCycleEnd(LocalDateTime.now().minusMinutes(1)); // 确保 cycleEnd 已过期
        activityMapper.updateById(a);
        groupBuyService.refreshProgress(id);
        groupBuyService.generateGroupBuyOrders(a);
        return R.ok(Map.of("activityId", id, "status", "FULFILLED", "msg", "强制成团 + 自动生成订单"));
    }
}
