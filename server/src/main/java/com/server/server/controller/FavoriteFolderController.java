package com.server.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.server.server.common.ApiResponse;
import com.server.server.dto.BatchRemoveFavoriteRequest;
import com.server.server.entity.FavoriteFolder;
import com.server.server.entity.FavoriteItem;
import com.server.server.entity.ResourceInfo;
import com.server.server.mapper.FavoriteFolderMapper;
import com.server.server.mapper.FavoriteItemMapper;
import com.server.server.mapper.ResourceInfoMapper;
import com.server.server.service.UserActionService;
import com.server.server.service.UserRecommendationService;
import com.server.server.vo.FavoriteFolderDetailVO;
import com.server.server.vo.FavoriteFolderItemVO;
import com.server.server.vo.InteractionRecordVO;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/folders")
// 收藏夹接口：提供收藏夹管理、批量移除和个人交互记录展示。
public class FavoriteFolderController {

    private final FavoriteFolderMapper favoriteFolderMapper;
    private final FavoriteItemMapper favoriteItemMapper;
    private final ResourceInfoMapper resourceInfoMapper;
    private final UserActionService userActionService;
    private final UserRecommendationService userRecommendationService;

    public FavoriteFolderController(FavoriteFolderMapper favoriteFolderMapper,
                                    FavoriteItemMapper favoriteItemMapper,
                                    ResourceInfoMapper resourceInfoMapper,
                                    UserActionService userActionService,
                                    UserRecommendationService userRecommendationService) {
        this.favoriteFolderMapper = favoriteFolderMapper;
        this.favoriteItemMapper = favoriteItemMapper;
        this.resourceInfoMapper = resourceInfoMapper;
        this.userActionService = userActionService;
        this.userRecommendationService = userRecommendationService;
    }

    @PostMapping
    public ApiResponse<Boolean> createFolder(@RequestParam Long userId, @RequestParam String folderName) {
        if (!StringUtils.hasText(folderName)) {
            return ApiResponse.error("收藏夹名称不能为空");
        }
        FavoriteFolder exist = favoriteFolderMapper.selectOne(new LambdaQueryWrapper<FavoriteFolder>()
                .eq(FavoriteFolder::getUserId, userId)
                .eq(FavoriteFolder::getFolderName, folderName.trim())
                .last("limit 1"));
        if (exist != null) {
            return ApiResponse.error("收藏夹名称已存在");
        }

        FavoriteFolder folder = new FavoriteFolder();
        folder.setUserId(userId);
        folder.setFolderName(folderName.trim());
        return ApiResponse.success("创建成功", favoriteFolderMapper.insert(folder) > 0);
    }

    @GetMapping("/user/{userId}")
    public ApiResponse<List<FavoriteFolder>> listFolders(@PathVariable Long userId) {
        List<FavoriteFolder> list = favoriteFolderMapper.selectList(new LambdaQueryWrapper<FavoriteFolder>()
                .eq(FavoriteFolder::getUserId, userId)
                .orderByDesc(FavoriteFolder::getCreatedAt));
        return ApiResponse.success(list);
    }

    @PostMapping("/{folderId}/items")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> addItem(@PathVariable Long folderId,
                                        @RequestParam Long userId,
                                        @RequestParam Long resourceId) {
        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null || !folder.getUserId().equals(userId)) {
            return ApiResponse.error("收藏夹不存在或无权限");
        }
        ResourceInfo resource = resourceInfoMapper.selectById(resourceId);
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }
        if ("PRIVATE".equals(resource.getVisibility()) && !userId.equals(resource.getUploaderId())) {
            return ApiResponse.error("私有资源无权收藏");
        }

        FavoriteItem existed = favoriteItemMapper.selectOne(new LambdaQueryWrapper<FavoriteItem>()
                .eq(FavoriteItem::getFolderId, folderId)
                .eq(FavoriteItem::getResourceId, resourceId)
                .last("limit 1"));
        if (existed != null) {
            return ApiResponse.error("该资源已在收藏夹中");
        }

        boolean userAlreadyFavorited = hasUserFavoritedResource(userId, resourceId);

        FavoriteItem item = new FavoriteItem();
        item.setFolderId(folderId);
        item.setUserId(userId);
        item.setResourceId(resourceId);
        favoriteItemMapper.insert(item);

        // 同一用户第一次收藏该资源才增加收藏计数并记录收藏行为。
        if (!userAlreadyFavorited) {
            resource.setFavoriteCount((resource.getFavoriteCount() == null ? 0 : resource.getFavoriteCount()) + 1);
            resourceInfoMapper.updateById(resource);
            userActionService.recordFavorite(userId, resourceId);
        }
        return ApiResponse.success("添加成功", true);
    }

    @DeleteMapping("/{folderId}/items/{itemId}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> removeItem(@PathVariable Long folderId,
                                           @PathVariable Long itemId,
                                           @RequestParam Long userId) {
        FavoriteItem item = favoriteItemMapper.selectById(itemId);
        if (item == null || !item.getFolderId().equals(folderId) || !item.getUserId().equals(userId)) {
            return ApiResponse.error("收藏记录不存在或无权限");
        }

        favoriteItemMapper.deleteById(itemId);
        ResourceInfo resource = resourceInfoMapper.selectById(item.getResourceId());
        boolean stillFavorited = hasUserFavoritedResource(userId, item.getResourceId());
        if (!stillFavorited) {
            if (resource != null && resource.getFavoriteCount() != null && resource.getFavoriteCount() > 0) {
                resource.setFavoriteCount(resource.getFavoriteCount() - 1);
                resourceInfoMapper.updateById(resource);
            }
            userActionService.removeFavoriteActions(userId, item.getResourceId());
            refreshRecommendQuietly(userId);
        }
        return ApiResponse.success("移除成功", true);
    }

    @DeleteMapping("/{folderId}/items/batch")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> batchRemove(@PathVariable Long folderId,
                                            @RequestBody BatchRemoveFavoriteRequest request) {
        if (request == null || request.getUserId() == null || request.getItemIds() == null || request.getItemIds().isEmpty()) {
            return ApiResponse.error("参数不完整");
        }

        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null || !folder.getUserId().equals(request.getUserId())) {
            return ApiResponse.error("收藏夹不存在或无权限");
        }

        List<FavoriteItem> items = favoriteItemMapper.selectList(new LambdaQueryWrapper<FavoriteItem>()
                .eq(FavoriteItem::getFolderId, folderId)
                .eq(FavoriteItem::getUserId, request.getUserId())
                .in(FavoriteItem::getId, request.getItemIds()));

        if (items.isEmpty()) {
            return ApiResponse.success("无可移除项", true);
        }

        Set<Long> resourceIds = items.stream().map(FavoriteItem::getResourceId).collect(Collectors.toSet());
        favoriteItemMapper.delete(new LambdaQueryWrapper<FavoriteItem>()
                .eq(FavoriteItem::getFolderId, folderId)
                .eq(FavoriteItem::getUserId, request.getUserId())
                .in(FavoriteItem::getId, request.getItemIds()));

        List<ResourceInfo> resources = resourceInfoMapper.selectList(new LambdaQueryWrapper<ResourceInfo>()
                .in(ResourceInfo::getId, resourceIds));

        Set<Long> touchedResourceIds = new HashSet<>();
        for (ResourceInfo r : resources) {
            if (!hasUserFavoritedResource(request.getUserId(), r.getId())) {
                int old = r.getFavoriteCount() == null ? 0 : r.getFavoriteCount();
                r.setFavoriteCount(Math.max(0, old - 1));
                resourceInfoMapper.updateById(r);
                userActionService.removeFavoriteActions(request.getUserId(), r.getId());
                touchedResourceIds.add(r.getId());
            }
        }

        if (!touchedResourceIds.isEmpty()) {
            refreshRecommendQuietly(request.getUserId());
        }

        return ApiResponse.success("批量移除成功", true);
    }

    @GetMapping("/{folderId}/items")
    public ApiResponse<FavoriteFolderDetailVO> folderDetail(@PathVariable Long folderId, @RequestParam Long userId) {
        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null || !folder.getUserId().equals(userId)) {
            return ApiResponse.error("收藏夹不存在或无权限");
        }

        List<FavoriteItem> itemList = favoriteItemMapper.selectList(new LambdaQueryWrapper<FavoriteItem>()
                .eq(FavoriteItem::getFolderId, folderId)
                .eq(FavoriteItem::getUserId, userId)
                .orderByDesc(FavoriteItem::getCreatedAt));

        Set<Long> ids = new HashSet<>(itemList.stream().map(FavoriteItem::getResourceId).toList());
        List<ResourceInfo> resources = ids.isEmpty() ? new ArrayList<>() : resourceInfoMapper.selectList(
                new LambdaQueryWrapper<ResourceInfo>().in(ResourceInfo::getId, ids)
        );

        FavoriteFolderDetailVO vo = new FavoriteFolderDetailVO();
        vo.setFolder(folder);
        vo.setResources(resources);
        return ApiResponse.success(vo);
    }

    @GetMapping("/{folderId}/items/list")
    public ApiResponse<List<FavoriteFolderItemVO>> folderItemList(@PathVariable Long folderId,
                                                                   @RequestParam Long userId) {
        FavoriteFolder folder = favoriteFolderMapper.selectById(folderId);
        if (folder == null || !folder.getUserId().equals(userId)) {
            return ApiResponse.error("收藏夹不存在或无权限");
        }

        List<FavoriteItem> items = favoriteItemMapper.selectList(new LambdaQueryWrapper<FavoriteItem>()
                .eq(FavoriteItem::getFolderId, folderId)
                .eq(FavoriteItem::getUserId, userId)
                .orderByDesc(FavoriteItem::getCreatedAt));

        if (items.isEmpty()) {
            return ApiResponse.success(new ArrayList<>());
        }

        Set<Long> resourceIds = items.stream().map(FavoriteItem::getResourceId).collect(Collectors.toSet());
        Map<Long, ResourceInfo> resourceMap = resourceInfoMapper.selectList(new LambdaQueryWrapper<ResourceInfo>()
                        .in(ResourceInfo::getId, resourceIds))
                .stream().collect(Collectors.toMap(ResourceInfo::getId, r -> r, (a, b) -> a));

        List<FavoriteFolderItemVO> voList = items.stream().map(i -> {
            FavoriteFolderItemVO vo = new FavoriteFolderItemVO();
            vo.setItemId(i.getId());
            vo.setResourceId(i.getResourceId());
            ResourceInfo resource = resourceMap.get(i.getResourceId());
            if (resource != null) {
                vo.setTitle(resource.getTitle());
                vo.setCoverUrl(resource.getCoverUrl());
                vo.setFileUrl(resource.getFileUrl());
            }
            vo.setCreatedAt(i.getCreatedAt());
            return vo;
        }).toList();

        return ApiResponse.success(voList);
    }

    @GetMapping("/interaction/{userId}")
    public ApiResponse<InteractionRecordVO> interactionRecords(@PathVariable Long userId,
                                                                @RequestParam(defaultValue = "20") Integer limit) {
        InteractionRecordVO vo = new InteractionRecordVO();
        vo.setDownloadHistory(userActionService.listDownloadHistory(userId, limit));

        List<FavoriteFolder> folders = favoriteFolderMapper.selectList(new LambdaQueryWrapper<FavoriteFolder>()
                .eq(FavoriteFolder::getUserId, userId)
                .orderByDesc(FavoriteFolder::getCreatedAt));

        if (folders.isEmpty()) {
            return ApiResponse.success(vo);
        }

        Set<Long> folderIds = folders.stream().map(FavoriteFolder::getId).collect(Collectors.toSet());
        List<FavoriteItem> allItems = favoriteItemMapper.selectList(new LambdaQueryWrapper<FavoriteItem>()
            .eq(FavoriteItem::getUserId, userId)
            .in(FavoriteItem::getFolderId, folderIds)
            .orderByDesc(FavoriteItem::getCreatedAt));

        Map<Long, List<FavoriteItem>> folderItemMap = allItems.stream()
            .collect(Collectors.groupingBy(FavoriteItem::getFolderId));

        Set<Long> resourceIds = allItems.stream().map(FavoriteItem::getResourceId).collect(Collectors.toSet());
        Map<Long, ResourceInfo> allResourceMap = resourceIds.isEmpty() ? Map.of() : resourceInfoMapper.selectList(
            new LambdaQueryWrapper<ResourceInfo>().in(ResourceInfo::getId, resourceIds)
        ).stream().collect(Collectors.toMap(ResourceInfo::getId, r -> r, (a, b) -> a));

        List<FavoriteFolderDetailVO> detailList = new ArrayList<>();
        for (FavoriteFolder folder : folders) {
            FavoriteFolderDetailVO detail = new FavoriteFolderDetailVO();
            detail.setFolder(folder);

            List<FavoriteItem> itemList = folderItemMap.getOrDefault(folder.getId(), List.of());
            List<ResourceInfo> folderResources = itemList.stream()
                .map(i -> allResourceMap.get(i.getResourceId()))
                .filter(r -> r != null)
                .toList();
            detail.setResources(folderResources);
            detailList.add(detail);
        }

        vo.setFavoriteFolders(detailList);
        return ApiResponse.success(vo);
    }

    private boolean hasUserFavoritedResource(Long userId, Long resourceId) {
        Long cnt = favoriteItemMapper.selectCount(new LambdaQueryWrapper<FavoriteItem>()
                .eq(FavoriteItem::getUserId, userId)
                .eq(FavoriteItem::getResourceId, resourceId));
        return cnt != null && cnt > 0;
    }

    private void refreshRecommendQuietly(Long userId) {
        try {
            userRecommendationService.refreshRecommendation(userId, 10);
        } catch (Exception ignored) {
        }
    }
}
