# 潮点 TideSpot · UI v3 美化设计侧落地件

> **离线交接包入口**。基线 = 工程 **v1.16.0（versionCode 20）**；v3 效果图已于 2026-11-11 由用户确认（四项美化全部采用），本包是确认后的完整设计侧交付。
> 全部文件为本地文件，**无需联网、无需登录、无需 Ardot 画布、无需任何外部服务**。
> **v3 定位：功能 / 结构 / 文案 / Tab 序 / 导航流全部以工程 v1.16.0 为准，只做视觉美化。**

## 0. 一句话说明

这是一套给「其他 agent / 实现方」离线消费的设计规格包：机器可读 JSON（token / 组件状态 / 屏组 / 文案 / 稿↔代码映射）+ 人读 MD + 可编辑 SVG 图标源稿 + 本地 2x PNG/SVG 视觉基准 + 自包含离线预览 + 验收清单。

**它不是**：Compose 代码、可编译 `res/` 资源、把设计稿自动同步到 app 的工具。本包不修改 `tidespot-android/` 下任何源码。

## 1. 离线使用说明

| 项 | 说明 |
|---|---|
| 网络依赖 | 无。所有 JSON / MD / SVG / PNG / HTML 均为本地文件，路径全部相对本目录 |
| 画布依赖 | 无。`assets/v3/` 已从 v3 效果生成器导出为本地 2x PNG；整板另有自包含 `assets/board-v3.html`（内嵌底图） |
| 路径基准 | **文档中的仓库相对路径以本目录的上一级（仓库根）为基准**；包内引用一律相对 `design-handoff/` |
| 文件名 | 全 ASCII，避免跨 agent / 跨系统编码问题；文件内容为中文 |
| 入口 | 人读 `INDEX.md` → `preview.html`；机器读 `manifest.json` |
| 校验 | `manifest.json` 含每个文件的字节数与 SHA-256，可用于整包拷贝后完整性自检 |

双击 `preview.html`（不需起服务）能一次看完整套视觉稿；`assets/board-v3.html` 是内嵌底图的自包含整板，同样可离线双击。

## 2. 文件树

```
design-handoff/
├── INDEX.md                          ← 本文件，入口
├── manifest.json                      ← 机器可读全包清单 + SHA-256
├── preview.html                       ← 离线拼板预览（相对路径，无外链）
├── DECISIONS.md                       ← 决策记录（v2 裁定 + v1.16 收官 + v3 四项确认）
├── tokens/
│   ├── design-tokens.json            ★ 颜色/字阶/间距/圆角/安全区 + v3 美化 token
│   └── design-tokens.md             人读速查 + 冻结红线
├── components/
│   ├── components.json               ★ 25 个组件完整状态矩阵
│   └── components.md                人读速查 + 8 条易错点
├── screens/
│   ├── screens.json                  ★ 9 屏 + 6 叠加层 + 4 深色变体 + 24 行数值表 + 标注
│   └── screens.md                    人读屏组规格 / 导航 / 验收要点
├── copy/
│   ├── copy.json                     ★ 全部用户可见文案字典（keys，中英双语基线）
│   └── copy.md                       人读文案表 + 需求 §9.6 差异说明
├── mapping/
│   ├── design-to-code.json           ★ 稿↔代码映射 + 已关闭决策 + 遗留项
│   └── design-to-code.md             人读映射 / 落地顺序 / 红线
├── icon/
│   ├── tidespot-icon-background.svg   背景层（108 dp · 蓝渐变 + 双潮线）
│   ├── tidespot-icon-foreground.svg   前景层（108 dp · 细环 + 四向刻度 + 薄荷点）
│   ├── tidespot-icon-monochrome.svg   24 dp 通知单色图标
│   ├── tidespot-icon-monochrome-108.svg  Android 13+ adaptive <monochrome>（108 dp）
│   ├── tidespot-icon-preview.svg      合成预览（192 dp）
│   └── app-icon-spec.md               图标规格 + 替换映射 + 校验清单
├── acceptance/
│   └── acceptance.md                 标注说明 + 全局/逐屏/图标/合规验收清单
└── assets/
    ├── board-v3.png / .html          v3 整板效果图（1400×5240）/ 自包含离线整板 ✅
    ├── v3/                           6 张关键屏 2x PNG（780×1688）✅
    │   ├── screen-01-map.png          S01 地图选点（准星三态 + 星标 + 236 浮条 + Top10）
    │   ├── screen-02-score.png        S02 钓鱼指数（52dp 两行 + 渐变主卡 + 图表）
    │   ├── screen-06-water.png        S06 水质（1×3 卡 + 录入实测）
    │   ├── screen-08-favorites.png    S08 收藏（白卡色带 + 批量 + 排序）
    │   ├── screen-09-settings.png     S09 设置（三选 + Copernicus 凭据 + API Key）
    │   └── screen-dv01-dark-score.png DV01 深色钓鱼指数（独立配色）
    └── derived/                      4 张补充屏 v3 风格结构稿 🔶
        ├── S03-factor-breakdown.svg
        ├── S04-inland-wave.svg
        ├── S05-weather.svg
        └── S07-tide.svg
```

✅ = 用户已确认的 v3 2x 像素基准（视觉基准）　🔶 = 按 `screens.json` + v3 语言推导的结构稿（表达层级与占位，细节以 ✅ + token 为准）

## 3. 读取顺序（给其他 agent）

1. `INDEX.md`（本文件）→ `manifest.json`（确认文件齐全）
2. `mapping/design-to-code.json → resolved_decisions`（**确认 v2 阻塞项已全关闭，无待决冲突**）
3. `tokens/design-tokens.json`（色值 / Shapes / Typography / v3 美化 token；注意冻结红线）
4. `copy/copy.json → keys`（所有文案；Tab 序取 `tab.sea/.inland/.land` = 渲染序）
5. `components/components.json`（25 个组件 × 必覆盖状态）
6. `screens/screens.json`（9 屏 `layers[].box` + `layers[].c` + 6 叠加层 + 24 行数值表）
7. `assets/v3/` 6 张基准屏 + `assets/board-v3.png/.html` + `preview.html`（对照）
8. `icon/app-icon-spec.md` + `icon/*.svg`（图标替换）
9. `acceptance/acceptance.md`（验收）

**权威性顺序**（冲突时）：工程冻结 Kotlin 单源（`ScoreBands.kt` / `ConfidenceBadge.kt` / `TideSpotStrings.kt` / `TideSpotTheme.kt`，含 Shapes/Typography）＞ 本包 JSON ＞ 本包 MD ＞ ✅ v3 像素基准 ＞ 🔶 结构稿 ＞ 需求文档 §8/§9.6（交互与合规口径）。

## 4. 可直接复制给其他 agent 的指令

> 请读取 `design-handoff/INDEX.md` 与 `design-handoff/manifest.json`，按 INDEX 第 3 节的顺序消费这套离线设计规格包。
> 该包完全自包含，不要访问网络、不要打开 Ardot 画布、不要依赖本会话上下文。
> **基线为工程 v1.16.0：功能 / 结构 / 文案 / Tab 序 / 导航流全部以工程为准，只做视觉美化。**
> 色值以 `tokens/design-tokens.json` 为准（含 v3 美化 token：三级层级 / 薄荷点缀 / 准星三态 / 主卡渐变 / 收藏白卡色带），文案以 `copy/copy.json → keys` 为准（frozen 引用工程常量），
> 屏结构按 `screens/screens.json → layers[].c` 关联 `components/components.json`，工程落点查 `mapping/design-to-code.json`，视觉对照 `assets/v3/*.png`。
> **Tab 渲染序恒 5 项 = 钓鱼指数 → 潮流(水位) → 天气 → 水质 → 风浪（titlesInTabOrder）；DaySelector 52dp 两行；水质 1×3 删 pH；信息条 236dp 浮条。**
> v2 的 CONFLICT-01~03 与 GAP-01~07 已全部关闭，无待决冲突；剩余 P2-01（地图铺满系统栏区）/ P2-02（英文真机）不阻塞。
> 不要修改 `ScoreBands.kt` / `ConfidenceBadge.kt` / `TideSpotTheme.kt` 的取值，不要新增 XML layout，不要把稿中的示例分数/时间/点位/源更新示意值当成业务数据；点星标不移动准星。
> 完成后按 `acceptance/acceptance.md` 逐项自检，并跑现有测试保持零行为漂移。

## 5. 已关闭决策与遗留项

| 类别 | 状态 |
|---|---|
| v2 CONFLICT-01/02/03 | ✅ 全关闭（底栏已删 / 免责以工程 D11 为准 / 源更新时间显示「—」） |
| v2 GAP-01~07 | ✅ 全关闭（图标已落地 / insets 归零 / Shapes+Typography 集中 / 文案 17/17 接线 / 深色与结构稿已补） |
| 决策① API Key/Copernicus | ✅ 用户「正式启用」，真实可保存 |
| 决策② 准星形态 | ✅ 用户「按你的来」，四向刻度 + 细环 + 薄荷点三态，需改 Compose Canvas |
| 决策③ GMS 口径 | ✅ 用户「以实际工程为准」，双通道定位，验收 AAK-AN00 / Android 16 |
| v3 四项美化 | ✅ 用户全部采用；冻结色（五档/六徽标/4 主色）一格未动 |
| P2-01 / P2-02 / FO-03 | 🔶 非阻塞遗留（详情见 `mapping/design-to-code.json → open_items`） |

## 6. 版本与依据

| 项 | 值 |
|---|---|
| 版本 | **v3.0.0**（UI v3 美化确认后的落地件；上一版 v2.0.0） |
| 工程基线 | v1.16.0 / versionCode 20（V1.2 需求池；`app/build.gradle.kts`） |
| 依据 | `tidespot-android` 实际源码（TideSpotTheme / ScoreBands / ConfidenceBadge / TideSpotStrings / WaterBodyTabTitles / DaySelector / DetailScreen / SettingsScreen / MainActivity / AndroidManifest）+ `.shots/` v16 系列真机实拍 + `TIDESPORT_REQUIREMENTS.md` §8/§9.6 |
| 视觉源 | v3 效果生成器（`潮点TideSpot-UIv3-美化效果图.png`，1400×5240）——**仅生成来源，不是交付依赖**；已导出为本地 2x PNG + 自包含 HTML |
| 关键结论 | 主色 `#1565C0/#00695C`、Shapes/Typography 工程既有；Tab 渲染序、DaySelector 52dp、水质 1×3、信息条 236dp 等以工程为准；五档/六徽标冻结色不动；薄荷点缀与准星三态为 v3 新增视觉语言 |

## 7. 边界

- 不含任何 Kotlin / Compose 代码，不含可编译 `res/` 资源；图标 SVG 是设计源稿，转 vector drawable 由实现方完成。
- 不含设计稿 ↔ app 的自动同步机制：Ardot 画布与 Android 工程之间没有自动通道，落地需实现方按本包规格改 UI。
- 未修改 `tidespot-android/` 下任何源码；未产出 APK 或构建产物。
- 🔶 结构稿不是逐屏确认过的像素精稿；如需像素级验收，以 `assets/v3/` 6 张基准屏 + `tokens` 为准。
