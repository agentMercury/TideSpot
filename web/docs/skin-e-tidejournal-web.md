# E · 潮汐手帐（TIDE JOURNAL）Web 皮肤

> 概念源：`idea-explorations/v3-fieldguide`（路亚图鉴探索稿 · SCORE TAB）。
> 视觉层：`src/styles/fieldnotes.css`（只作用于 `[data-skin='e-tidejournal']`）。
> 定位：在 A（复古户外）与 D（路亚图鉴）之外的第三套内置皮肤。

## 1. 概念

**一本随身的手帐**：楷体标题、纸卡实墨投影、珊瑚贴纸、空心描边巨分数、背景鱼群水印、底部海底波带。
与 D 的区别：

| | D · 路亚图鉴 | E · 潮汐手帐 |
|---|---|---|
| 标题字体 | 系统黑体 800 | 楷体（KaiTi） |
| 分数 | 珊瑚实色 56px | 84px 空心 + 2.5px 墨描边 + 4px 珊瑚错位影 |
| 档位 | 药丸贴纸 | 4px 圆角贴纸 + 2° 旋转 |
| 卡片 | 3px 硬投影 | 4px 实墨投影（纯 ink） |
| Tab | 纸面 + 下划线 | 药丸，选中墨底 + 珊瑚影 |
| 五档 | 色块 + 鱼数 | 8px 细色条 + 7px 标签 |
| 装饰 | 贴纸鱼 | 水彩鱼群水印 + 海底波带 |
| 图表 | 墨线柱 + 等宽坐标 | 同墨线柱，网格更淡 |

## 2. Token（`--fn-*`）

| Token | Light | Dark |
|---|---|---|
| `--fn-ink` | `#101820`（outline） | `#F2EFE6` |
| `--fn-paper` | `#FFFDF7` | `#12222E` |
| `--fn-soft` | `#456A84`（海）/ `#4A6B5E`（陆） | `#9FB6C4` / `#9DB8A8` |
| `--fn-accent` | 珊瑚 `#E5644C` / 琥珀 `#E9A13B` | `#FF8A70` / `#FFC15E` |
| `--fn-accent2` | 海蓝 `#1E6FA8` / 青绿 `#2F7F6B` | `#6FB6E8` / `#67C2A5` |
| `--fn-water` | `#B7DAEE` / `#B9E0CE` | `#17394C` / `#16382A` |
| `--fn-water2` | water 62% + accent2 | 同轴 |
| `--fn-edge` | 纯 ink | 黑 90% |
| 阴影 | `2px/3px/4px 0` 无模糊 | 同 |
| 圆角 | 卡 12 / 日期 16 / sheet 14 / chip 20 | 同 |

字阶沿用 `tokens.generated.css`；E 只改字族（标题 KaiTi）、分数视觉与纸卡材质。

## 3. 页面

| 页面 | E 皮肤 |
|---|---|
| AppShell | 楷体品牌 + 圆形剪影鱼章；药丸导航（选中墨底 + 珊瑚影）；固定鱼群水印 |
| 详情 | 标题 → 水体墨色贴纸 → 日期卡 → 药丸 Tab → 贴纸评分卡（空心分数）→ 细色条图例 → 柱状图 → 因子进度条 → 海底波带 → 免责手帐栏 |
| 收藏 | 楷体分组头；单行纸卡（圆勾选 / 名称 / 分数 / 剪影鱼）；空态水彩鱼剪影 |
| 地图 | 2px 地图版心；纸面搜索卡；药丸样式组；黑边虚线准星 + 珊瑚鱼 pin；纸面 Top10 药丸；贴纸化 MapLibre 控件 |
| 设置 | 三皮肤选择卡（新增潮汐手帐色板）；楷体分区标题 |

## 4. 新增资产

| 文件 | 说明 |
|---|---|
| `src/styles/fieldnotes.css` | E 皮肤视觉层 |
| `src/ui/fieldnotesMarks.tsx` | `FieldnotesWatermark` / `FieldnotesSeabed` |
| `src/ui/factorBars.tsx` | 因子分解的手帐进度条（仅 E） |
| `src/ui/icons/ic_fish_fieldnotes.tsx` | 水彩鱼剪影 |
| `src/ui/icons/ic_wave_fieldnotes.tsx` | 水体贴纸的极简波浪 |

## 5. 边界

- A / D 的 CSS 不受影响；E 规则全部 `[data-skin='e-tidejournal']` 作用域。
- 共享组件只新增「按皮肤显示不同图标」的节点，默认 `display:none`；A/D 不显示 E 资产。
- 结构、Tab 序、免责 5 条、五档/六徽标色、DaySelector 52px、水质 1×3、地图优先、准星唯一真源、文案 key 全部未改。
- 详情页头部在本次修复中回到正确层级（标题 → 水体贴纸 → 吸顶日期/Tab），A/D 同步恢复原始结构。

## 6. 验收

- `npm run typecheck` / `npm test` / `npm run build` / `check:budget` / `check:contrast` / `test:e2e`。
- 截图：海/陆 × 浅/深、详情/收藏/地图/设置，以及 A/D 回归（`web/.tmp/e/`）。