# 钓鱼指数 App「潮点 TideSpot」需求规格说明书 v1.0

> ⚠️ **本文件为历史稿，已于 2026-11-02 全部合入 `TIDESPORT_REQUIREMENTS.md` v1.3（台账 CHG-05），不再维护。实现请以 v1.3 基线为准。** v1.3 相对本稿的差异：标题与变更历史、N-3 删省流量开关（CHG-03）、§6.2b 现场校潮 ≥2 条门槛（CHG-04）、N-6 来源清单补 RG-A/B/C、§5.8 RG-A 补 307 followRedirects 提示。

> Android · 纯客户端（无自有服务端）· 面向 vibe coding，可独立执行
>
> 本文档自包含：接口 URL 与字段路径、算法公式、单位、数据库结构、UI 状态机、验收标准、红线清单、黄金测试值均已写清。实现者无需再问"数据从哪来""这个数怎么算"。
> 标注 **[已验证]** = 本规格编写时实测请求成功；**[M0验证]** = 必须在里程碑 M0 用附录 A 探针确认后才可依赖。

---

## 0. 产品定义

在地图上任意点一下（或用当前定位），看到该点 **未来 7~10 天、逐小时** 的 5 个维度：**风浪、天气、钓鱼指数、水质、潮流**；可收藏该点随时回看。**没有服务端**，采集与计算全部在手机本地完成。

## 1. 目标用户与场景

| 角色 | 场景 | 关键诉求 |
|---|---|---|
| 淡水路亚 | 周末出发前查水库/湖泊 | 什么时候有口（逐小时）、水温、风能不能抛投 |
| 海钓（岸/矶/防波堤） | 看潮汐决定几点上哪个堤 | 涨落潮时段、潮流流速、浪高能否站人、安全红线 |
| 夜钓 | 工作日夜间两小时 | 夜间窗口评分、雷暴规避 |

## 2. 硬性约束（不可协商）

| # | 约束 | 实现含义 |
|---|---|---|
| C1 | 无自有服务端 | 禁止自架 API/代理/云函数；App 直连公开数据源 |
| C2 | 采集与加工全在客户端 | 评分、换算、插值、校正、缓存失效全部在 Kotlin 进程内 |
| C3 | 只用免费且允许程序化访问的源 | 见 §5 契约；§12 红线源**禁止出现在代码里** |
| C4 | 5 维度齐全 | 风浪 / 天气 / 钓鱼指数 / 水质 / 潮流 |
| C5 | 今天 → 未来 7~10 天，**逐小时** | 数据源单次返回 240 h [已验证]，UI 必须显示到小时 |
| C6 | 定位 + 自由选点 + 收藏 | 三条进入路径：My Location / 地图长按（准星）/ 收藏列表 |
| C7 | 离线可读 | 已加载数据落 Room，无网可回看（带时间戳与"陈旧"标记） |

## 3. v1 明确不做

不做账号/云同步/社交（收藏仅本机）；不做付费源默认接入（仅作可选开关）；不做定时任务与推送（仅进入界面或手动刷新时计算）；不做渔获记录/社区/水深等高线；不做 iOS（算法层写成纯 Kotlin 以备复用）。

---

## 4. 功能需求（含验收标准）

### F-1 定位与选点

| ID | 需求 | 验收标准（Given/When/Then） |
|---|---|---|
| F-1.1 | 首次进入请求定位权限；拒绝后进入"仅手动选点"模式 | 拒绝权限后冷启动：地图正常、顶部提示"未开启定位，可长按地图选点"，无权限弹窗死循环 |
| F-1.2 | 有 GMS 用 FusedLocationProvider；无 GMS 回退 LocationManager(GPS_PROVIDER) | 无 GMS 真机：点"我的位置"仍能定位，显示精度半径 |
| F-1.3 | 长按地图放置**十字准星**；拖动地图可重新对准；准星旁显示经纬度（6 位小数）+ **逆地理名称**（按 §5.8 降级链产出，非单接口承诺） | 长按后信息条显示 `29.600000, 119.038000 · 千岛湖（淳安县, 浙江）`（RG-C 命中时）；全链失败时显示 `… · 7PXXJ24Q+26` 或 `未命名水域`；**信息条不得空白、不得阻塞地图**；逆地理被关闭时显示坐标 + 本地 plusCode |
| F-1.4 | **准星是唯一选点真源**；拖动地图但未确认时不切换数据 | 已选点 A，拖到 B 未点"确定"：仍显示 A 数据，信息条显示"移动中，点确定切换" |
| F-1.5 | 关键词搜索选点（Open-Meteo Geocoding，中文可用 [已验证]） | 输入"万绿湖"→选结果→地图飞行并设准星 |
| F-1.6 | **水体类型判定**：海洋/近岸/内陆水体/陆地，据此决定各维度可用性与降级方式 | 准星在千岛湖：风浪 Tab 标题变"湖面风与水温"、潮流 Tab 标注"无潮汐"；准星在青岛防波堤：5 维全开。判定规则见 §6.9 |
| F-1.7 | 陆地点允许看天气与钓鱼指数（淡通用），但水质/潮流必须显式标注为估算或不可用 | 任一数值旁都有 ConfidenceBadge（§8.2） |

### F-2 收藏位置

| ID | 需求 | 验收标准 |
|---|---|---|
| F-2.1 | 任意准星位置可收藏；必填名称（默认逆地理结果、可改）；可选备注、场景预设（淡水路亚/海水岸边/淡水台钓）、物种、标签 | 收藏后列表出现该项，显示名称/坐标/标签 |
| F-2.2 | Room 持久化；重启不丢；100 条内不降速 | 收藏 30 条 → 杀进程重启 → 列表完整，进首屏 <300 ms（读缓存） |
| F-2.3 | 点收藏项进详情页（先渲染缓存，后台刷新） | 有 6 h 内缓存：立即出数据 + 右上转圈；刷新失败保留旧数据并显示"数据时间" |
| F-2.4 | 编辑 / 删除（二次确认）/ 排序（名称、最近查看、分数） | 离线状态下也能完成编辑与删除 |
| F-2.5 | 列表项显示最近一次"最佳窗口"摘要 | 副标题如 `明日 17:00 · 92 分 · 爆护窗口`；未算过则显示"未加载" |
| F-2.6 | 支持"全部收藏"聚合的 Top10 窗口（§6.6 步骤 6） | 跨点列表每项附点名称，按窗口价值降序 |

### F-3 五维度逐小时数据（核心）

| ID | 需求 | 验收标准 |
|---|---|---|
| F-3.1 | 详情页 5 Tab；顶部 **DaySelector**：今天 + 未来 1~9 天（可选范围 7 或 10 天，设置项控制） | 选"第 8 天"→ 曲线显示该日 00:00–23:00 共 24 点，横轴为小时 |
| F-3.2 | 每 Tab 必须同时有：①逐小时曲线/柱图 ②逐小时数值表（24 行可滚）③当日摘要（极值 + 时刻） | 风浪表格显示 `时:00  浪高 m / 周期 s / 方向 °` |
| F-3.3 | 显示值 = 源值（仅做单位换算与格式化），禁止额外平滑/取整到 0 | 源 `wave_height=0.42` → 显示 `0.4 m`（或 1.4 ft）；禁止 `0` 或无解释的 `—` |
| F-3.4 | 钓鱼指数 Tab：0–100 整数分 + 5 档色带 + **因子分解视图**（子分、权重、ΠV 明细、输入原值） | 点某小时展开：`溶氧 .95×0.21  气压 .79×0.14  Solunar .62×0.17 …`、`V=0.95（阵风 40 km/h）` |
| F-3.5 | **未来 7 天最佳窗口 Top10**（算法 §6.6）：本点视图 + 全部收藏聚合视图 | 含起始/峰值时刻、时长、峰值分、档位、Top2 主因、注意事项；无 ≥60 分时给兜底文案 |
| F-3.6 | **分维降级**：任一维度失败不得影响其他维度 | 海洋接口超时：天气/指数照常；风浪/潮流 Tab 显示"来源不可用 + 重试 + 最近缓存时间" |
| F-3.7 | 每 Tab 底部显示来源与更新时间（模型名、网格分辨率、UTC 更新时刻、本机拉取时刻） | 文案由 §5 契约生成，禁止硬编码错误来源 |
| F-3.8 | 时区：小时轴用**选点当地墙上时间**（请求带 `timezone=auto`）；内部存 epoch | 收藏点在大阪：标签为 JST，"今天"按 JST 判定；与上海点同时打开不串轴 |

### F-4 数据采集与加工

| ID | 需求 | 验收标准 |
|---|---|---|
| F-4.1 | 单次进入详情页 ≤ 4 个并行网络请求（天气 / 海洋 / 水质 / 可选校正参考），天文计算本地零请求 | 抓包核对 URL 集合与并发；无重复请求、无串行等待 |
| F-4.2 | 响应按 §5 解析入库；TTL 见 §7.3 | 缓存未过期进入 → 0 网络请求，显示"缓存于 HH:mm" |
| F-4.3 | 评分在 Dispatch.Default 后台线程；240 h × 全因子 < 40 ms（JVM 基准） | CI 性能阈值（§10.3） |
| F-4.4 | 内部单位统一 SI（m/s/°/hPa/mg·L⁻¹/km·h⁻¹/°C）；UI 层才转换 | 单测断言无隐式换算 |
| F-4.5 | 重试：1s/4s/10s 退避 3 次；区分 429 / 5xx / 网络错误；重试期保留旧数据 | 飞行模式测试通过、无 crash |
| F-4.6 | 所有请求带可识别 UA（App 名/版本），并遵守 §12 频控 | code review 检查点；设置页可查看 fetch_log |

---

## 5. 数据源契约（精确到 URL 与字段）

### 5.0 通用规则

- 全部为 HTTPS GET（除注明 POST 的可选参考），**无 API Key、无鉴权**。
- `latitude/longitude` 一律来自准星坐标（WGS-84）。
- 时间轴统一请求 `timezone=auto`，返回形如 `2026-11-02T17:00` 的当地时间（无偏移）。
- **null 处理**：该小时缺值时**禁止当 0**，UI 显示 `—` 并在评分侧按 §6.7 处理。
- 请求头统一 `User-Agent: TideSpot/{version} (Android; single-user device; manual tap only)`；**禁止复用桌面浏览器通用 UA**（RG-* 与 Overpass 要求自报应用身份，见 §5.8/§12）。
- **两条序列不可按下标对齐**：天气用 `past_days=7`、海洋用 `past_days=1`，起始时间不同；合并**必须按时间戳 join**（[已验证踩坑]）。

### 5.1 天气 — Open-Meteo Weather API [已验证]

```
GET https://api.open-meteo.com/v1/forecast
  ?latitude={lat}&longitude={lon}
  &hourly=temperature_2m,relative_humidity_2m,dew_point_2m,apparent_temperature,
          pressure_msl,surface_pressure,cloud_cover,cloud_cover_low,cloud_cover_mid,
          cloud_cover_high,visibility,precipitation,rain,showers,snowfall,weather_code,
          wind_speed_10m,wind_direction_10m,wind_gusts_10m,cape,freezing_level_height
  &daily=sunrise,sunset,temperature_2m_max,temperature_2m_min,precipitation_sum,
         wind_speed_10m_max,wind_gusts_10m_max,weather_code
  &past_days=7&forecast_days=10&timezone=auto
```

映射：`hourly.time[i]` ↔ 各数组同下标。单位 °C / % / hPa / mm / km/h / ° / m / WMO code。
**`past_days=7` 非可选**：气压 Δ3h、Δ24h 与"相对 7 日基线"（§6.2d）必须有过去数据。
高级设置可加 `&models=gfs_seamless|ecmwf_ifs025|icon_seamless`；默认不指定（Best Match）。

### 5.2 风浪 + 潮流 + 潮位 + SST — Open-Meteo Marine API [已验证：forecast_days=10 返回 240 小时]

```
GET https://marine-api.open-meteo.com/v1/marine
  ?latitude={lat}&longitude={lon}
  &hourly=wave_height,wave_period,wave_direction,wave_peak_period,
          wind_wave_height,wind_wave_period,wind_wave_direction,
          swell_wave_height,swell_wave_period,swell_wave_direction,
          secondary_swell_wave_height,tertiary_swell_wave_height,
          ocean_current_velocity,ocean_current_direction,
          sea_level_height_msl,sea_surface_temperature
  &past_days=1&forecast_days=10&timezone=auto
```

网格与精度（**必须在 UI 展示**）：波浪 ECMWF WAM 9 km/逐小时(15天)、GFS Wave 0.16°(~16 km)/逐小时(16天)、MFWAM 0.08°/3 小时；潮位与潮流 SMOC 0.08°(~8 km)/逐小时。
字段名陷阱：**潮位是 `sea_level_height_msl`**，写成 `sea_level_height_mssh` 返回 400 [已验证]。
**内陆点行为 [已验证]**：千岛湖 (29.60,119.04) 返回 HTTP 200 但**所有数组元素为 null** → App 必须以"全 null"判定无海况并走 §6.9 降级；**禁止**显示"浪高 0 m"。

### 5.3 水质 — 分层数据源（按点的类型选择）

| 层 | 适用点 | 来源 | 内容与粒度 | 授权 | 状态 |
|---|---|---|---|---|---|
| Q-海 | 海洋/近岸 | Copernicus Marine **Ocean Colour L3** `OCEANCOLOUR_GLO_BGC_L3_NRT_009_101` | 叶绿素a、Kd490 透明度、悬浮泥沙、漫衰减；OLCI **300 m/每日**；多传感器 4 km/每日 | 免费注册 | 产品目录 [已验证]；取数端点 **[M0验证]** |
| Q-湖 | 内陆水体 | Copernicus Marine **Lakes (GLAKES)** `GLACIO_ANALYSISFORECAST_PHY_012_002` | **湖泊水温（表层/垂向分层）333 m / 每日 / 含预报**；冰厚 | 免费注册 | 产品页 [已验证]；格点取数 **[M0验证]** |
| Q-湖备选 | 内陆水体 | NASA **GIBS WMTS** `VIIRS_SNPP_Land_Surface_Temp_Day` / `MODIS_Terra_Land_Surface_Temp_Day` | 陆面/表皮温度，1 km/每日，PNG 瓦片（需按调色板反解） | 公开、需署名 | 瓦片 [已验证 200 image/png]；**.gtx 反解路径本次 404 → 数值化 [M0验证]**，未通过则仅作视觉叠加层，不参与评分 |
| Q-境外 | 美国站点 | **USGS NWIS IV**（`parameterCd=00010 水温,00300 溶解氧,00011,pH=00400,浊度=807630`）；Water Quality Portal | 实测 **15 分钟** | 无 Key | [已验证 200] |
| Q-中国实测 | 中国河流湖库 | 国家水质自动综合监管平台 (cnemc) | 1600+ 国控断面逐小时水温/DO/pH/浊度 | — | **禁止接入**（§12 红线）：站点脚本明示"禁止非法获取（采集）数据"，且非浏览器客户端返回空 `tbody` [已验证] |
| Q-兜底 | 任何点 | 本地推导：§6.1 水温 + §6.2a DO + §6.2j 水质代理 | 逐小时 | 无 | 始终可用 |

**结论性设计**：水质 Tab 采用**来源分级显示**，每个数值挂徽标：`实测`/`卫星`/`模型`/`估算`。中国境内点在无合法实测/卫星时，水质 Tab 显示 4 项**模型估算**：水温、溶氧潜力 DO%、浊度代理、叶绿素代理 + 置信度。并保留"手动录入实测值"入口（`manual_observation`，参与 §6.1.3 校准）。

### 5.4 潮流 — 复用 §5.2 + 校正层（§6.2b）

- 直取：`ocean_current_velocity`(km/h)、`ocean_current_direction`(°)、`sea_level_height_msl`(m，含潮汐)。
- 派生：涨落率 `dh/dt`(m/h，潮位差分)、潮差 `amp`、平潮/停流判定。
- 校正参考序列（仅用**合法开放**源）：NOAA CO-OPS `datagetter` [已验证] —— `product=water_level&interval=6&datum=MSL&time_zone=gmt`；`product=predictions` 亦可用。**注意**：`hourly_height` 产品对部分站点返回 "No data was found"，`time_zone=UTC` 返回 400（只接受 `gmt|lst|lst_ldt`）[已验证]；站点 ID 需从官方站点表校验（本次 `9063021/8571891/9457130` 被判无效）。

### 5.5 钓鱼指数 — 无外部数据源，纯本地算法

输入全部来自 §5.1 / §5.2（+ 可选 §5.3 实测、§5.4 校正）。算法 §6；参照实现已在桌面跑通，并对**千岛湖 / 青岛 / 深圳 / 舟山**四点做了 96 小时序列实测校验（同目录 `FISHSCORE.md`、`fishscore.mjs`）。App **必须移植**该实现，不得另起炉灶。

### 5.6 天文

- 日出日落：§5.1 `daily.sunrise/sunset` [已验证]。
- 月龄/月中天/照明度：本地 Meeus 简化式（§6.2c），零请求；精度 ±20–30 min，UI 标"近似"。
- 注意：Open-Meteo 的 `/v1/astronomy` 本次实测返回 **404**（[已验证]），**不得依赖**。

### 5.7 可选增强（默认关闭；设置页开关；用户自备 Key）

| 增强 | 来源 | 关键事实 |
|---|---|---|
| 站点级潮高曲线/高低潮表 | 和风 `/v7/ocean/tide`（全球港口站点，未来10天，逐小时） | **海洋与台风组无免费额度**（0–100万次 ¥0.003/次 [已验证定价页]）；启用前必须弹计费告知 |
| 官方"钓鱼指数"对照 | 和风 `/v7/indices/1d?type=4` | 仅逐日 3 档；只能与本 App 指数**并排显示作参考**，不得作为唯一显示值 |
| 国内断面实测水质 | 青悦数据 API | ¥0.0005/条，费用与合规由用户承担 |

### 5.8 逆地理（坐标 → 名称）——补齐 F-1.3 的契约缺口

**事实前提 [已验证]**：Open-Meteo **没有逆地理端点**：`geocoding-api.open-meteo.com/v1/reverse?latitude=…&longitude=…` 返回 **HTTP 404 {"error":true,"reason":"Not Found"}**。因此 F-1.3 的"逆地理名称"必须由独立源承担，本节是其唯一契约。

**语义澄清（决定验收怎么写）**：钓鱼用户要的名称是"**这是哪片水**"（千岛湖 / 太湖 / 某某水库 / 某某湾），不是"**这是哪个 POI**"。免 Key 的行政区级逆地理**给不出湖库名**——实测 BigDataCloud 在千岛湖湖心只给到 `淳安县/杭州市/浙江省`；开阔海面给 `loc:"渤海"`/`"南海"`/`"菲律宾海"`（有海区名，无具体湖库名）。故定义**四级降级链**，RG-C 专责水体名。

| 级 | 源与端点 | 授权/限制 | 实测行为与产出 |
|---|---|---|---|
| **RG-A 主** | `GET https://api.bigdatacloud.net/data/reverse-geocode-client?latitude={lat}&longitude={lon}&localityLanguage=zh` | **免 Key、免注册，官方定位为 client-side 使用**；无频控响应头；`access-control-allow-origin: *`；实测 `x-response-time-ms: 1.5` | 稳定返回 `countryName / countryCode / principalSubdivision / city / locality / plusCode`。湖心点(29.55,118.95)→`中华人民共和国/CN/浙江省/杭州市/淳安县`，plusCode `7PXWHX22+22`；**开阔海面仍 200** 且 locality 为海区名，不会空 |
| **RG-B 次** | `GET https://photon.komoot.io/reverse?lat={lat}&lon={lon}&limit=1&layer=locality` | 免 Key；OSM 派生须署名（N-6）；**勿传 `lang=zh`**（实测 400：仅支持 default/de/en/fr；不传即返回 OSM 本地名，中国为中文） | 聚落/街区级细名（实测岸边→`骑龙巷街区[locality] 杭州市`）。**湖心/远海返回 200 + `features:[]`**（实测湖心、太湖、舟山海域、20N125E 皆空）→ 判空降级，**不得当错误重试** |
| **RG-C 增强（水体名）** | `POST https://overpass-api.de/api/interpreter`，body `data=<urlencoded QL>`；QL=`[out:json][timeout:15];(way(around:3000,{lat},{lon})["natural"="water"]["name"];way(around:3000,{lat},{lon})["waterway"="riverbank"]["name"];);out tags 2;` | 免 Key 但**服务器不稳**：本次连续查询主端点返回 **502**；镜像 `https://maps.mail.ru/osm/tools/overpass/api/interpreter` 同刻 200（命中 `荔湖<water>`、`洪湖<water>`）。须 ≤1 req/s、自报 UA、结果缓存 30 d | 唯一能给出"千岛湖/太湖"级湖库名的免费路径；命中则作为显示主标题 |
| **RG-D 兜底** | 本地 `open-location-code`（纯 JVM 库，无网络） | 离线可用 | `plusCode` + 格式化坐标；水体点显示"未命名水域" |

**降级链（实现为 `ReverseGeocoder.resolve(lat, lon) → NamedPlace`）**
```
1) 命中缓存 spot_reverse_cache（键=经纬度四舍五入到 0.005°，TTL 30 d）→ 直接返回，不打网络
2) 并行发 RG-A(超时 6s) 与 RG-B(超时 4s)；任一失败只跳过该级，不阻塞
3) RG-C 仅在【非远洋点 且 RG-A/RG-B 未给出水体名 且 该格点 30 d 内未查过】时发起：
   主端点失败试镜像一次；总预算 6 s，超时即放弃（水体名是锦上添花，绝不可拖慢选点响应）
4) 显示名优先级：RG-C 水体名 > RG-B 聚落名 > RG-A(locality|city) > RG-A(principalSubdivision) > RG-D plusCode
   后缀恒为 RG-A 的 市/省（有则附）；海域点显示 RG-A 的海区名（如"南海"）
5) 全链失败仍须有输出：落到 RG-D（纯本地计算，永不失败）→ 信息条绝不空白
```

**三个必须写进实现的实测坑**

1. **繁简混排**：RG-A 对中国点层级**简繁不一致** —— 实测 `浙江/杭州/淳安`（简）但 `山東省/青島市/嶗山區`（繁），太湖点 `江蘇省/苏州市/吴中区`（省繁市简）。显示前必须过一次 **T2S 归一**（内置常用字映射即可），或整体改用 `localityLanguage=en`（实测稳定：`Shandong Sheng / Qingdao / Shinan Qu`）。验收：同一界面不得混排简繁。
2. **行政区 ≠ 水体，且不得反推水体类型**：RG-A 在珠江口水面返回 `深圳市/南山区`、长江口返回 `上海市/崇明区`。§6.9 的水体类型判定**只看海洋字段是否非 null 与 elevation**，与逆地理结果无关；显示时由 `waterBodyType` 追加"（近岸水域）/（湖面）"类后缀。
3. **RG-B 空结果是正常态**：`200 + features:[]` 表示"此处无聚落"，不是失败。若按失败重试会打爆频控。判定：`features.isEmpty() → 视为无聚落并继续降级`。另：Photon 的 `layer` **只接受单值或已知组合**，实测 `layer=locality,district`、`layer=city,state,country` 均 **400**，仅 `layer=locality` 可用；允许参数为 `include,debug,dedupe,query_string_filter,lon,layer,limit,osm_tag,distance_sort,exclude,geometry,lang,radius,lat`。

**频控与合规（同时并入 §12）**

- 逆地理仅在**用户显式确认选点**时发起；拖动预览期间不发请求（与 F-1.4 一致）。每设备令牌桶 ≤ 1 req/2 s；同格点缓存 30 d。
- 三源均**免 Key、免注册**。RG-A 官方声明为免费客户端接口、不与个人身份关联；RG-B/RG-C 数据源自 OSM → N-6 署名清单必须含 OpenStreetMap contributors。
- **Nominatim 官方实例不进入契约**：本环境实测 `nominatim.openstreetmap.org/reverse` 为**连接层 fetch failed**（非 4xx，可能网络不可达），且其政策限 1 req/s、禁批量。仅允许用户在设置中自配**其自建实例 URL** 时启用，默认关闭且不带任何默认地址。
- 隐私连带修订见 N-5：逆地理会把选点坐标发给第三方，必须如实披露并可整体关闭。

### 5.9 正地理与逆地理的分工

搜索框 = **正地理**（Open-Meteo `GET /v1/search?name={q}&count=5&language=zh&format=json`，中文 [已验证]，返回 `results[i].{name,admin1,admin2,country,latitude,longitude,elevation,timezone}`，用 `admin2`+`admin1` 作副标题）。准星旁名称 = **逆地理**（§5.8）。两者不得互相顶替：正地理不接受坐标反查，§5.8 各源不做关键词检索。

---

## 6. 算法规格（可逐条编码；函数名对齐参照实现）

### 6.1 水温融合 Tw（先算，DO 与适温依赖它）

**6.1.1 海洋点**：`Tw = marine.sea_surface_temperature[t]`（逐小时）。全 null → 走 6.1.2。

**6.1.2 内陆点（无实测）**：气温滞后 + 衰减模型。

```
W = 30, K = 0.93
Tlag(h) = Σ_{k=0..W} airTemp(h-k)·K^k / Σ K^k
Tw(h)   = α·Tlag(h) + (1-α)·airTemp(h)          // α = 0.70（缺湖泊数据时）
// 若取到 Copernicus Lakes 日值 Tw_lake(d)（333 m, 每日, 含预报）：
Tw(h)   = Tw_lake(day(h)) + β·(airTemp(h) − dailyMean(airTemp(day(h))))   // β = 0.25
// 季节修正：α = 0.30 + 0.10·sin(2π·(doy−105)/365)  的等价形式（夏季表层响应更强）
```
依据：水热惯性使峰值水温滞后气温 2–6 h；已验证实测中该近似给出的 Tw 与 DO 处于合理区间（千岛湖 16–22 ℃、DO 8.8–10.0 mg/L）。

**6.1.3 用户实测覆盖**：若存有 `manual_observation(kind=waterTemp)`，在 ±6 h 内 `Tw = 0.6·Tw_meas + 0.4·Tw_model`，窗外保留模型值；徽标改 `实测+模型`。

### 6.2 子分（全部返回 [0,1]）

工具：`clip01(x)=max(0,min(1,x))`；`gauss(x,μ,σ)=exp(−0.5·((x−μ)/σ)²)`；
`trap(x,a,b,c,d)`：`x<=a || x>=d → 0; x<b → (x−a)/(b−a); x<=c → 1; else (d−x)/(d−c)`。

**(a) 溶氧潜力与 s_oxygen** [已验证数值正确]

```
Cs(Tw,S) = (468 / (31.6 + Tw)) · (1 − 0.0075·S)        // mg/L；S=盐度‰（淡水0，海水30）
pw(Tw)   = 6.1078 · exp(17.27·Tw / (Tw + 237.3))       // 饱和水汽压 hPa
DO_p     = Cs · (P − pw) / (1013.25 − pw)              // P = pressure_msl (hPa)
DO_ref   = clamp(5.2, 8.5, DO_base − 0.05·(Tw − 15))   // 暖水下调"满分门槛"
s_oxygen = clip01((DO_p − 4.0) / (DO_ref − 4.0))        // <4 mg/L → 0
```
必过单测（黄金值见附录 B1）：(10,1013.25,0)=11.25｜(25,1013.25,0)=8.27｜(25,990,0)=8.07｜(25,1013.25,30)=6.41，容差 ±0.05。

**(b) 潮汐 s_tide（海水专用，权重最高）与相位/幅度校正层**

```
dh(h)   = SL[h] − SL[h−1]                              // m/h（逐小时潮位差分）
amp     = max(SL[窗口]) − min(SL[窗口])                 // 预报窗口内潮差
s_tide  = 0.22 + 0.58·clip01(|dh|/0.16) + 0.20·clip01(amp/1.8)
if |dh| < 0.02: s_tide *= 0.72                          // 平潮/满潮"停流"
```

校正层（纯客户端自校准，**已用真实数据验证**）：

```
去趋势：dt(x) = x − OLS(x ~ t)         // 每窗口剔除线性漂移（消除基准面/风暴潮偏移）
lag*   = argmax_{L∈[−8,+8]} corr( shift(dt(M), L), dt(R) )
(gain, offset) = OLS( dt(R) ~ shift(dt(M), lag*) )
M'(h)  = gain · M(h − lag*) + offset
```

实测验证结果（模型潮位 vs NOAA 实测小时均值，22 天窗口）：

| 站点 | 对齐小时 | 潮差 | 原始 RMSE | r | 拟合 lag | gain | 校正后 RMSE | 半样本外测试（fit 60%/test 40%） |
|---|---|---|---|---|---|---|---|---|
| 9414290 圣弗朗西斯科 | 480 | 2.39 m | 0.167 m | 0.949 | **+1 h** | 0.97 | **0.077 m（−54%）** | test RMSE 0.074 vs 基线 0.201（**−63%**，lag/gain 在留出集仍成立） |
| 9435380 纽波特(俄勒冈) | 480 | 3.42 m | 0.113 m | 0.989 | 0 h | 0.99 | 0.111 m（本已很好） | 0.132 vs 0.132（无过拟合收益，正确退回） |

采纳规则：
1. 仅当 `r ≥ 0.6` 且 `|lag*| ≤ 5 h` 才启用校正，否则不校正并标注"未校正"。
2. `gain ∈ [0.7, 1.4]`，`offset ∈ ±0.5 m`（超限即拒绝采用）。
3. 对齐样本 < 150 h → 禁用校正层。
4. 参数**按点**存 `calibration` 表，永久有效，重新校准时覆盖。
5. 无合法参考序列的地区（含中国沿海）：校正层退化为**用户现场校潮**——记录"我看到涨/退潮的时刻"或高低潮时刻 1–3 个，拟合 `lag*/offset`（gain 固定 1.0）。**这是唯一合规路径，禁止抓取官方潮汐页。**

**(c) Solunar s_solunar（本地天文）**

```
age  = 月龄(天)：Meeus 简化（太阳平黄经 Ls + 方程中心差；月球 Lm,Mm 及主要摄动 → 黄经差 mod 360 × 29.530588861/360）
illum = (1 − cos(2π·age/29.530588861)) / 2
transit = 12 + (50.47/60)·age + (tzOffsetHours − lon/15)      // 月上中天钟表时；再对 24.8412 取模
d = ((h + 0.5 − transit) mod 12.4206 + 12.4206) mod 12.4206
maj = trap(min(d, 12.4206−d), −0.2, 0.5, 1.2, 2.1)
min = trap(|d − 6.2103|, 0.15, 0.7, 1.1, 1.7) × 0.75
s_solunar = clip01( max(maj, min) · (0.55 + 0.45·illum) + 0.05 )
```
必过单测：2026-11-09 → 月龄 ≈ **0.4 天**（该日为新月，[已验证算法输出]）；其余见附录 B2；容差 ±0.6 天。

**(d) 气压 s_pressure**

```
dP3  = P(h) − P(h−3) ; dP24 = P(h) − P(h−24) ; P7 = mean(P[h−168 .. h−1])（不足则用 P(h)）
trend = dP3 >= 0 ? 0.55 + 0.45·clip01(dP3/5) : 0.55 − 0.55·clip01(−dP3/6)
s_pressure = clip01( 0.45·trend + 0.32·gauss(P, 1020, 10) + 0.23·clip01(0.5 + dP24/12) )
```
**不得只用绝对气压阈值跨地区判定**（[已验证踩坑]）：高原点常年地表压低，必须靠 24 h/7 日相对项归一，否则系统性误判"别去"。

**(e) 风 s_wind**：`castable = v<=28 ? 1 : clip01(1−(v−28)/20)`；`gustPen = gust>45 ? 0.45 : gust>35 ? 0.8 : 1`；`s_wind = clip01((0.55·gauss(v,15,13) + 0.45·castable)·gustPen)`。

**(f) 天气现象 s_weather**（WMO 基值 + 小雨/云修正）

```
base = {0:1.00,1:.97,2:.92,3:.86,45:.55,48:.45,51:.82,53:.70,55:.50,61:.62,63:.44,65:.22,
        80:.64,81:.46,82:.28,85:.25,86:.20,95:0,96:0,99:0}[code]  默认 0.70
lightRain = code ∈ [51..65] ∪ [80..82]
bonus = lightRain ? (rain<0.6 ? +0.12 : rain<2 ? +0.04 : −0.12) : 0
s_weather = clip01(base + bonus + 0.12·gauss(cloud, 55, 45))
```

**(g) 时段 s_daypart**：`sr/ss` 为日出日落小时；`cp = max(gauss(h,sr+0.4,1.1), gauss(h,ss−0.3,1.1))`；正午 `0.30+0.20·(1−cp)`，其余 `0.30+0.60·cp`；夜间 `nightTarget ? 0.55+0.25·cp : 0.28`；最后 `clip01(v + 0.20·cp)`。

**(h) 水温适温 s_tempmatch** = `clip01(gauss(Tw, T_opt, σ_T) + 0.04)`（物种库 §6.8）。

**(i) 人体感 s_comfort** = `clip01(gauss(apparent_temperature, 22, 11)·0.9 + 0.10)`。

**(j) 水质代理 s_water**（参与水质 Tab，可选计入指数）

```
turbidityIdx = clip01(0.5 + 0.25·gauss(cloud,60,40) − 0.05·rain24h)      // 强降雨→浑水
chlIdx       = 有 CHL 时 clip01((CHL−0.05)/0.35)，否则由水温+光照推导（标"估算"）
s_water      = 0.55·s_oxygen + 0.25·turbidityIdx + 0.20·chlIdx
```

### 6.3 合成

```
Score = 100 · clip01( Σ_k w_k·s_k / Σ_k w_k ) · Π_i V_i
```
档位与色值：`≥80 爆护窗口 #1B5E20`｜`65–79 很适合 #43A047`｜`50–64 可以 #FDD835(深灰字)`｜`35–49 较差 #FB8C00`｜`<35 别去 #E53935`。
内部保留浮点用于排序，显示才取整（避免并列时排序不稳定）。

### 6.4 权重（场景预设；JSON 资源文件，用户可改，改动即本地重算）

| 因子 | 淡水·路亚 | 海水·岸边/矶钓 | 淡水·台钓 |
|---|---|---|---|
| oxygen | 0.21 | 0.14 | 0.22 |
| pressure | 0.14 | 0.11 | 0.13 |
| solunar | 0.17 | 0.13 | 0.16 |
| tide | 0（不启用） | **0.24** | 0 |
| wind | 0.10 | 0.11 | 0.10 |
| weather | 0.10 | 0.09 | 0.11 |
| daypart | 0.08 | 0.06 | 0.10 |
| tempmatch | 0.14 | 0.06 | 0.12 |
| comfort | 0.06 | 0.06 | 0.06 |
| 参数 | S=0, night=false, 22℃/σ7, DO_base 8.0 | S=30, night=true, 20℃/σ6, DO_base 7.4 | S=0, night=false, 18℃/σ9, DO_base 7.6 |

规则：**因子为 null 时从分母剔除**（不是给 0 分）。

### 6.5 安全/可作钓性衰减 ΠV（连续 sigmoid，禁止硬阈值）

```
sig(x, mid, k) = 1 / (1 + exp(k·(x − mid)))        // x 越大越不利
V = 1
if WMOcode >= 95: V *= 0.05                         // 雷暴：唯一硬否决（安全）
V *= max(0.12, sig(windSpeed, 34, 0.30))
V *= max(0.10, sig(gust, 45, 0.18))
V *= max(0.40, 1 − clip01((−dP3 − 2.5)/3) · 0.55)   // ΔP3 起点 −2.5 hPa/3h
V *= max(0.20, 1 − clip01((rain − 2)/8) · 0.70)
if wave != null: V *= max(0.08, sig(wave, 1.6, 1.8)) // 内陆点跳过
V *= max(0.18, 1 − clip01((4.6 − DO_p)/1.6) · 0.75)
if V < 0.75: vetoReason = 拼接触发项（风速/阵风/ΔP3/雨强/浪高/DO）→ 存库并显示
```

**必须遵守的三条修正经验**（本规格编写时逐一踩过并修好）：
1. 不得用硬阈值乘子（如"阵风>55 → ×0.20"）：实测在 54.7→55.0 km/h 出现 **50 分→12 分** 的荒谬跳变。
2. ΔP3 惩罚起点必须 ≥ −2.5 hPa/3h：实测正常午后日变化为 −3.0 hPa/3h，旧规则把 71 分误杀成 19 分。
3. "无否决原因"必须存 **null**，不能存空列表/空串：空集合曾被判真导致 Top10 全部为空（UI 判定用 `reason == null`）。

### 6.6 未来 7 天最佳窗口 Top10

输入：该点（或全部收藏点）逐小时 Score，≤240 h，从"当前小时的下一整点"起 N 天（N=7 或 10，用户设置）。

```
1) 候选集 C = { h | Score[h] >= 60 且 未触发雷暴否决 }
2) 合并窗口：按时间升序，把相邻且间隔 <= 1 h 的小时并为 Window
   W.start/W.end = 首/尾；W.peak = max Score；W.peakHour = argmax；W.len = end−start+1；W.mean = mean(Score)
3) 窗口价值 rank(W) = 0.62·W.peak + 0.23·W.mean + 1.4·min(W.len, 4)
   （兼顾长度与强度，避免只挑 1 小时尖峰）
4) 去重：若两窗中点差 < 2.5 h 且同属一个潮汐周期（由 dh 符号连续段定义），仅保留 rank 高者；内陆点跳过本步
5) 取 rank 降序前 10，输出：起始/峰值时刻、时长、峰值分、档位、主因（Σw_k·s_k 贡献最高的 2 项）、注意事项（vetoReason / 停流 / 阵风>40 各一句）
6) 跨收藏点模式：各点独立跑 1–5，再按 rank 全局排序取 10，每项附点名称
```
边界（必须写测试）：全部 <60 → 返回"未来 7 天无 ≥60 分窗口，最高 X 分（时刻…）"；有效数据 <24 h → 禁用并提示；含否决的小时仍可入选，但"注意事项"必须显示。

### 6.7 缺失值与陈旧策略

| 情形 | 处理 |
|---|---|
| 某因子该小时 null | 从分母剔除；剔除后有效权重 < 0.6 → 显示分数 + "低置信"徽标 |
| 整维度 null（内陆的海洋字段） | Tab 显示"本点无该来源数据" + 已启用的降级说明 |
| 缺 past 数据算不了 ΔP3 | 取 `dP3=0` 且权重 0.14→0.08，标注"趋势数据不足" |
| 缓存超 TTL 且无网 | 可显示；顶部黄条"数据时间 X，已过期"；>48 h 视为不可用（不再据此评分） |

### 6.8 物种预设库（v1 内置 JSON）

```
[{"id":"largemouth_bass","name":"大口黑鲈","group":"fresh_lure","Tw_opt":22,"sigmaT":7,"DO_base":8.0,"night":false},
 {"id":"mandarin_fish","name":"鳜鱼","group":"fresh_lure","Tw_opt":21,"sigmaT":6,"DO_base":7.8,"night":true},
 {"id":"topmouth_culter","name":"翘嘴","group":"fresh_lure","Tw_opt":20,"sigmaT":8,"DO_base":8.2,"night":true},
 {"id":"snakehead","name":"黑鱼","group":"fresh_lure","Tw_opt":26,"sigmaT":5,"DO_base":7.0,"night":false},
 {"id":"crucian","name":"鲫鱼(台钓)","group":"fresh_bait","Tw_opt":18,"sigmaT":9,"DO_base":7.6,"night":false},
 {"id":"sea_bass","name":"海鲈","group":"sea_shore","Tw_opt":20,"sigmaT":6,"DO_base":7.4,"night":true},
 {"id":"black_porgy","name":"黑鲷","group":"sea_shore","Tw_opt":21,"sigmaT":5,"DO_base":7.2,"night":true},
 {"id":"rockfish","name":"许氏平鲉","group":"sea_shore","Tw_opt":15,"sigmaT":5,"DO_base":8.0,"night":false}]
```
选物种 = 覆盖 `Tw_opt/σ_T/DO_base/night`；权重仍按 group。

### 6.9 水体类型判定（决定可用维度）

```
优先级（全本地/免费）：
1) 海洋任一字段非 null → "海洋/近岸"
2) 否则 elevation（Open-Meteo 响应字段，[已验证]）> 3 m 且海洋全 null → "内陆/陆地"
3) 区分"内陆水体(湖/库)"与"陆地"：优先看 Copernicus Lakes 该格点是否有有效水温值（有 → 内陆水体）；
   否则查 OSM Overpass（5 km 半径内 natural=water / waterway=reservoir）[M0验证：本次 GET 返回 406、镜像 504，
   需改 POST 表单 + 备用镜像 + 结果缓存 30 天/点]
4) 全失败 → "未知"，按"内陆/陆地"降级并标注
```
UI 影响：内陆点风浪 Tab 更名"湖面风与水温"、潮流 Tab 更名"水位（无潮汐）"，**不隐藏 Tab**（保持 5 Tab 结构稳定）。
注意：模型 elevation 在近岸可能取到陆地格点（本次深圳点被读成 285 m [已验证]），故 elevation 只作辅助判据，海洋字段是否非 null 才是主判据。

---

## 7. 客户端架构

### 7.1 技术栈（写死，不自由发挥）

| 层 | 选择 | 备注 |
|---|---|---|
| 语言/UI | Kotlin 2.x + Jetpack Compose（Material 3） | 禁止 XML 布局混写 |
| 地图 | MapLibre Android v11 + 免费矢量样式（CARTO Positron `https://basemaps.cartocdn.com/gl/positron-gl-style/style.json` [已验证 200]） | 必须保留底图 attribution；不得内置他人 Key 的高德/天地图样式（用户可自配） |
| 网络 | Ktor Client（OkHttp engine）+ kotlinx.serialization | URL 只允许在 `core:network` 的一个构造器里拼接 |
| 存储 | Room（SQLite），DAO 全 suspend | |
| DI | Hilt | |
| 图表 | Compose Canvas 自绘 | 需能画 null 断点，不引第三方图表库 |
| 后台 | WorkManager（仅"收藏点预取"，默认关） | v1 不做定时推送 |
| SDK | minSdk 26 / targetSdk 35 | |
| 权限 | ACCESS_COARSE/FINE_LOCATION + INTERNET | 不得申请多余权限 |

### 7.2 模块分层

```
:app
:core:domain    纯 Kotlin，无 Android 依赖：FishScoreCalculator / TideCalibrator / DoPhysics / Solunar / WaterProxy / WindowFinder
:core:data      Repository + DTO + Room + Mapper
:core:network   Ktor 模块、URL 构造器、重试与令牌桶限流（含 RG-A/B/C 令牌桶 ≤1 req/2 s 与 6 s 预算）
:core:ui        HourChart / DaySelector / FactorBreakdown / ConfidenceBadge / Band
:feature:map    地图 + 准星 + 逆地理（调 §5.8 链，产出 NamedPlace）+ 水体判定 + 定位
:feature:favorites
:feature:detail 5 Tab + Top10
:feature:settings
```
**硬要求**：`:core:domain` 必须能在 JVM 单测中直接运行，用附录 B 黄金值断言 → 算法可脱离手机独立验证（vibe coding 友好）。

### 7.3 数据模型与 TTL

| 数据集 | TTL |
|---|---|
| 天气（小时/日） | 3 h |
| 海洋（浪/流/潮/SST） | 6 h |
| 水质（卫星/模型） | 24 h |
| 底图样式/字体 | 30 d |
| 水体判定结果 | 30 d / 点 |
| 潮位校正参数 lag/gain/offset | 永久（点级，重新校准时覆盖） |

```sql
spot(id, lat, lon, name, elevation, waterBodyType, tzOffsetSec,
     favorite, note, groupPreset, speciesId, lastViewedAt, updatedAt)
hourly_metric(spotId, hourEpoch, hourLocal, kind, field, value REAL NULL, source, fetchedAt)
  PK(spotId, hourEpoch, field)
hourly_score(spotId, hourEpoch, hourLocal, score REAL, band, vetoReason NULL,
             factorsJson, algoVersion)
  PK(spotId, hourEpoch, algoVersion)
calibration(spotId, refStation, lagHours, gain, offset, r, sampleHours, method, updatedAt)
manual_observation(spotId, hourEpoch, kind /*waterTemp|do|tideHigh|tideLow*/, value, note, createdAt)
fetch_log(spotId, source, urlHash, httpStatus, bytes, costMs, fetchedAt)
spot_reverse_cache(latKey, lonKey, waterBodyName, localityName, adminJson, plusCode, source, resolvedAt)
  PK(latKey, lonKey)   /* 键=坐标四舍五入到 0.005°；TTL 30 d，§5.8 步 1 */
```

## 8. UI 规格

### 8.1 屏幕与导航

```
MapScreen(首页)
 ├─ 顶部浮层：定位按钮 / 搜索框(§5.9 正地理) / 收藏入口 / 设置
 ├─ 长按或拖动 → 准星 → 底部信息条（经纬度·地名·水体类型·[确定]·[收藏]）
 ├─ 右侧抽屉：收藏列表 → DetailScreen(spot)
 └─ 底部：Top10 入口（本点 / 全部收藏）
DetailScreen(spot)
 ├─ Tab: 风浪 | 天气 | 钓鱼指数 | 水质 | 潮流
 ├─ DaySelector（今天 … +9，范围由设置 7/10 决定）
 ├─ HourChart ⇄ 数值表（可切换，默认图表）
 ├─ 点小时 → FactorBreakdown（指数 Tab）/ 字段明细（其他 Tab）
 ├─ 潮 Tab 额外：潮高曲线 + dh/dt 副轴 + 高低潮标记 + 涨/退箭头 + [校准] 入口
 └─ Sheet：数据源与免责
```

### 8.2 关键组件

1. **DaySelector**：横向 Chip（`今天 11-02`、`+1`…`+9`）；右侧显示"源更新 HH:mm · 本机拉取 HH:mm"。
2. **HourChart**：24 点；**null 断线不连线**（禁止画成 0）；y 轴自适应带单位；点选小时浮层；指数 Tab 用柱 + 5 档色带，≥80 段叠"窗口"横条。
3. **FactorBreakdown**：子分行（名称/权重/得分/贡献条）+ `ΠV` 明细（触发了哪些、乘子值）+ 原始输入（Tw、DO_p、P、ΔP3、ΔP24、风/阵风、雨、云、WMO、浪、潮位、dh/dt、流速、月龄、照明、中天）。
4. **ConfidenceBadge**：`实测` `卫星` `模型` `估算` `未校正` `低置信`（六色，且带文字，不靠颜色单独编码）。
5. **校准面板**：显示校正状态（lag/gain/offset/r/样本数/参考源）；无参考源时提供"记录一次涨潮时刻"的现场校潮流程（选时间 + 类型 → 落 `manual_observation` → 重算 lag/offset）。
6. 浅色/深色跟随系统；色带在深色下对比度 ≥ 4.5:1。

## 9. 非功能需求

| ID | 要求 |
|---|---|
| N-1 | 冷启动到可交互 < 1.5 s；地图 60 fps；准星拖动无掉帧 |
| N-2 | 240 h 全维 + 5 Tab 一次计算入库，Tab 切换不重算；内存峰值 < 180 MB |
| N-3 | 单次进详情页流量 < 1.2 MB（gzip 后）；"省流量"开关（仅请求所选日） |
| N-4 | 崩溃率 < 0.2%；任何源不可用都不得 crash（强制异常用例） |
| N-5 | 隐私：App 自身不建用户档案、无埋点 SDK（若加须默认关且可完全移除）。**但选点坐标会离开设备**——§5.1–§5.9 的每次请求都把经纬度发给第三方（Open-Meteo / Copernicus / NASA / NOAA / USGS / BigDataCloud / Photon / Overpass）。设置页须逐源列出"本机将向以下服务发送你选点的经纬度"，并提供**逆地理总开关**（关闭后 F-1.3 只显示坐标 + 本地 plusCode，其余功能不受损）。隐私政策不得声明"零数据传输" |
| N-6 | 合规：设置页"数据来源与许可"清单（Open-Meteo、Copernicus Marine/EU、NASA GIBS、NOAA、USGS、OSM/CARTO）+ 免责文案（§9.6）+ 频控可见（fetch_log） |
| N-7 | 无障碍：每张图有等价数值表（TalkBack 可读）；档位同时有文字 |
| N-8 | i18n：中（默认）/ 英；日期与单位本地化 |
| N-9 | 时区：小时轴 = 选点墙上时间；比较/排序用 epoch |
| N-10 | 算法版本化：`algoVersion` 入库，升级后可整体重算（后台一次性） |

### 9.6 必须出现的免责（原文照用）

- 风浪/潮流：「潮位与潮流来自 ~8 km 全球模型，官方声明不适用于近岸航行；本 App 不替代航路指南与官方潮汐表。」
- 安全：「雷暴、大风、大浪判定仅供参考。水上安全以当地预警为准，切勿在雷电、台风、大潮汛期间出钓。」
- 水质：「中国境内多数点位水质为**模型估算值**，非实测，不作为用水、饮用或接触安全依据。」
- 钓鱼指数：「指数基于气象与天文因子的经验加权估算，不构成渔获保证。」
- 潮汐校正：「校正仅在实测参考可用时生效；未校正点位显示"未校正"。」

## 10. 测试与验收

### 10.1 单元测试（必须实现，用附录 B 黄金值）

1. `DoPhysicsTest`：4 组已验证数值，容差 ±0.05 mg/L。
2. `SolunarTest`：6 个日期的月龄（含 2026-11-09 ≈ 0.4 天），容差 ±0.6 天；照明度单调性。
3. `TideRateTest`：给定潮位序列断言 dh/dt 符号与"停流"判定。
4. `VetoContinuityTest`（**关键回归**）：阵风 44.9/45.0/45.1/54.7/55.0/55.1 km/h 序列 → 分数单调不升且**相邻差 < 12 分**。
5. `PressureTrendTest`：ΔP3 = −2.4（日变化）不触发重罚；ΔP3 = −5 显著衰减。
6. `NullHandlingTest`：内陆点海洋全 null → 不产生 `wave=0`；`vetoReason` 空时为 null（不是空串/空表）。
7. `TideCalibratorTest`：**必须复现 §6.2b 实测结论** —— 用附录 B7 录制的旧金山序列，断言 `lag*=+1h`、`gain∈[0.9,1.05]`、校正后 RMSE ≤ 0.10 m；半样本外测试 RMSE 优于基线 ≥ 40%；并断言"纽波特（本已很好）"不被错误改动（|lag|≤0、RMSE 变化 < 5%）。
8. `WindowFinderTest`：合成 240 h 序列，断言合并、rank 公式、同周期去重、无 ≥60 兜底、跨点聚合。
9. `ReverseGeocoderTest`（对应 §5.8）：用 MockEngine 覆盖 5 条链 —— ①RG-C 命中→主标题为水体名；②RG-C 502 且镜像 200→仍拿到水体名；③RG-A+RG-B 正常、RG-C 双端点失败→退到聚落名/行政区名；④RG-B 返回 `features:[]`→判为"无聚落"且**不重试**（断言该源调用次数=1）；⑤全链失败→落 RG-D plusCode，信息条非空。另断言缓存命中时零网络请求。
10. `T2SNormalizeTest`（§5.8 坑 1）：`山東省/青島市/嶗山區`→`山东省/青岛市/崂山区`；`江蘇省/苏州市/吴中区`→`江苏省/…`；同一结果内不得混排。
11. `WaterBodyTypeIsolationTest`（§5.8 坑 2）：给定"RG-A 返回 深圳市/南山区 但海洋字段非 null"，断言 `waterBodyType=sea` 且显示名追加"（近岸水域）"——逆地理结果**不参与**类型判定。
12. `TzTest`：跨时区点的"今天"边界（当地 23:30 时请求日期正确）。
13. `CacheTest`：TTL 命中 0 请求；无网显示陈旧标记。

### 10.2 集成 / UI 测试

- Ktor **MockEngine** + 附录 B 录制的真实 JSON，覆盖 6 状态：正常 / 海洋全 null / 超时 / 429 / 部分字段缺失 / 陈旧缓存无网。
- Compose UI：DaySelector 切 +9 出 24 行；FactorBreakdown 展开含权重；徽标文案；收藏 CRUD 与 Room 迁移。
- 真机手测清单：无 GMS 设备定位；飞行模式回看；跨时区双点同开；中国内陆点 5 Tab 完整性；深色对比度；校准面板现场校潮流程。

### 10.3 性能基准（CI 阈值）

- `FishScoreCalculator`：240 h × 9 因子 × 3 预设 < 40 ms（JVM）；中档机折算 < 120 ms。
- `TideCalibrator`（480 点滑窗 xcorr，±8 lag）：< 25 ms。
- 详情页首屏（有缓存）< 300 ms。

## 11. 里程碑（每个可独立交付）

| M | 内容 | 退出标准 |
|---|---|---|
| **M0 数据源 Spike** | 跑附录 A 全部探针；确认 Copernicus Lakes/OceanColour 取数路径与注册；确认 **§5.8 逆地理三源（RG-A/B/C）与 Overpass 镜像的可用形式**；确认底图样式授权 | `DATA_SOURCE_PROBE.md` 逐条 PASS/FAIL + FAIL 时的降级决定（本文档已给兜底链） |
| **M1 算法核** | `:core:domain` 实现 §6 + §10.1 单测 | 单测全绿；与参照实现 `fishscore.mjs` 对同输入逐小时差 ≤ 2 分；VetoContinuity/TideCalibrator 通过 |
| **M2 网络+存储** | §5 契约、Room、TTL、分维降级 | Mock 6 状态全过；无 null→0；fetch_log 可查 |
| **M3 地图与选点** | MapLibre + 准星 + 逆地理 + 水体判定 + 定位双通道 | 四条进入路径可用；判定与 §6.9 一致并缓存 |
| **M4 详情 5 Tab** | HourChart + 数值表 + DaySelector + FactorBreakdown + 徽标 + 免责 | 五维显示满 240 h；空值断线；TalkBack 可读 |
| **M5 收藏 + Top10** | CRUD、Top10Screen、跨点聚合、潮位校准面板 | 重启不丢；Top10 含主因与注意事项；边界用例过 |
| **M6 打磨** | 设置（单位/天数/物种/权重/可选 Key/预取）、i18n、深色、性能、隐私页 | §9 全项达标，CI 无回归 |

## 12. 红线清单（合规/稳定性，须代码层硬防御）

| 源 | 红线 | 代码防御 |
|---|---|---|
| 国家水质自动综合监管平台 (cnemc) | 站点脚本明示「**水质监测数据仅供本网址查看，禁止非法获取（采集）数据**」；非浏览器客户端返回空 `tbody` [已验证]；并有 devtools 反调试 | **不得出现在代码里**。实测水质走用户手录或用户自备 Key |
| NMEFC / 中国海洋预报网 (oceanguide、nmefc) | 无公开 API；接口猜测请求返回 **418 访问被拦截（CloudWAF）** [已验证] | **禁止抓取**；官方潮位引导用户自查，或走用户自备的和风海洋 |
| 和风天气 | 免费 5 万次/月**仅天气与基础服务组**；海洋/台风组无免费额度（¥0.003/次起）；要求注明来源 | 海洋增强默认关 + 启用弹计费告知 + UI 强制标注来源 |
| Open-Meteo | 免费档限**非商用 & <1 万次/日** | 令牌桶 6 req/min + 单点最小刷新 15 min + fetch_log 可视化；商用需自购授权 |
| OSM/Overpass/瓦片 | 有使用政策，禁高频 | 判定结果缓存 30 d/点；仅显式选点时查；底图用 CARTO/OpenFreeMap 矢量样式而非 OSM 官方瓦片 |
| Copernicus / NASA GIBS / NOAA / USGS | 免费注册或公开，但**需署名** | 设置页许可清单必须逐条列出 |
| BigDataCloud (RG-A) | 免费但**为客户端单用户使用**设计；禁转售/禁服务端批量调用；无 SLA | 仅在用户显式确认选点时调用；令牌桶 ≤1 req/2 s；结果缓存 30 d；**不得改成轮询**；失败不重试超过 1 次 |
| Photon (RG-B) | OSM 派生，须署名；公共实例容量有限 | 同上频控；**`features:[]` 不得重试**（见 §5.8 坑 3）；禁止传 `lang=zh`（400） |
| Overpass (RG-C) | 公共实例容量有限，本次实测主端点**连续 502**；有明确使用政策 | ≤1 req/s、自报 UA、30 d/格点缓存、总预算 6 s、失败即放弃（水体名非必需）；镜像仅备一次；**不得用于批量遍历** |
| Nominatim 官方实例 | 政策限 1 req/s、禁批量；本环境实测**连接层 fetch failed** | **不进契约**；只允许用户自配自建实例 URL，默认关闭、无默认值 |
| 天地图 / 高德样式 | 需 Key 与授权 | 只允许用户自备 Key；**禁止内置** |

## 13. 交付验收清单

- [ ] 仓库内无任何自架服务；所有请求域名都在 §5 清单内
- [ ] 5 维 × 7/10 天 × 24 h 全部可显示，值与源一致
- [ ] 定位 / 长按 / 拖动准星 / 搜索 / 收藏 5 条路径都出数据
- [ ] 收藏：命名、备注、预设、物种、删除、排序，重启不丢
- [ ] 钓鱼指数有因子分解与衰减原因；VetoContinuityTest 通过
- [ ] Top10 按 §6.6 实现且含边界兜底
- [ ] 内陆点不显示假海况（无"浪高 0 m"）
- [ ] 潮位校正层：有参考源时生效且 RMSE 下降可量化；无参考源时走现场校潮且标注"未校正"
- [ ] 断网可回看缓存并显示陈旧标记
- [ ] 逆地理四级降级链（§5.8）5 条 Mock 用例全过；信息条永不空白；简繁不混排；逆地理总开关生效且关闭后其余功能不受损；设置页逐源披露"向第三方发送选点坐标"
- [ ] 免责与来源清单齐全；§12 红线源零出现
- [ ] §9 NFR 全项实测达标

---

## 附录 A：M0 探针命令（逐条执行并记录状态码）

```bash
# A1 天气 408 小时
curl 'https://api.open-meteo.com/v1/forecast?latitude=29.6&longitude=119.04&hourly=pressure_msl,temperature_2m&past_days=7&forecast_days=10&timezone=auto' | jq '.hourly.time|length'   # 期望 408
# A2 海洋 240 小时 + 内陆 null
curl 'https://marine-api.open-meteo.com/v1/marine?latitude=36.06&longitude=120.38&hourly=wave_height,ocean_current_velocity,sea_level_height_msl,sea_surface_temperature&forecast_days=10&timezone=auto' | jq '.hourly.wave_height|length'  # 240 [已验证]
curl 'https://marine-api.open-meteo.com/v1/marine?latitude=29.6&longitude=119.04&hourly=wave_height&forecast_days=1&timezone=auto' | jq '.hourly.wave_height[:5]'   # 期望全 null [已验证]
# A3 正地理（搜索框，§5.9）；注意 OM 无 reverse，见 A5b–A5e
curl 'https://geocoding-api.open-meteo.com/v1/search?name=%E5%8D%83%E5%B2%9B%E6%B9%96&count=3&language=zh&format=json' | jq '.results[0].name'   # [已验证]
# A4 NOAA 潮位（校正参考，境外）—— 注意 interval=6、time_zone=gmt
curl -sG 'https://api.tidesandcurrents.noaa.gov/api/prod/datagetter' --data-urlencode begin_date=20261010 --data-urlencode end_date=20261030 --data-urlencode product=water_level --data-urlencode datum=MSL --data-urlencode station=9414290 --data-urlencode time_zone=gmt --data-urlencode units=metric --data-urlencode interval=6 --data-urlencode format=json | jq '.data|length'   # 4800 [已验证]
# A5 USGS 水质（境外）
curl 'https://waterservices.usgs.gov/nwis/iv/?format=json&sites=01646500&parameterCd=00010,00300,00011&period=P1D' | jq '.value | length'   # [已验证 200]
# A5b 逆地理 RG-A（免 Key，客户端用；实测 200，湖心也有行政区 + plusCode）
curl -s 'https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=29.55&longitude=118.95&localityLanguage=zh' | jq '{countryName,principalSubdivision,city,locality,plusCode}'
# A5c 逆地理 RG-B（勿传 lang=zh；水面点应返回 200 + features:[] 而非错误）
curl -s 'https://photon.komoot.io/reverse?lat=29.605&lon=119.038&limit=1&layer=locality' | jq '.features[0].properties | {name,type,city,state,country}'
curl -s -o /dev/null -w '%{http_code}\n' 'https://photon.komoot.io/reverse?lat=29.55&lon=118.95&limit=1&layer=locality&lang=zh'   # 期望 400（证明必须去掉 lang）
# A5d 逆地理 RG-C 水体名（主端点本次 502 → 必须验证镜像；≤1 req/s）
curl -s -X POST 'https://overpass-api.de/api/interpreter' --data-urlencode 'data=[out:json][timeout:15];(way(around:3000,29.55,118.95)["natural"="water"]["name"];);out tags 2;' | jq '.elements|length'
curl -s -X POST 'https://maps.mail.ru/osm/tools/overpass/api/interpreter' --data-urlencode 'data=[out:json][timeout:15];(way(around:3000,29.55,118.95)["natural"="water"]["name"];);out tags 2;' | jq '.elements[0].tags.name'   # 本次返回「荔湖」级小水体名
# A5e Nominatim 官方实例（本环境实测连接失败；确认默认关闭策略）
curl -sS -o /dev/null -w '%{http_code}\n' -A 'TideSpot/1.0 (test)' 'https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=29.605&lon=119.038'
# A6 Copernicus Lakes 湖泊水温（内陆）：确认 GLACIO_ANALYSISFORECAST_PHY_012_002 的 token 取数与预报天数 [M0验证]
# A7 Copernicus OceanColour L3 OLCI 300m 叶绿素/透明度取数 [M0验证]
# A8 GIBS 瓦片（陆面温度，仅作叠加层候选）
curl -o t.png 'https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/VIIRS_SNPP_Land_Surface_Temp_Day/default/2026-10-30/GoogleMapsCompatible_Level7/4/12/6.png'   # [已验证 200 png]；.gtx 反解 [M0验证]
# A9 水体类型判定的 Overpass 查询：与 A5d 同一端点/同一表单写法（GET 实测 406，POST 表单可用但主端点偶发 502）[M0验证]
# A10 底图样式：curl -I https://basemaps.cartocdn.com/gl/positron-gl-style/style.json   # [已验证 200]
```

## 附录 B：黄金样例（用于 §10.1 断言；均由本次实测产生）

```
B1 DO_p(Tw, P, S) → mg/L
   (10, 1013.25, 0)=11.25 | (25, 1013.25, 0)=8.27 | (25, 990, 0)=8.07 | (25, 1013.25, 30)=6.41   容差 ±0.05

B2 月龄 age(date) → 天   （2026-11-09 为新月）
   10-20→9.1  10-27→16.1  11-03→23.6  11-09→0.4  11-16→6.6  11-23→13.6   容差 ±0.6

B3 青岛 36.06,120.38（海水岸边预设）2026-11-02 逐小时（参照实现输出）
   06:00 → 83 爆护窗口   SL=0.17 m, dh=+0.39 m/h, cur=1.2 km/h, wave=0.28 m, SST=19.7℃, P=1023.1, dP3=+1.4, wind=9.7, gust=19.8, 月龄21.8, 中天5.5h
   09:00 → 64 可以       dh=+0.06 m/h（近停流）, wind=6.0
   19:00 → 73 很适合     dh=+0.65 m/h, cur=2.0 km/h
   11-04 10:00–13:00 → 23/19/20/21（阵风 47.9–49.7 km/h 连续衰减；证明无跳变）
   日综合：11-02 72 | 11-03 71 | 11-04 57 | 11-05 57

B4 千岛湖 29.60,119.04（淡水路亚预设，海拔 99 m）
   11-02 17:00 → 92 爆护窗口  P=1021.6 dP3=+0.5 Tw≈20.9 DO≈8.99 wind=11.7 gust=22.3 rain=0 cloud=6% 月龄22.3 中天6.0
   11-02 06:00 → 89 | 11-02 13:00 → 71（dP3=−3.0 属日变化，不得误杀）
   日综合：11-02 76 | 11-03 73 | 11-04 68 | 11-05 77

B5 深圳 22.5,114.5：11-03/11-04 阵风 50–59 km/h → 日综合 17/16（大面积"别去"），11-05 风歇回到 60+
   ⚠ 该点 elevation 被模型读为 285 m（陆地格点）→ 证明 §6.9 不可把 elevation 当主判据

B6 舟山 30.25,122.6：潮差 ≈2.1 m，半日潮相位正确；11-03 07:00 → 81（风小 + 涨潮 + 流速 1.2）；
   11-05 全天阵风 40–54 → 2–37 分；11-04 07:00 出现 WMO≥95 → 分数 1（雷暴硬否决生效）

B7 潮位校正层实测（模型 vs NOAA，22 天，480 对齐小时）
   圣弗朗西斯科 9414290：原始 RMSE 0.167 m, r 0.949 → lag=+1 h, gain 0.97, offset 0.00 → 校正 RMSE 0.077 m（−54%）
       半样本外（fit 60% / test 40%）：test RMSE 0.074 vs 基线 0.201（−63%）→ 证明非过拟合
   纽波特 9435380：原始 RMSE 0.113 m, r 0.989 → lag 0, gain 0.99 → 校正 0.111 m（基本不变）；留出集 0.132 vs 0.132
   → 采纳规则(r≥0.6, |lag|≤5, gain∈[0.7,1.4], 样本≥150h) 可同时得到"该修就修、不必修就不动"

B7b 逆地理黄金值（本次实测，用于 §10.1 ReverseGeocoderTest 断言）
   RG-A zh 湖心(29.55,118.95)   → 中华人民共和国/CN/浙江省/杭州市/淳安县, plusCode 7PXWHX22+22
   RG-A zh 青岛外海(36.20,120.70)→ 山東省/青島市/嶗山區（繁！须 T2S → 山东省/青岛市/崂山区）
   RG-A zh 太湖水面(31.20,120.30)→ 江蘇省/苏州市/吴中区（省繁市简 → 必须归一）
   RG-A zh 开阔洋(20.0,125.0)   → 名称字段多为空, loc="菲律宾海"（海区名可用）
   RG-A zh 渤海湾(38.80,119.50) → loc="渤海", sd/city 空
   RG-A en 青岛(36.06,120.38)   → Shandong Sheng / Qingdao / Shinan Qu（英文稳定，可作 T2S 替代方案）
   RG-B    岸边(29.605,119.038) layer=locality → 「骑龙巷街区」type=locality, city=杭州市
   RG-B    湖心(29.55,118.95)  layer=locality → HTTP 200, features=[] （空=正常，禁止重试）
   RG-B    带 lang=zh → HTTP 400 "Language is not supported. Supported are: default, de, en, fr"
   RG-B    layer=locality,district 或 city,state,country → HTTP 400（只接受单值 locality）
   RG-C    深圳湾(22.50,114.15) r=3000 → 200, 2 hits: 荔湖<water>, 洪湖<water>（注意：是小水体，非"深圳湾"）
   RG-C    overpass-api.de 本次连续 4 次查询 → 502；maps.mail.ru 同刻 → 200（证明必须做镜像 + 预算放弃）
   Nominatim 官方 → fetch failed（连接层），故不入契约

B8 内陆海洋字段：29.60,119.04 → HTTP 200，wave_height/sea_level/SST/current 全 null（降级链触发条件）

B9 参数拼写错误响应（必须能识别并回退）：
   models=ncep_gfsocean016 → 400 "Cannot initialize MultiDomains"
   sea_level_height_mssh  → 400 "Cannot initialize SurfacePressureAndHeightVariable…"
   NOAA time_zone=UTC     → 400 "Wrong Time Zone"（只接受 gmt|lst|lst_ldt）
   NOAA product=hourly_height（部分站）→ {"error":{"message":"No data was found…"}}（改 water_level+interval=6 自聚合）
   NOAA 无效 station id → {"error":{"message":" The station is not a valid station or there is system error."}}
   https://api.open-meteo.com/v1/astronomy → 404（不要依赖；天文本地算）
```

## 附录 C：vibe coding 执行提示（建议直接放进 Issue）

1. 严格按 M0→M6 顺序，每个里程碑一次提交，退出标准即 PR 描述。
2. `:core:domain` 先写（M1），因为它能脱离 Android 独立验证；附录 B 全部值就是它的验收测试。
3. 任何新增数据源必须先补 §5 契约表 + §12 红线检查 + §9.6 免责；不得静默加域名。
4. 禁止"顺手美化"算法：权重、阈值、衰减起点（−2.5 hPa/3h、34/45 km/h、1.6 m 浪、4.6 mg/L DO）都是实测校准过的，改动必须同时改单测并说明理由。
5. 遇到 §5 未列出的字段需求，优先在已有响应里找；确实缺失则走 §6.7 降级 + 徽标，不得引入红线源。
6. 保持"准星是唯一真源"，所有请求都以准星坐标发起；缓存键为 (spotId, hourEpoch, field, algoVersion)。
