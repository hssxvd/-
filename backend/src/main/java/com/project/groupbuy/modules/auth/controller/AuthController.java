package com.project.groupbuy.modules.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.auth.dto.LoginDTO;
import com.project.groupbuy.modules.auth.dto.LoginVO;
import com.project.groupbuy.modules.auth.dto.RegisterDTO;
import com.project.groupbuy.modules.auth.dto.RegisterVO;
import com.project.groupbuy.modules.auth.service.AuthService;
import com.project.groupbuy.modules.community.entity.Community;
import com.project.groupbuy.modules.community.entity.PickupPoint;
import com.project.groupbuy.modules.community.mapper.CommunityMapper;
import com.project.groupbuy.modules.community.mapper.PickupPointMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 认证控制器（约束文档 8）
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CommunityMapper communityMapper;
    private final PickupPointMapper pickupPointMapper;

    /** 登录 */
    @PostMapping("/login")
    public R<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return R.ok(authService.login(dto));
    }

    /** 登出 */
    @PostMapping("/logout")
    public R<?> logout() {
        authService.logout();
        return R.ok(null);
    }

    /** 当前用户信息 */
    @GetMapping("/me")
    public R<?> me() {
        return R.ok(com.project.groupbuy.common.context.SecurityContext.get());
    }

    /** 健康检查 */
    @GetMapping("/health")
    public R<String> health() {
        return R.ok("UP");
    }

    // ====================== 注册相关（全部公开） ======================

    /**
     * 统一注册入口
     * body: RegisterDTO — 按 userType 分支
     */
    @PostMapping("/register")
    public R<RegisterVO> register(@Valid @RequestBody RegisterDTO dto) {
        return R.ok(authService.register(dto));
    }

    /** 用户名唯一校验：{available: true/false} */
    @GetMapping("/check-username")
    public R<Map<String, Object>> checkUsername(@RequestParam String username) {
        boolean available = authService.checkUsername(username);
        return R.ok(Map.of("available", available));
    }

    /** 手机号唯一校验：{available: true/false} */
    @GetMapping("/check-phone")
    public R<Map<String, Object>> checkPhone(@RequestParam String phone) {
        boolean available = authService.checkPhone(phone);
        return R.ok(Map.of("available", available));
    }

    /** 社区下拉列表（注册时选社区） */
    @GetMapping("/communities")
    public R<List<Map<String, Object>>> communities() {
        List<Community> list = communityMapper.selectList(
                new LambdaQueryWrapper<Community>().eq(Community::getStatus, "ACTIVE")
                        .orderByAsc(Community::getId));
        List<Map<String, Object>> data = list.stream().map(c -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getCommunityName());
            m.put("code", c.getCommunityCode());
            return m;
        }).collect(Collectors.toList());
        return R.ok(data);
    }

    /** 自提点下拉列表（可按社区过滤） */
    @GetMapping("/pickup-points")
    public R<List<Map<String, Object>>> pickupPoints(@RequestParam(required = false) Long communityId) {
        LambdaQueryWrapper<PickupPoint> q = new LambdaQueryWrapper<PickupPoint>()
                .eq(PickupPoint::getStatus, "ACTIVE");
        if (communityId != null) q.eq(PickupPoint::getCommunityId, communityId);
        q.orderByAsc(PickupPoint::getId);
        List<PickupPoint> list = pickupPointMapper.selectList(q);
        List<Map<String, Object>> data = list.stream().map(p -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getPointName());
            m.put("address", p.getAddress() != null ? p.getAddress() : "");
            m.put("communityId", p.getCommunityId() != null ? p.getCommunityId() : 0L);
            return m;
        }).collect(Collectors.toList());
        return R.ok(data);
    }
}
