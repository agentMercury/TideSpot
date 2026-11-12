# 手绘 A · 复古户外 —— 详情页 Compose 试点落地说明

> 试点日期：2026-11-12 ｜ 工程基线：`93d3c19`（v1.16.0 + BOM/日志清理后）
> 设计来源：本目录 `spec.json` / `SKIN-DESIGN-SPEC.md` / 28 屏效果图
> 口径：**只做美化，不动功能**；先详情页一屏试点，验证链路后再决定是否铺开

---

## 1. 为什么能「一行业务逻辑都不改」

探明的关键工程事实：`DetailScreen.kt`（1175 行）全部通过
`MaterialTheme.colorScheme / shapes / typography` 取视觉 token，UI 层没有硬编码色值。
因此只需在详情页子树外嵌套一层局部 `MaterialTheme` 覆盖，即可整屏换皮——
业务 Composable、参数、回调、导航状态一律不碰。

这也是 `SKIN-DESIGN-SPEC.md` 里「皮肤 = 一组可替换视觉 token」的第一处实证：
**结构/文案/交互共用，只替换视觉 token。**

## 2. 改动清单

**首轮 · 试点接入（3 个文件）**

| 动作 | 文件 | 内容 |
|---|---|---|
| 新增 | `core/ui/.../theme/VintageOutdoorTheme.kt` | 4 套 ColorScheme（海/陆 × 浅/深）、非对称圆角 Shapes、衬线 Typography、`VintageOutdoorTheme()` |
| 改 | `app/.../MainActivity.kt` 的 `DetailPage` | 加 2 个 import；外层包 `VintageOutdoorTheme(waterBody = ...)`；Column 加 `.background(colorScheme.background)` |
| 改 | `tidespot-android/verify.ps1` | 恢复 UTF-8 BOM 并加编码说明（见 §6） |

**V2 · 真机对拍暴露的三处换肤缺口，已修复（3 个文件，详见 `device-compare/真机对拍报告.md` §10）**

| 动作 | 文件 | 内容 |
|---|---|---|
| 改 | `feature/detail/.../DetailScreen.kt` `ScoreMainCard` | 背景 `Color(0xFF0D47A1)` → `primaryContainer`；3 处 `Color.White` → `onPrimaryContainer` |
| 改 | `core/ui/.../selector/DaySelector.kt` | 选中态 `Color(0xFF1565C0)` → `primary`；2 处白字 → `onPrimary`；移除失效 import |
| 改 | `core/ui/.../theme/VintageOutdoorTheme.kt` | 4 套 ColorScheme 各补 13 个字段（`surfaceContainer*` / `inverse*` / `surfaceTint` / `errorContainer` / `tertiaryContainer`） |

**明确未动**：`ScoreBands.kt`、`ConfidenceBadge.kt`、`TideSpotStrings.kt`、`WaterBodyTabTitles.kt`、
`TideSpotTheme.kt`、任何文案 / Tab 序 / 导航 / ViewModel / 资源；`DetailScreen.kt` 与 `DaySelector.kt`
**仅替换颜色取值，逻辑、文案、交互零改动**。

## 3. Token 映射（设计稿 → 工程）

| 设计侧（`engine.py` A-vintage palette） | 工程侧 ColorScheme | 海洋 / 近海 | 内陆水体 |
|---|---|---|---|
| `bg` | `background` | `#E4D9BC` 做旧米纸 | `#DCE2C4` 苔绿纸 |
| `card` | `surface` | `#F2EAD4` | `#F1EED8` |
| `card2` | `surfaceVariant` | `#E8DDBE` | `#E4E6C6` |
| `ink` | `onBackground` / `onSurface` / `outline` | `#16324F` 藏蓝 | `#213D2A` 墨绿 |
| `primary` | `primary` | `#1B3A5C` | `#2E5A48` |
| `green` | `secondary` | `#2E6E7E` | `#3E7A54` |
| `accent2` | `tertiary` | `#D9A734` 芥末黄 | `#D9A734` |
| `accent` | `error` | `#C15A3A` 朱红 | `#C15A3A` |
| `muted` | `onSurfaceVariant` | `#5C5540` | `#556045` |

深色两套取自 `engine_full.py` 的 `DARK`（海 `#0E1C2B` / 陆 `#101D18`）。

**形状**：四角半径刻意不齐（如 medium = 14/18/14/19 dp），模拟手工裁纸与版画套印歪斜；
量级与 `TideSpotShapes` 的 8/12/16/20 一致，不改变任何组件高度。

**字阶**：字号 / 行高 / 字重与 `TideSpotTypography` 逐项全等，仅把字族换为 `FontFamily.Serif`
（设计稿 Georgia / KaiTi → serif 同样回退），避免字体缺失引发换行与行高漂移。

## 4. 三轴正交落地

- **皮肤 Skin**：本文件，首版唯一内置；其余三页仍用 `TideSpotTheme`，视觉零影响。
- **水体 Water**：`waterBody = state.result?.effectiveWaterBody ?: spot.waterBody`，
  `INLAND_WATER` 走墨绿色板，其余走藏蓝（配色兜底，不改变 `WaterBodyTabTitles` 的 Tab 语义）。
  设计稿「一眼分辨海洋 / 内陆」由此在工程侧真实生效。
- **明暗 Mode**：`darkTheme = isSystemInDarkTheme()`，与皮肤、水体独立；对应 OPT-A-03 尚未做的手动开关。

## 5. 冻结契约（逐条不变）

1. Tab 渲染序（钓鱼指数 / 潮流或水位 / 天气 / 水质 / 风浪或湖面风与水温）—— 由 `WaterBodyTabTitles` 决定
2. 免责恒 5 条 —— `TideSpotStrings.kt` 单源
3. 五档分数色 `ScoreBands.kt`、六徽标色 `ConfidenceBadge.kt` —— **未在新主题内重定义、未派生副本**
4. DaySelector、水质 1×3（无 pH）、地图优先无底栏、准星唯一真源
5. 双水体六重区分中的语义部分（徽标 / 鱼种 / 第二 Tab / 图表单位 / 地点）由业务侧负责，本轮只补配色
6. 换皮肤只替换视觉 token，不触碰结构 / 文案 / 交互

## 6. 一个必要的兼容性例外

清理 BOM 时把 `verify.ps1` 的 BOM 也去掉了，导致 Windows PowerShell 5.1 按 ANSI 解码，
中文字符串破坏引号、脚本无法解析。**该脚本是全仓唯一需要保留 UTF-8 BOM 的文件**，
已恢复并在头部注明原因；其余 84 个（`.kt` / `.kts` / `.xml` / `.properties` / `.gitignore`）
保持无 BOM UTF-8，Gradle / Kotlin / XML 均按标准处理。后续清理请勿误删此 BOM。

## 7. 验收结果

```
verify.ps1 -SkipAssemble
[PASS] JDK 17 就位
[PASS] 红线扫描 main 源码零域名命中（119 个 .kt 文件）
[PASS] Gradle 8.9 就位
BUILD SUCCESSFUL in 4m 10s   （177 actionable tasks: 87 executed, 90 from cache）
[PASS] Gradle 八模块测试 全绿（退出码 0）
VERIFY PASS（全部步骤通过）
```

八模块单测覆盖 Kotlin 全模块编译，确认 4 套 ColorScheme / 非对称圆角 / 衬线字阶 /
`WaterBodyType` 接入均可编译，`AppNavTest` 等导航测试全绿（功能零回归）。

## 8. 试点边界（诚实清单）

- **未做**：纸张颗粒、套印错位、鱼形矢量装饰的 Compose 实现（需 drawable / Canvas 资源），
  本轮只落地可由 token 直接表达的三类视觉差异。
- **已补**：`assembleRelease` 打包（含 lintVital）；**真机对拍首轮四态**（AAK_AN00）见 `device-compare/真机对拍报告.md` §1–9。首轮为「部分通过」，暴露分数主卡 / DaySelector 选中态 / `surfaceContainer*` 三处不跟随主题；**D1/D2/D3 已修复**，复拍内陆 + 近海 × 浅/深四态全部通过，藏蓝海洋色板与墨绿内陆色板一眼可辨（见该报告 §10）。结论：视觉 token 层自洽，但手绘语言未工程化，仍不建议直接铺开。
- **未做**：视觉回归基准、对比度仪器测量、TalkBack 与多分辨率。
- **未做**：其余 3 个顶层页（MAP / FAVORITES / SETTINGS）换皮；设置页「外观·皮肤」入口仍为静态稿。
- **未做**：OPT-A-09 的皮肤数据类、切换逻辑与持久化（本文件是视觉 token 前置，不是换肤功能实现）。
- **不能承诺像素级 1:1**：手绘纸纹 / 套印在 Compose 里是近似还原，不是复印。

## 9. 回滚与下一步

回滚点：`93d3c19`（试点前基线，工作树干净）。本试点改动如需撤销，回到该提交即可。

建议顺序：
1. 真机装 debug 包对拍详情页浅色海 / 浅色陆 / 深色三态，确认配色与对比度；
2. 若视觉认可，再补纸纹与鱼形矢量资源，并把同一层主题推广到其余 3 页；
3. 最后才做 OPT-A-09 的换肤数据层与设置页交互（那属于功能实现，需单独排期）。
