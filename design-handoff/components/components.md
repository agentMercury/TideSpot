# 组件速查（人读版）· v3

> 机器可读版本：`components.json`（v3.0.0，25 个组件完整状态矩阵）。冲突时以 JSON 为准。
> 基线：**工程 v1.16.0**。标注「v3」为美化视觉规定，「工程既有」为已实现只需核对视觉值。
> 色值查 `tokens/design-tokens.json`，文案查 `copy/copy.json`，工程落点查 `mapping/design-to-code.json`。

## 1. 组件总表

| id | 组件 | 工程状态 | v3 要点 / 基准屏 |
|---|---|---|---|
| C01 | 准星 Crosshair | visual_tune | 四向刻度内核 + 细环 + 薄荷点三态，改 Compose Canvas · `assets/v3/screen-01-map.png` |
| C02 | 顶部工具浮层 | existing | L2 软阴影 + 搜索 250dp · S01 |
| C03 | 底部选点信息条 | existing | **236dp 浮条 + 操作面板**（非 366×200）· S01 |
| C04 | 确认收藏对话框 | existing | 预填名 + 保存中态 |
| C05 | Top10 BottomSheet | existing | **时段筛选四 Chip + 自选时段弹层**（非范围切换） |
| C06 | 免责 Sheet + 入口 | visual_tune | 恒 5 条；入口条 surfaceVariant 底 + › |
| C07 | DaySelector | existing | **52dp 两行**（topLabel/weekdayLabel）· S02 |
| C08 | 五维 TabBar | visual_tune | 渲染序恒定 5 项；选中胶囊填充 · S02 |
| C09 | HourChart ⇄ 数值表 | visual_tune | ≥80 改薄荷窗格；null 断线不连线 · S02 |
| C10 | 今日指数主卡 | visual_tune | 深蓝渐变 + 柔光 + 56sp 分数 + 五段色带选中高亮 · S02 |
| C11 | FactorBreakdown | existing | 标签左对齐 / 数值等宽右对齐；禁 parse 否决文案 · S03 |
| C12 | ConfidenceBadge | existing | 六档冻结色，勿改 |
| C13 | TideChart | existing | dh/dt 副轴 + 校正前后；客户裁定以现状为准 · S07 |
| C14 | 校准面板 / 现场校潮 | existing | 免责第 5 条 · S07 |
| C15 | 水质指标卡（1×3） | existing | **一行三卡**（删 pH），高 112dp，clipsContent=false · S06 |
| C16 | FilterChip/Switch/凭据区 | visual_tune | 六版块单卡；**API Key/Copernicus 真实可交互** · S09 |
| C17 | 离线/陈旧/48h 条 | existing | 三态配色 + 下拉刷新反馈 · DV01 |
| C18 | 分维降级卡 | existing | 缺失维度逐项可读；禁 parse reason |
| C19 | 收藏列表/入口 | visual_tune | **白卡 + 左档位色带**；批量多选；≥48h alpha 0.6 · S08 |
| C20 | SearchPanel | existing | 选中结果仍把准星落到该坐标 |
| C21 | 地图署名 | existing | **CARTO Voyager**；源更新显示「—」· S01 |
| C22 | 来源与更新时间行 | existing | 未透出字段显示「—」，不编造 · S02 |
| C23 | 星标层 + 气泡卡 | existing | 点星标不动准星；查看详情先收藏再进详情 · S01 |
| C24 | 水质实测录入 Sheet | existing | 全展开按钮常驻可点；离线可录入 · S06 |
| C25 | 内陆引导卡 | existing | 四条件注入；左边 4dp 薄荷色条 · S04 |

## 2. v3 相对 v2 的组件变更（重点）

| 组件 | v2 稿 | v3 / 工程实际 |
|---|---|---|
| C01 准星 | 圆环 + 中心点 | 四向刻度 + 细环 + 薄荷点三态（idle 薄荷 / preview 琥珀 / locked 主蓝） |
| C03 信息条 | 366×200 面板 | 236dp 浮条 + 独立操作面板 |
| C05 Top10 | 本点 / 全部范围切换 | 时段筛选四 Chip + 自选时段弹层 |
| C07 DaySelector | 36dp 单行 | **52dp 两行** |
| C08 Tab 序 | 风浪 → 天气 → 钓鱼指数 → 水质 → 潮流 | **钓鱼指数 → 潮流(水位) → 天气 → 水质 → 风浪** |
| C15 水质 | 2×2 含 pH | **1×3 删 pH** |
| C16 设置 | API Key 占位不可点 | **正式启用 + Copernicus 凭据真实可交互** |
| C17 状态条 | 离线 / 陈旧二态 | 离线 / 陈旧 / **48h 不可用**三态 + 下拉刷新 |
| C19 收藏 | 淡紫灰平铺 + 裸文字操作 | 白卡 + 左 4dp 档位色带 + 操作收进图标 |
| C21 底图 | CARTO Positron | CARTO Voyager 彩色 |
| C23–C25 | 无 | 新增：星标层气泡 / 水质录入 Sheet / 内陆引导卡 |

## 3. 状态覆盖红线（实现不得删减状态）

- **C01**：idle / dragging / preview / settled / locating / permission_denied 六态；点星标不动准星。
- **C05**：filter_all / filter_window / custom_range / empty / expanded；空态禁止 ≥60 兜底造假。
- **C12**：六档动态接线，`UNAVAILABLE → UNCALIBRATED`，禁硬编码 ESTIMATED。
- **C16**：凭据区 empty / filled / validating / saved / invalid / offline / cleared 七态 + API Key 保存。
- **C17**：offline / stale / unusable_48h / pull_refresh / fresh；48h 不作为评分依据。
- **C19**：empty / list / multi_select / batch_action / offline / favorited。
- **C06**：免责**恒 5 条**，顺序 1..5，原文完整、可滚动、禁止 ellipsis。

## 4. 全局易错点（8 条）

1. **Tab 序**：恒 5 项取 `titlesInTabOrder = [score, tide, weather, waterQuality, wave]`，禁止调用方手工组数组（D01 根因即 6 项错位、潮流 Tab 被水质标题覆盖）。
2. **DaySelector**：两行排版（OPT-V12-04 / CHG-57），跨零点「今天」自愈（D14 / CHG-16⑮）。
3. **准星真源**：搜索结果 / 星标 / 地图中心点击都不得绕过 C01；点星标不移动准星。
4. **对比度**：CHG-56 浅色次文字 ≥4.5:1（`#556579`），禁用 `#8A9DB0`。
5. **深色**：冻结语义色不改值，L2 抬升用 `surfaceVariant #1E2C3A`，底图压暗非反色。
6. **文案/数值**：全走 `TideSpotStrings` 与真实序列；源更新未透出显示「—」；示例分数/时间/点位非业务数据。
7. **禁 XML layout**：全部 Compose；图标 drawable 属资源不构成 layout。
8. **凭据/API Key**：决策①正式启用，真实可交互，不得画成假入口；凭据仅本地存储不上传。
