# A · 复古户外 Android 表达层（2026-11-16）

对齐 Web `vintage.css` 定稿，完成 P0/P1/P2 全量更新。结构、Tab 序、免责 5 条、五档/六徽标、地图瓦片与交互契约未变。

## 1. 视觉基线

| 项 | 值 |
| --- | --- |
| 浅色纸底 | `#E4D9BC`（背景）/ `#F2EAD4`（卡片） |
| 深色纸底 | `#101A24`（背景）/ `#1A2836`（海洋卡）/ `#1C2A21`（内陆卡） |
| 墨线 | 2.2dp 单一实墨，直接贴合卡片 Shape；不再叠第二层手绘轮廓 |
| 硬阴影 | 浅色 `onSurface` 18%，深色 `scrim` 55%，偏移 3dp |
| 砖红强调 | 评分 / 选中日期 / 选中标签；浅色 `error #C15A3A`，深色 `#E0805E` |
| 奶油墨线 | 深色 `#E9DCBE / #E6DFC0`，用于卡片、文字与描边 |
| 字体 | 正文 Noto Serif SC 子集；分数/标题数字系统 Serif |
| 纸纹 | 全屏高频 tile，`grainStrength=0.65`（仅纸底，不压文字） |

## 2. 逐页变化

### 详情
- 标题下新增水体贴纸（海/陆母题）+ 右上虚线鱼印章。
- 日期卡 52dp，选中砖红、2.2dp 墨线、硬阴影。
- 五 Tab 改为纯文字 + 18dp 砖红短下划线，去掉外框与鱼图标；高度仍 40dp。
- 评分卡：纸底、砖红分数、`/100`、档位文字 + 单一置信徽标；移除与色带挤压的「实测/模型」双圆。
- 图表/因子/天气/水质卡统一 `vintageCard()`；弹层与自定义时间 AlertDialog 同骨架。

### 收藏
- 勾选圆 22dp；分数 20sp + 档位；右侧 `›` 箭头，移除鱼图标。
- 搜索框改为纸底 + 1.6dp 墨线；空态改版画鱼票据卡。
- 删除 / 批量 / 编辑 / 自定义筛选等 AlertDialog 统一纸面 + 墨线 + 24dp 圆角；Top10 Sheet 纸底 + 墨线拖拽条。

### 地图
- HUD 搜索/定位/收藏/设置全部 `vintageCard()`，字形图标替换为矢量图标。
- 准星改为墨色虚线圈 + 芥末黄点（预览态点变青）。
- 信息条加墨线硬阴影；Top10 入口保持纸卡。

### 设置
- 皮肤下拉、分段控件保持 M3（CHG-95 客户裁决，有意偏离），但深色选中态改为奶油填充，避免低对比。
- 深色卡片/选中态/阴影已按新 deep token 渲染。

### 系统层
- 启动窗背景：浅 `#E4D9BC`、深 `#101A24`。
- 状态栏/导航栏图标随应用内明暗切换（手动深色也生效）。

## 3. 截图基线

`screenshots/a-vintage/`（Honor AAK-AN00 真机）：

- `01-map-light.png`
- `02-detail-light.png`
- `03-favorites-light.png`
- `04-settings-light.png`
- `05-detail-dark.png`
- `06-map-dark.png`
- `07-settings-dark.png`

复采：`tools/capture-a-vintage.ps1`（连接设备/模拟器后运行）。

## 4. 验收

- `gradlew :app:assembleRelease --offline`：SUCCESS，产出 `潮点TideSpot-v1.21.1.apk`。
- `gradlew testDebugUnitTest --offline`：全部通过（含 `SkinRegistryTest` 深色 token / 对比度、`ComposeSmokeRobolectricTest`）。
- 真机 Honor AAK-AN00（Android 16，1272×2800）：浅色/深色、地图/收藏/设置/详情人工走查通过。
- 真机修复：地图底部来源/署名加纸底衬并抬到 MapLibre logo 上方；浅色设置选中态改藏蓝实底。
- 单框修复：移除 `handDrawnOutline` 第二层轮廓，改为 `vintageCard(shape=...)` 的单一 2.2dp 墨线；当前皮肤卡用同一墨线染成芥末黄，不再额外叠框。

## 5. 已知边界

- 地图真实瓦片仍不重绘（S3 裁决）；模拟器无外网时底图会空白，HUD 与准星仍可验收。
- 设置页 M3 `FilterChip/Switch` 仍为有意偏离，未改成票券控件。
- JSON/共享偏好等数据层未改动；深色 token 变化只影响视觉。