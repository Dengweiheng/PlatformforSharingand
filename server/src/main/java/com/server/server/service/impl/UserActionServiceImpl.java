package com.server.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.server.server.entity.UserAction;
import com.server.server.mapper.UserActionMapper;
import com.server.server.service.UserActionService;
import com.server.server.service.UserRecommendationService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class UserActionServiceImpl extends ServiceImpl<UserActionMapper, UserAction> implements UserActionService {

	private final UserRecommendationService userRecommendationService;

	public UserActionServiceImpl(UserRecommendationService userRecommendationService) {
		this.userRecommendationService = userRecommendationService;
	}

	@Override
	public boolean recordDownload(Long userId, Long resourceId) {
		return saveAction(userId, resourceId, "DOWNLOAD");
	}

	@Override
	public boolean recordFavorite(Long userId, Long resourceId) {
		return saveAction(userId, resourceId, "FAVORITE");
	}

	@Override
	public List<UserAction> listByUserId(Long userId) {
		return this.list(new LambdaQueryWrapper<UserAction>()
				.eq(UserAction::getUserId, userId)
				.orderByDesc(UserAction::getCreatedAt));
	}

	@Override
	public boolean canDownloadNow(Long userId, Long resourceId, int minIntervalSeconds) {
		if (userId == null || resourceId == null) {
			return false;
		}
		UserAction lastDownload = this.getOne(new LambdaQueryWrapper<UserAction>()
				.eq(UserAction::getUserId, userId)
				.eq(UserAction::getResourceId, resourceId)
				.eq(UserAction::getActionType, "DOWNLOAD")
				.orderByDesc(UserAction::getCreatedAt)
				.last("limit 1"));
		if (lastDownload == null || lastDownload.getCreatedAt() == null) {
			return true;
		}
		return lastDownload.getCreatedAt().isBefore(LocalDateTime.now().minusSeconds(Math.max(1, minIntervalSeconds)));
	}

	@Override
	public List<UserAction> listDownloadHistory(Long userId, Integer limit) {
		int realLimit = limit == null ? 20 : Math.max(1, Math.min(limit, 200));
		return this.list(new LambdaQueryWrapper<UserAction>()
				.eq(UserAction::getUserId, userId)
				.eq(UserAction::getActionType, "DOWNLOAD")
				.orderByDesc(UserAction::getCreatedAt)
				.last("limit " + realLimit));
	}

	@Override
	public boolean hasFavorited(Long userId, Long resourceId) {
		if (userId == null || resourceId == null) {
			return false;
		}
		Long cnt = this.count(new LambdaQueryWrapper<UserAction>()
				.eq(UserAction::getUserId, userId)
				.eq(UserAction::getResourceId, resourceId)
				.eq(UserAction::getActionType, "FAVORITE"));
		return cnt != null && cnt > 0;
	}

	@Override
	public void removeFavoriteActions(Long userId, Long resourceId) {
		if (userId == null || resourceId == null) {
			return;
		}
		this.remove(new LambdaQueryWrapper<UserAction>()
				.eq(UserAction::getUserId, userId)
				.eq(UserAction::getResourceId, resourceId)
				.eq(UserAction::getActionType, "FAVORITE"));
	}

	private boolean saveAction(Long userId, Long resourceId, String actionType) {
		if (userId == null || resourceId == null) {
			return false;
		}
		UserAction action = new UserAction();
		action.setUserId(userId);
		action.setResourceId(resourceId);
		action.setActionType(actionType);
		boolean ok = this.save(action);
		if (ok) {
			// 行为写入后异步刷新推荐，避免阻塞当前请求。
			CompletableFuture.runAsync(() -> {
				try {
					userRecommendationService.refreshRecommendation(userId, 10);
				} catch (Exception ignored) {
				}
			});
		}
		return ok;
	}
}
