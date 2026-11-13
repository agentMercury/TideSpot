# -*- coding: utf-8 -*-
"""通用皮肤站自检：30 屏 PNG/HTML + 冻结契约 + 色板 + 索引 + spec。

用法：python selfcheck_style.py D-field-guide
"""
import sys, json, hashlib, struct, re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SITE = ROOT / (sys.argv[1] if len(sys.argv) > 1 else "A-vintage-outdoor")
spec = json.loads((SITE / "spec.json").read_text(encoding="utf-8"))
PASS = FAIL = 0


def ok(cond, msg):
    global PASS, FAIL
    if cond:
        PASS += 1
    else:
        FAIL += 1
        print("  FAIL:", msg)


def sha(p):
    return hashlib.sha256(p.read_bytes()).hexdigest()


# 1) 30 PNG：签名 + 780x1688 + spec SHA
pngs = list((SITE / "screens").glob("*.png")) + list((SITE / "screens_full").glob("*.png"))
ok(len(pngs) == 30, f"PNG 总数=30（实际 {len(pngs)}）")
for p in pngs:
    b = p.read_bytes()
    ok(b[:8] == b"\x89PNG\r\n\x1a\n", f"{p.name} PNG 签名")
    w, h = struct.unpack(">II", b[16:24])
    ok((w, h) == (780, 1688), f"{p.name} 尺寸 780x1688（实 {w}x{h}）")
for e in spec["screens"]:
    ok(sha(SITE / e["png"]) == e["sha256"], f'{e["key"]} PNG SHA')
    ok(sha(SITE / e["html"]) == e["html_sha256"], f'{e["key"]} HTML SHA')
for e in spec["full_ui"]["added_screens"]:
    ok(sha(SITE / e["png"]) == e["sha256_png"], f'{e["key"]} PNG SHA')
    ok(sha(SITE / e["html"]) == e["sha256_html"], f'{e["key"]} HTML SHA')

# 2) 30 HTML 自包含
htmls = list((SITE / "sources").glob("*.html")) + list((SITE / "sources_full").glob("*.html"))
ok(len(htmls) == 30, f"HTML 总数=30（实际 {len(htmls)}）")
for p in htmls:
    t = p.read_text(encoding="utf-8")
    ok("http://" not in t and "https://" not in t, f"{p.name} 无网络外链")
    ok("file://" not in t, f"{p.name} 无 file://")
    imgs = re.findall(r'<img[^>]+src="([^"]+)"', t)
    ok(all(s.startswith("data:") for s in imgs), f"{p.name} 无相对路径 img")
    ok("<svg" in t and "feTurbulence" in t, f"{p.name} 含手绘滤镜 SVG")

# 3) 冻结契约（结构/文案/交互不能随皮肤变）
detail = "\n".join(p.read_text(encoding="utf-8") for p in htmls)
for term in ["钓鱼指数", "免责声明", "共 5 条"]:
    ok(term in detail, f"冻结文案含「{term}」")
ov02 = (SITE / "sources_full" / "ov02-disclaimer.html").read_text(encoding="utf-8")
ok(all(k in ov02 for k in ["近岸航行", "水上安全", "渔获保证", "模型估算值", "现场校潮"]), "OV02 免责 5 条原文齐全")
water = (SITE / "sources_full" / "detail-tab-water.html").read_text(encoding="utf-8")
ok(all(k in water for k in ["水温", "溶解氧", "浊度"]), "水质 1×3 三指标")
ok(" pH " not in water and ">pH<" not in water, "水质已删 pH")
ok("52px" in detail, "DaySelector 52dp 表现值")
sea = (SITE / "sources_full" / "detail-tab-tide-sea.html").read_text(encoding="utf-8")
inl = (SITE / "sources_full" / "detail-tab-tide-inland.html").read_text(encoding="utf-8")
ok(sea != inl, "潮流海 vs 水位内陆 内容不同")
ok("现场校潮" in sea, "海洋含现场校潮入口")
ok("无潮汐" in inl, "内陆显式声明无潮汐")

# 4) 皮肤色板落到 HTML（设计 token 不能只写在 spec 里）
pal = spec.get("palette", {})
sea_pal, inl_pal = pal.get("sea", {}), pal.get("inland", {})
for key in ("bg", "primary", "accent", "card", "line", "muted"):
    ok(sea_pal.get(key, "") in detail, f"sea {key}={sea_pal.get(key)} 出现在 HTML")
    ok(inl_pal.get(key, "") in detail, f"inland {key}={inl_pal.get(key)} 出现在 HTML")

# 5) 设置页：外观·皮肤入口 + 已删两块
ss = (SITE / "sources" / "settings.html").read_text(encoding="utf-8")
ok("外观 · 皮肤" in ss, "设置页含外观·皮肤入口")
ok("用例 · 物种 · 权重" not in ss, "设置页已删用例·物种·权重")
ok("API Key" not in ss, "设置页已删 API Key")
ok("当前皮肤" in ss, "设置页显示当前皮肤")

# 6) 索引 + spec
idx = (SITE / "index-full.html").read_text(encoding="utf-8")
ok(idx.count("data:image/jpeg;base64") == 30, f"索引 30 张缩略（实 {idx.count('data:image/jpeg;base64')}）")
ok("http://" not in idx and "https://" not in idx, "索引无外链")
ok(spec["full_ui"]["screen_count"] == 30 and spec["full_ui"]["added_count"] == 23, "spec 30/23 计数")
ok(spec["skin_system"]["skins_total"] >= 2, "spec 皮肤系统 >= 2 款")
ok(any(s["id"] == "d-fieldguide" for s in spec["skin_system"]["built_in_skins"]), "spec 含 D 路亚图鉴")

print(f"\nSELFCHECK {SITE.name} PASS={PASS} FAIL={FAIL}")
if FAIL:
    raise SystemExit(1)