# 稿 ↔ 代码映射（人读版）

> 机器可读版本：`design-to-code.json`。所有路径相对**仓库根目录**（`design-handoff/` 的上一级）。
> 本文件基于实际读取工程源码得出，不是推测。

## 0. 动手前先看这里

⚠️ **CONFLICT-01：工程有通用底部 NavigationBar，设计稿没有。**

`MainActivity.AppRoot` 用 `Scaffold(bottomBar = NavigationBar)` 渲染四项（地图 / 收藏 / 详情 / 设置，`TAB_ORDER` line 63、87–104）；v2 设计稿是「地图优先，顶部浮层 + 准星 + 底部信息条 + Top10 入口，无通用底部 Tab Bar」。

这会改变导航结构。**先由产品决策，不要按设计稿直接删底栏**：

- 方案 ①：工程移除 NavigationBar，改走地图顶部入口 + Top10 入口（贴合 §8.1 与设计稿）；
- 方案 ②：保留底栏，设计侧补一版带底部导航的屏组并更新 `screens/screens.json`。

## 1. Token 落点

| 设计 token | 工程单源 | 状态 |
|---|---|---|
| 明暗主题色 | `core/ui/.../theme/TideSpotTheme.kt` → `LightColors / DarkColors / TideSpotTheme` | 已有，`MainActivity.setContent` 已包 `TideSpotTheme` |
| 五档分数色 | `core/ui/.../band/ScoreBands.kt` → `ScoreBand` | 已有且冻结，`HourChart` / `FactorBreakdown` 已引用 |
| 六枚徽标 | `core/ui/.../badge/ConfidenceBadge.kt` → `Kind` | 已有且冻结，M6 已动态接线 |
| 全部文案 | `core/ui/.../strings/TideSpotStrings.kt` | 已有，FO-01 唯一入口 |
| 三套 Tab 标题 | `core/ui/.../titles/WaterBodyTabTitles.kt` → `titlesFor(WaterBodyType)` | 已有；`feature/map` 侧是 typealias 委托 |
| 字阶 / 间距 / 圆角 / 安全区 | 无集中对象 | 分散在 `MaterialTheme.typography.*` 与 dp 字面量。按本包值落地即可，**不要为了对齐设计去改主题色值** |

## 2. 组件落点速查

| 组件 | 工程位置 | 状态 |
|---|---|---|
| C01 准星 | `MapScreen.kt: CrosshairOverlay` + `crosshair/CrosshairStateMachine.kt` | 已有 |
| C02 顶部浮层 | `MapScreen.kt` 顶部 Row | 部分 |
| C03 信息条 | `MapScreen.kt: InfoBarPanel` + `infobar/InfoBarState.kt` | 已有 |
| C04 确认收藏对话框 | `MainActivity.kt: ConfirmFavoriteDialog` (line 234) | 已有（M6 OPT-M5-07） |
| C05 Top10 Sheet | `DetailScreen.kt: ModalBottomSheet` + `Top10SheetContent` (line 317)；`FavoritesScreen.kt: Top10Content` | 已有，两处口径需一致 |
| C06 免责 Sheet | `DetailScreen.kt: ModalBottomSheet` (line 304) + 常驻入口 (line 279/285) | 已有 |
| C07 DaySelector | `core/ui/.../selector/DaySelector.kt` | 已有 |
| C08 五维 Tab | `core/ui/.../titles/WaterBodyTabTitles.kt` | 已有 |
| C09 HourChart | `core/ui/.../chart/HourChart.kt` + `ChartMath.kt` | 已有 |
| C10 今日指数主卡 | `DetailScreen.kt: DailySummaryLine` + `ScoreBadgeDerivations.kt` | 已有 |
| C11 因子分解 | `core/ui/.../breakdown/FactorBreakdown.kt` | 已有 |
| C12 徽标 | `core/ui/.../badge/ConfidenceBadge.kt` | 已有 |
| C13 潮流图 | `DetailScreen.kt` + `TideChartDerivations.kt` + `HourChart` | 部分（dh/dt 副轴 / 校正对照需核对） |
| C14 校准面板 | `DetailScreen.kt: CalibrationSheetContent` (line 371) + `TideCalibrator.kt` | 已有 |
| C15 水质 2×2 | `DetailScreen.kt` 水质 Tab | 部分（卡高 / 空值表现需核对） |
| C16 FilterChip / Switch | `SettingsScreen.kt` + `SettingsLogic.kt` | 已有 |
| C17 离线陈旧条 | `DetailScreen.kt: StaleBanner` (line 425) | 已有 |
| C18 分维降级卡 | `core/domain/.../VetoEvaluator.kt` + `Models.kt` | 部分（逻辑在 domain，UI 卡需核对） |
| C19 收藏列表 | `FavoritesScreen.kt: FavoriteCard / EditSpotDialog` | 已有 |
| C20 搜索面板 | `feature/map/.../search/SearchPanel.kt` | 已有 |
| C21 地图署名 | `MapScreen.kt` 底图右下 | 部分 |
| C22 来源时间行 | `DetailScreen.kt: SourceFooter` (line 476) + `sourceInfo` (line 418) | 部分（见 GAP-02） |

完整 22 项及每项动作见 `design-to-code.json → components`。

## 3. 冲突清单

| ID | 级别 | 内容 | 决策人 |
|---|---|---|---|
| CONFLICT-01 | 高 | 底部 NavigationBar vs 地图优先导航 | 产品 / 用户 |
| CONFLICT-02 | 中 | 免责第 5 条：需求 §9.6 原文 vs 工程 D11「现场校潮」长文 | 产品 / 需求负责人 |
| CONFLICT-03 | 低 | 稿件「源更新 06:00 UTC」是示意值，工程因数据层未透出而显示「—」 | 数据层负责人 |

**CONFLICT-03 的实操含义**：`DetailScreen.sourceInfo` 注释已写明「源端更新时刻数据层未透出（上报项）→ 源更新显示 —」。实现时**禁止硬编码编造源更新时间**，显示工程透出的真实值或「—」。

## 4. 缺口清单

| ID | 缺口 | 现状 |
|---|---|---|
| GAP-01 | APP 图标 | `AndroidManifest.xml:11` 仍为 `@android:drawable/sym_def_app_icon`；SVG 源稿已在本包 |
| GAP-02 | F-3.7 模型名 / 网格分辨率 / UTC 更新时刻 | 数据层未透出，登记 v1.1 |
| GAP-03 | Android 16 edge-to-edge 显式 insets | 全仓 main 源码无 `WindowInsets` / `enableEdgeToEdge`；依赖 Scaffold + NavigationBar 默认 padding。若移除底栏必须补 `navigationBarsPadding` |
| GAP-04 | 集中化尺寸 token | 无；可选重构 |
| GAP-05 | 17 条 `design_proposed` 文案 | 工程未定义，确认后统一补进 `TideSpotStrings.kt` |
| GAP-06 | 全套深色像素稿 | 本包给 DV01–DV03 样张 + DV04 全屏换色规则 |
| GAP-07 | S03–S08 像素精稿 | 本包给按规格推导的 SVG 结构稿 |

## 5. 给实现方的落地顺序

1. 读 `INDEX.md` → `manifest.json` → `mapping/design-to-code.json` 的 `conflicts`，先解决 CONFLICT-01。
2. 读 `tokens/design-tokens.json`，确认色值一律取工程单源，不新建色值。
3. 按 `screens/screens.json` 的 `layers[].c` 逐组件核对 `components/components.json` 的状态覆盖。
4. 文案一律从 `copy/copy.json` 的 `keys` 查；`frozen` 引用工程常量，`design_proposed` 需产品确认。
5. 图标按 `icon/app-icon-spec.md` 转 vector drawable 并替换 `AndroidManifest.xml`。
6. 按 `acceptance/acceptance.md` 逐项验收；改 UI 前跑现有测试（M6 退出快照 573 测试通过），保持零行为漂移。

## 6. 红线

- 色值改动只允许发生在 `ScoreBands.kt` / `ConfidenceBadge.kt` / `TideSpotTheme.kt`；**本包不授权修改这三个文件的取值**。
- 文案改动走 `TideSpotStrings.kt`，注意 `TideSpotStringsTest` 断言免责恒 5 条。
- 不新增 XML layout（项目禁 XML layout，全部 Compose）；图标属 drawable/mipmap，不在此限。
- 设计稿中的示例分数、示例时间、示例点位是版式示意，**不是业务数据**。
