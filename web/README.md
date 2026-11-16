# TideSpot Web · `web/`

> 状态：**M0–M7 完成 + B1–B3 完成 + B4 部分完成**（2026-11-15），版本 **1.18.3**。
> 纯静态 PWA，零服务端；与 `tidespot-android/` 评分逐字段一致（128 项单测 / 8 条 E2E）。
> 主计划：`../WEB_PROJECT_PLAN.md` ｜ 部署方案：`../GITHUB_PAGES_MIGRATION_PLAN.md` ｜ 差距台账：`../WEB_VS_ANDROID_GAP.md`

---

## 1. 定位

纯静态 PWA，零服务端，可直接部署 GitHub Pages / Cloudflare Pages / 国内静态 CDN。
与 Android 功能对齐、评分逐字段一致；同一 URL 自动适配 手机 / 平板 / 桌面 三档布局。

**首版范围**：完整版 65 人日；PC 为三栏布局；Android 平板布局一期不做（`PARITY_GAP-WEB-ADP-01`）。

## 2. 红线（迁移期）

- **禁止修改 `tidespot-android/` 下任何文件**（源码、资源、构建脚本、Gradle 配置）。
- 本目录不得被加入 `tidespot-android/settings.gradle.kts`。
- 发现 Android 侧问题 → 记入 `PARITY_GAP`，不在本工程顺手改。
- 提交前自检：`git status --porcelain -- tidespot-android` 只允许出现 `CONTRACT.md`（跨端契约文档）。

## 3. 双端适配契约（摘要，权威条款见 `../WEB_PROJECT_PLAN.md` §7）

| # | 条款 | 本工程执行 |
|---|---|---|
| C-1 | 新需求默认 `platforms: both` | 需求先登记，再开发 |
| C-2 | `both` 需求两端同批交付；不能同批必须登记 `PARITY_GAP` | 提审前核对台账 |
| C-3 | 单一真相源 | 评分 `FISHSCORE.md`；视觉 `design-handoff/tokens/*`；文案 `tidespot-android/**/res/strings.xml`；URL 常量 `TideSpotUrls.kt` |
| C-4 | 评分/派生逐字段一致 | `src/domain/parity.test.ts` 对拍 Kotlin 输出，容差 `1e-9` |
| C-5 | 文案 key 集合一致 | `npm run check:parity`（CI 必过，当前 580/580） |
| C-6 | 平台差异白名单 | 系统通知 / APK 签名 / SW 与安装 / 快捷键 / 返回键 / safe-area |
| C-7 | 版本号 | **各自独立**：Android `1.21.1` / Web `1.18.3`（README 与设置页如实展示） |
| C-8 | 验收清单双端各一份 | 本文 §8 + Android 真机清单 |

---

## 4. 目录结构

```
web/
├── public/                 # 图标、.nojekyll、maplibre worker 副本
├── src/
│   ├── app/
│   │   ├── router.tsx      # hash 路由：#/map · #/favorites · #/settings · #/spot/:id
│   │   ├── container.ts    # 依赖装配（对应 AppGraph.kt）
│   │   ├── environment/    # ADP：detect / EnvironmentProvider
│   │   ├── shell/          # AppShell + shell.css（含详情吸顶、收藏分栏样式）
│   │   └── pages/          # MapPage / MapSearch / MapFavorites / Top10Panel
│   │                       # DetailPage / DetailTabs / DesktopSpotLayout / FavoritesPage
│   ├── domain/             # 评分内核平移 + bestWindows + favorites（纯函数，可单测）
│   ├── data/               # client / dto / ttl / gate / bucket / retry / seriesJoin
│   │                       # db（Dexie）/ repository / geo / geocoding
│   ├── ui/
│   │   ├── tokens/         # bands.ts（五档 + 徽标，构建期生成）
│   │   ├── primitives/     # Card / Chip / Badge / ScoreCard / DaySelector / Sheet / Table
│   │   ├── charts/         # HourChart（柱状 Canvas 2D）+ hourRows + LineChart
│   │   ├── i18n.ts         # 全局语言单例 + 订阅
│   │   ├── skin.ts         # 皮肤注册表（SKIN-1）
│   │   └── settings.ts     # 底图提供方等本地设置
│   ├── styles/             # tokens.generated.css / skins.css
│   ├── i18n/{zh,en}.json   # 由 strings.xml 生成（只读源）
│   └── main.tsx            # SW 注册（onNeedRefresh → 自动接管重载）
├── e2e/                    # Playwright 冒烟（mobile + desktop 各 4 条）
├── test/fixtures/          # Kotlin 对拍数据 + Open-Meteo 样本
└── scripts/                # gen-tokens / gen-i18n / check_parity / check_contrast
                            # export-fixtures / check-budget / make-fixtures.ps1
```

## 5. 环境自适应（ADP）

| 档位 | 断点 | 布局 |
|---|---|---|
| mobile | `<768px` | 全屏地图 + 底部 Tab + BottomSheet（与 APP 同构） |
| tablet | `768–1199px` | 顶栏 + 可折叠侧栏 |
| desktop | `≥1200px` | 主导航 + 列表 / 地图 + 详情分栏（收藏页：列表 380 + 详情 `1fr`） |

- 判定优先级：**手动选择 > 视口宽度 > 交互能力（hover / pointer）**；UA 仅日志。
- 切换档位不重建页面；`#/spot/:id`、`#/favorites` 等以 URL 为唯一真相。
- 切档后调用 `map.resize()`，避免地图空白。

## 6. 常用命令

| 命令 | 作用 |
|---|---|
| `npm ci` | 安装依赖（CI 必用） |
| `npm run dev` | 本地开发（5173） |
| `npm run build` | 产物到 `dist/`（含 PWA：`sw.js` + manifest） |
| `npm run preview` | 预览生产包（4173） |
| `npm test` | Vitest：**128 passed / 2 skipped** |
| `npm run test:e2e` | Playwright：**8 passed**（需先 `npx playwright install chromium`） |
| `npm run check:parity` | 文案 key 与 Android 一致（**580/580**） |
| `npm run check:contrast` | 配色对比度（**21 组 0 fail**） |
| `npm run check:budget` | gzip 体积门禁（总 **458.9 KB**，主包 132.1 KB，0 项超） |
| `npm run gen:i18n` | 只读 Android `strings.xml` → `src/i18n/*.json` |
| `npm run gen:tokens` | `design-handoff/tokens/design-tokens.json` → CSS + `bands.ts` |
| `npm run export:fixtures` | 只读 Kotlin 单测 → 对拍候选 |

## 7. 已完成 / 待完成

**已完成（M0–M7）**

- 骨架 + 三档布局 + hash 路由 + 依赖装配
- 评分内核平移：`diff = 0` 对拍（24 条）；最佳窗口 / Top10
- 网络层：ttl / 限流 / 重试 / DTO / 逐时 join；真实源打通（408h 天气 / 264h 海洋）
- IndexedDB（Dexie）7 表 + Repository（缓存优先、降级标记、48h 不可用）
- token / 皮肤（SKIN-1）/ 组件 / 图表 / 双语
- 桌面三栏、地图（MapLibre GL JS + 三样式 + 海温）、跨点 Top10
- PWA（precache app shell，API 不进 SW）、预算门禁、CI 部署

**已完成（B1–B3）**

- B1 地图闭环：地名搜索、收藏星标图层、信息浮条操作、Top10 自定义时段
- B2 详情与收藏：图表 ↔ 数值表、收藏四序 / 搜索 / 分组 / 标签 / 批量 / 刷新全部、状态条（离线 / 48h / 降级 / 缓存回退）、水体类型回写
- B3 设置与 i18n：语言切换（全局订阅）、底图提供方（OpenFreeMap / CARTO）、隐私披露 / 开源许可 / 免责、清硬编码

**B4 部分完成**：水质三卡（水温 / 溶氧 / 浑浊度）
**B4 待做**：ΔV 原始输入清单（16 行）、安全摘要卡、潮汐相位与现场校潮、观测录入
**B5 待做**：收藏缩略图、地图皮肤配色、字体缩放、数据管理、骨架 / 空态

## 8. 与 App 的视觉 / 交互对齐点

| 项 | App 依据 | Web 实现 |
|---|---|---|
| 逐时指数图形 | `HourChart(mode = BAR)`：固定 0–100、五档柱色、≥80 顶部「窗口」条 | 同构（Canvas 2D） |
| 评分卡五档 | `DetailScreen`：真实档位色 + 5→1 图标 + 选中档 2dp 描边 | 同构（`SCORE_BANDS` + `is-active`） |
| 详情吸顶 | App 中标题 / 日期 / Tab 连续层级 | **Web 增强**：三者合并吸顶（标题 → 日期 → 来源 → Tab） |
| 收藏弹窗 | `ts_favorite_dialog_title`：名称可编辑、留空用坐标、显示坐标与水体 | 同构（BottomSheet） |
| 收藏卡片 | 点卡片任意处进详情 | 同构（按钮命中除外；键盘可达） |
| 返回地图定位 | UX-18：回到 lastViewed 最新点 | 同构（`load` 后读收藏首项 `jumpTo`） |
| 桌面收藏 | — | 列表 380 + 右栏内嵌详情，不跳路由 |

## 9. 已知限制

- **数据源可达性**：`api.open-meteo.com` 在部分网络下很慢或不可达；详情页会在 15s 预算后明确降级并提供「重试」，设置 → 诊断 可直接看 HTTP 状态。
- **GitHub Pages 国内访问**：可能较慢，需要换网络或自定义域名。
- **Copernicus**：Web 未接 OIDC 与数据链路，设置中不提供凭证入口（与 App 的差异已登记）。
- **搜索覆盖**：正地理与 App 同源（Open-Meteo Geocoding），中国小地名覆盖有限。
- **收藏摘要**：依赖本地 `hourly_score` 缓存，首次需先进详情或点「刷新全部」。