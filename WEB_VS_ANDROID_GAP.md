# Web 版 vs Android 差距清单 · WEB_VS_ANDROID_GAP

> 版本 v1.0 ｜ 日期 2026-11-13 ｜ 性质：**差距审计（含已修项）**
> 审计依据：`tidespot-android/**` 源码、`design-handoff/screens/screens.md`、`design-handdrawn/A-vintage-outdoor/feature-gaps.json`、`core/ui/theme/TideSpotSkin.kt`
> 结论：**当前 Web 版是「骨架可用」而非「功能对齐」**；主要流程（地图→详情→收藏→设置）能走通，但交互与状态覆盖明显少于 Android。

---

## 一、已修复（本轮）

| # | 问题 | 根因 | 修复 |
|---|---|---|---|
| F-1 | **地图空白** | 打包后 MapLibre worker URL 失效（`Worker failed to load`），瓦片无法解析 | `public/maplibre-gl-worker.mjs` + `setWorkerUrl(BASE_URL + ...)`；诊断确认 style/瓦片/字体均 200，底图正常渲染 |
| F-2 | 地图页右侧被「详情预览」挤占 | 地图 tab 仍渲染桌面 detail 栏 | `showDetail = desktop && detailOpen && tab !== 'map'` |
| F-3 | **无皮肤** | 从未实现 SKIN-1 的 Web 端 | 新增 `src/styles/skins.css` + `src/ui/skin.ts`：`a-vintage` / `d-fieldguide` × 海/内陆 × 明/暗，共 8 组色板；圆角/字族随皮肤；设置页可切，水体由点位自动切 |
| F-4 | **地图可交互区域≈0**（比 worker 更底层） | `.map-stage` 子元素全为 `absolute`，自身无高度；后置规则又把 `position` 覆盖回 `relative`。实测 `.maplibregl-map` 高度 **0px**，canvas 以默认 300px 溢出且不接收命中 | `.map-stage` → `position:absolute; inset:0`；桌面档 `.map-page--desk .map-stage` 单独 `position:relative; flex:1`。实测地图 **390×656**（手机）/ **760×759**（桌面） |
| F-5 | **详情页「加载中…」可长达分钟级** | 客户端默认超时 30s + 重试 3 次（1/4/10s）+ 双源并行 → 最坏 ≈105s；天气域慢时用户只看到转圈 | 默认超时降到 **10s**（对齐 Android OkHttp）；`getDetail` 加 **15s 整体预算** `withDeadline`，超时按失败降级。实测 15 017ms 收敛并挂「部分维度不可用」 |

> 若你仍看到空白：是旧 Service Worker 缓存了失败版本。DevTools → Application → Service Workers → **Unregister**，再 Ctrl+Shift+R。

---

## 二、功能差距（按影响排序）

### P0 · 主流程缺件（建议下一批）

| # | 差距 | Android 依据 | 现状 | 影响 |
|---|---|---|---|---|
| G-01 | 地图**搜索框**（顶部白色搜索框 + 地名候选面板 + 选中后移动准星） | `MapScreen.kt` C02 搜索框；`SearchPanel.kt`；`GeocoderApi` | **已补（B1）**：`MapSearch.tsx` + `data/geocoding.ts` | 数据源与 App 同源（Open-Meteo Geocoding），中国小地名覆盖有限 |
| G-02 | 地图**收藏星标图层**（已收藏点显示星标，点击出气泡 OV05） | `FavoriteStarLayer`、`MapScreen.kt` `favoriteStars` | **已补（B1）**：`MapFavorites.tsx` + 本地 `hourly_score` 摘要 | 无缓存时显示「暂无窗口摘要」，不造假 |
| G-03 | 地图**信息浮条独立操作面板**（详情/收藏/刷新等按钮分区） | `MapScreen.kt` C03 236dp 浮条 + 操作面板 | **已补（B1）**：浮条加「查看收藏」，星标气泡内「查看详情」 | 分享/批量刷新按钮仍缺 |
| G-04 | 详情**图表 / 数值表分段切换** | `DetailScreen.kt` 图表/数值表胶囊 | **已补（B2）**：逐时卡片内 `SegmentedTabs` + `hourTableRows`（纯函数单测） | 数值表与图表同源，档位复用五档冻结值 |
| G-05 | 收藏**排序**（推荐/最近/分数）、**分组**、**标签**、**窗口摘要**、**水体类型**、**批量刷新** | `FavoritesViewModel` `SortOrder`、`FavoritesGroupLogic`、`FavoritesBatchLogic`、`FavoritesSummaryFormatter` | **已补（B2）**：`FavoritesPage.tsx` + `domain/favorites.ts`（22 条单测） | 四序/搜索/分组折叠/标签/多选批量/刷新全部；缩略图留 B5 |
| G-06 | 设置页**语言切换**（中/英） | `SettingsScreen.kt` 语言版块；572 key 已双语 | **已补（B3）**：`ui/i18n.ts` 全局订阅 + 设置页「显示 / Language」 | 原先 `useLang` 是局部 state，切了只有设置页变——已修 |
| G-07 | 设置页**底图提供方**（OpenFreeMap / CARTO） | `SettingsLogic.resolveBasemapUrl`、`MapProvider` | **已补（B3）**：`ui/settings.ts` + `STYLE_URLS` 两组 × 三样式 | 切换后按当前样式重载 |
| G-08 | 设置页**Copernicus 凭证**（用户名/密码/校验/清除） | `CopernicusCredentialSection`、`FileCopernicusCredentialStore` | **不做（B3）**：Web 无 OIDC token 流程与数据链路，只做输入框是假功能 | 记为跨端差异，待有真实需求时两端同步立项 |
| G-09 | 设置**免责 / 隐私与来源 / 关于** | `SettingsScreen` 六版块 | **已补（B3）**：隐私 6 条 + 许可 8 条 + 免责 5 条（全部复用现有 key） | 无新增文案，parity 不变 |

### P1 · 体验与状态覆盖

| # | 差距 | Android 依据 | 现状 |
|---|---|---|---|
| G-10 | S03 **ΔV 明细 + 原始输入**（不只因子表） | `DetailScreen.kt` 因子分解三段布局 | 只有因子表 |
| G-11 | S05 **安全摘要卡**（免责第 2 条原文） | `DetailScreen.kt` 天气安全卡 | 只有曲线与区间 |
| G-12 | 水质**三卡**（水温 / 溶氧 / 浑浊度，各带评价） | `WaterQualityTab` 三卡 | **已补（B4）**：`wq-cards` 三卡 + 档位评价 | 缺测显示「当日无样本」，不补 0 |
| G-13 | S07 **潮汐相位 / 校准对照表 / 现场校潮 C14** | `TideCalibrator`、`ManualTideFit`、`DetailScreen` 校准 | 仅潮位曲线 + 占位 |
| G-14 | **观测录入 Sheet（OV04）**（水质实测/潮汐观测） | `TideObservationEntryLogic`、`WaterQualityEntrySheet` | 无 |
| G-15 | **状态条**：离线 / 陈旧（超 TTL）/ 48h 不可用 / 分维降级 | `OfflineBanner`、`C17/C18/C25` | **已补（B2）**：`detail-status` 多标签（离线/48h/降级/缓存回退） | 事件驱动（online/offline + detail 状态） |
| G-16 | 详情页**收藏星标**（顶栏） | `DetailScreen` 顶栏星标 | 仅静态「收藏」Chip |
| G-17 | **来源时间行格式**（「数据时间 HH:mm · 本机拉取」） | `ts_detail_source_update` | 简化拼接 |
| G-18 | 地图 Top10 **自定义时段**（起止时间） | `OV01` 时段 Chip + 自定义 | **已补（B1）**：`filterByHourRange` + 起止时间输入 | 按窗口起点本地小时过滤，支持跨午夜（起点>终点） |

### P2 · 细节与平台差异

| # | 差距 | 说明 |
|---|---|---|
| G-19 | 收藏**缩略图**（静态瓦片） | Android `MapThumbnailStore`；Web 未做 |
| G-20 | 地图**皮肤配色**（mapLand/mapWater/mapRoad 呼应） | Android 每皮肤 4 套地图三色；Web 未接入（瓦片由 OpenFreeMap 决定） |
| G-21 | **字体缩放**设置 | Android 有；Web 未做 |
| G-22 | **数据管理**（清缓存/查看 fetch_log） | Android 有 fetch_log 展示；Web 仅存储估算 |
| G-23 | 骨架屏 / 空态统一 | 部分页面仍是「加载中…」 |
| G-24 | 无障碍（aria 全覆盖） | 关键按钮已 aria-label，图表缺文字摘要 |

---

## 三、文案与 i18n 现状

- 已有 **572 key × 2 语**（由 Android `strings.xml` 生成，`check:parity` 0 差异）。
- 但 UI 中仍有**硬编码中文**：地图页（「收藏并查看」「定位」「海温」）、详情页部分（「最佳窗口」「逐时指数」、Tab 名走 key 但因子表列名硬编码）、设置页（「皮肤」「本地存储」等）。
- 这正是 G-06（语言入口）之外的第二层问题：**先接 i18n 切换，再清硬编码**，否则切英文会出现中英混杂。

---

## 四、建议实施顺序

| 批次 | 内容 | 估算 |
|---|---|---|
| **B1（P0 地图闭环）** | G-01 搜索、G-02 星标图层、G-03 操作面板、G-18 自定义时段 | 4 人日 |
| **B2（P0 详情/收藏）** | G-04 图表↔数值、G-05 收藏排序/分组/标签/摘要/批量、G-15 状态条 | 5 人日 |
| **B3（P0 设置与 i18n）** | G-06 语言入口、G-07 底图提供方、G-08 凭证、G-09 免责/隐私/关于；清理硬编码中文 | 4 人日 |
| **B4（P1 详情深度）** | G-10 ΔV/原始输入、G-11 安全摘要、G-12 水质三卡、G-13 校准、G-14 观测录入 | 6 人日 |
| **B5（P2 细节）** | G-16/17/19/20/21/22/23/24 | 4 人日 |

> 皮肤（F-3）与地图空白（F-1）已完成；B1 起才是「功能对齐」主体。

---

## 五、审计口径说明

- 「Android 依据」均为现有代码/设计文档可核对的位置，不是推断。
- 未列入的「小程序」不在本表（见 `WECHAT_MINIPROGRAM_FEASIBILITY.md`）。
- 本清单不含性能/部署项（见 `WEB_PROJECT_PLAN.md` §8）。