# -*- coding: utf-8 -*-
"""手绘 A 完整 UI + 需求包 整包自检。"""
import json, hashlib, struct, re
from pathlib import Path

A = Path(__file__).resolve().parent.parent / "A-vintage-outdoor"
spec = json.loads((A / "spec.json").read_text(encoding="utf-8"))
gaps = json.loads((A / "feature-gaps.json").read_text(encoding="utf-8"))
PASS = FAIL = 0
def ok(cond, msg):
    global PASS, FAIL
    if cond: PASS += 1
    else: FAIL += 1; print("  FAIL:", msg)

def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

# 1. 30 PNG 尺寸 + 签名 + spec SHA
all_png = list((A / "screens").glob("*.png")) + list((A / "screens_full").glob("*.png"))
ok(len(all_png) == 30, f"PNG 总数=30（实际 {len(all_png)}）")
for p in all_png:
    b = p.read_bytes()
    ok(b[:8] == b"\x89PNG\r\n\x1a\n", f"{p.name} PNG 签名")
    w, h = struct.unpack(">II", b[16:24])
    ok((w, h) == (780, 1688), f"{p.name} 尺寸 780x1688（实 {w}x{h}）")
# spec 中 21 新增屏 SHA 匹配
for e in spec["full_ui"]["added_screens"]:
    ok(sha(A / e["png"]) == e["sha256_png"], f'{e["key"]} spec SHA 匹配')
# 改版设置页（OPT-A-01/02/09）SHA 匹配（base 7 屏 settings）
_st = next(s for s in spec["screens"] if s["key"] == "settings")
ok(sha(A / _st["png"]) == _st["sha256"], "settings.png 改版后 SHA 匹配 spec")
ok(sha(A / _st["html"]) == _st["html_sha256"], "settings.html 改版后 SHA 匹配 spec")

# 2. 21 HTML 自包含（无外链 / 无相对 src / 无 file://）
htmls = list((A / "sources_full").glob("*.html"))
ok(len(htmls) == 23, f"HTML 总数=23（实际 {len(htmls)}）")
ok("钓鱼指数" in (A / "sources_full" / "detail-tab-score-sea.html").read_text(encoding="utf-8"), "SCORE 首 Tab(海) 含「钓鱼指数」")
ok("五档" in (A / "sources_full" / "detail-tab-score-inland.html").read_text(encoding="utf-8"), "SCORE 首 Tab(内陆) 含「五档」")
for p in htmls:
    t = p.read_text(encoding="utf-8")
    ok("http://" not in t and "https://" not in t, f"{p.name} 无网络外链")
    ok("file://" not in t, f"{p.name} 无 file://")
    # 允许 data:image base64；禁止 <img src=相对路径>（本引擎 HTML 无 <img>）
    imgs = re.findall(r'<img[^>]+src="([^"]+)"', t)
    bad = [s for s in imgs if not s.startswith("data:")]
    ok(not bad, f"{p.name} 无相对路径 img（{bad[:2]}）")
    ok("<svg" in t and "feTurbulence" in t, f"{p.name} 含手绘滤镜 SVG")

# 3. 冻结契约文案齐全（分散在详情类 HTML）
contract_terms = ["钓鱼指数", "免责声明", "共 5 条"]
detail_html = "\n".join(p.read_text(encoding="utf-8") for p in htmls)
for term in contract_terms:
    ok(term in detail_html, f"冻结文案含「{term}」")
ok(all(k in (A / "sources_full" / "detail-tab-tide-sea.html").read_text(encoding="utf-8") for k in ["潮高", "潮流", "现场校潮"]), "海洋 Tab 含「潮高/潮流/现场校潮」语义")
ok("无潮汐" in (A / "sources_full" / "detail-tab-tide-inland.html").read_text(encoding="utf-8"), "内陆 Tab 含「无潮汐」")
ok("COASTAL" in (A / "sources_full" / "detail-tab-tide-sea.html").read_text(encoding="utf-8"), "海洋徽标 COASTAL")
ok("INLAND" in (A / "sources_full" / "detail-tab-tide-inland.html").read_text(encoding="utf-8"), "内陆徽标 INLAND")
# 免责 5 条原文
ov02 = (A / "sources_full" / "ov02-disclaimer.html").read_text(encoding="utf-8")
ok(all(k in ov02 for k in ["近岸航行", "水上安全", "渔获保证", "模型估算值", "现场校潮"]), "OV02 免责 5 条原文齐全")
# 水质 1×3，无 pH
water = (A / "sources_full" / "detail-tab-water.html").read_text(encoding="utf-8")
ok(all(k in water for k in ["水温", "溶解氧", "浊度"]), "水质 1×3 三指标")
ok(" pH " not in water and ">pH<" not in water, "水质已删 pH")
# DaySelector 52dp
ok("height:52px" in detail_html or "52px" in detail_html, "DaySelector 52dp 表现值")

# 4. 双水体六重区分（base 7 屏已有，检查新增 tide 海/陆差异）
sea = (A / "sources_full" / "detail-tab-tide-sea.html").read_text(encoding="utf-8")
inl = (A / "sources_full" / "detail-tab-tide-inland.html").read_text(encoding="utf-8")
ok(sea != inl, "潮流海 vs 水位内陆 内容不同")
ok("现场校潮" in sea, "海洋含现场校潮入口")
ok("不画潮汐曲线" in inl, "内陆显式声明不画潮汐曲线")

# 5. 需求文档结构（2026-11-12 修订：9 条 / 7 转需求 / 含 ui_remove 与 OPT-A-09）
md = (A / "APP功能优化需求.md").read_text(encoding="utf-8")
for h in ["OPT-A-01", "OPT-A-02", "OPT-A-03", "OPT-A-04", "OPT-A-05", "OPT-A-06", "OPT-A-09", "冻结契约", "验收", "三轴正交"]:
    ok(h in md, f"需求文档含 {h}")
ok(len(gaps["gaps"]) == 9, "feature-gaps 9 条")
ok(sum(1 for g in gaps["gaps"] if g["category"] in ("ui_new","ui_remove","mismatch")) == 7, "转需求 7 条")
ok(sum(1 for g in gaps["gaps"] if g["category"] == "ui_remove") == 2, "ui_remove 2 条（OPT-A-01/02）")
ok(sum(1 for g in gaps["gaps"] if g["category"] == "code_ahead") == 2, "仅记录 2 条")
ok("ui_remove" in gaps["meta"]["category_def"], "机读表含 ui_remove 类别定义")
for g in gaps["gaps"]:
    if g["category"] in ("ui_new", "ui_remove", "mismatch"):
        ok(all(k in g for k in ["current","evidence","target","acceptance","code_hint","requirement_ref"]), f'{g["id"]} 字段齐全')
_g = {x["id"]: x for x in gaps["gaps"]}
ok(_g["OPT-A-01"]["category"] == "ui_remove" and "删除" in _g["OPT-A-01"]["title"], "OPT-A-01 为删除功能")
ok(_g["OPT-A-02"]["category"] == "ui_remove" and "删除" in _g["OPT-A-02"]["title"], "OPT-A-02 为删除功能")
ok("换皮肤" in _g["OPT-A-09"]["title"] and "B/C/D" in _g["OPT-A-09"]["target"], "OPT-A-09 换皮肤且 BCD 不并入")

# 5b. 改版设置页（OPT-A-01/02/09 落地到 UI）
ss = (A / "sources" / "settings.html").read_text(encoding="utf-8")
ok("外观 · 皮肤" in ss and "敬请期待" in ss and "手绘 A · 复古户外" in ss, "设置页含外观·皮肤入口+占位+当前皮肤")
ok("用例 · 物种 · 权重" not in ss, "设置页已删用例·物种·权重版块")
ok("API Key" not in ss, "设置页已删 API Key 版块")
ok(all(k in ss for k in ["数据与网络", "隐私披露", "Copernicus", "免责声明"]), "设置页其余版块保留齐全")
ok("feTurbulence" in ss, "设置页仍含手绘滤镜")
ok("http://" not in ss and "https://" not in ss and "file://" not in ss, "设置页无外链")

# 5c. 皮肤设计规范 + spec skin_system
skinspec = (A / "SKIN-DESIGN-SPEC.md").read_text(encoding="utf-8")
for h in ["三轴正交", "皮肤 token", "冻结", "接入清单", "敬请期待", "B/C/D", "验收清单"]:
    ok(h in skinspec, f"皮肤规范含 {h}")
ok("skin_system" in spec and spec["skin_system"]["skins_total"] == 1, "spec 含 skin_system 且首版 1 款")
ok(spec["skin_system"]["built_in_skins"][0]["id"] == "a-vintage", "skin_system 内置 A")
ok("selection_page" in spec["skin_system"] and "本轮未做" in spec["skin_system"]["selection_page"], "spec 标注选择页本轮未做")

# 6. 索引页 + README + spec
idx = (A / "index-full.html").read_text(encoding="utf-8")
ok("data:image/jpeg;base64" in idx, "索引页内嵌缩略 base64")
ok(idx.count("data:image/jpeg;base64") == 30, f"索引页 30 张缩略（实 {idx.count('data:image/jpeg;base64')}）")
ok("http://" not in idx and "https://" not in idx, "索引页无外链")
ok("摘要（7 条转需求）" in idx and "tag-rm" in idx, "索引页反映 7 条转需求 + 下线标签")
ok("OPT-A-09" in idx and "SKIN-DESIGN-SPEC.md" in idx and "换皮肤(OPT-A-09)" in idx, "索引页含换皮肤需求/规范/冻结条款")

# 6b. 参照稿错位勘误（2026-11-12）：确保索引/对照/文档不再把旧稿当对齐目标
ok("参照稿勘误" in idx and "两套并存的详情页视觉稿" in idx, "索引页含两套并存稿勘误块")
ok("早期 base 旧稿" in idx and "非真机参照" in idx, "索引页第①组标早期 base 旧稿/非真机参照")
ok("真机 Compose 试点参照的是新稿" in idx, "索引页声明真机参照新稿")
ok("不含 Compose 代码" not in idx and "Compose 试点" in idx, "索引页脚已修正过时声明、如实提试点")
ok("⚠" not in idx, "索引页无缺字特殊符号")
# 完整性缺口标注（认新稿推进中）
ok("完整性提示" in idx and "30 屏全为新稿骨架" in idx, "索引页含完整性提示块")
ok("钓鱼指数 SCORE 首 Tab 此前缺失，本次已补" in idx and "5 Tab + 因子分解" in idx, "索引页标注 SCORE 首 Tab 已补、②组 5 Tab")
ok("〔旧稿" in idx and "〔新稿〕" in idx, "索引页逐屏标稿源(旧稿/新稿)")
ok("仅剩第①组 详情·海洋/内陆 保留为早期 base 旧稿" in idx, "索引页交代仅剩早期 base 旧稿两屏")
ok("地图底图保留手绘原稿" in idx, "索引页标注地图底图保留手绘")
for cf in ["make_compare_v3.py", "make_compare_v4.py"]:
    ct = (Path(__file__).resolve().parent / cf).read_text(encoding="utf-8")
    ok("早期 base 旧稿" in ct and "非参照稿" in ct, f"{cf} 左标题改早期旧稿")
    ok("参照新稿" in ct and "非对齐证据" in ct and "⚠" not in ct, f"{cf} 右标题/说明口径已勘误")
rpt = (A / "device-compare" / "真机对拍报告.md").read_text(encoding="utf-8")
ok("参照稿勘误" in rpt and "两套并存的详情页视觉稿" in rpt, "对拍报告含参照稿勘误块")
ok("配色 token 层与功能入口红线" in rpt and "不含结构" in rpt, "对拍报告限定通过口径")
ok("不是照着旧稿做的" in rpt and "完全不一样" in rpt, "对拍报告交代错位主因")
note = (A / "Compose试点落地说明.md").read_text(encoding="utf-8")
ok("参照稿勘误" in note and "不是" in note and "早期 base" in note, "落地说明含参照稿勘误")
ok("两版稿暂不合并、不定稿" in note, "落地说明标注暂不定稿")

readme = (A / "README.md").read_text(encoding="utf-8")
ok("完整 UI" in readme and "30" in readme, "README 含完整 UI 说明")
ok(spec["full_ui"]["screen_count"] == 30 and spec["full_ui"]["added_count"] == 23, "spec full_ui 计数")

# 7. 原 7 屏 base 文件仍在且未被本轮覆盖（存在性 + 尺寸由第1步覆盖）
for n in ["detail-sea","detail-inland","map-sea","map-inland","favorites","settings","dark-detail"]:
    ok((A / "screens" / f"{n}.png").exists(), f"base 屏 {n} 仍在")

# 8. 认新稿收尾固化（#83–#85）：深色=新稿骨架+DARK 调色板；地图底图三色保留手绘
_dm = (A / "sources_full" / "dark-map.html").read_text(encoding="utf-8")
_dr = (A / "sources_full" / "dark-favorites.html").read_text(encoding="utf-8")
_dd = (A / "sources" / "dark-detail.html").read_text(encoding="utf-8")
_ms = (A / "sources" / "map-sea.html").read_text(encoding="utf-8")
ok("0E1C2B" in _dm and "1B3044" in _dm, "dark-map = DARK 调色板 + 新稿地图底图")
ok("OpenStreetMap" in _dm and "搜索地点" in _dm, "dark-map 含新稿地图周边组件")
ok("0E1C2B" in _dr and "我的收藏" in _dr, "dark-favorites = DARK 调色板 + 新稿收藏骨架")
ok("0E1C2B" in _dd and "钓鱼指数" in _dd, "深色详情 = DARK 调色板 + 新稿钓鱼指数骨架")
ok(all(c in _ms for c in ["D6C6A0", "9FB8B4", "EFE6CC"]), "map-sea 保留手绘地图底图三色（浅色）")
ok(all(c in _dm for c in ["1B3044", "123A55", "2A4358"]), "dark-map 保留手绘地图底图三色（DARK）")

print(f"\nSELFCHECK PASS={PASS} FAIL={FAIL}")
if FAIL: raise SystemExit(1)
