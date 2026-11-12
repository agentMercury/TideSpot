# A · 复古户外（VINTAGE OUTDOOR）

手绘风格视觉概念稿 · 基于工程 v1.16.0 · 与其他三版完全独立。

## 风格来源
- 图5 微信图片_20261112105649_5_163.jpg（MOUNTAIN ADVENTURE）
- 图6 微信图片_20261112105650_6_163.jpg（ADVENTURE IS CALLING）

## 视觉语言
墨绿做旧纸底 + 丝网颗粒噪点 + 藏蓝线描山峦/松林/日轮/星芒 + 芥末黄衬线大标题 + 套印错位阴影 + 双线边框

**鱼形母题**：版画套印鱼：限色块 + 错位描边 + 网点鳍条

**适合**：户外硬朗、品牌调性最鲜明；复古质感最强

## 双水体（详情页各一版，一眼可辨）
- `screens/detail-sea.png` — 海洋 / 近海：藏蓝/墨绿双水体，浪+贝+海鸟母题，第二 Tab「潮流」，图表「潮高·潮流」
- `screens/detail-inland.png` — 内陆 / 湖泊：山+日+芦苇母题，第二 Tab「水位（无潮汐）」，图表「水位·无潮汐」

## 文件
- `screens/` — 7 张 2x PNG（780×1688）：详情页×2、地图×2、收藏、设置、深色详情
- `sources/` — 对应 7 份自包含 HTML 源稿（SVG 内联、系统字体、无外链，可离线双击改色改字）
- `spec.json` — 机器可读规格 + 冻结契约 + SHA-256
- `preview.html` — 本版 7 屏缩略预览

---

## 【扩展】完整 UI（28 屏）+ APP 功能优化需求

在基础 7 屏之上，按设计侧完整清单（`design-handoff/screens/screens.json` 9 屏 + 6 叠层 + 4 深色）补齐到**覆盖全功能**的 28 屏：

| 组 | 屏 | 说明 |
|---|---|---|
| ① 基础主屏 · 7 | `screens/`（设置页已改版，余 6 张原样） | 详情海/陆、地图海/陆、收藏、设置（OPT-A-01/02/09）、深色详情 |
| ② 详情 5 Tab + 因子分解 · 7 | `screens_full/` | 潮流·海 / 水位·内陆、天气、水质 1×3、风浪·海 / 湖面风·内陆、因子分解展开 |
| ③ 6 叠层 · 6 | `screens_full/` | OV01 Top10 时段筛选、OV02 免责 5 条、OV03 收藏确认、OV04 水质录入、OV05 星标气泡、OV06 删除确认 |
| ④ 状态态 · 6 | `screens_full/` | 离线 / 陈旧 / 48h 不可用 / 分维降级、收藏空态、多选批量 |
| ⑤ 深色变体 · 2 | `screens_full/` | 深色地图、深色收藏（深色独立 token，非机械反转） |

**新增交付文件**
- `index-full.html` — 自包含索引页（28 屏分组拼览 + 需求摘要，双击离线看）
- `APP功能优化需求.md` — 功能差异清单 + 优化需求（逐条现状 / 代码证据 / 目标 / 验收 / 工程落点）
- `SKIN-DESIGN-SPEC.md` — 皮肤（Skin）设计规范：三轴正交 / 可变 vs 冻结 token / 新增皮肤 A1–A10 接入清单 / 换肤验收表
- `feature-gaps.json` — 机器可读差异表（9 条：3 ui_new + 2 ui_remove + 2 mismatch + 2 code_ahead）
- `screens_full/*.png`（21 张 2x 780×1688）+ `sources_full/*.html`（21 份自包含离线源稿）

**对照结论（2026-11-12 修订）**：工程 v1.16.0 功能完成度高，**9 条差异 / 7 条转需求**（P1×2：深色手动开关、地图 Top10 入口语义；P2×5：四态视觉分级、fetch_log 入口、换皮肤、下线用例/物种/权重、下线 API Key），**2 条仅记录**（批量不做导出、星标气泡/现场校潮/自选时段等工程已实现）。冻结契约未改动。OPT-A-01/02 由「升级/接线」改为**从设置页删除**；新增 **OPT-A-09 换皮肤**（首版仅内置 A，B/C/D 不并入），扩展规范见 `SKIN-DESIGN-SPEC.md`。

> **设置页改版（2026-11-12，需求已确认）**：`sources/settings.html` / `screens/settings.png` 已按 OPT-A-01/02/09 更新——删除「用例·物种·权重」「API Key」两版块、新增「外观·皮肤」入口（当前 = 手绘 A 复古户外 + 敬请期待占位）。独立外观选择页本轮未做（「只做需求 + 入口」）。屏数仍 28。

**复现**：`generator/engine.py` 先生成 4 版 base 7 屏 → `generator/engine_full.py` 补 A 版 21 张 `sources_full/*.html` 并覆盖改版 `sources/settings.html`（`render_settings_revised()`，不动 B/C/D）→ Edge 2x 截 PNG → `generator/gen_full_index.py` → `index-full.html`。

## 【工程侧】详情页 Compose 试点与 D5 质感层（2026-11-12）

设计稿之外，工程侧已按「**只做美化、不动功能**」在**详情页一屏**落地：

- `tidespot-android/core/ui/.../theme/VintageOutdoorTheme.kt` — 4 套色板（海/陆 × 浅/深）+ 非对称圆角 + 衬线字阶
- `tidespot-android/core/ui/.../theme/HandDrawn.kt` 与 `core/ui/src/main/res/drawable*` — D5 纸纹颗粒 / 鱼形装饰 / 套印错位 / 手绘描边
- `generator/gen_android_assets.py` — 上述质感资源的可复现生成脚本（重跑 SHA 一致）
- 完整改动清单 / token 映射 / 验收：`Compose试点落地说明.md`
- 真机四态对拍、像素证据、差异清单：`device-compare/真机对拍报告.md` §10（D1–D3）与 §11（D5）

**① 阶段（V4，2026-11-12）**：用户放开「可按设计图调整布局」权限（红线：不增减功能 / 入口、不改算法）。
已补五档鱼数色带（5→1 + 档位文字 + 选中高亮）、五个 Tab 鱼图标、图表手绘笔触
（`HourChart` 默认关闭参数，仅详情页开启）；主卡高度与 Top10 位置零位移，
入口红线校验 `36 PASS / 0 FAIL`，见 `device-compare/真机对拍报告.md` §12。
**手绘地图带经用户确认本轮不做**（设计稿静态 SVG 带「拖动选点」误导文案，
工程地图页为 CARTO 真实瓦片），地图页零改动。

结论：详情页视觉语言已自洽；**手绘地图带、其余 3 页**仍未做，不建议直接铺开。
**设计稿本身（28 屏 / `index-full.html` / `spec.json`）未因工程侧落地而改动**。

## 边界
设计稿为纯视觉概念稿 + 需求清单；示例数值非业务数据，档位恒带文字。
工程侧落地仅限 `tidespot-android/` 的详情页一屏（视觉 token 与质感层），
功能、文案、Tab 序、导航、冻结语义色（`ScoreBands.kt` / `ConfidenceBadge.kt`）一律未动；
设置页「外观·皮肤」仍是静态稿，OPT-A-09 换肤数据层与切换逻辑未实现。
详见 `Compose试点落地说明.md`。功能结论均来自 2026-11-12 实际代码核对。
