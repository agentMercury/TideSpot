#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""CHG-95 / r8 真机验收并排图（v1.21.0 主体 + v1.21.1 下划线修复复拍）。

用法（在仓库根目录或本目录均可）：
    python design-handdrawn/A-vintage-outdoor/device-compare/make_pairs_r8.py

口径（与 make_pairs.py 一致，防止新旧稿混挂）：
  - 左列是**权威设计稿**（详情页用 screens_full/detail-tab-score-*，不用 screens/detail-*）；
  - 右列是**同页面 / 同明暗 / 同水体**的 r8 真机 760px JPG；
  - 第 6 组是「修复前 vs 修复后」的 Tab 栏放大对比，**不是设计稿对照**，专门说明
    CHG-95 r8 修掉的「选中下划线未上屏」缺陷。

依赖：Pillow。只做等比缩放 + 横向拼接，不调色、不裁剪，保证证据未被加工。
"""
from pathlib import Path
from PIL import Image, ImageDraw

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
OUT = HERE / "pairs-r8"
OUT.mkdir(exist_ok=True)

# (输出名, 左图相对 ROOT, 右图相对 ROOT, 顶部标注)
PAIRS = [
    ("01-settings-light", "screens/settings.png",
     "device-compare/r8/r8-14-settings-light-fixed.jpg", "DESIGN settings.png | DEVICE r8 settings light v1.21.1"),
    ("02-favorites-light", "screens/favorites.png",
     "device-compare/r8/r8-03-favorites-light.jpg", "DESIGN favorites.png | DEVICE r8 favorites light"),
    ("03-favorites-dark", "screens_full/dark-favorites.png",
     "device-compare/r8/r8-11-favorites-dark.jpg", "DESIGN dark-favorites.png | DEVICE r8 favorites dark"),
    ("04-detail-light", "screens_full/detail-tab-score-sea.png",
     "device-compare/r8/r8-15-detail-light-fixed.jpg", "DESIGN detail-tab-score-sea | DEVICE r8 detail light v1.21.1"),
    ("05-detail-dark", "screens/dark-detail.png",
     "device-compare/r8/r8-13-detail-dark-fixed.jpg", "DESIGN dark-detail.png | DEVICE r8 detail dark v1.21.1"),
    ("06-tab-underline-fix", "device-compare/r8/crop-tab-07-detail-light.jpg",
     "device-compare/r8/crop-r8-15-detail-light-fixed.jpg",
     "BEFORE v1.21.0 (no underline) | AFTER v1.21.1 (underline) -- NOT a design comparison"),
]


def build(name: str, left: Path, right: Path, note: str) -> None:
    a, b = Image.open(left).convert("RGB"), Image.open(right).convert("RGB")
    h = min(a.height, b.height)
    a = a.resize((round(a.width * h / a.height), h), Image.LANCZOS)
    b = b.resize((round(b.width * h / b.height), h), Image.LANCZOS)
    bar = 26
    out = Image.new("RGB", (a.width + b.width + 8, h + bar), "#F2EAD2")
    out.paste(a, (0, bar))
    out.paste(b, (a.width + 8, bar))
    ImageDraw.Draw(out).text((6, 7), note, fill="#1B2A3A")
    out.save(OUT / f"{name}.jpg", quality=90, optimize=True)
    print(f"  {name}: {out.width}x{out.height}")


def main() -> None:
    for name, left, right, note in PAIRS:
        lp, rp = ROOT / left, ROOT / right
        if not lp.exists() or not rp.exists():
            raise SystemExit(f"缺少对照文件：{lp if not lp.exists() else rp}")
        build(name, lp, rp, note)
    print(f"OK -> {OUT}")


if __name__ == "__main__":
    main()
