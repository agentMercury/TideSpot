# GitHub Pages（GitHub.io）迁移与部署方案 · GITHUB_PAGES_MIGRATION_PLAN

> 版本 v1.0 ｜ 日期 2026-11-13 ｜ 性质：**方案（不改代码、不建工程）**
> 基线：`tidespot-android` v1.18.1 / `versionCode=23`（main 15,589 行 Kotlin）
> 前置评估：`WECHAT_MINIPROGRAM_FEASIBILITY.md`（结论：小程序需代理 + 降级，不推荐）
> 本文所有「实测」均为 2026-11-13 在本机 `curl.exe` 对生产端点发起的真实请求

---

## 0. 结论（TL;DR）

**可行，且是当前所有目标里唯一「零服务端 + 功能 100%」的形态。** 纯静态 PWA 直连公开 API，GitHub Pages 只负责托管构建产物。

| 关键项 | 实测结果 | 判定 |
|---|---|---|
| Open-Meteo Weather / Marine / Geocoding | `access-control-allow-origin: *`；`X-RateLimit-Limit: 600 / 60s` | 浏览器可直连 |
| Photon 逆地理 | `Access-Control-Allow-Origin: *` | 可直连 |
| BigDataCloud 逆地理 | `access-control-allow-origin: *` | 可直连 |
| OpenFreeMap 样式（MapLibre style JSON） | `access-control-allow-origin: *` | 三套底图可保留 |
| OSTIA WMTS（`GetCapabilities` + `GetFeatureInfo`） | 均 200 且 `access-control-allow-origin: *`；本次 `GetFeatureInfo` 返回 FeatureCollection（此前记录的 500 已恢复） | 海温取数与叠加可保留 |
| Copernicus OIDC token（OPTIONS 预检） | 200，回显 `Origin`，允许 `POST` + `Content-Type` | 凭证模式可保留 |
| MapLibre GL JS | 最新 **v6.18.0**（2026-11-11，BSD-3） | Android 同源生态 |
| GitHub Pages 限额 | 站点 ≤1GB；带宽软限 100GB/月；构建 10 次/小时（**自定义 Actions 不受此限**）；部署超时 10 分钟 | 完全够用 |

**代价只有三件事**：① 代码要重写 UI（约 9,000 行 Compose → TypeScript）；② 国内访问 `github.io` 不稳定，正式交付建议同产物另部署到国内静态托管（需备案）；③ 若商业化，Open-Meteo 免费层（仅非商业）+ GitHub Pages 条款（不得以商业 SaaS 为主）都要换。

**工时：完整范围 47–50 人日（≈10 周/单人，AI 辅助 7–8 周）；MVP（地图 + 指数 + 离线）28 人日（5–6 周）。**

---

## 1. 平台边界（必须先接受）

| # | 边界 | 说明 | 对策 |
|---|---|---|---|
| B-1 | 纯静态，**无服务端/无函数** | 不能藏密钥、不能做服务端缓存 | 本项目主流程**无 Key**，天然适配；Copernicus 凭证仍由用户自填 |
| B-2 | 站点 ≤1GB、带宽软限 100GB/月 | 单页应用产物 <2MB，瓦片走第三方 CDN | 无需处理 |
| B-3 | 无 SPA history 回退 | 刷新 `/spot/1` 会 404 | 用 **hash 路由**（`#/spot/1`）最稳；或 `404.html` 回退（不推荐） |
| B-4 | HTML 缓存头不可控（约 600s） | 发版后可能短暂旧页面 | 静态资源用内容 hash 文件名 + `immutable`；HTML 短缓存 |
| B-5 | 国内访问 `github.io` 不稳定 | 客户在中国大陆，这是**最大现实风险** | 同一份 `dist/` 同步部署 Cloudflare Pages / 国内 OSS+CDN（需备案域名） |
| B-6 | 条款禁止「以商业交易/SaaS 为主的站点」 | 免费工具可用；商业化需换托管 | 商用改自有域名静态托管 |
| B-7 | Open-Meteo 免费层**仅非商业**（600/min、5,000/h、10,000/天、30 万/月） | 订阅后走 `customer-api.open-meteo.com` + key，语法不变 | 上线前确认是否商用；商用则订阅并把域名常量换掉（一处配置） |
| B-8 | 数据出境 | 浏览器直连境外 API，用户坐标出境 | 隐私声明；或默认只发「网格化坐标」（精度 ~0.05°，评分影响可评估） |

---

## 2. 目标架构

```
┌────────────────────────── 静态站点（GitHub Pages / CDN） ──────────────────────────┐
│ index.html + app.[hash].js + assets（图标/字体/效果图） │
│ Service Worker（app shell 预缓存 + 运行时分策略） │
│ IndexedDB（Dexie）：spots / hourly_metric / hourly_score / fetch_log / manual_* │
└───────────────┬──────────────────────────────────────────────────────────────────────┘
 │ fetch（CORS 已实测）
 ┌───────────┼──────────────┬────────────────────┬─────────────────────┐
 ▼ ▼ ▼ ▼ ▼
Open-Meteo Photon / OpenFreeMap / OSTIA WMTS Copernicus OIDC
Weather+ BigDataCloud CARTO style GetTile/GetFeatureInfo （仅水质增强，可选）
Marine+Geo （逆地理） （底图三样式） （海温点值/叠加）
```

**与 Android 的架构同构**：`domain`（纯计算）→ `data`（拉取 + 缓存 + 降级）→ `ui`（页面/组件）。只是把 Room 换成 IndexedDB、OkHttp 换成 `fetch`、MapLibre Android 换成 MapLibre GL JS。

---

## 3. 技术选型

| 层 | 选型 | 理由 |
|---|---|---|
| 构建 | **Vite 6+ / TypeScript 5.x** | 产物小、hash 命名、GitHub Pages 友好 |
| UI | **React 19 + Tailwind**（或 Svelte 5） | 团队通用性最好；`design-tokens.json` → Tailwind theme 一次映射 |
| 路由 | **hash 路由**（`react-router` 的 `createHashRouter`） | 规避 B-3，零 404 配置 |
| 地图 | **maplibre-gl v6** | 与 Android 同一份 style/瓦片源；`moveend`/`unproject` 语义对齐 |
| 数据获取 | **TanStack Query** | `staleTime` 天然映射 TTL（天气 3h/海洋 6h），`retry`/`backoff` 映射 `RetryExecutor` |
| 本地库 | **Dexie（IndexedDB）** | 复合索引可对齐 Room 的 `[spotId+time]`；配额远大于 10MB |
| 离线 | **Workbox**（`vite-plugin-pwa`） | app shell 预缓存；API 不双写（交给 IndexedDB） |
| 图表 | **手写 Canvas 2D + `ChartMath` 公式**，或 uPlot | 避免重库；`HourChart.kt` 的坐标/刻度逻辑可直接移植 |
| i18n | **i18next** + `i18n/{zh,en}.json` | 572 key × 2 语一次性迁移 |
| 测试 | **Vitest**（单元/对拍）+ **Playwright**（E2E/离线/视觉） | 用现有 5,742 行 domain/data 用例做「输入→输出」对拍 |
| 质量门禁 | 复用 `.tools/i18n_scan.mjs`；`contrast_check.mjs` 改选择器后复用 | 已有脚本，改扫描根目录即可 |

**备选（不推荐）**：Compose Multiplatform `wasmJs` 复用 Compose。可省 UI 重写，但要换资源系统/ViewModel/SQLDelight，且地图需在 Compose 内嵌 DOM 跑 MapLibre GL JS（JS interop 风险高），Wasm 首包 2–5MB gzip。**只在「必须保持 Kotlin 单一代码库」时选它。**

**domain 怎么复用**：推荐**手写 TS 平移 + 对拍**，而不是 Kotlin/JS 跨编译。

- 手写：1,707 行、结构清晰（`FishScoreCalculator`/`SubScores`/`VetoEvaluator`/`Solunar`/`TideCalibrator`/`BestWindowFinder`/`Top10TimeFilter`/`WaterQualityProxy`），AI 辅助 5–6 人日；避免 Kotlin/JS 产物（300–500KB）与双工具链；
- 对拍：把现有 2,280 行 domain 单测的 `(输入, 期望输出)` 导成 JSON fixture，TS 端跑同一批，**逐字段 diff 必须为 0**；
- `java.time` → `Temporal`/`date-fns-tz`；`String.format(Locale.US)` → `toFixed()`（仅约 30 处）。

---

## 4. 模块映射（Android → Web）

| Android | Web 目标 | 处理方式 | 复用度 |
|---|---|---|---|
| `core/domain/*`（18 文件） | `src/domain/*` | 手写 TS 1:1，公式不改 | 语义 100% |
| `core/data/repo/Mappers.kt` | `src/data/mappers.ts` | 逐字段照搬（344 行） | 85% |
| `core/data/repo/SeriesJoiner.kt` | `seriesJoin.ts` | 按时戳并集切片，逻辑照搬 | 95% |
| `core/data/repo/Ttl.kt` / `MinRefreshGate.kt` / `TokenBucket.kt` / `RetryExecutor.kt` | `ttl.ts` / `refreshGate.ts` / `tokenBucket.ts` / `retry.ts` | 纯逻辑，直接翻译 | 95% |
| `core/data/remote/TideSpotUrls.kt` | `urls.ts` | 常量 + `URLSearchParams`；**新增 OSTIA GetTile 模板** | 90% |
| `OpenMeteoClient` / `ReverseGeoClient` / `GeocodingClient` | `clients/*.ts` | OkHttp → `fetch` + AbortSignal；错误码分类照搬 | 60% |
| `local/Entities.kt` + `Daos.kt` + `TideSpotDatabase.kt` | `db/schema.ts`（Dexie） | 表/索引照抄，去掉 Room 注解 | 0%（schema 100% 照抄） |
| `water/CopernicusAuthClient` / `CredentialStore` | `clients/copernicus.ts` + `settings` | token 存 `sessionStorage`（非 localStorage），300s 过期 | 70% |
| `water/OstiaLakeProbe` | `clients/ostia.ts` | 点值查询照搬，`GetFeatureInfo` | 90% |
| `core/ui/*`（22 文件） | `src/ui/*` | 组件重写；`ChartMath`/`Format`/`SpotZones`/`RelativeTimeFormatter` 直接翻译 | 组件 0%，纯函数 90% |
| `feature/map/CrosshairStateMachine` / `InfoBarState` / `PermissionStateMachine` | `map/state/*.ts` | 状态机照搬（有 26 个纯 Kotlin 测试可对拍） | 90% |
| `feature/detail/*Derivations` / `Top10Formatter` / `UserRefreshGate` | `detail/derive/*.ts` | 派生逻辑照搬 | 85% |
| `feature/favorites/*Logic` | `favorites/logic/*.ts` | 排序/分组/过期判定照搬 | 90% |
| `feature/settings/SettingsState` + `SettingsStore` | `settings/store.ts` | `localStorage` 实现同接口 | 80% |
| `app/AppNav.kt` / `AppGraph.kt` / `MainActivity.kt` | `app/router.tsx` / `app/container.ts` | 手工装配，替换 `MainActivity` | 0% |
| `res/values{,-en}/strings.xml`（572 key） | `i18n/{zh,en}.json` | 脚本转换 + 人工校对 | 100% |
| `.tools/i18n_scan.mjs` | CI 直接调用 | 改扫描根目录 | 100% |

---

## 5. 数据层设计

### 5.1 IndexedDB（Dexie）schema

| 表 | 主键 | 索引 | 对应 Room |
|---|---|---|---|
| `spots` | `id` | `name`, `waterBody`, `createdAt` | `SpotEntity` |
| `hourly_metric` | `[spotId+time]` | `spotId`, `time`, `fetchedAt` | `HourlyMetricEntity` |
| `hourly_score` | `[spotId+time]` | `spotId`, `time` | `HourlyScoreEntity` |
| `fetch_log` | `++id` | `spotId`, `ts`, `source` | `FetchLogEntity` |
| `manual_observation` | `++id` | `spotId`, `ts` | `ManualObservationEntity` |
| `manual_tide_fit` | `++id` | `spotId` | `ManualTideFitEntity` |
| `meta` | `key` | — | Room `version` / 设置迁移 |

> 版本迁移：`db.version(1)` 起，后续用 Dexie `upgrade()`；不继承 Room 的 3→6 迁移脚本，但迁移语义要对照 `MIGRATIONS` 文档确认（历史数据在 Android 端，Web 端全新库，无需导入）。

### 5.2 缓存与降级语义（必须与 Android 一致）

| 语义 | Android | Web |
|---|---|---|
| 天气 TTL 3h / 海洋 6h | `Ttl.kt` | TanStack Query `staleTime` + 自建 `ttl.ts` 双保险 |
| 单点最小刷新 15min | `MinRefreshGate` | 同逻辑，存 `meta` |
| 限流（≤4 并发、≤1 req/s 逆地理） | `TokenBucket` | 同逻辑 + `fetch` 并发池 |
| 重试/退避 | `RetryExecutor` | 同逻辑；`AbortSignal.timeout()` |
| 失败保留旧数据 + 降级徽标 | `servedFromCache` / `weatherDegraded` | 同字段名，UI 文案不变 |
| `fetch_log` 可观测 | Room 表 | IndexedDB 表 + 设置页导出 JSON |

### 5.3 网络层要点

- 统一 `User-Agent` 无法设置（浏览器禁改）→ 服务端统计口径变化可接受；
- `fetch` 默认 `no-cors` 不可用，必须走 CORS（已实测全通过）；
- 所有请求带 `AbortController`（切点/切页取消），对齐 `CancellationException`；
- 错误分类 `DataError`（网络/超时/4xx/5xx/解析/全 null）逐类照搬，UI 文案不改。

---

## 6. 地图页（S01）设计

### 6.1 能力对照

| Android | Web（MapLibre GL JS v6） | 工作量 |
|---|---|---|
| `MapView` + `MapLibreMap` 相机 | `new maplibregl.Map({style, center, zoom})` | 小 |
| 三套样式（color/plain/dark） | 复用 `TideSpotUrls` 的 6 个 style URL，`map.setStyle()` 切换 | 小 |
| 十字准星（相机中心） | `map.on('moveend')` → `map.getCenter()`；准星用绝对定位 DOM | 小 |
| 长按选点 | `mousedown/touchstart` 计时 600ms → `map.unproject([x,y])` | 小 |
| Top10 横滑卡 | DOM overlay + `scroll-snap`（比 Android 更简单） | 小 |
| 信息浮条 / 来源行 | DOM overlay，复用现有文案与数据 | 小 |
| 定位（Fused/LocationManager 双通道） | `navigator.geolocation` + `Permissions.query`，`PermissionStateMachine` 照搬 | 中 |
| OSTIA 海温点值 | `fetch(GetFeatureInfo)`（已实测 200 + CORS） | 中 |
| OSTIA 叠加层 | `addSource({type:'raster', tiles:[GetTile 模板], tileSize:256})` + `raster-opacity` | 中 |
| 底图署名 | MapLibre `AttributionControl`（OpenFreeMap/OSM） | 小 |
| 离线底图 | 可选：PMTiles 单文件 + `pmtiles` protocol（MVP 不做） | 大 |

### 6.2 关键实现片段

```ts
// 底图三样式（与 Android 同一常量）
const STYLES = {
 color: 'https://tiles.openfreemap.org/styles/liberty',
 plain: 'https://tiles.openfreemap.org/styles/positron',
 dark: 'https://tiles.openfreemap.org/styles/dark',
} as const;

// 海温叠加：WMTS 模板 → MapLibre raster source（注意 256px）
map.addSource('ostia', {
 type: 'raster',
 tiles: [`${WMTS_BASE}/teroWmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0` +
 `&LAYER=${encodeURIComponent(OSTIA_LAYER)}&STYLE=default&FORMAT=image/png` +
 `&TILEMATRIXSET=EPSG:3857&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&TIME=${latestDate}`],
 tileSize: 256, // 必须显式声明，否则按 512 请求
});
map.addLayer({ id: 'ostia-layer', type: 'raster', source: 'ostia', paint: { 'raster-opacity': 0.55 } });
```

> 注意：`TIME` 取最近可用日值（OSTIA 为 L4 NRT 日产品）；若某日瓦片为空需回退上一日，逻辑放在 `clients/ostia.ts`，与 `OstiaLakeProbe` 的降级一致。

---

## 7. PWA 与离线

| 项 | 策略 |
|---|---|
| App shell | `vite-plugin-pwa` precache（html/js/css/字体/图标），`navigateFallback` 指向 `index.html`（配合 hash 路由） |
| API 响应 | **不进 Service Worker**，统一写 IndexedDB（避免双写与配额浪费）；SW 仅做 `NetworkOnly` 透传 |
| 底图瓦片 | 可选 `CacheFirst`，上限 500 条、30 天过期（可关闭，默认不开，避免配额被瓦片吃掉） |
| 离线可用范围 | 最近访问的 N 个点（默认 3）详情 + 收藏列表 + 评分；超出提示「需联网」 |
| 配额管理 | `navigator.storage.estimate()` 展示占用；`hourly_metric` 按 `[spotId+time]` 淘汰最旧 |
| iOS 限制 | Safari 对 script-writable storage 有 7 天清理策略 → 引导「添加到主屏幕」；`geolocation` 必须用户手势触发 |
| 离线指示 | 复用 `OfflineBanner` 文案与触发条件（`navigator.onLine` + 请求失败） |

---

## 8. 部署（GitHub Pages）与 CI

### 8.1 仓库结构（建议独立目录，不动 Android 主线）

```
weatherfishcheck/
├── tidespot-android/ # 现状，保持不动
└── web/ # 新增
 ├── package.json
 ├── vite.config.ts # base 由环境变量注入
 ├── public/ # 图标、CNAME（可选）
 ├── src/{domain,data,ui,app}
 └── test/fixtures/ # 从 Kotlin 单测导出的对拍数据
```

### 8.2 Vite 关键配置

```ts
// vite.config.ts
export default defineConfig(({ mode }) => ({
 // 项目站点：https://<user>.github.io/<repo>/ → base 必须是 '/<repo>/'
 // 自定义域名或用户站点：base = '/'
 base: process.env.PAGES_BASE ?? '/',
 build: { target: 'es2022', sourcemap: mode !== 'production' },
 plugins: [react(), VitePWA({ registerType: 'autoUpdate', workbox: { navigateFallback: 'index.html' } })],
}));
```

### 8.3 GitHub Actions 工作流

```yaml
name: deploy-web
on:
 push: { branches: [main], paths: ['web/**', '.github/workflows/deploy-web.yml'] }
 workflow_dispatch:
permissions:
 contents: read
 pages: write
 id-token: write
concurrency: { group: pages, cancel-in-progress: true }
jobs:
 build:
 runs-on: ubuntu-latest
 steps:
 - uses: actions/checkout@v4
 - uses: actions/setup-node@v4
 with: { node-version: 22, cache: npm, cache-dependency-path: web/package-lock.json }
 - run: npm ci
 working-directory: web
 - run: npm run lint && npm run test && npm run build
 working-directory: web
 env: { PAGES_BASE: '/weatherfishcheck/' }
 - uses: actions/configure-pages@v5
 - uses: actions/upload-pages-artifact@v3
 with: { path: web/dist }
 deploy:
 needs: build
 runs-on: ubuntu-latest
 environment: github-pages
 steps:
 - uses: actions/deploy-pages@v4
```

> 用自定义 Actions 工作流部署，**不受 10 次/小时构建限制**；单次部署需在 10 分钟内完成（Vite 构建通常 <2 分钟）。

### 8.4 上线检查清单

| # | 项 | 标准 |
|---|---|---|
| 1 | `base` 与访问路径一致 | 刷新子路径 404 数为 0（hash 路由） |
| 2 | `.nojekyll` | 产物目录含该文件，避免下划线目录被忽略 |
| 3 | 资源 hash + immutable | `index.html` 短缓存、`assets/*` 长缓存 |
| 4 | 自定义域名（可选） | `public/CNAME` + DNS CNAME → `<user>.github.io` |
| 5 | HTTPS | Pages 默认强制，定位 API 必需 |
| 6 | 国内可达性 | 从大陆网络实测 `github.io`；必要时同产物发 Cloudflare Pages / 国内 CDN |
| 7 | 门禁 | `i18n_scan` FAIL=0、`contrast_check` 0 FAIL、Playwright 全绿 |

### 8.5 国内访问的现实处理

| 方案 | 是否需备案 | 说明 |
|---|---|---|
| GitHub Pages 直连 | 否 | 大陆访问不稳，仅适合验收/演示 |
| **Cloudflare Pages** | 否 | 同一 `dist/`，`npx wrangler pages deploy dist`；大陆可达性一般优于 github.io |
| 国内 OSS/COS + CDN | **是** | 正式商用推荐；域名备案 7–20 天 |
| 自有服务器 Nginx | 是 | 违背「无服务端」初衷，仅静态文件亦可 |

---

## 9. 分阶段计划（建议）

| 阶段 | 周期 | 内容 | 验收门槛 |
|---|---|---|---|
| **M0 Spike** | 3 人日 | Vite 骨架 + Pages 部署打通；domain 对拍框架；MapLibre 复刻 S01（准星/长按/Top10/三样式） | 真机 360dp 下准星偏差 ≤1px；Actions 部署成功 |
| **M1 Domain** | 6 人日 | 1,707 行平移 TS；`java.time`→`Temporal`；30 处格式化 | 2,280 行 Kotlin 用例对拍 **diff=0** |
| **M2 Data** | 9 人日 | `fetch` 客户端、Dexie schema、TTL/门控/限流/重试、降级字段 | 断网 + 5xx + 全 null 三类降级与 Android 一致 |
| **M3 Design** | 7 人日 | `design-tokens.json` → CSS 变量/Tailwind；组件库（卡片/胶囊/徽标/分段 Tab/免责条） | 对比度门禁 0 FAIL；深浅两套截图对齐 |
| **M4 Pages** | 12 人日 | 指数 / 因子 / 收藏 / 设置（含 3 处 Canvas 图表） | 逐屏对照 9 屏效果图 |
| **M5 Map** | 8 人日 | S01 完整（含 OSTIA 点值与叠加、定位、收藏星标） | 与 Android 同点同视野截图对比 |
| **M6 Release** | 5 人日 | PWA、E2E、性能、门禁、部署脚本、验收报告 | §11 全部通过 |
| **合计** | **50 人日** | — | **≈10 周（单人）/ 7–8 周（AI 辅助）** |

**可裁剪**：去掉 M5 的 OSTIA 叠加与 M4 的设置页高级项 → **35 人日（7 周）**；再砍收藏页 → **28 人日 MVP（5–6 周）**。

---

## 10. 资产复用率（相对 Android main 15,589 行）

| 资产 | 规模 | Web 复用 | 说明 |
|---|---:|---|---|
| `core:domain` 评分内核 | 1,707 行 | **语义 100%**（重写为 TS） | 公式/阈值/口径不改；`FISHSCORE.md` 为唯一口径 |
| `core:data` 纯逻辑 | ≈ 2,200 行 | **70%** | URL/解析/join/TTL/节流/重试/错误分类 |
| Room 层 | 455 行 | 0%（schema 照抄） | 换 Dexie |
| UI（Compose + Canvas） | ≈ 9,000 行 | 0%（重写） | 但 `ChartMath`/`Format`/`SpotZones` ≈ 300 行可平移 |
| 地图状态机 | ≈ 500 行 | **90%** | 纯状态机，有单测对拍 |
| `:app` 壳 | 986 行 | 0% | router + DI 重写 |
| **文案** | 572 key × 2 语 | **100%** | 脚本转 JSON |
| **设计资产** | tokens/组件映射/9 屏图/图标 SVG | **100%** | token 直接生成 CSS 变量 |
| **黄金用例** | 5,742 行单测 | **100%**（对拍集） | 跨端一致性核心保障 |
| **门禁脚本** | 2 个 `.mjs` | **100%**（contrast 换选择器） | CI 直接跑 |
| **合计** | — | **代码 ≈35%，资产 ≈95%** | 与小程序 D1（代码 25%）相比更高，且无降级 |

---

## 11. 验收标准（DoD）

| # | 项 | 门槛 |
|---|---|---|
| 1 | 评分一致性 | 30 个点 × 240h，与 Android 输出逐字段 `diff = 0`（分数/五维/否决码/置信/最佳窗口/Top10） |
| 2 | 降级一致性 | 网络失败、5xx、marine 全 null 三类场景，字段与徽标文案同 Android |
| 3 | 文案 | `i18n_scan.mjs` FAIL=0；`values`/`values-en` 572/572 全覆盖 |
| 4 | 视觉 | `contrast_check.mjs` 0 FAIL；深浅两套与效果图逐屏对照 |
| 5 | 性能 | 首屏 JS ≤350KB gzip（地图懒加载）；LCP ≤2.5s（4G）；Lighthouse PWA ≥90 |
| 6 | 离线 | 断网可打开最近 3 个点详情与评分；超范围有明确提示 |
| 7 | 兼容 | Chrome/Safari/Firefox 最近 2 版；iOS 16+；Android Chrome 低端机（WebGL1 回退） |
| 8 | 部署 | Pages 刷新 404=0；HTTPS；资源命中缓存；Actions 构建 ≤10 分钟 |

---

## 12. 风险与对策

| # | 风险 | 等级 | 对策 |
|---|---|---|---|
| R-1 | **商用许可**：Open-Meteo 免费层仅非商业；GitHub Pages 禁止商业 SaaS 为主 | **高** | 上线前确认商业模式；商用 → 订阅 Open-Meteo（换 `customer-api` 域名 + key 一处配置）+ 自有域名托管 |
| R-2 | **大陆访问 github.io 不稳** | **高** | 同产物双发 Cloudflare Pages / 国内 CDN；验收用大陆网络实测 |
| R-3 | 评分两端漂移 | 中 | M1 对拍门槛（diff=0）前置；每次发版跑 fixture |
| R-4 | iOS 存储 7 天清理、定位需手势 | 中 | 引导「添加到主屏幕」；定位按钮由用户点击触发 |
| R-5 | 低端机 WebGL 性能 | 中 | 降级：关闭 OSTIA 叠加、降低 `maxzoom`、`preserveDrawingBuffer:false` |
| R-6 | Copernicus token 存浏览器（XSS 风险） | 中 | 仅 `sessionStorage`、300s 短期、CSP、禁止 `innerHTML`；不预置任何凭证 |
| R-7 | 无服务端 → 无共享缓存，限流按 IP | 中 | `TokenBucket` + 客户端缓存；同网多人使用时提示「请求受限，稍后再试」 |
| R-8 | 数据出境 | 中 | 隐私声明；可选「网格化坐标」（0.05°）降低精度敏感度 |
| R-9 | 双端并行导致 Android 主线回归 | 中 | `web/` 独立目录 + 独立 CI；只共享 `FISHSCORE.md` 与 fixture，不共享 UI 代码 |

---

## 13. 决策点（需要客户确认）

| # | 问题 | 影响 |
|---|---|---|
| Q1 | 是否**商用**（订阅/广告/企业内部使用）？ | 决定 Open-Meteo 是否订阅、是否能用 GitHub Pages |
| Q2 | 正式域名与托管：`github.io` 演示 / Cloudflare Pages / 国内备案 CDN？ | 决定大陆可达性与上线时间（备案 7–20 天） |
| Q3 | 是否保留 **OSTIA 海温叠加 + Copernicus 水质**？ | 保留 → +8 人日；去掉 → M5 减半 |
| Q4 | 首版范围：4 页完整 / 3 页 / MVP（地图+指数）？ | 50 / 35 / 28 人日 |
| Q5 | 是否接受 **hash 路由**（URL 带 `#`）？ | 否则需 `404.html` 回退或换托管（Cloudflare Pages 支持 SPA） |

---

## 14. 与小程序方案的一句话对比

| | GitHub Pages / PWA | 小程序 D1 |
|---|---|---|
| 服务端 | **不需要** | 必须云函数 + 备案域名 |
| 功能对齐 | **100%** | 80–85% |
| 工时 | **28–50 人日** | 52 人日（含降级） |
| 大陆可达 | 需换托管 | 天然可用 |
| 上线门槛 | 静态托管即可 | 备案 + 类目 + 定位申请 + 审核 |

> 结论：**先做 PWA（GitHub Pages 验证 + 国内 CDN 交付），小程序只在「必须有微信入口」时作为二期壳（web-view，仅企业主体）。**

---

## 15. 附：实测记录（2026-11-13）

| 端点 | HTTP | CORS | 备注 |
|---|---|---|---|
| `api.open-meteo.com/v1/forecast` | 200 | `*` | `X-RateLimit-Limit: 600/60s` |
| `marine-api.open-meteo.com/v1/marine` | 200 | `*` | |
| `geocoding-api.open-meteo.com/v1/search` | 200 | `*` | `cache-control: max-age=86400` |
| `photon.komoot.io/reverse` | 200 | `*` | |
| `api.bigdatacloud.net/data/reverse-geocode-client` | 200 | `*` | |
| `tiles.openfreemap.org/styles/liberty` | 200 | `*` | 另有 positron / dark |
| `wmts.marine.copernicus.eu` GetCapabilities | 200 | `*` | |
| `wmts.marine.copernicus.eu` GetFeatureInfo（z=7, 青岛） | 200 | `*` | 返回 `FeatureCollection`（此前 500 已恢复） |
| `auth.marine.copernicus.eu` token（OPTIONS） | 200 | 回显 Origin，允许 POST | 300s token |

> 测试命令：`curl.exe -s -o NUL -D - -H "Origin: https://demo.github.io" <url>`；仅代表当时网络环境，上线前应在目标网络复测。