# M0 数据源探针报告 · DATA_SOURCE_PROBE

> 执行日期：2026-11-02 ｜ 执行环境：Windows / curl+Node ｜ SRS 基线 v1.2 附录 A

## 1. 探针结果

| # | 探针 | 结果 | 证据 | 降级决定 |
|---|---|---|---|---|
| A1 | Open-Meteo 天气 past_days=7+forecast_days=10（408h） | ✅ PASS | 返回 JSON 12951 B，hourly 数组完整 | — |
| A2a | Open-Meteo Marine 海岸点 240h（青岛 36.06,120.38） | ✅ PASS | 返回 JSON 9680 B | — |
| A2b | Marine 内陆点全 null（千岛湖 29.6,119.04） | ✅ PASS | `len=24 allNull=true`，与 SRS §5.2/附录 B8 一致 | 全 null → 触发 §6.9 内陆降级 |
| A3 | Open-Meteo Geocoding 中文搜索 | ✅ PASS | "千岛湖"返回 results（UTF-8 正常） | — |
| A4 | NOAA 潮位 water_level interval=6 time_zone=gmt | ✅ PASS | 返回 378 KB | — |
| A5 | USGS NWIS IV 水温/DO | ✅ PASS | 返回 48988 B | — |
| A6 | Copernicus Lakes 湖泊水温取数 | ⛔ FAIL（无凭据） | GAP-02：客户账号未到位 | **走 §6.1.2 本地推导兜底**，水质徽标"估算"；客户凭据到位后可后置增强 |
| A7 | Copernicus OceanColour 叶绿素取数 | ⛔ FAIL（无凭据） | 同 GAP-02 | 同上（§6.2j 水质代理兜底） |
| A8 | NASA GIBS WMTS 瓦片 | ✅ PASS | HTTP 200，PNG 1189 B | 仅作视觉叠加层候选（.gtx 数值化未验证，不参与评分） |
| A9 | OSM Overpass 水体判定 | ⛔ FAIL | 主镜像 POST 406（含表单编码复测）；备用 kumi 镜像超时（504 同既往） | **不接入 Overpass**：水体判定走 §6.9 前两级（Marine 全 null 判定 + 海岸距离/elevation 参考，附录 B5 已证明 elevation 仅作参考）；判定缓存 30 d/点 |
| A10 | CARTO positron 底图样式 | ✅ PASS | HTTP 200 | 采用为默认底图（授权符合 §12） |
| A11 | Photon 逆地理（主源） | ✅ PASS | 返回 371 B，含中文名称字段 | — |
| A11b | Nominatim 逆地理（备用） | ⛔ FAIL | 返回 0 B（连通失败，同 SRS 编写时 HTTP 000） | **移除备用**，仅 Photon + 坐标文本兜底（"29.600000, 119.038000"） |

## 2. 结论与对后续里程碑的影响

1. **主流程数据源 100% 可用**：天气/海洋/地理/逆地理/底图全部 PASS，M1–M4 可按期开工。
2. **水质维度走兜底链**：中国境内点 = §6.1.2 水温推导 + §6.2a DO + §6.2j 代理，ConfidenceBadge="估算"；境外点可选用 USGS 实测（A5 PASS）。GAP-02 保持"待澄清"，客户凭据到位后作为**增强**而非阻塞项。
3. **水体判定降级**：不依赖 Overpass；§6.9 判定树以前两级为主，M3 实现时按此收缩范围（对应 SRS GAP-03 处理方式，不阻塞）。
4. **逆地理单源**：Photon 唯一源 + 节流 ≥3 s + 缓存 30 d/点（§5.8 契约不变，仅删备用分支）。

## 3. M0 退出评审

- 退出标准（SRS §11）：`DATA_SOURCE_PROBE.md` 逐条 PASS/FAIL + FAIL 降级决定 → **本文件即满足，M0 ✅ 通过**。
- 遗留：GAP-02（客户凭据，转 A-01 行动项继续跟踪）；Overpass 若后续恢复可作 v1.x 增强。

## 4. M3.0 复测（2026-11-05，凭据探针）

> 背景：客户提供 Copernicus Marine 凭据（已存 `tidespot-android/local-secrets.properties`，不入库、不打包进 APK），针对 A6/A7 的"无凭据 FAIL"进行复测。

| # | 探针 | 结果 | 证据 | 结论 |
|---|---|---|---|---|
| A6-r1 | Copernicus 鉴权 token 获取 | ✅ PASS | `POST https://auth.marine.copernicus.eu/realms/MIS/protocol/openid-connect/token`（client_id=toolbox, grant_type=password）→ HTTP 200，Bearer 签发成功，expires_in=300 s | **GAP-02 鉴权层解除**；凭据有效 |
| A6-r2 | GLACIO_ANALYSISFORECAST_PHY_012_002 产品在档检查 | ⛔ FAIL（产品已退役） | 当前 Marine Data Store 目录（STAC bucket `mdl-metadata` 全量清单，2026-11-05 版）**无任何 GLACIO/LAK 产品**；官方帮助中心（2026-09-23 版《Lake data available in Copernicus Marine products》）确认：湖泊数据现仅存在于部分 SST/Ocean Colour 产品内 | **SRS §6.9 第 3 级与 §6.1.2 引用的 GLACIO 产品已不存在，需替换（→CHG-06）** |
| A6-r3 | 替代源可达性：OSTIA `SST_GLO_SST_L4_NRT_OBSERVATIONS_010_001`（数据集 METOFFICE-GLO-SST-L4-NRT-OBS-SST-V2，0.05°，日值，含 lake mask/LSWT） | ✅ PASS（元数据层） | STAC dataset.stac.json 200；native/WMTS/zarr 四类端点均返回地址；WMTS 图层 `analysed_sst` queryable=1（支持 GetFeatureInfo），time 维度有 Default，InfoFormat=application/json | §6.9 第 3 级（内陆水体判定）替代路径成立；**千岛湖是否在 OSTIA 湖泊掩膜内待取值验证**（见 A6-r4） |
| A6-r4 | OSTIA WMTS 取值验证（沙箱 + 本机双端） | ⛔ 暂不可用（图层级故障） | `GetTile`/`GetFeatureInfo`（z=6/7/8，含 TIME）均 500 "empty iterator"；**对照 GLOBAL_ANALYSISFORECAST_PHY_001_024 ist 图层 GetTile 返回 200 PNG** → 服务正常，该图层自身故障（admp_updated=2026-11-05T07:11Z 恰逢数据更新）；TILEMATRIXSET=EPSG:3857、级别名 0–10、InfoFormat 参数均已核实无误 | 非项目侧问题，**待 OSTIA 图层恢复后复测同一条命令**（沙箱/本机皆可）；不阻塞 M3.1–M3.3，仅影响 M3.4 第 3 级分支，复测不过则按 CHG-06 预案降级 |

### M3.0 环境自检结论

- Gradle 8.9：zip 完好（136 MB/305 条目）；**沙箱禁写 `D:\tools` 与用户目录**，已解压至 `D:\workspace\weatherfishcheck\.tools\gradle-8.9\bin\gradle.bat` 并验证 `--version` 正常。本机执行 A-05 时建议在 `D:\tools` 补一份正式解压（CONTRACT 引用路径）。
- `subst T:` 映射创建成功；JDK 17 / Android SDK 路径正常。
- 沙箱网络为选择性放行：`auth.marine.copernicus.eu` 可达；`stac.marine.copernicus.eu` 仅 curl.exe 可达（PowerShell Invoke-WebRequest 被代理拦为 400）；`catalogue/api.marine.copernicus.eu` DNS 不解析。后续沙箱内网络探针一律用 curl.exe。
- 实现注意（M3.4 水体判定）：token 有效期 300 s，需缓存并按需刷新；湖泊判定查询属低频（仅显式选点 + 30 d 缓存），不会触碰频控。

## 5. 对后续里程碑的影响（M3.0 更新）

1. **GAP-02 鉴权解除**：Copernicus 增强路径（内陆水体判定 OSTIA / 水质 OceanColour）重新可选，作为增强不阻塞 M3。
2. **§6.9 第 3 级数据源替换**（待 CHG-06 确认）：GLACIO → OSTIA L4 lake mask；若千岛湖覆盖验证不过，内陆水体判定统一按"内陆/陆地"降级并徽标标注。
3. 逆地理口径维持 M0 结论 + CHG-05 契约：实现 RG-A/RG-B/RG-D 三级（RG-C Overpass 维持不接入），节流 ≤1 req/2s（§12 口径，取代 HANDOVER 的"≥3 s"表述）。
