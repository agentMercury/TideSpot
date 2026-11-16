# 工程结构与开发约定（TideSpot）

> 本文承接原 README 中面向开发者的部分。面向使用者的项目介绍见 [README.md](../README.md)。

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

## 文档索引

| 文件 | 作用 |
|---|---|
| `FISHSCORE.md` | 评分算法、因子权重、否决规则（单一真相源） |
| `WEB_PROJECT_PLAN.md` | Web 里程碑、环境自适应、跨端适配契约 |
| `GITHUB_PAGES_MIGRATION_PLAN.md` | 静态化可行性、CORS 实测、部署方案 |
| `tidespot-android/CONTRACT.md` | Android 模块契约与冻结点 |
| `design-handdrawn/A-vintage-outdoor/SKIN-DEV-STANDARD.md` | 换肤 / 新增皮肤执行手册 |
| `web/README.md` | Web 工程约定与迁移期红线 |
| `HANDOVER_V115.md` | 开发交接：环境、基线、红线、并行会话隔离 |

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
