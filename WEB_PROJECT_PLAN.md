# Web 版项目计划 · WEB_PROJECT_PLAN

> 版本 v1.2 ｜ 日期 2026-11-13 ｜ 性质：**项目计划（不含源码实现）**
> **当前状态：M7 已完成（待人工验收，2026-11-13）**——O-1 非商业、O-2 GitHub Pages 演示；M0–M7 验收见 §13.4–§13.15。
> 上游方案：`GITHUB_PAGES_MIGRATION_PLAN.md`（架构/选型/部署已定）
> 关联评估：`WECHAT_MINIPROGRAM_FEASIBILITY.md`（小程序不作一期）
> 基线：`tidespot-android` v1.18.1 / `versionCode=23`，main 15,589 行 Kotlin
> **硬约束：本计划执行期间，`tidespot-android/**` 下的源码、构建脚本、资源文件零改动。**

---

## 0. 目标与范围

### 0.1 一期目标（M0–M7）

1. **功能对齐 Android**：地图选点 → 潮汐/天气/海洋聚合 → 五维评分 → 最佳窗口 → 收藏 → 设置，评分逐字段一致（`diff = 0`）。
2. **新增能力：环境自适应（ADP）**——同一个 URL，PC 走 PC 布局，移动端走与 APP 同构的移动布局，平板走中间态。
3. **零服务端**：纯静态构建产物，GitHub Pages / Cloudflare Pages / 国内静态 CDN 均可托管。
4. **不触碰 Android 工程**：web 代码全部在 `web/`；只读引用 Android 的 `strings.xml`、`FISHSCORE.md`、`design-handoff/tokens`、单测用例。

### 0.2 明确不做（一期）

| 不做 | 原因 |
|---|---|
| 服务端 / 云函数 / 账号体系 | 与「零服务端」冲突；收藏仅本地 |
| 微信小程序 | 见 `WECHAT_MINIPROGRAM_FEASIBILITY.md`：需代理 + 地图降级，二期再议 |
| 跨设备云同步 | 需后端 |
| 离线底图（PMTiles） | 体积与收益比低，列为可选增强 |
| PWA 推送 | 一期不需要，且 iOS 支持有限 |

### 0.3 迁移期 vs 新增需求期（关键区分）

| 阶段 | Android 侧改动 | 说明 |
|---|---|---|
| **迁移期（M0–M7）** | **0 文件改动** | web 只读 Android 资产；发现 Android 问题只登记 `PARITY_GAP`，不改代码 |
| **新增需求期（M7 之后）** | 按双端契约**同批改动** | 客户新需求默认两端一起适配，见 §7 |

> 「不影响 APP 工程」在迁移期是**硬红线**；新增需求期的双端同批是客户的**新要求**，两者不矛盾。

---

## 1. 需求增量：环境自适应（ADP）

### 1.1 判定策略（三级，不依赖 UA 做唯一样式源）

```ts
type EnvKind = 'mobile' | 'tablet' | 'desktop';
type EnvInput = 'touch' | 'mouse';

interface Environment {
 kind: EnvKind; // 布局档位
 input: EnvInput; // 交互能力（hover / 右键 / 键盘）
 width: number; // 视口 CSS px
 source: 'auto' | 'manual';
}
```

| 优先级 | 信号 | 规则 | 理由 |
|---|---|---|---|
| 1 | **用户显式选择** | `settings.layoutMode: 'auto' \| 'desktop' \| 'mobile'`，存 `localStorage` | 用户永远是对的；PC 上也有人要「手机版」 |
| 2 | **视口宽度** | `<768` → mobile；`768–1199` → tablet；`≥1200` → desktop | 响应式本质；折叠屏展开/窗口缩放即时生效 |
| 3 | **交互能力** | `matchMedia('(hover: hover) and (pointer: fine)')` → 启用 hover/右键/快捷键 | 触屏 PC、手写笔设备也能正确降级 |
| — | UA / `navigator.userAgentData` | **只写日志，不决定布局** | iPad 伪装成 Mac、PC 微信内置浏览器、折叠屏都会误判 |

**实现要点**

- 基于 `matchMedia` + `ResizeObserver`，**不用** `window.innerWidth` 轮询；
- 首帧用 CSS 媒体查询渲染骨架，JS 水合后接管，避免布局闪动（FOUC）；
- 切换档位时**不重建页面**：`AppShell` 换壳，路由与选中态保持（见 1.4）。

### 1.2 布局档位与差异

| 屏 | mobile（<768） | tablet（768–1199） | desktop（≥1200） |
|---|---|---|---|
| **S01 地图** | 全屏地图 + 底部 236dp 信息浮条 + 底部 Tab；Top10 横滑 | 地图 + 左 280 可折叠列表；右详情改为抽屉 | **左 320 列表 + 中地图 + 右 380 详情预览**（可收起） |
| **S02 指数** | 单列滚动 + 顶部日期胶囊 + 5 段色带 | 单列，内容 `max-width: 720` | 左导航 240 + 内容 960 + 右 320 因子摘要锚点 |
| **S03 因子** | 单列卡片 | 单列 | 内容 960 + 右侧锚点目录 |
| **S04–S07** | 单列 Canvas 图表 | 单列 | 960 居中，图表可并排 2 列 |
| **S08 收藏** | 卡片列表 | 卡片 + 顶部筛选 | **表格**（名称/水体/档位/最佳窗口/更新时间），列排序 |
| **S09 设置** | 单列六版块 | 单列 | 左侧分类 240 + 右侧表单 640 |

**导航**

| | mobile / tablet | desktop |
|---|---|---|
| 主入口 | 底部 TabBar（地图 / 收藏 / 设置） | 顶部全局栏（Logo / 搜索 / 主题 / 语言 / 布局切换） |
| 二级页 | 全屏 push | 右侧面板或同页展开 |
| 返回 | 系统返回 / 顶部返回 | `Esc` / 面包屑 |

### 1.3 平台能力映射

| 能力 | desktop | mobile | 实现 |
|---|---|---|---|
| hover 预览 | 卡片 hover 显示窗口摘要 | ❌ | `@media (hover:hover)` |
| 右键菜单 | 地图右键「设为钓点 / 复制坐标」 | ❌ | `contextmenu` |
| 键盘 | `Esc` 关闭、`/` 聚焦搜索、`F` 收藏、`←/→` 切日期 | ❌ | 快捷键表 + a11y |
| 地图缩放 | 滚轮 + `+/-` 按钮 | 双指 | MapLibre 原生 |
| 安全区 | `env(safe-area-inset-*)` 仅触屏 | | CSS 变量 |
| 命中区 | ≥40px | **≥44px** | token `--hit-size` |
| 滚动 | 三栏独立滚动 | 单列整页滚动 | `overscroll-behavior` |

### 1.4 状态保持（切换档位不丢状态）

- **URL 是唯一真相**：`#/map?spot=123&date=2026-11-13&panel=detail`
- PC 选中点 ↔ 移动当前点共用同一 `spotId`；
- 档位切换只换 `AppShell`，页面组件 `key` 不变 → 不卸载、不重新请求；
- 地图实例复用同一个 `maplibregl.Map`，切换后调用 `map.resize()`（见 §8 风险 R-2）；
- 手动切档写入 `localStorage`，并在设置页可恢复 `auto`。

---

## 2. 架构（在既定方案上增量）

```
src/
├── app/
│ ├── router.tsx # hash 路由；query 承载 spot/date/panel
│ ├── container.ts # 依赖装配（对应 AppGraph.kt）
│ ├── environment/ # ★ ADP
│ │ ├── detect.ts # 纯函数：输入视口/能力 → Environment
│ │ ├── EnvironmentProvider.tsx
│ │ ├── useEnvironment.ts
│ │ └── env.test.ts
│ └── shell/
│ ├── AppShell.tsx # 按 kind 选择 Desktop/Mobile/Tablet Shell
│ ├── DesktopShell.tsx # 顶栏 + 三栏
│ ├── TabletShell.tsx # 顶栏 + 可折叠侧栏
│ └── MobileShell.tsx # 页面栈 + 底部 TabBar
├── domain/ # 1,707 行平移（对拍）
├── data/ # fetch + Dexie + TTL/门控/限流/重试
├── ui/
│ ├── tokens/ # design-tokens.json → CSS 变量（含 layout 层）
│ ├── primitives/ # Button/Card/Chip/Badge/Sheet/Tab…
│ ├── charts/ # Canvas 2D（ChartMath 平移）
│ └── icons/ # 统一命名 ic_*（如 ic_map_fieldguide）
└── i18n/{zh,en}.json # 572 key × 2 语（由 strings.xml 生成）
```

**分层纪律**

- `domain` 不 import `data` / `ui`（与 Android 依赖方向一致）；
- `ui` 组件不直接 fetch，只收 props；
- 布局差异**只在 shell 与组件变体里**，禁止复制两份页面（防双布局维护失控）。

**组件变体约定**

```ts
type Density = 'compact' | 'regular' | 'comfortable';
type Size = 'sm' | 'md' | 'lg';
// 例：<ScoreCard size={env.kind === 'desktop' ? 'lg' : 'md'} density={...} />
```

---

## 3. 设计 token 三层（双布局的关键）

| 层 | 来源 | 用途 |
|---|---|---|
| primitive | `design-handoff/tokens/design-tokens.json` | 原始色值/字号/圆角/间距（不改） |
| semantic | 现有 `ScoreBands` / `ConfidenceBadge` / `TideSpotTheme` 语义 | 分数档位色、免责条、徽标 |
| **layout（新增）** | 本文档定义 | 断点相关：侧栏宽、内容 max-width、命中区、密度 |

```css
:root {
 --sidebar-w: 0px; /* mobile 默认无侧栏 */
 --content-max: 100%;
 --hit-size: 44px;
 --density: 1;
}
@media (min-width: 1200px) {
 :root { --sidebar-w: 320px; --content-max: 960px; --hit-size: 40px; }
}
```

> 深浅主题沿用现有两套 token；PC 不新增第三套配色，避免偏离 `design-handoff` 验收基准。

---

## 4. 里程碑与工时

| 阶段 | 人日 | 交付物 | 验收门槛 |
|---|---:|---|---|
| **M0 Spike** | 3 | Vite 骨架 + Pages 部署 + domain 对拍框架 + MapLibre 复刻 S01 | Actions 部署成功；准星偏差 ≤1px@360dp |
| **M1 Domain** | 6 | `domain/` 1:1 平移；`java.time`→`Temporal` | 2,280 行 Kotlin 用例对拍 **diff=0** |
| **M2 Data** | 9 | `fetch` 客户端、Dexie schema、TTL/门控/限流/重试、降级字段 | 断网 / 5xx / marine 全 null 三类与 Android 一致 |
| **M3 Design System** | **10** | token 三层、primitives、图标 `ic_*`、**组件 density/size 变体** | 对比度 0 FAIL；三档截图可出 |
| **M4 Mobile Pages** | 12 | S02–S09 移动布局（含 3 处 Canvas 图表） | 逐屏对照 9 屏效果图 |
| **M5 Desktop Layout** | **8** | Desktop/Tablet Shell、三栏 S01、表格 S08、两栏 S09 | 1280/1440/1920 三档无横向滚动 |
| **M6 Map** | **10** | S01 三档布局 + OSTIA 点值/叠加 + 定位 + 收藏星标 + `resize()` | 同点同视野截图对比；切档地图不空白 |
| **M7 Release** | **7** | ADP 测试矩阵、PWA、E2E、视觉回归、门禁、部署脚本 | §8 全部通过 |
| **合计** | **65** | — | **≈13 周（单人）/ 9–10 周（AI 辅助）** |

**可裁剪档位**

| 范围 | 人日 | 周期 |
|---|---:|---|
| 完整（4 页 + PC/移动双布局 + OSTIA） | 65 | 13 周 / 9–10 周 |
| 去掉 OSTIA 叠加 + 设置页高级项 | 50 | 10 周 / 7–8 周 |
| MVP：地图 + 指数 + 离线（双布局保留） | 38 | 7–8 周 / 5–6 周 |

> 相比 `GITHUB_PAGES_MIGRATION_PLAN.md` 的 50 人日，增量 **+15 人日**：Design +3（变体）、Desktop Layout +8（新增）、Map +2（三档 + resize）、Release +2（视口矩阵）。

**已锁定（2026-11-13 客户确认）**：按**完整范围 65 人日**推进；PC 地图页为**三栏（右栏可收起）**；Android 平板布局**一期不做**，登记 `PARITY_GAP-WEB-ADP-01`。

---

## 5. 周计划（WBS，单人 + AI 辅助）

| 周 | 阶段 | 任务 | 依赖 | 周末可验收 |
|---|---|---|---|---|
| W1 | M0 | 建 `web/`；Vite+TS+PWA 骨架；Actions→Pages 打通；fixture 导出脚本 | — | 访问 Pages 看到空壳；`npm test` 跑通 |
| W1–W2 | M0/M1 | MapLibre 复刻 S01 骨架（准星/长按/Top10/三样式）；`domain/` 平移启动 | W1 | 地图手感对齐；首个 `FishScoreCalculator` 对拍通过 |
| W2–W3 | M1 | `Solunar`/`SubScores`/`VetoEvaluator`/`TideCalibrator`/`BestWindowFinder`/`Top10TimeFilter`/`WaterQualityProxy` | M1 | **2,280 用例 diff=0** |
| W3–W4 | M2 | `fetch` 封装、`DataError` 分类；Dexie schema；`ttl`/`refreshGate`/`tokenBucket`/`retry` | M1 | 三类降级场景通过 |
| W4–W5 | M2/M3 | `OpenMeteoClient`/`Geocode`/`ReverseGeo`/`Ostia`；token 三层 + primitives | M2 | 五个端点联调成功 |
| W5–W6 | M3 | 组件变体（density/size）、图标 `ic_*`、深浅两套、对比度门禁 | M3 | 组件页（内部）三档可切 |
| W6–W8 | M4 | S02 指数 / S03 因子 / S04–S07 图表 / S08 收藏 / S09 设置（移动布局） | M3 | 移动端逐屏对照效果图 |
| W8–W9 | M5 | Desktop/Tablet Shell；S01 三栏；S08 表格；S09 两栏 | M4 | 1280/1440/1920 无横向滚动 |
| W9–W10 | M6 | S01 三档布局、OSTIA 点值+叠加、定位、收藏星标、`map.resize()` | M5 | 切档不空白；同点截图对比 |
| W10–W11 | M7 | ADP 测试矩阵、Playwright E2E、视觉回归基线、性能预算 | M6 | 矩阵全绿 |
| W11–W13 | M7 | 门禁接入 CI、部署脚本、验收报告、双端契约门禁 `check_parity` | M7 | §8 全部通过 |

**并行建议（若 2 人）**

- A：`domain` + `data`（W1–W5）→ 之后转 `M6 地图`
- B：`M3 设计系统` + `M4 移动页`（W3–W8）→ 之后转 `M5 PC 布局`
- 接口冻结：M2 结束时冻结 `DataGateway` 类型（对应 Android `TideSpotRepository` 门面），之后不再改签名。

---

## 6. 任务清单与交付物（可勾选）

### 6.1 工程与基础设施

- [ ] `web/` 目录、`package.json`、`vite.config.ts`（`base` 环境变量）、`tsconfig` 严格模式
- [ ] ESLint + Prettier + Vitest + Playwright
- [ ] `.github/workflows/deploy-web.yml`（仅 `web/**` 与文档触发）
- [ ] `web/scripts/export-fixtures.mjs`（**只读** `tidespot-android/**/src/test`，输出 JSON）
- [ ] `web/scripts/gen-i18n.mjs`（**只读** `strings.xml`，输出 `i18n/{zh,en}.json`）
- [ ] `web/scripts/check_parity.mjs`（比对 key 集合，CI 门禁）

### 6.2 领域与数据

- [ ] `domain/` 18 个文件平移 + `domain/parity.test.ts`
- [ ] `data/` 客户端 5 个 + Dexie schema 7 张表 + 4 个纯逻辑（ttl/gate/bucket/retry）
- [ ] 降级字段与 Android 同名：`servedFromCache` / `weatherDegraded` / `marineDegraded` / `marineAllNullInland` / `effectiveWaterBody`

### 6.3 UI 与布局

- [ ] token 三层 + `--layout` CSS 变量
- [ ] 12 个 primitives（Card / Chip / Badge / ScoreCard / SegmentedTabs / BottomSheet / Table / Tooltip / Toast / Skeleton / Switch / Field）
- [ ] 3 个 Shell（Desktop / Tablet / Mobile）+ 1 个 `AppShell` 选择器
- [ ] 6 个页面（S01 / S02 / S03 / S08 / S09 / S04–S07 合并为图表组）
- [ ] 图标：`ic_*_fieldguide` 命名，SVG sprite

### 6.4 验收与发布

- [ ] `env.test.ts`（断点/能力/手动切换/SSR 无关）
- [ ] Playwright：3 档 × 深浅 × 中英 = 12 组截图基线
- [ ] 离线用例（断网 → 最近 3 点可看）
- [ ] Lighthouse 报告（移动 + 桌面两档）
- [ ] `web/README.md` 与双端契约门禁上线

---

## 7. 双端适配契约（本次新增，三处落位）

> 客户要求：**「以后如果有新增需求，两端都要一起适配新的需求」**。
> 落位：本文件 §7（权威条款）、`web/README.md`（Web 侧执行）、`tidespot-android/CONTRACT.md`（Android 侧执行）。

### 7.1 条款

| # | 条款 | 执行方式 |
|---|---|---|
| **C-1** | 每条新需求登记时必须标注 `platforms: android \| web \| both`；**默认 `both`** | `REQUIREMENTS_REGISTER.md` 新增字段；未标注视为 `both` |
| **C-2** | `both` 需求必须**两端同批交付**（同一迭代内）；确不能同批时，登记 `PARITY_GAP`（含原因、补齐版本、验收人） | 提审前检查 `PARITY_GAP` 清单，不允许静默缺失 |
| **C-3** | 单一真相源，两端都从同一处派生 | 评分口径 `FISHSCORE.md`；视觉 `design-handoff/tokens/*`；文案 `tidespot-android/**/res/values{,-en}/strings.xml`；URL/数据源 `TideSpotUrls.kt` ↔ `urls.ts`（常量必须逐字一致） |
| **C-4** | 评分与派生结果**逐字段一致**，不允许「体验上差不多」 | `domain/parity.test.ts` 用 fixture 对拍，`diff=0` 才允许合并 |
| **C-5** | 文案 key 集合两端必须一致 | `check_parity.mjs`：`strings.xml` key 集合 == `i18n/{zh,en}.json` key 集合；缺一即 CI 失败 |
| **C-6** | 允许存在「平台差异白名单」，但必须登记 | 白名单：系统通知/WorkManager、APK 签名与分发、PWA 安装与 SW、PC 快捷键/右键、Android 分享 Intent、系统返回键、`safe-area` |
| **C-7** | 版本号语义对齐 | Android `versionName` = web `APP_VERSION`（web 页脚显示同一版本号）；发版记录同批更新 |
| **C-8** | 需求验收清单双端各一份 | Android 用真机清单（现有流程）；web 用 §8 矩阵（含 ADP 专项） |

### 7.2 提审/合并检查（PR 模板必填）

```
- [ ] 需求 ID: CHG-xx platforms: both
- [ ] Android 侧已改：<文件/页面>
- [ ] Web 侧已改：web/src/...
- [ ] 评分对拍 diff=0（若涉及 domain/派生）
- [ ] i18n key 集合一致（check_parity 通过）
- [ ] 若未双端同批：PARITY_GAP 已登记（编号 + 补齐版本）
```

### 7.3 例外与边界

- **平台专属需求**（如「PC 快捷键」）登记为 `platforms: web`，Android 侧不强制；
- **环境自适应本身**是 web 专属能力；Android 的等价物是「平板/折叠屏布局」，一期**不做**（2026-11-13 客户已裁决），登记为 `PARITY_GAP-WEB-ADP-01`；若后续要求，Android 侧预估 +6~8 人日；
- 迁移期（M0–M7）不适用 C-2 的「双端同批」，只适用「零 Android 改动」红线。

---

## 8. 验收标准（DoD）

### 8.1 功能与数据一致性

| # | 项 | 门槛 |
|---|---|---|
| 1 | 评分一致性 | 30 点 × 240h，与 Android 逐字段 `diff=0`（分数/五维/否决码/置信/最佳窗口/Top10） |
| 2 | 降级一致性 | 网络失败 / 5xx / marine 全 null，字段名与徽标文案同 Android |
| 3 | 收藏与设置 | CRUD、排序、分组、标签、过期判定与 Android 同规则 |
| 4 | 文案 | 572 key × 2 语全覆盖；`check_parity` 通过 |

### 8.2 ADP 专项（新增）

| # | 场景 | 门槛 |
|---|---|---|
| A1 | 视口 360 / 390 / 430 | 移动布局；命中区 ≥44px；无横向滚动 |
| A2 | 视口 768 / 1024 | 平板布局；侧栏可折叠 |
| A3 | 视口 1280 / 1440 / 1920 | 三栏布局；内容 `max-width` 生效；无横向滚动 |
| A4 | 拖动窗口 1024→1280 反复 | 布局切换 ≤1 帧闪动；URL 与选中点不变；地图不空白（`resize()` 生效） |
| A5 | 手动切「手机版」在 PC | 立即生效并持久化；刷新后保持 |
| A6 | 触屏笔记本（hover: none） | 不出现 hover-only 交互；右键菜单不依赖 |
| A7 | 折叠屏展开/收起（模拟 viewport 变化） | 自动切档且不重发请求 |
| A8 | 键盘 | `Tab` 顺序合理、`Esc` 关闭面板、`/` 搜索、`F` 收藏 |
| A9 | 缩放 200%（浏览器 zoom） | 仍可用，无内容截断 |

### 8.3 性能与部署

| # | 项 | 门槛 |
|---|---|---|
| P1 | 移动首屏 JS | ≤350KB gzip（地图懒加载） |
| P2 | LCP（4G，Moto G4 模拟） | ≤2.5s |
| P3 | 桌面 Lighthouse | ≥90（性能/可访问性/最佳实践/SEO） |
| P4 | 断网 | 最近 3 个点详情 + 收藏列表可看 |
| P5 | Pages | 刷新 404=0（hash 路由）、HTTPS、资源 hash 缓存 |
| P6 | 构建 | Actions ≤10 分钟；部署成功 |

---

## 9. 风险与对策

| # | 风险 | 等级 | 对策 |
|---|---|---|---|
| R-1 | **双布局维护成本翻倍**（两套页面） | 高 | 严禁复制页面；差异只进 shell 与组件 `size/density`；视觉回归锁三档 |
| R-2 | 切档时地图空白/抖动 | 中 | `ResizeObserver` + `requestAnimationFrame` → 过渡结束后 `map.resize()`；容器 `min-height` 兜底 |
| R-3 | 断点误判（折叠屏、PC 微信内置浏览器、iPad 伪装） | 中 | 宽度优先 + 能力检测 + 手动切换兜底；UA 仅日志 |
| R-4 | 契约执行走样 | 中 | `check_parity` + 对拍门禁 + PR 模板 + `PARITY_GAP` 台账 |
| R-5 | 文案单源在 Android，web 改文案必须动 Android | 中 | 迁移期只读；新增需求期按 C-2 同批，且登记 CHG |
| R-6 | 无服务端 → 限流按 IP（600/min） | 中 | `TokenBucket` + 客户端缓存；错误提示复用 `DataError` |
| R-7 | 高分屏 PC 的 WebGL 开销 | 中 | `maxPixelRatio: 2`、关闭 OSTIA 叠加时降 `maxzoom` |
| R-8 | iOS Safari 存储清理、定位需手势 | 中 | 引导「添加到主屏幕」；定位由按钮触发 |
| R-9 | 双端并行污染 Android 主线 | 中 | `web/` 独立目录 + 独立 CI；不加入 Gradle；迁移期 0 改动 |

---

## 10. 决策点与裁决（2026-11-13）

| # | 问题 | 裁决 | 状态 |
|---|---|---|---|
| Q1 | PC 布局：三栏还是两栏？ | **三栏（左列表 + 中地图 + 右详情预览），右栏可收起** | 客户已确认 |
| Q2 | 「大屏适配」是否反向要求 Android 平板/折叠屏布局？ | **一期不做**；登记 `PARITY_GAP-WEB-ADP-01`，若后续要求 Android +6~8 人日 | 客户已确认 |
| Q3 | 手动布局切换入口放哪？ | 设置页 + PC 顶栏（推荐值，可随时调整） | 按推荐执行 |
| Q4 | 版本号是否与 Android 同步？ | 同步（C-7，web 页脚显示同一 `versionName`） | 按推荐执行 |
| Q5 | 首版范围？ | **完整版 65 人日**（含 OSTIA 点值与叠加、全部设置项） | 客户已确认 |

**上线前仍需确认（不阻塞 M0–M3）**

| # | 事项 | 需要客户回答（二选一即可） | 判定与影响 |
|---|---|---|---|
| O-1 | **是否商用** | ① 是否**免费公开**（无广告、无订阅、无内购）？② 是否为**客户项目 / 企业内部使用**？ | 非商业 → Open-Meteo 免费层 + GitHub Pages 可用；商业 → 必须订阅 Open-Meteo（`customer-api` + `apikey`，一处配置）+ 自有域名托管 |
| O-2 | **托管与域名** | ① 用户是否**主要在中国大陆**？② 是否已有**已备案域名**？ | 有备案 → 国内 OSS/COS + CDN（正式推荐）；无备案 → Cloudflare Pages 过渡（免备案，大陆可达性一般） |
| O-3 | 隐私声明 | 是否接受坐标直传境外 API？或改用网格化坐标（0.05°）？ | 上线合规材料；不改代码结构 |

---

## 11. 对 Android 工程的影响（零改动证明）

| 项 | 结论 |
|---|---|
| 源码/资源/构建脚本 | **0 文件改动**；`settings.gradle.kts` 不加入 web |
| 只读依赖（web 构建时读取） | `tidespot-android/**/res/values{,-en}/strings.xml`、`core/domain` 单测、`core/data/remote/TideSpotUrls.kt`（常量核对） |
| 只读依赖（人工参照） | `FISHSCORE.md`、`design-handoff/tokens/*`、`design-handoff/mapping/*`、9 屏效果图 |
| 新增文件 | 根目录 3 份文档（`WEB_PROJECT_PLAN.md`、`GITHUB_PAGES_MIGRATION_PLAN.md`、`WECHAT_MINIPROGRAM_FEASIBILITY.md`）+ `web/` 目录 |
| 反向约束 | web 若需新文案，必须先在 Android `strings.xml` 加 key（新增需求期），属 C-2 允许范围 |
| CI | 新 workflow 仅监听 `web/**` 与 `.github/workflows/deploy-web.yml`，不触发 Android 构建 |

**迁移期红线自检（每次提交）**

```powershell
git status --porcelain | Select-String "tidespot-android" # 必须为空
```

---

## 12. 执行基线（2026-11-13 锁定）

| 项 | 值 |
|---|---|
| 范围 | **完整版 65 人日**（含 OSTIA 点值与叠加、全部设置项） |
| 周期 | 13 个自然周：**2026-11-16（周一）→ 2027-02-14**；**注意 2027-02-05 起为春节假期**，按工作日计建议交付窗口顺延至 **2027-02-26**（末 2 周为验收缓冲） |
| PC 布局 | 三栏：左 320 列表 / 中地图 / 右 380 详情（可收起） |
| 移动布局 | 与 APP 同构：全屏地图 + 底部 Tab + BottomSheet |
| 平板 | 顶栏 + 可折叠侧栏（Web 独有） |
| Android 平板 | 一期不做（`PARITY_GAP-WEB-ADP-01`，客户已裁决） |
| 关键路径 | M1 → M2 → M3 → M5 → M6 → M7；M4 与 M5 在 2 人配置下并行 |
| 首周交付 | M0：`web/` 骨架 + Actions 空壳上线 + fixture 导出 + S01 手感复刻 |
| 当前状态 | **M7 已完成（2026-11-13，待人工验收）**；`web/` 已交付，Android 零改动 |
| 上线前待办 | O-1 商用与否 / O-2 托管与域名 / O-3 隐私声明（不阻塞 M0–M3） |

## 13. 下一步

### 13.1 恢复开工条件

O-1 与 O-2 由客户确认后即启动，无需再改计划：

- **O-1 非商业** → Open-Meteo 免费层 + GitHub Pages（演示/验收）；
- **O-1 商业** → 订阅 Open-Meteo，`urls.ts` 切 `customer-api` + `apikey`，托管换自有域名；
- **O-2** 决定 `PAGES_BASE` 与 CNAME；有备案域名则同产物发国内 OSS/CDN。

### 13.2 M0 首日清单（1 个工作日）

| 顺序 | 任务 | 产出 |
|---|---|---|
| 1 | `npm create vite@latest web -- --template react-ts`；TS 严格模式 | `web/package.json`、`tsconfig.json` |
| 2 | 安装 `maplibre-gl` / `dexie` / `@tanstack/react-query` / `vite-plugin-pwa` / `vitest` / `playwright` | lockfile |
| 3 | `.github/workflows/deploy-web.yml` + 仓库 Pages 开关 | 空壳可访问（HTTPS） |
| 4 | `web/scripts/export-fixtures.mjs`（只读 Kotlin 单测） | `test/fixtures/domain.json` |
| 5 | `domain/parity.test.ts` + 首个 `fishScore` 平移 | 首个 `diff=0` 通过 |
| 6 | `AppShell` 三档骨架（空内容，仅切换） | 360/1024/1440 三档可切 |

### 13.3 暂停期间的边界

- 不创建 `web/src`、不安装依赖、不改 `tidespot-android/**`；
- 只保留文档与契约；
- 若期间 Android 侧有新需求，按 `CONTRACT.md`「跨端适配契约」登记 `platforms`，Web 侧待开工后统一补。

### 13.4 M0 完成情况（2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| 工程骨架 | Vite 5 + React 18 + TypeScript 5（strict） | `web/package.json`、`tsconfig.json` |
| 依赖安装 | 103 包（npmmirror 镜像，15s） | `web/package-lock.json` |
| 环境自适应 | `detectEnvironment`：手动 > 宽度 > 交互能力，UA 不参与 | `src/app/environment/detect.ts` |
| 单测 | 18 条通过（断点边界 / 手动覆盖 / 触屏判定 / 脏值回退） | `npm run test` |
| 三档骨架 | Desktop 三栏（右栏可收起）/ Tablet 可折叠侧栏 / Mobile 底部 Tab | `src/app/shell/AppShell.tsx` |
| 类型检查 | 通过 | `npm run typecheck` |
| 构建 | JS 147.87 kB（gzip 48.00 kB）、CSS 4.60 kB（gzip 1.35 kB） | `dist/` |
| 部署流水线 | 已写入；`PAGES_BASE` 自动取仓库名 | `.github/workflows/deploy-web.yml` |
| 文案生成 | zh 572 / en 572，与 Android 完全一致 | `src/i18n/{zh,en}.json` |
| 文案门禁 | PASS（zh / en / Android 三方 key 集合一致） | `npm run check:parity` |
| fixture 候选 | 扫描 49 个 Kotlin 测试文件，提取 399 条断言候选 | `test/fixtures/extracted-kotlin.json` |
| FishScore 平移 | 未开始（M0 阶段），已于 M1 完成 | 见下方 M1 表 |

### 13.5 M1 完成情况（2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| 平移模块 | `mathkit` / `solunar` / `subScores` / `doPhysics` / `waterTemp` / `waterQuality` / `veto` / `fishScore` / `presets` | `src/domain/`（9 个 TS 文件） |
| fixture | 由**真实 Kotlin 实现**生成 279,644 字符 JSON；`scripts/make-fixtures.ps1` 一键复现（只读 Android） | `test/fixtures/domain.json` |
| 对拍覆盖 | tide 105 / pressure 126 / wind 65 / weather 336 / daypart 144 / tempMatch 27 / comfort 11 / waterQuality 144 / veto 2,592 / 端到端 50 小时 | `npm run test` |
| 测试结果 | **42 passed**（ADP 18 + parity 24），相对容差 `1e-9` | vitest |
| 端到端样例 | 海水预设峰值 **65**（MODEL / 10 因子 / 无否决码）；淡水预设峰值 **82** | 浏览器 + fixture |
| 构建 | JS 155.87 kB（**gzip 51.65 kB**）、CSS 4.60 kB | `dist/` |

**M1 结论**：评分口径与 Android **逐位一致**（fixture 由 Kotlin 实际运行产生，非人工折算）。M2 起接数据层。

### 13.6 M2 进度（网络层完成，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| URL 构造 | 18 个常量 + weather / marine / geocoding / Photon / BigDataCloud / Copernicus / OSTIA（含 GetTile 模板） | `src/data/urls.ts` |
| 结构化错误 | 5 类（429 / 5xx / 网络 / B9 参数 / 其余 4xx），重试语义与 Android 一致 | `src/data/errors.ts` |
| 纯逻辑 | TTL（3h/6h/48h）/ MinRefreshGate（15min）/ TokenBucket（6 req/min，用户优先）/ Retry（1s·4s·10s）/ SeriesJoiner | `src/data/*.ts` |
| DTO 映射 | 天气 21+8 字段、海洋 16 字段；能见度 km→m；坏时间串跳过；内陆全 null 判定 | `src/data/dto.ts` |
| 客户端 | `fetch` + 并行 ≤4 + 30s 超时 + `fetch_log` 回调 + 脏 JSON 归一为 NetworkError | `src/data/client.ts` |
| 真实冒烟 | 青岛：weather **408h** / marine **264h**（潮位 −0.58m、SST 17.5）；千岛湖：marine 264h **全 null** | `SMOKE=1 npm run test` |
| 样本 | 前 48h 映射结果已留存，供离线回归 | `test/fixtures/open-meteo-sample.json`（54 KB） |
| 测试 | **69 passed**（ADP 18 + parity 24 + data 27），真实冒烟 2 条默认跳过 | vitest |
| 待续 | Dexie schema、Repository 编排、降级字段与缓存回退（M2b） | — |

### 13.7 数据持久化设计（2026-11-13 确认口径）

**原则：无服务端，用户数据只存在浏览器本地；origin 即"账号"。**

| 数据 | 存储 | 键/索引 | 生命周期 |
|---|---|---|---|
| 收藏点 | IndexedDB `spots` | `id`；索引 `lastViewedAt` / `waterBody` | 持久，直到清除站点数据 |
| 逐时指标 | IndexedDB `hourly_metric` | `[spotId+time]` | TTL 3h/6h；≥48h 不可用；LRU |
| 逐时评分 | IndexedDB `hourly_score` | `[spotId+time]` | 同 TTL |
| 拉取日志 | IndexedDB `fetch_log` | `++id` | 最近 200 条 |
| 设置 | localStorage `tidespot.settings` | 单 key JSON | 持久 |
| 布局模式 | localStorage `tidespot.layoutMode` | 单 key | 持久 |
| Copernicus token | sessionStorage | 单 key | 300s，关页失效 |

**读取顺序**：`spots`（立即渲染）→ 缓存命中（可离线）→ TTL 过期才请求 → 成功写库 / 失败保留旧值并挂降级徽标。

**已知限制与对策**

| 限制 | 对策 |
|---|---|
| 不跨设备/跨浏览器 | 一期接受；可选二期「导出/导入 JSON」或用户自带云盘同步（WebDAV/Gist） |
| 清理浏览数据、隐私模式 | 首次写入申请 `navigator.storage.persist()`；引导「添加到主屏幕」 |
| Safari 7 天存储清理 | 同上；PWA 安装后显著缓解 |
| **换域名 = 换 origin → 读不到旧数据** | 客户已裁决（2026-11-13）：**一期不固定域名、不做导出/导入**，换域名时用户重新收藏；该限制写入用户须知 |
| 改仓库名 | 不影响（IndexedDB 按 origin 隔离，不按路径） |

**环境备注**：本机 Node 不走系统代理，真实冒烟需 `NODE_USE_ENV_PROXY=1` + `HTTPS_PROXY`；浏览器会自动使用系统代理，不受此限制（同一 URL 已用 `curl` 与浏览器路径分别验证）。

### 13.8 M2 完成情况（仓储层，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| Dexie schema | 7 表：spots / hourly_metric / hourly_score / fetch_log / manual_observation / manual_tide_fit / meta（复合索引对应 Room） | `src/data/db.ts` |
| Repository | TTL 命中 → 缓存回退 → 并行拉取（≤4）→ 写库 → 按时间戳 join → 评分 → 降级标记；收藏 CRUD + 级联清理缓存 | `src/data/repository.ts` |
| 降级字段 | `weatherDegraded` / `marineDegraded` / `marineAllNullInland` / `servedFromCache` / `fetchedAtMillis` / `unusable`（≥48h） | 同上 |
| 页面接入 | 收藏 Tab：真实写入/读取/打开/删除；打开时拉真实数据并展示峰值；设置 Tab：布局模式、存储估算、版本 | `src/app/shell/AppShell.tsx` |
| 依赖装配 | `client` + `repository` 单例 + `APP_VERSION = 1.18.1`（C-7） | `src/app/container.ts` |
| 测试 | **78 passed**（ADP 18 + parity 24 + data 27 + repository 9），冒烟 2 条默认跳过 | `npm run test` |
| 构建 | JS 275.83 kB（**gzip 91.74 kB**）、CSS 5.53 kB | `dist/` |
| 关键用例 | 首次拉取 2 请求 / TTL 内零请求 / 天气过期单独刷新 / 刷新失败回退旧数据 / 内陆全 null / 缓存详情不发请求 | repository.test.ts |

**持久化验证方式**：浏览器「收藏」Tab → 点「收藏示例点」→ 刷新页面 → 条目仍在（IndexedDB，同 origin）。

### 13.9 M3 完成情况（设计系统，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| Token 生成 | 只读 `design-tokens.json` → CSS（浅/深各 24 个颜色变量 + 字号/圆角/间距/阴影/五档/徽标）+ TS 常量 | `scripts/gen-tokens.mjs`、`src/styles/tokens.generated.css`、`src/ui/tokens/bands.ts` |
| 三层结构 | primitive（生成）/ semantic（ScoreBands、ConfidenceBadge）/ layout（视口与命中区） | `tokens.css` + `tokens.generated.css` |
| 12 个组件 | Button / Card(L0-L2) / Chip / ConfidenceBadge / SegmentedTabs / DaySelector / ScoreCard / BottomSheet / DataTable / Tooltip / Switch / Field | `src/ui/primitives/primitives.tsx` |
| 主题 | 跟随系统 + 手动浅/深 + localStorage 记忆；`data-theme` 切换 | `src/ui/theme.ts` |
| 对比度门禁 | **21 对，0 fail**：正文类按 AA 4.5；冻结语义色（五档/徽标）按图形元素 3.0，并注明颜色不可改 | `scripts/check-contrast.mjs` |
| 组件预览 | 设置页「组件预览」可展开全部 12 个组件 | `/settings` 面板 |
| 测试 | **83 passed**（+5：五档闭区间、UNAVAILABLE→UNCALIBRATED、全 0–100 覆盖） | `npm run test` |
| 构建 | JS 284.75 kB（**gzip 94.70 kB**）、CSS 20.21 kB（gzip 3.73 kB） | `dist/` |

**设计约束落地**：薄荷绿仅用于图形点缀（准星/圆点/图标底），不承载文字；`UNAVAILABLE` 一律显示「未校准」，不虚构更高置信。

### 13.10 M4 进度（S02 指数 + S03 因子，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| 路由 | 极简 hash 路由（`#/`、`#/spot/:id`），规避 Pages 无 SPA 回退 | `src/app/router.tsx` |
| S02 指数 | 顶栏（返回/名称/6 位坐标/收藏）、DaySelector（两行 52dp，按本地日期聚合）、来源时间行、五维 Tab、ScoreCard、逐时曲线、最佳窗口、否决码 Chip | `src/app/pages/DetailPage.tsx` |
| S03 因子 | 因子表（名称/子分/权重/贡献），取自当日峰值小时；10 个因子名走 i18n | 同上 |
| 图表 | Canvas 2D：0–100 网格、面积渐变、峰值高亮、主题切换自动重绘、ResizeObserver 自适应 | `src/ui/charts/HourChart.tsx` |
| i18n 运行时 | 572 key 双语；`%1$s` 占位；缺 key 回退 key 便于发现漏译 | `src/ui/i18n.ts` |
| 免责 | 底部入口 + BottomSheet 恒 5 条（`ts_disclaimer_*`） | DetailPage |
| 收藏入口 | 列表「详情」跳转；「摘要」保留快速查看 | AppShell |
| 测试/构建 | **83 passed**；JS 349.93 kB（**gzip 121.06 kB**）、CSS 21.63 kB | `npm run test` / `npm run build` |
| 待续 | S04–S07 图表 Tab（风浪/天气/水质/潮流）、S08 桌面表格、S09 两栏（M4b） | — |

### 13.11 M4b 完成情况（图表 Tab，2026-11-13）

| Tab | 内容 | 数据来源 |
|---|---|---|
| S05 天气 | 气温 / 气压 / 风速 三条曲线 + 当日区间摘要 | `slices.weather` |
| S07 潮流 | 潮位曲线（含零点线）+ 校准对照（标注未校准）；内陆点显示「水位不可用」 | `slices.marine.seaLevelHeightMslM` |
| S04 风浪 | 有效波高 / 周期曲线；内陆点显示「湖面风与水温」降级说明 | `slices.marine` |
| S06 水质 | 水温（气温滞后融合）/ 溶氧子分 / 浑浊度代理 + 样本数 + 「估算」徽标 | `WaterTempFusion` + `DoPhysics` + `SubScores` |
| 通用图表 | 多序列折线：缺失点断开、零点参考线、图例、主题切换重绘、ResizeObserver | `src/ui/charts/LineChart.tsx` |
| 测试/构建 | **83 passed**；JS 356.94 kB（**gzip 123.78 kB**）、CSS 22.03 kB | `npm run build` |

**口径**：所有缺失值保持 null 并在图上断开，不做 0 填充；水质徽标固定「估算」，不虚构实测；内陆点潮汐/浪高维度不参与评分，与 Android 一致。

### 13.12 M5 完成情况（桌面布局，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| 桌面详情三栏 | 左 300 收藏列表（当前项高亮）+ 中详情（复用 `DetailPage embedded`）+ 右 360 摘要（分数/档位/前三因子/降级状态），右栏可收起为 52px 竖条 | `src/app/pages/DesktopSpotLayout.tsx` |
| 详情复用 | `DetailPage` 新增 `embedded` 参数：隐藏返回、去掉 100dvh 与居中约束；移动/桌面共用同一套逻辑 | `DetailPage.tsx` |
| 收藏表格（S08） | 桌面 `DataTable`（名称/坐标/水体/预设/删除），行点击进详情；移动端仍为卡片列表 | `AppShell.tsx` |
| 设置两栏（S09） | 左 220 分类导航（外观/主题/存储/组件预览/版本），右单卡渲染；平板 180，移动回到单列 | `AppShell.tsx` + `shell.css` |
| 路由 | `#/spot/:id` 时按档位分派：desktop → 三栏，mobile/tablet → 全屏详情 | `AppShell.tsx` |
| 构建 | JS 360.53 kB（**gzip 124.79 kB**）、CSS 24.49 kB（gzip 4.37 kB） | `npm run build` |

**验证**：浏览器宽度 ≥1200 → 收藏点「详情」进入三栏；<1200 自动回单栏/全屏，状态不丢。

### 13.13 M6 进度（S01 地图，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| 地图引擎 | MapLibre GL JS **6.18.0**（命名导入：`Map` / `Marker` / `NavigationControl`） | `src/app/pages/MapPage.tsx` |
| 底图 | OpenFreeMap 三样式（彩色/素色/深色）在线切换，与 Android 同源 | `urls.ts` |
| 准星 | DOM 叠加（116dp，薄荷环 + 实心点），**唯一选点真源**：点击只移动地图，坐标恒取地图中心 | MapPage |
| 长按 | 600ms 长按 → `map.unproject` → `easeTo` 居中（拖动 >8px 取消） | MapPage |
| 定位 | `navigator.geolocation` + `flyTo`，失败提示可手动拖动 | MapPage |
| 逆地理 | Photon 主源（500ms 防抖 + 序号防竞态），失败回退 6 位坐标并挂「坐标名」Chip | `src/data/geo.ts` |
| OSTIA | raster source（GetTile 模板，tileSize 256）+ 开关，默认关闭；切样式自动重置 | MapPage |
| 收藏闭环 | 「收藏并查看」→ 写 spots → `#/spot/:id`（桌面进三栏，移动进全屏） | MapPage + AppShell |
| 懒加载 | MapPage 独立 chunk：主包 gzip **125.01 kB**；地图 chunk gzip **298.67 kB**（首屏不含） | `dist/` |
| 待续 | Top10 横滑（跨点聚合 + 时段筛选）、地图页桌面右栏、瓦片离线缓存（M6b） | — |

**构建备注**：本机 `%TEMP%` 下 esbuild 临时文件被拒（Access denied），构建需把 `TEMP`/`TMP` 指到项目内目录（`web/.tmp/tmp`）；CI（Linux）无此问题，已记入 README。

### 13.14 M6b 完成情况（跨点 Top10 + 桌面侧栏，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| 窗口算法 | 连续高分（≥65）聚窗；`rank = 0.62·峰值 + 0.23·均值 + 1.4·min(时长,4)`；不连续不合并；无窗口返回空 | `src/domain/bestWindows.ts` |
| 时段筛选 | 全部 / 清晨(4–9) / 白天(9–16) / 傍晚(16–21) / 夜间(21–4)，按峰值本地小时 | 同上 |
| 数据来源 | 只读本地 `hourly_score` 缓存（不为 Top10 触发网络），按 rank 取前 10 | `Top10Panel.tsx` |
| 移动 | 地图底部深蓝横条（52dp）显示榜首，点击打开 Sheet：时段 Chip + 列表 + 空态 | 同上 |
| 桌面 | 地图右侧 360 侧栏：地图说明 + Top10 列表 + 刷新 | 同上 |
| 空态 | 「暂无适合窗口（峰值需 ≥65）」并说明如何生成（打开详情刷新） | 同上 |
| 测试 | **91 passed**（新增 8：聚窗/不连续/空/阈值/rank 排序/4 时段/筛选） | `npm run test` |
| 构建 | 主包 **gzip 125.02 kB**；地图 chunk gzip **300.23 kB**（懒加载） | `dist/` |

**已知边界**：Top10 依赖缓存，首次需先打开收藏点详情生成评分；跨点聚合只覆盖已收藏点（与 Android 一致）。

### 13.15 M7 完成情况（发布收尾，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| PWA | manifest（名称/主题色/standalone/SVG 图标）+ `autoUpdate` SW；precache 9 项（1531 KiB，含地图 chunk，实现离线开壳） | `dist/sw.js`、`dist/manifest.webmanifest` |
| SW 策略 | App shell 预缓存；**API 不进 SW**（一律写 IndexedDB） | `vite.config.ts` |
| 预算门禁 | 主包 **122.4 KB**、地图 chunk **293.2 KB**、CSS 9.9/4.9 KB；总 gzip 432.8 KB，**0 项超预算** | `scripts/check-budget.mjs` |
| CI | `deploy-web.yml` 依次：install → test → build → **budget** → **playwright install** → **E2E** → Pages 发布 | 工作流 |
| E2E | 4 条：壳与三档、收藏写入+刷新仍在、主题 `data-theme`、组件预览展开；mobile(390) + desktop(1440) 两 project | `e2e/smoke.spec.ts` |
| 产物自检 | `preview` 下 `/`、`/sw.js`、`/manifest.webmanifest` 均 200 | 本机验证 |

**人工验收清单（需客户/交付方执行）**

1. GitHub 仓库 **Settings → Pages → Source: GitHub Actions**；push 后访问 `https://<user>.github.io/<repo>/`。
2. 首次打开后刷新一次，DevTools → Application → Service Workers 应显示 activated；Manifest 无报错。
3. 断网后刷新：应用壳可打开（收藏与缓存数据来自 IndexedDB）。
4. `npx playwright install chromium && npm run test:e2e` 本地跑 4 条（本机未下载浏览器，未实测）。
5. Chrome DevTools Lighthouse（移动 + 桌面）各跑一次，记录 LCP/CLS/TBT。

**已知限制**

- E2E 只覆盖不依赖外网的路径（地图瓦片来自第三方 CDN，不进 CI 冒烟）。
- Lighthouse 未在本机实测（需真实浏览器环境）。
- 地图 chunk 300 KB gzip 属预期（懒加载，不进首屏）。

**已知限制与下一步**

1. GitHub Pages 需在仓库 Settings → Pages 选择「GitHub Actions」后才会发布；仓库名决定 `PAGES_BASE`。
2. 迁移期不初始化远端、不提交 Android 侧改动；是否 push 由交付方决定。
3. M1 起：`domain/` 平移，用 399 条候选筛出可信用例写入 `test/fixtures/domain.json`，跑 `diff=0`。
4. 构建产物含 `.nojekyll`（`web/public/`），避免 Pages 忽略下划线目录。

### 13.16 B1 完成情况（地图闭环，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| **F-4 地图塌陷**（新发现，比 worker 更底层） | `.map-stage` 子元素全 absolute、自身无高度，后置规则又把 `position` 覆盖回 `relative` → `.maplibregl-map` 实测 **0px**，canvas 以默认 300px 溢出且 **不接收命中** | 修复后手机 **390×656**、桌面 **760×759**；Marker 命中测试由 `map-page` 变为 `.map-star` |
| G-01 搜索 | `MapSearch.tsx`（防抖 350ms、AbortController、↑↓/Enter/Esc、点外收起）+ `data/geocoding.ts`；`geocodingUrl` 增可选 `lang` 参数（默认 `zh`，向后兼容） | 候选「青岛 · 辽宁 · 中国」等 3 条；选中后 flyTo 并回报 hint |
| G-02 星标 | `MapFavorites.tsx`：Marker 只在 `map.on('load')` 后挂载（否则首次投影落 `(0,0)`，点击被浮层拦截）；气泡摘要读本地 `hourly_score` 未来 24h 最高分 | 1 个星标 → 气泡「青岛·示例 1 ／ 暂无窗口摘要」（无缓存不造假） |
| G-03 浮条 | 新增「查看收藏」；星标气泡内「查看详情」 | 点击后 `#/favorites`，h1「收藏」 |
| G-18 时段 | `filterByHourRange`（起点>终点视为跨午夜，左闭右开）+ 起止 `time` 输入 | 6 条单测覆盖：不限/单端/同日/跨午夜/不改入参 |
| 路由 | Tab 进 hash：`#/map`、`#/favorites`、`#/settings`（原先 tab 是 `AppShell` 内 state，无法深链、刷新丢位置） | 三档深链直达；`#/spot/:id` 不变 |
| 门禁 | typecheck 0；**103 passed**（+6 geocoding、+6 时段）；`check:parity` 572/572；build OK | 本机 |

**边界**：① 搜索数据源与 App 完全同源（Open-Meteo Geocoding `/v1/search`，`language=zh`），中国小地名覆盖差（如「万绿湖」返空）——这是源站限制，非实现缺陷；要更好搜索需另立跨端需求（Photon 正地理），不能单边改 Web。② Top10 仍依赖本地评分缓存，无窗口时 `Top10Strip` 不渲染，自定义时段入口随之不可见（B2 处理空态入口）。
### 13.17 B2 完成情况（详情 / 收藏，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| **F-5 超时**（新发现） | 客户端默认超时 30s + 重试 3 次 + 双源并行 → 最坏 ≈105s「加载中」；改为默认 **10s**（对齐 Android OkHttp），`getDetail` 加 **15s 整体预算** `withDeadline`，超时按失败降级 | 实测 **15 017 ms** 收敛，页面挂「部分维度不可用」，不再无限转圈 |
| G-04 数值表 | 逐时卡片内 `SegmentedTabs`（图表/数值表）+ `ui/charts/hourRows.ts` 纯函数 | 3 条单测：补零、五档冻结档位、保持顺序 |
| G-13 状态条 | `detail-status` 多标签：离线 / 48h 超期 / 分维降级 / 缓存回退；`online` 事件驱动 | 触发 `offline` 事件后同时出现「网络不可用」「部分维度不可用」 |
| G-05 收藏 | 新 `pages/FavoritesPage.tsx`（旧 `FavoritesPanel` 删除）+ `domain/favorites.ts`：四序（综合推荐/最近/分数/名称）、搜索、分组折叠、标签/分组编辑、多选批量、刷新全部 + 汇总、窗口摘要 | 22 条单测；UI 实测 2 卡、4 排序、分组「水库 + 未分组」、全选 2 项、刷新汇总、批量删除后空态 |
| 水体回写 | 首轮数据到达后把 `UNKNOWN` → `NEARSHORE` / `INLAND_WATER` 落库（此前永远 UNKNOWN） | 收藏卡 chip 实测显示 `NEARSHORE` |
| 门禁 | **128 passed**；parity 572/572；contrast 21 对 0 fail；预算总 gzip **440.4 KB**、0 项超 | 本机 |

**环境注记**：本机浏览器访问 `api.open-meteo.com` 极慢/偶发失败（`marine-api` 正常），Node 侧带代理则成功。这是网络环境而非实现差异——页面现在会在 15s 后明确降级，而不是一直加载。

### 13.18 B3 完成情况（设置与 i18n，2026-11-13）

| 项 | 结果 | 证据 |
|---|---|---|
| **i18n 全局化（真 bug）** | 原 `useLang` 是每个组件各自 `useState`，切换后只有设置页变语言，其余页面仍是旧语言；改为全局单例 + 订阅（`ui/i18n.ts`），并同步 `<html lang>` / `data-lang` | 切 English 后 `documentElement.lang=en`、`h1=Settings`、tab=`Map/Favorites/Settings`、地图按钮=`Saved spots`/`Save & open`、样式=`Color/Plain/Dark/Sea temp` |
| G-06 语言入口 | 设置页新增「显示 / Language」版块（中文 / English），持久化到 localStorage | 实测切换即时生效且全站同步 |
| G-07 底图提供方 | 新增 `ui/settings.ts`（localStorage）；`MapPage` 维护 `openfreemap` / `carto` 两组 style URL，切换提供方后按当前样式重载；设置页可切 | `STYLE_URLS` 两组 × 三样式；提供方变化监听 `providerRef` |
| G-09 法务与来源 | 设置页新增「隐私披露（逐源）」「开源许可与数据署名」「免责声明」三版块（分别 6 / 8 / 5 条，全部取自 Android 文案 key） | 无新增 key，parity 仍 572/572 |
| 清硬编码 | 导航三 tab、地图页全部可见文案（样式名/海温/定位/收藏并查看/查看收藏/长按提示/说明/aria）已双语化 | `L(zh, en)` 局部助手 + 已有 key |
| **G-08 Copernicus 凭证：未做** | Web 端没有 Copernicus OIDC token 流程，也没有接其数据链路；只做输入框等于假功能 | 记为跨端差异，待真有数据需求时两端一起做 |
| 门禁 | 128 passed；parity 572；预算总 gzip **441.8 KB**、0 项超 | 本机 |

### 13.19 B4 进度（详情深度，2026-11-13，部分完成）

| 项 | 状态 | 说明 |
|---|---|---|
| G-12 水质三卡 | **完成** | 水温 / 溶氧子分 / 浑浊度 三张独立卡，各带档位评价与样本数；移动单列、≥768px 三列 |
| G-10 原始输入清单 | 待做 | Android 16 行（Tw / DO_p / P / ΔP3 / ΔP24 / 风速 / 阵风 / 降水 / 云量 / WMO / 浪高 / 潮位 / dh/dt / 流速 / 浑浊 / 叶绿素），需先把水质派生函数从 `DetailTabs` 抽到 `domain` 再复用 |
| G-11 安全摘要卡 | 待做 | 依赖 veto 码与风浪阈值同源 |
| G-13 潮汐相位 / 校准 / 现场校潮 | 待做 | 依赖 `TideCalibrator` 平移与 `manual_tide_fit` |
| G-14 观测录入 | 待做 | `manual_observation` 表已在 `db.ts` 预留 |

### 13.20 用户反馈修复（2026-11-14）

| 问题 | 根因 | 修复与证据 |
|---|---|---|
| **详情页选项卡被顶出视口**（切「天气」后） | `.detail-page` 用的是 `min-height: 100dvh` + `overflow-y:auto`：内容一多元素被撑高，overflow 失效 → 变成整页滚动，选项卡随内容滚出 | 改 `height: 100dvh; min-height: 0`（embedded 档同步），选项卡 `position: sticky` 吸顶。实测：页面高度恒定 **844**，天气页内容 **1352** 在内部滚动，滚到底后 `tabsTop=0` 仍可见 |
| **钓鱼指数无结果** | 天气源 `api.open-meteo.com` 在本机浏览器 30s 超时（`marine-api` 正常），15s 预算后降级 → `scores` 为空，界面只显示「暂无逐时数据」，用户不知道原因 | 详情页新增空态卡：说明「依赖天气与海洋两源，被拦截/超时会一直为空」+ **重试按钮**（`reloadTick` → `getDetail(force)`）。实测点击后确实重发请求 |

**说明**：指数本身计算无问题（Node 侧同源实测 408h 天气 / 264h 海洋，与 Android 逐位一致）；浏览器侧为空是网络可达性问题，重试与关代理可恢复。

### 13.21 用户复核第二轮（2026-11-14）

| 问题 | 结论 |
|---|---|
| 选项卡仍被顶出 | 第一轮只修了「页面被内容撑高」，漏了**同一容器是 column flex**：长内容会把 `position: sticky` 的 `.ts-tabs` 压缩（实测 40px → 12px，文字被裁）。已加 `flex: none; min-height: 34px`。三档（390 / 900 / 1440）注入 24 张卡后复测：页面高度恒等于视口、滚动到底 `tabsTop=12 / tabsBottom=52`（完整 40px 可见），`scrollH≈2920` |
| 钓鱼指数仍为空 | 用户环境「天气页全是 —」与「指数无结果」**同源**：`scores` 为空 → `days` 为空 → `dayKey` 为空 → 天气页按日过滤后无行，全部显示 —。已在详情页加**源诊断卡**（天气源/海洋源是否 fetched + 有效小时数 + error），用于区分「没拿到」与「拿到但解析空」 |
| 版本与缓存 | `registerSW` 原来没有 `onNeedRefresh`，新 SW 会停在 waiting，用户可能长期跑旧壳；改为就绪即 `updateSW(true)` 自动接管重载。设置 → 版本 新增「清除缓存并重载」（清 CacheStorage + 注销 SW）。版本升到 **1.18.2**，用户已确认看到该版本 |

### 13.22 索引为空 / 布局的第二轮定位（2026-11-14）

| 问题 | 真因 | 修复 |
|---|---|---|
| **钓鱼指数为空（真 bug）** | `client.ts` 写的是 `this.fetchFn = options.fetchFn ?? fetch`。存成对象属性后，调用 `this.fetchFn(...)` 的 `this` 变成 client 实例，浏览器直接抛 `Illegal invocation` —— **请求根本没有发出**。之前测试偶尔成功，是因为 `addInitScript` 包了一层 `window.fetch`（箭头函数无 this），恰好绕过了这个问题 | `fetch.bind(globalThis)`。直测 client：**2 034 ms 返回 408 小时**，字段完整；详情页实测出分「很适合 · 模型 · 75 · 最佳窗 5:00–6:00」，图表 canvas 正常 |
| 选项卡被顶出（第二轮） | 容器是 column flex，长内容会把 sticky 选项卡压扁（40px → 12px） | `flex: none; min-height: 34px`；三档注入 24 卡复测：页面高度恒等视口，滚到底 tabs 40px 完整可见 |
| 用户为何「关代理也没用」 | 不是网络（App/OkHttp 与浏览器无关），也不是代理 | 与网络彻底无关；这是 JS 调用约定错误 |

**版本 1.18.3**：用户此前确认看到的 1.18.2 是问题版本，升版本号便于区分。设置 → 版本 里的「清除缓存并重载」可强制拿新包。

### 13.23 图表与评分卡对齐 Android（2026-11-14）

| 项 | Android 实现 | Web 修复 |
|---|---|---|
| 逐时指数图形 | `HourChart(mode = BAR)`：固定 0–100 轴、柱色 = `ScoreBand` 五档、≥80 连续段顶部叠「窗口」横条（EXPLOSIVE 色 + onColor 文字）、x 轴每 3 小时 | Web 原本是折线（LINE 语义），已重写为柱状：逐柱取 `resolveScoreBand().color`、0/25/50/75/100 固定刻度、≥80 连续段画窗口条、x 轴每 3 小时 |
| 评分卡档位指示 | `DetailScreen`：5 个等宽块（真实档位色 + 5→1 个鱼形图标 + 档位文字 8sp），选中档 `border 2dp tertiary` | Web 之前 `activeSegment` 传了但 **CSS 完全没实现**（5 个空 span，看起来一模一样）。现改为 `SCORE_BANDS` 固定渲染：每块真实色 + 5→1 个小方块 + 文字，选中档 `border 2px currentColor + 白光晕 + 上移 1px`；去掉淡化改 0.92 保持可读 |

**实测**：截图中评分 77 → 第二档「很适合」白框高亮；柱状图按档位分绿/黄/橙，Y 轴 0–100，X 轴 0/3/6…21。当天峰值 77 < 80，故无窗口横条（符合「不造假」）。

### 13.24 日期选择层级对齐（2026-11-14）

**App 层级**（`DetailScreen.kt` LazyColumn）：OfflineBanner → StaleBanner → 通知 → **DaySelector + 来源行** → Tab 分段控件 → 降级卡 → 指数主卡 → 图表/表格 → …。即日期选择是**页面级、在 Tab 之上、所有 Tab 共用**，不是每个 Tab 一份。chips 由 `DaySelectorLogic` 按「今天 + 未来 N-1 天」生成，**与数据无关**；某天没数据只是没有分数角标。

| 问题 | 结论 | 修复 |
|---|---|---|
| 有的 tab 有日期选择、有的没有 | Web 结构上本来就在 Tab 之上（层级正确），但 `days.length > 0` 才渲染，而 `days` 来自 `scores` —— 数据未到达/为空时整条消失 | chips 改为独立生成（今天 + 3 天，有数据的天补分数角标，无数据留空）；`DaySelector` 无条件渲染；选中无数据的天→该天空态，不自动跳走 |

**实测**：5 个 Tab（指数/潮流/天气/水质/风浪）逐个切换，`.ts-day__chip` 恒为 **3** 个（今天 / 11/16 / 11/17），与指数是否加载完成无关。

### 13.25 日期条与 Tab 合并吸顶（2026-11-14，客户要求）

- 客户裁定：「日期选择 + Tab 一起吸顶」。App 中两者都不吸顶（随 LazyColumn 滚走），此条属 **Web 增强**，记录在案。
- 实现：`.detail-sticky` 包住 `DaySelector` + 来源行 + `SegmentedTabs`（与 App 的层级顺序一致），`top: 0`、`flex: none`、`background: var(--color-bg)`、底部投影；`.ts-tabs` 取消自身 `sticky`，避免双层吸顶叠在一起。
- 实测（注入 24 张卡后滚到底）：mobile 390×844 与 desktop 1440×900 均为 `stickyTop=12 / stickyBottom=150`，日期条 top=18、Tab bottom=142，**全部可见**。

### 13.26 发布前清理：界面上的开发备注（2026-11-14）

| 位置 | 原内容（会露在产品界面） | 处理 |
|---|---|---|
| `AppShell` 每个 Tab 标题下 | `S01：地图选点 + 十字准星 + Top10（M6 接入 MapLibre）` / `S08：收藏列表（真实 IndexedDB 读写）` / `S09：设置（桌面两栏，移动单列）` | 删除 `TAB_HINT` 常量与渲染 |
| `DetailPage` 底部 | `桌面三栏布局在 M5 接入；当前为详情全屏。` | 删除，并移除不再使用的 `useEnvironment` 依赖 |
| `TideTab` | `本期未接入现场观测；校准入口与对照表在 M4b 后续 / Android 对齐需求中提供。` | 改为「暂时只能查看模型潮位；现场校潮与对照表将在后续版本提供。」（双语） |
| `WaterQualityTab` | `中国境内默认无实测水质源（M0 结论 A6/A7）；…pH 不提供（领域模型无字段）。` | 改为面向用户的「多数点位没有公开实测水质…标注估算，不显示实测徽标；不提供 pH」 |
| `Top10List` | `rank = 0.62·峰值 + 0.23·均值 + 1.4·min(时长,4)` | 删除公式行 |
| 设置页 | 「组件预览（内部验收）」整卡 | 删除（`PrimitivesPreview` 不再打包进主流程） |
| 设置页 | 语言卡「跨端契约要求未来 Android 同步」、皮肤卡「…仍为冻结值」 | 改为用户语言 |
| 地图页说明 | 「准星是唯一选点真源」 | 改为「拖动地图，把十字准星对准目标水域，再点收藏并查看」 |

**验证**：脚本遍历地图/收藏/设置 + 详情 5 个 Tab，检索 `S01：`/`S08：`/`M6 接入`/`M5 接入`/`M0 结论`/`M4b`/`内部验收`/`组件预览`/`rank = 0.62` → **0 命中**。E2E 同步更新（删除组件预览用例，改为「设置页无内部验收入口」）。

### 13.27 用户反馈第三轮：收藏对话框 / 返回定位 / 桌面收藏分栏（2026-11-14）

| 反馈 | App 依据 | 修复 |
|---|---|---|
| 逆地理未完成就能收藏，导致无名点 | `MainActivity.kt`：点收藏先弹 `ts_favorite_dialog_title`「收藏该点位」，名称可编辑（默认逆地理名，**留空则用坐标**），另显示坐标与水体 | 地图页改为弹 `BottomSheet`：名称输入（默认逆地理名/可编辑）、坐标行、水体「未判定（打开详情后自动判定）」、确认/取消；名称空 → `36.06000, 120.38000`。实测收藏「测试点-A」成功并在列表显示 |
| 从收藏返回地图停在默认位置 | `MapScreen.kt` UX-18：返回地图回到「上一查看点 / lastViewed 最新点」 | MapPage `load` 后读 `listFavorites()` 首项（按 lastViewedAt 降序）并 `jumpTo`。实测：搜索到杭州 30.293650,120.161420 → 收藏 → 回地图仍在杭州（此前会跳回青岛默认点） |
| PC 收藏夹展开列表挤占/覆盖左栏 | — | 桌面档收藏页改为 **左列表 380px + 右详情 `1fr`**：点卡片/「查看详情」只在右栏加载 `DetailPage embedded`，不跳路由、左列表不动。**顺带发现并删除** AppShell 里早期骨架遗留的「详情预览」占位 aside —— 它把 main 挤到 740px，正是列表被压窄的根因 |
| 卡片显示 `OCEAN`/`UNKNOWN` 协议值 | — | 水体改为可读标签（外海/近岸/内陆水域/陆地），`UNKNOWN` 不显示 |

**实测**：1440×900 下主导航 + 列表 380 + 详情 680 并排；点击卡片右侧标题「桌面点-1」，左列表卡片数保持 1。

### 13.28 标题行并入吸顶区（2026-11-14，客户要求）

- 客户要求：详情页「标题 + 坐标」那一行也固定。
- 实现：把 `<header class="detail-top">` 移入 `.detail-sticky`，吸顶区顺序变为：**标题/坐标 → 日期条 → 来源行 → Tab**（与 App 的层级顺序一致）。
- 实测（注入 24 卡后滚到底，mobile 390×844 / desktop 1440×900 一致）：吸顶区 `top=12 / bottom=207`；标题 18–67、日期 75–127、Tab 159–199，全部可见。
- 代价：吸顶区高约 **195px**（视口 23%）；桌面右栏 720 高时内容区剩 525px。若嫌占屏，可先把「来源行」移出吸顶区（省 22px）。

**另注**：并行会话又给 Android `strings.xml` 新增了 4 个文案 key（总数 580），Web 已按跨端契约重新生成并 parity 通过。

### 13.29 收藏卡片整卡可点击（2026-11-15，客户要求）

- 客户要求：手机模式下点收藏卡片任意位置都能进详情，不只是「查看详情」按钮。
- 实现：点击区域从 `fav-card__main` 提升到整个 `<article class="fav-card">`；`role="button"` + `tabIndex` + Enter/Space 键盘可达；**按钮命中除外**（`closest('button')` 时不触发，勾选/改标签/改分组/删除不受影响）；桌面档行为不变（右栏内嵌，不跳路由）。
- 实测：手机点摘要文字 → `#/spot/1`；点「改标签」→ 弹批量改标签 Sheet 且 URL 不变；桌面点摘要 → 右栏内嵌显示「桌面整卡点」，左列卡片保持。

### 13.30 水体类型全部误标「近岸」的修复（2026-11-15）

| 项 | 内容 |
|---|---|
| 现象 | 收藏列表所有点都显示「近岸」，但打开详情能看到正确的水体（内陆/海洋） |
| 根因 | 详情页的「水体回写」只在 `waterBody === UNKNOWN` 时执行，且判定条件是 `detail.marineAllNullInland`。当海洋数据**尚未到达或拉取失败**时，`marineAllNullInland` 为 false → 一律写成 `NEARSHORE`；之后再拿到真实数据也不会覆盖（已有值即跳过）。详情页用的是实时判定，所以看起来是对的 |
| 修复 | ① 只有 `fetchedAtMillis.marine != null` **且** `!marineDegraded` 才判定；② 判定结果与库内值不一致时**允许纠正**（Web 暂无手动设置水体入口，覆盖安全） |
| 验证 | 内陆点（杭州 30.293650, 120.161420）收藏 → 进详情 → 返回列表，chip 显示 **内陆水域**；青岛等海域点仍为「近岸」 |
| 历史数据 | 已经误标的点不会自动改：**进一次详情**或点收藏页「刷新全部」即会按新逻辑纠正 |

**并行改动提示**：本轮构建时发现 `web/src/ui/skin.ts` 被另一会话改动（皮肤扩到 4 套：`f-bluetide` / `a-vintage` / `d-fieldguide` / `e-tidejournal`，且 `f-cleartide` 作为旧值映射到 `f-bluetide`）。一次 `npm run build` 曾因读到半成品文件报 TS2367，重跑即通过。说明有并行会话在改 Web 皮肤，后续合并需注意冲突。
