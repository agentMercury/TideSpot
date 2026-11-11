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

## 5. 登记与落地现状（2026-11-10 复核）

- **台账**：本裁定已登记 `REQUIREMENTS_REGISTER.md` **CHG-38**（与 CHG-25③ 同口径，本文件补逐条明细）。
- **GAP-05 现状修正**：17 条文案**已定稿入资源**（`:core:ui/src/main/res/values/strings.xml:12-28` 的 `ts_resolving` … `ts_cancel`，与设计稿逐字一致；values-en 62/62 对齐），但全仓 `.kt` **0 处引用**——UI 仍走硬编码。剩余工作＝**接线替换**，不是新增文案。
- **CONFLICT-01 现状**：方案① **已落地**（`MainActivity` 无 NavigationBar；`AppRoot` 改 Scaffold ＋ `when(page)` ＋ 地图 `floatingActions` 浮动三按钮，UX-21 返回手势在位）。§3 红线第 1 条中的「移除底栏」动作**已完成，无需重复执行**；仅剩 edge-to-edge 视觉项。
- **GAP-01 现状**：**已落地**（manifest 为 `@mipmap/ic_launcher`，background/foreground/monochrome 四件齐全）。
- **GAP-03 现状**：无显式 `navigationBarsPadding`/`WindowInsets`，依赖 Scaffold 默认 contentWindowInsets——insets 生效无遮挡，但地图未全屏铺入系统栏区，与设计稿 edge-to-edge 口径存在视觉偏差 → **P2 视觉遗留**。
  - **客户裁定（2026-11-10）：暂不单独开工**——不构成功能缺陷（遮挡已由 Scaffold 默认 insets 消除，纯色条与底图同色而不可见），**随下次 UI 更新批次一并做**；届时改动点＝`MainActivity`（Scaffold insets）＋`MapScreen`（地图全屏、浮层单独 insets），预算 1–2 h，改后跑 map/app 模块测试。

## 6. GAP-05 待复核项（等 OPT-V11-13 完成后执行）

**归属确认（2026-11-10）**：GAP-05 的 17 条接线属 **OPT-V11-13 全量 i18n 文案外移**（`OPT_REQUIREMENTS_V11.md:133`，P2 第 6 项）。P2 其余 5 项（12/14/15/16/17）已随 CHG-37 落地，台账记「13 待专项批次」——故 **GAP-05 无需另立批次**，随 OPT-V11-13 关闭即可。

**复核触发**：OPT-V11-13 落地后（客户通知或 P2 收官时）。

**机械复核清单（逐项打勾才算闭环）**：

1. 17 个 `ts_*` key 在 `.kt` 中每 key ≥1 处 `R.string.` 引用（**基线 2026-11-10：0 处**）。
2. 旧硬编码消失：`MapScreen`「逆地理解析中…」、`MainActivity` 对话框标题「确认收藏」与「取消」、`DetailScreen`「未来最佳窗口 Top10」「记录一条高潮观测」「图表」「数值表」等。
3. 资源对齐：`values/` 与 `values-en/` 的 `ts_*` 条目数相等（基线 62/62）。
4. 冻结链未动：`BestWindowFinder` fallbackMessage、`Top10Formatter` 兜底、`:core:domain` VetoEvaluator reason、`ScoreBands` / `ConfidenceBadge` / `TideSpotTheme` 取值零变更（jj/git diff 比对）。
5. #8/#13/#14 兼容：StaleBanner 两态合并后相关测试**更新而非删除**；降级卡文案仅改 UI 层。
6. 测试：`:core:ui` / `:feature:map` / `:feature:detail` / `:app` 全绿；文案枚举锁测试在位。
7. OPT-V11-13 自有验收：红线扫描扩展（非资源文件含中文字面量=FAIL）生效；切英文后五 Tab/免责/徽标/按钮全英文且无截断。

**结论口径**：1–7 全绿才可把 GAP-05 记为「已闭环」；任一项不满足，登记偏差并回推 OPT-V11-13。

### 6.1 复核结果（2026-11-11，V1.15 / CHG-46）

| # | 项 | 结果 | 证据 |
|---|---|---|---|
| 1 | 17 key 每 key ≥1 处引用 | ✅ | 17/17（另新增 `ts_stale_with_time` 中英双语） |
| 2 | 旧硬编码消失 | ✅ | 「逆地理解析中…」「未来最佳窗口 Top10」「记录一条高潮观测」「确认收藏」全仓 0 命中 |
| 3 | 中英条目对齐 | ✅ | `values` 241 / `values-en` 241 |
| 4 | 冻结链未动 | ✅ | `ScoreBands` / `ConfidenceBadge` diff 无 0xFF 变化；`TideSpotTheme` 主色未改（仅新增 Shapes/Typography） |
| 5 | StaleBanner 两态兼容 | ✅ | 过期黄条=通用句+时间；≥48h 不可用态独立保留，测试未删改 |
| 6 | 模块测试 | ✅ | 八模块 `gradlew test` BUILD SUCCESSFUL（含 core:ui Robolectric 冒烟 8 用例） |
| 7 | i18n 红线 + 英文界面 | 🔶 部分 | `i18n_scan` FAIL=0 ✅；真机英文界面未验（`adb devices` 为空），Robolectric `qualifiers=en` 未新增断言 → 随下次真机批次 |

**GAP-05 结论**：文案定稿与接线（1–6）**已闭环**；英文真机验收（7）因设备缺席挂起，不阻塞 v1.15.0 交付。

## 7. OPT-V11-13 i18n 范围裁定（D22，2026-11-11）

**裁定人**：交付负责人（客户任务「接手完成 OPT-V11-13」授权范围内）→ **待客户复核**。

### 7.1 语言切换方式

**系统语言驱动**（`values/` 中文默认 + `values-en/` 英文），App 内**不新增语言选择器**。
依据：`OPT_REQUIREMENTS_V11.md:133-138` 只要求「中英双语准备 + N-8 日期与单位本地化」，未要求应用内切换；SRS 无语言设置项。
验证：`TideSpotStringsTest`（中）与 `EnStringsParityTest`（英）逐键锁；真机切换系统语言后重组即生效（`localTideSpotStrings()` 监听 `LocalConfiguration`）。

### 7.2 文案提供者白名单（红线扫描豁免，代码级）

| 位置 | 豁免理由 |
|---|---|
| `core/ui/.../strings/{ZhStrings,EnStrings}.kt` | 资源镜像（纯 Kotlin 侧取词），与 `values{-en}` 逐键相等测试锁死 |
| `core/domain/.../strings/{DomainCopy,ZhDomainCopy}.kt` | 纯 Kotlin domain 取词层 |
| `core/data/.../geo/T2S.kt` | 繁简归一**数据表**，非文案 |
| `core/data/.../repo/HourlyScoreStore.kt` | `preset` 中文标签**解析关键字**（协议值） |
| `core/domain/.../algorithm/ScenePreset.kt` | `label` 为 `spot.preset` 数据协议值；展示改走 `Presets.displayLabel(id, copy)` |
| `core/data/.../{DataError,CopernicusCredentialStore,OpenMeteoClient}.kt`、`TideSpotRepository.kt` | 技术诊断/开发期断言，**不上屏** |
| `core/domain/.../tide/TideCalibrator.kt` | `rejectReason` 为存库诊断列（不上屏） |
| `core/data/.../repo/SourceMetaRegistry.kt` | 数据源技术元数据（非语言敏感） |
| `feature/map/.../geo/{GeocoderApi,PlaceResolver}.kt` | 测试/搜索桩数据（地名样本） |

### 7.3 数据协议值红线（禁翻译）

- `spot.preset`：仍写中文标签（`淡水路亚/海水岸边/淡水台钓`），`HourlyScoreStore.resolvePreset` 的「id → 中文标签子串 → 水体兜底」链不变（D18 只覆盖 `groupPreset`）。
- `groupName`：用户数据原样。
- `vetoReasons` / `FactorScore.detail`：**落库文本**，新写入行跟随当前语言；历史行为迁移前中文（既有行为，不回填）。消费侧判定只认 `vetoCodes`（FO-01 口径不变）。

### 7.4 交付口径

本批（CHG-40）完成**架构 + core:ui / core:domain / feature:settings / feature:map** 全量外移；`feature:detail` / `feature:favorites` / `app` 共 276 处转**批次二**（资源与注入接口已就绪，纯替换）。批次二完成前，**7.2 #7 验收项（切英文全量无残留）不成立**，GAP-05 不得记「已闭环」。
## 8. 视觉落地进度（OPT-V115-02，2026-11-11 / CHG-47）

| 设计层 | 状态 | 工程落点 |
|---|---|---|
| S01 C02 顶部工具浮层 | ✅ | `MapScreen` TopBarAction（定位/搜索/收藏/设置）+ 搜索收起展开 |
| S01 C19 Top10 入口 | ✅ | 信息条上方 48dp Surface（:app 传 `top10Label`/`onOpenTop10`） |
| S01 浮动三按钮 | ✅ 已移除 | 定位并入顶栏；收藏/设置进顶栏；Top10 进 C19 |
| S01 C03 信息条版式 | 🔶 | 现为标题+副标题+按钮列；设计为 236×48 浮条 + 200dp 面板 |
| S01 C21/C22 底部署名/来源行 | ❌ | 尚未接入地图底部 |
| S02 TOPBAR | ✅ | 返回‹ + 名称 + 6 位坐标 |
| S02 C10 今日指数主卡 | ✅ | 88dp 档位色块 + 摘要 + 置信徽标 |
| S02 C09 图表卡容器 | ✅ | Card 包裹（高度仍 180dp，设计 210dp 待调） |
| S02 C17 位置 | ✅ | 移至 DaySelector 之上 |
| S02 C06 底部免责条 | ✅ | 56dp Surface + Sheet |
| S09 TOPBAR / C06 | ✅ | 标题 52dp 样式 + 底部免责条与 Sheet |
| S09 C16 六版块卡片化 | 🔶 | 仍为平铺版块 + 分隔线 |
| 9 屏真机中英+深色截图 | ⏳ | `adb devices` 为空，待设备 |

## 9. V1.15 收官结论（2026-11-11，CHG-46~56）

> §8 为**阶段性**记录（写于 CHG-47 时点），以下为最终状态，冲突以本节为准。

| 设计层 | 最终状态 | 说明 |
|---|---|---|
| S01 C02 顶部工具浮层 | ✅ | 白色搜索框 + 3 个 44×46 方块按钮（定位实心） |
| S01 C03 信息条 | ✅ | 236dp 浮条 + 独立操作面板（真机预览态验证） |
| S01 C19 Top10 入口 | ✅ | 深蓝 `#0D47A1` 横幅 366×48 rx14 |
| S01 C21 署名 / C22 来源行 | ✅ | C22 由 `:app` 透传 `fetchedAt`，无数据显示「—」 |
| S02 TOPBAR / C10 主卡 / C09 卡 / C17 位置 / C06 | ✅ | 深蓝主卡 + 档位胶囊 + 五段色带；Tab 分段胶囊；日期 rx18；来源行独立 |
| S03 因子分解 | ✅ | 卡化 + 因子名本地化修复 |
| S04 / S07 | ✅（客户裁定） | 以现有实现为准，不复刻结构稿细节 |
| S05 天气 | ✅ | 安全摘要卡 |
| S06 水质 | ✅ | 三卡一行（pH 已删，客户裁定） |
| S08 收藏 | ✅ | 卡片 + 窗口摘要 + 档位 + 水体类型 |
| S09 设置 | ✅ | 六版块单卡 + 底部免责条 |
| 9 屏真机中英 + 深色 | ✅ | 中文/英文/深色均留证（`.shots/f_en*`、`dk*`、`i*`） |
| 22 组件 | ✅ | 6 项收尾（C01 dp 化 / C04 20dp / 五个 Sheet 20dp / C12 复核 / C11 · C13 复用卡） |
| 深色对比度 ≥4.5:1 | ✅ | `contrast_check.mjs` FAIL=0/29 |

**唯一有意偏离**：浅色次要文字 `#8A9DB0`（2.19–2.79:1）→ `#556579`（4.68–5.96:1），见 CHG-56。

**交付物**：`E:\Trans\潮点TideSpot-v1.15.14.apk`（versionCode 19）；台账 CHG-46~56；交接见 `HANDOVER_V115.md`。

---

## 10. V1.16.0 + v3 美化 · 落地件全量刷新（2026-11-11）

### 10.1 本轮触发与用户裁定
- 触发：用户「当前工作区的工程文件是最新版本的，你根据这一版看一下 UI 图是否需要更新」，要求以工程实际情况为准。
- 用户三项决策原文：「1.正式启用 2.按你的来 3.以实际工程为准。实际上大多数跟功能有关的部分都要以现在的实际情况为准，你的任务是美化 UI」：
  - **决策① API Key / Copernicus 凭据** → 正式启用，不做占位；实现方可真实保存到 SharedPreferences。
  - **决策② 准星形态** → 由设计侧定稿：四向刻度 + 细环 + 薄荷中心点，idle / active / locked 三态。
  - **决策③ GMS 口径** → 以实际工程为准：双通道定位，`play-services-location 21.3.0`，不再声明「无 GMS 单机口径」。
- 边界再确认：**功能 / 结构 / 文案 / Tab 序 / 导航流全部以工程 v1.16.0 为准，设计侧只负责视觉美化。**
- 用户「四项都确认」：v3 四项美化方向全部采用，进入完整设计侧落地件刷新。

### 10.2 基线锁定（工程 v1.16.0 / versionCode 20）

| 事实 | 值 | 工程权威源 |
|---|---|---|
| Tab 渲染序 | 钓鱼指数 → 潮流/水位 → 天气 → 水质 → 风浪 | `WaterBodyTabTitles.titlesInTabOrder` |
| DaySelector | 52dp 高 · 两行 · 圆角 18 · min 宽 56 · 间距 6 | `DaySelector.kt` |
| 水质卡 | 1 行 × 3 卡（水温 / 溶解氧 / 浊度，pH 已删）· 卡高 112dp | `DetailScreen.kt` |
| 信息条 | 236dp 浮条 + 独立操作面板 | `DetailScreen.kt` |
| 地图底图 | CARTO Voyager | 工程配置 |
| APP 图标 | `@mipmap/ic_launcher`，adaptive 五件已落地 | `AndroidManifest.xml` + mipmap |
| edge-to-edge | `contentWindowInsets = WindowInsets(0,0,0,0)`，各屏自行 padding | `MainActivity.kt` |
| 定位 | 双通道，不再声明无 GMS 单机口径 | `app/build.gradle.kts` |
| 主题 | Light / Dark + Shapes(8/12/16/20/20) + Typography 工程既有 | `TideSpotTheme.kt` |

### 10.3 DECISION-V3-BEAUTY（四项美化，用户全采纳）

| 编号 | 美化项 | v3 结论 |
|---|---|---|
| V3-1 | 准星形态 | 细环 + 四向刻度 + 薄荷中心点；三态；与图标前景层同源母题 |
| V3-2 | 视觉点缀 | 薄荷色 `#80CBC4` 作唯一强调点缀，不新增主题色、不派生深色副本 |
| V3-3 | 收藏卡 | 白卡 + 左侧五档色带，替代 v2 深色卡 |
| V3-4 | 主卡 | 深蓝 `#0D47A1` 轻微渐变 + 48sp 分数 + 档位胶囊 + 五段色带 |
| 另 | 卡片层级 | 三级 surface 层级 + 柔和阴影；深色为独立设计（非浅色反色） |

- **冻结红线**：五档色（`ScoreBands.kt`）、六徽标色（`ConfidenceBadge.kt`）、4 主色（`TideSpotTheme.kt`）一格未动，未进 `MaterialTheme`、未派生深色副本。
- 免责恒 5 条、第 5 条为工程 D11「现场校潮」长文；文案唯一入口 `TideSpotStrings.kt`；浅色次要文字对比度 ≥ 4.5:1（CHG-56）。

### 10.4 v2 阻塞项关闭状态

- **关闭** CONFLICT-01（底部 `NavigationBar` 已删，地图优先导航）、CONFLICT-02（免责第 5 条以工程 D11 为准）、CONFLICT-03（源更新时间显示「—」，不硬编码）。
- **关闭** GAP-01~07（图标已落地 / insets 归零 / Shapes+Typography 集中 / 文案接线 / 深色与结构稿已补）。
- **关闭** DECISION-API-KEY / DECISION-CROSSHAIR / DECISION-GMS / DECISION-V3-BEAUTY。
- **当前无待决冲突**；`mapping/design-to-code.json → resolved_decisions` 为权威记录。

### 10.5 非阻塞遗留（open_items）

| 编号 | 项 | 说明 |
|---|---|---|
| P2-01 | 地图铺满系统栏区 | edge-to-edge 下地图延伸至状态栏 / 导航栏，需真机微调 padding |
| P2-02 | 英文真机验收 | i18n 文案已接线，待英文 locale 真机走查 |
| FO-03 | v3 美化 token 集中化 | 薄荷点缀 / 阴影 / 渐变建议收进 `TideSpotTheme` 或独立 token 文件，避免散落硬编码 |

### 10.6 交付边界

- 本轮只刷新 `design-handoff/` 离线包至 **v3.0.0**；未修改 `tidespot-android/` 任何源码，未产出 APK / 构建产物。
- 图标 SVG 为视觉升级替换源稿，转 vector drawable 由实现方完成。
- 验收口径见 `acceptance/acceptance.md`；包完整性（bytes + SHA-256）见 `manifest.json`。
- v2 视觉资产已归档至工作区外 `weatherfishcheck-trash-20261110/.m4tmp/v3/v2-assets-archive/`，不在交付包内。
