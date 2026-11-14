#!/usr/bin/env python3
"""生成「设计稿 | 真机」并排对拍图（皮肤视觉验收的可复现工具）。

用法（在仓库根目录或本目录均可）：
    python design-handdrawn/A-vintage-outdoor/device-compare/make_pairs.py

口径（写死在 PAIRS 里，防止再次出现「新旧稿混挂还标对齐」的事故）：
  - 左列必须是**权威新稿**：详情页用 screens_full/detail-tab-*，不用 screens/detail-*（早期 base 旧稿）。
  - 右列必须是**同一页面 / 同一明暗 / 同一水体**的真机截图。
  - 文件名带 old-vs-device 的那一组是**勘误演示**，专门用来说明「两列不是同一稿」，禁止当对齐证据。

输出：本目录 pairs/*.jpg（等高拼图，左 DESIGN / 右 DEVICE，顶部标注来源文件名）。
依赖：Pillow。仅做等比缩放 + 横向拼接，不做任何色彩/裁剪修饰，保证证据不被加工。
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent  # design-handdrawn/A-vintage-outdoor
OUT = HERE / "pairs"

# (输出名, 设计稿相对 ROOT, 真机截图相对 ROOT, 备注)
PAIRS = [
    ("01-settings-light", "screens/settings.png", "device-compare/r2-01-settings-light-v2.jpg", "设置 · 浅色"),
    ("02-map-light", "screens/map-inland.png", "device-compare/r2-00-launch.jpg", "地图 · 浅色"),
    ("03-map-dark", "screens_full/dark-map.png", "device-compare/r2-03-map-dark.jpg", "地图 · 深色（r2 期，底图尚未自动切深）"),
    ("03b-map-dark-latest", "screens_full/dark-map.png", "device-compare/r5-03-map-dark-follow.jpg", "地图 · 深色（v1.19 已自动切深）"),
    ("04-favorites-light", "screens/favorites.png", "device-compare/r2-08-favorites-light.jpg", "收藏 · 浅色"),
    ("05-favorites-dark", "screens_full/dark-favorites.png", "device-compare/r2-04-favorites-dark.jpg", "收藏 · 深色"),
    ("06-detail-sea-light", "screens_full/detail-tab-score-sea.png", "device-compare/r2-09-detail-sea-light.jpg", "详情 · 海洋浅色"),
    ("07-detail-sea-dark", "screens/dark-detail.png", "device-compare/r2-06-detail-sea-dark.jpg", "详情 · 海洋深色"),
    ("08-top10-sheet", "screens_full/ov01-top10.png", "device-compare/r5-12-top10-sheet.jpg", "Top10 叠层"),
    ("09-old-vs-device", "screens/detail-inland.png", "device-compare/v4-inland-light.jpg", "勘误：旧 base 稿 vs 新稿真机，非同稿"),
    ("10-settings-dark", "screens/settings.png", "device-compare/r2-02-settings-dark.jpg", "设置 · 深色（设计侧无深色设置稿，左列仅作结构参照）"),
]

HEIGHT = 1500
BAR = 30
GAP = 12


def resize(im: Image.Image) -> Image.Image:
    return im.resize((int(im.width * HEIGHT / im.height), HEIGHT), Image.LANCZOS)


def main() -> None:
    OUT.mkdir(exist_ok=True)
    for name, design, device, note in PAIRS:
        a = resize(Image.open(ROOT / design).convert("RGB"))
        b = resize(Image.open(ROOT / device).convert("RGB"))
        canvas = Image.new("RGB", (a.width + b.width + GAP, HEIGHT + BAR), (245, 245, 245))
        d = ImageDraw.Draw(canvas)
        d.text((8, 8), f"DESIGN  {design}", fill=(20, 20, 20))
        d.text((a.width + GAP + 8, 8), f"DEVICE  {Path(device).name}", fill=(20, 20, 20))
        canvas.paste(a, (0, BAR))
        canvas.paste(b, (a.width + GAP, BAR))
        canvas.save(OUT / f"{name}.jpg", quality=88)
        print(f"[pair] {name}.jpg  {note}")
    print(f"[done] {len(PAIRS)} pairs -> {OUT}")


if __name__ == "__main__":
    main()
