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

**V3 · D5 手绘质感层（新增 6 个文件 + 改 3 个文件，详见 `device-compare/真机对拍报告.md` §11）**

| 动作 | 文件 | 内容 |
|---|---|---|
| 新增 | `design-handdrawn/generator/gen_android_assets.py` | 可复现生成脚本：2 张无缝纸纹 tile + 3 个可 tint vector（版画鱼 / 海洋浪纹 / 内陆山纹），路径取自 `engine.py` 的 `fish_svg()` / `water_motif()` |
| 新增 | `core/ui/src/main/res/drawable-nodpi/paper_grain_{light,dark}.png` | 96×96 周期性分形噪点 tile，alpha 上限对齐设计稿 `grain=0.16`，`ImageShader` Repeated 平铺 |
| 新增 | `core/ui/src/main/res/drawable/ic_{fish,wave,mount}_vintage.xml` | 单色可 tint 矢量装饰，不含任何皮肤配色常量 |
| 新增 | `core/ui/.../theme/HandDrawn.kt` | `rememberPaperGrainBrush()` / `paperGrain()` / `printMisregister()` / `handDrawnOutline()`，全为无状态纯绘制 Modifier，不消费触摸、不改测量 |
| 改 | `app/.../MainActivity.kt` 的 `DetailPage` | 外层 `Box` 铺纸纹；右下角鱼群 + 水体母题水印；水体 / 明暗显式取一次，保证色板、tile、母题三者同口径 |
| 改 | `feature/detail/.../DetailScreen.kt` | `ScoreMainCard` 挂套印错位 + 手绘描边 + 版画鱼角标；图表卡挂手绘描边外框 |

**明确未动**：`ScoreBands.kt`、`ConfidenceBadge.kt`、`TideSpotStrings.kt`、`WaterBodyTabTitles.kt`、
`TideSpotTheme.kt`、任何文案 / Tab 序 / 导航 / ViewModel；`DetailScreen.kt` 仅替换颜色取值与追加纯绘制装饰，
`DaySelector.kt` 仅替换颜色取值，**逻辑、文案、交互零改动**。所有新增质感资源均单色可 tint，
配色仍由 `MaterialTheme` 单源决定，皮肤 / 水体 / 明暗三轴保持正交。

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

**D5 后（最新，2026-11-12 15:07）**

```
verify.ps1（全量，含 assembleRelease）
[PASS] 红线扫描 main 源码零域名命中（120 个 .kt 文件）
[PASS] Gradle 八模块测试 + assembleRelease 全绿（退出码 0）
VERIFY PASS（全部步骤通过）   BUILD SUCCESSFUL
→ adb install -r 同签名 release 覆盖安装（保留真机收藏数据）
→ 复拍内陆 / 海洋 × 浅 / 深四态，UI dump 判定 INLAND / SEA
→ 纯背景块灰度标准差 0.00 → 4.97~5.62（四态全部纸纹生效）
```

`verify.ps1` 首次运行曾遇 Gradle daemon 启动意外退出（环境抖动，非代码错误），重跑即通过。

## 8. 试点边界（诚实清单）

- **已做（D5）**：纸纹颗粒、套印错位、鱼形矢量装饰、非平滑手绘描边全部工程化，
  由 `gen_android_assets.py` 可复现生成 + `HandDrawn.kt` 无状态 Modifier 提供；
  真机四态复拍验证通过，见 `device-compare/真机对拍报告.md` §11。
- **已补**：`assembleRelease` 打包（含 lintVital）；真机对拍首轮四态见报告 §1–9（部分通过）；
  **D1/D2/D3 修复**与海洋色板取证见 §10；**D5 质感层**见 §11。结论：详情页一屏的视觉语言已自洽，
  但手绘地图 / 五档色带鱼数图标 / 图表笔触未做，**仍不建议直接铺开**。
- **未做**：手绘地图（属地图页）、五档色带鱼数图标、`HourChart` 笔触手绘化（共享 `core/ui`，
  避免影响其他页）、视觉回归基准、对比度仪器测量、TalkBack 与多分辨率回归。
- **未做**：其余 3 个顶层页（MAP / FAVORITES / SETTINGS）换皮；设置页「外观·皮肤」入口仍为静态稿。
- **未做**：OPT-A-09 的皮肤数据类、切换逻辑与持久化（本文件是视觉 token 前置，不是换肤功能实现）。
- **一次被回退的试错**：曾把 `DetailScreen(fillMaxSize)` 改成 `weight(1f)` 想让模型署名行可见，
  结果把「最佳窗口 Top10」入口挤出视口；已立即回退，改为把水体母题 / 鱼形并进右下角水印，
  **最终版不动任何布局**。详见报告 §11.2。
- **不能承诺像素级 1:1**：手绘纸纹 / 套印在 Compose 里是近似还原，不是复印。
  `paperGrain(brush, strength)` 的 `strength` 可从 1.0 下调（如 0.6）以收敛颗粒强度。

## 9. 回滚与下一步

回滚链（每步都是带描述、可独立撤销的提交）：

```
7d88338  v1.15.14
ed89d39  v1.16.0 基线固化
93d3c19  清理 BOM / 编译日志
5542734  详情页皮肤试点
4bd3bce  首轮真机对拍
7c1a722  D1/D2/D3 修复 + V2 复拍
(本轮)   D5 手绘质感层 + V3 复拍
```

建议顺序：
1. 若认可 D5 质感，补手绘地图 / 图表笔触 / 五档色带鱼数图标；
2. 跑全页回归（尤其 `core/ui` 共享层）后，再把同一层主题推广到其余 3 页；
3. 最后做 OPT-A-09 换肤数据层与设置页交互——那属于功能实现，需单独授权与排期。
