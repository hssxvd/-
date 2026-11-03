package com.project.groupbuy.config;

import com.project.groupbuy.security.JwtAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web MVC 配置：拦截器 + CORS + 静态资源
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;
    private final UploadProperties uploadProperties;

    /**
     * JWT 拦截器（白名单：公开接口 + 支付回调 + 接口文档 + 静态上传资源）
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        // 认证公开
                        "/auth/login",
                        "/auth/register",
                        "/auth/check-username",
                        "/auth/check-phone",
                        "/auth/communities",
                        "/auth/pickup-points",
                        // 头像上传后的文件访问（静态资源，无需 token）
                        "/uploads/**",
                        // 商品浏览公开（匿名消费者可访问的具体端点，不是 /** 整包）
                        "/products/categories/tree",
                        "/products/attr-dict",
                        "/products/filterable-attr-keys",
                        "/products/attr-values",
                        "/categories/**",
                        // 评价公开（看评价无需登录，写评价 POST 仍需鉴权，不在这里放行）
                        "/reviews/sku/**",
                        "/reviews/avg/**",
                        "/reviews/distribution/**",
                        "/reviews/product/**",
                        "/reviews/avg-product/**",
                        "/reviews/distribution-product/**",
                        // 支付回调（第三方/模拟回调无 token）
                        "/payments/callback/**",
                        // Actuator
                        "/actuator/**",
                        // Knife4j 接口文档
                        "/doc.html",
                        "/v2/api-docs/**",
                        "/webjars/**",
                        "/swagger-resources/**",
                        // Druid 监控
                        "/druid/**",
                        // 健康检查
                        "/health"
                );
    }

    /**
     * 静态资源映射：/uploads/** → 本地 upload.dir
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path uploadPath = Paths.get(uploadProperties.getDir()).toAbsolutePath().normalize();
        // handler 是 /uploads/**，Spring 会剥掉 /uploads/ 再拼到 location 上
        // 所以 location 必须是 uploads 的父目录（backend/uploads/），否则多拼一层 avatars/
        Path handlerRoot = uploadPath.getParent() != null ? uploadPath.getParent() : uploadPath;
        String fileUrl = "file:" + handlerRoot.toString().replace('\\', '/') + "/";
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(fileUrl);
    }

    /**
     * CORS（前后端分离开发）
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
