# 潮点 TideSpot · 皮肤开发标准（Skin Developer Standard）

> **面向对象**：未来要**新增一套皮肤**或**修改现有皮肤**的 Agent / 工程师。
> **与 `SKIN-DESIGN-SPEC.md` 的关系**：那份是**契约**（什么能换、什么绝不能换）；本文是**执行手册**（照着做就能产出一套合格皮肤）。
> **一句话**：**皮肤 = 一组可替换的视觉 token；业务 Composable 零改动。**
> **版本**：1.5 ｜ 2026-11-15 ｜ 基线皮肤：手绘 A · 复古户外 + D · 路亚图鉴（两款内置）
> **v1.5.1 变更**：r6 真机复拍校准——采样区改左右边距窄条（旧「下部留白」区会被卡片顶到）、
> 必须传原始 PNG（760px JPG 会让颜色数虚高 3~5 倍）；补实测结论与设计稿纸底不一致这一新损耗点。
> **v1.5 变更**：CHG-93 视觉对齐实战——设计稿定稿红线、设计源→工程四个高频损耗点、
> 结构层授权边界（路径 B）、纯装饰节点豁免口径、量化验收工具链写进 §10；反模式补 15–18。
> **v1.4 变更**：D 路亚图鉴设计 + 工程双落地——30 屏设计稿（`engine.py STYLES` 参数化，`build_style.py` 一键复现）+ 工程 `d-fieldguide`（4 套 ColorScheme + Shapes/Typography，业务零改动）；补 §9 D 实录与对比度校准。
> **v1.1 变更**：v1.0 是「设计假设 + 参考骨架（未编译）」；本版由 A 的**全屏落地结果反向校准**——字段、路径、持久化键、门禁命令、踩坑全部对应真实代码。凡与 v1.0 冲突处，以本版为准。
> **v1.3 变更**：设置页样式改四选（`MapStyleChoice` 含「跟随主题」），更正 §4.4.1 的「chip 与生效值同口径」为「与用户表态同口径」。
> **v1.2 变更**：补 §4.4.1「地图底图 = 第 4 个跟随轴」——底图不属于皮肤 token，但必须跟明暗走；记录 `mapStyleExplicit` 判定与迁移坑。反模式补 13/14。

---

## 0. 适用与前置

- **适用**：新增第 2、3… 款内置皮肤；把探索稿（B/C/D）正式皮肤化（需先经产品决策，默认**不并入**）。
- **前置**：读 `SKIN-DESIGN-SPEC.md` §1 三轴正交、§2 可变/冻结清单；读 `UI落地交接文档.md` §3 目标架构。
- **不要做**：把「海洋/内陆两套配色」当两款皮肤；用改布局/改文案来「做皮肤」；伪造不可点的可切换样式。
- **当前形态**：皮肤切换入口 = **设置页「皮肤」下拉**（按名字选），**不做独立选择页**（用户 2026-11-13 决策）。

---

## 1. 心智模型（先记这 3 行）

1. **三轴正交**：`最终视觉 = 皮肤token(水体) × 明暗模式`；皮肤 / 水体 / 明暗各自独立持久化，互不重置。
2. **每款皮肤必须自带海 + 陆两套色板**，并在其上派生浅/深 → 共 **4 套 ColorScheme**。
3. **皮肤只换「长什么样」**；「是什么 / 有几个 / 叫什么 / 排第几」一律冻结（见 §6.2）。

---

## 2. 交付物清单（一套皮肤 = 这些）

### 2.1 设计侧
- [ ] `generator/engine.py` 的 `STYLES` 新增该风格配置（§3 全量 token）
- [ ] `engine_full.py` 渲染出**同一套 30 屏**（复用组件，不复制布局）
- [ ] `screens/`(7) + `screens_full/`(23) 的 2x PNG（780×1688）
- [ ] `sources/` + `sources_full/` 自包含 HTML
- [ ] 更新 `spec.json`（palette / 屏 SHA / `skin_system` 条目）、`index-full.html`、`README.md`
- [ ] `selfcheck_full.py` 全绿

> **探索稿例外（D 路亚图鉴）**：`idea-explorations/v3-fieldguide` 只交付 3 帧详情首屏探索稿，不是 30 屏。
> 本轮工程皮肤只取其中的色板 / 形状 / 字阶，30 屏设计侧交付列为后续项；**工程侧已可切换、可验收**。

### 2.2 工程侧（**只有 3 个文件**）
- [ ] `core/ui/theme/TideSpotSkin.kt`：新增 4 套 `ColorScheme` + 1 个 `TideSpotSkin` 实例
- [ ] 同文件 `TideSpotSkins.all` 追加该实例（**唯一扩展点**）
- [ ] `core/ui/src/main/res/drawable*`：鱼形 / 母题 / 纸纹资源（命名见 §5）；**画风未变可直接复用现有资源**（D 复用 A 的 `ic_fish/wave/mount_vintage`，零新增）
- [ ] **不改**任何 `feature:*` 屏、`TideSpotTheme`、`SettingsScreen`、`AppSettings`——它们在加皮肤时应当零改动

---

## 3. 设计侧标准：怎么产出一套新皮肤

### 3.1 在 `engine.py` 的 `STYLES` 新增风格配置

一套皮肤 = 一份参数表，**必须覆盖海/陆 × 浅/深**：

| 分组 | 字段 | 说明 |
|---|---|---|
| 色板·海洋（浅） | `bg bg2 ink primary accent accent2 card card2 white green muted line map_land map_water map_road` | `band` 五档为冻结语义色，此处仅占位 |
| 色板·海洋（深） | 同上全套 | 独立调色，**非机械反转** |
| 色板·内陆（浅/深） | 同上全套 | 暖绿调，保持「海冷陆暖」六重区分 |
| 字体 | `display font / body font / num&caps / track` | 例：A = Georgia 700 + KaiTi |
| 形状 | `radius（非对称）/ stroke_w` | 例：A = `16 20 15 19` + 2.2px |
| 质感 | `paper_grain / paper_tint / base_freq / sketch_scale / print_offset` | 例：A = 0.16 / 套印 on |
| 装饰 | `fish_style / motif_sea / motif_inland / tape` | 例：A = woodcut / 浪贝鸟 / 山日芦苇 |

> **硬性**：海洋与内陆**各一套**，六重区分（徽标/配色冷暖/鱼种/第二 Tab/图表单位/地点）必须成立。

### 3.2 用 `engine_full.py` 渲染（不复制布局）
- `engine_full.py` 已 `import engine as E`，共享调色板 / CSS / SVG 滤镜 / 鱼形 / 组件原语。
- **正确**：只让 `C(water)` 读新皮肤的参数字典，组件函数一行不改。
- **错误**：复制 30 屏渲染函数改色 —— 两套布局会漂移。

### 3.3 资源生成
- `generator/gen_android_assets.py` → `drawable*/`：`ic_fish_<skin>.xml`、`paper_grain_<skin>_<light|dark>.png`。
- 现有 A 的资源名是 `ic_{fish,wave,mount}_vintage.xml` + `paper_grain_{light,dark}.png`；新皮肤必须带 `<skin>` 后缀，避免覆盖 A。

---

## 4. 工程侧标准：真实代码（v1.1 校准）

### 4.1 数据结构：`TideSpotSkin`（`core/ui/theme/TideSpotSkin.kt`）

```kotlin
data class SkinPalette(
    val scheme: ColorScheme,       // 完整 M3 色板（不是派生出来的 13 色）
    val mapLand: Color, val mapWater: Color, val mapRoad: Color,
)

data class SkinPalettes(           // 每款皮肤必须有 4 套
    val seaLight: SkinPalette, val seaDark: SkinPalette,
    val inlandLight: SkinPalette, val inlandDark: SkinPalette,
) {
    fun pick(water: WaterBodyType, darkTheme: Boolean): SkinPalette
}

data class SkinTexture(            // 质感与装饰参数（已就位，铺开时消费）
    val paperGrain: Boolean, val grainStrength: Float,
    val printMisregister: Boolean, val handDrawnOutline: Boolean,
    val fishMotifRes: Int, val waveMotifRes: Int, val mountMotifRes: Int,
    val tapeDecoration: Boolean,
) { fun motifFor(water: WaterBodyType): Int }

data class TideSpotSkin(
    val id: String, val name: String, val nameEn: String,
    val palettes: SkinPalettes,
    val shapes: Shapes, val typography: Typography,
    val texture: SkinTexture,
)
```

**为什么不用「13 个源色 + 派生」**：A 的 4 套色板是逐槽位独立调过的——深色的 `surfaceContainerLowest` 是最暗的 `bg`，浅色的是最亮的纸白，梯度方向相反；`outlineVariant`、`errorContainer`、`inversePrimary` 都无法由 13 色线性推出。硬做派生函数一定会让详情页视觉回归。**结论：每套皮肤直接写 4 个 `ColorScheme`，`SkinPalette` 持有成品。**

**`name` 必须唯一**：设置页下拉按 `name` 展示，重名会让用户无法分辨。注册表在类加载时校验：

```kotlin
object TideSpotSkins {
    val VintageOutdoor = TideSpotSkin(id = "a-vintage", name = "复古户外", nameEn = "VINTAGE OUTDOOR", ...)
    val all: List<TideSpotSkin> = listOf(VintageOutdoor)
    val default: TideSpotSkin = VintageOutdoor
    init { require(validationErrors().isEmpty()) { ... } }

    fun byId(id: String?): TideSpotSkin = all.firstOrNull { it.id == id } ?: default
    fun byName(name: String?): TideSpotSkin = all.firstOrNull { it.name == name } ?: default
    fun validationErrors(): List<String>   // id / name / nameEn 唯一 + 非空 + 深色不得等于浅色
}
```

> 单测锁：`SkinRegistryTest.registry_hasNoValidationErrors` / `skinIdsAndNames_areUnique`。
> 加皮肤后这两个测试自动覆盖新条目。

### 4.2 注册表是唯一扩展点

- 加皮肤 = 在 `TideSpotSkin.kt` 里加 4 套色板 + 1 个实例 + 往 `all` 追加。
- **不得**为了新皮肤改 `SettingsScreen`：下拉遍历 `TideSpotSkins.all`，自动出现新项。
- **不得**为了新皮肤改 `AppSettings` / `SettingsState`：`skinId` 是 `String`，未知 id 自动归一为默认。

### 4.3 注入：`TideSpotTheme(skin, water, darkTheme)`

```kotlin
@Composable
fun TideSpotTheme(
    skin: TideSpotSkin = TideSpotSkins.default,
    water: WaterBodyType = WaterBodyType.OCEAN,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = skin.palettes.pick(water, darkTheme)
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(palette.scheme, skin.shapes, skin.typography, content)
    }
}
```

**调用约定**：
- `MainActivity.setContent` 只用**一次**，`skin = TideSpotSkins.byId(settings.skinId)`、`water = OCEAN`、`darkTheme = SettingsLogic.resolveDarkTheme(themeMode, systemDark)`。
- App 外壳页跨水体，海洋色板兜底；**详情页**按点位水体再包一层（`VintageOutdoorTheme(waterBody, darkTheme)` 是等价薄封装，仅换 water 不换 skin）。
- **不要**再出现「基础 M3 主题 + 皮肤主题」双层嵌套——A 的基础色板已随 SKIN-2 合并进皮肤，`TideSpotTheme` 是唯一入口。

**token → M3 槽位**：A 的映射已固化在 4 套 `ColorScheme` 里（浅/深各 33 槽位），照抄结构即可，不要自创槽位名。关键槽位用途：

| 槽位 | A 浅色（海洋） | 用在哪 |
|---|---|---|
| `background` | `#E4D9BC` 纸底 | Scaffold / 页面底色 |
| `surface` | `#F2EAD4` 卡片 | Card / Surface |
| `surfaceVariant` | `#E8DDBE` | 次级容器、chip 底 |
| `primary` / `onPrimary` | `#1B3A5C` / `#F7F1DE` | 主按钮、Top10 横幅、星标文字 |
| `tertiary` | `#D9A734` 芥末黄 | 准星预览态、强调 |
| `error` | `#C15A3A` 朱红 | 48h 不可用警示 |
| `onSurfaceVariant` | `#5C5540` 褐灰 | 陈旧提示、次要文字 |
| `outlineVariant` | `#8A7C60` | 手绘描边外框 |

### 4.4 持久化（三轴各自独立，已实现）

- 键：`settings.skinId`、`settings.themeMode`（`SettingsKeys.SKIN_ID` / `THEME_MODE`）。
- 状态：`SettingsState.skinId: String`、`SettingsState.themeMode: ThemeMode`（`SYSTEM/LIGHT/DARK`，枚举在 `feature:settings`）。
- 写：`AppSettings.writeSkinId`（先经 `byId` 归一）/ `writeThemeMode`；读：`read()` 各自解析，缺失落默认（A + SYSTEM）。
- **互不重置**：`persistSettings` 逐键写回；`SettingsLogic.withSkinId` 只改 skinId，`withThemeMode` 只改 themeMode。
- 锁测试：`AppSettingsTest.三轴互不重置_切皮肤不动明暗_切明暗不动皮肤`、`SettingsLogicTest.withSkinId与withThemeMode_互不重置`。

### 4.4.1 地图底图（第 4 个跟随轴，2026-11-13 裁决）

底图不是皮肤 token（是远程矢量样式：默认 OpenFreeMap，可切 CARTO），但它必须跟明暗一起走，否则「周边全深、底图仍浅」观感割裂。

| 键 | 含义 |
|---|---|
| `settings.mapStyle` | 用户选的**具体**样式（`VOYAGER` / `POSITRON` / `DARK_MATTER`） |
| `settings.mapStyleExplicit` | 用户**是否动手选过**（决定自动切换是否生效） |
| `settings.mapProvider` | 瓦片提供方（OPT-A-10；与样式正交，2 × 3） |

```kotlin
// 唯一入口，业务侧禁止直接取样式 URL 或自拼
val url = SettingsLogic.resolveBasemapUrl(state, darkTheme)   // provider × 生效样式
```

- `mapStyleExplicit = false`（默认 / 全新安装）→ 深色 `DARK_MATTER`、浅色 `VOYAGER`，**自动切**；
- `mapStyleExplicit = true`（用户点过具体样式）→ `mapStyle` 说了算，明暗不再覆盖（D8 用户选择优先）。
- **设置页是四选**（OPT-A-12）：`跟随主题` / `彩色` / `素色` / `深色`。
  - `跟随主题` **不是一种 `MapStyle`**，它是「用户没表态」这个状态；因此单列 `MapStyleChoice(style: MapStyle?)` 枚举，
    选中即写 `mapStyleExplicit = false`，其余三项走 `withMapStyle`（写样式 + explicit = true）。**不要**把 `FOLLOW_THEME` 塞进 `MapStyle`——
    那会让一个没有 URL 的成员进 `entries`，`styleUrl()` 只能返 null 或抛异常，并污染 `settings.mapStyle` 既有语义。
  - chip 高亮用 `SettingsLogic.mapStyleChoice(state)`（**与「用户表态」同口径，不与「生效样式」同口径**）：
    手动选「彩色」后深色下生效的仍是彩色，高亮也必须停在「彩色」，否则 chip 与底图互相打脸。
  - 四个 chip 用 `FlowRow` 而非 `Row`：`跟随主题` 比其余长，窄屏/大字体下 Row 会顶出屏幕。
  - 说明文字随 explicit 在 `ts_settings_map_style_auto` / `_pinned` 之间切换。
- 旧版本迁移：`settings.mapStyleExplicit` 缺失一律读作 `false`（未改过 → 自动切）。**不能拿「旧 `settings.mapStyle` 键存在」反推**——v1.16 的 `persistSettings` 全量写回，未改过样式的用户也会留下该键，据此判断会把绝大多数人误锁成「已选定」。
- **样式热切换靠 `MapScreen` 的 `LaunchedEffect(styleUrl, mapLibre)`**：`setStyle` 会丢弃所有运行时图层，所以该效果内必须重跑 `installStarLayer(style)` 并强制首帧重绘——新增皮肤若动这块，务必保留这个顺序。

### 4.5 设置页入口（下拉，不是独立页）

`SettingsScreen` 的「显示」版块内：

```kotlin
Text(stringResource(R.string.ts_settings_skin), style = MaterialTheme.typography.titleSmall)
SkinDropdown(currentId = state.skinId, onSelect = onSkinChange)
Text(stringResource(R.string.ts_settings_skin_note), ...)   // 「切换仅改变视觉…」
Text(stringResource(R.string.ts_settings_theme_mode), ...)  // 深色模式三选
```

- 下拉遍历 `TideSpotSkins.all`，显示 `skin.name`，当前项带 ✓。
- 新增皮肤**不需要**改这里；只有「皮肤数量为 0」或「需要分组」时才动 UI。

### 4.6 全屏取色门禁（换肤不漏屏的唯一保证）

- 业务 Composable **禁止** `Color(0x...)` / `Color.White` / `Color.Black`。
- 冻结单源是唯一例外：`ScoreBand.argb/onArgb`、`ConfidenceBadge` 色值——直取，不复制。
- **MapLibre 特例**：`SymbolLayer` 的 `textColor` / `textHaloColor` / `fillColor` 不读 Compose 主题，必须显式转字符串：

```kotlin
val starTextHex = MaterialTheme.colorScheme.primary.toHexRgb()   // core:ui 的 Color.toHexRgb()
PropertyFactory.textColor(starTextHex)
```

- 自检命令（Windows Git Bash）：

```bash
grep -rn "Color(0x" tidespot-android/feature tidespot-android/app \
  --include=*.kt | grep -v "ScoreBand" | grep -v "ConfidenceBadge"
# 期望：无输出
```

### 4.7 质感与纸纹挂载

- 纸纹**统一挂在外壳**（`MainActivity.AppRoot` 的 `Scaffold` content），`navState.page != MAP` 时启用；地图页跳过（CARTO 真实瓦片，颗粒伤可读性）。
- 详情页**不再**叠加第二层纸纹（避免文字发糊），只保留 `.background(colorScheme.background)` + 鱼群水印。
- 单个组件质感：`Modifier.printMisregister(color)`（套印错位）、`Modifier.handDrawnOutline(color)`（手绘描边）——颜色一律来自 `MaterialTheme`。
- `skin.texture`（grainStrength / motifRes）已就位，细粒度铺开时从这里取，不要在各页写常量。

### 4.8 四态状态色（语义等级不变，色值随皮肤）

| 状态 | 组件 | token |
|---|---|---|
| 离线 | `OfflineBanner` | `secondaryContainer` / `onSecondaryContainer` |
| 陈旧（3h/6h TTL） | `StaleBanner` | `onSurfaceVariant`（褐灰，弱提示，5.0–5.3:1） |
| ≥48h 不可用 | `StaleBanner` | `onErrorContainer`（深暖红，最醒目，5.4–9.5:1） |
| 分维降级 / 低置信 | `ConfidenceBadge`（`LOW_CONFIDENCE`） | 冻结单源，**不进皮肤** |

> ⚠️ **实测坑**：A 的 `error`（`#C15A3A`）在浅色纸底上只有 **3.1–3.7:1**，它适合做**填充色**（按钮底），
> **不适合做浅底文字色**。状态文字用 `error` 会踩 4.5:1 门禁——改用同色系更深的 `onErrorContainer`。
> 换肤时每个皮肤都要复算：`淡背景 + 淡强调色` 的组合最容易不达标。

> 换肤只允许改表现色，不允许改「谁比谁更醒目」的顺序。

---

## 5. 命名 / 文件规范

| 对象 | 规范 | 示例 |
|---|---|---|
| 皮肤 id | 小写连字符，稳定不变（持久化用） | `a-vintage` |
| 中文名 | 全皮肤唯一，下拉显示 | `复古户外` |
| 英文名 | 全皮肤唯一 | `VINTAGE OUTDOOR` |
| 数据类实例 | `TideSpotSkin.<PascalName>` | `TideSpotSkin.VintageOutdoor` |
| 注册表 | `TideSpotSkins.all` | — |
| 鱼形/母题 drawable | `ic_<kind>_<skin>.xml` | `ic_fish_vintage.xml` |
| 纸纹 png | `paper_grain_<skin>_<light|dark>.png` | `paper_grain_light.png` |
| 持久化键 | `settings.<camel>` | `settings.skinId` |
| 主题文件 | `core/ui/theme/` 下，不散落 feature | — |

---

## 6. 验收清单

### 6.1 新增皮肤接入（A1–A10）

- [ ] **A1** `TideSpotSkins` 新增实例（id / 中文名 / 英文名 / 来源素材），`all` 追加
- [ ] **A2** 海洋 + 内陆**两套**色板齐全，六重区分仍成立
- [ ] **A3** 深色两套独立调色（不得等于浅色），对比度 ≥4.5:1
- [ ] **A4** 字体 / 形状 / 质感 / 鱼形 / 母题 token
- [ ] **A5** 冻结契约 §6.2 逐项回归未变
- [ ] **A6** 设计侧 30 屏自包含 HTML + 2x PNG（`STYLES` 参数化，不复制布局）
- [ ] **A7** `spec.json` / `index-full.html` / `README.md` 更新（含 SHA）
- [ ] **A8** 设置页下拉自动出现该项（**无需改 SettingsScreen**）；选中后排他
- [ ] **A9** 整包自检 + 真机逐屏走查（无破图/重叠/截断/漏屏），写入工作区记忆
- [ ] **A10** 结论写回 `DECISIONS.md`（或等价决策记录）

### 6.2 冻结契约（换肤前后逐项不变）

1. Tab 渲染序 2. 免责恒 5 条 3. 五档/六徽标单源 4. DaySelector 52dp + 水质 1×3 5. 地图优先/无底部导航/准星唯一真源 6. 双水体六重区分 7. 文案单源 8. 示例数据非业务值 9. 四态语义等级不变

### 6.3 换肤上线前门禁

| # | 检查项 | 命令 / 标准 |
|---|---|---|
| 1 | 注册表自检 | `SkinRegistryTest.registry_hasNoValidationErrors` 绿 |
| 2 | 色值未漂移 | `SkinRegistryTest.vintagePalettes_keepPilotValues` 绿（4 套关键槽位） |
| 3 | 全屏无硬编码 | §4.6 的 grep 无输出 |
| 4 | 三轴正交 | `AppSettingsTest` / `SettingsLogicTest` 的三轴用例绿 |
| 5 | 八模块编译 + 单测 | `gradle.bat :app:testDebugUnitTest :core:domain:test :core:data:test :core:ui:testDebugUnitTest :feature:detail:testDebugUnitTest :feature:favorites:testDebugUnitTest :feature:map:testDebugUnitTest :feature:settings:testDebugUnitTest -p <root>` |
| 6 | 设计侧自检 | `python3 selfcheck_full.py` → 276 PASS / 0 FAIL |
| 7 | 对比度 | 状态文字/次要文字 ≥4.5:1（A 实测：陈旧 5.0–5.3 / 48h 5.4–9.5 / 离线 ≥7）；`error` 只作填充色 |
| 8 | 真机逐屏 | 详情/地图/收藏/设置/叠层/四态 × 浅深 × 海陆 |
| 9 | 可扩展 | 照 §8 加一套测试皮肤，业务 Composable 零改动即可选中 |

---

## 7. 反模式（实测踩过，禁止）

1. ❌ 把「海洋/内陆两套配色」当两款皮肤让用户切（破坏水体语义）。
2. ❌ 换肤 = 改布局 / 改文案 / 改 Tab 序 / 改逻辑。
3. ❌ 业务层写死 `Color(0x...)`（换肤漏屏）。
4. ❌ 深色 = 浅色机械反转（必须独立调；A 的深色 `surfaceContainer` 梯度与浅色方向相反）。
5. ❌ 伪造可切换样式 / 空卡让用户以为能点。
6. ❌ 把 B/C/D 探索稿命名/呈现为「皮肤」并入（需产品决策）。
7. ❌ 复制 30 屏渲染函数改色（应只换 `STYLES` 参数）。
8. ❌ 改字号/行高/字重来做「手绘感」（字重变粗会改变文本宽度，同样破坏换行与卡片高度；D 的「英制」chip 真机换行即此坑）。
9. ❌ **双层主题嵌套**：`TideSpotTheme` 内再包一套不同 skin 的 `MaterialTheme`（A 已在 SKIN-2 合并为单层；仅允许「同 skin 不同 water」的覆盖）。
10. ❌ **MapLibre 图层写死 `"#0D47A1"`**：不走 Compose 主题，必须 `colorScheme.primary.toHexRgb()`。
11. ❌ 详情页与外壳各挂一层纸纹（双层颗粒，文字发糊）。
12. ❌ 给 `skinId` 存中文名（应存稳定 id；名字用于显示，允许将来改名）。
13. ❌ 地图直接读 `state.mapStyle.url`：绕过 `resolveMapStyle` 就等于关掉「自动跟随明暗」。
14. ❌ 让深色无条件覆盖用户显式选的地图样式（D8：用户选择优先，自动切只在用户没表态时发生）。

---

## 8. 新皮肤落地模板（diff 级，3 个文件）

```
[ ] 1. 设计侧：engine.py STYLES["<skin>"] = { sea:{light,dark}, inland:{light,dark}, fonts, shapes, texture, motifs }
[ ] 2. python3 engine_full.py → Edge@2x 渲图 → gen_full_index.py → selfcheck_full.py（全绿）
[ ] 3. spec.json / index-full.html / README.md 更新（含 SHA）
[ ] 4. 工程：core/ui/theme/TideSpotSkin.kt
        + private val <Skin>SeaLight/SeaDark/InlandLight/InlandDark = lightColorScheme/darkColorScheme(...)
        + TideSpotSkin(id="<id>", name="<唯一中文名>", nameEn="<唯一英文名>", palettes=..., shapes=..., typography=..., texture=...)
        + TideSpotSkins.all = listOf(VintageOutdoor, <Skin>)
[ ] 5. 资源：drawable/ic_*_<skin>.xml + drawable-nodpi/paper_grain_<skin>_*.png
[ ] 6. 跑 §6.3 门禁 1–5
[ ] 7. 真机：设置 → 皮肤 下拉出现新项 → 切换 → 逐屏走查（浅/深 × 海/陆）
```

**不要改**：`TideSpotTheme.kt`、`SettingsScreen.kt`、`AppSettings.kt`、`SettingsState.kt`、任何 `feature:*` 屏。
若加皮肤必须改这些文件，说明有新硬编码，先治理再加。

---

## 9. A / D 落地实录（2026-11-13）

| 阶段 | 需求 | 实际改动 |
|---|---|---|
| SKIN-1 | token 抽象 | 新增 `TideSpotSkin.kt`（4 套 ColorScheme + 注册表 + `validationErrors`）、`LocalSkin.kt`；`VintageOutdoorTheme` 退役为薄封装；详情页视觉零回归（4 套 33 槽位锁测试） |
| SKIN-2 | 全局注入 | `TideSpotTheme(skin, water, darkTheme)` 唯一入口；`MainActivity` 去掉外层基础主题、Scaffold 铺纸底、纸纹统一挂载；删除基础 `LightColors/DarkColors` |
| SKIN-3 | 持久化 | `SettingsKeys.SKIN_ID/THEME_MODE`；`SettingsState.skinId/themeMode`；`AppSettings` 读写 + 归一；冷启动分别回放 |
| OPT-A-03 | 明暗三选 | `ThemeMode` + `SettingsLogic.resolveDarkTheme`；设置页三选；详情页与全局共用同一解析 |
| OPT-A-09 | 换肤 UI | 设置页 `SkinDropdown`（DropdownMenu，按名字选）+ 文案（中英）；**不做独立选择页** |
| OPT-A-01/02 | 设置页下线 | 删「用例/物种/权重」「API Key」两块 + 参数 + 状态字段 + 键 + 中英文案；`clearLegacyApiKey` 启动时一次性清理历史键 |
| LAND-MAP | 地图页 | 星标图层文字色 → `primary/onPrimary.toHexRgb()`；Top10 横幅 → `primary/onPrimary`；准星预览 → `tertiary` |
| LAND-FAV/SET | 收藏/设置 | 继承全局纸底 + 纸纹 + 衬线字阶；设置页新增皮肤/明暗控件 |
| LAND-STATE | 四态 | `StaleBanner` 陈旧改 `onSurfaceVariant`（弱于 48h 的 `error`）；离线/低置信沿用既有 token 与冻结徽标 |
| LAND-DARK | 深色 | 由 4 套色板覆盖；真机逐屏已验（`device-compare/r2-换肤对拍.md`） |
| **D 皮肤** | DES-SKIN2（第 2 款） | 设计侧：`D-field-guide/` 30 屏（`engine.py STYLES["d-guidebook"]` 参数化，`build_style.py` 一键复现，`selfcheck_style.py` 273/0）；工程侧：`d-fieldguide` 4 套 `ColorScheme` + `FieldGuideShapes(3/8/12/14/18)` + `FieldGuideTypography(SansSerif，字重/字号/行高与 A 全等)` + 注册表追加；复用 A drawable；设置页下拉自动出现，**零业务代码改动**；真机走查通过（`device-compare/D-真机走查-2026-11-13.md`：字重 Black→Bold 修复 chip 换行） |

**真机对拍修复（r2，2026-11-13）**——这三条都是 token 层问题，换肤时必须一并检查：

| ID | 现象 | 修复 |
|---|---|---|
| D1 | 卡片比纸底**更深**，像脏斑 | M3 `Card` 用 `surfaceContainerHighest`；浅色该槽位必须≥surface 亮度（A 改为 `#F2EAD4`） |
| D2 | chip 选中态不可辨 | `secondaryContainer` 不能≈`background`；A 改为 `#D8C9A5`（深一档） |
| D3 | 皮肤下拉切出下方说明残片 | `DropdownMenu(modifier = Modifier.fillMaxWidth(0.9f))` |

> **换肤通用教训**：M3 的容器层级假设（越高越深）与「纸叠纸」设计相反，浅色皮肤的
> `surfaceContainerHighest` / `secondaryContainer` 必须单独校一遍，别照 M3 默认语义填。

**已知限制**：
- `skin.texture` 已被定义但尚未在 3 页细粒度消费（纸纹强度/母题仍走调用方参数）。
- MapLibre 已有图层在换肤后不会自动重设属性（MapView 不重建）；本次只保证首次安装/冷启动正确。
- 地图页不挂纸纹（刻意）。
- **D 的设计侧**已完成 30 屏（`D-field-guide/`：7 base + 23 full，PNG 780×1688）；`idea-explorations/v3-fieldguide` 仅为源探索稿。
- **D 复用 A 的鱼形/母题资源**；若后续要「图鉴线稿鱼」，按 §5 新增 `ic_*_fieldguide`，业务组件零改动。
- **`skin.texture` 已在外壳消费**：`paperGrain` 开关 + `grainStrength` 生效（D 为 0.45 且不叠纸纹）；详情页不叠第二层。

---

## 10. 快速命令

```bash
# 工程侧（Windows）
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
D:\workspace\weatherfishcheck\.tools\gradle-8.9\bin\gradle.bat \
  :core:ui:testDebugUnitTest :feature:settings:testDebugUnitTest :app:testDebugUnitTest \
  --console=plain -p D:\workspace\weatherfishcheck\tidespot-android

# 硬编码色自检
grep -rn "Color(0x" tidespot-android/feature tidespot-android/app --include=*.kt

# 设计侧
cd design-handdrawn/generator && python3 engine_full.py && python3 gen_full_index.py && python3 selfcheck_full.py
```

---

## 10. 视觉对齐实战（CHG-93，2026-11-15）

> 背景：客户要求「设计稿 vs 真机逐页对拍 → 找差异 → 最大限度接近设计图 → 把经验同步进指南」。
> 结论先写在最前：**token 对齐 ≠ 视觉对齐**。只换 ColorScheme 而不处理「质感叠加层 / 字族回落 /
> 卡片容器形态 / 结构层授权」，成品会与稿差出一大截。
> 分析证据：`device-compare/UI差异逐页对拍-2026-11-15.md` + `device-compare/pairs/*.jpg`；
> **效果验收已于 2026-11-15 USB 真机复拍完成**（v1.20.0 → 修复页头后 v1.20.1），
> 逐页结论与量化数字见 `device-compare/r6-真机复拍验证记录.md`；本节记录方法与长期口径。

### 10.1 先定稿，再对齐（红线）
- 新旧稿**不得混挂**：`screens/detail-*.png` 是早期 base 旧稿（78 分 / 白底 / 潮高·潮流折线），
  `screens_full/detail-tab-score-*.png` 才是落地目标（82 分 / 苔绿 / 钓鱼指数柱）。曾把两版混挂并称「对齐」。
- 对照矩阵必须写进脚本（`device-compare/make_pairs.py`），旧稿只能单列「勘误组」；
  用 `spec.json:authoritative_design` 声明唯一权威稿与已知缺口。
- 新增皮肤必须交付**完整深浅 × 双水体矩阵**；缺深色稿时深色只能靠 token 推断，不要假装对齐。
- 稿里的示例假数据（78/86/92 分）不是业务值，不能当布局或文案验收依据。

### 10.2 设计源 → 工程的四个高频损耗点
| 设计侧 | 工程侧易错实现 | 正确做法 |
|---|---|---|
| `feTurbulence fractalNoise baseFrequency=0.82` 高频同色细噪 | 96px 低频正余弦 + 高对比墨色颗粒 → 真机规则网格、纸底压灰（灰度方差 7~16 倍） | 高频 tile（≤64px）、颗粒色取纸底同色系 ±5%、`drawBehind` 只作用于纸底、强度收敛到 0.5 |
| `KaiTi/STKaiti` 中文衬线 | `FontFamily.Serif` 对中文回落**无衬线 CJK** | 打包可商用中文衬线字体子集（OFL 许可随附声明），字号/行高/字重**禁止改** |
| CSS 双线描边 + 偏移阴影 + 虚线 | 原生 M3 `Card` 无描边、无阴影、实线分隔；只铺了详情页 3 处 | 抽公共 `Modifier.vintageCard()`，四页统一挂载，颜色取 ColorScheme、开关取 `LocalSkin.texture` |
| 静态示例布局 | 30 屏各写一套渲染函数 / 工程各页各写一套骨架 | `STYLES` 参数化 + 共享渲染函数；工程侧同理由 token 驱动，业务 Composable 零改动 |
| 设计稿各屏纸底手写色值 | r6 实测 `settings.png` #DFD4B8 与 `favorites.png` #DACDAE 不同，工程单一 `background` token 只能对齐一屏 | 定稿时把纸底收敛为**一个 token 值**，逐屏比对前先确认设计源自身一致 |

### 10.3 结构层授权的边界（路径 B）
- 客户 2026-11-15 选**路径 B**：A 稿结构提升为**全局共用骨架**，所有皮肤共用；
  `TideSpotSkin` **不加 layout 字段**，门禁第 9 条「新增皮肤时业务 Composable 零改动」仍成立。
  这比「每套皮肤各写一套布局」（路径 A）成本低得多，代价是 D 皮肤需在同一骨架下用 token / texture 表达自己。
  这也与 `spec.json:skin_system.definition`「结构/文案/交互/冻结契约全部共用」一致。
- 「放开结构 1:1」必须先回答「这份结构是 A 专属还是全局」；不答会直接推翻换肤抽象。
- 结构重排分两批：**S2a 纯视觉重排**（容器/页头/装饰，不改交互语义，可只改 Android）与
  **S2b 控件形态替换**（FilterChip/Switch → 下拉，改交互语义，触发双端契约 N-4 同批）。
  混在一批做会让「不改交互」的口径失效。

### 10.4 入口红线：豁免装饰，不放松入口
- `check_entries_v4.py` 旧口径「新增标签只允许五档档位文字」会误杀 S2a 的页头/副标题/`/100`/鱼形装饰文字。
  新口径：新增标签若命中白名单，或其**全部同名节点都不可点击、不可聚焦**且不含入口关键字 → 视为纯装饰豁免。
- **一条未放松**：入口零减少、可交互节点数一致、Top10 bounds 四态逐像素一致、Tab/免责完整（36 项）。

### 10.5 量化，而不是「看着像」
- 纯色区采样三指标：RGB 中位数、灰度标准差、颜色种类数；阈值 ΔRGB ≤8/通道、std ≤ 设计×2、颜色数 ≤ 设计×3。
- 工具链：`device-compare/make_pairs.py`（并排图）→ `design-handdrawn/generator/visual_quant.py`（采样 + `--tokens` 防漂移）
  → `check_entries_v4.py`（入口红线）→ `selfcheck_full.py`（设计侧 276 项）
  → 八模块单测 / `i18n_scan` / `contrast_check` / `assembleRelease` / `check_parity`。
- 每次换肤或改皮肤后重跑并**人工逐页签字**；`visual_tokens` 写进 `spec.json` 并加防漂移断言。
- **r6 两条实测坑**：① 采样区必须选「左右边距窄条」这类恒定纯色区，别用「页面下部留白」——布局一改就会被卡片顶到；
  ② 必须传原始 PNG，760px JPG 的 JPEG 块效应会让颜色种类数虚高 3~5 倍，制造假 FAIL。

### 10.6 反模式补充（15–18）
15. ❌ 把旧稿挂左列称「对齐」——两个设计版本没有可比性。
15b. ❌ 用会被内容顶到的固定坐标做量化采样，或拿 JPG 压缩图跑颜色数阈值——两者都会产生假 FAIL。
16. ❌ 用 `drawWithContent` 把颗粒盖在文字之上——文字发糊、卡片被压灰（同源见反模式 #11）。
17. ❌ 为了「更像手绘」改字号/行高/字重，或整包塞 24 MB 可变字体——前者破坏换行，后者撑爆 APK。
18. ❌ 结构层 1:1 时顺手换控件形态——那已跨到 S2b，必须走双端同批与全页回归。
