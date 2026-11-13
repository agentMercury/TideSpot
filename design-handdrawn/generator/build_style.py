# -*- coding: utf-8 -*-
"""为一套 STYLES 生成完整 30 屏：base 7 + full 23 + 2x PNG + spec + index。

用法：
    python build_style.py d-guidebook D-field-guide
    python build_style.py a-vintage A-vintage-outdoor   # 仅重建 HTML/spec/index，PNG 可选

设计侧事实：engine.py 的 STYLES 是唯一色板源；engine_full.py 复用同一套组件函数，
只切 SID，不复制布局（SKIN-DEV-STANDARD §3.2）。
"""
import sys, json, shutil, hashlib, subprocess
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import engine as E
import engine_full as EF

EDGE = Path(r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe")

BASE_NAMES = ["detail-sea", "detail-inland", "map-sea", "map-inland", "favorites", "settings", "dark-detail"]
BASE_LABELS = {
    "detail-sea": "详情 · 海洋/近海（base 骨架）",
    "detail-inland": "详情 · 内陆/湖泊（base 骨架）",
    "map-sea": "地图 · 海洋",
    "map-inland": "地图 · 内陆",
    "favorites": "收藏",
    "settings": "设置（含外观·皮肤入口）",
    "dark-detail": "深色详情",
}
FULL_NAMES = [
    "detail-tab-score-sea", "detail-tab-score-inland",
    "detail-tab-tide-sea", "detail-tab-tide-inland",
    "detail-tab-weather", "detail-tab-water",
    "detail-tab-wave-sea", "detail-tab-wave-inland",
    "detail-breakdown",
    "ov01-top10", "ov02-disclaimer", "ov03-confirm-favorite",
    "ov04-water-entry", "ov05-star-bubble", "ov06-delete-confirm",
    "state-offline", "state-stale", "state-unusable-48h", "state-degraded",
    "favorites-empty", "favorites-multiselect",
    "dark-map", "dark-favorites",
]
FULL_LABELS = {
    "detail-tab-score-sea": "钓鱼指数 · 海",
    "detail-tab-score-inland": "钓鱼指数 · 内陆",
    "detail-tab-tide-sea": "潮流 · 海",
    "detail-tab-tide-inland": "水位 · 内陆",
    "detail-tab-weather": "天气",
    "detail-tab-water": "水质 1×3",
    "detail-tab-wave-sea": "风浪 · 海",
    "detail-tab-wave-inland": "湖面风 · 内陆",
    "detail-breakdown": "因子分解",
    "ov01-top10": "OV01 Top10",
    "ov02-disclaimer": "OV02 免责 5 条",
    "ov03-confirm-favorite": "OV03 收藏确认",
    "ov04-water-entry": "OV04 水质录入",
    "ov05-star-bubble": "OV05 星标气泡",
    "ov06-delete-confirm": "OV06 删除确认",
    "state-offline": "状态 · 离线",
    "state-stale": "状态 · 陈旧",
    "state-unusable-48h": "状态 · 48h 不可用",
    "state-degraded": "状态 · 分维降级",
    "favorites-empty": "收藏空态",
    "favorites-multiselect": "多选批量",
    "dark-map": "深色地图",
    "dark-favorites": "深色收藏",
}


def sha(p: Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def build_html(style_id: str, site: Path) -> Path:
    site = Path(site)
    site.mkdir(parents=True, exist_ok=True)
    (site / "sources").mkdir(exist_ok=True)
    (site / "screens").mkdir(exist_ok=True)
    (site / "screens_full").mkdir(exist_ok=True)

    # 1) base 7：engine.py 的通用渲染（含新旧稿；随后 render_base_redraw 覆盖 5 屏）
    E.build_all(style_id)
    pages = E.WORK / "pages"
    for name in BASE_NAMES:
        shutil.copyfile(pages / f"{style_id}__{name}.html", site / "sources" / f"{name}.html")

    # 2) full 23 + base 覆盖（favorites/settings/dark-detail/map）
    EF.configure(style_id, site)
    EF.build()
    return site


def shot(html: Path, png: Path) -> bool:
    png.parent.mkdir(parents=True, exist_ok=True)
    if not EDGE.exists():
        print(f"  SKIP render (Edge missing): {html.name}")
        return False
    cmd = [
        str(EDGE), "--headless=new", "--disable-gpu", "--hide-scrollbars",
        "--force-device-scale-factor=2", "--window-size=390,844",
        "--virtual-time-budget=2500",
        f"--user-data-dir={HERE.parent / '_work' / 'edge-profile'}",
        f"--screenshot={png.resolve()}", html.resolve().as_uri(),
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return True


def render_png(site: Path) -> int:
    n = 0
    for name in BASE_NAMES:
        if shot(site / "sources" / f"{name}.html", site / "screens" / f"{name}.png"):
            n += 1
    for name in FULL_NAMES:
        if shot(site / "sources_full" / f"{name}.html", site / "screens_full" / f"{name}.png"):
            n += 1
    return n


def build_spec(style_id: str, site: Path) -> Path:
    s = E.STYLES[style_id]
    screens = []
    for name in BASE_NAMES:
        png = site / "screens" / f"{name}.png"
        html = site / "sources" / f"{name}.html"
        screens.append({
            "key": name, "label": BASE_LABELS[name],
            "png": f"screens/{name}.png", "html": f"sources/{name}.html",
            "sha256": sha(png), "html_sha256": sha(html),
        })
    added = []
    for name in FULL_NAMES:
        png = site / "screens_full" / f"{name}.png"
        html = site / "sources_full" / f"{name}.html"
        added.append({
            "key": name, "label": FULL_LABELS[name],
            "png": f"screens_full/{name}.png", "html": f"sources_full/{name}.html",
            "sha256_png": sha(png), "sha256_html": sha(html),
        })
    spec = {
        "schema": "tidespot.handdrawn.version/1.0",
        "id": site.name,
        "no": s["no"],
        "name": s["name"],
        "name_en": s["en"],
        "based_on": "工程 v1.17.0（versionCode 21）· 皮肤 d-fieldguide",
        "source_images": ["idea-explorations/v3-fieldguide（路亚鱼种图鉴探索稿）"],
        "visual_language": s["idea"],
        "fish_motif": "图鉴贴纸鱼：粗黑描边 + 硬投影 + 平涂",
        "best_for": s["fit"],
        "tone": "冷白/薄荷平涂 路 海蓝/青绿双水体",
        "offline": True,
        "selfcontained": True,
        "palette": {w: s["pal"][w] for w in ("sea", "inland")},
        "screens": screens,
        "full_ui": {
            "screen_count": 30,
            "added_count": len(added),
            "added_screens": added,
        },
        "skin_system": {
            "requirement_ref": "OPT-A-09 / SKIN-1..3",
            "spec": "../A-vintage-outdoor/SKIN-DESIGN-SPEC.md",
            "definition": "皮肤 = 一组可替换的视觉 token，结构/文案/交互/冻结契约全部共用",
            "three_orthogonal_axes": [
                "皮肤 Skin（用户偏好）",
                "水体 Water（点位数据决定，不可切）",
                "明暗 Mode（跟随系统/浅/深）",
            ],
            "built_in_skins": [
                {"id": "a-vintage", "name": "手绘 A · 复古户外", "name_en": "VINTAGE OUTDOOR",
                 "status": "内置", "palettes": ["sea", "inland"], "screens": 30},
                {"id": "d-fieldguide", "name": "D · 路亚图鉴", "name_en": "LURE FIELD GUIDE",
                 "status": "内置（工程已落地 v1.17.0）", "palettes": ["sea", "inland"], "screens": 30},
            ],
            "skins_total": 2,
            "selection_page": "已做：设置页「皮肤」下拉（SKIN-3 / OPT-A-09）；无独立选择页",
            "excluded": "B 拟人鱼绅士 / C 水彩几何鱼 仍是独立探索稿，不并入；D 已皮肤化",
            "entry_screen": "screens/settings.png（外观·皮肤入口卡）",
            "mutable_tokens": ["色板(sea/inland 两套)", "字体", "形状/圆角", "纸张质感", "鱼形装饰", "深色 token"],
            "frozen_not_in_skin": [
                "Tab 渲染序", "免责 5 条", "五档/六徽标色", "DaySelector 52dp",
                "水质 1×3", "无底部 NavigationBar", "地图优先", "准星唯一真源",
                "双水体六重区分", "全部文案",
            ],
            "new_skin_checklist": "../A-vintage-outdoor/SKIN-DEV-STANDARD.md §6（A1–A10）",
        },
    }
    out = site / "spec.json"
    out.write_text(json.dumps(spec, ensure_ascii=False, indent=2), encoding="utf-8")
    return out


def build_index(style_id: str, site: Path) -> Path:
    from PIL import Image
    import base64, io
    s = E.STYLES[style_id]
    groups = [
        ("① 基础主屏 · 7", [(n, BASE_LABELS[n], site / "screens") for n in BASE_NAMES]),
        ("② 详情 5 Tab + 因子分解 · 9", [(n, FULL_LABELS[n], site / "screens_full") for n in FULL_NAMES[:9]]),
        ("③ 6 叠层 · 6", [(n, FULL_LABELS[n], site / "screens_full") for n in FULL_NAMES[9:15]]),
        ("④ 状态态 + 空态/批量 · 6", [(n, FULL_LABELS[n], site / "screens_full") for n in FULL_NAMES[15:21]]),
        ("⑤ 深色变体 · 2", [(n, FULL_LABELS[n], site / "screens_full") for n in FULL_NAMES[21:]]),
    ]
    tw = 190
    tiles = []
    for title, items in groups:
        cells = []
        for name, label, folder in items:
            im = Image.open(folder / f"{name}.png").convert("RGB")
            h = int(im.height * tw / im.width)
            im = im.resize((tw, h))
            buf = io.BytesIO()
            im.save(buf, "JPEG", quality=82)
            b64 = base64.b64encode(buf.getvalue()).decode("ascii")
            cells.append(
                f'<figure><img src="data:image/jpeg;base64,{b64}" alt="{label}">'
                f'<figcaption>{label}<small>{name}.png</small></figcaption></figure>'
            )
        tiles.append(f'<section><h2>{title}</h2><div class="grid">{"".join(cells)}</div></section>')
    html = f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
<title>潮点 TideSpot · {s['no']} {s['name']} · 完整 UI（30 屏）</title>
<style>
body{{margin:0;background:#101820;color:#F2EFE6;font:14px/1.6 "Microsoft YaHei",sans-serif}}
header{{padding:28px 32px 10px}}h1{{margin:0 0 6px;font-size:26px;font-weight:800}}
.meta{{color:#9FB6C4;font-size:12px}}section{{padding:14px 32px 6px}}
h2{{font-size:15px;margin:0 0 10px;color:#8AC1E3;border-left:4px solid #E5644C;padding-left:8px}}
.grid{{display:grid;grid-template-columns:repeat(auto-fill,minmax(190px,1fr));gap:16px}}
figure{{margin:0;background:#12222E;border:2px solid #F2EFE6;border-radius:12px;overflow:hidden}}
figure img{{display:block;width:100%;height:auto}}
figcaption{{padding:8px 10px;font-size:12px;color:#F2EFE6}}
figcaption small{{display:block;color:#9FB6C4;font-size:10px}}
footer{{padding:20px 32px 40px;color:#9FB6C4;font-size:11px}}
b{{color:#F2EFE6}}
</style></head><body>
<header><h1>潮点 TideSpot · {s['no']} · {s['name']}</h1>
<div class="meta">LURE FIELD GUIDE · 30 屏 · 工程皮肤 <b>d-fieldguide</b> · 与 <b>TideSpotSkin.kt</b> 同源色板</div>
<div class="meta">浅色源色取自 idea-explorations/v3-fieldguide；深色为独立调色；结构/文案/Tab 序/冻结契约与 A 共用。</div></header>
{''.join(tiles)}
<footer>复现：<b>python generator/build_style.py {style_id} {site.name}</b> → 30 HTML + 2x PNG + spec.json + 本索引。<br>
工程侧：设置 → 皮肤 → 路亚图鉴（v1.17.0）。</footer>
</body></html>"""
    out = site / "index-full.html"
    out.write_text(html, encoding="utf-8")
    return out


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    if len(argv) < 2:
        print("usage: python build_style.py <style_id> <site_dir> [--no-png]")
        raise SystemExit(2)
    style_id, site = argv[0], HERE.parent / argv[1]
    no_png = "--no-png" in argv
    print(f"STYLE={style_id} SITE={site.name}")
    build_html(style_id, site)
    if no_png:
        print("HTML ready; PNG / SPEC / INDEX skipped (use --no-png only for dry runs)")
        return
    n = render_png(site)
    print(f"PNG={n}/30")
    print("SPEC", build_spec(style_id, site).name)
    print("INDEX", build_index(style_id, site).name)


if __name__ == "__main__":
    main()