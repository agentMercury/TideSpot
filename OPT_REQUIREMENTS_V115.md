# 潮点 TideSpot · V1.15 需求池（OPT-V115）

> 建立日期：**2026-11-11** ｜ 建档：交付负责人 ｜ 版本口径：**V1.15 需求池 → 目标发布 `v1.15.0`**（`OPT-V1x-y` 为需求编号，`vX.Y.Z` 为发布版本，见 `REQUIREMENTS_REGISTER.md` CHG-39）
> 上游依据：`design-handoff/DECISIONS.md`（UI v2 设计包裁定，**结论：设计包可直接作为 UI 权威依据**）、`OPT_REQUIREMENTS_V11.md`（v1.1 池）、`REQUIREMENTS_REGISTER.md` CHG-38/39/43/44
> 前置状态：V1.1 需求池 26 项中 25 项已交付、1 项（OPT-V11-17 缩略图）按 D21 移除；i18n（OPT-V11-13）技术目标达成（红线扫描 FAIL=0），**GAP-05 定稿接线 10/17**。

---

## 1. 范围（2 项）

### OPT-V115-01｜GAP-05 剩余 7 条文案接线【P0】

- **需求**：把 `:core:ui/res/values{-en}/strings.xml` 中已定稿但**无 UI 落点**的 7 条 GAP-05 文案接入界面（或按设计稿补齐其承载 UI）。已完成 10 条见 §3。
- **待接线清单（key → 设计稿位置 → 所需 UI）**：

| # | key | 文案（中） | 设计要求 | 落点建议 |
|---|---|---|---|---|
| 1 | `ts_reverse_geo_off` | 逆地理已关闭，仅显示坐标与本地 plusCode | 信息条上方一次性提示条 | `MapScreen` 信息条区：`reverseGeoEnabled == false` 时显示 `labelSmall` 提示条（可关闭/自动隐藏 5s） |
| 2 | `ts_window_bar` | 窗口 | 逐小时图表 ≥80 段「窗口」横条文字标签 | `HourChart` BAR 模式：`windowSegments` 上方绘制文字标签（Canvas `drawText`，复用现有 `textMeasurer`） |
| 3 | `ts_record_high_tide` | 记录一次涨潮时刻 | 校潮 Sheet 空态引导 | `CalibrationSheetContent`：`observations.isEmpty()` 时显示该引导句（替代空白） |
| 4 | `ts_offline` | 网络不可用，显示本地缓存数据 | 全局离线横幅 | 详情/收藏页顶部 `StaleBanner` 同级：`ConnectivityManager` 无网 → 显示该条（状态栏样式） |
| 5 | `ts_stale` | 数据已过期 · 显示上次成功拉取结果 | 过期黄条通用句 | `DetailScreen.StaleBanner` 黄条改为「该句 + 具体数据时间」，替换现 `ts_detail_data_time_expired` 纯时间句 |
| 6 | `ts_dimension_degraded` | 部分维度不可用，已按有效权重重算 | 低置信提示 | `DetailScreen` 指数 Tab：`Confidence.LOW_CONFIDENCE` 徽标同行附该说明 |
| 7 | `ts_chart_table_switch` | 图表 / 数值表 | 单开关切换 | 详情「图表 / 数值表」由**双 FilterChip 改单开关**（`Switch` 或单按钮，`stringResource` 作 label，`ts_detail_chart/table` 作状态说明） |

- **验收标准（GWT）**：
  - Given 关闭逆地理总开关，When 进入地图，Then 信息条上方出现 `ts_reverse_geo_off` 提示且可自动消失；
  - Given 指数 Tab 存在 ≥80 分小时，When 查看柱状图，Then ≥80 段有「窗口」文字标签；
  - Given 校潮 Sheet 无记录，Then 显示「记录一次涨潮时刻」；
  - Given 断网，Then 详情/收藏页显示 `ts_offline` 横幅（不阻断离线数据）；
  - Given 缓存超 TTL，Then 黄条文案为 `ts_stale` + 具体时间；
  - Given 有效权重 <0.6，Then 低置信徽标旁出现 `ts_dimension_degraded`；
  - Given 详情图表区，Then 仅一个「图表 / 数值表」开关且中英均正确；
  - **AND：`i18n_scan.mjs` FAIL=0；`grep ` 统计 GAP-05 17/17 每个 key ≥1 处 `R.string.` 引用（`DECISIONS §6` 复核项 1）。**
- **涉及**：`:feature:map`、`:feature:detail`、`:core:ui`（HourChart）、`:app`（离线探测）。

### OPT-V115-02｜UI v2 设计包全量落地（视觉更新）【P0】

- **需求**：按 `design-handoff/`（v2.0.0，37 文件 SHA-256 全一致）把界面视觉/组件/间距/token 对齐设计稿，关闭 **GAP-03（edge-to-edge insets）** 并完成 9 屏视觉验收。
- **范围**：
  1. **GAP-03 收口**：全部页面入口显式 `navigationBarsPadding()` / `statusBarsPadding()`（MapScreen 浮动按钮组、DetailScreen 底部免责入口、FavoritesScreen 批量栏、Settings 滚动尾、所有 `ModalBottomSheet`）；`enableEdgeToEdge()` 在 `MainActivity` 启用（可选，需真机确认状态栏对比度）。
  2. **Token 对齐**：`design-tokens.json` → `TideSpotTheme`（色板/圆角/间距/字阶）；**色值冻结项不改**（`ScoreBands`/`ConfidenceBadge`/`TideSpotTheme` 主色）。
  3. **组件对齐**：`components.json` 22 组件（卡片圆角/分割线/徽标尺寸/chips 间距/空态版式）。
  4. **9 屏视觉验收**：`screens.json` 逐屏对照（地图 / 指数 / 设置 / 深色指数 / 水情派生 5 屏）。
  5. **深色模式**：`assets/dark-adaptation.svg` 口径，逐屏核对对比度 ≥4.5:1（正文）。
- **验收标准**：
  - Given 任一屏，Then 关键间距/圆角/字阶与 `design-tokens.json` 偏差 ≤1dp/1sp（抽样 10 项）；
  - Given 手势条/状态栏，Then 无控件被遮挡（真机 AAK-AN00 截图留证）；
  - Given 深色模式，Then 无对比度不足（正文 ≥4.5:1）；
  - Given 中/英双语，Then 无截断（英文最长串回归）。
- **涉及**：`:app`、`:core:ui`、四个 `:feature:*` 的 Screen/组件样式。
- **前置**：设计包已评估可用（`DECISIONS.md` §1，客户确认）。

---

## 2. 非目标（明确不做）

- 不新增语言（语言集仍中/英；不含日文）。
- 不改算法/评分/时效（`ScoreBands`/`ConfidenceBadge`/`TideSpotTheme` 冻结色值不动）。
- 不恢复 OPT-V11-17 缩略图（D21）。
- 不接入高德地图（V1.2 池 OPT-V12-05，仍 backlog）。
- 不引入新依赖（除非设计稿明确要求，需客户批准）。

## 3. 前置已完成（避免重复施工）

| 项 | 状态 | 证据 |
|---|---|---|
| i18n 架构（`TideSpotStrings`/`DomainCopy` 可注入层） | ✅ | CHG-40/41 |
| 中文硬编码清零 | ✅ | `.tools/i18n_scan.mjs` **FAIL=0**（538→0，豁免 12） |
| 中英资源逐键锁 | ✅ | `TideSpotStringsTest` / `EnStringsParityTest` |
| 应用级语言声明 | ✅ | `app/src/main/res/xml/locales_config.xml` + Manifest `localeConfig` |
| GAP-05 已接线 10/17 | ✅ | `ts_resolving`/`ts_location_denied`/`ts_favorite_dialog_title`/`ts_favorite_name_hint`/`ts_top10_this_spot`/`ts_top10_all`/`ts_top10_empty`/`ts_confirm_favorite`/`ts_placeholder_v11`/`ts_cancel` |
| UI v2 已落地项 | ✅ | GAP-01 图标、CONFLICT-01 地图优先无底栏、CONFLICT-02 免责 D11 版 |

## 4. 风险（风险—影响—概率—应对）

| 风险 | 影响 | 概率 | 应对 |
|---|---|---|---|
| UI 更新触碰冻结色值/组件取值 | 视觉回归 + 破坏既有锁测试 | 中 | 只改布局/圆角/间距；`ScoreBands`/`ConfidenceBadge`/主色不动；跑全量测试 |
| GAP-05 新增 UI 元素破坏 `assertCountEquals` 类语义锁 | 测试失败 | 中 | 新增元素带独立 testTag/contentDescription；同步更新锁测试而非删除 |
| 英文长串截断 | 视觉回归 | 中 | 每屏英文最长串真机核对（V1.15 验收项） |
| edge-to-edge insets 误配 | 控件被手势条遮挡 | 中 | 逐屏 `navigationBarsPadding`；Sheet 遵守「操作按钮置顶」教训（HANDOVER §2） |
| 真机 adb 无法切语言（本机 ROM 限制） | 英文验收受阻 | 高 | 人工在系统设置切「应用语言」；或 Robolectric `qualifiers=en` 断言 + 截图留证 |

## 5. 交付定义（DoD）

1. `i18n_scan.mjs` FAIL=0，且 **GAP-05 17/17 有引用**；
2. `gradlew test` 八模块 EXIT=0；
3. `assembleRelease` 产出并分发 `E:\Trans`；
4. 真机（AAK-AN00 / Android 16）九屏中英双语 + 深色冒烟截图留证；
5. `PROGRESS_TRACKER` §10 逐项回写、`REQUIREMENTS_REGISTER` 登记 CHG。