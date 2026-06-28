# 近处 / 我们的空间

面向情侣双人使用的私密空间应用。项目采用 monorepo：

- `apps/mobile`：uniapp + Vue3 移动端，优先 H5 和微信小程序。
- `server`：Spring Boot API 服务，负责鉴权、配对、多租户隔离、相册、动态、日记、纪念日和可选模块。
- `infra`：本地 MySQL、Redis、MinIO 等依赖。
- `docs`：产品、接口、隐私和部署文档。

## 技术栈

- Java 17 + Spring Boot 3
- MySQL 8 + Flyway
- Redis 7
- uniapp + Vue3 + Pinia
- 私有对象存储，生产推荐阿里云 OSS，本地可用 MinIO 模拟

> 当前机器检测到的 `java` 是 Java 8。后端按计划使用 Spring Boot 3，需要安装 JDK 17 后再构建运行。

## 本地启动

1. 复制环境变量：

   ```bash
   cp .env.example .env
   ```

2. 启动依赖：

   ```bash
   docker compose -f infra/docker-compose.yml up -d
   ```

3. 启动后端：

   ```bash
   cd server
   mvn spring-boot:run
   ```

4. 启动移动端：

   ```bash
   cd apps/mobile
   npm install
   npm run dev:h5
   ```

## 已落地范围

- P0 工程目录与基础配置
- Spring Boot API 骨架
- Flyway 数据库表结构
- JWT 鉴权与租户上下文
- 账号 mock 登录、邀请码配对、解绑申请
- 相册、动态、日记、纪念日基础接口
- 可选模块开关与宠物栏首版接口
- uniapp 移动端基础页面、设计 token、模块注册表

## 隐私原则

- 情侣空间数据按 `couple_id` 严格隔离。
- 私密日记按 `owner_user_id` 二次校验。
- 日记正文使用应用层 AES-GCM 加密后入库。
- 媒体文件只保存对象存储 key，不保存公开直链。
- 管理后台默认只允许查看元数据，不提供私密内容明文浏览入口。
