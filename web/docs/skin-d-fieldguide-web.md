# D · 路亚图鉴（LURE FIELD GUIDE）Web 皮肤

> 设计源：`design-handdrawn/D-field-guide`（30 屏，780×1688）。
> 工程源：Android `d-fieldguide`（`core:ui/theme/TideSpotSkin.kt`）。
> Web 落地：SKIN-1 的 **shape / typography / texture** 轴，视觉层集中在 `src/styles/fieldguide.css`。

## 1. 边界

| 只换「长什么样」 | 冻结不动 |
|---|---|
| 色板（sea / inland × light / dark） | Tab 渲染序 |
| 描边宽度 / 颜色 | 免责 5 条 |
| 圆角（3 / 8 / 10 / 14 / 18 / pill） | 五档 / 六徽标色 |
| 字重 / 字距 / 数字等宽 | DaySelector 52px |
| 硬投影 / 纸纹 / 贴纸鱼 / 印章 | 水质 1×3（无 pH） |
| 空态插画 | 地图优先 / 准星唯一真源 / 双水体六重区分 |

A · 复古户外不受影响：所有新增规则都限定在 `[data-skin='d-fieldguide']`；A 的图表色、卡片阴影、圆角、字体保持原样。

## 2. Token（`--fg-*`）

| Token | 浅色 | 深色 | 用途 |
|---|---|---|---|
| `--fg-line` | `#101820` | `#F2EFE6` | 描边 / 印章线（深色反转为暖白） |
| `--fg-line-w` | `2px` | `2px` | 卡片 / 按钮主描边 |
| `--fg-line-w-thin` | `1.5px` | `1.5px` | chip / 徽标 / 表格细分隔 |
| `--fg-edge` | `ink 24%` | `black 90%` | 硬投影颜色 |
| `--fg-shadow-sticker-sm` | `2px 2px 0` | 同 | chip / 按钮 / 日期选中 |
| `--fg-shadow-sticker` | `3px 3px 0` | 同 | 卡片 / 面板 |
| `--fg-shadow-sticker-lg` | `4px 4px 0` | 同 | 评分卡 / 地图信息条 |
| `--fg-accent` | `--color-mint`（海 #E5644C / 陆 #E9A13B） | 同轴 | 数据焦点 / 选中日期 |
| `--fg-on-accent` | `#101820` | `#0B1620` | accent 实底上的文字（≥4.5:1） |
| `--fg-stamp` | `#3E9E5A` | `#67C2A5` | 评分卡虚线盖章 |
| `--fg-radius-seal` | `3px` | 同 | 徽标 / 标签 |
| `--fg-radius-chip` | `8px` | 同 | 小控件 |
| `--fg-radius-card` | `14px` | 同 | 卡片 / 评分卡 / 地图卡 |
| `--fg-radius-sheet` | `18px` | 同 | 底部抽屉 |
| `--fg-radius-pill` | `999px` | 同 | chip / 导航 / 日期 |
| `--fg-mono` | `ui-monospace / SF Mono / Consolas` | 同 | 坐标 / 分数 / 表格数字 |

字号与行高沿用 `tokens.generated.css`（与 A 全等）；D 只改字重（800）、字距（.01–.02em）与数字的 `tabular-nums`。

## 3. 组件映射

| 组件 | D 皮肤规则 |
|---|---|
| `.ts-card` | 纸卡 `#FFFDF7` + 2px 墨线 + 14px 圆角 + 3px 硬投影；标题下虚线 |
| `.ts-btn` | primary 实底 + 2px 墨线 + 2px 硬投影；ghost 纸卡同款；`:active` 下沉 1px 且阴影归零 |
| `.ts-chip` / `.ghost-button` | 药丸 + 1.5px 墨线；accent = ink 文字（拒绝白字 3.3:1） |
| `.ts-badge` | 3px 印章角 + 2px 墨线（六色仍取冻结值） |
| `.ts-tabs` | 纸面 + 2px 下边线；选中 primary 文字 + 3px accent 短下划线 |
| `.ts-day__chip` | 52px 高 + 14px 圆角 + 2px 墨线；选中 accent 实底 + ink 文字 + 2px 硬投影 |
| `.ts-score` | **图鉴贴纸卡**：纸底、墨线、4px 硬投影；56px accent 分数 + `/100`；右上绿色虚线鱼章；五档色带加 1.5px 描边 |
| `.ts-table` | 2px 表头线 + 1px 淡墨行线；右对齐数字列 `.is-num` 等宽 |
| `.ts-sheet` | 纸面 + 2px 顶部墨线 + 18px 上圆角 + 向上硬投影 |
| `.ts-field input` / 搜索 | 2px 墨线 + 纸底；focus 用 3px accent 外圈 |
| 空态 `.fg-empty` | 贴纸鱼 + 标题；A 皮肤自动退化为纯文案 |

## 4. 页面映射

| 页面 | D 皮肤 |
|---|---|
| AppShell | 纸面顶栏 + 2px 墨线；品牌 32px 圆形贴纸鱼 + `LURE FIELD GUIDE` 小标；导航印章片；移动底栏 accent 顶线 |
| 设置 | 皮肤选择改为可视化 **SkinPicker**（色板条 + 中英文名 + `已选` 印章）；两栏导航贴纸化 |
| 详情 | 水体胶囊（波浪 SVG + `海洋/近海 COASTAL` / `内陆/湖泊 INLAND`）；日期条横滑；Tab 下划线；评分卡贴纸化；状态条虚线印章 |
| 收藏 | 卡片网格：圆形勾选 / 信息 / 大号分数+档位 / 贴纸鱼；摘要只留窗口时间；空态贴纸鱼插画 |
| 地图 | 2px 内框 + 虚线准星 + 中央贴纸鱼 pin；HUD 全部贴纸化；MapLibre 缩放控件下移避开「我的位置」；Top10 条 accent 标签 |
| 桌面三栏 | 侧栏 2px 分隔；选中项 primary + 硬投影；右栏分数 accent |

## 5. 图标（`ic_*_fieldguide`）

| 文件 | 组件 | 用途 |
|---|---|---|
| `src/ui/icons/ic_fish_fieldguide.tsx` | `IcFishFieldguide` | 品牌、评分盖章、收藏卡、空态、皮肤卡 |
| `src/ui/icons/ic_wave_fieldguide.tsx` | `IcWaveFieldguide` | 详情页水体胶囊 |
| `src/ui/icons/ic_crosshair_fieldguide.tsx` | `IcCrosshairFieldguide` | 地图准星四向刻度 |

全部内联 SVG：无外链、无 emoji、`currentColor` + `--fg-line` 描边，离线/PWA 可用。

## 6. 深色

- 描边反转为暖白 `#F2EFE6`（不做机械反转）。
- 硬投影用黑色 90%，在 `#0B1620 / #12222E` 上保持「浮起」。
- 纸纹 `mix-blend-mode: screen`，opacity 0.08。
- accent 在深色下自动变亮（海 `#FF8A70` / 陆 `#FFC15E`），其上文字用 `#0B1620`。

## 7. 图表

- `LineChart` 支持 `var(--…)` 颜色：Canvas 内解析，`data-theme / data-skin / data-water` 变化时重绘。
- D 皮肤折线宽 2.6px、末点 4px 并加墨线描边；网格为 18% 墨线。
- `HourChart` 柱体加 1.5px 墨线描边，窗口条同步；坐标标签用等宽字体。
- A 皮肤继续使用原有固定色与绘制参数（`chartColor()` 只在 D 返回 `var()`）。

## 8. 验收

| 项 | 结果 |
|---|---|
| `npm run typecheck` | PASS |
| `npm test` | 128 passed / 2 skipped |
| `npm run build` | PASS（需将 `TEMP` 指到工作区内，见下） |
| `npm run check:budget` | PASS（主 CSS 11.3 KB gzip / 预算 30 KB） |
| `npm run check:contrast` | 21 pairs / 0 fail |
| `npm run test:e2e` | 8 passed |

> Windows 构建提示：若沙箱禁止写 `C:\Users\…\Temp`，先设置
> `$env:TEMP='D:\workspace\weatherfishcheck\web\.tmp\esbuild'; $env:TMP=$env:TEMP` 再 `npm run build`。

## 9. 新增皮肤 / 新页面约定

1. 色板只加到 `styles/skins.css` 的 `[data-skin='…'][data-water][data-theme]`。
2. 形状/描边/投影等表达层加到 `styles/fieldguide.css`（或同级的皮肤 CSS），并全部加 `[data-skin='…']` 前缀。
3. 新图标命名 `ic_<name>_<skin>.tsx`，组件 `Ic<Name><Skin>`，只接受 `size / className / title`。
4. 业务组件不感知皮肤；禁止在 JSX 里写死皮肤色（图表用 `chartColor()`，DOM 用 CSS 变量）。
5. 不新增 Tab、不改文案 key、不动五档/六徽标色与 52px 日期卡。