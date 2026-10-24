package com.server.server.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.server.server.common.ApiResponse;
import com.server.server.entity.DailyStatSnapshot;
import com.server.server.entity.ResourceAuditLog;
import com.server.server.entity.ResourceInfo;
import com.server.server.entity.SystemMessage;
import com.server.server.entity.SysUser;
import com.server.server.entity.UserAction;
import com.server.server.entity.UserRecommendation;
import com.server.server.mapper.DailyStatSnapshotMapper;
import com.server.server.mapper.FavoriteItemMapper;
import com.server.server.mapper.ResourceFileVersionMapper;
import com.server.server.mapper.ResourceAuditLogMapper;
import com.server.server.mapper.ResourceInfoMapper;
import com.server.server.mapper.ResourceTagRelMapper;
import com.server.server.mapper.SysUserMapper;
import com.server.server.mapper.UserActionMapper;
import com.server.server.mapper.UserRecommendationMapper;
import com.server.server.service.SystemMessageService;
import com.server.server.vo.AdminDashboardVO;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
// 后台管理接口：提供用户管理、资源审核和数据看板能力。
public class AdminController {

    private final SysUserMapper sysUserMapper;
    private final UserActionMapper userActionMapper;
    private final ResourceInfoMapper resourceInfoMapper;
    private final ResourceFileVersionMapper resourceFileVersionMapper;
    private final ResourceTagRelMapper resourceTagRelMapper;
    private final FavoriteItemMapper favoriteItemMapper;
    private final ResourceAuditLogMapper resourceAuditLogMapper;
    private final UserRecommendationMapper userRecommendationMapper;
    private final DailyStatSnapshotMapper dailyStatSnapshotMapper;
    private final SystemMessageService systemMessageService;

    public AdminController(SysUserMapper sysUserMapper,
                           UserActionMapper userActionMapper,
                           ResourceInfoMapper resourceInfoMapper,
                           ResourceFileVersionMapper resourceFileVersionMapper,
                           ResourceTagRelMapper resourceTagRelMapper,
                           FavoriteItemMapper favoriteItemMapper,
                           ResourceAuditLogMapper resourceAuditLogMapper,
                           UserRecommendationMapper userRecommendationMapper,
                           DailyStatSnapshotMapper dailyStatSnapshotMapper,
                           SystemMessageService systemMessageService) {
        this.sysUserMapper = sysUserMapper;
        this.userActionMapper = userActionMapper;
        this.resourceInfoMapper = resourceInfoMapper;
        this.resourceFileVersionMapper = resourceFileVersionMapper;
        this.resourceTagRelMapper = resourceTagRelMapper;
        this.favoriteItemMapper = favoriteItemMapper;
        this.resourceAuditLogMapper = resourceAuditLogMapper;
        this.userRecommendationMapper = userRecommendationMapper;
        this.dailyStatSnapshotMapper = dailyStatSnapshotMapper;
        this.systemMessageService = systemMessageService;
    }

    @GetMapping("/users")
    public ApiResponse<List<SysUser>> users(@RequestParam(required = false) Integer status,
                                            @RequestParam(required = false) String keyword) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(SysUser::getStatus, status);
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getEmail, keyword)
                    .or().like(SysUser::getPhone, keyword));
        }
        qw.orderByDesc(SysUser::getCreatedAt);
        return ApiResponse.success(sysUserMapper.selectList(qw));
    }

    @PutMapping("/users/{userId}/status")
    public ApiResponse<Boolean> updateUserStatus(@PathVariable Long userId,
                                                 @RequestParam Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            return ApiResponse.error("status 仅支持 0 或 1");
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            return ApiResponse.error("用户不存在");
        }
        user.setStatus(status);
        return ApiResponse.success("更新成功", sysUserMapper.updateById(user) > 0);
    }

    @GetMapping("/users/{userId}/actions")
    public ApiResponse<List<UserAction>> userActions(@PathVariable Long userId,
                                                     @RequestParam(defaultValue = "50") Integer limit) {
        int realLimit = Math.max(1, Math.min(limit, 500));
        List<UserAction> actions = userActionMapper.selectList(new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getUserId, userId)
                .orderByDesc(UserAction::getCreatedAt)
                .last("limit " + realLimit));
        return ApiResponse.success(actions);
    }

    @GetMapping("/resources/pending")
    public ApiResponse<List<ResourceInfo>> pendingResources() {
        List<ResourceInfo> list = resourceInfoMapper.selectList(new LambdaQueryWrapper<ResourceInfo>()
                .eq(ResourceInfo::getAuditStatus, "PENDING")
                .orderByDesc(ResourceInfo::getCreatedAt));
        return ApiResponse.success(list);
    }

    @PutMapping("/resources/{resourceId}/audit")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<Boolean> auditResource(@PathVariable Long resourceId,
                                              @RequestParam Long adminId,
                                              @RequestParam String result,
                                              @RequestParam(required = false) String remark) {
        if (!"PASSED".equals(result) && !"REJECTED".equals(result) && !"DELETED".equals(result)) {
            return ApiResponse.error("审核结果仅支持 PASSED/REJECTED/DELETED");
        }

        ResourceInfo resource = resourceInfoMapper.selectById(resourceId);
        if (resource == null) {
            return ApiResponse.error("资源不存在");
        }

        if ("DELETED".equals(result)) {
            resourceFileVersionMapper.delete(new LambdaQueryWrapper<com.server.server.entity.ResourceFileVersion>()
                .eq(com.server.server.entity.ResourceFileVersion::getResourceId, resourceId));
            resourceTagRelMapper.delete(new LambdaQueryWrapper<com.server.server.entity.ResourceTagRel>()
                .eq(com.server.server.entity.ResourceTagRel::getResourceId, resourceId));
            favoriteItemMapper.delete(new LambdaQueryWrapper<com.server.server.entity.FavoriteItem>()
                .eq(com.server.server.entity.FavoriteItem::getResourceId, resourceId));
            userActionMapper.delete(new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getResourceId, resourceId));
            userRecommendationMapper.delete(new LambdaQueryWrapper<UserRecommendation>()
                .eq(UserRecommendation::getResourceId, resourceId));
            resourceInfoMapper.deleteById(resourceId);
        } else {
            resource.setAuditStatus(result);
            resourceInfoMapper.updateById(resource);
        }

        ResourceAuditLog log = new ResourceAuditLog();
        log.setResourceId(resourceId);
        log.setAdminId(adminId);
        log.setAuditResult(result);
        log.setAuditRemark(remark);
        resourceAuditLogMapper.insert(log);

        SystemMessage message = new SystemMessage();
        message.setUserId(resource.getUploaderId());
        message.setMsgType("AUDIT_RESULT");
        message.setTitle("资源审核结果通知");
        message.setContent("资源《" + resource.getTitle() + "》审核结果：" + result + (StringUtils.hasText(remark) ? "，备注：" + remark : ""));
        message.setIsRead(0);
        systemMessageService.createMessage(message);

        return ApiResponse.success("审核完成", true);
    }

    @GetMapping("/dashboard/overview")
    public ApiResponse<AdminDashboardVO> dashboardOverview(@RequestParam(required = false) String date) {
        LocalDate statDate;
        if (StringUtils.hasText(date)) {
            try {
                statDate = LocalDate.parse(date);
            } catch (Exception e) {
                return ApiResponse.error("日期格式错误，请使用 yyyy-MM-dd");
            }
        } else {
            statDate = LocalDate.now();
        }

        AdminDashboardVO vo;
        if (statDate.isBefore(LocalDate.now())) {
            DailyStatSnapshot existed = dailyStatSnapshotMapper.selectOne(new LambdaQueryWrapper<DailyStatSnapshot>()
                    .eq(DailyStatSnapshot::getStatDate, statDate)
                    .last("limit 1"));
            if (existed != null) {
                vo = new AdminDashboardVO();
                vo.setStatDate(existed.getStatDate());
                vo.setRegisterUserCount(existed.getRegisterUserCount());
                vo.setTotalResourceCount(existed.getTotalResourceCount());
                vo.setDailyActiveUserCount(existed.getDailyActiveUserCount());
                vo.setRecClickRate(existed.getRecClickRate());
            } else {
                vo = buildDashboard(statDate);
            }
        } else {
            vo = buildDashboard(statDate);
        }
        return ApiResponse.success(vo);
    }

    @PostMapping("/dashboard/snapshot")
    @Transactional(rollbackFor = Exception.class)
    public ApiResponse<AdminDashboardVO> snapshot(@RequestParam(required = false) String date) {
        LocalDate statDate;
        try {
            statDate = StringUtils.hasText(date) ? LocalDate.parse(date) : LocalDate.now();
        } catch (Exception e) {
            return ApiResponse.error("日期格式错误，请使用 yyyy-MM-dd");
        }
        AdminDashboardVO vo = buildDashboard(statDate);

        DailyStatSnapshot existed = dailyStatSnapshotMapper.selectOne(new LambdaQueryWrapper<DailyStatSnapshot>()
                .eq(DailyStatSnapshot::getStatDate, statDate)
                .last("limit 1"));

        if (existed == null) {
            existed = new DailyStatSnapshot();
            existed.setStatDate(statDate);
            existed.setRegisterUserCount(vo.getRegisterUserCount());
            existed.setTotalResourceCount(vo.getTotalResourceCount());
            existed.setDailyActiveUserCount(vo.getDailyActiveUserCount());
            existed.setRecClickRate(vo.getRecClickRate());
            dailyStatSnapshotMapper.insert(existed);
        } else {
            existed.setRegisterUserCount(vo.getRegisterUserCount());
            existed.setTotalResourceCount(vo.getTotalResourceCount());
            existed.setDailyActiveUserCount(vo.getDailyActiveUserCount());
            existed.setRecClickRate(vo.getRecClickRate());
            dailyStatSnapshotMapper.updateById(existed);
        }

        return ApiResponse.success("快照保存成功", vo);
    }

    private AdminDashboardVO buildDashboard(LocalDate statDate) {
        LocalDateTime start = statDate.atStartOfDay();
        LocalDateTime end = statDate.plusDays(1).atStartOfDay();

        long registerUserCount = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .ge(SysUser::getCreatedAt, start)
                .lt(SysUser::getCreatedAt, end));

        long totalResourceCount = resourceInfoMapper.selectCount(new LambdaQueryWrapper<ResourceInfo>());

        List<UserAction> todayActions = userActionMapper.selectList(new LambdaQueryWrapper<UserAction>()
            .ge(UserAction::getCreatedAt, start)
            .lt(UserAction::getCreatedAt, end));
        int dailyActiveUserCount = (int) todayActions.stream().map(UserAction::getUserId).distinct().count();

        long recExpose = userRecommendationMapper.selectCount(new LambdaQueryWrapper<UserRecommendation>());
        long recClicks = userActionMapper.selectCount(new LambdaQueryWrapper<UserAction>()
                .eq(UserAction::getActionType, "DOWNLOAD")
                .ge(UserAction::getCreatedAt, start)
                .lt(UserAction::getCreatedAt, end));

        BigDecimal recClickRate = BigDecimal.ZERO;
        if (recExpose > 0) {
            recClickRate = BigDecimal.valueOf((double) recClicks / recExpose)
                    .setScale(4, RoundingMode.HALF_UP);
        }

        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setStatDate(statDate);
        vo.setRegisterUserCount((int) registerUserCount);
        vo.setTotalResourceCount((int) totalResourceCount);
        vo.setDailyActiveUserCount(dailyActiveUserCount);
        vo.setRecClickRate(recClickRate);
        return vo;
    }
}
