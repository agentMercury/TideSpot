# -*- coding: utf-8 -*-
"""生成 A · 复古户外皮肤使用的中文衬线字体子集（可复现）。

来源：Noto Serif SC Variable（Adobe / Google，SIL Open Font License 1.1）
  - 本机 `C:/Windows/Fonts/NotoSerifSC-VF.ttf`，或 `--source` 指定同款静态/可变 TTF
  - 授权声明取自字体 nameID 13 / 14：SIL OFL 1.1，允许商用与再分发，
    但再分发须随附 OFL 许可文本且不得单独售卖字体本身。
    本工程随附许可见 `tidespot-android/CONTRACT.md` 的 CHG-93 条款。

为什么必须子集化：原可变字体 24 MB / 31058 字形，直接打进 APK 会让包体
增加约 24 MB；且默认 wght=200（ExtraLight），与字阶里的 Normal/Bold 不符。
本脚本把可变轴实例化到 wght=400，再按工程真实用到的字符做子集。

输出：core/ui/src/main/res/font/noto_serif_sc_subset.ttf
复现：python design-handdrawn/generator/gen_font_subset.py
依赖：pip install fonttools brotli
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

from fontTools import subset
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
ANDROID = ROOT / "tidespot-android"
OUT = ANDROID / "core" / "ui" / "src" / "main" / "res" / "font" / "noto_serif_sc_subset.ttf"
DEFAULT_SOURCE = Path(r"C:\Windows\Fonts\NotoSerifSC-VF.ttf")

# 常用中文标点 / 单位 / 符号（字体里不一定被 strings.xml 覆盖，显式补齐）
EXTRA = (
    "，。！？；：、·「」『』（）【】《》—…～％‰℃°′″"
    "＋－±×÷＝≠≤≥≈°㎡"
    "①②③④⑤⑥⑦⑧⑨⑩"
    "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    " .,:;!?()[]{}<>/\\-_+=*&%$#@~^'\"|"
)


def collect_chars() -> str:
    """工程内所有可能上屏的字符：res 文案 + Kotlin 字符串/注释里的中文。"""
    chars: set[str] = set(EXTRA)
    patterns = ("*.xml", "*.kt")
    roots = [ANDROID / "app" / "src" / "main", ANDROID / "core", ANDROID / "feature"]
    for base in roots:
        if not base.exists():
            continue
        for pat in patterns:
            for p in base.rglob(pat):
                if "build" in p.parts:
                    continue
                try:
                    text = p.read_text(encoding="utf-8", errors="replace")
                except OSError:
                    continue
                for ch in text:
                    if ord(ch) >= 0x2E80:  # CJK / 全角标点 / 其它上屏符号
                        chars.add(ch)
    return "".join(sorted(chars))


def main() -> None:
    src = Path(sys.argv[1]) if len(sys.argv) > 1 else DEFAULT_SOURCE
    if not src.exists():
        raise SystemExit(f"font source not found: {src}")
    chars = collect_chars()
    print(f"source     : {src} ({src.stat().st_size / 1024 / 1024:.1f} MB)")
    print(f"characters : {len(chars)} (CJK {sum(1 for c in chars if 0x4E00 <= ord(c) <= 0x9FFF)})")

    font = TTFont(str(src))
    if "fvar" in font:
        # 默认轴是 ExtraLight(200)，必须固定到 Regular(400) 才与 Typography 字阶一致
        font = instantiateVariableFont(font, {"wght": 400}, inplace=True, optimize=True)

    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = [1, 2, 4, 6, 13, 14]
    opts.name_legacy = True
    opts.notdef_outline = True
    opts.recalc_bounds = True
    sub = subset.Subsetter(options=opts)
    sub.populate(text=chars)
    sub.subset(font)

    OUT.parent.mkdir(parents=True, exist_ok=True)
    font.save(str(OUT))
    print(f"output     : {OUT.relative_to(ROOT).as_posix()} ({OUT.stat().st_size / 1024:.0f} KB)")
    print(f"glyphs     : {font['maxp'].numGlyphs}")


if __name__ == "__main__":
    main()
