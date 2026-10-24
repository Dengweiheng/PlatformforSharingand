package com.server.server.controller;

import com.server.server.common.ApiResponse;
import com.server.server.service.UserRecommendationService;
import com.server.server.vo.RecommendationItemVO;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
// 推荐接口：提供个性化推荐查询与手动刷新。
public class RecommendationController {

    private final UserRecommendationService userRecommendationService;

    public RecommendationController(UserRecommendationService userRecommendationService) {
        this.userRecommendationService = userRecommendationService;
    }

    @GetMapping("/{userId}")
    public ApiResponse<List<RecommendationItemVO>> list(@PathVariable Long userId,
                                                        @RequestParam(defaultValue = "10") Integer limit,
                                                        @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResponse.success(userRecommendationService.recommendForUser(userId, limit, refresh));
    }

    @PostMapping("/{userId}/refresh")
    public ApiResponse<List<RecommendationItemVO>> refresh(@PathVariable Long userId,
                                                           @RequestParam(defaultValue = "10") Integer limit) {
        return ApiResponse.success(userRecommendationService.refreshRecommendation(userId, limit));
    }
}
