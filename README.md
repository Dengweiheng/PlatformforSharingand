# 个性化学习资源共享与推荐平台
(Personalized Learning Resource Sharing and Recommendation Platform)

<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.5.x-brightgreen.svg" alt="Spring Boot" />
  <img src="https://img.shields.io/badge/Vue.js-3.5.x-42b883.svg" alt="Vue 3" />
  <img src="https://img.shields.io/badge/Element%20Plus-2.13.x-409EFF.svg" alt="Element Plus" />
  <img src="https://img.shields.io/badge/MySQL-8.0+-blue.svg" alt="MySQL" />
  <img src="https://img.shields.io/badge/Redis-6.0+-red.svg" alt="Redis" />
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License" />
</p>

## 📖 项目简介

**个性化学习资源共享与推荐平台** 是一个面向师生、学习者的综合性资源共享与互动平台。平台以“资源共享”为核心，集成了**多级分类导航、资源版本迭代控制、互动社区、智能个性化推荐算法与多级后台管理**等功能。

项目采用前后端分离架构开发，后端基于 **Spring Boot 3 + MyBatis-Plus + Redis + Spring Security**，前端基于 **Vue 3 + Vite + Element Plus + Pinia**，致力于为用户提供高效、智能、流畅的学习资源获取与交流体验。
<img width="2560" height="1516" alt="image" src="https://github.com/user-attachments/assets/53f6ebe6-8630-4780-b97e-2e1b9103a5ab" />
<img width="2560" height="1516" alt="image" src="https://github.com/user-attachments/assets/829f070c-8e8b-4e0b-8d57-2224aa265328" />
<img width="2560" height="1516" alt="image" src="https://github.com/user-attachments/assets/710c47b6-70d4-4372-aab7-7c39a5b88c7b" />
<img width="2560" height="1516" alt="image" src="https://github.com/user-attachments/assets/1dc1dfa8-9e25-42fc-a27a-5cc3529ae63b" />

---

## 🌟 核心功能特性

### 1. 📚 资源中心与共享
- **多维度检索与分类**：支持按分类、关键词、标签等多维度快速查找课件、代码、文档等学习资料。
- **资源上传与发布**：支持多格式资源文件上传（最大支持200MB），并可配置资源封面、标签及简介。
- **版本控制与迭代**：同一资源支持版本更新与历史版本追溯，保障学习资料的时效性。
- **资源下载与防护**：内置下载间隔频率保护机制，保障服务器带宽与资源防刷。

### 2. 🤖 个性化协同推荐（核心算法）
- **多源行为数据采集**：记录用户浏览、下载、点赞、评分与收藏等多维交互行为。
- **混合推荐引擎**：结合基于用户协同过滤（User-CF）与基于物品/内容特征匹配的混合推荐策略。
- **Redis 缓存加速**：推荐计算结果与相似度数据通过 Redis 进行缓存与 TTL 过期管理，大幅降低数据库查询压力，毫秒级响应。

### 3. 💬 社区交流与互动中心
- **交流发帖与问答**：支持用户发布问题、心得分享与交流讨论。
- **多级评论与互动**：针对资源与帖子的多级评论、回复、点赞互动。
- **资源评分体系**：严格控制 1~5 星评分机制，多维度统计资源综合口碑，并联动触发推荐池重算。
- **个性化收藏夹**：支持用户自定义创建、管理多级多分类收藏夹，分类归档高价值资料。

### 4. 🛡️ 管理员后台中心
- **资源全生命周期审核**：后台审核上传资源，规范资源内容与安全性。
- **用户权限与封禁管理**：用户状态监控、角色权限分配及封禁机制。
- **系统消息通知**：支持向用户下发系统通知、审核结果通知及互动消息。
- **统计面板与日志追溯**：平台数据宏观监控与关键操作审计。

---

## 🛠️ 技术选型

### 后端技术栈
| 技术 | 说明 |
| :--- | :--- |
| **Spring Boot 3** | 核心开发框架 |
| **Spring Security** | 安全认证与权限拦截 |
| **MyBatis-Plus** | ORM 数据持久层框架 |
| **MySQL 8.0+** | 关系型数据库 |
| **Redis** | 高性能缓存中间件（用于推荐结果缓存、限流等） |
| **Lombok** | 简化实体类 JavaBean 开发 |

### 前端技术栈
| 技术 | 说明 |
| :--- | :--- |
| **Vue 3 (Composition API)** | 前端主流渐进式框架 |
| **Vite** | 新一代高速前端构建工具 |
| **Element Plus** | 现代 UI 组件库 |
| **Pinia** | 轻量级状态管理库 |
| **Vue Router** | 路由管理 |
| **Axios** | HTTP 网络请求库 |

---

## 📂 项目结构

```text
PlatformforSharingand/
├── server/                               # 后端工程 (Spring Boot)
│   ├── src/main/java/com/server/server/
│   │   ├── common/                       # 通用常量、枚举与统一响应封装
│   │   ├── config/                       # Web、Security、Redis 等配置
│   │   ├── controller/                   # 业务控制器接口层
│   │   ├── dto/                          # 数据传输对象
│   │   ├── entity/                       # 数据库持久化实体类
│   │   ├── mapper/                       # MyBatis-Plus 数据访问层
│   │   ├── service/                      # 业务逻辑接口及实现类 (包含推荐算法)
│   │   └── vo/                           # 前端视图展示对象
│   └── src/main/resources/
│       └── application.properties        # 核心配置文件
├── vue-project/                          # 前端工程 (Vue 3 + Vite)
│   ├── src/
│   │   ├── api/                          # 后端接口对接封装
│   │   ├── components/                   # 公共复用组件
│   │   ├── router/                       # 页面路由配置
│   │   ├── stores/                       # Pinia 状态存储
│   │   ├── views/                        # 页面视图 (资源中心/社区/推荐/个人中心/后台等)
│   │   ├── App.vue                       # 根组件
│   │   └── main.js                       # 前端入口配置
│   ├── package.json
│   └── vite.config.js
├── _localhost-2026_04_14_19_24_07-dump.sql # 完整数据库结构与初始初始化数据
├── update.sql                            # 增量更新 SQL
└── README.md                             # 项目说明文档
```

---

## 🚀 快速上手与本地部署

### 1. 环境准备
运行本项目前，请确保本地已安装以下环境：
- **JDK 17 或 JDK 21**
- **Node.js 20.x 或 22.x**
- **MySQL 8.0+**
- **Redis 6.0+**
- **Maven 3.8+**

### 2. 数据库配置
1. 打开 MySQL，创建名为 `learning_platform` 的数据库（推荐使用 `utf8mb4` 字符集）：
   ```sql
   CREATE DATABASE learning_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```
2. 依次导入根目录下的 SQL 脚本文件：
   - 导入主数据文件：`_localhost-2026_04_14_19_24_07-dump.sql`
   - 导入后续增量升级文件（若有）：`update.sql`

### 3. 后端服务启动
1. 使用 IDEA 或 Eclipse 导入 `server` 目录为 Maven 项目。
2. 检查并修改配置文件 `server/src/main/resources/application.properties`：
   ```properties
   spring.datasource.url=jdbc:mysql://localhost:3306/learning_platform?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
   spring.datasource.username=root
   spring.datasource.password=你的数据库密码

   spring.data.redis.host=localhost
   spring.data.redis.port=6379
   ```
3. 启动本地 Redis 服务。
4. 运行 `ServerApplication.java`，后端默认运行在 `http://localhost:8080`。

### 4. 前端服务启动
1. 终端进入 `vue-project` 目录：
   ```bash
   cd vue-project
   ```
2. 安装依赖：
   ```bash
   npm install
   ```
3. 启动开发服务器：
   ```bash
   npm run dev
   ```
4. 控制台输出地址后，打开浏览器访问 `http://localhost:5173`。

---

## 📌 默认账号说明

导入初始数据后，可使用以下测试账号登录体验：
- **普通用户**：可通过注册页面自由注册，或使用初始化数据库中的测试用户。
- **系统管理员**：请使用数据库中具备管理权限的账号（或查看 `管理员历史记录.md` 获取相关说明）。

---

## 🤝 贡献与反馈

欢迎提交 PR 或在 GitHub Issues 中提出改进建议与 Bug 反馈！
项目仓库地址：[PlatformforSharingand](https://github.com/Dengweiheng/PlatformforSharingand.git)
