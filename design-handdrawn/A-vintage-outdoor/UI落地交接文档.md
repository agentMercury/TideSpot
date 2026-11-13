# 潮点 TideSpot · 手绘 A UI 落地交接文档

> **面向对象**：接手把「手绘 A · 复古户外 30 屏」真正落地到 `tidespot-android/` 的后续 Agent / 工程师。
> **设计基线**：`design-handdrawn/A-vintage-outdoor/`（30 屏 / `spec.json` / `SKIN-DESIGN-SPEC.md`）。
> **工程基线**：v1.16.0（versionCode 20），模块结构见 `tidespot-android/CONTRACT.md`。
> **日期**：2026-11-13 ｜ **性质**：落地指引（现状 + 目标架构 + 分步路线 + 逐屏映射 + 验收 + 坑），**不含可编译代码**。
> **配套**：换肤怎么做看 `SKIN-DEV-STANDARD.md`；还剩什么没做看 `未开始需求清单.md`。

---

## 0. 一句话结论：可执行性判断

| 视角 | 结论 | 依据 |
|---|---|---|
| **作为「设计规格」** | ✅ **可执行** | 30 屏齐全、token 清单明确（`SKIN-DESIGN-SPEC.md` §2）、冻结契约成文、主题层已在**详情页真机试点跑通**、纸张/鱼形资源可脚本复现生成（`gen_android_assets.py`） |
| **作为「可直接上线的代码」** | 🟡 **已具备（真机待验）** | 2026-11-13：`TideSpotSkin` + 注册表 + `LocalSkin` 已落地；`TideSpotTheme(skin, water, darkTheme)` 全 App 生效；`skinId` / `themeMode` 持久化；设置页皮肤下拉 + 明暗三选；3 页与叠层继承皮肤；四态分级色已实现。剩余：真机逐屏复拍（USB 待恢复） |
| **落地工作量** | 中等偏上 | 重心在三件事：① token 抽象层；② 全屏「去硬编码色、改从主题取」；③ 3 个顶层页铺开 + 换肤选择页 |

> **一句话给用户**：**能落地，但不是「拿图即上线」**——图已经是可信的设计规格，代码侧还差「把 token 抽出来 + 把 3 页接上 + 加换肤入口」这三步，按 §4 路线走即可。

---

## 1. 资产清单（设计侧已交付）

| 资产 | 路径 | 说明 |
|---|---|---|
| 30 屏 PNG | `screens/`(7) + `screens_full/`(23) | 780×1688 @2x，全部 780×1688 校验通过 |
| 自包含 HTML 源稿 | `sources/`(7) + `sources_full/`(23) | 无外链、无相对路径 `<img>`、内联手绘滤镜 |
| 规格 | `spec.json` | 屏清单 + SHA-256 + 冻结契约 + `skin_system` |
| 皮肤契约 | `SKIN-DESIGN-SPEC.md` | 三轴正交 / 可变 vs 冻结 token / 接入清单 A1–A10 |
| 索引页 | `index-full.html` | 30 屏分组拼览 + 需求摘要 + 参照稿勘误（旧稿详情已加「非落地目标」水印） |
| 需求 | `APP功能优化需求.md` + `feature-gaps.json` | 9 条差异（7 转需求 / 2 仅记录） |
| 生成器 | `generator/engine.py`（base 7）+ `engine_full.py`（23）+ `gen_full_index.py` + `selfcheck_full.py` + `gen_android_assets.py` | 改设计后重跑即可复现 |
| 自检 | `selfcheck_full.py` | **当前 276 PASS / 0 FAIL**（30 PNG + 23 HTML + 冻结文案 + 深色/地图三色固化） |
| 对拍/落地 | `device-compare/真机对拍报告.md`、`Compose试点落地说明.md` | 试点证据与边界 |

---

## 2. 工程现状（真实代码，截至 v1.16.0）

> ⚠️ **本节是 2026-11-13 开工前的基线快照**（用于记录「改造前长什么样」）。其中「仅详情页换皮 / 无皮肤抽象 / 无 skinId 与 themeMode」等断言**已被 #89 之后的实现取代**。
> 当前架构请看 §2.5 状态表 + `core/ui/theme/TideSpotSkin.kt`、`TideSpotTheme.kt`、`SKIN-DEV-STANDARD.md` v1.3；不要用本节判断现有代码。

> 结论先行（**开工前**）：详情页已试点换皮（唯一），其余全部是基础 Material 3 主题。

### 2.1 模块与依赖方向（`CONTRACT.md` §模块与依赖方向）
```
:app → :feature:map / :feature:detail / :feature:favorites / :feature:settings
feature:* → :core:data → :core:domain（纯 Kotlin JVM，无 Compose 依赖）
:core:ui —— 主题与质感层（被 feature 引用）
```

### 2.2 主题与质感现状

| 文件 | 现状 | 关键点 |
|---|---|---|
| `core/ui/.../theme/TideSpotTheme.kt` | 全局 M3 主题，`darkTheme = isSystemInDarkTheme()`，固定 Light/Dark `ColorScheme` + `TideSpotShapes` + `TideSpotTypography` | **不感知皮肤**；只有跟系统明暗 |
| `core/ui/.../theme/VintageOutdoorTheme.kt` | **详情页试点的皮肤主题**：海/陆 × 浅/深 4 套 `ColorScheme` + `VintageShapes`（非对称圆角）+ `VintageTypography`（只换衬线字族、字号行高与 `TideSpotTypography` 全等） | 仅包裹详情子树，**不影响另外 3 页** |
| `core/ui/.../theme/HandDrawn.kt` | 质感 Modifier：纸颗粒 / 套印错位 / 手绘描边 / 鱼形装饰 | **颜色无关**（颜色由调用方从 `MaterialTheme` 取），保证三轴正交 |
| `core/ui/src/main/res/drawable*` | `ic_fish_vintage.xml` / `ic_mount_vintage.xml` / `ic_wave_vintage.xml` / `paper_grain_light.png` / `paper_grain_dark.png` | 由 `generator/gen_android_assets.py` 生成 |

### 2.3 应用点（谁套了主题）
- `app/.../MainActivity.kt:87`：`TideSpotTheme { ... }` 包裹**全部**页面。
- `app/.../MainActivity.kt:357`：`VintageOutdoorTheme(waterBody = waterBody, darkTheme = dark) { ... }` **只包详情页**。

### 2.4 设置与持久化现状
- **（开工前）** `SettingsKeys` 只有 `UNIT_SYSTEM / API_KEY / MAP_STYLE`，无 `skinId` / `themeMode`；**现已落地** `SKIN_ID` / `THEME_MODE`（`SettingsState.kt:124/125`），且 `API_KEY` 已按 OPT-A-02 下线。
- `SettingsScreen.kt:157` 仍有「用例 / 物种 / 权重」占位；`:165` 仍有「API Key（占位）」；**无「外观 · 皮肤」入口**；深色只有地图样式三选（`:113`），**无应用内明暗三选**。
- `AppSettings.kt`：纯函数映射 `SettingsState` ↔ SharedPreferences；新字段照此模式加。

### 2.5 已落地 vs 未落地（一眼看清）
| 能力 | 状态 |
|---|---|
| 详情页换手绘 A（浅/深 × 海/陆 4 套色板 + 非对称圆角 + 衬线字族 + 纸张/鱼形质感） | ✅ 已试点（`VintageOutdoorTheme`） |
| 地图 / 收藏 / 设置 换手绘 A | 🟡 代码已铺开（继承全局皮肤），真机待验 |
| 皮肤抽象层（`TideSpotSkin` + 注册表 + `LocalSkin`） | ✅ 已落地（`core/ui/theme/TideSpotSkin.kt`、`LocalSkin.kt`） |
| 应用内明暗三选（OPT-A-03） | ✅ 已落地（设置页「深色模式」三选，`ThemeMode` 持久化；底图在用户未改过样式时随深色自动切 Dark Matter） |
| 换肤入口（OPT-A-09） | ✅ 已落地为**设置页「皮肤」下拉**（按名字选，无独立选择页） |
| 叠层（Top10/免责/确认收藏/水质录入/星标气泡/删除确认） | 🟡 已随 `MaterialTheme` 继承皮肤，真机待验 |
| 四状态态视觉分级（OPT-A-05） | ✅ 已实现（离线 `secondaryContainer` / 陈旧 `onSurfaceVariant` / 48h `error` / 低置信冻结徽标） |
| 设置页下线 用例/物种/权重、API Key（OPT-A-01/02） | ✅ 已下线（含历史 Key 清理） |
| 地图页 Top10 入口语义对齐（OPT-A-04） | ⏸ 延后（本次不做，功能改动） |

---

## 3. 目标架构（落地后应长成什么样）

### 3.1 三轴正交（不可破坏）
`最终视觉 = 皮肤 token(水体) × 明暗模式`；三轴各自持久化，任一轴切换不重置另外两轴。
- **皮肤**：用户偏好（内置 A 复古户外 / D 路亚图鉴）
- **水体**：点位数据决定（海/陆），用户不可切
- **明暗**：跟随系统 / 浅 / 深

### 3.2 分层建议
```
core/ui/theme/
  TideSpotSkin.kt        ← 皮肤 token 数据类（色板/字体/形状/质感/母题/深色），A 实例 + 注册表
  LocalSkin.kt           ← CompositionLocal（让任意 Composable 取当前皮肤）
  TideSpotTheme.kt       ← 改造：TideSpotTheme(skin, water, darkMode) 派生 ColorScheme/Shapes/Typography
  VintageOutdoorTheme.kt ← 退役为 A 的一层薄封装（或直接由 skin 派生），保留 pilot 兼容
  HandDrawn.kt           ← 不变（颜色无关）
```
**数据流**：`SettingsState(skinId, themeMode)` + 点位 `waterBody` → `MainActivity` 读 → `TideSpotTheme(skin=byId(skinId), water=waterBody, darkMode=resolve(themeMode))` → 各 Composable 只从 `MaterialTheme.colorScheme` / `LocalSkin` 取。

### 3.3 冻结单源（皮肤不能碰）
`ScoreBands.kt`（五档）、`ConfidenceBadge.kt`（六徽标）、`TideSpotStrings.kt`（文案）、`WaterBodyTabTitles`（Tab 序）——**直取，不进皮肤 token**。

---

## 4. 分步落地路线（每步可独立验收）

> 原则：**先抽象、再铺开、最后换肤 UI**；不要先改页面再补抽象（会反复返工）。

| 阶段 | 目标 | 主要改动 | 验收 |
|---|---|---|---|
| **P0 · token 抽象** | 把 A 的 token 从 `VintageOutdoorTheme.kt` 抽成 `TideSpotSkin` 数据类 + 注册表 + `LocalSkin` | `core/ui/theme/TideSpotSkin.kt`（新）、`LocalSkin.kt`（新）；`VintageOutdoorTheme` 改为读 skin 派生 | 详情页视觉**像素级不回归**（对比 pilot 截图）；`TideSpotSkins.all` 可从注册表取到 A |
| **P1 · 全局注入 + 持久化** | `TideSpotTheme(skin, water, darkMode)` 全 App 生效；`skinId`/`themeMode` 落盘 | `TideSpotTheme.kt` 加参；`SettingsState`/`SettingsKeys`/`AppSettings` 加 `skinId`、`themeMode`；`MainActivity` 读取注入 | 切明暗/换皮肤全屏一致；冷启动回放；三轴互不重置 |
| **P2 · 3 页铺开** | 地图 / 收藏 / 设置 换手绘 A（**去硬编码色**） | `feature:map/MapScreen.kt`、`feature:favorites/*Screen.kt`、`feature:settings/SettingsScreen.kt`：所有硬编码 `Color(...)` 改 `MaterialTheme.colorScheme.*` / `LocalSkin` | 逐屏走查无漏切；地图页保持 CARTO 真实瓦片 + 只换周边组件样式 |
| **P3 · 叠层/状态/深色全套** | Top10/免责/确认收藏/水质录入/星标气泡/删除确认 + 四状态态 + 深色 detail/map/favorites | 对应 Sheet/Dialog/Banner 套 token；`StaleBanner` 扩 state 入参 | 叠层不遮挡 Tab/DaySelector；四态语义等级正确；深色对比度 ≥4.5:1 |
| **P4 · 换肤入口** | 设置「皮肤」下拉（A/D 两款）+ 明暗三选（**无独立选择页**） | `feature:settings` 皮肤下拉；`skinId` 持久化 | 入口可达；两款可选；切换即时生效；三轴正交 |

> **P0 是硬前置**：不先抽象 token，直接改页面会导致「每加一个皮肤改一处页面」。详见 `SKIN-DEV-STANDARD.md`。

---

## 5. 30 屏 → 工程落点映射

> 说明：**按模块/组件粒度映射**，不承诺结构 1:1（试点口径见 §7）。详情页为新稿骨架（82/苔绿），`detail-sea/inland` 是早期旧稿（78），仅作历史视觉、**不落地**。

### 5.1 基础主屏（screens/ 7）
| 设计屏 | 对应工程 | 现状 | 落地要点 |
|---|---|---|---|
| `map-sea` / `map-inland` | `feature:map` `MapScreen.kt` | 基础主题 | 周边组件（搜索/工具/准星/底卡）套 token；**底图用 CARTO 真实瓦片，不重画** |
| `favorites` | `feature:favorites` `FavoritesScreen.kt` | 基础主题 | 列表行/筛选/批量栏套 token |
| `settings` | `feature:settings` `SettingsScreen.kt` | 基础主题 + 有 用例/API Key 占位 | 套 token；**同时执行 OPT-A-01/02 删除 + OPT-A-09 入口** |
| `dark-detail` | `feature:detail` | 已随 pilot 深色 | 校验深色 token 与 `DARK` 一致 |
| `detail-sea` / `detail-inland` | — | **历史旧稿，不落地** | 落地目标用 `detail-tab-score-sea/inland`（新稿） |

### 5.2 详情 Tab（screens_full/ 9）→ `feature:detail/DetailScreen.kt`
`detail-tab-score-sea/inland`（钓鱼指数首 Tab）、`detail-tab-tide-sea/inland`（潮流/水位）、`detail-tab-weather`、`detail-tab-water`（1×3）、`detail-tab-wave-sea/inland`（风浪/湖面风）、`detail-breakdown`（因子分解）。
→ **复用工程现有 5 Tab 骨架与 `DetailScreen`/`Derivations`，只套 token**；不重排结构、不改 Tab 序。

### 5.3 叠层（6）→ 工程既有 Sheet/Dialog
`ov01-top10`（Top10 Sheet）、`ov02-disclaimer`（免责 5 条）、`ov03-confirm-favorite`（收藏确认）、`ov04-water-entry`（`WaterQualityEntrySheet.kt`）、`ov05-star-bubble`（`MapScreen.kt` 星标气泡）、`ov06-delete-confirm`（删除二次确认）。
→ 套 token + 手绘质感；文案取 `TideSpotStrings`。

### 5.4 状态态 + 空态/批量（6）→ `DetailScreen` 状态条 + `FavoritesScreen`
`state-offline/stale/unusable-48h/degraded`（扩 `StaleBanner` 四态分级）、`favorites-empty`、`favorites-multiselect`（`FavoritesBatchLogic`）。

### 5.5 深色变体（3）
`dark-detail`（detail）、`dark-map`、`dark-favorites` → 由 `skin.darkTokens[water]` 派生，**非机械反转**。

---

## 6. 冻结契约（落地/换肤均不得改）

逐项见 `APP功能优化需求.md` §5 与 `SKIN-DESIGN-SPEC.md` §2.2，摘要：
1. Tab 渲染序；2. 免责恒 5 条；3. 五档/六徽标 = `ScoreBands.kt`/`ConfidenceBadge.kt` 单源；4. DaySelector 52dp、水质 1×3（pH 已删）；5. 地图优先、无底部 NavigationBar、准星唯一真源；6. 双水体六重区分；7. 文案取 `TideSpotStrings`；8. 示例数据非业务值。

> 口诀：**换「长什么样」✅；换「是什么 / 有几个 / 叫什么 / 排第几」❌。**

---

## 7. 验收口径（重要：不要拿旧稿当基准）

- **口径**：沿用对拍报告 §0——「**配色 token 层 + 功能入口红线**，**不含结构/布局 1:1**」。手绘纸纹/套印在 Compose 是**近似还原**。
- **工程侧**：`tidespot-android/verify.ps1` 一键验证（含各模块 test）；Gradle 锁超时等 60s 重试。
- **设计侧**：`generator/selfcheck_full.py` **276 PASS / 0 FAIL**（改设计后必须重跑）。
- **逐屏走查**：换肤后详情/地图/收藏/设置/叠层/状态/深色全部过一遍，确认无单屏漏切、无硬编码色残留。
- **对比度**：冻结语义色与状态色浅/深均 ≥4.5:1。
- **六重区分**：换肤后海洋/内陆仍一眼可辨。

---

## 8. 常见坑（真实踩过）

1. **硬编码色 → 换肤漏屏**：`feature:*` 仍有 `Color(0x...)` 直接写在 Composable 里；换肤只改主题不会生效。**P2 必须逐个替换为 `MaterialTheme.colorScheme.*`**。
2. **字体缺失 → 行高/换行漂移**：`VintageTypography` 故意只换字族、字号行高与 `TideSpotTypography` 全等；中文回退系统 serif。**别为了「更像手绘」改字号行高**。
3. **地图底图不能 1:1**：设计稿是静态 SVG 装饰 + 「拖动选点」误导文案；工程是 CARTO 真实矢量瓦片。地图只做「样式三选 + 周边组件套 token」，**不重画底图**。
4. **深色 token 独立**：`DARK` 是独立色板，不是浅色的机械反转（对比度、层次都单独调过）。
5. **pilot 越界**：`VintageOutdoorTheme` 只包详情子树；铺开时要改成全局 `TideSpotTheme(skin,...)`，别把 4 套色板硬塞进每页。
6. **主题嵌套**：外层 `TideSpotTheme` + 内层皮肤主题同时存在时，注意「谁覆盖谁」；统一为单层 skin 派生最稳。
7. **`/d/` 路径 & Windows Python**：生成器里用 `Path(__file__)` 解析，勿手写 `/d/...`（Windows Python 不认 `/d/`）。
8. **不要动 B/C**：B 拟人鱼绅士 / C 水彩几何鱼仍是独立风格探索，**不是皮肤、不并入**；D 手绘鱼种图鉴**已皮肤化**（`d-fieldguide`，设计 `D-field-guide/` 30 屏）。详见 `SKIN-DEV-STANDARD.md` §7。

---

## 9. 快速开始（命令）

```bash
# 1) 生成设计 HTML（改设计后）
cd design-handdrawn/generator && python3 engine_full.py

# 2) Edge 渲图（2x）
#   msedge --headless --force-device-scale-factor=2 --window-size=390,844 \
#          --screenshot=<win_abs_png> <file:///win_abs_html>
#   注意：--screenshot 与 file:// 都用 Windows 绝对路径；地图/深色屏改后要重渲对应 PNG

# 3) 重建索引 + 自检
python3 gen_full_index.py && python3 selfcheck_full.py   # 期望 276 PASS / 0 FAIL

# 4) 工程侧验证（Android）
cd ../../../tidespot-android && ./verify.ps1
#   Gradle：仓库 .tools/gradle-8.9/bin/gradle.bat（或 CONTRACT 记的 D:\tools\gradle-8.9）
```

---

## 10. 关联文件索引

| 主题 | 文件 |
|---|---|
| 皮肤契约（设计侧） | `SKIN-DESIGN-SPEC.md` |
| 皮肤开发标准（执行版） | `SKIN-DEV-STANDARD.md` |
| D 皮肤设计稿（30 屏） | `../D-field-guide/`（`screens/` + `screens_full/` + `spec.json` + `index-full.html`） |
| 未开始需求 | `未开始需求清单.md` |
| 功能需求 | `APP功能优化需求.md` / `feature-gaps.json` |
| 试点与边界 | `device-compare/真机对拍报告.md` / `Compose试点落地说明.md` |
| 主题代码 | `tidespot-android/core/ui/.../theme/{TideSpotTheme,VintageOutdoorTheme,HandDrawn}.kt` |
| 设置持久化 | `tidespot-android/app/.../AppSettings.kt`、`feature/settings/.../SettingsState.kt` |
| 模块契约 | `tidespot-android/CONTRACT.md` |
