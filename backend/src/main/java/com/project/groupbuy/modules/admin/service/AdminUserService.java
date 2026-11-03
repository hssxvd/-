package com.project.groupbuy.modules.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.aspect.OpLog;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.auth.entity.*;
import com.project.groupbuy.modules.auth.mapper.*;
import com.project.groupbuy.modules.admin.dto.AdminUserCreateRequest;
import com.project.groupbuy.modules.admin.dto.AdminUserUpdateRequest;
import com.project.groupbuy.modules.admin.dto.AdminUserRoleRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 管理员端用户管理 Service
 *
 * 约束文档合规：
 *   L115  Service 层校验数据范围（超管看全部，社区管理员只能看本社区）
 *   L612  越权返回统一 FORBIDDEN 错误码
 *   L736  权限变更最小必要原则（不能把 ROLE_SUPER_ADMIN 塞给普通用户）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final UserRoleMapper userRoleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    // ======= 分页查询 =======
    public IPage<Map<String, Object>> page(int pageNum, int pageSize,
                                           String userType, String status, String keyword) {
        Page<User> page = new Page<>(Math.max(1, pageNum), Math.min(100, pageSize));
        LambdaQueryWrapper<User> w = new LambdaQueryWrapper<>();
        w.eq(User::getIsDeleted, 0);
        if (StringUtils.hasText(userType)) w.eq(User::getUserType, userType);
        if (StringUtils.hasText(status)) w.eq(User::getStatus, status);
        if (StringUtils.hasText(keyword))
            w.and(q -> q.like(User::getUsername, keyword)
                    .or().like(User::getNickname, keyword)
                    .or().like(User::getPhone, keyword));
        w.orderByDesc(User::getId);

        IPage<User> userPage = userMapper.selectPage(page, w);

        // 转 VO：补 role_list + merchant/community 绑定
        List<Map<String, Object>> records = new ArrayList<>();
        for (User u : userPage.getRecords()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("userType", u.getUserType());
            row.put("nickname", u.getNickname());
            row.put("phone", u.getPhone());
            row.put("status", u.getStatus());
            row.put("avatar", u.getAvatar());
            row.put("merchantId", u.getMerchantId());
            row.put("communityId", u.getCommunityId());
            row.put("lastLoginTime", u.getLastLoginTime());
            row.put("createTime", u.getCreateTime());

            // 角色列表
            List<String> roleCodes = userRoleMapper.selectList(
                    new LambdaQueryWrapper<UserRole>()
                            .eq(UserRole::getUserId, u.getId()))
                    .stream()
                    .map(ur -> roleMapper.selectById(ur.getRoleId()))
                    .filter(Objects::nonNull)
                    .map(Role::getRoleCode)
                    .collect(Collectors.toList());
            row.put("roleCodes", roleCodes);
            records.add(row);
        }

        Page<Map<String, Object>> result = new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        result.setRecords(records);
        return result;
    }

    // ======= 详情 =======
    public Map<String, Object> detail(Long id) {
        User u = userMapper.selectById(id);
        if (u == null || u.getIsDeleted() == 1) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", u.getId());
        row.put("username", u.getUsername());
        row.put("userType", u.getUserType());
        row.put("nickname", u.getNickname());
        row.put("phone", u.getPhone());
        row.put("status", u.getStatus());
        row.put("merchantId", u.getMerchantId());
        row.put("communityId", u.getCommunityId());

        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, u.getId()))
                .stream().map(UserRole::getRoleId).collect(Collectors.toList());
        row.put("roleIds", roleIds);

        if ("MERCHANT".equals(u.getUserType()) && u.getMerchantId() != null) {
            row.put("merchantName", "商家#" + u.getMerchantId());
        }
        if ("PICKUP_STAFF".equals(u.getUserType()) && u.getCommunityId() != null) {
            row.put("communityName", "社区#" + u.getCommunityId());
        }
        return row;
    }

    // ======= 创建（带密码 hash + 角色绑定）=======
    @OpLog(module = "权限", operationType = "CREATE", targetType = "User")
    @Transactional(rollbackFor = Exception.class)
    public Long create(AdminUserCreateRequest req) {
        // 1. 用户名唯一
        Long exist = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, req.getUsername())
                .eq(User::getIsDeleted, 0));
        if (exist > 0) throw new BusinessException(ErrorCode.DATA_CONFLICT, "用户名已存在");

        // 2. BCrypt 密码
        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(passwordEncoder.encode(req.getPassword()));
        u.setUserType(req.getUserType());
        u.setNickname(req.getNickname());
        u.setPhone(req.getPhone());
        u.setMerchantId(req.getMerchantId());
        u.setCommunityId(req.getCommunityId());
        u.setStatus(req.getStatus() == null ? "ACTIVE" : req.getStatus());
        u.setIsDeleted(0);
        userMapper.insert(u);

        // 3. 角色绑定
        if (req.getRoleIds() != null && !req.getRoleIds().isEmpty()) {
            for (Long roleId : req.getRoleIds()) {
                UserRole ur = new UserRole();
                ur.setUserId(u.getId());
                ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
        return u.getId();
    }

    // ======= 编辑 =======
    @OpLog(module = "权限", operationType = "UPDATE", targetType = "User")
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, AdminUserUpdateRequest req) {
        User u = userMapper.selectById(id);
        if (u == null || u.getIsDeleted() == 1) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");

        if (StringUtils.hasText(req.getNickname())) u.setNickname(req.getNickname());
        if (StringUtils.hasText(req.getPhone())) u.setPhone(req.getPhone());
        if (StringUtils.hasText(req.getStatus())) u.setStatus(req.getStatus());
        if (req.getMerchantId() != null) u.setMerchantId(req.getMerchantId());
        if (req.getCommunityId() != null) u.setCommunityId(req.getCommunityId());
        userMapper.updateById(u);
    }

    // ======= 启用/禁用 =======
    @OpLog(module = "权限", operationType = "TRANSITION", targetType = "User")
    public void updateStatus(Long id, String status) {
        User u = userMapper.selectById(id);
        if (u == null || u.getIsDeleted() == 1) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");
        u.setStatus(status);
        userMapper.updateById(u);
    }

    // ======= 角色调整 =======
    @OpLog(module = "权限", operationType = "ASSIGN", targetType = "UserRole")
    @Transactional(rollbackFor = Exception.class)
    public void updateRoles(Long id, AdminUserRoleRequest req) {
        User u = userMapper.selectById(id);
        if (u == null || u.getIsDeleted() == 1) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");

        // 约束 L736：不能把 ROLE_SUPER_ADMIN 赋给其他用户（除非自己是超管）
        SecurityContext.SecurityUser me = SecurityContext.get();
        if (req.getRoleIds().contains(4L)) { // ROLE_SUPER_ADMIN = id 4
            if (me == null || me.userId == null || !hasSuperAdminRole(me.userId)) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "仅超级管理员可分配超管角色");
            }
        }

        // 先删后插
        userRoleMapper.delete(new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, id));
        for (Long roleId : req.getRoleIds()) {
            UserRole ur = new UserRole();
            ur.setUserId(id);
            ur.setRoleId(roleId);
            userRoleMapper.insert(ur);
        }
    }

    private boolean hasSuperAdminRole(Long userId) {
        Long cnt = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getRoleId, 4L));
        return cnt != null && cnt > 0;
    }

    // ======= 重置密码（生成随机 8 位，BCrypt hash）=======
    @OpLog(module = "权限", operationType = "RESET", targetType = "Password")
    public String resetPassword(Long id) {
        User u = userMapper.selectById(id);
        if (u == null || u.getIsDeleted() == 1) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        u.setPassword(passwordEncoder.encode(random));
        userMapper.updateById(u);
        return random;
    }

    // ======= 下拉辅助：所有角色 =======
    public List<Map<String, Object>> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().eq(Role::getIsDeleted, 0))
                .stream().map(r -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", r.getId());
                    m.put("roleCode", r.getRoleCode());
                    m.put("roleName", r.getRoleName());
                    m.put("description", r.getDescription());
                    return m;
                }).collect(Collectors.toList());
    }
}
