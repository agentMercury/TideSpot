# -*- coding: utf-8 -*-
"""
生成「手绘 A · 复古户外」皮肤在 Android 工程侧使用的 drawable 资源。

设计侧单源：design-handdrawn/generator/engine.py
  - 纸纹参数 STYLES['a-vintage']['paper'] = {grain:0.16, base_freq:'0.82'}
  - 鱼形 / 水体母题路径 fish_svg('a-vintage', ...) / water_motif('a-vintage', ...)

输出到 :core:ui（皮肤资源归属皮肤所在模块，见 SKIN-DESIGN-SPEC.md）：
  core/ui/src/main/res/drawable-nodpi/paper_grain_light.png   浅色纸底：深墨颗粒
  core/ui/src/main/res/drawable-nodpi/paper_grain_dark.png    深色纸底：暖白颗粒
  core/ui/src/main/res/drawable/ic_fish_vintage.xml           版画鱼剪影（可 tint）
  core/ui/src/main/res/drawable/ic_wave_vintage.xml           海洋母题：双浪 + 贝 + 海鸟
  core/ui/src/main/res/drawable/ic_mount_vintage.xml          内陆母题：尖山 + 日轮 + 芦苇

约束：
  - 所有资源单色/可 tint，不写死任何皮肤配色，交由 MaterialTheme 决定海/陆 × 浅/深
  - tile 为高频细噪，平铺无接缝；alpha 上限对齐设计稿 grain=0.16
  - CHG-93：旧版 96px 低频正余弦 + 高对比墨色颗粒在真机形成可见规则网格并压灰纸底，
    现改为「高频同色细噪 + 内容之下叠加」，量化基线见 device-compare/UI差异逐页对拍-2026-11-15.md §2
  - 本脚本只写 res 资源，不改任何 Kotlin 源码

复现：python design-handdrawn/generator/gen_android_assets.py
"""
from __future__ import annotations

import hashlib
import random
from pathlib import Path

from PIL import Image

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
RES = ROOT / "tidespot-android" / "core" / "ui" / "src" / "main" / "res"
NODPI = RES / "drawable-nodpi"
DRAWABLE = RES / "drawable"

# 设计稿 svg_defs 的 feTurbulence seed=11 / fractalNoise numOctaves=3 / baseFreq 0.82
# CHG-93：旧实现的 96px 低频项在 nodpi 平铺时形成肉眼可见的规则网格，
# 高对比墨绿/暖白颗粒又整体压暗去饱和（真机灰度方差 7~16 倍于设计稿）。
SEED = 11
TILE = 64
# design-handdrawn/generator/engine.py: STYLES['a-vintage']['paper']['grain'] == 0.16
GRAIN = 0.16
ALPHA_LIGHT = round(255 * GRAIN)        # 41（运行时再由 grainStrength 收敛）
ALPHA_DARK = round(255 * GRAIN * 0.82)  # 33

# 颗粒色改「纸底同色系 ±5%」：浅色取海 #E4D9BC 与内陆 #DCE2C4 的中值，
# 深色取 #122334 系。只产生极轻微明暗起伏，不改变底色色相与饱和度。
INK_LIGHT = (205, 197, 168)
INK_DARK = (30, 47, 64)


def grain_tile(dark: bool) -> Image.Image:
    """高频同色细噪 tile（CHG-93）：逐像素独立随机 + 多尺度邻域相关，平铺无缝。

    对齐设计稿 `feTurbulence fractalNoise baseFrequency=0.82`（特征尺度 1~2px），
    不叠加任何低频正余弦项。邻域索引取模保证 tile 上下左右无缝。
    """
    rnd = random.Random(SEED)
    ink = INK_DARK if dark else INK_LIGHT
    max_a = ALPHA_DARK if dark else ALPHA_LIGHT
    noise = [[rnd.random() for _ in range(TILE)] for _ in range(TILE)]
    img = Image.new("RGBA", (TILE, TILE))
    px = img.load()
    for y in range(TILE):
        for x in range(TILE):
            # 3 阶分形近似：本像素为主，叠加 2×2 / 3×3 邻域均值
            n0 = noise[y][x]
            n1 = sum(noise[(y + dy) % TILE][(x + dx) % TILE]
                     for dy in (0, 1) for dx in (0, 1)) / 4.0
            n2 = sum(noise[(y + dy) % TILE][(x + dx) % TILE]
                     for dy in (0, 2) for dx in (0, 2)) / 4.0
            v = 0.70 * n0 + 0.22 * n1 + 0.08 * n2
            a = int(max(0.0, min(1.0, v)) * max_a)
            px[x, y] = (ink[0], ink[1], ink[2], a)
    return img


# ── vector drawable ────────────────────────────────────────────────────────
# SVG path d 与 Android vector pathData 语法兼容（M/L/Q/Z/A/C 均支持）；
# 设计稿里的 SVG filter（feDisplacementMap 毛边）无法直接迁移，由
# HandDrawn.kt 的 handDrawnOutline() 在 Compose 侧近似还原。
FISH_BODY = "M22 32 Q46 10 92 27 Q100 30 92 34 Q46 54 22 32 Z"
FISH_TAIL = "M22 32 L4 16 L10 32 L4 48 Z"
FISH_FIN = "M60 15 Q66 6 76 14 Z"

# 海洋：双层浪 + 贝 + 海鸟（engine.py water_motif 'sea'）
WAVE_1 = "M2 30 Q12 21 22 30 Q32 39 42 30 Q52 21 62 30 Q70 37 78 30"
WAVE_2 = "M2 41 Q12 32 22 41 Q32 50 42 41 Q52 32 62 41 Q70 48 78 41"
WAVE_SHELL = "M50 24 Q56 9 62 24 Z"
WAVE_BIRD = "M66 20 Q71 15 76 20 Q71 25 66 20"

# 内陆：尖山 + 日轮 + 水线 + 芦苇（engine.py water_motif 'inland'）
MOUNT_RIDGE = "M2 42 L20 12 L33 32 L47 6 L66 36 L78 22"
MOUNT_WATER = "M2 45 Q40 39 78 45"
MOUNT_REED = "M12 45 Q9 28 14 18 M14 45 Q18 30 13 21"


def vector_xml(
    viewport_w: int,
    viewport_h: int,
    paths: list[tuple[str, str]],
    comment: str,
) -> str:
    """paths: [(pathData, 类型)] 类型 = fill | stroke | fillStroke(带细描边)"""
    lines = [
        '<?xml version="1.0" encoding="utf-8"?>',
        f"<!-- {comment} -->",
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        f'    android:width="{viewport_w}dp"',
        f'    android:height="{viewport_h}dp"',
        f'    android:viewportWidth="{viewport_w}"',
        f'    android:viewportHeight="{viewport_h}">',
    ]
    for data, kind in paths:
        if kind == "fill":
            lines.append(
                f'    <path android:pathData="{data}" android:fillColor="#FF000000"/>'
            )
        elif kind == "fillStroke":
            lines.append(
                f'    <path android:pathData="{data}" android:fillColor="#FF000000" '
                'android:strokeColor="#FF000000" android:strokeWidth="1.8" '
                'android:strokeLineJoin="round"/>'
            )
        else:
            lines.append(
                f'    <path android:pathData="{data}" android:fillColor="#00000000" '
                'android:strokeColor="#FF000000" android:strokeWidth="2.6" '
                'android:strokeLineCap="round" android:strokeLineJoin="round"/>'
            )
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


ASSETS: list[tuple[Path, str | Image.Image, str]] = []


def build() -> None:
    NODPI.mkdir(parents=True, exist_ok=True)
    DRAWABLE.mkdir(parents=True, exist_ok=True)

    ASSETS.append((NODPI / "paper_grain_light.png", grain_tile(False), "浅色纸纹 tile"))
    ASSETS.append((NODPI / "paper_grain_dark.png", grain_tile(True), "深色纸纹 tile"))

    ASSETS.append((
        DRAWABLE / "ic_fish_vintage.xml",
        vector_xml(120, 64, [
            (FISH_BODY, "fill"),
            (FISH_TAIL, "fill"),
            (FISH_FIN, "fill"),
        ], "手绘 A · 丝网版画鱼剪影（engine.py fish_svg a-vintage）；运行时由 Icon tint 着色"),
        "版画鱼剪影",
    ))
    ASSETS.append((
        DRAWABLE / "ic_wave_vintage.xml",
        vector_xml(80, 48, [
            (WAVE_1, "stroke"),
            (WAVE_2, "stroke"),
            (WAVE_SHELL, "fillStroke"),
            (WAVE_BIRD, "stroke"),
        ], "手绘 A · 海洋母题：双层浪 + 贝 + 海鸟（engine.py water_motif sea）"),
        "海洋母题",
    ))
    ASSETS.append((
        DRAWABLE / "ic_mount_vintage.xml",
        vector_xml(80, 48, [
            (MOUNT_RIDGE, "stroke"),
            (MOUNT_WATER, "stroke"),
            (MOUNT_REED, "stroke"),
            ("M55.5 12 A6.5 6.5 0 1 0 68.5 12 A6.5 6.5 0 1 0 55.5 12 Z", "stroke"),  # 日轮轮廓
        ], "手绘 A · 内陆母题：尖山 + 日轮 + 水线 + 芦苇（engine.py water_motif inland）"),
        "内陆母题",
    ))

    for path, payload, label in ASSETS:
        if isinstance(payload, Image.Image):
            payload.save(path, format="PNG", optimize=True)
        else:
            path.write_text(payload, encoding="utf-8", newline="\n")
        raw = path.read_bytes()
        print(f"  OK  {path.relative_to(ROOT).as_posix():78s} "
              f"{len(raw):6d} B  sha256={hashlib.sha256(raw).hexdigest()[:12]}  {label}")

    print(f"\n共 {len(ASSETS)} 个资源。全部单色可 tint，不含任何皮肤配色常量。")


if __name__ == "__main__":
    build()
