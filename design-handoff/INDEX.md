# 潮点 TideSpot · UI v2 设计侧落地件

> **离线交接包入口**。v2 效果图已于 2026-11-09 由用户确认，本包是确认后的完整设计侧交付。
> 全部文件为本地文件，**无需联网、无需登录、无需 Ardot 画布、无需任何外部服务**。

## 0. 一句话说明

这是一套给「其他 agent / 实现方」离线消费的设计规格包：机器可读 JSON（token / 组件状态 / 屏组 / 文案 / 稿↔代码映射）+ 人读 MD + 可编辑 SVG 图标源稿 + 本地 PNG/SVG 视觉基准 + 离线预览页 + 验收清单。

**它不是**：Compose 代码、可编译 `res/` 资源、把设计稿自动同步到 app 的工具。本包不修改 `tidespot-android/` 下任何源码。

## 1. 离线使用说明

| 项 | 说明 |
|---|---|
| 网络依赖 | 无。所有 JSON / MD / SVG / PNG / HTML 均为本地文件，路径全部相对本目录 |
| 画布依赖 | 无。`assets/` 已从 Ardot v2 导出为本地 PNG + SVG；在线画布只是当初的生成来源，不是交付件的一部分 |
| 路径基准 | **文档中的仓库相对路径以本目录的上一级（仓库根）为基准**；包内引用一律相对 `design-handoff/` |
| 文件名 | 全 ASCII，避免跨 agent / 跨系统编码问题；文件内容为中文 |
| 入口 | 人读 `INDEX.md` → `preview.html`；机器读 `manifest.json` |
| 校验 | `manifest.json` 含每个文件的字节数与 SHA-256，可用于整包拷贝后完整性自检 |

打开 `preview.html`（双击即可，不需起服务）能一次看完整套视觉稿。

## 2. 文件树

```
design-handoff/
├── INDEX.md                          ← 本文件，入口
├── manifest.json                      ← 机器可读全包清单 + SHA-256
├── preview.html                       ← 离线拼板预览（无外链）
├── tokens/
│   ├── design-tokens.json             ★ 颜色/字阶/间距/圆角/安全区（机器可读）
│   └── design-tokens.md               人读速查 + 冻结红线
├── components/
│   ├── components.json               ★ 22 个组件的完整状态矩阵
│   └── components.md                 人读速查 + 易错点
├── screens/
│   ├── screens.json                  ★ 9 屏 + 3 叠加层 + 4 深色变体 + 24 行数值表 + 标注
│   └── screens.md                    人读屏组规格 / 导航 / 验收要点
├── copy/
│   ├── copy.json                     ★ 全部用户可见文案字典（keys）
│   └── copy.md                       人读文案表 + 与需求 §9.6 的差异说明
├── mapping/
│   ├── design-to-code.json           ★ 稿↔代码映射 + conflicts + gaps
│   └── design-to-code.md             人读映射 / 冲突 / 落地顺序
├── icon/
│   ├── tidespot-icon-background.svg   adaptive icon 背景层（108 dp）
│   ├── tidespot-icon-foreground.svg   adaptive icon 前景层（108 dp / 72 dp 安全区）
│   ├── tidespot-icon-monochrome.svg   单色通知小图标（24 dp）
│   ├── tidespot-icon-preview.svg      合成预览（192 dp）
│   └── app-icon-spec.md               自适应图标规格 + 落地映射 + 校验清单
├── acceptance/
│   └── acceptance.md                 标注说明 + 全局/逐屏/图标/合规验收清单
└── assets/
    ├── board-v2.png                  v2 整板效果图 1700 × 3025 ✅
    ├── screen-01-map.png / .svg      S01 地图选点 + 确认收藏对话框 ✅
    ├── screen-02-score.png / .svg    S02 钓鱼指数 + Top10 Sheet ✅
    ├── screen-03-settings.png / .svg S09 设置与隐私 ✅
    ├── screen-04-dark-score.png/.svg DV01 深色钓鱼指数 ✅
    ├── icon-system.png / .svg        图标系统效果稿 ✅
    ├── visual-baseline.svg           视觉基调规范卡（主题/五档/六徽标/字阶）
    ├── dark-adaptation.svg           深色对照 + edge-to-edge 适配规范
    └── derived/                      6 张按规格推导的补充屏结构稿 🔶
        ├── S03-factor-breakdown.svg
        ├── S04-inland-wave.svg
        ├── S05-weather.svg
        ├── S06-water-quality.svg
        ├── S07-tide.svg
        └── S08-favorites.svg
```

✅ = 用户已确认的 v2 像素稿（视觉基准）　🔶 = 按 `screens.json` 推导的结构稿（表达层级与占位，细节以 ✅ + token 为准）

## 3. 读取顺序（给其他 agent）

1. `INDEX.md`（本文件）→ `manifest.json`（确认文件齐全）
2. **`mapping/design-to-code.json → conflicts`** ← 必须先读，有未决策的导航结构冲突
3. `tokens/design-tokens.json`（色值、尺寸、安全区；注意冻结红线）
4. `copy/copy.json → keys`（所有文案；区分 frozen / design_proposed）
5. `components/components.json`（22 个组件 × 必覆盖状态）
6. `screens/screens.json`（9 屏 `layers[].box` + `layers[].c` + 叠加层 + 24 行数值表）
7. `assets/` 视觉基准 + `preview.html`（对照）
8. `icon/app-icon-spec.md` + `icon/*.svg`（图标落地）
9. `acceptance/acceptance.md`（验收）

**权威性顺序**（冲突时）：工程冻结 Kotlin 单源（`ScoreBands.kt` / `ConfidenceBadge.kt` / `TideSpotStrings.kt` / `TideSpotTheme.kt`）＞ 本包 JSON ＞ 本包 MD ＞ ✅ 视觉稿 ＞ 🔶 结构稿 ＞ 需求文档 §8/§9.6（交互与合规口径）。

## 4. 可直接复制给其他 agent 的指令

> 请读取 `design-handoff/INDEX.md` 与 `design-handoff/manifest.json`，按 INDEX 第 3 节的顺序消费这套离线设计规格包。
> 该包完全自包含，不要访问网络、不要打开 Ardot 画布、不要依赖本会话上下文。
> 色值以 `tokens/design-tokens.json` 为准，文案以 `copy/copy.json` 的 `keys` 为准（frozen 引用工程常量，design_proposed 需产品确认），
> 屏结构按 `screens/screens.json` 的 `layers[].c` 关联 `components/components.json`，工程落点查 `mapping/design-to-code.json`。
> **动手前先读 `mapping/design-to-code.json` 的 `conflicts`，CONFLICT-01（底部 NavigationBar vs 地图优先导航）需产品决策。**
> 不要修改 `ScoreBands.kt` / `ConfidenceBadge.kt` / `TideSpotTheme.kt` 的取值，不要新增 XML layout，不要把稿中的示例分数/时间/点位当成业务数据。
> 完成后按 `acceptance/acceptance.md` 逐项自检，并跑现有测试保持零行为漂移。

## 5. 决策前置项（重要）

| ID | 内容 | 决策人 |
|---|---|---|
| CONFLICT-01 | 工程 `MainActivity` 有底部四项 `NavigationBar`（地图/收藏/详情/设置）；v2 设计稿是**地图优先、无通用底部 Tab Bar**。二选一：工程改走地图入口，或设计侧补一版带底栏的屏组。**未决策前不要按稿删底栏。** | 产品 / 用户 |
| CONFLICT-02 | 需求 §9.6 第 5 条原文 vs 工程 M6/D11「现场校潮」长文。本包以工程版为准，§9.6 的「未校正」由徽标承载。 | 产品 / 需求负责人 |
| CONFLICT-03 | 稿件「源更新 06:00 UTC」是版式示意；工程因数据层未透出显示「—」，禁止硬编码编造。 | 数据层负责人 |
| GAP-05 | 17 条 `design_proposed` 文案工程未定义，需确认后统一进 `TideSpotStrings.kt`。 | 产品 |

## 6. 版本与依据

| 项 | 值 |
|---|---|
| 版本 | v2.0.0（UI v2 确认后的落地件） |
| 依据 | `TIDESPORT_REQUIREMENTS.md` v1.3 §8 / §9.6；`REQUIREMENTS_REGISTER.md` CHG-06~16；`PROGRESS_TRACKER.md` M6 退出快照；`HANDOVER.md`；实际读取工程 `TideSpotTheme.kt` / `ScoreBands.kt` / `ConfidenceBadge.kt` / `TideSpotStrings.kt` / `SettingsScreen.kt` / `MainActivity.kt` / `DetailScreen.kt` / `MapScreen.kt` / `AndroidManifest.xml` |
| 视觉源 | Ardot v2 效果图（`https://ardot.tencent.com/file/745521310890154`）——**仅生成来源，不是交付依赖** |
| 关键结论 | 工程主色已切到 `#1565C0 / #00695C`（v1 青绿稿作废）；五档分数色仍为冻结值；六徽标色值已重定；APP 图标此前为系统默认，本版首次设计 |

## 7. 边界

- 不含任何 Kotlin / Compose 代码，不含可编译 `res/` 资源；图标 SVG 是设计源稿，转 vector drawable 由实现方完成。
- 不含设计稿 ↔ app 的自动同步机制：Ardot 画布与 Android 工程之间没有自动通道，落地需实现方按本包规格改 UI。
- 未修改 `tidespot-android/` 下任何源码；未产出 APK 或构建产物。
- 🔶 结构稿不是逐屏确认过的像素精稿；如需像素级验收，可在 `assets/derived/` 基础上再细化。
