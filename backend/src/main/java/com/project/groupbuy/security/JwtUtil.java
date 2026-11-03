package com.project.groupbuy.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（约束文档 8）
 *
 * V6 新增 claims：merchantId / pickupPointId
 * — 登录时从 user + pickup_point 表查出后塞 JWT，
 *   拦截器直接 parse，避免每次请求查 DB。
 */
@Component
public class JwtUtil {

    @Value("${business.jwt.secret}")
    private String secret;

    @Value("${business.jwt.expiration}")
    private long expiration;

    private SecretKey getKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** V6 新 —— 4 字段全塞 JWT */
    public String generate(Long userId, String userType, Long merchantId, Long pickupPointId) {
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("userId", userId)
                .claim("userType", userType)
                .claim("merchantId", merchantId)
                .claim("pickupPointId", pickupPointId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration * 1000))
                .signWith(getKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /** 老接口兼容 —— merchantId/pickupPointId 传 null */
    public String generate(Long userId, String userType) {
        return generate(userId, userType, null, null);
    }

    public Claims parse(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public Long getUserId(String token) {
        Claims c = parse(token);
        return c.get("userId", Long.class);
    }

    public String getUserType(String token) {
        Claims c = parse(token);
        return c.get("userType", String.class);
    }

    /** V6：从 JWT 取 merchantId */
    public Long getMerchantId(String token) {
        Claims c = parse(token);
        Object v = c.get("merchantId");
        return v == null ? null : ((Number) v).longValue();
    }

    /** V5：从 JWT 取 pickupPointId */
    public Long getPickupPointId(String token) {
        Claims c = parse(token);
        Object v = c.get("pickupPointId");
        return v == null ? null : ((Number) v).longValue();
    }

    public boolean isExpired(String token) {
        try {
            Claims claims = parse(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }
}
