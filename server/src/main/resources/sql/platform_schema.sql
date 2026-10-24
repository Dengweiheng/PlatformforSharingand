-- 个性化学习资源共享与推荐平台（简化版 MySQL 8.x）
CREATE DATABASE IF NOT EXISTS learning_platform DEFAULT CHARSET utf8mb4;
use learning_platform;

SET NAMES utf8mb4;

-- =========================
-- 1. 用户中心
-- =========================
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100),
    phone VARCHAR(20),
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS user_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    nickname VARCHAR(50),
    avatar_url VARCHAR(255),
    major_name VARCHAR(100),
    interest_tags VARCHAR(255),
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_profile_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户资料';

CREATE TABLE IF NOT EXISTS system_message (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    msg_type VARCHAR(30) NOT NULL COMMENT 'AUDIT_RESULT/RECOMMEND_UPDATE/COMMENT_REPLY',
    title VARCHAR(100) NOT NULL,
    content VARCHAR(500) NOT NULL,
    is_read TINYINT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_msg_user (user_id),
    INDEX idx_msg_type (msg_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统消息';

-- =========================
-- 2. 分类与资源上传
-- =========================
CREATE TABLE IF NOT EXISTS resource_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    parent_id BIGINT DEFAULT NULL,
    name VARCHAR(100) NOT NULL,
    sort_no INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源分类（支持两级）';

CREATE TABLE IF NOT EXISTS resource_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    uploader_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    summary VARCHAR(500),
    category_lv1_id BIGINT,
    category_lv2_id BIGINT,
    subject_name VARCHAR(100),
    exam_type VARCHAR(50),
    competition_type VARCHAR(50),
    file_url VARCHAR(255),
    cover_url VARCHAR(255),
    preview_url VARCHAR(255),
    latest_version_no INT DEFAULT 1,
    visibility VARCHAR(20) DEFAULT 'PUBLIC',
    audit_status VARCHAR(20) DEFAULT 'PENDING',
    download_count INT DEFAULT 0,
    favorite_count INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_resource_uploader (uploader_id),
    INDEX idx_resource_title (title),
    INDEX idx_resource_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源主表';

CREATE TABLE IF NOT EXISTS resource_file_version (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    version_no INT NOT NULL,
    file_url VARCHAR(255) NOT NULL,
    file_name VARCHAR(255),
    change_log VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_resource_version (resource_id, version_no),
    INDEX idx_version_resource (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源历史版本';

CREATE TABLE IF NOT EXISTS resource_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tag_name VARCHAR(50) NOT NULL UNIQUE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

CREATE TABLE IF NOT EXISTS resource_tag_rel (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    UNIQUE KEY uk_resource_tag (resource_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源标签关系';

-- =========================
-- 3. 社区模块
-- =========================
CREATE TABLE IF NOT EXISTS community_post (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    post_type VARCHAR(30) NOT NULL DEFAULT 'DISCUSSION' COMMENT 'DISCUSSION/RESOURCE_UPDATE',
    resource_id BIGINT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_post_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区帖子';

CREATE TABLE IF NOT EXISTS community_comment (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    content VARCHAR(1000) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_comment_post (post_id),
    INDEX idx_comment_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='社区评论';

-- =========================
-- 4. 下载/收藏/评分 行为记录
-- =========================
CREATE TABLE IF NOT EXISTS user_action (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    action_type VARCHAR(20) NOT NULL COMMENT 'DOWNLOAD/FAVORITE/RATE',
    score TINYINT DEFAULT NULL COMMENT '评分时写1-5，其他可为空',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_action_user (user_id),
    INDEX idx_action_resource (resource_id),
    INDEX idx_action_type (action_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为表';

CREATE TABLE IF NOT EXISTS favorite_folder (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    folder_name VARCHAR(100) NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_folder (user_id, folder_name),
    INDEX idx_folder_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏夹';

CREATE TABLE IF NOT EXISTS favorite_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    folder_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_folder_resource (folder_id, resource_id),
    INDEX idx_item_folder (folder_id),
    INDEX idx_item_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收藏夹资源明细';

-- =========================
-- 5. 推荐结果（简化版）
-- =========================
CREATE TABLE IF NOT EXISTS user_recommendation (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    rec_score DECIMAL(8,4) DEFAULT 0,
    rec_reason VARCHAR(200),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_rec (user_id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='推荐结果';

-- =========================
-- 6. 后台管理（审核与统计）
-- =========================
CREATE TABLE IF NOT EXISTS resource_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    admin_id BIGINT NOT NULL,
    audit_result VARCHAR(20) NOT NULL COMMENT 'PASSED/REJECTED/DELETED',
    audit_remark VARCHAR(255),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_resource (resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源审核日志';

CREATE TABLE IF NOT EXISTS daily_stat_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    stat_date DATE NOT NULL,
    register_user_count INT DEFAULT 0,
    total_resource_count INT DEFAULT 0,
    daily_active_user_count INT DEFAULT 0,
    rec_click_rate DECIMAL(6,4) DEFAULT 0,
    UNIQUE KEY uk_stat_date (stat_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日统计';
-- =========================
-- 7. 初始化测试数据
-- =========================

-- 密码均为 123456 (BCrypt 加密)
-- 如果您的环境解析有问题，可以手动 update 为明文或您自定义的加密串
-- 管理员：admin / 123456
INSERT INTO sys_user (username, password, email, phone, role, status)
VALUES ('admin', '$2a$10$vPZ5zQfBq.aM7.x3K0vMzu/q.mJ5.aL1m5y5.O7v.OuSJQyY3w9O7v.OuSJQ', 'admin@example.com', '13800000000', 'ADMIN', 1);

-- 普通用户：user1 / 123456
INSERT INTO sys_user (username, password, email, phone, role, status)
VALUES ('user1', '$2a$10$vPZ5zQfBq.aM7.x3K0vMzu/q.mJ5.aL1m5y5.O7v.OuSJQyY3w9O7v.OuSJQ', 'user1@example.com', '13811111111', 'USER', 1);

-- 新增用户：张三 / 123456
INSERT INTO sys_user (username, password, email, phone, role, status)
VALUES ('zhangsan', '$2a$10$vPZ5zQfBq.aM7.x3K0vMzu/q.mJ5.aL1m5y5.O7v.OuSJQyY3w9O7v.OuSJQ', 'zhangsan@example.com', '13822222222', 'USER', 1);

-- 初始化用户资料
INSERT INTO user_profile (user_id, nickname, avatar_url, major_name, interest_tags)
VALUES (1, '超级管理员', 'https://api.dicebear.com/7.x/avataaars/svg?seed=Admin', '系统架构', '管理,安全,AI');

INSERT INTO user_profile (user_id, nickname, avatar_url, major_name, interest_tags)
VALUES (2, '普通同学', 'https://api.dicebear.com/7.x/avataaars/svg?seed=User1', '计算机科学', 'Java,Vue,考研');

INSERT INTO user_profile (user_id, nickname, avatar_url, major_name, interest_tags)
VALUES (3, '张三', 'https://api.dicebear.com/7.x/avataaars/svg?seed=ZhangSan', '计算数学', '大数据,Python,数学建模');

-- =========================
-- 8. 扩展测试数据（分类与张三发布的资源）
-- =========================

-- 初始化资源分类
INSERT INTO resource_category (id, parent_id, name, sort_no) VALUES 
(1, NULL, '计算机科学', 1),
(2, NULL, '数学与理学', 2),
(3, NULL, '外语学习', 3),
(4, 1, 'Java后端开发', 1),
(5, 1, 'Vue前端框架', 2),
(6, 1, '人工智能与算法', 3),
(7, 2, '高等数学精华', 1),
(8, 2, '线性代数与矩阵', 2),
(9, 3, '考研英语词汇', 1),
(10, 3, '四六级真题集', 2);

-- 初始化资源信息（10条，全部归属于张三 ID=3）
INSERT INTO resource_info (id, uploader_id, title, summary, category_lv1_id, category_lv2_id, subject_name, exam_type, competition_type, file_url, cover_url, audit_status, download_count, favorite_count) VALUES
(1, 3, 'Java 并发编程实战指南', '系统讲解多线程与高并发处理，大厂面试必备。', 1, 4, 'Java', '社招/校招', '技术面试', 'http://example.com/java_concurrent.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=java', 'PASSED', 120, 45),
(2, 3, 'Vue3+Vite 全栈实战', '手把手教你搭建现代化前端工程化项目。', 1, 5, 'Vue', '前端开发', '个人项目', 'http://example.com/vue3_fullstack.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=vue', 'PASSED', 85, 32),
(3, 3, '考研数学：高等数学重点笔记', '总结了考研常考的导数、积分、级数等考点。', 2, 7, '数学', '考研', '国家笔试', 'http://example.com/math_notes.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=math', 'PASSED', 300, 150),
(4, 3, '线性代数进阶习题集', '涵盖特征值、二次型等高难度习题及解析。', 2, 8, '线性代数', '期末检修', '学科基础', 'http://example.com/linear_algebra.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=linalg', 'PASSED', 60, 20),
(5, 3, '考研英语词汇闪过', '高频词、低频词分类整理，让你背词更高效。', 3, 9, '英语', '考研', '英语证书', 'http://example.com/english_words.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=english', 'PASSED', 450, 210),
(6, 3, 'Python 深度学习入门', '从零打造手写数字识别神经网络。', 1, 6, '人工智能', '前沿研究', '学术竞赛', 'http://example.com/python_dl.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=python', 'PASSED', 210, 88),
(7, 3, '四级英语模拟题（带听力原文）', '包含最新的10套全真模拟卷及详尽解析。', 3, 10, '英语', '大学四级', '水平认定', 'http://example.com/cet4_sim.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=cet4', 'PASSED', 180, 55),
(8, 3, '数据结构：常用算法模板', 'C++ 实战实现：链表、树、图、动态规划。', 1, 4, '数据结构', '算法竞赛', 'ACM/蓝桥杯', 'http://example.com/ds_algo.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=algo', 'PASSED', 95, 40),
(9, 3, 'Spring Boot 微服务架构设计', '探讨分布式、微服务以及云原生架构。', 1, 4, 'Spring Boot', '架构师进阶', '企业级开发', 'http://example.com/spring_micro.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=spring', 'PASSED', 150, 65),
(10, 3, '大学物理公式汇总', '覆盖力、热、光、电、磁全套公式整理。', 2, 7, '物理', '期末复习', '基础课程', 'http://example.com/physics_formula.pdf', 'https://api.dicebear.com/7.x/identicon/svg?seed=physics', 'PASSED', 75, 18);

-- 初始化资源版本记录
INSERT INTO resource_file_version (resource_id, version_no, file_url, file_name, change_log) VALUES
(1, 1, 'http://example.com/java_concurrent.pdf', 'java_concurrent.pdf', '初始版本'),
(2, 1, 'http://example.com/vue3_fullstack.pdf', 'vue3_fullstack.pdf', '初始版本'),
(3, 1, 'http://example.com/math_notes.pdf', 'math_notes.pdf', '初始版本'),
(4, 1, 'http://example.com/linear_algebra.pdf', 'linear_algebra.pdf', '初始版本'),
(5, 1, 'http://example.com/english_words.pdf', 'english_words.pdf', '初始版本'),
(6, 1, 'http://example.com/python_dl.pdf', 'python_dl.pdf', '初始版本'),
(7, 1, 'http://example.com/cet4_sim.pdf', 'cet4_sim.pdf', '初始版本'),
(8, 1, 'http://example.com/ds_algo.pdf', 'ds_algo.pdf', '初始版本'),
(9, 1, 'http://example.com/spring_micro.pdf', 'spring_micro.pdf', '初始版本'),
(10, 1, 'http://example.com/physics_formula.pdf', 'physics_formula.pdf', '初始版本');

-- 初始化一些标签
INSERT INTO resource_tag (tag_name) VALUES ('高频重点'), ('零基础'), ('大厂校招'), ('考研冲刺'), ('极简总结'), ('实战派');

-- 绑定标签关系
INSERT INTO resource_tag_rel (resource_id, tag_id) VALUES (1, 3), (3, 4), (5, 4), (5, 1), (8, 6), (10, 5);
