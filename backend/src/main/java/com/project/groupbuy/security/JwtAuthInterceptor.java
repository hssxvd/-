package com.project.groupbuy.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.result.R;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.List;

/**
 * JWT 认证拦截器 — 含手写白名单
 * 根因：preHandle 里手写了"无 Authorization 一律 401"逻辑，完全绕过 Spring MVC excludePathPatterns，
 * 所以必须自己维护一份 PUBLIC_PREFIXES 并用 getServletPath()（已剥 context-path）匹配。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    /** 公开路径前缀（servletPath 已剥 context-path /api） */
    private static final List<String> PUBLIC_PREFIXES = Arrays.asList(
            "/auth/login", "/auth/register", "/auth/check-username", "/auth/check-phone",
            "/auth/communities", "/auth/pickup-points",
            "/uploads/",
            "/products/categories/tree", "/products/attr-dict", "/products/filterable-attr-keys",
            "/products/attr-values", "/categories",
            "/reviews/sku/", "/reviews/avg/", "/reviews/distribution/",
            "/reviews/product/", "/reviews/avg-product/", "/reviews/distribution-product/",
            "/payments/callback/",
            "/actuator/", "/doc.html", "/v2/api-docs", "/webjars/", "/swagger-resources/", "/druid/", "/health"
    );

    private boolean isPublicPath(String servletPath) {
        if (servletPath == null) return false;
        for (String p : PUBLIC_PREFIXES) {
            if (p.endsWith("/")) { if (servletPath.startsWith(p)) return true; }
            else { if (servletPath.equals(p) || servletPath.startsWith(p + "/")) return true; }
        }
        return false;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String servletPath = request.getServletPath();
        if (isPublicPath(servletPath)) return true;

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(R.fail(401, "未登录")));
            return false;
        }
        String token = authHeader.substring(7);
        try {
            Long userId = jwtUtil.getUserId(token);
            String userType = jwtUtil.getUserType(token);
            Long merchantId = jwtUtil.getMerchantId(token);
            Long pickupPointId = jwtUtil.getPickupPointId(token);
            SecurityContext.set(userId, userType, merchantId, pickupPointId);
            return true;
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(R.fail(401, "token 无效或过期")));
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        SecurityContext.clear();
    }
}