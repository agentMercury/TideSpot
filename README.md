> 本项目是几乎完全由 agent 开发的项目。鉴于本人几乎没有代码能力，所以在这个项目里只负责许愿和创建本仓库。
>
> 因为这个项目是围绕我实际存在的需求、在本人电脑上完成开发的，所以本项目会遗留一些本地的痕迹，请不要在意。
>
> 后续本项目的运营和更新也几乎都会由 agent 独立完成，各位也可以在 issue 上许愿。
>
> 最后：如果本项目中有我本人亲自回复或者写作的部分，我会明确表明我的存在（比如这一段）。所以如果没有明确说明是本人写的东西，大家默认是 agent 进行的操作就好。
>
> —— 以上

# 潮点 TideSpot

**离线优先的钓鱼指数工具**：地图选点 → 潮汐 / 天气 / 海洋多源聚合 → 0–100 五维加权指数 → 最佳作钓窗口。

回答一个问题：**「今天到底值不值得去钓，几点去？」**

Android（Kotlin + Jetpack Compose）与 Web（React PWA）双端，评分内核**逐字段一致**——同一坐标、同一小时，两端算出同一个分。

---

## 为什么做这个

现有钓鱼指数要么是黑箱（只有「适宜/不宜」三档、逐日粒度，对「今晚几点去」毫无用处），要么不开源、需注册 Key、只覆盖美国水域。

这个项目把评分算法**完全公开且可复现**：每个因子的权重、每一项否决规则都写在 [FISHSCORE.md](FISHSCORE.md) 里，所有数据源免费免 Key。

---

## ✨ 功能

| 能力 | 说明 |
|---|---|
| **地图选点** | 十字准星是唯一选点真源；地名搜索、收藏星标图层、长按居中、底图三样式 + 海温叠加 |
| **钓鱼指数** | 溶氧、气压趋势、Solunar、潮汐、风、天气、时段等因子加权；含安全否决与低置信降级 |
| **最佳窗口** | 当日逐时柱状图（五档色带，≥80 分标窗）；跨收藏点 Top10 按「峰值 + 均值 + 时长」排序；首页常驻「下一个窗口」倒计时 |
| **详情五 Tab** | 钓鱼指数 / 潮流 / 天气 / 水质 / 风浪；图表 ↔ 数值表切换 |
| **收藏管理** | 四序（综合 / 最近 / 分数 / 名称）、搜索、分组折叠、标签、批量操作 |
| **多皮肤** | 皮肤 × 水体（海 / 内陆）× 明暗 正交切换；只改颜色、圆角、字族 |
| **中英双语** | 580 个文案 key，全站即时切换 |
| **离线优先** | 本地库 / IndexedDB 缓存 + Service Worker；缺测一律 `null` 断开，不补 0 造假 |
| **自适应布局** | Web 同一 URL 按视口切 手机 / 平板 / 桌面 三档 |

---

## 🧠 评分是怎么算的

详见 **[FISHSCORE.md](FISHSCORE.md)**（单一真相源）。概括：

**九个正因子**加权 → 得到 0–100 基础分：

| 因子 | 为什么重要 |
|---|---|
| 溶氧潜力（DO） | 第一限制因子。水温↑→饱和溶氧↓；气压↓→氧分压↓。溶氧低→停口 |
| 气压及其趋势 | 鱼感知锋面。缓慢上升的高压脊最好；骤降（风雨前）最差 |
| Solunar | 月中天与对点前后为摄食窗；朔望大潮加成 |
| 潮汐 / 潮流 | 海水钓第一权重：涨落带来饵料与溶氧；平潮几乎无口 |
| 风 | 双面：微风增氧聚饵，大风无法抛投且危险 |
| 天气 / 降水 / 云 | 毛毛雨 + 多云常加分；暴雨雷暴是否决项 |
| 水温适温匹配 | 决定代谢率与攻击性，路亚尤其依赖 |
| 时段（日出日落） | 弱光时掠食性鱼敢靠近浅水 |
| 体感温度 | 权重刻意压低，但极热极冷应封顶 |

**安全否决是乘性的，不是扣分**：雷暴、大风、大浪、强降水直接一票否决。因为「下着雷暴但其他条件很好」不该得到 60 分。

> 核心原则：**拿不到的数据就断开，不猜、不补零**。多数点位没有公开实测水质，水质数值为模型推导并明确标注「估算」。

---

## 🛠 技术栈

| | Android | Web |
|---|---|---|
| 语言 | Kotlin 1.9 | TypeScript 5 |
| UI | Jetpack Compose (Material 3) | React 18 + 自建 token 体系 |
| 构建 | Gradle 8.9 + AGP | Vite 5 |
| 地图 | MapLibre Android | MapLibre GL JS 6 |
| 存储 | Room (SQLite) | IndexedDB (Dexie) + CacheStorage |
| 网络 | OkHttp + 自研 TokenBucket 限流 | `fetch` + 指数退避重试 |
| 离线 | Room + WorkManager | Service Worker (Workbox) |
| 测试 | JUnit + Robolectric | Vitest + Playwright |

代码规模：Kotlin 约 2.7 万行（217 文件），TypeScript 约 8 千行（73 文件）。

---

## 🏗 怎么实现的

**双端同构**——Android 与 Web 共享同一套分层，只是换了实现：

```
domain（纯计算，无平台依赖）
   ↓
data（拉取 + 缓存 + 降级 + 重试）
   ↓
ui（页面 / 组件 / 图表）
```

关键设计：

1. **`domain` 层是纯函数**，不碰网络和存储，所以能被完整单测覆盖，也是双端对拍的基准。
2. **评分对拍测试**：Web 的 `parity.test.ts` 用 Kotlin 导出的黄金值逐字段比对，容差 `1e-9`。改算法时两端不一致会立刻红。
3. **文案 key 强制同步**：Web 的文案 key 集合必须与 Android `strings.xml` 完全一致（`check:parity` 580/580）。
4. **视觉 token 单一来源**：`design-handoff/tokens/*` 在 Web 构建期生成 CSS，两端同源。
5. **降级链而非硬失败**：逆地理四级降级、底图多源切换、水质无数据时标注估算。

---

## 📦 部署

### Web（GitHub Pages，零服务端）

线上地址：**https://agentmercury.github.io/TideSpot/**

纯静态 PWA，可「添加到主屏幕」离线使用。部署由 GitHub Actions 自动完成（`.github/workflows/deploy-web.yml`）：推送 `main` 且改动 `web/**` → `npm ci` → 测试 → 构建 → E2E → 发布。

自己部署也很简单：

```bash
cd web
npm ci
npm run build          # 产物在 dist/
# 把 dist/ 丢到任意静态托管（Cloudflare Pages / Vercel / 对象存储 + CDN）
```

> 首次打开后刷新一次，让 Service Worker 接管。国内访问 `github.io` 可能较慢，建议自建或挂自定义域名。

### Android

需要 JDK 17 + Android SDK 35。

```bash
cd tidespot-android
./gradlew :app:assembleRelease     # 产物：app/build/outputs/apk/release/
adb install -r "潮点TideSpot-v1.21.1.apk"
```

> 仓库不含签名密钥与凭据。自签名配置放 `tidespot-android/local-secrets.properties`（已在 `.gitignore`）。缺失时 release 会回退 debug 签名并告警——仅供本机调试，**不可对外分发**。

---

## 🧪 测试与门禁

| 端 | 命令 | 说明 |
|---|---|---|
| Web | `npm test` | 单元 + 领域对拍 |
| Web | `npm run test:e2e` | Playwright 冒烟（手机 + 桌面） |
| Web | `npm run check:parity` | 文案 key 与 Android 一致 |
| Web | `npm run check:contrast` | WCAG 对比度 |
| Web | `npm run check:budget` | 首屏 / 地图 chunk 体积预算 |
| Android | `verify.ps1` | 八模块单测 + 红线扫描 |
| 通用 | `node .tools/i18n_scan.mjs` | 文案缺失必须 `FAIL=0` |
| 通用 | `node .tools/contrast_check.mjs` | 对比度 `0 FAIL` |

---

## 🔌 数据源与署名

| 来源 | 用途 | 许可 |
|---|---|---|
| [Open-Meteo](https://open-meteo.com/) | 天气 / 海洋预报、地名搜索 | CC BY 4.0 |
| [Photon](https://photon.komoot.io/) | 逆地理编码 | ODbL（© OpenStreetMap） |
| [BigDataCloud](https://www.bigdatacloud.com/) | 逆地理编码（备用） | CC BY 4.0 |
| [OpenFreeMap](https://openfreemap.org/) / [CARTO](https://carto.com/) | 底图 | ODbL / BSD-2 |
| [Copernicus Marine](https://marine.copernicus.eu/) | 海温 / 水质（可选，需自备凭据） | Copernicus 许可 |

**本项目不收集任何用户数据**：坐标只发往上述公开 API，不上传服务器。凭据（如 Copernicus）仅存用户本机。

---

## ⚠️ 免责声明

- 指数由气象与天文因子经验加权估算，**不构成渔获保证**。
- 雷暴、大风、大浪判定仅供参考；**出钓安全以当地预警为准**，雷电与台风期间切勿下水。
- 潮位 / 潮流来自约 8 km 全球模型，官方明确不适用于近岸航行；**不替代**官方潮汐表与航路指南。
- 多数点位无公开实测水质，水质数值为模型推导并标注「估算」。

---

## 🤝 参与贡献

欢迎提 issue——尤其欢迎：

- **算法改进**：你觉得某个因子权重不合理，请带上具体水域和实际钓况
- **数据源**：知道更好的免费源（尤其中国沿海潮位实测）
- **Bug 报告**：请写明端（Android/Web）、版本、复现步骤

这个项目由 agent 主导开发，会在 issue 里直接回复技术方案和实现进度。

---

## 📄 文档

| 文件 | 作用 |
|---|---|
| [FISHSCORE.md](FISHSCORE.md) | **评分算法**：因子权重、否决规则、实测校验 |
| [WEB_PROJECT_PLAN.md](WEB_PROJECT_PLAN.md) | Web 里程碑与跨端适配契约 |
| [tidespot-android/CONTRACT.md](tidespot-android/CONTRACT.md) | Android 模块契约与冻结点 |
| [HANDOVER_V115.md](HANDOVER_V115.md) | 开发交接：环境、基线、红线 |
| 其余 `*.md` | 历史批次的开发记录，**请勿用于判断当前架构** |

---

## 📌 版本

| 端 | 版本 |
|---|---|
| Android | `1.22.0`（versionCode 30） |
| Web | `1.18.3` |

两端版本号独立管理，评分内核与文案 key 强制一致。

---

<p align="center"><sub>潮点 TideSpot · 双端一体，评分同源</sub></p>
