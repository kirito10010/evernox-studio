# EverNox Studio · 永夜照相馆

一个自托管的个人数字生活管理平台：集图床相册、网站导航、记事本、记账、绩效与工资、话题社区、火影忍者OL 图鉴测验于一体。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2 · Java 21 · MyBatis-Plus · MySQL 8 · JWT (jjwt) · Argon2id · jsoup · Apache POI · Hutool |
| 前端 | Vue 3 · Vite 5 · Element Plus · Pinia · Vue Router 4 · ECharts 6 · Quill 2 · axios · TypeScript |
| 部署 | jar + Nginx（Windows），本地文件存储 |

## 功能模块

- **图床管理**：图片 / 相册，公开与私密可见性，服务端加密落盘存储，缩略图，相册封面裁剪
- **网站分享**：友链导航 + 标签 + 管理员审批
- **火影忍者OL**：官方公告、忍者图鉴（含技能）、忍者测验（Excel 批量导入）
- **个人工作台**：记事本（富文本 + 图片）、待办、记账、绩效（项目/加班/迟到）、工资
- **话题集中营**：圈子、帖子、评论、点赞、收藏
- **账号体系**：注册 / 登录 / JWT 无状态鉴权 / 邮箱找回密码（163 SMTP）
- **管理员后台**：用户、资产、网站审批、笔记审批、公告、话题、测验管理

## 目录结构

```
evernox-studio/
├── evernox-backend/                # Spring Boot 后端
│   ├── config/application.yml      # 外置敏感配置（已 gitignore，需自行创建）
│   ├── src/main/java/com/evernox/  # controller / service / repository / entity / dto / config / security / util
│   └── src/main/resources/         # application.yml / application-dev.yml / schema.sql
├── evernox-frontend/               # Vue 3 前端
│   └── src/                        # api / views / components / router / stores / types / utils / styles
├── nginx-1.30.4/                   # Nginx（生产部署，已 gitignore）
├── start.bat                       # 本地一键启动脚本
└── README.md
```

## 环境要求

- JDK 21
- Maven 3.8+
- Node.js 18+（含 npm）
- MySQL 8.0

## 本地开发

### 1. 准备外置配置

后端启动会读取工作目录下的 `config/application.yml`（优先级高于 jar 内配置，且不会被打包）。请在后端目录 `evernox-backend/config/` 下创建该文件，填入你自己的值：

```yaml
spring:
  datasource:
    username: root
    password: <你的数据库口令>
  mail:
    username: <SMTP 账号，如 xxx@163.com>
    password: <SMTP 授权码>

evernox:
  security:
    allowed-origins:
      - http://localhost:5211
  codec:
    secret: "<图片编解码密钥>"
    salt: "<图片编解码盐，Base64>"
  admin:
    username: "<管理员账号>"
    password: "<管理员初始密码>"
    email: "<管理员邮箱>"
```

> 说明：数据库 `evernox_backend` 会在首次启动时自动创建，表结构由 `src/main/resources/schema.sql` 自动初始化（`CREATE TABLE IF NOT EXISTS`）。
> `codec.secret / codec.salt` 一旦有图片数据后不可再修改，否则已存图片无法解码。

### 2. 启动

**方式一：一键启动**（Windows，双击或命令行运行）

```bat
start.bat
```

脚本会先清理 11002 / 5211 端口占用，再分别启动后端与前端。

**方式二：手动启动**

```bash
# 后端（端口 11002，context-path /api）
cd evernox-backend
mvn spring-boot:run

# 前端（端口 5211，已配置 /api 代理到后端）
cd evernox-frontend
npm install
npm run dev
```

访问 `http://localhost:5211`。

## 构建与部署

```bash
# 后端打包
cd evernox-backend
mvn clean package -DskipTests
# 产物：target/evernox-backend-1.0.0.jar

# 前端打包
cd evernox-frontend
npm run build
# 产物：dist/
```

生产环境使用 Nginx 托管 `dist` 静态文件，并将 `/api` 反向代理到后端 jar（默认对外端口 5212）。后端部署时需把 `config/application.yml` 放到 jar 同级目录。

## 注意事项

- **`evernox-backend/config/application.yml` 含明文密钥，已被 gitignore，切勿提交。** 部署或克隆后需自行补建。
- JWT 密钥对（`jwt-keys/`）会在首次启动时自动生成，无需手动维护。
- 图片以加密形式存储在本地磁盘（`evernox-backend/data/`），无云对象存储依赖。
