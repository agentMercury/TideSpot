# 设计 Token 速查（人读版）

> 机器可读版本：`design-tokens.json`。两者冲突时以 JSON 为准；色值与文案冲突时以 Kotlin 单源为准。
> 本目录全部文件可离线使用，无任何外链依赖。

## 1. 基准

| 项 | 值 |
|---|---|
| 设计视口 | 390 × 844 dp（1x，1px = 1dp） |
| 导航模型 | 地图优先，**无通用底部 Tab Bar** |
| 主题 | Material 3 / `TideSpotTheme`，明暗跟随系统 |
| 验收机型口径 | 荣耀 Win RT / Android 16 / 无 GMS（单机口径） |
| 屏幕水平外边距 | 12 dp → 主卡片 366 dp；次级内容 358 dp（内缩 16 dp） |

## 2. 主题色（工程 `TideSpotTheme.kt` 单源）

| 角色 | Light | Dark |
|---|---|---|
| primary | `#1565C0` | `#90CAF9` |
| secondary | `#00695C` | `#80CBC4` |
| tertiary | `#6A1B9A` | `#CE93D8` |
| error | `#C62828` | `#EF9A9A` |
| 画布 | `#EEF3F8` | `#0E1721` |
| 卡片 / Sheet | `#FFFFFF` | `#16212E` |
| 主文字 | `#10243A` | `#E8F0F8` |
| 次文字 | `#4A6076` | `#A9BCCE` |
| 弱文字 | `#73879B` | `#7C93A8` |
| 描边 | `#DCE5EE` | `#2A3A4C` |

工程只显式定义 4 个 colorScheme 角色，其余为 Material 3 默认值。**禁止在 feature 层硬编码补齐。**

## 3. 冻结语义色（红线）

色值由 Kotlin 单源定义，**不进 MaterialTheme**，避免双源漂移。

### 五档分数色（`ScoreBands.kt`）

| 档位 | 分数 | 文字 | 色值 | 字色 |
|---|---|---|---|---|
| EXPLOSIVE | ≥80 | 爆护窗口 | `#1B5E20` | `#FFFFFF` |
| GREAT | 65–79 | 很适合 | `#43A047` | `#FFFFFF` |
| OK | 50–64 | 可以 | `#FDD835` | `#263238` |
| POOR | 35–49 | 较差 | `#FB8C00` | `#000000` |
| BAD | <35 | 别去 | `#E53935` | `#FFFFFF` |

`WINDOW_THRESHOLD = 80`：指数 Tab 中 ≥80 段叠加「窗口」横条。

### 六枚置信徽标（`ConfidenceBadge.Kind`）

| 档位 | 文字 | 色值 | 字色 |
|---|---|---|---|
| MEASURED | 实测 | `#1565C0` | `#FFFFFF` |
| SATELLITE | 卫星 | `#00695C` | `#FFFFFF` |
| MODEL | 模型 | `#6A1B9A` | `#FFFFFF` |
| ESTIMATED | 估算 | `#EF6C00` | `#000000` |
| UNCALIBRATED | 未校正 | `#757575` | `#FFFFFF` |
| LOW_CONFIDENCE | 低置信 | `#C62828` | `#FFFFFF` |

`Confidence.UNAVAILABLE → UNCALIBRATED`，不虚构更高置信。徽标圆角 4 dp、横向内边距 6 dp、纵向 2 dp、`labelSmall`。

### 深色下的处理（易踩坑）

深色屏里色带看起来更亮，是**深色卡片底色造成的相对明度感受，不是第二套色值**。实现仍取冻结色，禁止派生 dark variant。可读性靠：

1. 保留文字标签（N-7，禁止纯颜色编码）；
2. 必要时给色块加 1 dp `#00000033` 深色描边；
3. 卡片底色与色块保持 ≥ 4.5:1 对比。

## 4. 字阶 / 间距 / 圆角

| 角色 | 设计字阶 | M3 建议角色 |
|---|---|---|
| 屏标题 / 卡片主标题 | Bold 20 | `titleLarge` |
| 分区标题 / Sheet 标题 | Bold 16 | `titleMedium` |
| 卡片小标题 / 指标名 | Bold 14 | `titleSmall` |
| 正文 / 标签 | Regular 13 | `bodyMedium` |
| 数值（等宽） | Medium 13 | `bodyMedium` + `FontFamily.Monospace` |
| 来源时间 / 免责 | Regular 10 | `labelSmall` |
| Chip / Tab / FilterChip | Medium 12 | `labelLarge` |

- 设计字族：Noto Sans SC（中文）、DM Sans / Geist Mono（数值）。Android 未内置时回退系统 sans + monospace，**不得因字体缺失改变行高或换行**。
- 辅助文案行高 12 dp；多行明细（ΠV / 免责）行高 15 dp；免责原文禁止 ellipsis 截断。
- 间距阶梯：`4 / 8 / 12 / 16 / 24` dp；卡片内边距 12 dp；分区间距 16 dp；组件间距 8 dp。
- 圆角：卡片 16、按钮 12、Chip 18、徽标 **4（工程值，勿改）**、BottomSheet 顶角 20（建议）、对话框 20（建议）。

## 5. 关键布局与安全区

| 元素 | 尺寸 / 位置 |
|---|---|
| 顶部工具浮层 | 起点 `y ≥ 68 dp`；高 46 dp；圆形按钮 44 dp；搜索框 206 dp |
| 准星 | 64 × 64 dp，**F-1.3 唯一选点真源** |
| 准星信息条 | 236 × 48 dp |
| 底部信息条 | 366 × 200 dp |
| Top10 入口 | 366 × 48 dp |
| DaySelector | 358 × 36 dp |
| 五维 Tab | 358 × 40 dp |
| 今日指数主卡 | 366 × 152 dp |
| 逐小时图表卡 | 366 × 210 dp |
| Top10 Sheet | 390 × 240 dp |

**Android 16 edge-to-edge**：设计稿状态栏占位 58 dp、手势条预留 ≥ 24 dp，仅用于稿件表现。实现必须走 `WindowInsets.statusBars` / `navigationBars`，**不得把 58/24 写死进代码**。需要预留底部余量的组件：底部信息条、Top10 入口、BottomSheet 内容底部、对话框底部按钮、免责 Sheet 末条。

## 6. 图标色板

蓝渐变 `#1976D2 → #0D47A1`、潮线 / 中心点 `#80CBC4`、图形白 `#FFFFFF`、次色 `#00695C`。完整规格见 `icon/app-icon-spec.md`。
