package com.project.groupbuy.modules.community.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.community.entity.Community;
import com.project.groupbuy.modules.community.mapper.CommunityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/communities")
@RequiredArgsConstructor
public class CommunitiesController {

    private final CommunityMapper communityMapper;

    /** 分页列表（admin 管理用） */
    @GetMapping
    public R<Page<Community>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<Community> w = new LambdaQueryWrapper<Community>()
                .orderByDesc(Community::getCreateTime);
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(Community::getCommunityName, keyword)
                    .or().like(Community::getAddress, keyword)
                    .or().like(Community::getCommunityCode, keyword));
        }
        if (status != null && !status.isBlank()) {
            w.eq(Community::getStatus, status);
        }
        return R.ok(communityMapper.selectPage(new Page<>(page, size), w));
    }

    /** 活跃社区下拉（前端选社区） */
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

    /** 详情 */
    @GetMapping("/{id}")
    public R<Community> detail(@PathVariable Long id) {
        return R.ok(communityMapper.selectById(id));
    }

    /** 新增 */
    @PostMapping
    public R<Community> create(@RequestBody Community c) {
        c.setStatus(c.getStatus() == null ? "ACTIVE" : c.getStatus());
        communityMapper.insert(c);
        return R.ok(c);
    }

    /** 修改 */
    @PutMapping("/{id}")
    public R<Community> update(@PathVariable Long id, @RequestBody Community c) {
        c.setId(id);
        communityMapper.updateById(c);
        return R.ok(c);
    }

    /** 逻辑删除 */
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id) {
        communityMapper.deleteById(id);
        return R.ok();
    }

    /** 切换状态 */
    @PutMapping("/{id}/status")
    public R<Void> toggleStatus(@PathVariable Long id) {
        Community c = communityMapper.selectById(id);
        if (c == null) return R.fail(404, "社区不存在");
        c.setStatus("ACTIVE".equals(c.getStatus()) ? "DISABLED" : "ACTIVE");
        communityMapper.updateById(c);
        return R.ok();
    }
}