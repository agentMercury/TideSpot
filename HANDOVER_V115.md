# 交接文档 · 潮点 TideSpot **V1.15**（HANDOVER_V115）

> 交接日期：**2026-11-11** ｜ 状态：**V1.15 已收官（DoD 5/5 全绿）**，可进入验收；后续施工转向 **V1.2 池**（`OPT_REQUIREMENTS_V11.md` §7）与新需求
> 交接方：V1.15 交付会话（交付负责人） ｜ 接手方：下一开发会话
> **必读顺序**：本文件 → `REQUIREMENTS_REGISTER.md`（**CHG-46~56 为 V1.15**）→ `PROGRESS_TRACKER.md` §10（V1.15 台账 + DoD 收官 + 英文验收 + 收尾补记）→ `design-handoff/DECISIONS.md`（§1 裁定 / §2 GAP-05 明细 / §6 复核 / §8 视觉进度 / §9 结论）→ `OPT_REQUIREMENTS_V115.md` → `tidespot-android/CONTRACT.md` → `HANDOVER_V11.md`（v1.1 细节与环境雷区）

## 0. 文档维护契约（改代码前读，收工前必须执行）

> **为什么单列成一节**：本工程出现过「代码已落地、文档还写着没做」，下一个 agent 照旧描述误判或重做
> （2026-11-13 实例：某处仍写「还没有 SKIN-1 抽象层」，实际已落地 3 个批次）。
> **文档是交接物的一部分，不是交付后的可选动作。**

### 0.1 三条硬规则

1. **同批更新**：代码 / 资源 / 配置的任何语义改动，必须与代码在**同一个提交**内更新受影响文档，禁止「下次一起补」。
2. **不许留过时断言**：无法改写的历史材料（已发布报告、旧对拍记录），必须在文首加
   `> ⚠️ 历史快照（日期）｜已被 <提交号/需求 ID> 取代，勿用于判断现有代码`。
   **禁止让旧结论以现在时态留在正文**——这是最容易误导下一个 agent 的形态。
3. **冲突以代码为准**：文档与代码不一致时，代码是唯一事实来源；立刻改文档；确实改不动就写明差异与原因，不留隐性歧义。

### 0.2 改动 → 必更文档（映射表）

| 你改了什么 | 必须同步 |
|---|---|
| `:core:*` 公共签名 / 数据模型 / 冻结点 | `tidespot-android/CONTRACT.md`（附变更记录 + 回滚方式） |
| 用户可见文案 | `values` + `values-en` 双资源；`node .tools/i18n_scan.mjs` 必须 FAIL=0 |
| 设置项 / 持久化键 | `SettingsKeys` + `AppSettings` + `CONTRACT.md` + 设置页文案与其说明行 |
| 皮肤 / 主题 token / 换肤入口 | `design-handdrawn/A-vintage-outdoor/SKIN-DEV-STANDARD.md`（版本号 +1 + 顶部变更行） |
| 有意偏离设计稿（色值 / 布局 / 结构） | 本文件 §5「已知偏离」+ `device-compare/` 对拍报告（附量化对比度） |
| 一批需求做完 | `design-handdrawn/A-vintage-outdoor/未开始需求清单.md`（状态 + **落地结果指向真实文件与行号**） |
| 每个提交 | `jj describe` 写清「改了什么 / 为什么 / 门禁结果」；建 change 后 `git update-ref refs/heads/main <commit>` |
| 多会话 / 多人并行 | 见 §0.7：**各自开 `jj workspace`**，禁止两个会话共用一个工作副本 |
| 会话结束 | `.workbuddy/memory/YYYY-MM-DD.md` 追加当天决策与踩坑（不记临时路径、工具报错） |

### 0.3 文档地图（谁管什么、谁权威）

| 文件 | 管什么 | 权威度 |
|---|---|---|
| `HANDOVER_V115.md`（本文件） | 交接指引、环境红线、已知偏离、**文档维护契约** | 规则类权威 |
| `tidespot-android/CONTRACT.md` | 模块契约与冻结点、变更记录 | 契约类权威（改动必须登记） |
| `PROGRESS_TRACKER.md` | 里程碑台账、验收结论 | 台账类，**状态可能滞后，以代码与本文件为准** |
| `REQUIREMENTS_REGISTER.md` | 需求登记与裁定（`CHG-*`） | 索引类 |
| `design-handdrawn/A-vintage-outdoor/` | 设计稿；`SKIN-DEV-STANDARD.md` 换肤执行手册；`未开始需求清单.md` 需求状态；`device-compare/` 真机证据 | 设计 / 皮肤权威 |
| `.workbuddy/memory/` | 跨会话工作记忆（决策、踩坑、行尾与构建陷阱） | 经验类，非交付物 |

### 0.4 收工自检（4 条，缺一不算完成）

1. 把本次改过的关键词在全仓 `.md` 里搜一遍，确认没有「现在时态的旧结论」残留：
   `grep -rn "<关键词>" --include=*.md .`
2. 门禁全绿：八模块测试 / `node .tools/i18n_scan.mjs` / `node .tools/contrast_check.mjs` / `assembleRelease`。
3. 需求状态旁有「落地结果」并指向真实文件与行号，**不是只写「已完成」三个字**。
4. §0.5「已知过时项」受影响时同步增删。

### 0.5 已知过时项（读到先核实代码，不要直接采信）

| 位置 | 过时内容 | 现状 |
|---|---|---|
| §1 基线表 | 版本行已回填为 1.17.0；**同表其余行**（APK 路径 / SHA / i18n 豁免数 / 对比度对数）仍是 1.15.14 快照 | 版本号以 `tidespot-android/app/build.gradle.kts` 为准 |
| §2 第 1 条 | 「必须用 `T:` 盘构建（`subst T:`）」 | **已废弃**：2026-11-11 移除 `subst T:` 与 `android.overridePathCheck`，`verify.ps1` 直接用真实路径 |
| §9 台账表 | 只到 `CHG-57` | `CHG-58` 之后以 `jj log` 与 `PROGRESS_TRACKER.md` 为准 |
| `PROGRESS_TRACKER.md` §1 | 「v1.1 P0 完成 → P1 待开工」 | 与现状（v1.16.0、手绘 A 全屏换肤已落地）不符，属未回填 |

### 0.6 发版与版本号（每次发版必改，客户 2026-11-13 明确要求）

**落点**：`tidespot-android/app/build.gradle.kts` 的 `versionCode` 与 `versionName`（当前 `20` / `1.16.0`）。

| 改动性质 | 怎么加 | 例子 |
|---|---|---|
| **小版本**：修 bug、文案 / 样式微调，**无功能变化** | **第二个 `.` 之后 +1** | `1.16.0` → `1.16.1` |
| **有功能修改 / 新增** | **第一个 `.` 之后 +1，末位归零** | `1.16.1` → `1.17.0` |
| **重大更新** | **第一位 +1**，且**必须经客户明确认证** | `1.17.0` → `2.0.0` |

铁律：

1. **每个发版都必须动版本号**，不许「内容少就不改」——同名同码的包会让真机验收与回滚失真。
2. **`versionCode` 每次 +1**（严格递增），与 `versionName` 同批改；只改 `versionName` 会导致覆盖安装失败。
3. **第一位只在客户认证的重大更新时加**。自评「这次改动很大」不算认证；未经客户明确同意，版本号永远停在 `1.x`。
4. 定性拿不准时按**低一档**处理（拿不准是功能还是修补 → 当小版本），不要擅自升 MINOR/MAJOR。
5. 版本号变更后产物名自动跟随（`潮点TideSpot-v{versionName}.apk`），`copyApkToTrans` 分发到 `E:\Trans`，无需改脚本。

**发版必做（缺一不算发版）**：

1. 改 `versionCode` / `versionName`；
2. `assembleRelease` 出包 → 真机 `adb install -r` 冒烟（至少地图 / 详情 / 收藏 / 设置四页 + 明暗切换）；
3. 更新本文件 §1 基线表的版本号（或 §0.5 标注差异）+ `PROGRESS_TRACKER.md` 台账 + 相关需求清单状态；
4. `jj describe` 写清版本号变化（`chore(release): vX.Y.Z`）+ 门禁结果 → `git update-ref refs/heads/main`。

### 0.7 并行会话隔离（长期方案，客户 2026-11-13 选定）

> **事故背景**：2026-11-13 发 `v1.17.0` 时，jj 把**另一个并行会话**的在途代码（`TideSpotSkin.kt` /
> `SkinRegistryTest.kt`，即 D 皮肤）一起快照进了 release 提交。根因不是误操作，而是**两个会话共用同一个工作副本**：
> jj 的提交粒度是**整个工作副本**，不是「我改的那几个文件」。当天靠 `jj split` 拆出并正名，文件指纹未变。

**规则：并行任务必须各自开工作副本，禁止两个会话共用 `D:\workspace\weatherfishcheck`。**

```bash
# 主线（已有）：D:\workspace\weatherfishcheck
jj workspace add ../wf-skin --name skin   # 新建副本，自带独立的 @ 提交
cd ../wf-skin && jj status                # 只看得到本副本的改动
```

| 要点 | 说明 |
|---|---|
| 独立 `@` | 每个 workspace 有自己的 working-copy commit，提交互不吞并 |
| 共用历史 | 副本共享同一个 `.jj` 仓库，`jj log` 看到全部提交，无需 fetch |
| 合回主线 | 在主线 `jj rebase -s <你的 change> -d main`（或先 `jj new main` 再 rebase） |
| **git 同步** | colocated 的 git 只在 **default 副本**自动跟随；其它副本提交后回 default 执行 `git update-ref refs/heads/main <commit>` |
| 副本位置 | 放到仓库**外**（`../wf-*`）；放仓库内会被快照进主线，等于没隔离 |
| 用完清理 | `jj workspace forget <name>` + 删目录，否则 `jj log` 会留游离工作副本 |

**临时兜底（还没开副本就撞上并行改动时）**：提交前先 `jj status` 逐项确认文件来源；不属于本次任务的文件
**只能做 `jj split` 拆分与正名，不得删改对方内容**；提交描述里注明「并行会话产出」。

---


## 1. 当前基线（2026-11-11 收官时）

| 项 | 值 |
|---|---|
| 代码 | `D:\workspace\weatherfishcheck`，jj/git colocated（每次提交后手动 `git update-ref refs/heads/main <commit>`） |
| 版本 | **`versionCode=21` / `versionName=1.17.0`**（2026-11-13 回填；发版规则见 §0.6） |
| APK | **`E:\Trans\潮点TideSpot-v1.15.14.apk`**（51,933,996 B；SHA-256 `40e59f66a644694d9c09f498ab9d2a5ddd5b43463ffffa95316ba07a1c8c0d3d`；自签名，已装 AAK-AN00） |
| 测试 | 八模块 `gradlew test` **EXIT=0**（每次批次均跑） |
| i18n | `.tools/i18n_scan.mjs` **FAIL=0**（豁免 12）；`values` / `values-en` 241/241 |
| GAP-05 | **17/17 每 key ≥1 处引用**（曾出现 15/17 回归，CHG-54 已修复） |
| 对比度 | `.tools/contrast_check.mjs`（WCAG 2.1）**FAIL=0 / 29 对**（浅色 13 对 + 深色 16 对 + 冻结色 11 对） |
| 真机 | AAK-AN00 / Android 16 / 1272×2800 / 560dpi；`adb` 可用；**ROM 不支持 `cmd locale set-app-locales`**，切 App 语言须在系统「应用语言」手工操作（当前已设为 `en`，验收后记得切回） |
| App locale | 当前 `en`（`adb shell cmd locale get-app-locales com.tidespot.app` → `[en]`），中英文均已验收 |

## 2. 环境红线（每次都踩，务必先看）

1. **必须用 `T:` 盘构建**：`subst T: D:\workspace\weatherfishcheck`，命令在 `T:\tidespot-android` 下执行（中文路径会让 Gradle test worker 抛 `ClassNotFoundException`）。
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

1. `subst T: D:\workspace\weatherfishcheck` → `.\gradlew.bat test` 确认基线绿；`node .tools/i18n_scan.mjs`、`node .tools/contrast_check.mjs` 均应为 0 FAIL。
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
| 57 | **V1.2 需求池批次**：OPT-V12-01/02/03/04/07（v1.16.0，versionCode 20） |

## 10. V1.2 需求池批次（2026-11-11，CHG-57，v1.16.0）——接手先读本节

- **范围**：`OPT_REQUIREMENTS_V11.md` §7 的 **OPT-V12-01/02/03/04/07**（客户裁定）。**05 高德仍缓行**（前置：客户开发测试 Key），**06 已于 v1.15.11 闭环**。
- **当前基线**：`versionCode=20 / versionName=1.16.0`；八模块 `gradlew test` **BUILD SUCCESSFUL**；`i18n_scan` **FAIL=0**（豁免 13）；APK `E:\Trans\潮点TideSpot-v1.16.0.apk`。
- **新增能力（接手勿重做）**：
  1. 令牌优先级：`TokenBucket.Priority{USER,BACKGROUND}`（用户操作优先、预取让出；6 req/min 速率不变）；
  2. 详情页下拉刷新：`force` 绕 15 min 门、不绕令牌桶；同点去重（`UserRefreshGate`）；离线/失败/成功提示走资源；
  3. 收藏列表下拉刷新 + 「刷新全部」：非 force、并发 2、进度与汇总、切页取消；
  4. DaySelector 两行 chip（`今天`/`11-12` + `周三`；52dp/56dp；一行 ≥5；来源行在下方）；
  5. 标签预设 chips（`TagPresets` 7 项，忽略大小写去重，存量 `night` 原样保留）；
  6. 地图收藏星标 + 气泡卡（`FavoriteStarLayer`；名称 z≥14 显示；点击星标不动准星；返回相机保持）。
- **未闭环（阻塞）**：真机冒烟（`adb devices` 为空）——按 `PROGRESS_TRACKER §11` 验收单逐条执行。
- **契约**：`tidespot-android/CONTRACT.md`「V1.2 需求池子契约」；SRS §8.1/§8.2-1 描述回写待下一批次。