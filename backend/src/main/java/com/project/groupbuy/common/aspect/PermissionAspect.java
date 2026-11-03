package com.project.groupbuy.common.aspect;

import com.project.groupbuy.common.annotation.RequirePermission;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * RBAC 权限切面 —— 拦截 @RequirePermission 注解，校验当前登录用户的权限集合
 *
 * 约束文档合规：
 *   L115  权限两层：角色权限 + 数据范围权限（本切面做角色层，数据范围在 Service 层补）
 *   L612  越权返回统一 403 + FORBIDDEN 错误码
 *   L749  无权限按钮不渲染（前端），但后端仍必须校验（本切面就是后端兜底）
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    private final PermissionLoader permissionLoader;

    @Around("@annotation(req)")
    public Object check(ProceedingJoinPoint pjp, RequirePermission req) throws Throwable {
        SecurityContext.SecurityUser user = SecurityContext.get();

        // 1. 登录校验
        if (user == null || user.userId == null) {
            log.warn("[Permission] 未登录访问: {}", pjp.getSignature());
            throw new BusinessException(ErrorCode.FORBIDDEN, "请先登录");
        }

        // 2. requireAdmin 校验（部分敏感操作要求 ADMIN 身份）
        if (req.requireAdmin() && !"ADMIN".equals(user.userType)) {
            log.warn("[Permission] 非 ADMIN 身份访问敏感端点: userId={}, userType={}", user.userId, user.userType);
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅管理员可执行此操作");
        }

        // 3. 超级管理员放行（ROLE_SUPER_ADMIN 拥有全部权限）
        Set<String> roles = permissionLoader.loadRoles(user.userId);
        if (roles.contains("ROLE_SUPER_ADMIN")) {
            return pjp.proceed();
        }

        // 4. 权限码校验（OR 语义：任一满足即可）
        Set<String> perms = permissionLoader.loadPermissions(user.userId);
        for (String need : req.value()) {
            if (perms.contains(need)) {
                return pjp.proceed();
            }
        }

        // 5. 越权拒绝
        log.warn("[Permission] 越权: userId={}, need={}, has={}", user.userId, List.of(req.value()), perms);
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权限执行此操作");
    }
}
