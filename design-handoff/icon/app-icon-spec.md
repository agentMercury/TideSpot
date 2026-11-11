# APP 图标系统 · 设计侧规格 · v3

> 本目录 5 份 SVG 是**可离线使用的设计源稿**。本包不产出可编译 `res/` 资源；转 Android vector drawable 由实现 agent 完成。
> 工程现状：**图标已落地**（`@mipmap/ic_launcher`，三层 drawable + round 五件齐全，GAP-01 已关闭）。本版是**视觉升级源稿**，替换现有 drawable 内容即可，manifest 指向无需改。

## 1. 母题与语义（v3 升级）

**准星（选点真源）× 双潮线（潮汐）**，与地图准星 idle 态同源。

| 元素 | 含义 |
|---|---|
| 细定位环 r21（stroke 2.6 / opacity .92） | 可拖动 / 可定位的选点范围 |
| 四向刻度（4 段，落安全区 18..90） | F-1.3「准星是唯一选点真源」——保留工程内核 |
| 薄荷中心点 r6.4 + 白芯 r2 | 当前钓点 / 品牌点缀 `#80CBC4` |
| 背景双潮线（两条正弦） | 潮汐 / 水位；从前景层移到**背景层**，避免小尺寸与刻度打架 |
| 蓝渐变底 | 工程主色体系 `#1976D2 → #0D47A1` |

**v3 相对 v2 的变化**：圆环 r22 → r21、中心 cy48 → cy50、刻度改「穿越环的四段长刻度」并内收进 72dp 安全区、薄荷点 r5.5 → r6.4 加白芯、双潮线由前景移到背景层、单色层去掉环只留刻度 + 中心点。

小尺寸识别优先级：先读**刻度 + 中心点**，潮线降为辅助；24 dp 单色图标直接省略潮线与环。

## 2. 色值

| 用途 | 值 |
|---|---|
| 渐变起点 / 终点 | `#1976D2` / `#0D47A1` |
| 潮线 / 中心点 | `#80CBC4` |
| 准星图形 | `#FFFFFF` |
| 主色 / 次色（工程 token，参考） | `#1565C0` / `#00695C` |

## 3. 自适应图标几何

| 项 | 值 |
|---|---|
| 画布 | 108 × 108 dp（foreground / background 两层同尺寸，monochrome-108 同） |
| 安全区 | 72 × 72 dp，中心 54,54、范围 `18…90` |
| Launcher 可视区 | 系统遮罩裁切（约 66 dp 直径）；关键图形必须落在安全区 |
| 外层圆角（预览 / 桌面方图标） | 44 dp @192 |
| 前景包围盒 | `x: 18…90 / y: 18…82`（含描边），全部落在安全区 |
| 描边 | 刻度 3.6 dp、环 2.6 dp、端点 round；背景潮线 3.2 / 2.2 dp |

`foreground.svg` / `background.svg` 可独立叠加；`preview.svg` 是 192 dp 合成预览；`monochrome.svg` 是 24 dp 通知小图标，`monochrome-108.svg` 是 Android 13+ adaptive `<monochrome>` 用的 108 dp 版本。

## 4. 文件清单

| 文件 | 用途 | 目标资源（替换现有） |
|---|---|---|
| `tidespot-icon-background.svg` | 蓝渐变底 + 双潮线（108 dp） | `res/drawable/ic_launcher_background.xml` |
| `tidespot-icon-foreground.svg` | 细环 + 四向刻度 + 薄荷点 + 白芯（108 dp） | `res/drawable/ic_launcher_foreground.xml` |
| `tidespot-icon-monochrome.svg` | 24 dp 单色剪影 | 通知 `setSmallIcon` 小图标 |
| `tidespot-icon-monochrome-108.svg` | 108 dp 单色层 | `mipmap-anydpi-v26/ic_launcher.xml` 的 `<monochrome>` |
| `tidespot-icon-preview.svg` | 192 dp 合成预览 | 设计评审 / 应用商店素材基准 |

## 5. 工程落点（现状 + 替换动作）

**当前状态**：`app/src/main/AndroidManifest.xml:13 android:icon="@mipmap/ic_launcher"`；已存在：
```
app/src/main/res/
├── mipmap-anydpi-v26/ic_launcher.xml / ic_launcher_round.xml   ← <adaptive-icon> 引用三层
├── drawable/ic_launcher_background.xml
├── drawable/ic_launcher_foreground.xml
└── drawable/ic_launcher_monochrome.xml
```
**本次动作**：用 v3 SVG 重新转换并**替换**上述 4 个 drawable 的内容；`ic_launcher.xml` 增加/指向 `<monochrome>`；manifest 指向不变。

转换要点：
1. `<vector android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">`，路径坐标**一比一照抄** SVG，勿缩放。
2. 背景渐变可用 `<aapt:attr name="android:fillColor"><gradient …/></aapt:attr>`；若构建链不支持，退化为纯色 `#1565C0`（视觉差异需记录）。
3. `<monochrome>` 取 `monochrome-108.svg`（108 dp 白剪影），通知小图标取 `monochrome.svg`（24 dp）。
4. 遵守项目约束：**不新增 XML layout，图标资源属 drawable/mipmap，不构成 layout**。

## 6. 校验清单

- [ ] 48 dp 桌面实际尺寸下，刻度 + 中心点仍可辨识（潮线允许弱化）
- [ ] 圆形 / 方形 / 圆角三种 launcher 遮罩下无关键元素被裁（包围盒 18..90）
- [ ] 深色桌面壁纸下蓝底与白色图形对比充足
- [ ] Android 13+ 主题图标（monochrome）显示为纯白剪影，无灰阶残留
- [ ] 通知栏 24 dp 显示清晰，不出现彩色块
- [ ] `AndroidManifest.xml` 已不再引用 `sym_def_app_icon`（当前已满足）
- [ ] 与 `assets/board-v3.png` 整板「01 APP 图标区」及 `assets/v3/screen-01-map.png` 的准星 idle 态同源

## 7. 视觉基准

`assets/board-v3.png`（整板 01 图标区：合成 / 三层 / 48dp 桌面 / 准星三态）与 `assets/v3/screen-01-map.png`（准星 idle 态）为已确认基准；本目录 SVG 是它们的可编辑矢量源。
