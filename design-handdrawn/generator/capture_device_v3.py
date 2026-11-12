"""D5 手绘质感层真机复拍：内陆 + 近海 各浅/深两态，另留地图/收藏对照。
输出 v3-*.png，与 v2-*.png（质感层之前）同坐标，可直接做前后对比。"""
import re
import subprocess
import time
from pathlib import Path

ADB = r"C:\Users\gamer\AppData\Local\Android\Sdk\platform-tools\adb.exe"
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "design-handdrawn" / "A-vintage-outdoor" / "device-compare"
PKG = "com.tidespot.app"
XML, PNG = "/sdcard/ts_v3.xml", "/sdcard/ts_v3.png"


def run(a):
    return subprocess.run([ADB] + a, capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=90)


def sh(*a):
    return run(["shell", *a])


def pull(r, l):
    return run(["pull", r, str(l)])


def dump(tag):
    sh("uiautomator", "dump", XML)
    pull(XML, OUT / f"_{tag}.xml")
    t = (OUT / f"_{tag}.xml").read_text(encoding="utf-8", errors="replace")
    out = []
    for raw in re.findall(r"<node ([^>]+)/>", t):
        d = dict(re.findall(r"([\w-]+)=\"([^\"]*)\"", raw))
        d["label"] = (d.get("text", "") or d.get("content-desc", "")).strip()
        out.append(d)
    return out


def ctr(n):
    v = [int(x) for x in re.findall(r"\d+", n.get("bounds", ""))]
    return ((v[0] + v[2]) // 2, (v[1] + v[3]) // 2) if len(v) >= 4 else None


def tap(n, why=""):
    c = ctr(n)
    sh("input", "tap", str(c[0]), str(c[1]))
    print(f"  [TAP] {why} @ {c}")
    time.sleep(2.0)


def shot(name):
    for _ in range(3):
        r = sh("screencap", "-p", PNG)
        if "Failed" not in (r.stdout + r.stderr):
            break
        time.sleep(1.0)
    pull(PNG, OUT / f"{name}.png")
    print(f"  [SHOT] {name}.png  {(OUT / f'{name}.png').stat().st_size} bytes")


def water(nodes):
    s = " ".join(n.get("label", "") for n in nodes)
    if "水位（无潮汐）" in s or "Water Level (no tide)" in s:
        return "INLAND"
    if "潮流" in s or "潮高" in s or "Tide" in s:
        return "SEA"
    return "UNKNOWN"


def find(nodes, key, desc_only=True):
    for n in nodes:
        if key in (n.get("content-desc", "") if desc_only else n.get("label", "")):
            return n
    return None


def open_favorites(prefix):
    sh("am", "force-stop", PKG)
    run(["shell", "monkey", "-p", PKG, "-c", "android.intent.category.LAUNCHER", "1"])
    time.sleep(4.5)
    n = dump(f"{prefix}-map")
    shot(f"{prefix}-map")
    fav = find(n, "收藏")
    tap(fav, "收藏入口")
    n = dump(f"{prefix}-favorites")
    shot(f"{prefix}-favorites")
    return n


def capture_spot(prefix, spot_key):
    nodes = open_favorites(prefix)
    item = find(nodes, spot_key)
    if not item:
        print(f"  [FAIL] 找不到收藏点 {spot_key}")
        return None
    tap(item, f"打开 {spot_key}")
    n = dump(f"{prefix}-light")
    w = water(n)
    print(f"  [WATER] {spot_key} = {w}")
    (OUT / f"_{prefix}-water.txt").write_text(w, encoding="utf-8")
    shot(f"{prefix}-light")
    sh("cmd", "uimode", "night", "yes")
    time.sleep(2.5)
    dump(f"{prefix}-dark")
    shot(f"{prefix}-dark")
    sh("cmd", "uimode", "night", "no")
    time.sleep(1.5)
    return w


def main():
    t = sh("dumpsys", "trust").stdout
    m = re.search(r"deviceLocked=(\d)", t)
    if m and m.group(1) == "1":
        print("BLOCKED: 设备锁屏，请解锁后重跑")
        return 2
    sh("input", "keyevent", "224")
    time.sleep(1.0)

    print("=== 内陆点：环球贸易中心 ===")
    capture_spot("v3-inland", "收藏点 环球贸易中心")
    print("=== 近海点：金湾区 ===")
    capture_spot("v3-sea", "收藏点 金湾区")

    print("\nDONE")
    for p in sorted(OUT.glob("v3-*.png")):
        print("  ", p.name, p.stat().st_size)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
