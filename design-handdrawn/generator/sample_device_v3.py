"""D5 质感层量化验证：v2（质感前）vs v3（质感后）。

判据一：自动在 v2 上定位「最平的 64×64 纯背景块」，比较该块在 v3 的灰度标准差。
        纯色块 sd≈0；铺了纸纹颗粒后 sd 明显上升 → 颗粒真实绘制。
判据二：同坐标中位亮度。浅色纸底叠深墨颗粒应变暗、深色纸底叠暖白颗粒应变亮，
        且幅度应在 grain=0.16 可解释的范围内（≤25），方向相反即为回归。
"""
from pathlib import Path
from statistics import median, pstdev
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
D = ROOT / "design-handdrawn" / "A-vintage-outdoor" / "device-compare"
S = 3
PTS = {
    "页面背景(上)": (200, 100),
    "分数主卡": (150, 320),
    "图表区底": (200, 520),
    "DaySelector选中": (40, 145),
    "Tab栏底": (250, 225),
}
PAIRS = [
    ("内陆浅色", "v2-inland-light.png", "v3-inland-light.png", "darken"),
    ("内陆深色", "v2-inland-dark.png", "v3-inland-dark.png", "lighten"),
    ("海洋浅色", "v2-sea-light.png", "v3-sea-light.png", "darken"),
    ("海洋深色", "v2-sea-dark.png", "v3-sea-dark.png", "lighten"),
]


def im(f):
    return Image.open(D / f).convert("RGB")


def med(a, x, y):
    cx, cy = x * S, y * S
    px = list(a.crop((cx - 12, cy - 12, cx + 12, cy + 12)).get_flattened_data())
    return tuple(int(median(p[i] for p in px)) for i in range(3))


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def flat_patch(a):
    """在 v2 图上扫描，找灰度标准差最小的 64×64 块（避开文字/线条）。"""
    w, h = a.size
    gray = a.convert("L")
    best = (1e9, None)
    for y in range(260, h - 420, 70):
        for x in range(16, w - 180, 70):
            box = (x, y, x + 64, y + 64)
            sd = pstdev(list(gray.crop(box).get_flattened_data()))
            if sd < best[0]:
                best = (sd, box)
    return best


def sd_of(a, box):
    return pstdev(list(a.convert("L").crop(box).get_flattened_data()))


for tag, a_name, b_name, expect in PAIRS:
    a, b = im(a_name), im(b_name)
    print(f"\n=== {tag}（预期纸纹使画面{ '变暗' if expect == 'darken' else '变亮'}）===")

    sd2, box = flat_patch(a)
    sd3 = sd_of(b, box)
    verdict = "纸纹生效" if sd3 > sd2 + 1.5 else "未检出纸纹"
    print(f"  纯背景块 {box}:  sd {sd2:6.2f} → {sd3:6.2f}   {verdict}")

    print(f"  {'区域':18s} {'v2':>14s} {'v3':>14s}  Δ亮度  判定")
    for label, (x, y) in PTS.items():
        ca, cb = med(a, x, y), med(b, x, y)
        delta = lum(cb) - lum(ca)
        ok = (delta < 0 if expect == "darken" else delta > 0) and abs(delta) <= 26
        print(f"  {label:18s} "
              f"#{ca[0]:02X}{ca[1]:02X}{ca[2]:02X}       "
              f"#{cb[0]:02X}{cb[1]:02X}{cb[2]:02X}       "
              f"{delta:+6.1f}  {'符合纸纹覆盖' if ok else '需复核'}")
