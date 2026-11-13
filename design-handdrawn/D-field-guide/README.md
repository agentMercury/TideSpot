# 潮点 TideSpot · D · 路亚图鉴（LURE FIELD GUIDE）— 完整 UI 30 屏

> **定位**：`idea-explorations/v3-fieldguide` 探索稿的**完整 30 屏工程化设计稿**。
> **同源皮肤**：工程侧 `d-fieldguide`（`core:ui/theme/TideSpotSkin.kt`），v1.17.0 已内置。
> **口径**：皮肤只换「长什么样」；30 屏的结构、Tab 序、免责 5 条、五档/六徽标、DaySelector、水质 1×3 全部与 A 共用。

---

## 1. 交付物

| 文件 | 说明 |
|---|---|
| `screens/`（7） | 基础主屏 2x PNG（780×1688） |
| `screens_full/`（23） | 详情 5 Tab / 因子分解 / 6 叠层 / 4 状态态 / 空态批量 / 深色 2x PNG |
| `sources/`（7） | 自包含 HTML 源稿（无外链、无相对路径 img、内联手绘滤镜） |
| `sources_full/`（23） | 同上 |
| `spec.json` | 30 屏清单 + SHA-256 + 双水体色板 + `skin_system`（A/D 两款） |
| `index-full.html` | 30 屏分组索引（内嵌 base64 缩略图） |

## 2. 视觉语言

- **纸底**：海洋 `#E9F3FA` / 内陆 `#E9F5EE`，平涂冷白卡片 `#FFFDF7`。
- **描边**：近黑 `#101820` 粗描边 + 硬投影贴纸（与 A 的「做旧纸 + 套印」区分）。
- **强调色**：海洋 海蓝 `#1E6FA8` + 珊瑚 `#E5644C`；内陆 青绿 `#2F7F6B` + 琥珀 `#E9A13B`。
- **形状**：小圆角印章（3/8/12/14/18dp，非对称手绘圆角在 A）。
- **字阶**：系统黑体，标题/标签更粗（800/700），字号行高与 A 全等。

### 4 套色板

| 水体 | 浅色 | 深色（独立调色） |
|---|---|---|
| 海洋 | `bg #E9F3FA` / `primary #1E6FA8` / `card #FFFDF7` | `bg #0B1620` / `primary #6FB6E8` / `card #12222E` |
| 内陆 | `bg #E9F5EE` / `primary #2F7F6B` / `card #FFFDF7` | `bg #0C1A14` / `primary #67C2A5` / `card #12261C` |

> 深色不是浅色的机械反转；`outline` 在深色下反转为暖白 `#F2EFE6`，保持贴纸轮廓。

## 3. 与工程 token 的映射

| 设计侧 `STYLES["d-guidebook"]` | 工程侧 |
|---|---|
| `pal.sea / pal.inland`（浅） | `FieldGuideSeaLight` / `FieldGuideInlandLight` |
| `DARK_D.sea / DARK_D.inland` | `FieldGuideSeaDark` / `FieldGuideInlandDark` |
| `font.display/body/num` | `FieldGuideTypography`（SansSerif + Black/Bold） |
| `radius` | `FieldGuideShapes`（3/8/12/14/18dp） |
| `paper.grain 0.05 / tint False` | `SkinTexture(paperGrain=false, grainStrength=0.45f)` |
| `fish/motif` | 复用 A 的 `ic_fish/wave/mount_vintage`（画风未变） |

## 4. 复现

```powershell
# 需要 Python 3 + Pillow + Edge
python design-handdrawn/generator/build_style.py d-guidebook D-field-guide

# 自检（30 PNG/HTML + 冻结契约 + 色板 + 索引 + spec）
python design-handdrawn/generator/selfcheck_style.py D-field-guide
# 期望：SELFCHECK D-field-guide PASS=273 FAIL=0
```

## 5. 验收结果（2026-11-13）

- 30 PNG：780×1688，PNG 签名通过。
- 30 HTML：无外链 / 无 `file://` / 无相对路径 `<img>` / 含 `feTurbulence`。
- 冻结契约：钓鱼指数、免责 5 条、五档、水质 1×3（无 pH）、DaySelector 52px 全部保留。
- 双水体：海/陆 HTML 内容不同，现场校潮 vs 无潮汐。
- 自检：**273 PASS / 0 FAIL**。
- 工程侧：`verify.ps1` 全绿；APK v1.17.0 含 `d-fieldguide`。

## 6. 边界

- **未做**：手绘鱼种图鉴的「线稿鱼群」新 drawable（当前复用 A 版画鱼）；如需独立画风，按 `SKIN-DEV-STANDARD.md` §5 新增 `ic_*_fieldguide`，业务组件零改动。
- **未做**：地图底图仍为 CARTO/手绘示意，不重画真实瓦片。
- **不做**：改布局 / 改文案 / 改 Tab 序 / 改逻辑（换肤反模式）。
- B（拟人鱼绅士）、C（水彩几何鱼）仍是独立探索稿，不并入。