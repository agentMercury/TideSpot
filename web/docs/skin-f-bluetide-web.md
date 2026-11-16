# F · 蓝潮（BLUE TIDE）Web 皮肤 · 默认

> 概念源：`TideSpot-UIv3-design-handoff.zip` / `design-handoff/`（v3 干净基线）。
> 视觉层：`src/styles/bluetide.css`（只作用于 `[data-skin='f-bluetide']`）。
> 默认皮肤：`DEFAULT_SKIN = 'f-bluetide'`。

## 1. 概念

把 handoff v3 的「白卡 + 1px 浅描边 + 柔和阴影 + 蓝/薄荷点缀」直接落成 Web 皮肤：

- 无纸纹、无硬投影、无贴纸、无粗黑描边
- 卡片 16px 圆角，按钮 12px，chip 18px
- 主卡保留 handoff 的深蓝渐变 `#1565C0 → #0D47A1`
- 薄荷只出现在水体标签、选中锚点、准星与 mint chip，保持克制

## 2. Token

| Token | Light | Dark |
|---|---|---|
| primary | `#1565C0` | `#90CAF9` |
| secondary | `#00695C` | `#80CBC4` |
| tertiary | `#26A69A` | `#80CBC4` |
| background | `#EEF3F8` | `#0E1721` |
| card | `#FFFFFF` | `#16212E` |
| surfaceVariant | `#EEF3F8` | `#1E2C3A` |
| 次文字 | `#556579` | `#9FB0C0` |
| outline | `#DCE5EE` | `#2A3A4C` |
| mint | `#80CBC4` / deep `#26A69A` / bg `#E0F2F1` | `#80CBC4` / `#12302C` |

内陆：primary 青绿 `#00695C`、bg `#EDF6F4`、outline `#D6E7E2`；深色 `#0C1B18 / #132420`。

阴影：`0 3px 10px rgba(16,36,58,.06)`（卡）/ `0 10px 26px rgba(13,71,161,.20)`（浮层）。

## 3. 细节

| 位置 | 处理 |
|---|---|
| 详情 | 白底水体胶囊（薄荷波浪）；主卡深蓝渐变；五段色带保留冻结色；Tab 为 M3 分段胶囊 |
| 收藏 | 白卡 + 左侧 4px 档位色带（`--fav-band` 由 `resolveScoreBand` 注入）+ 右侧分数列 + 圆勾选；无鱼贴纸 |
| 地图 | 白卡搜索/信息条；薄荷虚线准星 + 实心点；Top10 保持 handoff 深蓝横幅 |
| 设置 | 四皮肤选择卡，蓝潮排第一并默认选中 |
| 品牌 | 纯文字，副标题 BLUE TIDE；无图形章 |

## 4. 默认切换

- `SKINS` 顺序：蓝潮 → 复古户外 → 路亚图鉴 → 潮汐手帐。
- `DEFAULT_SKIN = 'f-bluetide'`；`normalizeSkin()` 对未知/空值回落到蓝潮，并把旧 id `f-cleartide` 迁移到 `f-bluetide`。
- 已存 `localStorage` 的老用户保持原皮肤；新用户/清站点数据即为蓝潮。
- PWA `theme_color` 同步为 `#1565C0`。

## 5. 边界与验收

- A / D / E 的规则均带各自 `[data-skin]` 前缀，F 不覆盖它们。
- 冻结契约未改：Tab 序、免责 5 条、五档/六徽标色、52px 日期卡、水质 1×3、地图优先、准星真源、文案 key。
- 校验：`typecheck` / `test` / `build` / `check:budget` / `check:contrast` / `test:e2e`。
- 截图：`web/.tmp/f/`（默认设置、四皮肤卡、收藏空态、地图、详情浅/深、评分卡 harness）。