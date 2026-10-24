package com.server.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.server.server.dto.LoginRequest;
import com.server.server.dto.RegisterRequest;
import com.server.server.dto.ResetPasswordRequest;
import com.server.server.entity.SysUser;
import com.server.server.entity.UserProfile;
import com.server.server.mapper.SysUserMapper;
import com.server.server.mapper.UserProfileMapper;
import com.server.server.service.SysUserService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

	private final PasswordEncoder passwordEncoder;
	private final UserProfileMapper userProfileMapper;

	public SysUserServiceImpl(PasswordEncoder passwordEncoder, UserProfileMapper userProfileMapper) {
		this.passwordEncoder = passwordEncoder;
		this.userProfileMapper = userProfileMapper;
	}

	@Override
	public SysUser register(RegisterRequest request) {
		if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
			return null;
		}
		if (!StringUtils.hasText(request.getEmail()) && !StringUtils.hasText(request.getPhone())) {
			return null;
		}

		SysUser existedByUsername = this.getOne(
				new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, request.getUsername()));
		if (existedByUsername != null) {
			return null;
		}

		if (StringUtils.hasText(request.getEmail())) {
			SysUser existedByEmail = this.getOne(
					new LambdaQueryWrapper<SysUser>().eq(SysUser::getEmail, request.getEmail()));
			if (existedByEmail != null) {
				return null;
			}
		}

		if (StringUtils.hasText(request.getPhone())) {
			SysUser existedByPhone = this.getOne(
					new LambdaQueryWrapper<SysUser>().eq(SysUser::getPhone, request.getPhone()));
			if (existedByPhone != null) {
				return null;
			}
		}

		SysUser user = new SysUser();
		user.setUsername(request.getUsername());
		user.setPassword(passwordEncoder.encode(request.getPassword()));
		user.setEmail(request.getEmail());
		user.setPhone(request.getPhone());
		user.setRole("USER");
		user.setStatus(1);
		user.setCreatedAt(LocalDateTime.now());
		this.save(user);

		UserProfile profile = new UserProfile();
		profile.setUserId(user.getId());
		profile.setNickname(request.getUsername());
		userProfileMapper.insert(profile);

		user.setPassword(null);
		return user;
	}

	@Override
	public SysUser login(LoginRequest request) {
		if (request == null || !StringUtils.hasText(request.getAccount()) || !StringUtils.hasText(request.getPassword())) {
			return null;
		}

		SysUser user = this.getOne(
				new LambdaQueryWrapper<SysUser>()
						.eq(SysUser::getUsername, request.getAccount())
						.or()
						.eq(SysUser::getEmail, request.getAccount())
						.or()
						.eq(SysUser::getPhone, request.getAccount()));

		if (user == null) {
			return null;
		}
		if (user.getStatus() == null || user.getStatus() == 0) {
			return null;
		}
		if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
			return null;
		}

		user.setPassword(null);
		return user;
	}

	@Override
	public boolean resetPassword(ResetPasswordRequest request) {
		if (request == null || !StringUtils.hasText(request.getAccount()) || !StringUtils.hasText(request.getNewPassword())) {
			return false;
		}

		// 演示环境：简单校验验证码
		if (!"123456".equals(request.getCaptcha())) {
			return false;
		}

		SysUser user = this.getOne(
				new LambdaQueryWrapper<SysUser>()
						.eq(SysUser::getUsername, request.getAccount())
						.or()
						.eq(SysUser::getEmail, request.getAccount())
						.or()
						.eq(SysUser::getPhone, request.getAccount()));
		if (user == null) {
			return false;
		}

		user.setPassword(passwordEncoder.encode(request.getNewPassword()));
		return this.updateById(user);
	}

	@Override
	public UserProfile getProfileByUserId(Long userId) {
		return userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>().eq(UserProfile::getUserId, userId).last("limit 1"));
	}

	@Override
	public boolean saveOrUpdateProfile(UserProfile profile) {
		if (profile == null || profile.getUserId() == null) {
			return false;
		}
		UserProfile existed = getProfileByUserId(profile.getUserId());
		if (existed == null) {
			return userProfileMapper.insert(profile) > 0;
		}

		profile.setId(existed.getId());
		return userProfileMapper.updateById(profile) > 0;
	}
}
