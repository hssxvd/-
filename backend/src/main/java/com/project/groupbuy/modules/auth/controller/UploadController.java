package com.project.groupbuy.modules.auth.controller;

import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 头像上传
 * - 单文件 MultipartFile 上传 → 本地存储 → 返回可访问 URL
 * - 后续由前端调 /users/me/avatar 绑定到当前用户
 */
@Slf4j
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadProperties props;

    /**
     * POST /upload/avatar
     * Content-Type: multipart/form-data, field name: file
     * 返回: { url: "/uploads/avatars/xxx.png" }
     */
    @PostMapping("/avatar")
    public R<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "请选择文件");
        }
        // 大小校验
        if (file.getSize() > props.getMaxSize()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件超过 2MB 限制");
        }
        // 扩展名校验
        String origName = file.getOriginalFilename();
        String ext = "";
        if (origName != null && origName.contains(".")) {
            ext = origName.substring(origName.lastIndexOf('.') + 1).toLowerCase();
        }
        if (!props.getAllowTypes().contains(ext)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅支持 " + props.getAllowTypes() + " 格式");
        }

        // 子目录按日期分（uploads/avatars/2026-10-26/）
        String dateSub = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        Path dirPath = Paths.get(props.getDir(), dateSub).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", dirPath, e);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "服务器存储异常");
        }

        // 文件名: {userId}_{timestamp}.{ext}
        Long uid = SecurityContext.getUserId();
        String safeName = (uid == null ? "anon" : uid) + "_" + System.currentTimeMillis() + "." + ext;
        Path targetPath = dirPath.resolve(safeName).normalize();

        // 安全：targetPath 必须在 dirPath 内（防 ../ 路径穿越）
        if (!targetPath.startsWith(dirPath)) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "非法文件路径");
        }

        try {
            file.transferTo(targetPath.toFile());
        } catch (IOException e) {
            log.error("写入文件失败: {}", targetPath, e);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "保存文件失败");
        }

        // 返回可访问 URL：/uploads/avatars/{dateSub}/{safeName}
        String url = "/uploads/avatars/" + dateSub + "/" + safeName;
        log.info("头像上传成功: userId={}, size={}, url={}", uid, file.getSize(), url);
        return R.ok(Map.of("url", url));
    }
}
