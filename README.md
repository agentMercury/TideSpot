# 潮点 TideSpot

离线优先的钓鱼指数 App：地图选点 → 潮汐 / 天气 / 海洋多源聚合 → 五维评分与最佳窗口。
工程在 `tidespot-android/`（多模块 Gradle + Kotlin + Jetpack Compose）；设计稿与换肤手册在 `design-handdrawn/`。

## 快速开始

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot'
.\.tools\gradle-8.9\bin\gradle.bat ':app:assembleRelease' -p tidespot-android   # 出包
.\tidespot-android\verify.ps1            # 八模块测试 + 红线扫描
node .tools\i18n_scan.mjs                # 文案门禁：必须 FAIL=0
node .tools\contrast_check.mjs           # 对比度门禁：必须 0 FAIL
```

真机验证统一装 **release** 包（`adb install -r`；release 与 debug 签名不兼容）。`adb` 不在 PATH：`C:\Users\gamer\AppData\Local\Android\Sdk\platform-tools\adb.exe`。

## 文档怎么读（别从旧文档开始）

| 顺序 | 文件 | 作用 |
|---|---|---|
| 1 | `HANDOVER_V115.md` **§0 文档维护契约** | **先读这条**：改代码必须同批更新哪些文档、权威顺序、收工自检 |
| 2 | `HANDOVER_V115.md` §1–§7 | 基线、环境红线、已交付清单、已知偏离 |
| 3 | `tidespot-android/CONTRACT.md` | 模块契约与冻结点（`:core:*` 公共签名不得随意改） |
| 4 | `design-handdrawn/A-vintage-outdoor/SKIN-DEV-STANDARD.md` | 换肤 / 新增皮肤的执行手册 |
| 5 | `design-handdrawn/A-vintage-outdoor/未开始需求清单.md` | 需求状态与「落地结果」 |
| 6 | `PROGRESS_TRACKER.md` | 里程碑台账（**可能滞后，以代码为准**） |

其余 `*.md` 多为历史批次的需求/改进记录，**不要用它们判断当前架构**。

## 版本号（每次发版必改，不允许跳）

改 `tidespot-android/app/build.gradle.kts` 的 `versionCode`（每次 **+1**，覆盖安装依据）与 `versionName`：

| 改动性质 | 怎么加 | 例子 |
|---|---|---|
| 小版本：修 bug、文案/样式微调，**无功能变化** | **第二个 `.` 之后 +1** | `1.16.0` → `1.16.1` |
| 有功能修改 / 新增 | **第一个 `.` 之后 +1，末位归零** | `1.16.1` → `1.17.0` |
| 重大更新 | **第一位 +1**，且**必须经客户明确认证** | `1.17.0` → `2.0.0`（未经授权不得动） |

细则与「发版必做清单」见 `HANDOVER_V115.md` §0.6。产物名随 `versionName` 自动跟随（`潮点TideSpot-v{versionName}.apk`）。

## 提交

jj 与 git colocated：jj 建提交 → `git update-ref refs/heads/main <commit>` 同步分支指针（无远端，`main` 即本地主线）。
