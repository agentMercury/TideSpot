# 交接文档 · 潮点 TideSpot **V1.15**（HANDOVER_V115）

> 交接日期：**2026-11-11** ｜ 交接方：V1.1 批次开发会话（交付负责人） ｜ 接手方：**下一开发会话**
> **接手起点**：直接开工 **V1.15 需求池**（`OPT_REQUIREMENTS_V115.md`），共 2 项：`OPT-V115-01`（GAP-05 剩余 7 条文案接线）、`OPT-V115-02`（UI v2 设计包全量落地）。设计包**已评估可用**，无需再论证。
> **必读顺序**：本文件 → `OPT_REQUIREMENTS_V115.md`（V1.15 池，含 GWT/风险/DoD）→ `design-handoff/DECISIONS.md`（§1 裁定汇总、§2 GAP-05 逐条明细、§3 实现红线、§6 复核项）→ `PROGRESS_TRACKER.md` §10（V1.15 台账）+ §9（i18n 现状）→ `REQUIREMENTS_REGISTER.md`（CHG-38~45）→ `tidespot-android/CONTRACT.md` → `HANDOVER_V11.md`（v1.1 全量实现细节，**环境雷区与内联设计见 §9**）

---

## 1. 当前基线（2026-11-11 交接时，可直接在此之上开工）

| 项 | 值 |
|---|---|
| 代码 | 工作区 `D:\workspace\天气鱼情`，jj/git colocated（`main` ↔ `@` 同步；每次提交后手动 `git update-ref`） |
| 版本 | `versionCode=4` / `versionName=1.2.0` → **V1.15 交付时升为 `1.15.0`（`versionCode=5`）** |
| 测试 | 八模块 **全绿**（`gradlew test` EXIT=0，最近一次 2026-11-11） |
| i18n 门禁 | `.tools/i18n_scan.mjs` **FAIL=0**（基线 538 → 0；豁免 12 条，见白名单 `DECISIONS §7.2`） |
| APK | `E:\Trans\潮点TideSpot-v1.2.0.apk`（51,915,708 B，自签名；v1.0/v1.1/v1.1.1 三包保留可回退） |
| GAP-05 | **10/17 已接线**，剩余 7 条见 `OPT_REQUIREMENTS_V115.md` §1 表 |
| 真机 | AAK-AN00 / Android 16（HarmonyOS 侧 ROM **不支持** `cmd locale set-app-locales`，切语言须在系统「应用语言」手工操作；交接结束时 `adb devices` 为空，需重新连 USB 并 `adb kill-server` 后重试） |

## 2. 环境红线（每次都踩，务必先看）

1. **必须用 `T:` 盘构建**：`subst T: D:\workspace\天气鱼情`（中文路径会让 Gradle test worker 抛 `ClassNotFoundException` / 「this and base files have different roots」）。所有命令在 `T:\tidespot-android` 下执行：`.\gradlew.bat test`、`.\gradlew.bat :app:assembleRelease`。构建后再 `git status` 确认无残留。
2. `JAVA_HOME` 指向 Android Studio JBR；`adb` = `C:\Users\gamer\AppData\Local\Android\Sdk\platform-tools\adb.exe`。
3. **Release 签名不匹配时不能 `adb install -r` debug 包**（`INSTALL_FAILED_UPDATE_INCOMPATIBLE`）→ 统一测 release 包。
4. `assembleRelease` 偶发 `lintVitalAnalyzeRelease … FileSystemException`（jar 被占用）→ 关掉 Studio/杀 java 进程后重跑，或 `-x lintVitalAnalyzeRelease` 后再完整跑一次。
5. 扫描器用法：`node .tools/i18n_scan.mjs`（全量）/ `--only=<相对路径子串>`（单模块复扫）；豁免白名单在脚本 `EXEMPT` 内，**加豁免必须写明理由注释**（当前 12 条：`AppGraph`/`AppSettings` 日志、`FavoritesSortLogic.PRESET_OPTIONS` 协议值、`TideSpotStrings`/`ZhStrings` 中文镜像本身等）。

## 3. 已交付、不要重做（V1.1 池）

- **26 项中 25 项已完成**：P0-1（凭据外部化/引导卡/图标）、P0-2（水质录入/时段筛选/F-3.7/Top10 核心段/阈值）、P1（14 项 OPT+UX）、P2（12 批量 / 14 排序 / 15 详情卡片 / 16 分组）、**13 i18n 存量全量外移（技术完成）**。
- **移除项**：OPT-V11-17 缩略图（D21，CARTO 栅格瓦片需 key）。
- **i18n 架构（后续新增文案必须走这里，别硬编码）**：
  - `:core:ui`：`TideSpotStrings`（接口）↔ `ZhStrings` / `EnStrings` / `ResStrings`（读 `res/values`）；`ProvideTideSpotStrings {}` 注入，`localTideSpotStrings()`（`CompositionLocal<TideSpotStrings?>` 默认 **null** 哨兵，判断「是否已注入」用它）。
  - `:core:domain`：`DomainCopy` ↔ `ZhDomainCopy` / `ResDomainCopy`；`localDomainCopy()`。
  - **坑（本次已踩）**：纯函数默认参数为 `ZhStrings`/`ZhDomainCopy`，**调用点忘了注入 → 英文界面残留中文**。新增/改造任何纯逻辑函数时，调用点必须显式传 `strings = localTideSpotStrings()`（Compose）或构造参数（非 Compose）。
  - 资源键中英双语**逐键对齐**由 `TideSpotStringsTest` + `EnStringsParityTest` 锁死，新增 key 必须两处同加。

## 4. 本次会话（11-11）完成的事

1. **修英文残留 4 类**（客户真机报障）：根因＝纯函数回落中文默认值。已修 `WaterBodyTabTitles.titlesFor`、`DaySelectorLogic.chips`、`Derivations.tabStatus/rowsFor`、`SourceMetaRegistry.granularity`→`DomainCopy.hourlyGranularity`，连带 `chipText`/`extremesLine`/`correctionCompare`/`Top10Formatter`/`SourceFooter`；重建分发 + 扫描 FAIL=0（CHG-44）。
2. **建立 V1.15 需求池**（`OPT_REQUIREMENTS_V115.md`）：GAP-05 剩余 7 条 + UI v2 设计包落地。
3. **刷新台账**：`PROGRESS_TRACKER.md` §9（i18n → ✅ 技术完成 / GAP-05 10/17）、新增 §10（V1.15 池）；`REQUIREMENTS_REGISTER.md` 追加 CHG-45。

## 5. 接手第一步（建议按此顺序）

1. `subst T: D:\workspace\天气鱼情` → `T:\tidespot-android` 跑 `.\gradlew.bat test` 确认基线绿；
2. `node .tools/i18n_scan.mjs` 确认 FAIL=0；
3. 执行 `OPT-V115-01`：7 条 key 逐条接线（**先做 1/3/5/6/7，改动小、无新 UI 控件依赖；2/4 需新绘制/新探测，放后面**），每条：加 `stringResource(R.string.ts_*)` → 跑扫描 → 更新对应锁测试；
4. GAP-05 7 条全绿后，按 `DECISIONS.md §6` 七项复核，把 GAP-05 记为**闭环**；
5. 再执行 `OPT-V115-02`（insets 收口 → token/组件对齐 → 9 屏真机截图）；
6. 升级版本号 `1.15.0 / versionCode=5` → `assembleRelease` → 分发 `E:\Trans` → 真机九屏中英+深色冒烟 → 回写 `PROGRESS_TRACKER §10` + 登记 CHG。

## 6. 硬约束（违反即返工）

- 设计红线：`ScoreBands` / `ConfidenceBadge` / `TideSpotTheme` **取值与主色冻结**，UI 更新只动布局/圆角/间距/字阶；免责恒 5 条（`TideSpotStringsTest` 断言）。
- 数据红线：「源更新时间」显示真实值或「—」，**禁止编造**（CONFLICT-03）；协议值（`UNGROUPED_KEY`、预设排序 key 等）**禁翻译**。
- BottomSheet 一律 `skipPartiallyExpanded=true` + **操作按钮置顶** + `navigationBarsPadding()`（贴底按钮不可点的真实故障）。
- 改任何 `:core:*` 公共签名 → 同步 `tidespot-android/CONTRACT.md`；新增用户可见文案 → 必须中英双资源 + 锁测试。
- 交付口径：功能 + 文档 + 台账 + 真机验证同时给到，不「只看编译过」。
---

## 7. V1.15 交付结果（2026-11-11，交接会话完成）

- **OPT-V115-01**：7 条 GAP-05 文案全部接线，**17/17 key 有引用**；`i18n_scan` FAIL=0；新增 `ts_stale_with_time`（中英双语，241/241 对齐）。
- **OPT-V115-02**：GAP-03 收口（`WindowCompat.setDecorFitsSystemWindows(false)` + `Scaffold(contentWindowInsets=0)` + 四屏/四 Sheet insets）；`TideSpotTheme` 新增 Shapes/Typography token（色值零改动）。
- **产物**：`versionCode=5` / `versionName=1.15.0`；`E:\Trans\潮点TideSpot-v1.15.0.apk`（51,916,184 B，SHA256 与源一致）；八模块 `test` 全绿。
- **遗留（下次）**：22 组件逐项像素校对；9 屏真机中英+深色截图；正文对比度 ≥4.5:1 实测；英文真机验收（`adb devices` 为空）。
- 台账：`REQUIREMENTS_REGISTER` **CHG-46**；`PROGRESS_TRACKER §10` 两项状态已回写；`design-handoff/DECISIONS.md §6.1` 复核结果已留证。
