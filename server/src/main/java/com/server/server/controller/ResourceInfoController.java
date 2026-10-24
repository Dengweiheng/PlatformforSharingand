package com.server.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.server.server.common.ApiResponse;
import com.server.server.entity.ResourceFileVersion;
import com.server.server.entity.ResourceInfo;
import com.server.server.entity.ResourceTag;
import com.server.server.entity.ResourceTagRel;
import com.server.server.entity.FavoriteItem;
import com.server.server.entity.UserAction;
import com.server.server.entity.UserRecommendation;
import com.server.server.mapper.FavoriteItemMapper;
import com.server.server.mapper.ResourceFileVersionMapper;
import com.server.server.mapper.ResourceTagMapper;
import com.server.server.mapper.ResourceTagRelMapper;
import com.server.server.mapper.UserActionMapper;
import com.server.server.mapper.UserRecommendationMapper;
import com.server.server.service.LocalFileStorageService;
import com.server.server.service.ResourceFileVersionService;
import com.server.server.service.ResourceInfoService;
import com.server.server.service.UserActionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/resources")
// 资源接口：提供资源上传、版本管理、可见性管理、预览与行为记录。
public class ResourceInfoController {

    private final ResourceInfoService resourceInfoService;
    private final UserActionService userActionService;
    private final LocalFileStorageService localFileStorageService;
    private final ResourceFileVersionService resourceFileVersionService;
    private final ResourceFileVersionMapper resourceFileVersionMapper;
    private final FavoriteItemMapper favoriteItemMapper;
    private final ResourceTagMapper resourceTagMapper;
    private final ResourceTagRelMapper resourceTagRelMapper;
    private final UserActionMapper userActionMapper;
    private final UserRecommendationMapper userRecommendationMapper;

    @Value("${app.download.min-interval-seconds:30}")
    private int minDownloadIntervalSeconds;

    private static final Set<String> RESOURCE_FILE_EXT = Set.of(
            "pdf", "doc", "docx", "ppt", "pptx", "mp4", "avi", "mov", "mkv", "txt"
    );

    private static final Set<String> COVER_EXT = Set.of("jpg", "jpeg", "png", "webp");

    public ResourceInfoController(ResourceInfoService resourceInfoService,
                                  UserActionService userActionService,
                                  LocalFileStorageService localFileStorageService,
                                  ResourceFileVersionService resourceFileVersionService,
                                  ResourceFileVersionMapper resourceFileVersionMapper,
                                  FavoriteItemMapper favoriteItemMapper,
                                  ResourceTagMapper resourceTagMapper,
                                  ResourceTagRelMapper resourceTagRelMapper,
                                  UserActionMapper userActionMapper,
                                  UserRecommendationMapper userRecommendationMapper) {
        this.resourceInfoService = resourceInfoService;
        this.userActionService = userActionService;
        this.localFileStorageService = localFileStorageService;
        this.resourceFileVersionService = resourceFileVersionService;
        this.resourceFileVersionMapper = resourceFileVersionMapper;
        this.favoriteItemMapper = favoriteItemMapper;
        this.resourceTagMapper = resourceTagMapper;
        this.resourceTagRelMapper = resourceTagRelMapper;
        this.userActionMapper = userActionMapper;
        this.userRecommendationMapper = userRecommendationMapper;
    }

    @GetMapping
    public ApiResponse<List<ResourceInfo>> list() {
        List<ResourceInfo> list = resourceInfoService.list(
                new LambdaQueryWrapper<ResourceInfo>().eq(ResourceInfo::getVisibility, "PUBLIC")
                        .eq(ResourceInfo::getAuditStatus, "PASSED")
                        .orderByDesc(ResourceInfo::getCreatedAt)
        );
        return ApiResponse.success(list);
    }

    @GetMapping("/{id}")
    public ApiResponse<ResourceInfo> getById(@PathVariable Long id,
                                              @RequestParam(required = false) Long viewerId) {
        ResourceInfo resource = resourceInfoService.getById(id);
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }
        boolean isOwner = viewerId != null && viewerId.equals(resource.getUploaderId());
        if (!isOwner && "PRIVATE".equals(resource.getVisibility())) {
            return ApiResponse.error("资源仅自己可见");
        }
        if (!isOwner && !"PASSED".equals(resource.getAuditStatus())) {
            return ApiResponse.error("资源审核未通过，暂不可查看");
        }
        return ApiResponse.success(resource);
    }

    @GetMapping("/uploader/{uploaderId}")
    public ApiResponse<List<ResourceInfo>> listByUploader(@PathVariable Long uploaderId) {
        List<ResourceInfo> list = resourceInfoService.list(
                new LambdaQueryWrapper<ResourceInfo>().eq(ResourceInfo::getUploaderId, uploaderId)
                        .orderByDesc(ResourceInfo::getCreatedAt)
        );
        for (ResourceInfo res : list) {
            populateResourceTags(res);
        }
        return ApiResponse.success(list);
    }

    private void populateResourceTags(ResourceInfo res) {
        if (res == null || res.getId() == null) return;
        List<ResourceTagRel> rels = resourceTagRelMapper.selectList(
                new LambdaQueryWrapper<ResourceTagRel>().eq(ResourceTagRel::getResourceId, res.getId())
        );
        if (rels.isEmpty()) return;
        List<Long> tagIds = rels.stream().map(ResourceTagRel::getTagId).toList();
        List<ResourceTag> tags = resourceTagMapper.selectBatchIds(tagIds);
        String tagStr = String.join(",", tags.stream().map(ResourceTag::getTagName).toList());
        res.setTags(tagStr);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<ResourceInfo> upload(
            @RequestParam Long uploaderId,
            @RequestParam String title,
            @RequestParam String summary,
            @RequestParam String subjectName,
            @RequestParam String examType,
            @RequestParam String competitionType,
            @RequestParam(required = false) String visibility,
            @RequestParam String tags,
            @RequestParam("resourceFile") MultipartFile resourceFile,
            @RequestParam(value = "coverFile", required = false) MultipartFile coverFile
    ) {
        StringBuilder missingFields = new StringBuilder();
        if (!StringUtils.hasText(title)) missingFields.append("标题 ");
        if (!StringUtils.hasText(summary)) missingFields.append("简介 ");
        if (!StringUtils.hasText(subjectName)) missingFields.append("学科 ");
        if (!StringUtils.hasText(examType)) missingFields.append("考试类型 ");
        if (!StringUtils.hasText(competitionType)) missingFields.append("竞赛/证书 ");
        if (!StringUtils.hasText(tags)) missingFields.append("标签 ");
        if (resourceFile == null || resourceFile.isEmpty()) missingFields.append("资源文件 ");

        if (missingFields.length() > 0) {
            return ApiResponse.error("未收到必填项：" + missingFields.toString().trim());
        }
        if (StringUtils.hasText(visibility) && !"PUBLIC".equals(visibility) && !"PRIVATE".equals(visibility)) {
            return ApiResponse.error("可见性仅支持 PUBLIC 或 PRIVATE");
        }

        String fileUrl = localFileStorageService.store(resourceFile, "resource-files", RESOURCE_FILE_EXT);
        if (!StringUtils.hasText(fileUrl)) {
            return ApiResponse.error("资源文件格式不支持，仅支持PDF/TXT/Word/PPT/视频");
        }

        String coverUrl = null;
        if (coverFile != null && !coverFile.isEmpty()) {
            coverUrl = localFileStorageService.store(coverFile, "resource-covers", COVER_EXT);
            if (!StringUtils.hasText(coverUrl)) {
                return ApiResponse.error("封面格式不支持，仅支持jpg/png/webp");
            }
        }

        ResourceInfo resource = new ResourceInfo();
        resource.setUploaderId(uploaderId);
        resource.setTitle(title);
        resource.setSummary(summary);
        resource.setSubjectName(subjectName);
        resource.setExamType(examType);
        resource.setCompetitionType(competitionType);
        resource.setFileUrl(fileUrl);
        resource.setCoverUrl(coverUrl);
        resource.setVisibility(StringUtils.hasText(visibility) ? visibility : "PUBLIC");
        resource.setAuditStatus("PENDING");
        resource.setDownloadCount(0);
        resource.setFavoriteCount(0);
        resource.setLatestVersionNo(1);
        resourceInfoService.save(resource);

        // 上传后自动生成预览页地址，前端可直接跳转该地址。
        resource.setPreviewUrl("/api/resources/" + resource.getId() + "/preview");
        resourceInfoService.updateById(resource);

        // 首次上传写入版本表，后续更新时可追溯。
        ResourceFileVersion v1 = new ResourceFileVersion();
        v1.setResourceId(resource.getId());
        v1.setVersionNo(1);
        v1.setFileUrl(fileUrl);
        v1.setFileName(resourceFile.getOriginalFilename());
        v1.setChangeLog("初始版本");
        resourceFileVersionMapper.insert(v1);

        bindTags(resource.getId(), tags);
        return ApiResponse.success("上传成功", resource);
    }

    @GetMapping("/{id}/versions")
    public ApiResponse<List<ResourceFileVersion>> listVersions(@PathVariable Long id) {
        return ApiResponse.success(resourceFileVersionService.listByResourceId(id));
    }

    @PostMapping(value = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> uploadNewVersion(@PathVariable Long id,
                                                 @RequestParam Long uploaderId,
                                                 @RequestParam("resourceFile") MultipartFile resourceFile,
                                                 @RequestParam(required = false) String changeLog) {
        ResourceInfo resource = resourceInfoService.getById(id);
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }
        if (uploaderId == null) {
            return ApiResponse.error("未收到上传者 ID");
        }
        if (!resource.getUploaderId().equals(uploaderId)) {
            return ApiResponse.error("仅上传者本人可更新版本 (当前用户:" + uploaderId + ", 拥有者:" + resource.getUploaderId() + ")");
        }
        if (resourceFile == null || resourceFile.isEmpty()) {
            return ApiResponse.error("后端未收到文件 (MultipartFile is empty)");
        }

        String fileUrl = localFileStorageService.store(resourceFile, "resource-files", RESOURCE_FILE_EXT);
        if (!StringUtils.hasText(fileUrl)) {
            return ApiResponse.error("资源文件格式不支持，仅支持PDF/TXT/Word/PPT/视频");
        }

        Integer nextVersionNo = resourceFileVersionService.nextVersionNo(id);
        ResourceFileVersion version = new ResourceFileVersion();
        version.setResourceId(id);
        version.setVersionNo(nextVersionNo);
        version.setFileUrl(fileUrl);
        version.setFileName(resourceFile.getOriginalFilename());
        version.setChangeLog(StringUtils.hasText(changeLog) ? changeLog : "版本更新");
        resourceFileVersionService.save(version);

        resource.setFileUrl(fileUrl);
        resource.setLatestVersionNo(nextVersionNo);
        resource.setAuditStatus("PENDING");
        resourceInfoService.updateById(resource);
        return ApiResponse.success("版本上传成功", true);
    }

    @PutMapping("/{id}/visibility")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> updateVisibility(@PathVariable Long id,
                                                 @RequestParam Long uploaderId,
                                                 @RequestParam String visibility) {
        ResourceInfo resource = resourceInfoService.getById(id);
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }
        if (!resource.getUploaderId().equals(uploaderId)) {
            return ApiResponse.error("仅上传者本人可修改可见性");
        }
        if (!"PUBLIC".equals(visibility) && !"PRIVATE".equals(visibility)) {
            return ApiResponse.error("可见性仅支持 PUBLIC 或 PRIVATE");
        }

        resource.setVisibility(visibility);
        return ApiResponse.success("资源可见性更新成功", resourceInfoService.updateById(resource));
    }

    @PostMapping(value = "/{id}/update", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> update(@PathVariable Long id,
                                       @RequestParam Long uploaderId,
                                       @RequestParam String title,
                                       @RequestParam String summary,
                                       @RequestParam String subjectName,
                                       @RequestParam String examType,
                                       @RequestParam String competitionType,
                                       @RequestParam String tags,
                                       @RequestParam(value = "resourceFile", required = false) MultipartFile resourceFile,
                                       @RequestParam(value = "coverFile", required = false) MultipartFile coverFile) {
        ResourceInfo resource = resourceInfoService.getById(id);
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }
        if (!resource.getUploaderId().equals(uploaderId)) {
            return ApiResponse.error("仅上传者本人可修改资源信息");
        }
        if (!StringUtils.hasText(title) || !StringUtils.hasText(summary)) {
            return ApiResponse.error("标题和简介不能为空");
        }

        resource.setTitle(title);
        resource.setSummary(summary);
        resource.setSubjectName(subjectName);
        resource.setExamType(examType);
        resource.setCompetitionType(competitionType);

        if (resourceFile != null && !resourceFile.isEmpty()) {
            String fileUrl = localFileStorageService.store(resourceFile, "resource-files", RESOURCE_FILE_EXT);
            if (!StringUtils.hasText(fileUrl)) {
                return ApiResponse.error("资源文件格式不支持");
            }
            resource.setFileUrl(fileUrl);
            
            // 更新当前版本或新增版本？这里简单处理：更新基础信息中的文件指向，并记录一个新版本
            Integer nextVersionNo = resourceFileVersionService.nextVersionNo(id);
            ResourceFileVersion version = new ResourceFileVersion();
            version.setResourceId(id);
            version.setVersionNo(nextVersionNo);
            version.setFileUrl(fileUrl);
            version.setFileName(resourceFile.getOriginalFilename());
            version.setChangeLog("通过编辑功能更新文件");
            resourceFileVersionMapper.insert(version);
            resource.setLatestVersionNo(nextVersionNo);
        }

        if (coverFile != null && !coverFile.isEmpty()) {
            String coverUrl = localFileStorageService.store(coverFile, "resource-covers", COVER_EXT);
            if (!StringUtils.hasText(coverUrl)) {
                return ApiResponse.error("封面格式不支持");
            }
            resource.setCoverUrl(coverUrl);
        }

        // 更新标签关系
        resourceTagRelMapper.delete(new LambdaQueryWrapper<ResourceTagRel>().eq(ResourceTagRel::getResourceId, id));
        bindTags(id, tags);

        return ApiResponse.success("资源更新成功", resourceInfoService.updateById(resource));
    }

    @GetMapping(value = "/{id}/preview", produces = "text/html;charset=UTF-8")
    public String preview(@PathVariable Long id, @RequestParam(required = false) Long viewerId) {
        ResourceInfo resource = resourceInfoService.getById(id);
        if (resource == null) {
            return "<h3>资源不存在</h3>";
        }
        if ("PRIVATE".equals(resource.getVisibility()) && (viewerId == null || !viewerId.equals(resource.getUploaderId()))) {
            return "<h3>该资源仅上传者可预览</h3>";
        }
        String coverHtml = StringUtils.hasText(resource.getCoverUrl())
                ? "<img src='" + resource.getCoverUrl() + "' style='max-width:280px;border-radius:8px;'/>"
                : "<div style='color:#666;'>暂无封面</div>";
        String summary = escapeHtml(resource.getSummary());
        String title = escapeHtml(resource.getTitle());

        return "<!doctype html><html><head><meta charset='utf-8'><title>资源预览</title></head>"
                + "<body style='font-family:Microsoft YaHei;padding:20px;'>"
                + "<h2>" + title + "</h2>"
                + "<p><b>简介：</b>" + summary + "</p>"
                + "<p><b>学科：</b>" + escapeHtml(resource.getSubjectName()) + "</p>"
                + "<p><b>考试类型：</b>" + escapeHtml(resource.getExamType()) + "</p>"
                + "<p><b>竞赛类别：</b>" + escapeHtml(resource.getCompetitionType()) + "</p>"
                + coverHtml
                + "<p style='margin-top:16px;'><a href='" + resource.getFileUrl() + "' target='_blank'>打开资源文件</a></p>"
                + "</body></html>";
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> delete(@PathVariable Long id, @RequestParam Long uploaderId) {
        ResourceInfo resource = resourceInfoService.getById(id);
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }
        if (!resource.getUploaderId().equals(uploaderId)) {
            return ApiResponse.error("仅上传者本人可删除资源");
        }
        resourceFileVersionMapper.delete(new LambdaQueryWrapper<ResourceFileVersion>().eq(ResourceFileVersion::getResourceId, id));
        resourceTagRelMapper.delete(new LambdaQueryWrapper<ResourceTagRel>().eq(ResourceTagRel::getResourceId, id));
        favoriteItemMapper.delete(new LambdaQueryWrapper<FavoriteItem>().eq(FavoriteItem::getResourceId, id));
        userActionMapper.delete(new LambdaQueryWrapper<UserAction>().eq(UserAction::getResourceId, id));
        userRecommendationMapper.delete(new LambdaQueryWrapper<UserRecommendation>().eq(UserRecommendation::getResourceId, id));
        return ApiResponse.success("删除成功", resourceInfoService.removeById(id));
    }

    @PostMapping("/{id}/download")
    public ApiResponse<Boolean> download(@PathVariable Long id, @RequestParam Long userId) {
        ResourceInfo resource = resourceInfoService.getById(id);
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }
        if ("PRIVATE".equals(resource.getVisibility()) && !userId.equals(resource.getUploaderId())) {
            return ApiResponse.error("资源仅自己可见，不能下载");
        }
        if (!userId.equals(resource.getUploaderId()) && !"PASSED".equals(resource.getAuditStatus())) {
            return ApiResponse.error("资源审核未通过，不能下载");
        }
        if (!userActionService.canDownloadNow(userId, id, minDownloadIntervalSeconds)) {
            return ApiResponse.error("下载过于频繁，请稍后再试");
        }

        // 自动记录下载行为，供后续推荐算法使用。
        boolean actionOk = userActionService.recordDownload(userId, id);
        if (!actionOk) {
            return ApiResponse.error("下载行为记录失败");
        }

        resource.setDownloadCount((resource.getDownloadCount() == null ? 0 : resource.getDownloadCount()) + 1);
        resourceInfoService.updateById(resource);
        return ApiResponse.success("下载记录成功", true);
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<Boolean> favorite(@PathVariable Long id, @RequestParam Long userId) {
        ResourceInfo resource = resourceInfoService.getById(id);
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }
        if ("PRIVATE".equals(resource.getVisibility()) && !userId.equals(resource.getUploaderId())) {
            return ApiResponse.error("资源仅自己可见，不能收藏");
        }
        if (!userId.equals(resource.getUploaderId()) && !"PASSED".equals(resource.getAuditStatus())) {
            return ApiResponse.error("资源审核未通过，不能收藏");
        }

        if (userActionService.hasFavorited(userId, id)) {
            return ApiResponse.success("已收藏，无需重复操作", true);
        }

        // 自动记录收藏行为，供后续推荐算法使用。
        boolean actionOk = userActionService.recordFavorite(userId, id);
        if (!actionOk) {
            return ApiResponse.error("收藏行为记录失败");
        }

        resource.setFavoriteCount((resource.getFavoriteCount() == null ? 0 : resource.getFavoriteCount()) + 1);
        resourceInfoService.updateById(resource);
        return ApiResponse.success("收藏记录成功", true);
    }

    private void bindTags(Long resourceId, String tags) {
        if (resourceId == null || !StringUtils.hasText(tags)) {
            return;
        }

        // 标签去重后入库，并建立资源-标签关系。
        Set<String> tagSet = new LinkedHashSet<>(Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList());

        for (String tagName : tagSet) {
            ResourceTag tag = resourceTagMapper.selectOne(
                    new LambdaQueryWrapper<ResourceTag>().eq(ResourceTag::getTagName, tagName).last("limit 1")
            );
            if (tag == null) {
                tag = new ResourceTag();
                tag.setTagName(tagName);
                resourceTagMapper.insert(tag);
            }

            ResourceTagRel rel = resourceTagRelMapper.selectOne(
                    new LambdaQueryWrapper<ResourceTagRel>()
                            .eq(ResourceTagRel::getResourceId, resourceId)
                            .eq(ResourceTagRel::getTagId, tag.getId())
                            .last("limit 1")
            );
            if (rel == null) {
                rel = new ResourceTagRel();
                rel.setResourceId(resourceId);
                rel.setTagId(tag.getId());
                resourceTagRelMapper.insert(rel);
            }
        }
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
