package com.server.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.server.server.dto.LoginRequest;
import com.server.server.dto.RegisterRequest;
import com.server.server.dto.ResetPasswordRequest;
import com.server.server.entity.UserProfile;
import com.server.server.entity.SysUser;

public interface SysUserService extends IService<SysUser> {

	SysUser register(RegisterRequest request);

	SysUser login(LoginRequest request);

	boolean resetPassword(ResetPasswordRequest request);

	UserProfile getProfileByUserId(Long userId);

	boolean saveOrUpdateProfile(UserProfile profile);
}
