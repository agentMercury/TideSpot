# 潮点 TideSpot · 皮肤开发标准（Skin Developer Standard）

> **面向对象**：未来要**新增一套皮肤**或**修改现有皮肤**的 Agent / 工程师。
> **与 `SKIN-DESIGN-SPEC.md` 的关系**：那份是**契约**（什么能换、什么绝不能换）；本文是**执行手册**（照着一步步做就能产出一套合格皮肤）。
> **一句话**：**皮肤 = 一组可替换的视觉 token；业务 Composable 零改动。** 换肤只替换 token 表，不重画页面、不改布局/文案/逻辑。
> **版本**：1.0 ｜ 2026-11-13 ｜ 基线皮肤：手绘 A · 复古户外（首版唯一内置）

---

## 0. 适用与前置

- **适用**：新增第 2、3… 款内置皮肤；把探索稿（如 B/C/D）正式皮肤化（需先经产品决策，B/C/D 默认**不并入**）。
- **前置**：读过 `SKIN-DESIGN-SPEC.md` §1 三轴正交、§2 可变/冻结清单；读过 `UI落地交接文档.md` §3 目标架构。
- **不要做**：把「海洋/内陆两套配色」当两款皮肤；用改布局/改文案来「做皮肤」；伪造不可点的可切换样式。

---

## 1. 心智模型（先记这 3 行）

1. **三轴正交**：`最终视觉 = 皮肤token(水体) × 明暗模式`；皮肤 / 水体 / 明暗各自独立持久化，互不重置。
2. **每款皮肤必须自带海 + 陆两套色板**，并在其上派生浅/深 → 共 **4 套 ColorScheme**。
3. **皮肤只换「长什么样」**；「是什么 / 有几个 / 叫什么 / 排第几」一律冻结（见 §6.2）。

---

## 2. 皮肤交付物清单（一套皮肤 = 这些）

### 2.1 设计侧
- [ ] `generator/engine.py` 的 `STYLES` 新增该风格配置（§3.1 全量 token）
- [ ] 用 `engine_full.py` 渲染出**同一套 30 屏**（复用组件，不复制布局）
- [ ] 产出 `screens/`(7) + `screens_full/`(23) 的 2x PNG（780×1688）
- [ ] 产出 `sources/` + `sources_full/` 自包含 HTML
- [ ] 更新 `spec.json`（该皮肤的 palette / 屏 SHA / `skin_system` 条目）
- [ ] 更新 `index-full.html`（该皮肤分组或切换演示）、`README.md`
- [ ] `selfcheck_full.py` 全绿

### 2.2 工程侧
- [ ] `core:ui` 新增 `TideSpotSkin.<YourSkin>` 实例（§4.1）
- [ ] 加入注册表 `TideSpotSkins.all`（§4.2，**唯一扩展点**）
- [ ] `TideSpotTheme(skin, water, darkMode)` 能派生该皮肤的 ColorScheme/Shapes/Typography（§4.3）
- [ ] `skinId` 持久化 + 选择页可选中（§4.4）
- [ ] 质感/装饰资源（drawable / paper grain）按命名规范产出（§5）
- [ ] 冻结契约回归通过（§6.2）

---

## 3. 设计侧标准：怎么产出一套新皮肤

### 3.1 在 `engine.py` 的 `STYLES` 新增风格配置

一套皮肤 = 一份参数表。**必须覆盖海/陆 × 浅/深**，字段与 `SKIN-DESIGN-SPEC.md` §2.1 对齐：

| 分组 | 字段 | 说明 |
|---|---|---|
| 色板·海洋（浅） | `bg bg2 ink primary accent accent2 card card2 white green muted line map_land map_water map_road band[]` | `band` 五档=**冻结语义色**，此处仅占位引用，实际取 `ScoreBands.kt` |
| 色板·海洋（深） | 同上全套 | 独立调色，**非机械反转** |
| 色板·内陆（浅/深） | 同上全套 | 暖绿调，保持「海冷陆暖」六重区分 |
| 字体 | `display font / body font / num&caps / track` | 例：A = Georgia 700 衬线 + KaiTi 正文 |
| 形状 | `radius（非对称）/ stroke_w` | 例：A = `16 20 15 19` 非对称 + 2.2px |
| 质感 | `paper_grain / paper_tint / base_freq / sketch_scale / print_offset` | 例：A = grain 0.16 / sketch 2.2 / 套印 on |
| 装饰 | `fish_style / motif_sea / motif_inland / tape` | 例：A = woodcut 版画鱼 / 浪贝鸟 / 山日芦苇 |

> **硬性**：海洋与内陆**各一套**，且六重区分（徽标/配色冷暖/鱼种/第二 Tab/图表单位/地点）必须成立。

### 3.2 用 `engine_full.py` 渲染（不复制布局）
- `engine_full.py` 已 `import engine as E`，共享调色板 / CSS / SVG 滤镜 / 鱼形 / 组件原语。
- **正确做法**：只让 `C(water)` 读到你新皮肤的参数表，**组件函数一行不改**。
- **错误做法**：把 30 屏的渲染函数复制一份改色 —— 会导致两套布局漂移。

### 3.3 资源生成（工程可用）
- `generator/gen_android_assets.py` 生成 `drawable*/`：`ic_fish_<skin>.xml`、`paper_grain_<skin>_<light|dark>.png` 等。
- 新皮肤的鱼形/母题改了画风，需同步产出对应 drawable，并遵守 §5 命名。

### 3.4 元数据
- `spec.json`：新增该皮肤的 palette 快照、屏 SHA；`skin_system.skins_total` 递增；新增 `skins[]` 条目。
- `index-full.html`：新增分组或「皮肤切换」预览区（**不要伪造可切换样式**，仅展示真实存在的皮肤）。
- `README.md`：说明该皮肤来源素材与差异。

---

## 4. 工程侧标准：怎么接进 App

### 4.1 `TideSpotSkin` 数据类（字段对齐 SKIN-DESIGN-SPEC §4.1）

```kotlin
// core/ui/src/main/kotlin/com/tidespot/core/ui/theme/TideSpotSkin.kt  （参考骨架，未编译）
data class SkinPalette(
    val bg: Color, val bg2: Color, val ink: Color, val line: Color,
    val card: Color, val card2: Color, val white: Color,
    val primary: Color, val accent: Color, val accent2: Color,
    val green: Color, val muted: Color,
    val mapLand: Color, val mapWater: Color, val mapRoad: Color,
)

data class SkinPalettes(val seaLight: SkinPalette, val seaDark: SkinPalette,
                        val inlandLight: SkinPalette, val inlandDark: SkinPalette)

data class TideSpotSkin(
    val id: String,                 // 稳定 id，持久化用（如 "a-vintage"）
    val name: String, val nameEn: String,
    val palettes: SkinPalettes,
    val shapes: Shapes,             // 非对称圆角等
    val typography: Typography,     // 只换字族，字号/行高保持不变
    val texture: SkinTexture,       // grain/shade/sketch/printOffset 参数
    val motifs: SkinMotifs,         // 海陆母题、鱼形画风、胶带
)
```

### 4.2 注册表（唯一扩展点）

```kotlin
object TideSpotSkins {
    val VintageOutdoor = TideSpotSkin(id = "a-vintage", name = "手绘 A · 复古户外", nameEn = "VINTAGE OUTDOOR", /* ... */)
    val all: List<TideSpotSkin> = listOf(VintageOutdoor)   // 未来加皮肤只往这里加
    val default: TideSpotSkin = VintageOutdoor
    fun byId(id: String?): TideSpotSkin = all.firstOrNull { it.id == id } ?: default
}
```

> **规则**：任何新皮肤**只加数据 + 资源**，不改业务 Composable。若加皮肤需要改某个屏的代码，说明该屏有硬编码色/写死样式，先治理它。

### 4.3 注入：`TideSpotTheme(skin, water, darkMode)`

```kotlin
@Composable
fun TideSpotTheme(
    skin: TideSpotSkin = TideSpotSkins.default,
    water: WaterBodyType = WaterBodyType.SEA,
    darkMode: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = skin.palettes.pick(water, darkMode)   // 4 选 1
    val scheme = palette.toColorScheme()
    CompositionLocalProvider(LocalSkin provides skin) {
        MaterialTheme(colorScheme = scheme, shapes = skin.shapes,
                      typography = skin.typography, content = content)
    }
}
```

**token → Material3 ColorScheme 映射**（照此，不要自创槽位）：

| Material3 槽 | 取自 palette | 备注 |
|---|---|---|
| `primary / onPrimary` | `primary / white` | 主色 |
| `primaryContainer / onPrimaryContainer` | `bg2 / ink` | 主色容器 |
| `secondary / onSecondary` | `green / white` | 辅绿 |
| `secondaryContainer / onSecondaryContainer` | `card2 / ink` | |
| `tertiary / onTertiary` | `accent2 / ink` | 芥末黄强调 |
| `background / onBackground` | `bg / ink` | 纸底 |
| `surface / onSurface` | `card / ink` | 卡片 |
| `surfaceVariant / onSurfaceVariant` | `card2 / muted` | |
| `outline / outlineVariant` | `line / muted` | 描边 |
| `error / onError` | `accent / white` | 暖红 |
| `scrim / surfaceTint / inverseSurface…` | `ink / primary / ink…` | 见 `VintageOutdoorTheme.kt` 现值 |
| `surfaceContainerLowest…Highest` | `white → card → card2 → bg2 → darker` | 层次梯度 |

> 五档 / 六徽标 / 状态语义色 **不走此映射**，直接取冻结单源（§4.6）。

### 4.4 持久化（三轴各自独立）

- `SettingsKeys` 新增 `SKIN_ID = "settings.skinId"` 与 `THEME_MODE = "settings.themeMode"`（与 `MAP_STYLE` 同级）。
- `SettingsState` 增 `skinId: String`、`themeMode: ThemeMode`（`SYSTEM / LIGHT / DARK`）。
- `AppSettings` 增 `parseThemeMode / writeThemeMode / writeSkinId`（照现有纯函数模式）。
- **冷启动**：分别回放，任一轴缺失落默认（skin=A、mode=SYSTEM）。
- **互不重置**：切皮肤只写 `skinId`；切明暗只写 `themeMode`。

### 4.5 全屏取色（换肤不漏屏的唯一保证）

- 所有 Composable **禁止** `Color(0xFF...)` 硬编码；一律 `MaterialTheme.colorScheme.xxx` 或 `LocalSkin.current.xxx`。
- 新写屏必须自检：grep 该模块无 `Color(0x`。
- 冻结语义色是唯一例外（直取单源，见 §4.6）。

### 4.6 冻结单源旁路

`ScoreBands.kt`（五档）、`ConfidenceBadge.kt`（六徽标）、`TideSpotStrings.kt`（文案）、`WaterBodyTabTitles`（Tab 序）**不经皮肤**；状态色可从皮肤派生，但**语义等级不变**且对比度 ≥4.5:1。

---

## 5. 命名 / 文件规范

| 对象 | 规范 | 示例 |
|---|---|---|
| 皮肤 id | 小写连字符，稳定不变 | `a-vintage` |
| 数据类实例 | `TideSpotSkin.<PascalName>` | `TideSpotSkin.VintageOutdoor` |
| 注册表 | `TideSpotSkins.all` | — |
| 鱼形/母题 drawable | `ic_<kind>_<skin>.xml` | `ic_fish_vintage.xml` |
| 纸纹 png | `paper_grain_<skin>_<light|dark>.png` | `paper_grain_light.png` |
| 设计侧网格 | `screens*/` + `sources*/` 同结构 | 与 A 一致 |
| 持久化键 | `settings.<camel>` | `settings.skinId` |

---

## 6. 验收清单

### 6.1 新增皮肤接入（A1–A10，逐项打勾）
- [ ] **A1** 注册表新增 `TideSpotSkin` 实例（id/名称/中英/来源素材）
- [ ] **A2** 海洋 + 内陆**两套**色板齐全（§3.1 全 token），六重区分仍成立
- [ ] **A3** 深色 token（海/陆各一套），对比度 ≥4.5:1
- [ ] **A4** 字体 / 形状 / 质感 / 鱼形 / 母题 / 胶带 token
- [ ] **A5** 冻结契约 §6.2 逐项回归未变
- [ ] **A6** 设计侧自包含 HTML + 2x PNG（`engine.py STYLES` 参数化，不复制业务布局）
- [ ] **A7** 更新 `spec.json` / `index-full.html` / `README.md`
- [ ] **A8** 选择页从「敬请期待」升为可选（勾选互斥）
- [ ] **A9** 整包自检 + 浏览器整页渲染验收（无破图/重叠/截断/漏屏），写入工作区记忆
- [ ] **A10** 结论写回 `DECISIONS.md`（或等价决策记录）

### 6.2 冻结契约（换肤前后逐项不变）
1. Tab 渲染序 2. 免责恒 5 条 3. 五档/六徽标单源 4. DaySelector 52dp + 水质 1×3 5. 地图优先/无底部导航/准星唯一真源 6. 双水体六重区分 7. 文案单源 8. 示例数据非业务值 9. 四态语义等级不变

### 6.3 换肤功能上线前验收
| # | 检查项 | 标准 |
|---|---|---|
| 1 | 入口可达 | 设置「外观·皮肤」进选择页，显示当前皮肤 |
| 2 | 全屏一致 | 换肤后逐屏走查无漏切、无硬编码色残留 |
| 3 | 首版唯一 | 仅 A 可选，其余「敬请期待」不可点 |
| 4 | 冻结契约 | §6.2 通过 |
| 5 | 双水体可辨 | 换肤后海/陆仍一眼可辨 |
| 6 | 三轴正交 | 切皮肤不重置明暗/水体，各自持久化 |
| 7 | 对比度 | 语义色/状态色浅深 ≥4.5:1 |
| 8 | 可扩展 | 照 §3–§5 加一套测试皮肤，业务 Composable 零改动即可生效 |

---

## 7. 反模式（禁止清单）

1. ❌ 把「海洋/内陆两套配色」当两款皮肤让用户切（破坏水体语义）。
2. ❌ 换肤 = 改布局 / 改文案 / 改 Tab 序 / 改逻辑。
3. ❌ Composable 里写死 `Color(0x...)`（换肤漏屏）。
4. ❌ 深色 = 浅色机械反转（必须独立调对比度与层次）。
5. ❌ 伪造可切换样式 / 空卡让用户以为能点。
6. ❌ 把 B/C/D 探索稿命名/呈现为「皮肤」并入（需产品决策，默认不并入）。
7. ❌ 复制 30 屏渲染函数改色（应只换 `STYLES` 参数）。
8. ❌ 改字号/行高来做「手绘感」（会破坏布局与换行）。

---

## 8. 新皮肤落地模板（照着填）

```
[ ] 1. engine.py: STYLES["<skin>"] = { sea: {light,dark}, inland: {light,dark}, fonts, shapes, texture, motifs }
[ ] 2. python3 engine_full.py  → 生成 30 屏 HTML（组件零改）
[ ] 3. Edge 2x 渲图 → screens*/ *.png（780×1688 校验）
[ ] 4. python3 gen_full_index.py && python3 selfcheck_full.py  → 全绿
[ ] 5. spec.json / index-full.html / README.md 更新（含 SHA）
[ ] 6. TideSpotSkin.<Skin> 实例 + TideSpotSkins.all 追加
[ ] 7. TideSpotTheme(skin, water, darkMode) 派生正常
[ ] 8. skinId 持久化 + 选择页可选中
[ ] 9. drawable / paper_grain 资源命名规范产出
[ ] 10. 冻结契约 §6.2 回归 + 三轴正交 + 对比度 + 六重区分
```

---

## 9. 从现有 pilot 迁移到本标准

当前 `core/ui/theme/VintageOutdoorTheme.kt` = **详情页专用薄主题**（4 套 `ColorScheme` 写死在文件里）。迁移到本标准：
1. 把 4 套 `ColorScheme` 的**源色**抽成 `SkinPalette` 四件套（seaLight/seaDark/inlandLight/inlandDark）放入 `TideSpotSkin.VintageOutdoor`。
2. `VintageOutdoorTheme(waterBody, darkTheme)` 改为 `TideSpotTheme(skin = TideSpotSkins.VintageOutdoor, water = waterBody, darkMode = darkTheme)` 的**薄封装**（保持现有调用点 `MainActivity:357` 不改签名，降低回归面）。
3. 全局 `MainActivity:87` 的 `TideSpotTheme { }` 升级为传 `skin + water + darkMode`，从而把换皮从「详情一屏」扩到全 App。
4. 逐页去硬编码色（§4.5），用 §6.3 验收。

> 目标终态：**`TideSpotTheme` 是唯一主题入口；`TideSpotSkins.all` 是唯一皮肤扩展点；业务屏零硬编码色。**
