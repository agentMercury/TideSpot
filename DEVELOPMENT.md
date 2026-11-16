# 工程结构与开发约定（TideSpot）

> 本文承接原 README 中面向开发者的部分。面向使用者的项目介绍见 [README.md](README.md)。

## 仓库结构

```
tidespot-android/        Android 多模块工程（Kotlin / Compose）
  core:domain            评分与派生（纯 Kotlin，无 Android 依赖）
  core:data              网络 / 缓存 / Repository
  core:ui                SKIN-1 皮肤、组件、图表、文案
  feature:map            地图选点、搜索、星标、Top10
  feature:detail         详情五 Tab、因子分解、校准
  feature:favorites      收藏列表 / 分组 / 批量
  feature:settings       设置（皮肤 / 主题 / 地图 / 隐私 / 许可）
  app                    组装与导航

web/                     Web PWA（Vite + React + TS）
  src/domain             评分内核（Kotlin 平移，含对拍测试）
  src/data               网络层 / IndexedDB / Repository
  src/ui                 token、皮肤、组件、图表
  src/app                路由、环境自适应、页面
  e2e/                    Playwright 冒烟

design-handoff/          设计 token（Web 构建期读取生成 CSS）
design-handdrawn/        皮肤与换肤手册（SKIN-1）
FISHSCORE.md             评分算法说明（单一真相源）
```

## 跨端约定（摘要）

- 新需求默认 `platforms: both`：**Android 与 Web 同批交付**；不能同批必须登记 `PARITY_GAP`。
- **单一真相源**：评分算法 `FISHSCORE.md`；视觉 token `design-handoff/tokens`；文案 key 来自 Android `res/strings.xml`。
- Web 侧禁止改动 `tidespot-android/` 下源码、资源与构建脚本。
- 评分必须逐字段一致；文案 key 集合由 `check:parity` 强制一致。
- 两端的**版本号独立管理**（发布节奏不同），这不是缺陷。

## 文档地图（先看这里，别从旧文档开始）

这个项目积累了较多历史文档。**状态标注很重要**——标「历史」的不要用于判断当前架构。

### 活跃文档（当前依据）

| 文件 | 作用 | 状态 |
|---|---|---|
| [README.md](README.md) | 项目介绍、功能、评分算法、部署（面向使用者） | ✅ 活跃 |
| [FISHSCORE.md](FISHSCORE.md) | 评分算法、因子权重、否决规则（**单一真相源**） | ✅ 活跃 |
| [TIDESPORT_REQUIREMENTS.md](TIDESPORT_REQUIREMENTS.md) | 客户需求基线 SRS | ✅ 活跃 |
| [REQUIREMENTS_REGISTER.md](REQUIREMENTS_REGISTER.md) | 需求变更台账（CHG-xx 编号可追溯） | ✅ 活跃 |
| [PROGRESS_TRACKER.md](PROGRESS_TRACKER.md) | 里程碑进度台账（**可能滞后，以代码为准**） | ✅ 活跃 |
| [HANDOVER_V115.md](HANDOVER_V115.md) | 开发交接：环境红线、基线、并行会话隔离 | ✅ 活跃 |
| [HANDOVER.md](HANDOVER.md) | 早期交接（M5/M6 视角），部分已被 V115 取代 | ⚠️ 部分过时 |
| [DEVELOPMENT.md](DEVELOPMENT.md) | 本文：结构、约定、发版、文档地图 | ✅ 活跃 |
| [tidespot-android/CONTRACT.md](tidespot-android/CONTRACT.md) | Android 模块契约与**冻结点**（改签名前必读） | ✅ 活跃 |
| [web/README.md](web/README.md) | Web 工程约定与迁移期红线 | ✅ 活跃 |
| [design-handdrawn/A-vintage-outdoor/SKIN-DEV-STANDARD.md](design-handdrawn/A-vintage-outdoor/SKIN-DEV-STANDARD.md) | 换肤 / 新增皮肤执行手册 | ✅ 活跃 |

### 专项方案（一次性评估，结论已落地）

| 文件 | 作用 |
|---|---|
| [WEB_PROJECT_PLAN.md](WEB_PROJECT_PLAN.md) | Web 里程碑、环境自适应、跨端适配契约 |
| [GITHUB_PAGES_MIGRATION_PLAN.md](GITHUB_PAGES_MIGRATION_PLAN.md) | 静态化可行性、CORS 实测、部署方案 |
| [WECHAT_MINIPROGRAM_FEASIBILITY.md](WECHAT_MINIPROGRAM_FEASIBILITY.md) | 小程序可行性评估（结论：不推荐） |
| [AMAP_FEASIBILITY.md](AMAP_FEASIBILITY.md) | 高德地图接入评估 |
| [WEB_VS_ANDROID_GAP.md](WEB_VS_ANDROID_GAP.md) | Web/Android 功能差距台账 |
| [DATA_SOURCE_PROBE.md](DATA_SOURCE_PROBE.md) | 数据源连通性实测 |
| [ACCEPTANCE_PRECHECK.md](ACCEPTANCE_PRECHECK.md) | 验收执行单 |
| [PROJECT_PLAN.md](PROJECT_PLAN.md) | 项目排期（M0–M6，已完成） |
| [OPT_REQUIREMENTS_V11.md](OPT_REQUIREMENTS_V11.md) / [OPT_REQUIREMENTS_V115.md](OPT_REQUIREMENTS_V115.md) | 优化需求规格 |

### 历史归档（**不要用于判断当前状态**）

全部在 [docs/archive/](docs/archive/)，见其 [README](docs/archive/README.md) 说明。

- M0–M5 各轮独立验收报告（8 份，2026-11-05/06）
- 已合入 SRS 的历史需求修改稿

---

## 代码阅读路径（新人 / agent 上手）

按这个顺序读，能快速建立心智模型：

### 1. 先懂算法（双端的共同基础）

```
FISHSCORE.md                          九因子 + 乘性否决的设计理由
  → web/src/domain/fishScore.ts       最易读的实现（TS）
  → web/src/domain/bestWindows.ts     最佳窗口聚合
  → tidespot-android/core/domain/…/FishScoreCalculator.kt   同算法 Kotlin 版
```

**为什么要先读 Web 版**：TypeScript 版本带完整 JSDoc，且 `parity.test.ts` 用 Kotlin 导出的黄金值逐字段比对（容差 1e-9）——读它就能确认"两端一致"不是口号。

### 2. 再看数据流（两端同构）

```
web/src/data/repository.ts     TTL 命中 → 缓存回退 → 并行拉取 → 写库 → join → 评分 → 降级
  ↕ 一一对应
tidespot-android/core/data/repo/TideSpotRepository.kt
```

### 3. 最后看 UI

| 端 | 入口 | 备注 |
|---|---|---|
| Web | `web/src/app/router.tsx` → `pages/*.tsx` | hash 路由，页面文件较大但结构清晰 |
| Android | `tidespot-android/app/…/AppNav.kt` → `feature/*/…Screen.kt` | 多模块，Screen 是页面级 Composable |

> ⚠️ 几个文件超过 900 行（`DetailScreen.kt` 1486 行、`FavoritesScreen.kt` 1397 行），
> 阅读时先找 `// ===== =====` 形式的分区注释，不要从头读到尾。

### 4. 改代码前必读

- 改 `:core:*` 公共签名 → 先看 `CONTRACT.md` 的**冻结点**
- 改评分 → 必须同步改两端 + 更新 `FISHSCORE.md`
- 改文案 → 改 Android `strings.xml`，Web 侧由 `gen:i18n` 生成
- 改样式 → 走 `design-handoff/tokens`，不要硬编码颜色

## 发版规则

改 `tidespot-android/app/build.gradle.kts` 的 `versionCode`（每次 **+1**）与 `versionName`：

| 改动性质 | 怎么加 | 例子 |
|---|---|---|
| 小版本：修 bug、文案 / 样式微调，**无功能变化** | 第二个 `.` 之后 +1 | `1.16.0` → `1.16.1` |
| 有功能修改 / 新增 | 第一个 `.` 之后 +1，末位归零 | `1.16.1` → `1.17.0` |
| 重大更新 | 第一位 +1，且**必须经明确认证** | `1.17.0` → `2.0.0` |

**每个发版都必须动版本号**。同名同码的包会让真机验收与回滚失真。

Web 端同步修改 `web/package.json` 与 `web/src/app/container.ts` 的 `APP_VERSION`。

## 提交

jj 与 git colocated：

```bash
jj describe -m "..."
git update-ref refs/heads/main <commit>    # 同步本地分支指针
git push origin main                        # 推到远端
```
