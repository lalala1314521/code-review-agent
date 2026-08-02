<p align="center">
  <h1 align="center">Code Review Agent</h1>
  <p align="center">基于 LLM + 规则引擎的自动化代码审查平台，支持 GitLab / GitHub</p>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/JDK-17+-orange" alt="JDK 17+">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen" alt="Spring Boot 3.2">
  <img src="https://img.shields.io/badge/Vue-3.4-4fc08d" alt="Vue 3.4">
  <img src="https://img.shields.io/badge/license-MIT-blue" alt="license MIT">
</p>

---

## 功能特性

- **多平台适配**：统一 GitLab Merge Request 与 GitHub Pull Request 的差异拉取、评论回写接口，通过 `PlatformRouter` 屏蔽平台差异
- **LLM 代码审查**：将 Unified Diff 构造为结构化 Prompt，调用大模型输出分类审查意见，支持 DeepSeek、通义千问、OpenAI 三套 Provider 运行时动态切换
- **规则引擎**：内置 9 条静态分析规则（SQL 注入检测、硬编码密钥、空 catch 块、事务边界检查、超大函数、魔法数字等），规则可启用/禁用、可自定义参数，审查结果与 LLM 发现合并裁决
- **Veridct 综合裁决**：规则引擎与 LLM 的发现经 `VerdictDecider` 综合评估，输出 APPROVE / NEEDS_FIX / BLOCK 三级结论及置信度
- **Webhook 幂等**：基于 Redis 的 `IdempotentGuard`，同一 commit 仅触发一次审查，避免重复评论
- **异步审查 + SSE 进度**：Webhook 接收后立即返回 200，审查转入异步线程池执行；前端通过 SSE 实时订阅进度（排队 → 拉取 diff → 规则扫描 → LLM 分析 → 裁决 → 发布）
- **上下文对话**：支持在审查结果上与 Agent 进行追问对话，携带当前 diff 上下文，用于澄清发现或获取修复建议
- **管理控制台**：Vue 3 + Tailwind CSS 构建的玻璃态管理台，包含仪表盘（KPI + 趋势）、审查队列、审查详情、历史记录、规则管理、Provider 配置等视图
- **统计聚合**：按日 + 平台 + 仓库维度定时聚合审查数据，支持通过趋势图表观察代码质量变化
- **审查评估框架**：`eval/` 模块提供批量回归测试能力，可定义测试用例与预期输出，验证 Prompt 调优或规则改动的影响

## 架构概览

```
                  ┌──────────────┐
                  │  GitLab /    │
                  │  GitHub      │
                  └──────┬───────┘
                         │ Webhook (MR/PR events)
                         ▼
              ┌─────────────────────┐
              │  WebhookController  │
              │  + SignatureVerify  │
              └────────┬────────────┘
                       │
              ┌────────▼────────────┐
              │  IdempotentGuard    │  ← Redis
              └────────┬────────────┘
                       │
              ┌────────▼────────────┐
              │  Async Thread Pool  │
              └────────┬────────────┘
                       │
         ┌─────────────┼─────────────┐
         ▼             ▼             ▼
  ┌──────────┐  ┌──────────┐  ┌───────────┐
  │ Diff     │  │ Rule     │  │ LLM       │
  │ Parser   │  │ Engine   │  │ Provider  │
  └────┬─────┘  └────┬─────┘  └─────┬─────┘
       │              │              │
       └──────────────┼──────────────┘
                      ▼
           ┌──────────────────┐
           │  VerdictDecider  │
           └────────┬─────────┘
                    │
         ┌──────────┼──────────┐
         ▼                     ▼
  ┌──────────────┐    ┌─────────────────┐
  │ Comment      │    │ MySQL           │
  │ Publisher    │    │ + SSE Progress  │
  └──────────────┘    └─────────────────┘
```

## 技术栈

| 层级 | 技术 | 说明 |
|------|------|------|
| 运行时 | JDK 17, Spring Boot 3.2 | 后端框架 + 嵌入式 Tomcat |
| Web | Spring MVC, RestClient | REST API 与外部 HTTP 调用 |
| 持久化 | MyBatis-Plus 3.5, MySQL 8 | ORM + 关系存储 |
| 缓存 | Spring Data Redis | Webhook 幂等去重 |
| 异步 | `@Async` + ThreadPoolExecutor | 审查任务异步执行 |
| 实时推送 | SSE (Server-Sent Events) | 审查进度推送至前端 |
| 加密 | AES-GCM | API Key 落库加密 |
| 前端 | Vue 3, TypeScript, Vite 5 | SPA 管理控制台 |
| UI | Tailwind CSS 3 | 液态玻璃风格 |
| 状态管理 | Pinia, Vue Router 4 | 状态与路由 |
| LLM | DeepSeek / Qwen / OpenAI | OpenAI 兼容协议统一调用 |
| 构建 | Maven Wrapper | 无需预装 Maven |

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Node.js 18+（仅前端开发需要）

### 1. 初始化数据库

```sql
-- 执行建库建表脚本，包含 5 张业务表 + 9 条预设规则
mysql -u root -p < src/main/resources/db/schema.sql
```

### 2. 配置

项目通过 `application.yml` 提供默认值，敏感信息通过环境变量注入。本地开发可编辑 `src/main/resources/application-dev.yml`：

```yaml
# GitLab
gitlab:
  base-url: https://gitlab.example.com
  token: <your-gitlab-pat>
  webhook-secret: <your-webhook-secret>

# GitHub
github:
  token: <your-github-pat>
  webhook-secret: <your-github-webhook-secret>

# LLM（至少配置一个 Provider）
llm:
  default-provider: deepseek
  providers:
    deepseek:
      api-key: <your-deepseek-api-key>

# MySQL（覆盖默认值）
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/code_review_agent?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: <your-password>

# Redis（覆盖默认值）
  data:
    redis:
      host: localhost
      port: 6379
```

生产环境请使用环境变量，参考 [配置参考](#配置参考)。

### 3. 启动后端

```bash
./mvnw spring-boot:run
```

服务默认监听 `http://localhost:8080`。

### 4. 启动前端（可选）

```bash
cd frontend
npm install
npm run dev
```

管理台 `http://localhost:5173`，API 请求通过 Vite 代理转发至后端。

### 5. 配置 Webhook

**GitLab** — Repo → Settings → Webhooks：

| 字段 | 值 |
|------|-----|
| URL | `http://<host>:8080/webhook/gitlab` |
| Secret token | 与 `WEBHOOK_SECRET` 一致 |
| Triggers | Merge request events |

**GitHub** — Repo → Settings → Webhooks：

| 字段 | 值 |
|------|-----|
| Payload URL | `http://<host>:8080/webhook/github` |
| Content type | `application/json` |
| Secret | 与 `GITHUB_WEBHOOK_SECRET` 一致 |
| Events | Pull requests |

### 6. 验证

向已配置的仓库提交一个 MR / PR，一分钟内应收到 AI 审查评论。管理台 `Dashboard` 页可查看审查进度与统计。

## 配置参考

完整环境变量列表：

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `GITLAB_BASE_URL` | GitLab 实例地址 | `https://gitlab.com` |
| `GITLAB_TOKEN` | GitLab Personal Access Token（需 `api` scope） | — |
| `WEBHOOK_SECRET` | GitLab Webhook 验签密钥 | — |
| `GITHUB_API_URL` | GitHub API 地址 | `https://api.github.com` |
| `GITHUB_TOKEN` | GitHub Personal Access Token（需 `repo` scope） | — |
| `GITHUB_WEBHOOK_SECRET` | GitHub Webhook HMAC-SHA256 密钥 | — |
| `MYSQL_URL` | 数据库连接串 | `jdbc:mysql://localhost:3306/code_review_agent?...` |
| `MYSQL_USERNAME` | 数据库用户 | `root` |
| `MYSQL_PASSWORD` | 数据库密码 | — |
| `REDIS_HOST` | Redis 地址 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `REDIS_PASSWORD` | Redis 密码 | — |
| `LLM_DEFAULT_PROVIDER` | 默认 LLM Provider | `deepseek` |
| `DEEPSEEK_API_KEY` | DeepSeek API Key | — |
| `DASHSCOPE_API_KEY` | 通义千问 API Key | — |
| `OPENAI_API_KEY` | OpenAI API Key | — |

## API 概览

| 端点 | 方法 | 说明 |
|------|------|------|
| `/webhook/gitlab` | POST | GitLab MR 事件入口 |
| `/webhook/github` | POST | GitHub PR 事件入口 |
| `/api/health` | GET | 健康检查 |
| `/api/reviews` | GET | 审查列表（分页 + 筛选） |
| `/api/reviews/{id}` | GET | 审查详情（含 findings） |
| `/api/reviews/local` | POST | 手动提交 diff 触发审查 |
| `/api/reviews/{id}/chat` | POST | 审查上下文对话 |
| `/api/dashboard/kpi` | GET | 仪表盘 KPI 数据 |
| `/api/dashboard/trend` | GET | 审查趋势数据 |
| `/api/rules` | GET/PUT | 规则列表查询 / 批量更新 |
| `/api/agents` | GET | 可用 Agent 列表 |
| `/api/providers` | GET/PUT | LLM Provider 配置 |
| `/api/history` | GET | 审查历史（分页） |

## 项目结构

```
.
├── src/main/java/.../codereviewagent/
│   ├── common/              # 通用模块：响应体、鉴权、加密、异常处理
│   ├── config/              # 配置类、异步线程池、定时统计
│   ├── webhook/             # Webhook 接收、验签、幂等守卫、编排
│   ├── platform/            # 平台适配：GitLab / GitHub Client + Router
│   ├── model/               # 领域模型（UnifiedMergeRequest, DiffFile, Verdict 等）
│   ├── review/              # 审查核心
│   │   ├── diff/            #   Unified Diff 解析器
│   │   ├── llm/             #   LLM 抽象 + Provider 工厂 + 3 套实现
│   │   ├── rule/            #   规则引擎 + 9 条内置规则
│   │   ├── progress/        #   SSE 进度事件
│   │   └── chat/            #   审查上下文对话
│   ├── verdict/             # 综合裁决器
│   ├── persistence/         # 持久层（MyBatis-Plus Mapper + Service）
│   ├── publisher/           # 评论回写
│   ├── api/                 # REST Controller 层
│   └── eval/                # 审查评估框架
├── frontend/                # Vue 3 管理控制台
│   └── src/
│       ├── views/           # 页面：Dashboard / ReviewQueue / ReviewDetail / History / Rules
│       └── components/      # 组件：agent / review / rule / history / kpi / common / layout
├── docs/                    # 设计文档（共 17 篇）
├── scripts/                 # Webhook 测试脚本
├── pom.xml
└── README.md
```

## 设计文档

`docs/` 目录包含从 MVP 到 V4 的完整设计与实施记录，按编号递增对应演进顺序：

| 编号 | 文档 |
|------|------|
| 00 | 快速上手 |
| 01 | Redis 幂等与异步线程池 |
| 02 | Verdict 综合裁决机制 |
| 03 | LLM Provider 可插拔抽象 |
| 04 | Vue 3 玻璃态前端工程 |
| 05 | MySQL 持久化与 Management API |
| 06 | 可扩展规则引擎 |
| 07 | SSE 审查进度流 |
| 08 | 统计聚合与趋势 |
| 09 | 管理台功能补全 |
| 10 | Agent 运行时切换与上下文对话 |
| 11 | 2026-07-25 变更摘要 |
| 12 | UI 打磨与 Provider 管理台配置 |
| 13 | 管理台鉴权与本地审查 |
| 14 | 上下文幻觉否决机制 |
| 15 | 仓库/分支上下文增强 |
| 16 | GitHub Adapter 适配 |

## 路线图

- [x] **MVP** — Webhook → diff → DeepSeek → 评论回写
- [x] **V1** — Redis 幂等、异步审查、行级评论、Verdict 裁决、LLM Provider 抽象、前端工程初始化
- [x] **V2** — MySQL 持久化、Management API、Vue 3 玻璃态管理台、规则引擎（9 条内置规则）
- [x] **V3** — SSE 进度流、Agent/Provider 运行时切换、审查上下文对话、统计趋势、本地审查、仓库分支上下文增强
- [x] **V4** — GitHub Adapter（Webhook + PR diff + 评论回写）
- [ ] **V4+** — AST 级规则、流式聊天、团队 Agent 模板、审查评估框架完善

## License

MIT
