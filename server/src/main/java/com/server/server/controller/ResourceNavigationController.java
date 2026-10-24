package com.server.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.server.server.common.ApiResponse;
import com.server.server.entity.ResourceInfo;
import com.server.server.mapper.ResourceTagMapper;
import com.server.server.mapper.ResourceTagRelMapper;
import com.server.server.service.ResourceInfoService;
import com.server.server.entity.ResourceTag;
import com.server.server.entity.ResourceTagRel;
import com.server.server.mapper.ResourceTagMapper;
import com.server.server.mapper.ResourceTagRelMapper;
import com.server.server.vo.HotTagVO;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/navigation")
// 资源导航接口：提供分类树、热门标签、榜单和搜索筛选能力。
public class ResourceNavigationController {
 
    private final ResourceInfoService resourceInfoService;
    private final ResourceTagMapper resourceTagMapper;
    private final ResourceTagRelMapper resourceTagRelMapper;
 
    public ResourceNavigationController(ResourceInfoService resourceInfoService,
                                        ResourceTagMapper resourceTagMapper,
                                        ResourceTagRelMapper resourceTagRelMapper) {
        this.resourceInfoService = resourceInfoService;
        this.resourceTagMapper = resourceTagMapper;
        this.resourceTagRelMapper = resourceTagRelMapper;
    }


    @GetMapping("/tags/hot")
    public ApiResponse<List<HotTagVO>> hotTags(@RequestParam(defaultValue = "10") Integer limit) {
        int realLimit = Math.max(1, Math.min(limit, 50));

        List<ResourceTagRel> relList = resourceTagRelMapper.selectList(null);
        Map<Long, Long> countMap = relList.stream()
                .collect(Collectors.groupingBy(ResourceTagRel::getTagId, Collectors.counting()));

        List<ResourceTag> tags = resourceTagMapper.selectList(new LambdaQueryWrapper<ResourceTag>());
        Map<Long, String> tagNameMap = tags.stream().collect(Collectors.toMap(ResourceTag::getId, ResourceTag::getTagName));

        List<HotTagVO> hotTagList = countMap.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(realLimit)
                .map(e -> new HotTagVO(e.getKey(), tagNameMap.getOrDefault(e.getKey(), ""), e.getValue()))
                .toList();

        return ApiResponse.success(hotTagList);
    }

    @GetMapping("/rank/latest")
    public ApiResponse<List<ResourceInfo>> latestRank(@RequestParam(defaultValue = "10") Integer limit) {
        int realLimit = Math.max(1, Math.min(limit, 50));
        List<ResourceInfo> list = resourceInfoService.list(
                new LambdaQueryWrapper<ResourceInfo>()
                        .eq(ResourceInfo::getVisibility, "PUBLIC")
                        .eq(ResourceInfo::getAuditStatus, "PASSED")
                        .orderByDesc(ResourceInfo::getCreatedAt)
                        .last("limit " + realLimit)
        );
        return ApiResponse.success(list);
    }

    @GetMapping("/rank/hot")
    public ApiResponse<List<ResourceInfo>> hotRank(@RequestParam(defaultValue = "10") Integer limit) {
        int realLimit = Math.max(1, Math.min(limit, 50));
        List<ResourceInfo> list = resourceInfoService.list(
                new LambdaQueryWrapper<ResourceInfo>()
                        .eq(ResourceInfo::getVisibility, "PUBLIC")
                .eq(ResourceInfo::getAuditStatus, "PASSED")
                        .orderByDesc(ResourceInfo::getDownloadCount)
                        .orderByDesc(ResourceInfo::getFavoriteCount)
                        .last("limit " + realLimit)
        );
        return ApiResponse.success(list);
    }

    @GetMapping("/search")
    public ApiResponse<List<ResourceInfo>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String subjectName,
            @RequestParam(required = false) String examType,
            @RequestParam(required = false) String competitionType,
            @RequestParam(required = false) String tagName,
            @RequestParam(defaultValue = "latest") String sortBy,
            @RequestParam(defaultValue = "20") Integer limit
    ) {
        int realLimit = Math.max(1, Math.min(limit, 100));

        LambdaQueryWrapper<ResourceInfo> qw = new LambdaQueryWrapper<>();
        qw.eq(ResourceInfo::getVisibility, "PUBLIC");
        qw.eq(ResourceInfo::getAuditStatus, "PASSED");

        if (StringUtils.hasText(tagName)) {
            ResourceTag tag = resourceTagMapper.selectOne(new LambdaQueryWrapper<ResourceTag>()
                    .eq(ResourceTag::getTagName, tagName.trim()).last("limit 1"));
            if (tag == null) {
                return ApiResponse.success(new ArrayList<>());
            }
            List<ResourceTagRel> rels = resourceTagRelMapper.selectList(
                    new LambdaQueryWrapper<ResourceTagRel>().eq(ResourceTagRel::getTagId, tag.getId())
            );
            Set<Long> idSet = new HashSet<>(rels.stream().map(ResourceTagRel::getResourceId).toList());
            if (idSet.isEmpty()) {
                return ApiResponse.success(new ArrayList<>());
            }
            qw.in(ResourceInfo::getId, idSet);
        }

        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(ResourceInfo::getTitle, keyword).or().like(ResourceInfo::getSummary, keyword));
        }
        if (StringUtils.hasText(subjectName)) {
            qw.eq(ResourceInfo::getSubjectName, subjectName.trim());
        }
        if (StringUtils.hasText(examType)) {
            qw.eq(ResourceInfo::getExamType, examType.trim());
        }
        if (StringUtils.hasText(competitionType)) {
            qw.eq(ResourceInfo::getCompetitionType, competitionType.trim());
        }

        if ("hot".equalsIgnoreCase(sortBy)) {
            qw.orderByDesc(ResourceInfo::getDownloadCount).orderByDesc(ResourceInfo::getFavoriteCount);
        } else if ("download".equalsIgnoreCase(sortBy)) {
            qw.orderByDesc(ResourceInfo::getDownloadCount);
        } else if ("favorite".equalsIgnoreCase(sortBy)) {
            qw.orderByDesc(ResourceInfo::getFavoriteCount);
        } else {
            qw.orderByDesc(ResourceInfo::getCreatedAt);
        }

        qw.last("limit " + realLimit);
        List<ResourceInfo> list = resourceInfoService.list(qw);

        return ApiResponse.success(list);
    }
}
