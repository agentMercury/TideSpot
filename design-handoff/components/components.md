# 组件状态规范（人读版）

> 机器可读版本：`components.json`（22 个组件、完整状态矩阵）。本文件是速查与红线说明。

## 1. 全局红线

| 编号 | 约束 |
|---|---|
| A1 | N-7：每张图必须有等价数值表，TalkBack 可读 |
| A2 | 档位 / 徽标必须**色块 + 文字**同时出现，禁止纯颜色编码 |
| A3 | 免责原文完整展示，禁止省略、改写、ellipsis |
| A4 | 可点元素命中区 ≥ 48 dp |
| A5 | null 数据**断线不连线**，禁止画成 0；海洋 null 不得产生 `wave = 0` |
| A6 | 语义色（五档 / 六徽标）取 Kotlin 单源，不进 MaterialTheme、不派生深色副本 |

## 2. 组件清单与必覆盖状态

| ID | 组件 | 必覆盖状态 | 视觉基准 |
|---|---|---|---|
| C01 | 准星 | idle / dragging / settled / locating / permission_denied | `assets/screen-01-map.svg` |
| C02 | 顶部工具浮层 | default / search_focused / offline | 同上 |
| C03 | 底部选点信息条 | resolving / resolved / reverse_geo_off / unknown_waterbody / land_point / all_geocoder_failed | 同上 |
| C04 | 确认收藏对话框 | prefilled / empty_name / editing / confirmed | 同上（M6 新增） |
| C05 | Top10 BottomSheet | this_spot / all_favorites / empty / expanded | `assets/screen-02-score.svg` |
| C06 | 免责 BottomSheet | collapsed_entry / open（恒 5 条） | `assets/screen-04-dark-score.svg` |
| C07 | DaySelector | selected / unselected / range_7 / range_10 / stale | 屏 2 / 屏 4 |
| C08 | 五维 TabBar | sea / inland / land / unavailable_tab | 屏 2 / 屏 4 |
| C09 | HourChart ⇄ 数值表 | chart_default / null_gap / window_overlay / hour_selected / table_mode | 屏 2 / 屏 4 |
| C10 | 今日指数主卡 | normal / vetoed / degraded / no_data | 屏 2 / 屏 4 |
| C11 | FactorBreakdown | collapsed / expanded / veto_triggered | v1 校样修正对齐 |
| C12 | ConfidenceBadge | 六档（见 token 文件） | `assets/visual-baseline.svg` |
| C13 | TideChart | sea / inland / calibrated / uncalibrated / no_tide_data | 潮流屏规格 |
| C14 | 校准面板 / 现场校潮 | status / manual_entry / recorded | 同上 |
| C15 | 水质 2×2 指标卡 | measured / estimated / unavailable | 卡高 ≥ 112 dp，禁裁切 |
| C16 | FilterChip / Switch | chip_selected / chip_unselected / switch_on / switch_off / placeholder | `assets/screen-03-settings.svg` |
| C17 | 离线陈旧条 | offline / stale / fresh | `assets/dark-adaptation.svg` |
| C18 | 分维降级卡 | partial / all_missing | 同上 |
| C19 | 收藏入口 / 列表 | empty / list / favorited | — |
| C20 | SearchPanel | idle / typing / result_selected / no_result | — |
| C21 | 地图署名 | default | 屏 1 底图右下 |
| C22 | 来源与更新时间行 | default（模型名 · 分辨率 · UTC 更新 · 本机拉取） | 屏 2 / 屏 4 |

## 3. 几个容易做错的点

1. **C01 准星是唯一选点真源**（F-1.3）。搜索结果选中后也要把准星落到该坐标，不能把点击点直接当选点。
2. **C04 对话框不可遮挡 C03 信息条**。v2 校样已把对话框下移到 `y = 386`，实现时用居中 + 安全区计算，不要写死坐标。
3. **C05 空态禁止兜底造假窗口**：没有 ≥60 分窗口就显示空态，不造 60 分兜底。
4. **C06 恒 5 条**，第 5 条是 M6 新增「潮汐校正 / 现场校潮」；需求 §9.6 的「未校正点位显示未校正」由 C12 徽标承载，不是第 6 条免责。
5. **C08 降级不隐藏 Tab**：潮流（不可用）仍可选中，内容区显示降级说明，不弹错误。
6. **C11 消费侧禁止 parse 否决文案**：结构化兜底走 `VetoCode`（Models.kt 冻结契约）。
7. **C16 隐私版块**：必须逐源列出「本机将向以下服务发送你选点的经纬度」，并提供逆地理总开关；隐私政策不得声明「零数据传输」。
8. **C15 卡高 ≥ 112 dp 且 `clipsContent = false`**：v1 校样出现过首行徽标被裁切。

## 4. 深色

- 卡片 / Sheet `#16212E`、画布 `#0E1721`、描边 `#2A3A4C`。
- 冻结色带与徽标**不改值**；深浅对照见 `assets/screen-04-dark-score.svg` 与 `assets/dark-adaptation.svg`。
- C17 / C18 的深色状态卡在 `assets/dark-adaptation.svg` 中有独立样张。
