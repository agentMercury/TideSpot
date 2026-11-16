# A · 复古户外（VINTAGE OUTDOOR）Web 表达层

> 设计源：`design-handdrawn/A-vintage-outdoor/index-full.html`（第②组新稿 `detail-tab-*`）。
> 实现：`web/src/styles/vintage.css` + `web/src/ui/icons/ic_fish_vintage.tsx`。
> 作用域：全部规则以 `[data-skin='a-vintage']` 开头，不改变 DOM 契约与其他皮肤。

## 1. 设计语言

| 维度 | 取值 |
| --- | --- |
| 纸底 | `--color-bg` / `--color-card`，叠 160px SVG 噪点，normal 0.13 / dark screen 0.16 |
| 墨线 | `--vt-ink` = `--color-text`；主卡 2.2px，辅助 1.6px |
| 硬阴影 | `2px 3px 0` 墨色 22%（大卡 `3px 4px`），不用模糊投影 |
| 砖红 | 分数 / 选中日期 / 选中标签 / 底部导航；小字号用 `--vt-rust-deep` 保证 4.5:1 |
| 芥末黄 | 鱼鳍、印章鱼、地图准星点 |
| 字体 | 正文 `KaiTi / STKaiti / Kaiti SC`；标题与数字 `Georgia / Songti SC` |
| 圆角 | 卡片 `16px 20px 15px 19px`，sheet `20px 26px 20px 27px`，按钮 `10px 14px 10px 15px` |

## 2. 逐页映射

### 详情页

- 标题：Georgia 18px、字距 0.1em；右上「收藏」chip + 虚线鱼印章（砖红/芥末黄）。
- 水体贴纸：纸底、1.6px 墨线、20px 胶囊、2px 硬阴影，波浪图标显示，手帐线隐藏。
- 吸顶区：日期卡 52px 高、选中深砖红；**不显示日期角标分数**（对齐新稿）；标签为纯文字 + 19px 短下划线，无外框、无鱼形。
- 评分卡：三列 grid（分数 / 分隔线 / 文字），`81 / 100` 砖红 Georgia 40px；档位文字独立成行，徽标纸底墨线；鱼印章 38px 虚线框；五段色带保留冻结色与 `is-active` 加粗。
- 卡片：图表 / 最佳窗口 / 因子分解统一纸底 + 墨线 + 硬阴影；图表与数值表切换为纸面拨杆。

### 收藏页

- 卡片 grid：圆勾选 20px / 标题与摘要 / 分数 20px + 档位 / 箭头 `›`。
- 勾选为圆形；选中藏青实心 + 纸色对勾。
- 标题 Georgia 15.5px；副标题坐标 Georgia 8.5px；摘要只保留最佳窗口时间。
- 操作行以 1px 虚线分隔；不渲染鱼图标（对齐新稿右侧箭头）。

### 地图页

- 底图瓦片不动，仅 HUD：纸底墨线搜索、样式按钮、地点信息卡、Top10 aside、MapLibre 控件。
- 准星：墨色虚线圈 + 芥末黄点 + 纸色内描边；隐藏多余蓝色 pin / 十字刻线。
- 地点名 Georgia、坐标 Georgia 小字；收藏星为纸底墨线圆 + 芥末黄。

### 外壳 / 设置

- 顶栏与侧栏纸底、2.2px 墨线；品牌鱼为芥末黄版画鱼；导航激活为藏青实心 + 硬阴影。
- 底部 tabbar 纸底，激活砖红。
- 皮肤卡墨线纸底；A 的 swatch 仍为 `#e4d9bc / #1b3a5c / #d9a734`。

## 3. 空态与图标

- 新增 `IcFishVintage`（限色块 + 粗描边 + 鳃线 + 芥末黄鳍），用于品牌、详情印章、评分印章、空态。
- 其他皮肤的鱼图标不受影响：`--vintage` 变体默认 `display:none`，仅 A 开启。
- D 继续显示 `--guide` 印章；E/F 印章保持隐藏。

## 4. 未改动

- Tab 顺序、免责 5 条、五档 / 六徽标色、DaySelector 52px、水质 1×3（无 pH）、地图优先、准星唯一真源、全部 i18n key。
- 水体与明暗的四套 palette 仍由 `skins.css` 提供；本次只调整 A 的深色 palette，使其保持奶油墨线 / 珊瑚强调的复古印刷感。

## 5. 验收

- `typecheck`、`test`（128 passed / 2 skipped）、`build`、`check:budget`、`check:contrast` 全通过。
- 截图：`.tmp/vintage/01-map.png`、`02-detail-light.png`、`05-favorites.png`、`08-detail-dark.png`、`11-favorites-dark.png`、`06-harness.png`。
- D/E/F 回归：`.tmp/vintage/12-regress-*.png`，评分卡背景、印章显隐、日期分数均保持原状。