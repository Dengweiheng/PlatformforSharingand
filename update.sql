-- 数据库表结构更新脚本
-- 1. 为社区讨论表（community_post）新增评分字段
ALTER TABLE `community_post` ADD COLUMN `score` INT DEFAULT NULL COMMENT '评分值(1-5)';

-- 2. 为资源主表（resource_info）新增平均评分字段
ALTER TABLE `resource_info` ADD COLUMN `average_score` DECIMAL(3,2) DEFAULT 0.00 COMMENT '平均评分';

-- 3. 新建每日数据统计快照表
CREATE TABLE IF NOT EXISTS `daily_stat_snapshot` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    `stat_date` DATE NOT NULL UNIQUE COMMENT '统计日期',
    `register_user_count` INT DEFAULT 0 COMMENT '当日注册用户数',
    `total_resource_count` INT DEFAULT 0 COMMENT '平台资源总数',
    `daily_active_user_count` INT DEFAULT 0 COMMENT '日活跃用户数',
    `rec_click_rate` DECIMAL(6,4) DEFAULT 0.0000 COMMENT '推荐点击率'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日数据统计快照表';

