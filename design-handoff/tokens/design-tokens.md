# 设计 Token 速查（人读版）· v3

> 机器可读版本：`design-tokens.json`（v3.0.0）。两者冲突时以 JSON 为准；色值/字阶冲突时以工程 Kotlin 单源为准。
> 基线：**工程 v1.16.0（versionCode 20）**；v3 = 功能/结构/文案/Tab 序/导航照工程，仅做视觉美化。
> 本目录全部文件可离线使用，无任何外链依赖。

## 1. 基准

| 项 | 值 |
|---|---|
| 设计视口 | 390 × 844 dp（1x，1px = 1dp）；基准屏 2x PNG 为 780 × 1688 |
| 导航模型 | 地图优先，**无通用底部 Tab Bar**（工程已移除 NavigationBar，CONFLICT-01 关闭） |
| 主题 | Material 3 / `TideSpotTheme`，明暗跟随系统 |
| 验收机型口径 | **AAK-AN00 / Android 16**（v2「荣耀 Win RT / 无 GMS 单机」作废）；定位为**双通道**（有 GMS 走 Fused，无 GMS 降级） |
| 屏幕水平外边距 | 12 dp → 主卡片 366 dp；次级内容 358 dp（内缩 16 dp） |

## 2. 主题色（工程 `TideSpotTheme.kt` 单源，2026-11-11 核对）

| 角色 | Light | Dark |
|---|---|---|
| primary | `#1565C0` | `#90CAF9` |
| secondary | `#00695C` | `#80CBC4` |
| tertiary | `#6A1B9A` | `#CE93D8` |
| error | `#C62828` | `#EF9A9A` |
| background / 画布 | `#EEF3F8` | `#0E1721` |
| surface / 卡片 | `#FFFFFF` | `#16212E` |
| surfaceVariant / 分层 | `#EEF3F8` | **`#1E2C3A`** |
| onBackground / onSurface | `#10243A` | `#E6EDF5` |
| onSurfaceVariant / 次文字 | **`#556579`** | **`#9FB0C0`** |
| outline / 描边 | `#DCE5EE` | `#2A3A4C` |

****v2 过期值已修正**：v2 稿的 `text.secondary #4A6076 / tertiary #73879B` 不是工程 token；v3 一律用工程 `onSurfaceVariant`（浅 `#556579` / 深 `#9FB0C0`）。**CHG-56 硬约束**：浅色次要文字用 `#556579`（≥4.5:1），禁用初稿 `#8A9DB0`（2.2–2.8:1）。

工程已落地 `TideSpotShapes` 与 `TideSpotTypography` 并注入 MaterialTheme，禁止在 feature 层硬编码补齐 colorScheme 角色。

## 3. Shapes 与 Typography（工程既有 token）

- **Shapes**：extraSmall 8 / small 12 / medium 16 / large 20 / extraLarge 20 dp。徽标圆角 4 dp 独立冻结（ConfidenceBadge），不走 Shapes。
- **Typography**：titleLarge 20/26 · titleMedium 16/22 · titleSmall 14/20 · bodyLarge 15/22 · bodyMedium 13/18 · bodySmall 12/16 · labelLarge 12/16 · labelMedium 12/16 · labelSmall 10/14（size/lineHeight sp）。
- **v3 建议（非工程 token）**：主卡分数放大到 56sp 作视觉锚点；实现可按需采用，不改 Typography 本体。

## 4. 冻结语义色（红线）

色值由 Kotlin 单源定义，**不进 MaterialTheme**，避免双源漂移。

### 五档分数色（`ScoreBands.kt`）

| 档位 | 分数 | 文字 | 色值 | 字色 |
|---|---|---|---|---|
| EXPLOSIVE | ≥80 | 爆护窗口 | `#1B5E20` | `#FFFFFF` |
| GREAT | 65–79 | 很适合 | `#43A047` | `#FFFFFF` |
| OK | 50–64 | 可以 | `#FDD835` | `#263238` |
| POOR | 35–49 | 较差 | `#FB8C00` | `#000000` |
| BAD | <35 | 别去 | `#E53935` | `#FFFFFF` |

`WINDOW_THRESHOLD = 80`：指数 Tab ≥80 段叠窗口标记（v3 改薄荷小圆点）。

### 六枚置信徽标（`ConfidenceBadge.Kind`）

| 档位 | 文字 | 色值 | 字色 |
|---|---|---|---|
| MEASURED | 实测 | `#1565C0` | `#FFFFFF` |
| SATELLITE | 卫星 | `#00695C` | `#FFFFFF` |
| MODEL | 模型 | `#6A1B9A` | `#FFFFFF` |
| ESTIMATED | 估算 | `#EF6C00` | `#000000` |
| UNCALIBRATED | 未校正 | `#757575` | `#FFFFFF` |
| LOW_CONFIDENCE | 低置信 | `#C62828` | `#FFFFFF` |

`Confidence.UNAVAILABLE → UNCALIBRATED`（承载需求 §9.6「未校正」展示）；徽标圆角 4 dp、padding 6×2、`labelSmall`。

**深色下的处理**：色带看起来更亮是深色卡片底色的相对明度感受，**不是第二套色值**。实现仍取冻结色，禁止派生 dark variant；靠文字标签 + 1dp `#00000033` 描边 + 卡片底色对比保证 ≥4.5:1。

## 5. v3 美化 token（设计侧新增，不改工程冻结角色）

### 三级卡片层级 + 阴影

| 层 | 浅色 | 深色 | 阴影 |
|---|---|---|---|
| L0 画布 | `#EEF3F8` | `#0E1721` | 无 |
| L1 卡片 | `#FFFFFF` | `#16212E` | `0 3px 10px rgba(16,36,58,.06)` |
| L2 抬升浮层 | `#FFFFFF` | `#1E2C3A` | `0 10px 26px rgba(13,71,161,.20)` |

### 薄荷点缀（本版唯一新增视觉语言）

`#80CBC4` / `#26A69A` / 浅底 `#E0F2F1` —— 色源来自工程既有 `secondary #00695C` 家族与图标潮线，**不新增独立色源**；每屏克制 ≤3 处（选中锚点 / 关键数值 / 准星中心 / 信息条图标）。禁止替代五档冻结色、禁止满屏铺、深色下 `#26A69A` 提亮到 `#80CBC4`。

### 准星三态（与 APP 图标同源）

| 态 | 环 | 中心点 | 触发 |
|---|---|---|---|
| idle | `#80CBC4` 虚线 | `#26A69A` | 常态 |
| preview | `#F59E0B` 虚线 | `#F59E0B` | 拖动预览（工程现有琥珀态） |
| locked | `#1565C0` 实线 | `#1565C0` | 停稳落点 |

工程现为四段 drawLine + 中心圆 r5f、无环；v3 细环 + 薄荷点需改 Compose Canvas（用户已确认采用）。点星标不动准星（F-1.3）。

### 主卡渐变 / 收藏白卡

- **主卡**：`linear-gradient(135deg, #1565C0 0%, #0D47A1 78%)` + 右上柔光圆 + 薄荷光斑；工程现纯色 `#0D47A1`，v3 改 brush 背景，冻结色带仍取 ScoreBands。
- **收藏卡**：白卡（深 `#16212E`）+ 左侧 4dp 档位色带；≥48h 不可用卡 alpha 0.6；编辑/删除收进图标按钮。

## 6. 关键布局与安全区

| 元素 | 尺寸 / 位置（v1.16.0 实测） |
|---|---|
| 顶部工具浮层 | 起点 `y ≥ 68 dp`；高 46 dp；搜索框 250 dp；按钮 44 dp |
| 准星 | 116 dp 包围盒；细环 r30 + 薄荷中心点 r7；**F-1.3 唯一选点真源** |
| 信息条 | **236 dp 浮条 + 独立操作面板**（v2 的 366×200 作废） |
| Top10 入口 | 358 × 52 dp，深蓝 `#0D47A1`，圆角 14 |
| DaySelector | **52 dp 高、两行、圆角 18、min 56、间距 6**（v2 的 36dp 单行作废） |
| 五维 Tab | 358 × 40 dp；渲染序 钓鱼指数→潮流→天气→水质→风浪 |
| 今日指数主卡 | 366 × 152 dp |
| 逐小时图表卡 | 366 × 210 dp（工程现 180，v3 建议调 210） |
| 水质指标卡 | **1 行 × 3 卡**（水温/溶氧/浊度，pH 已删），单卡高 112 dp |
| Top10 Sheet | 390 × 240 dp |

**Android 16 edge-to-edge**：工程 `MainActivity contentWindowInsets = WindowInsets(0,0,0,0)` 全屏铺入，各屏自行 `statusBarsPadding` / `navigationBarsPadding`；稿件 58 dp 状态栏占位 / ≥24 dp 手势条仅为表现值，**不得写死进代码**。P2 遗留：地图未铺入系统栏区，随下次 UI 批次处理。

## 7. 图标色板

蓝渐变 `#1976D2 → #0D47A1`、潮线 / 中心点 `#80CBC4`、图形白 `#FFFFFF`；v3 母题＝细环 r21 + 四向刻度 + 薄荷点 r6.4 + 白芯（背景双潮线）。完整规格见 `icon/app-icon-spec.md`。
