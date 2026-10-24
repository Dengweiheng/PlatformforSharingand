package com.server.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.server.server.common.ApiResponse;
import com.server.server.dto.CommunityCommentCreateRequest;
import com.server.server.dto.CommunityPostCreateRequest;
import com.server.server.entity.CommunityComment;
import com.server.server.entity.CommunityPost;
import com.server.server.entity.SysUser;
import com.server.server.mapper.CommunityCommentMapper;
import com.server.server.mapper.CommunityPostMapper;
import com.server.server.mapper.SysUserMapper;
import com.server.server.mapper.UserActionMapper;
import com.server.server.mapper.ResourceInfoMapper;
import com.server.server.entity.UserAction;
import com.server.server.entity.ResourceInfo;
import com.server.server.service.UserRecommendationService;
import com.server.server.vo.CommunityCommentVO;
import com.server.server.vo.CommunityPostVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/community")
// 社区接口：支持讨论与资源动态发布，以及评论互动。
public class CommunityController {

    private final CommunityPostMapper communityPostMapper;
    private final CommunityCommentMapper communityCommentMapper;
    private final SysUserMapper sysUserMapper;
    private final UserActionMapper userActionMapper;
    private final ResourceInfoMapper resourceInfoMapper;
    private final UserRecommendationService userRecommendationService;

    public CommunityController(CommunityPostMapper communityPostMapper,
                               CommunityCommentMapper communityCommentMapper,
                               SysUserMapper sysUserMapper,
                               UserActionMapper userActionMapper,
                               ResourceInfoMapper resourceInfoMapper,
                               UserRecommendationService userRecommendationService) {
        this.communityPostMapper = communityPostMapper;
        this.communityCommentMapper = communityCommentMapper;
        this.sysUserMapper = sysUserMapper;
        this.userActionMapper = userActionMapper;
        this.resourceInfoMapper = resourceInfoMapper;
        this.userRecommendationService = userRecommendationService;
    }

    @GetMapping("/posts")
    public ApiResponse<List<CommunityPostVO>> listPosts(@RequestParam(required = false) String postType,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(defaultValue = "20") Integer limit) {
        int realLimit = Math.max(1, Math.min(limit, 100));
        LambdaQueryWrapper<CommunityPost> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(postType)) {
            qw.eq(CommunityPost::getPostType, postType.trim().toUpperCase());
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(CommunityPost::getTitle, keyword).or().like(CommunityPost::getContent, keyword));
        }
        qw.orderByDesc(CommunityPost::getCreatedAt).last("limit " + realLimit);
        List<CommunityPost> posts = communityPostMapper.selectList(qw);
        return ApiResponse.success(toPostVO(posts));
    }

    @PostMapping("/posts")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> createPost(@RequestBody CommunityPostCreateRequest request) {
        if (request == null || request.getUserId() == null || !StringUtils.hasText(request.getContent())) {
            return ApiResponse.error("参数不完整");
        }

        if (request.getResourceId() == null || request.getScore() == null) {
            return ApiResponse.error("请选择关联资源并评分");
        }

        if (request.getScore() < 1 || request.getScore() > 5) {
            return ApiResponse.error("评分必须在1-5之间");
        }

        SysUser user = sysUserMapper.selectById(request.getUserId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return ApiResponse.error("用户不存在或已被封禁");
        }

        // 检查资源
        ResourceInfo resource = resourceInfoMapper.selectById(request.getResourceId());
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }
        boolean isOwner = request.getUserId().equals(resource.getUploaderId());
        if (!isOwner && "PRIVATE".equals(resource.getVisibility())) {
            return ApiResponse.error("资源仅限上传者本人查看");
        }
        if (!isOwner && !"PASSED".equals(resource.getAuditStatus())) {
            return ApiResponse.error("资源未通过审核，暂不可评价");
        }

        // 检查重复评价
        Long existingCount = communityPostMapper.selectCount(
            new LambdaQueryWrapper<CommunityPost>()
                .eq(CommunityPost::getUserId, request.getUserId())
                .eq(CommunityPost::getResourceId, request.getResourceId())
                .isNotNull(CommunityPost::getScore)
        );
        if (existingCount != null && existingCount > 0) {
            return ApiResponse.error("您已评价过该资源");
        }

        String type = StringUtils.hasText(request.getPostType()) ? request.getPostType().trim().toUpperCase() : "DISCUSSION";

        CommunityPost post = new CommunityPost();
        post.setUserId(request.getUserId());
        post.setPostType(type);
        post.setResourceId(request.getResourceId());
        post.setTitle(request.getScore() + "分");
        post.setScore(request.getScore());
        post.setContent(request.getContent().trim());
        boolean postOk = communityPostMapper.insert(post) > 0;

        if (postOk) {
            UserAction action = new UserAction();
            action.setUserId(request.getUserId());
            action.setResourceId(request.getResourceId());
            action.setActionType("RATE");
            action.setScore(request.getScore());
            userActionMapper.insert(action);

            recalculateAverageScore(request.getResourceId());

            CompletableFuture.runAsync(() -> {
                try {
                    userRecommendationService.refreshRecommendation(request.getUserId(), 10);
                } catch (Exception ignored) {}
            });
        }

        return ApiResponse.success("评价成功", postOk);
    }

    @GetMapping("/posts/{postId}/comments")
    public ApiResponse<List<CommunityCommentVO>> listComments(@PathVariable Long postId) {
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null) {
            return ApiResponse.error("帖子不存在");
        }
        List<CommunityComment> comments = communityCommentMapper.selectList(new LambdaQueryWrapper<CommunityComment>()
                .eq(CommunityComment::getPostId, postId)
                .orderByAsc(CommunityComment::getCreatedAt));
        return ApiResponse.success(toCommentVO(comments));
    }

    @PostMapping("/posts/{postId}/comments")
    public ApiResponse<Boolean> createComment(@PathVariable Long postId,
                                              @RequestBody CommunityCommentCreateRequest request) {
        if (request == null || request.getUserId() == null || !StringUtils.hasText(request.getContent())) {
            return ApiResponse.error("参数不完整");
        }
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null) {
            return ApiResponse.error("帖子不存在");
        }

        SysUser user = sysUserMapper.selectById(request.getUserId());
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            return ApiResponse.error("用户不存在或已被封禁");
        }

        CommunityComment comment = new CommunityComment();
        comment.setPostId(postId);
        comment.setUserId(request.getUserId());
        comment.setContent(request.getContent().trim());
        return ApiResponse.success("评论成功", communityCommentMapper.insert(comment) > 0);
    }

    @DeleteMapping("/posts/{postId}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> deletePost(@PathVariable Long postId, @RequestParam Long userId) {
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null) {
            return ApiResponse.error("评价或讨论不存在");
        }
        SysUser user = sysUserMapper.selectById(userId);
        boolean isAdmin = user != null && "ADMIN".equals(user.getRole());
        boolean isOwner = post.getUserId().equals(userId);
        if (!isAdmin && !isOwner) {
            return ApiResponse.error("无权删除此条评价");
        }

        communityCommentMapper.delete(new LambdaQueryWrapper<CommunityComment>().eq(CommunityComment::getPostId, postId));
        boolean ok = communityPostMapper.deleteById(postId) > 0;

        if (ok && post.getResourceId() != null && post.getScore() != null) {
            userActionMapper.delete(new LambdaQueryWrapper<UserAction>()
                    .eq(UserAction::getUserId, post.getUserId())
                    .eq(UserAction::getResourceId, post.getResourceId())
                    .eq(UserAction::getActionType, "RATE"));

            recalculateAverageScore(post.getResourceId());

            CompletableFuture.runAsync(() -> {
                try {
                    userRecommendationService.refreshRecommendation(post.getUserId(), 10);
                } catch (Exception ignored) {}
            });
        }

        return ApiResponse.success("删除成功", ok);
    }

    @PutMapping("/posts/{postId}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> updatePost(@PathVariable Long postId, @RequestBody CommunityPostCreateRequest request) {
        if (request == null || request.getUserId() == null || !StringUtils.hasText(request.getContent())) {
            return ApiResponse.error("参数不完整");
        }
        CommunityPost post = communityPostMapper.selectById(postId);
        if (post == null) {
            return ApiResponse.error("评价不存在");
        }
        if (!post.getUserId().equals(request.getUserId())) {
            return ApiResponse.error("无权修改他人评价");
        }

        post.setContent(request.getContent().trim());
        if (request.getScore() != null) {
            if (request.getScore() < 1 || request.getScore() > 5) {
                return ApiResponse.error("评分必须在1-5之间");
            }
            post.setScore(request.getScore());
            post.setTitle(request.getScore() + "分");
        }

        boolean ok = communityPostMapper.updateById(post) > 0;

        if (ok && post.getResourceId() != null && post.getScore() != null) {
            UserAction action = userActionMapper.selectOne(new LambdaQueryWrapper<UserAction>()
                    .eq(UserAction::getUserId, post.getUserId())
                    .eq(UserAction::getResourceId, post.getResourceId())
                    .eq(UserAction::getActionType, "RATE")
                    .last("limit 1"));
            if (action != null) {
                action.setScore(post.getScore());
                userActionMapper.updateById(action);
            }

            recalculateAverageScore(post.getResourceId());

            CompletableFuture.runAsync(() -> {
                try {
                    userRecommendationService.refreshRecommendation(post.getUserId(), 10);
                } catch (Exception ignored) {}
            });
        }

        return ApiResponse.success("修改成功", ok);
    }

    private void recalculateAverageScore(Long resourceId) {
        if (resourceId == null) return;
        List<CommunityPost> posts = communityPostMapper.selectList(
            new LambdaQueryWrapper<CommunityPost>()
                .eq(CommunityPost::getResourceId, resourceId)
                .isNotNull(CommunityPost::getScore)
        );
        double avg = 0.0;
        if (!posts.isEmpty()) {
            double total = posts.stream().mapToInt(CommunityPost::getScore).sum();
            avg = total / posts.size();
        }
        ResourceInfo resource = resourceInfoMapper.selectById(resourceId);
        if (resource != null) {
            resource.setAverageScore(BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP));
            resourceInfoMapper.updateById(resource);
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ApiResponse<Boolean> deleteComment(@PathVariable Long commentId, @RequestParam Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || !"ADMIN".equals(user.getRole())) {
            return ApiResponse.error("仅管理员有权删除评论");
        }
        return ApiResponse.success("删除成功", communityCommentMapper.deleteById(commentId) > 0);
    }

    private List<CommunityPostVO> toPostVO(List<CommunityPost> posts) {
        if (posts == null || posts.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = posts.stream().map(CommunityPost::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, userIds))
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        Set<Long> postIds = posts.stream().map(CommunityPost::getId).collect(Collectors.toSet());
        Map<Long, Long> commentCountMap = communityCommentMapper.selectList(new LambdaQueryWrapper<CommunityComment>()
                .in(CommunityComment::getPostId, postIds))
                .stream().collect(Collectors.groupingBy(CommunityComment::getPostId, Collectors.counting()));

        return posts.stream().map(p -> {
            CommunityPostVO vo = new CommunityPostVO();
            vo.setId(p.getId());
            vo.setUserId(p.getUserId());
            SysUser u = userMap.get(p.getUserId());
            vo.setUsername(u == null ? "未知用户" : u.getUsername());
            vo.setUserRole(u == null ? "USER" : u.getRole());
            vo.setPostType(p.getPostType());
            vo.setResourceId(p.getResourceId());
            vo.setTitle(p.getTitle());
            vo.setScore(p.getScore());
            vo.setContent(p.getContent());
            vo.setCommentCount(commentCountMap.getOrDefault(p.getId(), 0L).intValue());
            vo.setCreatedAt(p.getCreatedAt());
            return vo;
        }).toList();
    }

    private List<CommunityCommentVO> toCommentVO(List<CommunityComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return new ArrayList<>();
        }
        Set<Long> userIds = comments.stream().map(CommunityComment::getUserId).collect(Collectors.toSet());
        Map<Long, SysUser> userMap = sysUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, userIds))
                .stream().collect(Collectors.toMap(SysUser::getId, u -> u, (a, b) -> a));

        return comments.stream().map(c -> {
            CommunityCommentVO vo = new CommunityCommentVO();
            vo.setId(c.getId());
            vo.setPostId(c.getPostId());
            vo.setUserId(c.getUserId());
            SysUser u = userMap.get(c.getUserId());
            vo.setUsername(u == null ? "未知用户" : u.getUsername());
            vo.setUserRole(u == null ? "USER" : u.getRole());
            vo.setContent(c.getContent());
            vo.setCreatedAt(c.getCreatedAt());
            return vo;
        }).toList();
    }
}
