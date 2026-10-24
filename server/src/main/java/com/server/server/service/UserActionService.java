package com.server.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.server.server.entity.UserAction;

import java.util.List;

public interface UserActionService extends IService<UserAction> {

	boolean recordDownload(Long userId, Long resourceId);

	boolean recordFavorite(Long userId, Long resourceId);

	List<UserAction> listByUserId(Long userId);

	boolean canDownloadNow(Long userId, Long resourceId, int minIntervalSeconds);

	List<UserAction> listDownloadHistory(Long userId, Integer limit);

	boolean hasFavorited(Long userId, Long resourceId);

	void removeFavoriteActions(Long userId, Long resourceId);
}
