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
- **火影忍者OL**：官方公告、忍者图鉴（含技能）、忍者测验（Excel 批量导入）、组织积分（周积分/礼包/Excel 导入）
- **个人工作台**：记事本（富文本 + 图片）、待办、记账、绩效（项目/加班/迟到）、工资
- **话题集中营**：圈子、帖子、评论、点赞、收藏
- **AI 工具**：AI 编程资讯（Hacker News，自动翻译）、Code Arena 模型排行榜（arena.ai，12 个分类 + 价格筛选）、Ollama 模型库（本地可下载模型与变体）
- **账号体系**：注册 / 登录 / JWT 无状态鉴权 / 邮箱找回密码（163 SMTP）/ 积分与会员（每日签到、积分自助升级、卡密兑换）
- **管理员后台**：用户、资产、网站审批、笔记审批、公告、话题、测验、积分与会员、卡密管理（待审批红点 + 邮件提醒）

## 目录结构

```
evernox-studio/
├── evernox-backend/                # Spring Boot 后端
│   ├── config/application.yml      # 外置敏感配置（已 gitignore，需自行创建）
│   ├── data/                       # 上传的图片（自动创建，本地可随时清空）
│   ├── src/main/java/com/evernox/  # controller / service / repository / entity / dto / config / security / util
│   └── src/main/resources/         # application.yml / application-dev.yml / schema.sql
├── evernox-frontend/               # Vue 3 前端
│   └── src/                        # api / views / components / router / stores / types / utils / styles
├── nginx-1.30.4/                   # Nginx 二进制（已 gitignore）+ conf/nginx.conf（已纳入版本管理）
├── Redis/                          # Redis 二进制（已 gitignore；忽略规则 /Redis*/ 也兼容带版本号的目录名）
├── jwt-keys/                       # JWT 密钥对，首启动自动生成（已 gitignore）
├── start.bat                       # 本地一键启动脚本
├── 使用手册.md                     # 部署运维 + 重装恢复 + 功能说明（重点看这个）
└── README.md
```

> `jwt-keys/` 也可能出现在 `evernox-backend/jwt-keys/`（取决于启动时的工作目录），两处都会自动生成、都已 gitignore，不用管。

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
> `codec.secret / codec.salt` 一旦有图片数据后不可再修改，否则已存图片无法解码。**`codec.salt` 必须是合法 Base64 字符串**（否则启动报 `初始化图片编解码密钥失败`），本地开发可直接用 `bG9jYWwtZGV2LXNhbHQtMDE=`。
> 上面这个文件是**本地开发唯一需要手动准备的东西**：JWT 密钥、图片目录、数据库、数据表全部自动生成。本地开发**不需要备份任何东西**（重装系统见「使用手册.md」第 5A 节）。

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

生产环境使用 Nginx 托管 `dist` 静态文件（对外端口 80），并将 `/api` 反向代理到后端 jar（后端默认端口 11002，context-path `/api`）。服务器上用 `切换端口11002.bat` / `切换端口11003.bat` 在 11002 / 11003 之间轮换运行，所以 `nginx.conf` 里的 `proxy_pass` 必须与后端**当前实际监听的端口**一致（仓库当前快照是 `11003`）。后端部署时需把 `config/application.yml` 放到 jar 同级目录。

## 注意事项

- **`evernox-backend/config/application.yml` 含明文密钥，已被 gitignore，切勿提交。** 克隆后需自行创建（模板见上文「本地开发」）。
- JWT 密钥对（`jwt-keys/`，可能在项目根目录或 `evernox-backend/` 下）会在首次启动时自动生成，无需手动维护、也无需备份，删除后会自动重建。
- 图片以加密形式存储在本地磁盘（`evernox-backend/data/`），无云对象存储依赖；本地开发可随意清空，**只有服务器上的 `data/` 才需要备份**。
- **服务器**需要备份的只有 3 样：`config/application.yml`、数据库转储 `.sql`、`data/` 目录（详见「使用手册.md」第 4 节）。
- 完整部署运维与重装恢复流程见 **[使用手册.md](./使用手册.md)**。
