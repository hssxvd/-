package com.project.groupbuy.modules.community.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.community.entity.Community;
import com.project.groupbuy.modules.community.mapper.CommunityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 社区公开接口（商品详情页选择社区用，JWT 白名单）
 */
@Slf4j
@RestController
@RequestMapping("/communities")
@RequiredArgsConstructor
public class CommunitiesController {

    private final CommunityMapper communityMapper;

    /**
     * 获取活跃社区列表（status=ACTIVE，按创建顺序）
     * 约束文档 11.2：用户必须先选择社区才能查看库存
     */
    @GetMapping("/active")
    public R<List<Map<String, Object>>> active() {
        List<Community> list = communityMapper.selectList(
                new LambdaQueryWrapper<Community>()
                    .eq(Community::getStatus, "ACTIVE")
                    .eq(Community::getIsDeleted, 0)
                    .orderByAsc(Community::getCreateTime));

        List<Map<String, Object>> result = list.stream().map(c -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("communityName", c.getCommunityName());
            m.put("communityCode", c.getCommunityCode());
            m.put("address", c.getAddress());
            return m;
        }).collect(Collectors.toList());
        return R.ok(result);
    }
}