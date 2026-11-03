package com.project.groupbuy.modules.auth.controller;

import com.project.groupbuy.common.context.SecurityContext;
import com.project.groupbuy.common.result.R;
import com.project.groupbuy.modules.auth.dto.UserProfileVO;
import com.project.groupbuy.modules.auth.entity.User;
import com.project.groupbuy.modules.auth.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户个人中心（当前登录用户资料）
 * 所有接口自动从 SecurityContext 取 userId — 不信任前端传的 id
 */
@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService profileService;

    /** 获取当前登录用户完整资料 */
    @GetMapping("/profile")
    public R<UserProfileVO> profile() {
        return R.ok(profileService.getProfile(SecurityContext.getUserId()));
    }

    /** 更新昵称 / 手机号 */
    @PutMapping
    public R<User> update(@RequestBody Map<String, String> body) {
        String nickname = body.get("nickname");
        String phone = body.get("phone");
        return R.ok(profileService.updateProfile(SecurityContext.getUserId(), nickname, phone));
    }

    /** 绑定头像 URL（前端先 /upload/avatar 拿到 url 再调这个） */
    @PostMapping("/avatar")
    public R<User> setAvatar(@RequestBody Map<String, String> body) {
        String url = body.get("avatarUrl");
        if (url == null || url.isBlank()) {
            return R.fail(1001, "avatarUrl 不能为空");
        }
        return R.ok(profileService.updateAvatar(SecurityContext.getUserId(), url));
    }

    /** 修改密码 */
    @PostMapping("/password")
    public R<Map<String, String>> changePassword(@RequestBody Map<String, String> body) {
        String oldPwd = body.get("oldPassword");
        String newPwd = body.get("newPassword");
        profileService.updatePassword(SecurityContext.getUserId(), oldPwd, newPwd);
        return R.ok(Map.of("msg", "密码修改成功"));
    }
}
