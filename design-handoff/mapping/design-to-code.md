# 稿 ↔ 代码映射（人读版）· v3

> 机器可读版本：`design-to-code.json`（v3.0.0）。所有路径相对**仓库根目录**（`design-handoff/` 的上一级）。
> 基线：**工程 v1.16.0（versionCode 20）**。本文件基于 2026-11-11 实际核对工程源码与 `.shots/` 真机实拍得出，不是推测。

## 0. 动手前先看这里（v2 阻塞项已全部关闭）

v2 的 **3 项 CONFLICT + 7 项 GAP 已全部关闭**，v3 **无阻塞性冲突**，可以直接按本包做视觉美化。关键变化：

| v2 标「未决/缺失」 | v1.16.0 实际 |
|---|---|
| CONFLICT-01 底部 NavigationBar vs 地图优先 | ✅ 工程已移除底栏，`AppRoot = Scaffold + when(page)`，地图优先口径成立 |
| GAP-01 APP 图标系统默认 | ✅ 已落地 `@mipmap/ic_launcher`，三层 drawable + round 五件齐全 |
| GAP-03 edge-to-edge insets | ✅ `contentWindowInsets = WindowInsets(0,0,0,0)`，各屏自行 padding；P2 视觉遗留（地图未铺满系统栏区）随下次批次 |
| GAP-04 集中化尺寸 token | ✅ `TideSpotShapes` + `TideSpotTypography` 已定义并注入 MaterialTheme |
| GAP-05 17 条文案 | ✅ 17/17 入资源并接线，中英 241/241 对齐 |
| 决策① API Key/Copernicus | ✅ 用户裁定正式启用，真实可保存 |
| 决策② 准星形态 | ✅ 用户裁定按设计：四向刻度 + 细环 + 薄荷点三态（需改 Canvas） |
| 决策③ GMS 口径 | ✅ 用户裁定以工程为准：双通道定位，验收 AAK-AN00 / Android 16 |

**唯一 P2 遗留**（不阻塞本次美化）：地图未全屏铺入系统栏区（纯色条与底图同色不可见）；见 JSON `open_items`。

## 1. Token 落点

| 设计 token | 工程单源 | 状态 |
|---|---|---|
| 明暗主题色 | `core/ui/.../theme/TideSpotTheme.kt` → `LightColors / DarkColors` | 已有；`MainActivity.setContent` 已包 `TideSpotTheme` |
| Shapes（8/12/16/20/20）| 同文件 → `TideSpotShapes` | **已有**（v2 列为建议，现升为既有 token） |
| Typography（20/16/14/15/13/12/12/12/10）| 同文件 → `TideSpotTypography` | **已有** |
| 五档分数色 | `core/ui/.../band/ScoreBands.kt` → `ScoreBand` | 已有且冻结 |
| 六枚徽标 | `core/ui/.../badge/ConfidenceBadge.kt` → `Kind` | 已有且冻结，M6 动态接线 |
| 全部文案 | `core/ui/.../strings/TideSpotStrings.kt` | 已有，FO-01 唯一入口，中英对齐 |
| Tab 渲染序 / 标题矩阵 | `core/ui/.../titles/WaterBodyTabTitles.kt` → `titlesInTabOrder` | 已有，恒 5 项 |
| DaySelector 52dp 两行 | `core/ui/.../selector/DaySelector.kt` | 已有（OPT-V12-04 / CHG-57） |
| **v3 美化 token**（层级阴影 / 薄荷点缀 / 准星三态 / 主卡渐变）| 无工程 token | 设计侧新增，**只改视觉不动 colorScheme 冻结角色**；可按需另起扩展 token 并同步本包 |

## 2. 组件落点速查（25 项）

| 组件 | 工程位置 | 状态 / v3 动作 |
|---|---|---|
| C01 准星 | `MapScreen.kt: CrosshairOverlay`（Canvas） | visual_tune：加细环 + 薄荷点三态，改 Canvas |
| C02 顶部浮层 | `MapScreen.kt: TopBarAction` | existing：L2 软阴影 |
| C03 信息条 | `MapScreen.kt: InfoBarPanel` | existing：236dp 浮条 + 操作面板 |
| C04 确认收藏对话框 | `MainActivity.kt: ConfirmFavoriteDialog` | existing：保存中态 |
| C05 Top10 Sheet | `DetailScreen.kt: Top10SheetContent` + `Top10ViewModel.top10Filtered` | existing：改时段筛选四 Chip + 自选弹层 |
| C06 免责 Sheet + 入口 | `DetailScreen.kt: ModalBottomSheet` + 底部 Surface | visual_tune：入口条 surfaceVariant 底 |
| C07 DaySelector | `core/ui/.../selector/DaySelector.kt` | existing：52dp 两行 |
| C08 五维 Tab | `WaterBodyTabTitles.kt` + `DetailScreen` | visual_tune：选中胶囊填充 |
| C09 HourChart | `core/ui/.../chart/HourChart.kt`（Canvas 自绘） | visual_tune：≥80 改薄荷窗格 |
| C10 今日指数主卡 | `DetailScreen.kt: DailySummaryLine` | visual_tune：brush 渐变 + 柔光 + 56sp + 色带选中高亮 |
| C11 因子分解 | `core/ui/.../breakdown/FactorBreakdown.kt` | existing |
| C12 徽标 | `core/ui/.../badge/ConfidenceBadge.kt` | existing |
| C13 潮流图 | `DetailScreen.kt: TideChartDerivations` | existing：客户裁定以现状为准 |
| C14 校准面板 | `DetailScreen.kt: CalibrationSheetContent` + `TideCalibrator.kt` | existing |
| C15 水质 1×3 | `DetailScreen.kt` 水质卡（height 112.dp） | existing：删 pH |
| C16 设置 / 凭据 | `SettingsScreen.kt` + `CopernicusCredentialStore` + `OpenMeteoClient` | visual_tune：六版块单卡 + 凭据区真实化视觉 |
| C17 离线/陈旧/48h | `DetailScreen.kt: StaleBanner` + `PullToRefreshBox` | existing：三态配色 + 刷新反馈 |
| C18 分维降级卡 | `core/domain/.../VetoEvaluator.kt` | existing：禁 parse reason |
| C19 收藏列表 | `FavoritesScreen.kt: FavoriteCard / EditSpotDialog / FavoritesSortLogic` | visual_tune：白卡 + 左档位色带 |
| C20 搜索面板 | `feature/map/.../search/SearchPanel.kt` | existing |
| C21 地图署名 | `MapScreen.kt` 右下 | existing：Voyager + 「—」 |
| C22 来源时间行 | `DetailScreen.kt: SourceFooter`（:app 透传 fetchedAt） | existing：未透出显示「—」 |
| C23 星标层 + 气泡卡 | `feature/map/.../FavoriteStarLayer.kt` | existing：点星标不动准星 |
| C24 水质录入 Sheet | `DetailScreen.kt` 录入 Sheet | existing：全展开按钮常驻 |
| C25 内陆引导卡 | `DetailScreen.kt`（:app 注入） | existing：左边 4dp 薄荷条 |

完整 25 项及每项动作见 `design-to-code.json → components`。

## 3. 已关闭决策（供追溯，无需再决策）

| ID | 裁定 / 结果 |
|---|---|
| CONFLICT-01 | 方案①已落地：移除底栏，地图优先，`Scaffold + when(page)` |
| CONFLICT-02 | 免责第 5 条以工程 D11「现场校潮」为准；「未校正」由徽标承载 |
| CONFLICT-03 | 源更新时间未透出显示「—」，禁止硬编码 |
| DECISION-API-KEY | 用户①正式启用：API Key + Copernicus 凭据真实可保存 |
| DECISION-CROSSHAIR | 用户②按设计：四向刻度 + 细环 + 薄荷点三态，需改 Canvas |
| DECISION-GMS | 用户③以工程为准：双通道定位，验收 AAK-AN00 / Android 16 |
| DECISION-V3-BEAUTY | 用户确认四项美化全采用；冻结色一格未动 |

**CONFLICT-03 实操含义**：`DetailScreen.sourceInfo` 已注明「源端更新时刻数据层未透出 → 显示 —」。实现**禁止硬编码编造源更新时间**，`attribution.map/.model` 的 `{0}` 用 `:app` 透传的真实 `fetchedAt`。

## 4. 遗留项（不阻塞）

| ID | 内容 | 处理 |
|---|---|---|
| P2-01 | 地图未全屏铺入系统栏区 | 随下次 UI 批次：`MainActivity` Scaffold insets + `MapScreen` 全屏；1–2h，改后跑 map/app 测试 |
| P2-02 | 英文真机验收 | 设备缺席，随真机批次；i18n 锁测试已在位 |
| FO-03 | v3 美化 token 集中化 | 可选：另起设计扩展 token（brush/阴影/点缀），并同步本包 |

## 5. 给实现方的落地顺序

1. 读 `INDEX.md` → `manifest.json` → `mapping/design-to-code.json`；确认无阻塞冲突（v2 的已全关）。
2. 读 `tokens/design-tokens.json`：色值一律取工程单源，v3 美化只动视觉不动冻结色。
3. 按 `screens/screens.json` 的 `layers[].c` 逐组件核对 `components.json` 状态覆盖；对照 `assets/v3/` 基准屏。
4. 文案从 `copy/copy.json → keys` 查；`frozen` 引用工程常量，Tab 序取 `titlesInTabOrder`。
5. 图标按 `icon/app-icon-spec.md` 把 v3 SVG 转 vector drawable，**替换**现有三层内容（manifest 指向无需改）。
6. 按 `acceptance/acceptance.md` 逐项验收；改 UI 前跑现有测试，保持零行为漂移。

## 6. 红线（v3）

- 色值改动只允许发生在 `ScoreBands.kt` / `ConfidenceBadge.kt` / `TideSpotTheme.kt`；**本包不授权修改这三个文件的取值**。
- v3 美化只动视觉：三级层级 / 薄荷点缀 / 字阶 / 准星三态 / 主卡渐变 / 收藏白卡色带 / 深色独立设计；**不动功能、结构、文案、Tab 序、导航流**。
- 文案改动走 `TideSpotStrings.kt`，`TideSpotStringsTest` 断言免责恒 5 条必须保持通过。
- 不新增 XML layout（项目禁 XML layout，全部 Compose）；图标属 drawable/mipmap，不在此限。
- CHG-56：浅色次要文字 ≥4.5:1（`#556579`），禁用 `#8A9DB0`。
- 设计稿中的示例分数、示例时间、示例点位、源更新示意值是版式示意，**不是业务数据**。
- 点星标 / 搜索结果不得绕过 C01 准星真源；点星标不动准星。
