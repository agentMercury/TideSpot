# E · 潮汐手帐 / F · 蓝潮（Android 复刻说明）

对应 Web 定稿：`web/src/styles/fieldnotes.css`（E）、`web/src/styles/bluetide.css`（F）。
接入方式：只扩展 `TideSpotSkins` 注册表 + 复用统一票据卡骨架；Tab 顺序、五项免责、五档/六徽标色、DaySelector 52dp、水质 1×3 等冻结契约未改动。

## 1. 皮肤 token

### E · 潮汐手帐（`e-tidejournal`）
- 四套色板：与 D 共用纸/墨源色；海洋 tertiary 换为珊瑚 `#E5644C`，内陆保留琥珀 `#E9A13B`。
- 形状：`4 / 10 / 12 / 14 / 20`（卡 12、按钮 10、标签 20、浮层 14）。
- 字阶：标题/标签走既有 Noto Serif SC 子集（KaiTi 替身），正文系统无衬线；字号行高与基线一致。
- 质感：纸纹 `0.8`、套印投影 `0.9`（浅色实墨边）、手绘墨线、贴纸装饰全开。
- 选中态：M3 FilterChip 的 `secondaryContainer` 映射为墨色（深色下反转为纸色），保证设置页也是「墨底纸字」。
- 标志性组件：
  - 空心巨分数：珊瑚实心字 3dp 偏移打底 + 2.2dp 墨线描边，隐藏 `/100`。
  - 药丸 Tab：墨线胶囊，选中墨底纸字（深色下按 token 反转）。
  - 日期 chip：墨线 + 选中墨底；卡片 4dp 硬墨影。
  - 地图准星：墨线虚线环 + 四向刻度，无实心点。
  - 详情水印：珊瑚色鱼群，透明度略高于 A。

### F · 蓝潮（`f-bluetide`）
- 浅色：海蓝 `#1565C0` / 薄荷 `#00695C`；内陆青绿 `#00695C` / 天蓝 `#1565C0`；白卡 + `#EEF3F8` / `#EDF6F4` 底。
- 深色：`#90CAF9` / `#80CBC4`，深蓝黑底 `#0E1721` / `#0C1B18`，深色独立调校。
- 形状：`6 / 12 / 16 / 20 / 24`，大圆角现代卡。
- 字阶：系统无衬线，标题 700。
- 质感：无纸纹、无墨线、无贴纸；1dp 浅色 hairline + 2dp 柔和投影。
- 选中态：M3 FilterChip 的 `secondaryContainer` 映射为主色，设置页选中按钮为蓝底（深色下浅蓝底）。
- 标志性组件：
  - 评分主卡：`#1565C0 → #0D47A1` 深蓝渐变 + 白字，档位为白色 16% 药丸。
  - 药丸 Tab：浅底容器，选中白卡蓝字。
  - 日期 chip：选中主色蓝底白字。
  - 地图准星：薄荷虚线环 + 薄荷深色圆点，无纸感装饰。
  - Top10 入口：`#0D47A1` 深蓝实心横条。
  - 详情页隐藏鱼群水印与鱼形角标。

## 2. 代码落点
- `core/ui/theme/TideSpotSkin.kt`：E/F 四套色板、形状、字阶、质感与注册。
- `core/ui/theme/VintageCard.kt`：新增 `softShadow / softOutline / printShadowAlpha / sectionOrnament / dashedDivider` 质感开关；当前皮肤卡高亮 A 用芥末黄、E/F/D 用主色；A/D 默认值不变。
- `core/ui/selector/DaySelector.kt`：E 墨线药丸、F 蓝底选中；52dp 高度与最小 56dp 宽度未变。
- `feature/detail/.../DetailScreen.kt`：E 空心分数、E/F 药丸 Tab、F 深蓝评分卡与白字。
- `feature/map/.../MapScreen.kt`：E 刻度准星、F 薄荷准星、F 深蓝 Top10。
- `feature/favorites/.../FavoritesScreen.kt`：F 分数改无衬线；E 浅分数字体按字阶。
- `app/MainActivity.kt`：F 隐藏鱼群水印；E 水印改珊瑚。
- `feature/settings/.../SettingsScreen.kt`：修复 Map source / Skin 卡片标题重复渲染。

## 3. 验证
- 单测：`SkinRegistryTest` 新增 E/F 色板与质感断言；全部 `testDebugUnitTest` 通过。
- 真机（Honor AAK-AN00 / Android 16，1272×2800）与模拟器 Pixel 6 / Android 35：
  - 设置页四款皮肤均可选：复古户外 / 路亚图鉴 / 潮汐手帐 / 蓝潮。
  - E 浅色：`screenshots/phone-e-08-settings-light-final.png`、`phone-e-05-detail-light.png`。
  - E 深色：`screenshots/phone-e-06-settings-dark.png`、`phone-e-07-detail-dark.png`。
  - F 浅色：`screenshots/phone-f-08-settings-light-final.png`、`phone-f-02-map-light.png`、`phone-f-03-detail-light.png`。
  - F 深色：`screenshots/phone-f-07-settings-dark-final.png`、`phone-f-05-map-dark.png`、`phone-f-06-detail-dark.png`。
  - 真机缓存数据下已核对 88 分：E 空心分数（珊瑚偏移 + 墨线描边）、F 深蓝渐变评分卡均正常显示。
- 已核对：无手绘框与 Material 圆角双层描边；设置页无重复标题；E/F Tab、日期 chip、地图准星、Top10 无重叠。