package com.project.groupbuy.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 文件上传配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    /** 上传目录（绝对路径） */
    private String dir = "D:/Project/社区团购电商平台/backend/uploads/avatars";

    /** 单文件最大 byte */
    private long maxSize = 2 * 1024 * 1024;

    /** 允许的扩展名 */
    private List<String> allowTypes = List.of("jpg", "jpeg", "png", "gif", "webp");
}