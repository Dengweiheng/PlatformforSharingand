package com.server.server.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.server.server.entity.UserRecommendation;
import com.server.server.vo.RecommendationItemVO;

import java.util.List;

public interface UserRecommendationService extends IService<UserRecommendation> {

	List<RecommendationItemVO> recommendForUser(Long userId, Integer limit, boolean refresh);

	List<RecommendationItemVO> refreshRecommendation(Long userId, Integer limit);
}
