# EverNox Studio · 永夜照相馆

一个自托管的个人数字生活管理平台：图床相册、网站导航、记事本、记账、绩效与工资、话题社区、火影忍者OL 图鉴/测验/组织积分、项目版本发布（启动器更新镜像）、应援板、实用查询与 AI 工具。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2.5 · Java 21 · MyBatis-Plus 3.5.5 · MySQL 8 · Redis（应援板像素缓存，不可用时自动回退数据库）· JWT (jjwt) · Argon2id · jsoup · Hutool · Apache POI（组织积分 Excel 导入） |
| 前端 | Vue 3 · Vite 5 · Element Plus · Pinia · Vue Router 4 · ECharts 6 · Quill 2 · axios · TypeScript |
| 部署 | 可执行 jar + Nginx（Windows），文件存储在本机磁盘 |

## 功能模块

- **图床管理**：图片 / 相册，公开与私密可见性，服务端加密落盘存储，缩略图，相册封面裁剪
- **网站分享**：友链导航 + 标签 + 管理员审批
- **火影忍者OL**：官方公告、忍者图鉴（离线数据集，含技能）、忍者测验（JSON 批量导入；答案留空的题目由用户投票确认，连续 3 人选择一致即写入答案、各 +10 积分）、组织积分（周积分 / 礼包 / Excel 导入）
- **项目版本发布**：每个项目独立的版本列表、发布说明、附件目录与公开下载地址，自动生成 `latest.yml`，可替代 GitHub Releases 作为启动器更新镜像（入口在个人区「项目管理」，仅管理员可见，数据按账号隔离）
- **个人工作台**：记事本（富文本 + 图片，可公开）、待办、记账、绩效（项目 / 加班 / 迟到）、工资
- **话题集中营**：圈子、帖子、评论、点赞、收藏
- **应援板**：多人协作像素画，Redis 缓存加速
- **实用查询**：地震信息、天气查询
- **AI 工具**：AI 编程资讯（Hacker News，自动翻译）、Code Arena 模型排行榜（12 个分类 + 价格筛选）、Ollama 模型库（本地可下载模型与变体）
- **账号体系**：注册 / 登录 / JWT 无状态鉴权 / 邮箱找回密码（163 SMTP）/ 积分与会员（每日签到、积分自助升级、卡密兑换）
- **管理员后台**：用户、资产、网站审批、笔记审批、公告、话题、测验、积分与会员、卡密、访问数据、应援板

## 目录结构

```
evernox-studio/
├── evernox-backend/                # Spring Boot 后端
│   ├── config/application.yml      # 外置敏感配置（已 gitignore，需自行创建）
│   ├── data/                       # 运行时数据（自动创建）：图片、缩略图、版本发布附件
│   ├── src/main/java/com/evernox/  # controller / service / repository / entity / dto / config / security / util
│   ├── src/main/resources/         # application.yml / application-dev.yml / schema.sql
│   ├── target/                     # IDE（iDev / JDT）编译输出，不要手动删
│   └── target-package/             # 打包输出（mvn -Dbuild.dir=target-package）
├── evernox-frontend/               # Vue 3 前端
│   ├── src/                        # api / views / components / router / stores / types / utils / styles
│   └── dist/                       # 打包产物（npm run build 生成，不上传）
├── nginx-1.30.4/                   # Nginx 二进制 + 站点配置（自行从官网下载，不入库）
├── Redis/                          # Redis 二进制（自行下载，不入库；忽略规则 /Redis*/ 也兼容带版本号的目录名）
├── jwt-keys/                       # JWT 密钥对，首启动自动生成（已 gitignore）
├── start.bat                       # 本地一键启动脚本
├── 切换端口11002.bat / 切换端口11003.bat  # 服务器后端端口平滑切换
├── 使用手册.md                     # 部署运维 + 重装恢复 + 功能说明（重点看这个）
└── README.md
```

> `nginx-1.30.4/`、`Redis/` 只存在于自己的机器上（都是官方二进制，不入库），从 GitHub clone 下来不会有；`config/application.yml` 也没被提交，需要按下文自行创建。
>
> `jwt-keys/` 也可能出现在 `evernox-backend/jwt-keys/`（取决于启动时的工作目录），两处都会自动生成、都已 gitignore，不用管。

## 环境要求

- JDK 21
- Maven 3.8+
- Node.js 18+（含 npm）
- MySQL 8.0
- Redis（只用于应援板像素缓存；没启动时自动回退数据库，不装也能跑）
- Nginx（仅生产部署需要；本机用的是 1.30.4）
- Git

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
> Redis 默认连 `localhost:6379`（见 `application.yml` 的 `spring.data.redis`），地址不同时在这里覆盖即可。
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

# 前端（端口 5211，已配置 /api 代理到后端 11002）
cd evernox-frontend
npm install
npm run dev
```

访问 `http://localhost:5211`。

## 构建与部署

```bash
# 后端打包（独立输出目录，不会删掉 IDE 用的 target/classes）
cd evernox-backend
mvn clean package "-Dbuild.dir=target-package" -DskipTests
# 产物：target-package/evernox-backend-1.0.0.jar（约 60–70MB）

# 前端打包
cd evernox-frontend
npm run build
# 产物：dist/
```

> 为什么要加 `-Dbuild.dir=target-package`：IDE（iDev / JDT）把编译结果放在 `target/classes` 并缓存索引，直接 `mvn clean` 会删掉它，IDE 随后整片报 `import cannot be resolved`。打包只产出 `evernox-backend-1.0.0.jar`，部署时改名为 `evernox-backend-11002.jar` / `evernox-backend-11003.jar` 由自己决定。

生产环境用 Nginx 托管 `dist` 静态文件（对外端口 80，服务器公网端口按实际配置），并把 `/api` 反向代理到后端 jar（默认 11002，context-path `/api`）。服务器上用 `切换端口11002.bat` / `切换端口11003.bat` 在 11002 / 11003 之间轮换运行，所以 `nginx.conf` 里的 `proxy_pass` 必须与后端**当前实际监听的端口**一致（以服务器上那份配置为准）。另外两处不能漏：

- `client_max_body_size 8m;`：版本发布附件走 4MB 分片上传，Nginx 默认 1MB 会直接 413。
- `/api/release/public/` 不要做 SPA 的 `try_files ... /index.html` 重写、不要开 gzip：启动器更新包依赖 Range / Content-Length 断点续传。

后端部署时需把 `config/application.yml` 放到 jar 同级目录。完整步骤（含端口切换、备份恢复）见 **[使用手册.md](./使用手册.md)** 第 5、6 节。

## 注意事项

- **`evernox-backend/config/application.yml` 含明文密钥，已被 gitignore，切勿提交。** 克隆后需自行创建（模板见上文「本地开发」）。
- JWT 密钥对（`jwt-keys/`，可能在项目根目录或 `evernox-backend/` 下）会在首次启动时自动生成，无需手动维护、也无需备份，删除后会自动重建。
- 图片与版本发布附件都存储在本地磁盘（`evernox-backend/data/`，后者在 `data/releases/`），无云对象存储依赖；本地开发可随意清空，**只有服务器上的 `data/` 才需要备份**。
- **服务器**需要备份的只有 3 样：`config/application.yml`、数据库转储 `.sql`、`data/` 目录（详见「使用手册.md」第 4 节）。
- 版本发布中心对外接口（`latest.yml`、匿名下载）的契约见「使用手册.md」第 6.5 节。
