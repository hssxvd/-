package com.project.groupbuy.modules.auth.service;

import com.project.groupbuy.common.exception.BusinessException;
import com.project.groupbuy.common.result.ErrorCode;
import com.project.groupbuy.modules.auth.dto.UserProfileVO;
import com.project.groupbuy.modules.auth.entity.User;
import com.project.groupbuy.modules.auth.mapper.UserMapper;
import com.project.groupbuy.modules.community.entity.Community;
import com.project.groupbuy.modules.community.mapper.CommunityMapper;
import com.project.groupbuy.modules.product.entity.Merchant;
import com.project.groupbuy.modules.product.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户资料服务（个人中心）
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private final UserMapper userMapper;
    private final CommunityMapper communityMapper;
    private final MerchantMapper merchantMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 获取当前登录用户完整资料（含关联） */
    public UserProfileVO getProfile(Long userId) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");

        UserProfileVO vo = new UserProfileVO();
        vo.setId(u.getId());
        vo.setUsername(u.getUsername());
        vo.setNickname(u.getNickname());
        vo.setPhone(u.getPhone());
        vo.setAvatar(u.getAvatar());
        vo.setUserType(u.getUserType());
        vo.setStatus(u.getStatus());
        vo.setLastLoginTime(u.getLastLoginTime() != null ? u.getLastLoginTime().toString() : null);
        vo.setCreateTime(u.getCreateTime() != null ? u.getCreateTime().toString() : null);

        vo.setCommunityId(u.getCommunityId());
        if (u.getCommunityId() != null) {
            Community c = communityMapper.selectById(u.getCommunityId());
            if (c != null) vo.setCommunityName(c.getCommunityName());
        }
        vo.setMerchantId(u.getMerchantId());
        if (u.getMerchantId() != null) {
            Merchant m = merchantMapper.selectById(u.getMerchantId());
            if (m != null) vo.setMerchantName(m.getMerchantName());
        }
        return vo;
    }

    /** 更新昵称 + 手机号 */
    public User updateProfile(Long userId, String nickname, String phone) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");
        if (phone != null && !phone.isBlank()) {
            if (!phone.matches("^1[3-9]\\d{9}$")) {
                throw new BusinessException(ErrorCode.PARAM_ERROR, "手机号格式不正确");
            }
            u.setPhone(phone);
        }
        if (nickname != null) u.setNickname(nickname);
        userMapper.updateById(u);
        return u;
    }

    /** 绑定头像 URL */
    public User updateAvatar(Long userId, String avatarUrl) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");
        u.setAvatar(avatarUrl);
        userMapper.updateById(u);
        return u;
    }

    /** 修改密码（校验旧密码） */
    public void updatePassword(Long userId, String oldPassword, String newPassword) {
        User u = userMapper.selectById(userId);
        if (u == null) throw new BusinessException(ErrorCode.DATA_NOT_FOUND, "用户不存在");
        if (oldPassword == null || !passwordEncoder.matches(oldPassword, u.getPassword())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "旧密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "新密码至少 6 位");
        }
        u.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(u);
    }
}
