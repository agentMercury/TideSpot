# APP 图标系统 · 设计侧规格

> 本目录的 4 份 SVG 是**可离线使用的设计源稿**。本包不产出可编译的 `res/` 资源；转成 Android vector drawable 由实现 agent 完成。

## 1. 母题与语义

**准星 × 潮位曲线**。

| 元素 | 含义 |
|---|---|
| 准星环 + 四向刻度 | F-1.3「准星是唯一选点真源」——产品最核心的交互契约 |
| 中心薄荷点 | 当前钓点 |
| 下方双潮线 | 潮汐 / 水位 |
| 蓝渐变底 | 工程主色体系 `#1976D2 → #0D47A1` |

小尺寸识别优先级：先读**圆环 + 中心点**，潮线降为辅助元素；24 dp 单色图标直接省略潮线。

## 2. 色值

| 用途 | 值 |
|---|---|
| 渐变起点 | `#1976D2` |
| 渐变终点 | `#0D47A1` |
| 潮线 / 中心点 | `#80CBC4` |
| 准星图形 | `#FFFFFF` |
| 主色（工程 token，参考） | `#1565C0` |
| 次色（工程 token，参考） | `#00695C` |

## 3. 自适应图标几何

| 项 | 值 |
|---|---|
| 画布 | 108 × 108 dp（foreground / background 两层同尺寸） |
| 安全区 | 72 × 72 dp，即中心 54,54、范围 `18…90` |
| Launcher 可视区 | 由系统遮罩裁切（常见约 66 dp 直径），**关键图形必须落在安全区内** |
| 外层圆角（预览 / 桌面方图标） | 44 dp @192 |
| 前景元素包围盒 | `x: 20…88 / y: 20…89`（含描边），全部落在安全区 |
| 描边 | 准星 4 dp、潮线 3.5 dp、端点 round |

`foreground.svg` 与 `background.svg` 均为透明/纯色底，可独立叠加；`preview.svg` 是合成后的 192 dp 预览。

## 4. 文件清单

| 文件 | 用途 | 目标资源 |
|---|---|---|
| `tidespot-icon-background.svg` | 蓝渐变底（108 dp） | `res/drawable/ic_launcher_background.xml` |
| `tidespot-icon-foreground.svg` | 准星 + 潮线（108 dp） | `res/drawable/ic_launcher_foreground.xml` |
| `tidespot-icon-monochrome.svg` | 24 dp 单色剪影 | `res/drawable/ic_launcher_monochrome.xml` + 通知小图标 |
| `tidespot-icon-preview.svg` | 192 dp 合成预览 | 设计评审 / 应用商店素材基准 |

## 5. 工程落点建议（由实现 agent 执行，本包不写代码）

**当前状态**：`app/src/main/AndroidManifest.xml` 仍使用 `@android:drawable/sym_def_app_icon`（系统默认图标），图标是空白项，必须替换。

建议结构：

```
app/src/main/res/
├── mipmap-anydpi-v26/ic_launcher.xml          <adaptive-icon> 引用三层 drawable
├── mipmap-anydpi-v26/ic_launcher_round.xml
├── drawable/ic_launcher_background.xml        vector drawable, 108dp / viewport 108
├── drawable/ic_launcher_foreground.xml        vector drawable, 108dp / viewport 108
├── drawable/ic_launcher_monochrome.xml        Android 13+ 主题图标（<adaptive-icon><monochrome>）
└── mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png   兼容旧版桌面图标
```

转换要点：

1. `<vector android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">`，路径坐标**一比一照抄** SVG，勿缩放。
2. 背景渐变可用 `<aapt:attr name="android:fillColor"><gradient …/></aapt:attr>`；若构建链不支持，退化为纯色 `#1565C0` 也是可接受降级（视觉差异需记录）。
3. `tidespot-icon-monochrome.svg` 是 24 dp 白剪影；adaptive icon 的 `android:monochrome` 需要 108 dp 版本，实现时把 24 dp 图形按 4.5× 等比放大到 108 dp 安全区内，保持白剪影。
4. `AndroidManifest.xml` 的 `android:icon` / `android:roundIcon` 指向 `@mipmap/ic_launcher`；通知 `setSmallIcon` 用单色小图标，禁用彩色位图。
5. 遵守项目约束：**不新增 XML layout，图标资源属 drawable/mipmap，不构成 layout**。

## 6. 校验清单

- [ ] 48 dp 桌面实际尺寸下，圆环与中心点仍可辨识（潮线允许弱化）
- [ ] 圆形 / 方形 / 圆角三种 launcher 遮罩下无关键元素被裁
- [ ] 深色桌面壁纸下蓝底与白色图形对比充足
- [ ] Android 13+ 主题图标（monochrome）显示为纯白剪影，无灰阶残留
- [ ] 通知栏 24 dp 显示清晰，不出现彩色块
- [ ] `AndroidManifest.xml` 已不再引用 `sym_def_app_icon`
- [ ] 与 `assets/icon-system.png` / `assets/icon-system.svg` 的 v2 效果稿一致

## 7. 视觉基准

`assets/icon-system.svg` / `assets/icon-system.png` 是用户已确认的 v2 图标系统效果稿（主图标、圆形遮罩、单色小图标、48 dp 桌面校验、规格说明）。本目录 SVG 是它的可编辑矢量源。
