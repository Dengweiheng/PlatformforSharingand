# 后端接口验收清单（3.1 ~ 3.6）

说明：
- 基础前缀：`http://localhost:8080`
- 仅列最核心接口，按模块验收。

## 3.1 用户中心模块

### 1) 注册
- POST `/api/users/register`
- Body(JSON):
```json
{
  "username": "u1001",
  "password": "123456",
  "email": "u1001@test.com",
  "phone": "13800000001"
}
```

### 2) 登录（用户名/邮箱/手机号都可）
- POST `/api/users/login`
- Body(JSON):
```json
{
  "account": "u1001",
  "password": "123456"
}
```

### 3) 找回密码
- POST `/api/users/reset-password`
- Body(JSON):
```json
{
  "account": "u1001",
  "newPassword": "654321"
}
```

### 4) 个人资料
- GET `/api/users/{userId}/profile`
- PUT `/api/users/profile`
- Body(JSON):
```json
{
  "userId": 1,
  "nickname": "张三",
  "avatarUrl": "/uploads/avatar/a1.png",
  "majorName": "计算机科学",
  "interestTags": "考研,算法,Java"
}
```

### 5) 消息通知
- GET `/api/messages/user/{userId}`
- PUT `/api/messages/{id}/read`
- POST `/api/messages`
- Body(JSON):
```json
{
  "userId": 1,
  "msgType": "RECOMMEND_UPDATE",
  "title": "推荐更新",
  "content": "已为你刷新推荐列表"
}
```

## 3.2 资源管理与上传模块

### 1) 资源上传（多格式 + 元信息 + 封面）
- POST `/api/resources/upload`
- Form-Data:
  - `uploaderId`: 1
  - `title`: 算法导论笔记
  - `summary`: 第1-8章整理
  - `categoryLv1Id`: 1
  - `categoryLv2Id`: 2
  - `subjectName`: 计算机
  - `examType`: 考研
  - `competitionType`: 挑战杯
  - `visibility`: PUBLIC
  - `tags`: 算法,数据结构
  - `resourceFile`: 文件（pdf/doc/docx/ppt/pptx/mp4/avi/mov/mkv）
  - `coverFile`: 文件（jpg/jpeg/png/webp，可选）

### 2) 资源预览页
- GET `/api/resources/{id}/preview?viewerId=1`

### 3) 版本管理
- GET `/api/resources/{id}/versions`
- POST `/api/resources/{id}/versions`
- Form-Data:
  - `uploaderId`: 1
  - `resourceFile`: 新版本文件
  - `changeLog`: 修复第3章错误

### 4) 可见性管理
- PUT `/api/resources/{id}/visibility?uploaderId=1&visibility=PRIVATE`

## 3.3 社区分类与导航模块

### 1) 分类管理与分类树
- POST `/api/navigation/categories?name=计算机&sortNo=1`
- POST `/api/navigation/categories?parentId=1&name=考研&sortNo=1`
- GET `/api/navigation/categories/tree`

### 2) 热门标签、榜单
- GET `/api/navigation/tags/hot?limit=10`
- GET `/api/navigation/rank/latest?limit=10`
- GET `/api/navigation/rank/hot?limit=10`

### 3) 搜索筛选
- GET `/api/navigation/search?keyword=算法&categoryLv1Id=1&sortBy=hot&limit=20`
- GET `/api/navigation/search?tagName=数据结构&examType=考研&sortBy=latest`

## 3.4 个性化推荐模块

### 1) 获取推荐
- GET `/api/recommendations/{userId}?limit=10&refresh=false`

### 2) 手动刷新推荐
- POST `/api/recommendations/{userId}/refresh?limit=10`

说明：
- UserCF：基于下载/收藏行为计算余弦相似度。
- 冷启动：热门资源 + 兴趣标签。
- Redis：缓存推荐列表与相似度矩阵。
- 实时反馈：下载/收藏后异步刷新推荐。

## 3.5 下载与收藏模块

### 1) 下载（含频率控制）
- POST `/api/resources/{id}/download?userId=1`

### 2) 收藏（快捷收藏）
- POST `/api/resources/{id}/favorite?userId=1`

### 3) 收藏夹管理
- POST `/api/folders?userId=1&folderName=考研资料`
- GET `/api/folders/user/1`
- POST `/api/folders/{folderId}/items?userId=1&resourceId=10`
- DELETE `/api/folders/{folderId}/items/{itemId}?userId=1`
- DELETE `/api/folders/{folderId}/items/batch`
- Body(JSON):
```json
{
  "userId": 1,
  "folderId": 1,
  "itemIds": [11, 12, 13]
}
```

### 4) 交互记录展示（个人中心）
- GET `/api/folders/interaction/{userId}?limit=20`

## 3.6 后台管理与数据分析模块

### 1) 用户管理
- GET `/api/admin/users?status=1&keyword=u1001`
- PUT `/api/admin/users/{userId}/status?status=0`
- GET `/api/admin/users/{userId}/actions?limit=50`

### 2) 资源审核
- GET `/api/admin/resources/pending`
- PUT `/api/admin/resources/{resourceId}/audit?adminId=1&result=PASSED&remark=通过`

### 3) 数据看板
- GET `/api/admin/dashboard/overview`
- POST `/api/admin/dashboard/snapshot?date=2026-04-09`

## 启动前检查

1. MySQL 已启动，库名 `learning_platform`。
2. Redis 已启动，默认 `localhost:6379`。
3. 执行 SQL：`src/main/resources/sql/platform_schema.sql`。
4. 运行项目后再按上面顺序联调。
