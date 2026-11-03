package com.project.groupbuy.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.project.groupbuy.common.aspect.OpLog;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.auth.dto.LoginDTO;
import com.project.groupbuy.modules.auth.dto.LoginVO;
import com.project.groupbuy.modules.auth.dto.RegisterDTO;
import com.project.groupbuy.modules.auth.dto.RegisterVO;
import com.project.groupbuy.modules.auth.entity.Permission;
import com.project.groupbuy.modules.auth.entity.Role;
import com.project.groupbuy.modules.auth.entity.RolePermission;
import com.project.groupbuy.modules.auth.entity.User;
import com.project.groupbuy.modules.auth.entity.UserRole;
import com.project.groupbuy.modules.auth.mapper.PermissionMapper;
import com.project.groupbuy.modules.auth.mapper.RoleMapper;
import com.project.groupbuy.modules.auth.mapper.RolePermissionMapper;
import com.project.groupbuy.modules.auth.mapper.UserMapper;
import com.project.groupbuy.modules.auth.mapper.UserRoleMapper;
import com.project.groupbuy.modules.community.entity.Community;
import com.project.groupbuy.modules.community.entity.PickupPoint;
import com.project.groupbuy.modules.community.mapper.CommunityMapper;
import com.project.groupbuy.modules.community.mapper.PickupPointMapper;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import com.project.groupbuy.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.project.groupbuy.common.constant.UserTypeConstant;

/**
 * 认证服务（约束文档 8：JWT + BCrypt + RBAC）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final PermissionMapper permissionMapper;
    private final PickupPointMapper pickupPointMapper;
    private final CommunityMapper communityMapper;
    private final MerchantMapper merchantMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 登录
     * 登录接口跳过 JWT 拦截（公开端点），SecurityContext 初始为 null。
     * 验证通过后手动 set — 让 OpLogAspect 在 finally 块能拿到 userId 写日志。
     */
    @OpLog(module = "认证", operationType = "LOGIN", desensitize = true)
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginDTO dto) {
        // 1. 查用户
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, dto.getUsername())
                .last("LIMIT 1"));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "账号已禁用");
        }

        // 登录成功：手动填充 ThreadLocal，让 OpLogAspect 能拿到 userId 写日志
        SecurityContext.set(user.getId(), user.getUserType(), user.getMerchantId(), null);

        // 2. 查角色
        List<Long> roleIds = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRole>().eq(UserRole::getUserId, user.getId()))
                .stream().map(UserRole::getRoleId).collect(Collectors.toList());
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        List<String> roleCodes = roles.stream().map(Role::getRoleCode).collect(Collectors.toList());

        // 3. 查权限
        List<Long> permissionIds = rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().in(RolePermission::getRoleId, roleIds))
                .stream().map(RolePermission::getPermissionId).distinct().collect(Collectors.toList());
        List<String> permissions = permissionMapper.selectBatchIds(permissionIds)
                .stream().map(Permission::getPermissionCode).collect(Collectors.toList());

        // 4. 生成 token（V6：merchantId / pickupPointId 一起塞 JWT）
        Long pickupPointId = null;
        PickupPoint pp = null;
        if (UserTypeConstant.PICKUP_STAFF.equals(user.getUserType())) {
            pp = pickupPointMapper.selectOne(new LambdaQueryWrapper<PickupPoint>()
                    .eq(PickupPoint::getStaffUserId, user.getId()).last("LIMIT 1"));
            if (pp != null) pickupPointId = pp.getId();
        }
        String token = jwtUtil.generate(user.getId(), user.getUserType(), user.getMerchantId(), pickupPointId);

        // 5. 更新最后登录时间
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        // 6. 组装返回
        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setUserType(user.getUserType());
        vo.setMerchantId(user.getMerchantId());
        vo.setCommunityId(user.getCommunityId());
        vo.setRoles(roleCodes);
        vo.setPermissions(permissions);
        vo.setPickupPointId(pickupPointId);
        if (pp != null) {
            vo.setPickupPointName(pp.getPointName());
            vo.setPickupPointAddress(pp.getAddress());
            if (pp.getCommunityId() != null) {
                Community ppc = communityMapper.selectById(pp.getCommunityId());
                if (ppc != null) vo.setPickupCommunityName(ppc.getCommunityName());
            }
        }
        return vo;
    }

    /**
     * 登出
     * 纯 JWT 无状态 — 后端不存 session，这里只清理 ThreadLocal 并写一条日志。
     * token 失效依赖前端删除本地存储，JWT 本身无法主动吊销（除非加黑名单）。
     */
    @OpLog(module = "认证", operationType = "LOGOUT", desensitize = true)
    public void logout() {
        // SecurityContext 由 JwtAuthInterceptor 填充，这里只清 ThreadLocal
        SecurityContext.clear();
    }

    // ====================== 注册 ======================

    /** 用户名唯一校验：true=可用 */
    public boolean checkUsername(String username) {
        if (username == null || username.length() < 3) return false;
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username)) == 0;
    }

    /** 手机号唯一校验：true=可用（phone 为空或未占用） */
    public boolean checkPhone(String phone) {
        if (phone == null || phone.length() < 6) return false;
        return userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getPhone, phone)) == 0;
    }

    /**
     * 统一注册入口 — 按 userType 分支处理，全部 @Transactional
     * 注册成功即自动登录，签发 JWT
     */
    @Transactional(rollbackFor = Exception.class)
    public RegisterVO register(RegisterDTO dto) {
        // 0. 角色合法性
        String type = dto.getUserType();
        if (!UserTypeConstant.CONSUMER.equals(type)
                && !UserTypeConstant.MERCHANT.equals(type)
                && !UserTypeConstant.PICKUP_STAFF.equals(type)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "不支持的注册角色: " + type);
        }

        // 1. 用户名唯一（DB UNIQUE 兜底）
        if (!checkUsername(dto.getUsername())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "用户名已被占用");
        }
        // 2. 手机号唯一
        if (dto.getPhone() != null && !dto.getPhone().isBlank()) {
            if (!dto.getPhone().matches("^1[3-9]\\d{9}$")) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
            }
            if (!checkPhone(dto.getPhone())) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号已被注册");
            }
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setPhone(dto.getPhone());
        user.setNickname(dto.getNickname() != null && !dto.getNickname().isBlank()
                ? dto.getNickname() : dto.getUsername());
        user.setUserType(type);
        user.setStatus("ACTIVE");

        Long merchantId = null;
        Long communityId = null;
        Long pickupPointId = null;
        String roleCode;

        switch (type) {
            case UserTypeConstant.CONSUMER: {
                if (dto.getCommunityId() == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择社区");
                Community c = communityMapper.selectById(dto.getCommunityId());
                if (c == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "社区不存在");
                communityId = dto.getCommunityId();
                roleCode = "ROLE_CONSUMER";
                break;
            }
            case UserTypeConstant.MERCHANT: {
                // 商家必填字段
                if (dto.getCommunityId() == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择社区");
                if (dto.getMerchantName() == null || dto.getMerchantName().isBlank())
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写商家名称");
                if (dto.getMerchantCode() == null || dto.getMerchantCode().isBlank())
                    throw new BusinessException(ErrorCode.PARAM_ERROR, "请填写商家编码");
                Community c = communityMapper.selectById(dto.getCommunityId());
                if (c == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "社区不存在");
                // 商家编码唯一
                Long cnt = merchantMapper.selectCount(new LambdaQueryWrapper<Merchant>()
                        .eq(Merchant::getMerchantCode, dto.getMerchantCode()));
                if (cnt > 0) throw new BusinessException(ErrorCode.PARAM_ERROR, "商家编码已被占用");

                // 先建 merchant
                Merchant m = new Merchant();
                m.setMerchantName(dto.getMerchantName());
                m.setMerchantCode(dto.getMerchantCode());
                m.setCommunityId(dto.getCommunityId());
                m.setBusinessScope(dto.getBusinessScope());
                m.setContactName(dto.getContactName() != null ? dto.getContactName() : dto.getNickname());
                m.setContactPhone(dto.getContactPhone() != null ? dto.getContactPhone() : dto.getPhone());
                m.setStatus("ACTIVE");
                merchantMapper.insert(m);

                merchantId = m.getId();
                communityId = dto.getCommunityId();
                roleCode = "ROLE_MERCHANT";
                break;
            }
            case UserTypeConstant.PICKUP_STAFF: {
                if (dto.getPickupPointId() == null) throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择自提点");
                PickupPoint pp = pickupPointMapper.selectById(dto.getPickupPointId());
                if (pp == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "自提点不存在");
                pickupPointId = pp.getId();
                communityId = pp.getCommunityId();
                roleCode = "ROLE_PICKUP_STAFF";
                break;
            }
            default:
                throw new BusinessException(ErrorCode.PARAM_ERROR, "未知角色");
        }

        user.setMerchantId(merchantId);
        user.setCommunityId(communityId);
        userMapper.insert(user);

        // 绑定角色
        Role role = roleMapper.selectOne(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleCode, roleCode).last("LIMIT 1"));
        if (role == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "角色配置缺失: " + roleCode);
        UserRole ur = new UserRole();
        ur.setUserId(user.getId());
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);

        // 4. 自动签发 token
        String token = jwtUtil.generate(user.getId(), user.getUserType(), merchantId, pickupPointId);

        // 5. 组装返回
        RegisterVO vo = new RegisterVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setUserType(user.getUserType());
        vo.setToken(token);
        switch (type) {
            case UserTypeConstant.CONSUMER:     vo.setRedirect("/consumer/home"); break;
            case UserTypeConstant.MERCHANT:     vo.setRedirect("/merchant/dashboard"); break;
            case UserTypeConstant.PICKUP_STAFF: vo.setRedirect("/pickup/dashboard"); break;
            default:                            vo.setRedirect("/");
        }
        return vo;
    }
}
