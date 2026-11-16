# 高德地图（AMap）接入可行性评估 · AMAP_FEASIBILITY

> 版本 v1.1 ｜ 日期 2026-11-10 ｜ 编制：交付负责人 ｜ 性质：**评估报告（不改代码）**
> 对应台账：**CHG-36（已裁定）** ｜ 需求登记：`OPT_REQUIREMENTS_V11.md` §7 **OPT-V12-05（D-AM）** ｜ 基线：SRS v1.4 ｜ 评估对象：`:feature:map` 现有 MapLibre 实现

## 0. 结论摘要（TL;DR）

- **可行**：地图展示、地点搜索、逆地理/地理编码均可替换为高德（Android SDK + Web 服务 API），高德在中文 POI、门牌级逆地理、国内网络稳定性上优于现方案。
- **但不可"直接替换"**，三条硬约束：①SRS §12/§7.1 明确 **禁止内置高德 Key**（只能用户自备）；②坐标系 **GCJ-02 vs WGS-84** 必须双向转换，且 WGS-84 保持为唯一真源；③高德**境外覆盖差**，须保留开源底图回退。
- **方案推荐**：单代码库 + `MapSurface` 抽象 + **应用内开关（BYOK）**；不推荐独立分支；"两个 APK 版本"仅在"发行纯净版（不含高德 SDK）"成为硬要求时采用，且以 **productFlavors** 实现而非分支。
- **工作量**：约 **6–9 个 AI 天**（含 1–2 天 spike），不含高德账号/审图号材料准备时间。建议排入 **v1.2 backlog**，不触碰 v1.1 交付。

## 1. 现状基线（代码取证）

| 能力 | 现实现 | 证据 |
|---|---|---|
| 底图 | MapLibre Android 11.8.0 + CARTO Voyager（默认）/Positron/Dark Matter 三选，URL 收敛 `TideSpotUrls`，attribution 保留 | SRS §7.1 `:444`；CHG-21/D8；`MapScreen.kt:71-72` |
| 地图组件 | Compose `AndroidView` 包裹 `MapView`，直接使用 `MapLibreMap/LatLng/Style/CameraUpdateFactory` | `MapScreen.kt:64-69` |
| 逆地理 | `PlaceResolver` 接口（4 级降级链 RG-A/B/C + 坐标兜底），实现由 `:core:data` 提供 | `feature/map/geo/PlaceResolver.kt:10`；SRS §5.8 / CHG-05 |
| 搜索 | `GeocoderApi`（Open-Meteo Geocoding） | `feature/map/geo/GeocoderApi.kt` |
| 水体判定 | `WaterBodyResolver`：marine 实测 + OSTIA 探针 + 缓存（Overpass 已按 M0 结论不接入） | CHG-35 修复链；`DATA_SOURCE_PROBE.md` A9 |
| 准星/信息条/权限 | 纯 Kotlin 状态机 `CrosshairStateMachine` / `InfoBarState` / `PermissionStateMachine` | `feature/map/crosshair|infobar|location` |
| 定位 | 系统 `LocationController`（Fused/LocationManager 双通道） | `feature/map/location/LocationController.kt` |
| 测试 | map 模块 26 个纯 Kotlin 测试（零 Compose/MapLibre 交互测试） | `docs/archive/M3改进意见02.md:87` |

**关键判断**：搜索与逆地理**已有接口抽象**（改造成本低）；**地图渲染/手势/相机没有抽象**，直接绑死 MapLibre 类型（改造成本主要集中于此）。

## 2. 高德能力对照

| 能力 | 高德方案 | 对照现方案 | 迁移结论 |
|---|---|---|---|
| 地图展示 | AMap 3D 地图 SDK（`MapView`/`AMap`/`CameraUpdate`） | MapLibre + CARTO | 可替换，需 `MapSurface` 抽象 + `AndroidView` 双实现 |
| 长按/拖动/相机 | `AMap.setOnMapLongClickListener`、`OnCameraChangeListener` | MapLibre 对应监听 | 语义可对齐；准星"相机中心对准式"逻辑可保留 |
| 地点搜索 | Search SDK `PoiSearch`（关键词/周边/分类） | Open-Meteo Geocoding（中文可用但 POI 弱） | **高德更强**（POI 级、含水域类型标签） |
| 地理编码（地址→坐标） | `GeocodeSearch.getFromLocationName` | Open-Meteo Geocoding | 可替换 |
| 逆地理（坐标→地址） | `ReGeocodeSearch`（含 `aoiName`、门牌） | Photon + 四级降级链 | **高德更强**（`aoiName` 可直接作水体名候选） |
| 坐标转换 | `CoordinateConverter`（GPS/WGS-84→GCJ-02 等） | 无（全链 WGS-84） | **必做双向转换**，且转换仅发生在适配层 |
| 离线地图 | 高德离线地图包（需授权/配额） | Room 数据缓存 + 底图样式缓存 30 d | 可作为 v1.3 增强，本期不做 |
| 定位 | 高德定位 SDK | 系统定位（已双通道） | **不引入**，避免多一个 SDK/权限面 |
| 海况/潮汐数据 | 无 | Open-Meteo Marine | **不受影响**（本来就非地图职责） |
| 境外覆盖 | 弱 | CARTO/OSM 全球可用 | **必须保留回退** |

## 3. 硬约束与合规（必须先解决，否则方案不成立）

| # | 约束 | 依据 | 处置 |
|---|---|---|---|
| C-1 | **禁止内置高德 Key** | SRS §12 `:603`、§7.1 `:444` | 只能 **BYOK**：设置页填 Key（复用 `CopernicusCredentialSection` 模式）；开发期 Key 放 `local.properties`（不入库）。内置需客户裁定改基线 |
| C-2 | 坐标必须 WGS-84 提交数据源 | SRS §5.0 `:97` | 存储/计算/缓存一律 WGS-84；AMap 适配层内做 GCJ-02→WGS-84；渲染前再转回 |
| C-3 | 境外点必须可用 | 附录 B7/B9（NOAA 旧金山等） | 高德模式下境外坐标 → 自动回退 CARTO + Photon，并 UI 提示"该区域使用开源底图" |
| C-4 | 审图号与署名 | 《地图管理条例》/高德 ToS | 高德模式底图角标显示审图号 + "© 高德地图"；开源模式保留 OSM/CARTO attribution |
| C-5 | SDK 隐私合规 | 个保法/高德 SDK 要求 | 首次初始化前调用 `updatePrivacyShow/updatePrivacyAgree`；隐私政策逐源披露（N-5 已有框架） |
| C-6 | 授权与配额 | 高德开放平台 | 个人 Key 有日配额（展示/搜索/逆地理分别计），商用需商用授权；需客户确认发行性质 |
| C-7 | 不得破坏既有验收 | SRS F-1.1~F-1.7、CHG-09/CHG-35 | 准星唯一真源、拖动不切数据（F-1.4）、海域判定修复（CHG-35）在两种模式下行为必须一致 |

## 4. 改造矩阵（工作量与风险）

| 改造项 | 现状 | 工作内容 | 人日（AI） | 风险 |
|---|---|---|---|---|
| `MapSurface` 抽象 | 无 | 抽接口：相机/长按/拖动 idle/图层/标记/attribution；MapLibre 实现回归 | 2–3 | 中：抽象泄漏（MapLibre 类型外溢） |
| AMap 地图实现 | 无 | `AMapMapSurface`：MapView 生命周期、准星、相机、样式、审图号 | 2–3 | 中：Compose 与 AMap `MapView` 生命周期（黑屏/重复 parent，参见 `docs/archive/M3改进意见02.md:87`） |
| 坐标转换层 | 无 | `Gcj02Converter`（双向）+ 精度测试（千岛湖/青岛/深圳三点，判定不翻转） | 1 | 低：算法公开，误差米级 |
| 搜索适配 | `GeocoderApi` | `AmapGeocoder` 实现（POI 搜索→统一 `SearchResult`） | 1 | 低：接口已抽象 |
| 逆地理适配 | `PlaceResolver` | `AmapPlaceResolver`（`aoiName` 映射 `waterName`，保留 plusCode 兜底） | 1 | 中：命名口径与 Photon 不一致，收藏名变化需提示 |
| 设置与开关 | Copernicus 凭据区 | "地图源"三态（开源/高德/自动）+ Key 校验 + 失效回退 | 1 | 低：模式已有先例 |
| 境外回退 | 无 | 坐标围栏判定 + 自动切开源 + UI 提示 | 0.5–1 | 低 |
| 合规与文案 | 部分 | 审图号、隐私披露、许可清单更新 | 0.5 | 低 |
| 测试 | 26 纯 Kotlin | 双 provider 参数化测试 + 坐标往返 + 状态机零回归 | 1–1.5 | 中：需真实 SDK 冒烟（仪器测试缺位） |
| **合计** | | | **9.5–13**（乐观 6–9，含并行） | |

## 5. 交付形态三方案对比

| 维度 | 独立分支 | 两个 APK（productFlavors） | 应用内开关（推荐） |
|---|---|---|---|
| 代码重复 | 高，后续合并困难 | 无（单代码多变体） | 无 |
| 回归矩阵 | 双份 | 双变体构建 | 单构建 + 双路径测试 |
| 合规/包体 | 同 | **开源变体不含 AMap SDK**（包体小、隐私最干净） | AMap SDK 常驻（≈+8 MB，需全覆盖披露） |
| 境外回退 | 各写一遍 | 各写一遍 | **天然支持** |
| Key 门槛 | 仍在（§12 禁内置） | 仍在 | 仍在（但设置内闭环） |
| 用户体验 | 装哪个用哪个 | 装哪个用哪个 | 一个 APK，自选/自动 |
| 维护成本 | 高 | 中 | **低** |
| 结论 | ❌ | ⚠️ 备选 | ✅ |

**推荐理由**：①§12 已强制 BYOK，"高德版本"并不省配置步骤；②只有开关能解决境外覆盖（纯高德版境外不可用，等于功能退化）；③与现有 BYOK 模式一致；④单构建降低 M6 后的维护成本。
**采用 flavors 的唯一条件**：客户要求发行"不含第三方地图 SDK"的纯净 APK（隐私审查/海外发行）。

## 6. 风险台账（风险—影响—概率—应对）

| ID | 风险 | 影响 | 概率 | 应对 |
|---|---|---|---|---|
| AM-01 | 坐标系转换遗漏致点位偏移/水体误判 | 数据错位、判定翻转，验收失败 | 中 | WGS-84 唯一真源；适配层单点转换；三点往返测试进 CI；CHG-35 判定链回归 |
| AM-02 | Key 内置违反 §12 红线 | 合规风险、基线违背 | 中 | BYOK 强制；红线扫描加入"高德 Key 字面量"检查 |
| AM-03 | 境外点在高德模式不可用 | 境外功能退化 | 高 | 坐标围栏自动回退 + UI 提示（C-3） |
| AM-04 | AMap SDK 隐私合规不到位 | 应用商店/监管风险 | 中 | 初始化前同意；隐私政策逐源披露；许可清单更新 |
| AM-05 | 双 SDK 同存导致体积/权限/崩溃面上升 | 包体 +8 MB、N-4 崩溃率 | 中 | 按需初始化（用户启用高德才 init）；双路径冒烟 |
| AM-06 | MapView 生命周期（Compose 包裹）黑屏/重复 parent | 崩溃/黑屏 | 中 | 参照 `docs/archive/M3改进意见02.md:87` 既有经验；`AndroidView` + `DisposableEffect` 明确顺序；真机冒烟 |
| AM-07 | 个人 Key 配额不足（搜索/逆地理计费项不同） | 用户侧功能不可用 | 中 | 设置页显示配额提示与失败原因；缓存（逆地理 30 d/点）复用 |
| AM-08 | `aoiName` 与 Photon 命名口径差异 | 收藏点名称变化、用户困惑 | 低 | 首次切换提示；保留用户手动改名 |

## 7. 排期建议

| 阶段 | 内容 | 人日 | 前置 |
|---|---|---|---|
| S-1 Spike | AMap SDK 接入、相机/长按/准星、坐标往返、境外回退、审图号/隐私清单验证 | 1–2 | 客户提供测试 Key（仅开发用） |
| S-2 抽象 | `MapSurface` 抽取 + MapLibre 实现回归（功能零退化） | 2–3 | S-1 通过 |
| S-3 适配 | AMap 实现 + 搜索/逆地理适配 + 坐标层 + 设置开关 | 3–4 | S-2 合入 |
| S-4 收尾 | 双 provider 测试、合规文案、真机冒烟 | 2–3 | S-3 |
| **合计** | | **8–12** | |

**排期落位**：登记为 **v1.2 backlog**，v1.1 交付（12-20 起批次）不受影响。若要提前，可与 M6 打磨并行（两者文件冲突低，`:feature:map` 除外）。

## 8. 验收标准草案（GWT，供立项时细化）

1. **Given** 设置中未填高德 Key，**When** 进入地图，**Then** 使用开源底图且功能与现状完全一致（F-1.1~F-1.7 全过）。
2. **Given** 已填有效 Key 且点位于境内，**When** 切到高德模式，**Then** 底图/搜索/逆地理均由高德提供，信息条地名与水体类型正确，底图显示审图号与高德署名。
3. **Given** 高德模式，**When** 选点千岛湖/青岛/深圳，**Then** 提交 Open-Meteo 的坐标为 WGS-84（往返误差 ≤ 5 m），水体判定结果与开源模式一致（CHG-35 链不回归）。
4. **Given** 高德模式下选境外点（如 37.8,-122.4），**When** 确认选点，**Then** 自动回退开源底图并提示，数据链路正常。
5. **Given** 高德 Key 失效/配额耗尽，**When** 进入地图，**Then** 自动降级开源底图并给出可操作提示，不崩溃（N-4）。
6. **Given** 全仓扫描，**Then** 无内置高德 Key 字面量（§12 红线）。

## 9. 客户裁定（2026-11-10 已定案）

| ID | 问题 | 裁定结果 |
|---|---|---|
| D-AM1 | 发行形态 | **单 APK + 应用内开关**（不做独立分支；不做双 APK 变体） |
| D-AM2 | Key 使用方式 | **发行 BYOK**（用户在设置页填 Key）；开发测试 Key 仅放 `local.properties`，不入库、不封包 |
| D-AM3 | 发行性质 | **个人使用、非商用**（按高德个人开发者配额；商用授权不适用） |
| D-AM4 | 境外点策略 | **自动回退开源底图** + UI 提示 |
| D-AM5 | 排期落位 | **v1.2 backlog**（不触碰 v1.1 交付） |

**落账**：`REQUIREMENTS_REGISTER.md` **CHG-36（已裁定）** ↔ `OPT_REQUIREMENTS_V11.md` **§7 OPT-V12-05（D-AM）** ↔ 本文件。开工前置：客户提供**开发测试用**高德 Key（不入库）。

**个人非商用带来的两处调整**：
1. 配额按高德个人开发者上限（地图展示 / 搜索 / 逆地理分项独立计数）→ 设置页需展示配额与失败原因，逆地理缓存 30 d/点继续复用，避免无谓消耗；
2. 商用授权与法务评估项关闭，但**审图号与署名**仍必须展示（属地图展示合规，不因个人使用豁免）。

## 10. 原待裁定项（已由 §9 取代，保留追溯）

| ID | 问题 | 建议 | 结论 |
|---|---|---|---|
| D-AM1 | 发行形态：单 APK 开关 / 双变体 / 仅开源 | 单 APK 开关 | ✅ 采纳 |
| D-AM2 | Key 使用方式：发行 BYOK 还是改基线内置 | 开发测试 + 发行 BYOK | ✅ 采纳 |
| D-AM3 | 发行性质：个人 / 商用 | 客户确认 | 个人·非商用 |
| D-AM4 | 境外点策略：自动回退 / 不支持 | 自动回退 | ✅ 采纳 |
| D-AM5 | 是否本期启动 | v1.2 backlog | ✅ 采纳 |