# 交接文档 · 潮点 TideSpot **V1.15**（HANDOVER_V115）

> 交接日期：**2026-11-11** ｜ 状态：**V1.15 已收官（DoD 5/5 全绿）**，可进入验收；后续施工转向 **V1.2 池**（`OPT_REQUIREMENTS_V11.md` §7）与新需求
> 交接方：V1.15 交付会话（交付负责人） ｜ 接手方：下一开发会话
> **必读顺序**：本文件 → `REQUIREMENTS_REGISTER.md`（**CHG-46~56 为 V1.15**）→ `PROGRESS_TRACKER.md` §10（V1.15 台账 + DoD 收官 + 英文验收 + 收尾补记）→ `design-handoff/DECISIONS.md`（§1 裁定 / §2 GAP-05 明细 / §6 复核 / §8 视觉进度 / §9 结论）→ `OPT_REQUIREMENTS_V115.md` → `tidespot-android/CONTRACT.md` → `HANDOVER_V11.md`（v1.1 细节与环境雷区）

---

## 1. 当前基线（2026-11-11 收官时）

| 项 | 值 |
|---|---|
| 代码 | `D:\workspace\天气鱼情`，jj/git colocated（每次提交后手动 `git update-ref refs/heads/main <commit>`） |
| 版本 | **`versionCode=19` / `versionName=1.15.14`** |
| APK | **`E:\Trans\潮点TideSpot-v1.15.14.apk`**（51,933,996 B；SHA-256 `40e59f66a644694d9c09f498ab9d2a5ddd5b43463ffffa95316ba07a1c8c0d3d`；自签名，已装 AAK-AN00） |
| 测试 | 八模块 `gradlew test` **EXIT=0**（每次批次均跑） |
| i18n | `.tools/i18n_scan.mjs` **FAIL=0**（豁免 12）；`values` / `values-en` 241/241 |
| GAP-05 | **17/17 每 key ≥1 处引用**（曾出现 15/17 回归，CHG-54 已修复） |
| 对比度 | `.tools/contrast_check.mjs`（WCAG 2.1）**FAIL=0 / 29 对**（浅色 13 对 + 深色 16 对 + 冻结色 11 对） |
| 真机 | AAK-AN00 / Android 16 / 1272×2800 / 560dpi；`adb` 可用；**ROM 不支持 `cmd locale set-app-locales`**，切 App 语言须在系统「应用语言」手工操作（当前已设为 `en`，验收后记得切回） |
| App locale | 当前 `en`（`adb shell cmd locale get-app-locales com.tidespot.app` → `[en]`），中英文均已验收 |

## 2. 环境红线（每次都踩，务必先看）

1. **必须用 `T:` 盘构建**：`subst T: D:\workspace\天气鱼情`，命令在 `T:\tidespot-android` 下执行（中文路径会让 Gradle test worker 抛 `ClassNotFoundException`）。
2. `JAVA_HOME` 指向 JDK 17；`adb` = `C:\Users\gamer\AppData\Local\Android\Sdk\platform-tools\adb.exe`。
3. Release 签名与 debug 不兼容 → **统一测 release 包**（`adb install -r`）。
4. `assembleRelease` 偶发 `lintVitalAnalyzeRelease … FileSystemException` → 关 Studio / 杀 java 后重跑，或 `-x lintVitalAnalyzeRelease` 再补跑一次。
5. 扫描器：`node .tools/i18n_scan.mjs`（全量）/ `--only=<子串>`（单模块）；新增豁免必须写理由注释。
6. jj 大文件上限：`design-handoff` 内有 2.2MB zip 与 1.1MB svg，提交时需 `jj --config snapshot.max-new-file-size=2304382 commit ...`。

## 3. 已交付、不要重做

### V1.1 池（26 项 = 25 交付 + 1 移除）
P0-1（凭据外部化/引导卡/图标）、P0-2（水质录入/时段筛选/F-3.7/Top10 核心段/阈值）、P1（14 项 OPT+UX）、P2（批量/排序/详情卡片/分组）、i18n 存量全量外移；OPT-V11-17 缩略图按 D21 移除。

### V1.15 池（2 项，DoD 5/5）
- **OPT-V115-01**：GAP-05 剩余 7 条全部接线，**17/17**。
- **OPT-V115-02**：UI v2 设计包落地（详见 §4）。

## 4. V1.15 交付内容（按屏）

| 屏 | 已落地 |
|---|---|
| **S01 地图** | C02 顶部「白色搜索框（横滑展开）+ 3 个 44×46 方块按钮（定位实心）」；C19 **深蓝 Top10 横幅**（366×48 rx14）；C03 **236dp 信息浮条 + 独立操作面板**；C21 底图署名行；**C22 来源行**（`:app` 透传 `fetchedAt`，无数据显示「—」）；移除右下浮动三按钮 |
| **S02 指数** | TOPBAR（返回 + 名称 + 坐标）；**分段 Tab**（容器 358×40 rx12，选中白胶囊 rx10）；**rx18 日期胶囊**（选中 `#1565C0`）；来源行独立；**深蓝主卡 `#0D47A1` 152dp**（48sp 分数 + 档位胶囊 + 五段色带 + 置信徽标）；C09 图表卡；**图表/数值表分段胶囊**；C06 底部 56dp 免责条 |
| **S03 因子分解** | 卡片化（因子行 / ΠV 明细 / 原始输入）；**修复因子名回退英文 id**（`valueOf(id.uppercase())`） |
| **S04 湖面风 / S07 潮流** | **客户裁定「以现有实现为准」**（不复刻结构稿的数值表节选 / 校潮对照版式） |
| **S05 天气** | 图表卡 + **安全摘要卡**（免责第 2 条原文） |
| **S06 水质** | **三卡一行**（水温 / 溶解氧 / 浊度，112dp，置信徽标）；**pH 已删除**（领域模型无字段，不虚构） |
| **S08 收藏** | 卡片列表 + 窗口摘要 + 分数档位 + **水体类型行** |
| **S09 设置** | 标题样式化 + **六版块单卡** + 底部免责条 + Sheet |
| **主题/组件** | GAP-03 edge-to-edge（四屏 + 四 Sheet insets）；surface/onSurface token；Shapes（16/12/20）；Typography（20/16/14/15/13/12/10）；C01 准星 dp 化；C04 对话框 20dp；Sheet 顶部 20dp |

## 5. 已知偏离与注意（验收会被问到）

1. **浅色次要文字**：设计稿 `#8A9DB0` 在浅底仅 **2.19–2.79:1**，不满足 DoD ≥4.5:1 → 实现取 **`#556579`**（4.68–5.96:1，色相不变）。**这是唯一有意偏离设计稿的色值**，CHG-56 留证。
2. **`enableEdgeToEdge` 不可解析**（activity 1.9.3 的 FQN 问题）→ 用等价 `WindowCompat.setDecorFitsSystemWindows(window,false)`。
3. **pH 不实现**：`WaterQualitySample` 无 pH 字段；如后续要接，需扩冻结领域模型（另立需求）。
4. **S04/S07**：结构稿仅作参考，验收以现有实现为准（客户裁定，CHG-53）。
5. **英文 Tab**：五维 Tab 已改**横滑 + 内容宽度**（不再等分），英文长串不截断；若产品要求"一屏看全五个"，需缩文案或降字号（未做）。
6. **地图来源行**：数据来自最近一次详情拉取；冷启动无数据时显示「—」（符合 CONFLICT-03 禁编造）。

## 6. 硬约束（违反即返工）

- `ScoreBands` / `ConfidenceBadge` / `TideSpotTheme` 的**冻结色值与主色**不改；UI 更新只动布局/圆角/间距/字阶。
- 免责恒 5 条（`TideSpotStringsTest` 断言）；免责文案以工程 D11 版为准。
- 「源更新时间」显示真实值或「—」，**禁止编造**（CONFLICT-03）。
- 协议值（`UNGROUPED_KEY`、预设排序 key 等）**禁翻译**。
- BottomSheet 一律 `skipPartiallyExpanded=true` + 操作按钮置顶 + `navigationBarsPadding()`。
- 改 `:core:*` 公共签名 → 同步 `tidespot-android/CONTRACT.md`；新增用户可见文案 → 中英双资源 + 锁测试。
- 交付口径：功能 + 文档 + 台账 + 真机验证同时给到。

## 7. 接手第一步

1. `subst T: D:\workspace\天气鱼情` → `.\gradlew.bat test` 确认基线绿；`node .tools/i18n_scan.mjs`、`node .tools/contrast_check.mjs` 均应为 0 FAIL。
2. 读 `REQUIREMENTS_REGISTER` CHG-46~56，了解每批改动与遗留裁定。
3. 后续方向：**V1.2 池**（`OPT_REQUIREMENTS_V11.md` §7：星标 OPT-V12-01、列表刷新 02、详情刷新 03、DaySelector 两行 04、高德 OPT-V12-05）或客户新需求。
4. 改 UI 前先看 `design-handoff/` 的 **SVG 几何**（不是只看 `screens.json` 的 box），改完 `adb` 截图比对。

## 8. 验证与证据

- 构建：`assembleRelease` + `copyApkToTrans` 自动分发 `E:\Trans`；产物名 `潮点TideSpot-v{versionName}.apk`。
- 扫描：`node .tools/i18n_scan.mjs`；对比度：`node .tools/contrast_check.mjs`（29 对，正文 4.5 / 图形 3）。
- 真机截图（工作区 `.shots/`）：`f_en01~06.png`（英文地图/收藏/详情/水质/天气/深色）、`g_en03.png`（英文 Tab 横滑修复后）、`dk01~04.png`（深色 S01/S02/S08/S09）、`i01/i05/k03/l08/m06.png`（中文各屏）。

## 9. 台账索引（V1.15，CHG-46~56）

| CHG | 内容 |
|---|---|
| 46 | GAP-05 7 条接线 + UI v2 骨架（insets/token）+ 1.15.0 出包 |
| 47 | 首轮视觉重排（S01/S02/S09 结构） |
| 48 | 真机取图后按 SVG 几何校准（分段 Tab / 胶囊日期 / 深蓝主卡 / 搜索框 / 深蓝横幅 / 文字色） |
| 49 | S02 图表分段胶囊 + 来源行拆行；S09 卡片化 |
| 50 | S01 C03 信息浮条 + 独立操作面板 |
| 51 | S06 2×2 指标卡 + C21 署名 + 深色对比度修复 |
| 52 | C22 来源行贯通 + S03 卡化/因子名修复 + S05 安全摘要 + S08 水体类型 |
| 53 | 删除 pH + S04/S07 以现有实现为准（客户裁定） |
| 54 | GAP-05 回归修复（15/17 → 17/17） |
| 55 | 英文真机验收 + 3 处英文缺陷修复 |
| 56 | 22 组件余 6 项对齐 + 对比度量化 FAIL=0/29 + 深色四屏留证 |