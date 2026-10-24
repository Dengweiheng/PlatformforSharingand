package com.server.server.controller;

import com.server.server.common.ApiResponse;
import com.server.server.dto.LoginRequest;
import com.server.server.dto.RegisterRequest;
import com.server.server.dto.ResetPasswordRequest;
import com.server.server.dto.UserProfileUpdateRequest;
import com.server.server.entity.SysUser;
import com.server.server.entity.UserProfile;
import com.server.server.service.SysUserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
// 用户中心接口：提供注册、登录、密码找回和个人资料管理。
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @PostMapping("/register")
    public ApiResponse<SysUser> register(@RequestBody RegisterRequest request) {
        SysUser user = sysUserService.register(request);
        if (user == null) {
            return ApiResponse.error("注册失败：用户名/邮箱/手机号重复，或参数不完整");
        }
        return ApiResponse.success("注册成功", user);
    }

    @PostMapping("/login")
    public ApiResponse<SysUser> login(@RequestBody LoginRequest request) {
        SysUser user = sysUserService.login(request);
        if (user == null) {
            return ApiResponse.error("登录失败：账号或密码错误");
        }
        return ApiResponse.success("登录成功", user);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Boolean> resetPassword(@RequestBody ResetPasswordRequest request) {
        boolean ok = sysUserService.resetPassword(request);
        if (!ok) {
            return ApiResponse.error("找回密码失败：账号不存在或验证码错误");
        }
        return ApiResponse.success("密码重置成功", true);
    }

    @PostMapping("/send-code")
    public ApiResponse<String> sendCode(@RequestParam String account) {
        // 演示环境：直接返回成功提示
        return ApiResponse.success("验证码已发送至您的绑定手机/邮箱（演示：123456）", "123456");
    }

    @GetMapping("/{userId}/profile")
    public ApiResponse<UserProfile> getProfile(@PathVariable Long userId) {
        UserProfile profile = sysUserService.getProfileByUserId(userId);
        return ApiResponse.success(profile);
    }

    @PutMapping("/profile")
    public ApiResponse<Boolean> updateProfile(@RequestBody UserProfileUpdateRequest request) {
        UserProfile profile = new UserProfile();
        profile.setUserId(request.getUserId());
        profile.setNickname(request.getNickname());
        profile.setAvatarUrl(request.getAvatarUrl());
        profile.setMajorName(request.getMajorName());
        profile.setInterestTags(request.getInterestTags());
        boolean ok = sysUserService.saveOrUpdateProfile(profile);
        if (!ok) {
            return ApiResponse.error("个人资料保存失败");
        }
        return ApiResponse.success("个人资料保存成功", true);
    }
}
