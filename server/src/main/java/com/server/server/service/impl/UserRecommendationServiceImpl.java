package com.server.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.server.entity.ResourceInfo;
import com.server.server.entity.ResourceTag;
import com.server.server.entity.ResourceTagRel;
import com.server.server.entity.UserAction;
import com.server.server.entity.UserProfile;
import com.server.server.entity.UserRecommendation;
import com.server.server.mapper.ResourceInfoMapper;
import com.server.server.mapper.ResourceTagMapper;
import com.server.server.mapper.ResourceTagRelMapper;
import com.server.server.mapper.UserActionMapper;
import com.server.server.mapper.UserProfileMapper;
import com.server.server.mapper.UserRecommendationMapper;
import com.server.server.service.UserRecommendationService;
import com.server.server.vo.RecommendationItemVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class UserRecommendationServiceImpl extends ServiceImpl<UserRecommendationMapper, UserRecommendation> implements UserRecommendationService {

    private static final String REC_LIST_KEY_PREFIX = "rec:list:";
    private static final String SIM_MAP_KEY_PREFIX = "rec:sim:";

	private final UserActionMapper userActionMapper;
	private final ResourceInfoMapper resourceInfoMapper;
	private final UserProfileMapper userProfileMapper;
	private final ResourceTagMapper resourceTagMapper;
	private final ResourceTagRelMapper resourceTagRelMapper;
	private final StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper;

	@Value("${app.recommend.redis-ttl-minutes:30}")
	private long redisTtlMinutes;

	public UserRecommendationServiceImpl(UserActionMapper userActionMapper,
										 ResourceInfoMapper resourceInfoMapper,
										 UserProfileMapper userProfileMapper,
										 ResourceTagMapper resourceTagMapper,
									 ResourceTagRelMapper resourceTagRelMapper,
									 StringRedisTemplate stringRedisTemplate,
									 ObjectMapper objectMapper) {
		this.userActionMapper = userActionMapper;
		this.resourceInfoMapper = resourceInfoMapper;
		this.userProfileMapper = userProfileMapper;
		this.resourceTagMapper = resourceTagMapper;
		this.resourceTagRelMapper = resourceTagRelMapper;
		this.stringRedisTemplate = stringRedisTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public List<RecommendationItemVO> recommendForUser(Long userId, Integer limit, boolean refresh) {
		int realLimit = normalizeLimit(limit);
		if (refresh) {
			return refreshRecommendation(userId, realLimit);
		}

		List<RecommendationItemVO> redisList = readRecListFromRedis(userId, realLimit);
		if (!redisList.isEmpty()) {
			return redisList;
		}

		List<UserRecommendation> cacheList = this.list(new LambdaQueryWrapper<UserRecommendation>()
				.eq(UserRecommendation::getUserId, userId)
				.orderByDesc(UserRecommendation::getRecScore)
				.last("limit " + realLimit));

		if (cacheList.isEmpty()) {
			return refreshRecommendation(userId, realLimit);
		}
		List<RecommendationItemVO> voList = toVOList(cacheList);
		writeRecListToRedis(userId, voList);
		return voList;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public List<RecommendationItemVO> refreshRecommendation(Long userId, Integer limit) {
		int realLimit = normalizeLimit(limit);
		List<UserRecommendation> generated = generateByUserCfOrColdStart(userId, realLimit);

		this.remove(new LambdaQueryWrapper<UserRecommendation>().eq(UserRecommendation::getUserId, userId));
		if (!generated.isEmpty()) {
			this.saveBatch(generated);
		}
		List<RecommendationItemVO> voList = toVOList(generated);
		writeRecListToRedis(userId, voList);
		return voList;
	}

	private List<UserRecommendation> generateByUserCfOrColdStart(Long userId, int limit) {
		List<ResourceInfo> publicResources = resourceInfoMapper.selectList(new LambdaQueryWrapper<ResourceInfo>()
				.eq(ResourceInfo::getVisibility, "PUBLIC")
				.eq(ResourceInfo::getAuditStatus, "PASSED"));
		if (publicResources.isEmpty()) {
			return new ArrayList<>();
		}
		Map<Long, ResourceInfo> resourceMap = publicResources.stream()
				.collect(Collectors.toMap(ResourceInfo::getId, r -> r, (a, b) -> a));

		List<UserAction> actions = userActionMapper.selectList(new LambdaQueryWrapper<UserAction>());
		Map<Long, Map<Long, Double>> userVectorMap = buildUserVectorMap(actions, resourceMap.keySet());
		Map<Long, Double> targetVector = userVectorMap.getOrDefault(userId, new HashMap<>());

		if (targetVector.isEmpty()) {
			writeSimMapToRedis(userId, new HashMap<>());
			return coldStartRecommend(userId, limit, resourceMap, "零行为冷启动（热门+兴趣标签）");
		}

		Map<Long, Double> simMap = new HashMap<>();
		for (Map.Entry<Long, Map<Long, Double>> e : userVectorMap.entrySet()) {
			Long otherUserId = e.getKey();
			if (otherUserId.equals(userId)) {
				continue;
			}
			double sim = cosine(targetVector, e.getValue());
			if (sim > 0) {
				simMap.put(otherUserId, sim);
			}
		}
		writeSimMapToRedis(userId, simMap);

		Map<Long, Double> candidateScore = new HashMap<>();
		Set<Long> targetSeen = targetVector.keySet();
		for (Map.Entry<Long, Double> simEntry : simMap.entrySet()) {
			Map<Long, Double> otherVector = userVectorMap.get(simEntry.getKey());
			for (Map.Entry<Long, Double> rv : otherVector.entrySet()) {
				Long resourceId = rv.getKey();
				if (targetSeen.contains(resourceId)) {
					continue;
				}
				candidateScore.merge(resourceId, simEntry.getValue() * rv.getValue(), Double::sum);
			}
		}

		List<UserRecommendation> list = candidateScore.entrySet().stream()
				.sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
				.limit(limit)
				.map(e -> buildRec(userId, e.getKey(), e.getValue(), "基于相似用户行为推荐"))
				.toList();

		if (list.isEmpty()) {
			return coldStartRecommend(userId, limit, resourceMap, "行为交集过窄，切换至热门补偿推荐");
		}
		return list;
	}

	private List<UserRecommendation> coldStartRecommend(Long userId, int limit, Map<Long, ResourceInfo> resourceMap, String fallbackReason) {
		String finalReason = StringUtils.hasText(fallbackReason) ? fallbackReason : "冷启动推荐（热门+兴趣标签）";
		Map<Long, Double> scoreMap = new HashMap<>();

		// 冷启动默认使用热门资源分数。
		for (ResourceInfo r : resourceMap.values()) {
			int d = r.getDownloadCount() == null ? 0 : r.getDownloadCount();
			int f = r.getFavoriteCount() == null ? 0 : r.getFavoriteCount();
			scoreMap.put(r.getId(), d + f * 1.2);
		}

		// 冷启动叠加兴趣标签命中分。
		UserProfile profile = userProfileMapper.selectOne(new LambdaQueryWrapper<UserProfile>()
				.eq(UserProfile::getUserId, userId).last("limit 1"));
		if (profile != null && StringUtils.hasText(profile.getInterestTags())) {
			Set<String> tags = Arrays.stream(profile.getInterestTags().split(","))
					.map(String::trim).filter(StringUtils::hasText).collect(Collectors.toSet());
			if (!tags.isEmpty()) {
				List<ResourceTag> tagList = resourceTagMapper.selectList(new LambdaQueryWrapper<ResourceTag>());
				Set<Long> tagIds = tagList.stream()
						.filter(t -> tags.contains(t.getTagName()))
						.map(ResourceTag::getId)
						.collect(Collectors.toSet());
				if (!tagIds.isEmpty()) {
					List<ResourceTagRel> relList = resourceTagRelMapper.selectList(new LambdaQueryWrapper<ResourceTagRel>());
					for (ResourceTagRel rel : relList) {
						if (tagIds.contains(rel.getTagId()) && resourceMap.containsKey(rel.getResourceId())) {
							scoreMap.merge(rel.getResourceId(), 5.0, Double::sum);
						}
					}
				}
			}
		}

		return scoreMap.entrySet().stream()
				.sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
				.limit(limit)
				.map(e -> buildRec(userId, e.getKey(), e.getValue(), finalReason))
				.toList();
	}

	private Map<Long, Map<Long, Double>> buildUserVectorMap(List<UserAction> actions, Set<Long> allowedResourceIds) {
		Map<Long, Map<Long, Double>> map = new HashMap<>();
		for (UserAction a : actions) {
			if (!allowedResourceIds.contains(a.getResourceId())) {
				continue;
			}
			double weight = actionWeight(a);
			map.computeIfAbsent(a.getUserId(), k -> new HashMap<>())
					.merge(a.getResourceId(), weight, Double::sum);
		}
		return map;
	}

	private double actionWeight(UserAction action) {
		if ("FAVORITE".equals(action.getActionType())) {
			return 1.2;
		}
		if ("RATE".equals(action.getActionType()) && action.getScore() != null) {
			return Math.max(1.0, action.getScore() / 2.5);
		}
		return 1.0;
	}

	private double cosine(Map<Long, Double> a, Map<Long, Double> b) {
		if (a.isEmpty() || b.isEmpty()) {
			return 0;
		}
		double dot = 0;
		for (Map.Entry<Long, Double> e : a.entrySet()) {
			Double bv = b.get(e.getKey());
			if (bv != null) {
				dot += e.getValue() * bv;
			}
		}
		if (dot == 0) {
			return 0;
		}
		double normA = Math.sqrt(a.values().stream().mapToDouble(v -> v * v).sum());
		double normB = Math.sqrt(b.values().stream().mapToDouble(v -> v * v).sum());
		if (normA == 0 || normB == 0) {
			return 0;
		}
		return dot / (normA * normB);
	}

	private UserRecommendation buildRec(Long userId, Long resourceId, Double score, String reason) {
		UserRecommendation rec = new UserRecommendation();
		rec.setUserId(userId);
		rec.setResourceId(resourceId);
		rec.setRecScore(BigDecimal.valueOf(score).setScale(4, RoundingMode.HALF_UP));
		rec.setRecReason(reason);
		return rec;
	}

	private List<RecommendationItemVO> toVOList(List<UserRecommendation> recList) {
		if (recList == null || recList.isEmpty()) {
			return new ArrayList<>();
		}
		Set<Long> ids = recList.stream().map(UserRecommendation::getResourceId).collect(Collectors.toSet());
		Map<Long, ResourceInfo> resourceMap = resourceInfoMapper.selectList(
						new LambdaQueryWrapper<ResourceInfo>()
								.in(ResourceInfo::getId, ids)
								.eq(ResourceInfo::getVisibility, "PUBLIC")
								.eq(ResourceInfo::getAuditStatus, "PASSED"))
				.stream().collect(Collectors.toMap(ResourceInfo::getId, r -> r, (a, b) -> a));

		List<RecommendationItemVO> voList = new ArrayList<>();
		for (UserRecommendation rec : recList) {
			ResourceInfo r = resourceMap.get(rec.getResourceId());
			if (r == null) {
				continue;
			}
			RecommendationItemVO vo = new RecommendationItemVO();
			vo.setResourceId(r.getId());
			vo.setTitle(r.getTitle());
			vo.setCoverUrl(r.getCoverUrl());
			vo.setRecScore(rec.getRecScore());
			vo.setRecReason(rec.getRecReason());
			voList.add(vo);
		}

		voList.sort(Comparator.comparing(RecommendationItemVO::getRecScore, Comparator.nullsLast(BigDecimal::compareTo)).reversed());
		return voList;
	}

	private int normalizeLimit(Integer limit) {
		if (limit == null) {
			return 10;
		}
		return Math.max(1, Math.min(limit, 50));
	}

	private void writeRecListToRedis(Long userId, List<RecommendationItemVO> voList) {
		try {
			String key = REC_LIST_KEY_PREFIX + userId;
			String json = objectMapper.writeValueAsString(voList);
			stringRedisTemplate.opsForValue().set(key, json, redisTtlMinutes, TimeUnit.MINUTES);
		} catch (Exception ignored) {
		}
	}

	private List<RecommendationItemVO> readRecListFromRedis(Long userId, int limit) {
		try {
			String key = REC_LIST_KEY_PREFIX + userId;
			String json = stringRedisTemplate.opsForValue().get(key);
			if (!StringUtils.hasText(json)) {
				return new ArrayList<>();
			}
			List<RecommendationItemVO> list = objectMapper.readValue(json, new TypeReference<List<RecommendationItemVO>>() {
			});
			if (list == null || list.isEmpty()) {
				return new ArrayList<>();
			}
			return filterVisibleRecommendations(list).stream().limit(limit).toList();
		} catch (Exception ignored) {
			return new ArrayList<>();
		}
	}

	private void writeSimMapToRedis(Long userId, Map<Long, Double> simMap) {
		try {
			String key = SIM_MAP_KEY_PREFIX + userId;
			String json = objectMapper.writeValueAsString(simMap);
			stringRedisTemplate.opsForValue().set(key, json, redisTtlMinutes, TimeUnit.MINUTES);
		} catch (Exception ignored) {
		}
	}

	private List<RecommendationItemVO> filterVisibleRecommendations(List<RecommendationItemVO> list) {
		if (list == null || list.isEmpty()) {
			return new ArrayList<>();
		}
		Set<Long> ids = list.stream().map(RecommendationItemVO::getResourceId).collect(Collectors.toSet());
		if (ids.isEmpty()) {
			return new ArrayList<>();
		}

		Set<Long> publicIds = resourceInfoMapper.selectList(
				new LambdaQueryWrapper<ResourceInfo>()
						.in(ResourceInfo::getId, ids)
						.eq(ResourceInfo::getVisibility, "PUBLIC")
						.eq(ResourceInfo::getAuditStatus, "PASSED")
		).stream().map(ResourceInfo::getId).collect(Collectors.toSet());

		return list.stream().filter(i -> publicIds.contains(i.getResourceId())).toList();
	}
}
