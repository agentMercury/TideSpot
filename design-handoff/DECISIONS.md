# design-handoff 决策记录（DECISIONS）

> 裁定日期：2026-11-10；裁定人：产品 / 用户；记录人：交付负责人。
> 本文件补齐 `manifest.json → decision_blockers` 列出的全部决策前置项。
> **读取顺序**：`INDEX.md` → `manifest.json` → 本文件 → `mapping/design-to-code.json`（conflicts 已全部关闭）。

## 1. 裁定结论汇总

| ID | 内容 | 裁定 | 状态 |
|---|---|---|---|
| CONFLICT-01 | 底部 NavigationBar vs 地图优先导航 | **方案①：工程改地图优先、移除底栏**（贴合 §8.1 与 v2 设计稿）；随移除同步补 `navigationBarsPadding` 等 edge-to-edge insets（GAP-03 一并解决） | ✅ 已关闭 |
| CONFLICT-02 | 免责第 5 条：§9.6 原文 vs 工程 D11 | **以工程 D11「现场校潮」版为准**；免责恒 5 条不变，「未校正」状态由 ConfidenceBadge 徽标承载；需求 §9.6 原文由需求负责人回改登记 | ✅ 已关闭 |
| CONFLICT-03 | 「源更新 06:00 UTC」为示意值 | 按包内口径执行：显示数据层透出的真实值或「—」，**禁止硬编码编造** | ✅ 已关闭（实操口径） |
| GAP-05 | 17 条 `design_proposed` 文案 | **全部按设计稿文案采纳**（逐条裁定明细见 §2）；统一补进 `TideSpotStrings.kt`（FO-01 唯一入口） | ✅ 已关闭 |

## 2. GAP-05 · 17 条文案逐条裁定明细（全部按设计稿采纳）

> 实现要求：全部迁入 `TideSpotStrings.kt`；工程内对应硬编码文案同步替换为字符串常量引用。
> 凡标注「需测试核对」的条目，替换后必须跑对应模块测试；测试断言文案本身的，按新文案更新断言（属文案落地允许范围，不算行为漂移）。

| # | key | 定稿文案（= 设计稿） | 工程落点与动作 |
|---|---|---|---|
| 1 | `resolving` | 正在解析点位… | 替换 `MapScreen.kt:356`「逆地理解析中…」 |
| 2 | `location_denied` | 未获得定位权限，可拖动地图手动选点 | 新增；定位拒绝态使用 |
| 3 | `reverse_geo_off` | 逆地理已关闭，仅显示坐标与本地 plusCode | 新增；N16 关闭态信息条提示行 |
| 4 | `favorite_dialog_title` | 收藏该点位 | 替换 `MainActivity.kt:252`「确认收藏」标题 |
| 5 | `favorite_name_hint` | 留空则使用坐标作为名称 | 新增；收藏对话框名称输入 hint |
| 6 | `top10_this_spot` | 本点最佳窗口 | 替换 `DetailScreen.kt:320`「未来最佳窗口 Top10」标题 |
| 7 | `top10_all` | 全部收藏 Top10 | 新增；FavoritesScreen Top10 标题（实现时与现有 contentDescription 对齐） |
| 8 | `top10_empty` | 当前范围内没有符合条件的最佳窗口 | 采纳为 UI 空态文案；**domain 冻结链不动**：`BestWindowFinder` fallbackMessage 及 `Top10Formatter` 兜底逻辑保持原值，仅 UI 侧空态展示用本条 |
| 9 | `window_bar` | 窗口 | 新增；Top10 / 数值表窗口列标签（实现时核对现有行格式后统一引用） |
| 10 | `record_high_tide` | 记录一次涨潮时刻 | 替换 `DetailScreen.kt:397`「记录一条高潮观测」按钮文案（D11 校正面板相关测试需核对） |
| 11 | `placeholder_v11` | 垂钓用例、物种预设与因子权重编辑入口于 v1.1 开放（当前沿收藏点预设生效）。 | 与 `SettingsScreen.kt:105` 一字相同，迁入表后改为引用 |
| 12 | `offline` | 网络不可用，显示本地缓存数据 | 新增；离线态提示条 |
| 13 | `stale` | 数据已过期 · 显示上次成功拉取结果 | 采纳；**合并工程两态展示**：原 `DetailScreen.kt:432`（不可用态）/`:442`（过期态）差异改为「不可用」附加说明或时间戳承载，正文统一引用本条；StaleBanner 语义测试需核对 |
| 14 | `dimension_degraded` | 部分维度不可用，已按有效权重重算 | 采纳为 C18 降级卡 UI 文案；`:core:domain` VetoEvaluator reason 文案**冻结不动**，消费侧禁 parse 文案（走 VetoCode 结构化兜底） |
| 15 | `chart_table_switch` | 图表 / 数值表 | 采纳为切换控件标签；原 `DetailScreen.kt:159/162` 两个 Tab 标签统一引用本条（控件形态按 screens.json） |
| 16 | `confirm_favorite` | 确认收藏 | 与 `MainActivity.kt:252` 一字相同，迁入表后改为引用 |
| 17 | `cancel` | 取消 | 与 `MainActivity.kt:320` 一字相同，迁入表后改为引用 |

## 3. 随裁定生效的实现红线（在包内既有红线之上新增）

1. 移除 `MainActivity` 底部 `NavigationBar`（`:90-104` 及 `TAB_ORDER`）属本决策授权范围；删除后各屏必须显式补 `navigationBarsPadding()` / `WindowInsets` 适配（GAP-03），Android 16 edge-to-edge 不允许内容被系统栏遮挡。
2. 免责文案不改（CONFLICT-02 裁定），`TideSpotStringsTest`「恒 5 条」断言必须保持通过。
3. GAP-05 落地只动 UI 层文案与字符串常量，禁止触碰 `ScoreBands.kt` / `ConfidenceBadge.kt` / `TideSpotTheme.kt` 取值与 `:core:domain` 冻结模块。
4. #8/#13/#14 三条涉及与冻结链/两态展示的兼容，实现时先跑对应模块测试再改，改完复跑，M6 退出快照 573 测试不得无故红。

## 4. 登记建议

本批裁定建议由需求负责人登记 `REQUIREMENTS_REGISTER.md` 下一个 CHG 条目（CONFLICT-01/02 + GAP-05 定稿 + 方案① 导航改造范围），并在 `PROGRESS_TRACKER.md` 立对应 UI 落地里程碑。
