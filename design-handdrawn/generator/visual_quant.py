# -*- coding: utf-8 -*-
"""S4：把「实际 UI 接不接近设计图」变成可量化门禁（CHG-93）。

两种用法：

1) 视觉 token 防漂移（无需真机，任何提交都可跑）:
       python design-handdrawn/generator/visual_quant.py --tokens
   比对 `design-handdrawn/A-vintage-outdoor/spec.json` 的 `visual_tokens`
   与 Android / 生成器里的真实常量，防止设计源改了、工程没跟（或反之）。

2) 纯色区对拍（需要真机截图）:
       python design-handdrawn/generator/visual_quant.py <design.png> <device.jpg> [--strict]
   在「本应纯色」的相对坐标区域采样 RGB 中位数、灰度标准差、颜色种类数。
   `--strict` 时按 UI差异逐页对拍-2026-11-15.md §5 S1 的阈值判定：
       - 灰度 std ≤ 设计稿 × 2
       - RGB 中位每通道差 ≤ 8
       - 颜色种类数 ≤ 设计稿 × 3
   旧真机截图（r2 期）跑 --strict 预期 FAIL；接入 USB 复拍后再用它把关。

依赖：Pillow。
"""
from __future__ import annotations

import json
import re
import statistics
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SPEC = ROOT / "design-handdrawn" / "A-vintage-outdoor" / "spec.json"
SKIN = ROOT / "tidespot-android" / "core" / "ui" / "src" / "main" / "kotlin" / "com" / "tidespot" / "core" / "ui" / "theme" / "TideSpotSkin.kt"
GEN = ROOT / "design-handdrawn" / "generator" / "gen_android_assets.py"
FONT = ROOT / "tidespot-android" / "core" / "ui" / "src" / "main" / "res" / "font" / "noto_serif_sc_subset.ttf"

# 相对坐标 (x0, y0, x1, y1)，取自对拍报告 §2；只保留「本应纯色」的背景区。
# 顶部区域含标题文字，std 天然很高，不纳入阈值判定。
SAMPLES = {
    "设置页左侧纸底": (0.005, 0.30, 0.035, 0.70),
    "收藏页下部背景": (0.06, 0.72, 0.42, 0.92),
}


def sample(path: Path, box: tuple[float, float, float, float]) -> dict:
    from PIL import Image

    im = Image.open(path).convert("RGB")
    w, h = im.size
    x0, y0, x1, y1 = box
    crop = im.crop((int(x0 * w), int(y0 * h), int(x1 * w), int(y1 * h)))
    px = list(crop.getdata())
    med = (
        int(statistics.median(p[0] for p in px)),
        int(statistics.median(p[1] for p in px)),
        int(statistics.median(p[2] for p in px)),
    )
    gray = [0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2] for p in px]
    return {"median": med, "std": round(statistics.pstdev(gray), 2),
            "colors": len(set(px)), "size": im.size}


def compare(design: Path, device: Path, strict: bool) -> int:
    fails = 0
    print(f"DESIGN {design.name}   DEVICE {device.name}")
    for name, box in SAMPLES.items():
        a, b = sample(design, box), sample(device, box)
        diff = tuple(abs(x - y) for x, y in zip(a["median"], b["median"]))
        ratio = b["std"] / a["std"] if a["std"] else 0.0
        print(f"  {name}")
        print(f"    设计 #{a['median'][0]:02X}{a['median'][1]:02X}{a['median'][2]:02X} "
              f"std={a['std']:.2f} colors={a['colors']}")
        print(f"    真机 #{b['median'][0]:02X}{b['median'][1]:02X}{b['median'][2]:02X} "
              f"std={b['std']:.2f} colors={b['colors']}  ΔRGB={diff} std×{ratio:.1f}")
        if strict:
            checks = {
                "底色色差 ≤8/通道": max(diff) <= 8,
                "灰度方差 ≤设计×2": ratio <= 2.0,
                "颜色数 ≤设计×3": b["colors"] <= a["colors"] * 3,
            }
            for k, v in checks.items():
                print(f"    {'PASS' if v else 'FAIL'}  {k}")
                if not v:
                    fails += 1
    print(f"VISUAL_CHECK {'FAIL=' + str(fails) if strict else '(report only)'}")
    return fails


def check_tokens() -> int:
    spec = json.loads(SPEC.read_text(encoding="utf-8"))
    if "visual_tokens" not in spec:
        print("FAIL  spec.json 缺 visual_tokens 节点")
        return 1
    v = spec["visual_tokens"]
    skin = SKIN.read_text(encoding="utf-8")
    gen = GEN.read_text(encoding="utf-8")
    checks = {
        "spec 声明 grainStrength 与工程一致": (
            f"grainStrength = {v['paper']['android_grain_strength']}f" in skin
        ),
        "工程确实引用字体子集": "R.font.noto_serif_sc_subset" in skin,
        "字体子集文件存在": FONT.exists() and FONT.stat().st_size > 100_000,
        "生成器 tile 为高频小尺寸": re.search(r"^TILE = (\d+)", gen, re.M) is not None
        and int(re.search(r"^TILE = (\d+)", gen, re.M).group(1)) <= 64,
        "生成器不含低频正弦项": "math.sin" not in gen,
        "纸纹画在内容之下": "fun Modifier.paperGrain" in (ROOT / "tidespot-android/core/ui/src/main/kotlin/com/tidespot/core/ui/theme/HandDrawn.kt").read_text(encoding="utf-8")
        and "drawBehind" in (ROOT / "tidespot-android/core/ui/src/main/kotlin/com/tidespot/core/ui/theme/HandDrawn.kt").read_text(encoding="utf-8"),
        "定稿节点声明唯一权威稿": len(spec.get("authoritative_design", {}).get("authoritative_detail", [])) == 2
        and len(spec.get("authoritative_design", {}).get("historical_not_target", [])) == 2,
    }
    fails = 0
    print("=== 视觉 token 防漂移（spec.json ↔ 工程） ===")
    for k, ok in checks.items():
        print(f"  {'PASS' if ok else 'FAIL'}  {k}")
        if not ok:
            fails += 1
    print(f"TOKEN_CHECK PASS={len(checks) - fails} FAIL={fails}")
    return fails


def main() -> None:
    args = sys.argv[1:]
    if args and args[0] == "--tokens":
        raise SystemExit(1 if check_tokens() else 0)
    if len(args) < 2:
        print(__doc__)
        raise SystemExit(0)
    strict = "--strict" in args
    raise SystemExit(1 if compare(Path(args[0]), Path(args[1]), strict) else 0)


if __name__ == "__main__":
    main()
