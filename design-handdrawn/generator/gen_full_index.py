# -*- coding: utf-8 -*-
"""生成「手绘 A 完整 UI」自包含索引页 index-full.html（30 屏分组拼览 + 需求摘要）。"""
from pathlib import Path
import base64, io, json
from PIL import Image

A = Path(__file__).resolve().parent.parent / "A-vintage-outdoor"
SHOT = A / "screens_full"
BASE = A / "screens"
OUT = A / "index-full.html"

groups = [
    ("① 基础主屏 · 7（详情=旧稿；地图/收藏/设置/深色=认新稿·底图保留手绘）", "#0D47A1", [
        ("detail-sea", "详情·海洋〔旧稿78·非落地目标〕", BASE), ("detail-inland", "详情·内陆〔旧稿78·非落地目标〕", BASE),
        ("map-sea", "地图·海洋〔新稿〕", BASE), ("map-inland", "地图·内陆〔新稿〕", BASE),
        ("favorites", "收藏〔新稿〕", BASE), ("settings", "设置〔新稿〕", BASE), ("dark-detail", "深色详情〔新稿·深色〕", BASE),
    ]),
    ("② 详情 5 Tab 变体 + 因子分解 · 9（新稿结构）", "#2E6E7E", [
        ("detail-tab-score-sea", "钓鱼指数·海", SHOT), ("detail-tab-score-inland", "钓鱼指数·内陆", SHOT),
        ("detail-tab-tide-sea", "潮流·海", SHOT), ("detail-tab-tide-inland", "水位·内陆", SHOT),
        ("detail-tab-weather", "天气", SHOT), ("detail-tab-water", "水质 1×3", SHOT),
        ("detail-tab-wave-sea", "风浪·海", SHOT), ("detail-tab-wave-inland", "湖面风·内陆", SHOT),
        ("detail-breakdown", "因子分解", SHOT),
    ]),
    ("③ 6 叠层 · Sheet / Dialog / 气泡 · 6（新稿骨架）", "#8C5A3A", [
        ("ov01-top10", "OV01 Top10〔新稿〕", SHOT), ("ov02-disclaimer", "OV02 免责5条〔新稿〕", SHOT),
        ("ov03-confirm-favorite", "OV03 收藏确认〔新稿〕", SHOT), ("ov04-water-entry", "OV04 水质录入〔新稿〕", SHOT),
        ("ov05-star-bubble", "OV05 星标气泡〔新稿〕", SHOT), ("ov06-delete-confirm", "OV06 删除确认〔新稿〕", SHOT),
    ]),
    ("④ 4 状态态 + 空态 / 批量 · 6（状态态×4 + 批量已转新稿；空态新稿）", "#6E7B3E", [
        ("state-offline", "离线〔新稿〕", SHOT), ("state-stale", "陈旧〔新稿〕", SHOT),
        ("state-unusable-48h", "48h不可用〔新稿〕", SHOT), ("state-degraded", "分维降级〔新稿〕", SHOT),
        ("favorites-empty", "收藏空态〔新稿〕", SHOT), ("favorites-multiselect", "多选批量〔新稿〕", SHOT),
    ]),
    ("⑤ 深色变体 · 2（新稿骨架 + DARK 调色板）", "#16324F", [
        ("dark-map", "深色地图〔新稿·深色〕", SHOT), ("dark-favorites", "深色收藏〔新稿·深色〕", SHOT),
    ]),
]

TW = 190
def thumb(name, folder):
    p = folder / f"{name}.png"
    im = Image.open(p).convert("RGB")
    h = int(im.height * TW / im.width)
    im = im.resize((TW, h))
    bio = io.BytesIO(); im.save(bio, "JPEG", quality=80)
    return "data:image/jpeg;base64," + base64.b64encode(bio.getvalue()).decode(), h

gaps = json.loads((A / "feature-gaps.json").read_text(encoding="utf-8"))
reqs = [g for g in gaps["gaps"] if g["category"] in ("ui_new", "ui_remove", "mismatch")]
notes = [g for g in gaps["gaps"] if g["category"] == "code_ahead"]
REQ_N = len(reqs)
NOTE_N = len(notes)
CAT_LABEL = {"ui_new": "新增", "ui_remove": "下线", "mismatch": "对齐"}

html = ["""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>潮点 TideSpot · 手绘 A 完整 UI</title>
<style>
*{box-sizing:border-box;margin:0;padding:0}body{font-family:"Microsoft YaHei",sans-serif;background:#E4D9BC;color:#16324F;padding:32px}
.wrap{max-width:1480px;margin:0 auto}.badge{display:inline-block;background:#1B3A5C;color:#F7F1DE;font-weight:700;font-size:12px;letter-spacing:.12em;padding:5px 14px;border:2px solid #16324F;border-radius:6px;box-shadow:2px 2px 0 #16324F55}
h1{font-family:Georgia,serif;font-size:34px;margin:14px 0 4px;letter-spacing:.02em}.sub{color:#5C5540;font-size:14px;margin-bottom:22px;line-height:1.7}
.cards{display:grid;grid-template-columns:repeat(7,1fr);gap:13px}
.grp{background:#F2EAD4;border:2.2px solid #16324F;border-radius:16px;padding:16px 18px;margin-bottom:20px;box-shadow:3px 4px 0 #16324F33;position:relative}
.grp:before{content:"";position:absolute;left:50%;top:-1px;transform:translateX(-50%);width:70px;height:6px;background:#D9A734;opacity:.85}
.grp h2{font-family:Georgia,serif;font-size:16px;margin-bottom:12px;letter-spacing:.03em}
.tile{background:#F7F1DE;border:1.8px solid #16324F;border-radius:10px;overflow:hidden;box-shadow:1.5px 2px 0 #16324F33}
.tile img{width:100%;display:block}.tile .lb{font-size:11px;padding:5px 7px;color:#16324F;font-weight:700;text-align:center;border-top:1.4px dashed #16324F66}
.tile.old{position:relative;border-color:#8A3B2E;box-shadow:1.5px 2px 0 #8A3B2E66}.tile.old img{filter:saturate(.8) contrast(.96)}
.tile.old .lb{color:#8A3B2E;border-top-color:#8A3B2E66}
.tile.old .wm{position:absolute;left:0;right:0;top:0;bottom:26px;display:flex;align-items:center;justify-content:center;pointer-events:none;background:repeating-linear-gradient(45deg,#8A3B2E0E,#8A3B2E0E 9px,transparent 9px,transparent 18px)}
.tile.old .wm span{transform:rotate(-16deg);font-size:11px;font-weight:900;color:#8A3B2E;border:2.4px solid #8A3B2E;border-radius:6px;padding:5px 9px;background:#FBF3D9EE;letter-spacing:.05em;line-height:1.3;text-align:center;box-shadow:0 0 0 3px #FBF3D966}
.req{background:#F2EAD4;border:2.2px solid #16324F;border-radius:16px;padding:18px 22px;margin-bottom:16px;box-shadow:3px 4px 0 #16324F33}
.req h2{font-family:Georgia,serif;font-size:19px;margin-bottom:12px}
table{width:100%;border-collapse:collapse;font-size:12.5px}th,td{text-align:left;padding:8px 9px;border-bottom:1.4px dashed #16324F55;vertical-align:top;line-height:1.6}
th{color:#5C5540;font-weight:700;width:74px}.p1{color:#8A3B2E;font-weight:700}.p2{color:#5C5540}.tag{display:inline-block;font-size:10px;border:1.6px solid #16324F;border-radius:20px;padding:1px 8px;margin-right:5px;background:#E4D9BC;font-weight:700}.tag-rm{display:inline-block;font-size:10px;border:1.6px solid #8A3B2E;border-radius:20px;padding:1px 8px;margin-right:5px;background:#8A3B2E;color:#F7F1DE;font-weight:700}
.frozen{background:#1B3A5C;color:#F2EAD4;border-radius:14px;padding:18px 22px;font-size:12.5px;line-height:1.9;margin-top:8px}
.frozen b{color:#D9A734}.foot{color:#5C5540;font-size:11.5px;line-height:1.8;margin-top:18px;text-align:center}
.note{font-size:11px;color:#5C5540;line-height:1.7;margin-top:8px}
.errata{background:#FBF3D9;border:2.6px dashed #8A3B2E;border-radius:14px;padding:16px 20px;margin-bottom:22px;font-size:12.5px;line-height:1.8;color:#5C2E22}
.errata h3{font-family:Georgia,serif;font-size:16px;color:#8A3B2E;margin-bottom:8px}
.errata b{color:#8A3B2E}.errata .k{background:#8A3B2E;color:#F7F1DE;padding:1px 7px;border-radius:5px;font-size:11px;font-weight:700;margin-right:6px}
.errata .new{background:#2E5A48;color:#F7F1DE;padding:1px 7px;border-radius:5px;font-size:11px;font-weight:700;margin-right:6px}
</style></head><body><div class="wrap">
<span class="badge">DESIGN CONCEPT A · VINTAGE OUTDOOR · COMPLETE UI</span>
<h1>潮点 TideSpot · 手绘 A 复古户外 · 完整 UI（30 屏）</h1>
<div class="sub">基于工程 v1.16.0（versionCode 20）· 视觉概念稿 · 全部离线自包含 · 双水体（海洋/近海 ↔ 内陆/湖泊）一眼可辨<br>
设置页已按需求决策改版：下线「用例·物种·权重」「API Key」，新增「外观·皮肤」入口（首版仅内置手绘 A，扩展规范见 <b>SKIN-DESIGN-SPEC.md</b>）。<br>
右下方为《APP 功能优化需求》摘要（{REQ_N} 条转需求 + {NOTE_N} 条仅记录）；逐条代码证据与验收见 <b>APP功能优化需求.md</b> / <b>feature-gaps.json</b>。</div>
<div class="errata"><h3>参照稿勘误（2026-11-12 · 阅读前必看）</h3>
本索引里存在 <b>两套并存的详情页视觉稿</b>，它们是不同时间、不同生成器产出的<b>两个版本</b>，<b>不是同一张稿的多个 Tab 变体</b>，请勿当作"逐屏对齐"来读：<br>
<span class="k">旧稿 · base 7 屏</span>第①组 <b>detail-sea / detail-inland</b>（分数 <b>78</b>／<b>白底细边</b>／圆鱼徽标／虚线鱼框，图表为「潮高·潮流」折线柱）由 <b>engine.py</b> 生成，是手绘 A 最早的一版；索引第①组、早期 <b>preview.html</b> 展示的是这套；<b>第①组这两张缩略图已叠「历史旧稿 · 非落地目标」水印，切勿再拿它们当实现/对齐参照</b>。<br>
<span class="new">新稿 · 完整 UI 变体</span>第②组 <b>detail-tab-*</b>（分数 <b>82</b>／<b>苔绿·米黄纸底</b>／五档鱼数卡片／图表⇄数值表切换）由 <b>engine_full.py</b> 生成，是后续"完整 UI"扩出的版本（现 30 屏）。<br>
<b>真机 Compose 试点参照的是新稿（screens_full）+ 工程既有骨架，不是旧稿。</b>此前把旧稿标成"设计稿"与真机并排称"对齐/通过"属参照错位，已在 <b>device-compare/真机对拍报告.md</b> §0 勘误。两套稿暂不合并、不定稿，待用户决策；本轮仅纠错，未改任何视觉。
</div>
<div class="errata" style="border-color:#2E5A48;color:#21402E;background:#EEF2DA"><h3 style="color:#2E5A48">完整性提示：30 屏全为新稿骨架（认新稿 #83–#85 已完成）</h3>
这套"完整 UI"现已扩到 <b>30 屏</b>（基础 7 + 新增 23），各屏底稿来源已标在〔旧稿〕〔新稿〕里：
<br>① <b>详情 5 Tab 已齐（新稿）</b>：钓鱼指数 / 潮流（水位）/ 天气 / 水质 / 风浪 首 Tab 均出新稿样式；<b>钓鱼指数 SCORE 首 Tab 此前缺失，本次已补</b>（海/陆两版，②组现为 5 Tab + 因子分解 · 9 屏）。
<br>② <b>认新稿推进（#83–#85 已完成）</b>：第①组主屏的 <b>地图·海洋/内陆、收藏、设置</b> 已于 <b>#84</b> 重绘为新稿骨架；<b>第③组 OV01–OV06 叠层 #83</b>、<b>第⑤组深色地图/收藏与第①组深色详情 #85</b> 均已转 <b>新稿骨架 + DARK 调色板</b>（地图底图保留手绘原稿，与 OV03/OV05 同源）；真正新稿重画的为 第②组 Tab + OV04 水质录入 + 收藏空态 + 第④组 4 状态态与多选批量。<b>仅剩第①组 详情·海洋/内陆 保留为早期 base 旧稿</b>（与第②组新稿并存，属勘误所述「两套并存稿」，非待重绘对象、非真机参照）。
<br>③ <b>地图底图保留手绘原稿</b>：认新稿仅重绘地图页周边组件，手绘地图底图（map_land/map_water/map_road 三色 + 路网）不作重绘，避免重画地图。
</div>
"""]

for gtitle, color, items in groups:
    html.append(f'<div class="grp" style="border-color:{color}"><h2 style="color:{color}">{gtitle}</h2><div class="cards">')
    for name, lab, folder in items:
        src, h = thumb(name, folder)
        if name in ("detail-sea", "detail-inland"):
            html.append(f'<div class="tile old"><img src="{src}" alt="{lab}"><div class="wm"><span>历史旧稿<br>非落地目标</span></div><div class="lb">{lab}</div></div>')
        else:
            html.append(f'<div class="tile"><img src="{src}" alt="{lab}"><div class="lb">{lab}</div></div>')
    html.append('</div></div>')

html.append(f'<div class="req"><h2>APP 功能优化需求 · 摘要（{REQ_N} 条转需求）</h2><table>')
html.append('<tr><th>ID</th><th>优先级</th><th>需求 / 对齐项</th><th>工程现状要点</th></tr>')
for g in reqs:
    pc = "p1" if g["priority"] == "P1" else "p2"
    cat = CAT_LABEL[g["category"]]
    tag_cls = "tag-rm" if g["category"] == "ui_remove" else "tag"
    html.append(f'<tr><td><b>{g["id"]}</b></td><td class="{pc}">{g["priority"]}</td>'
                f'<td><span class="{tag_cls}">{cat}</span>{g["title"]}</td>'
                f'<td>{g["current"][:120]}…</td></tr>')
html.append('</table><div class="note">仅记录、不转需求：' + "；".join(f'<b>{g["id"]}</b> {g["title"]}' for g in notes) + '</div></div>')

html.append('''<div class="frozen"><b>冻结契约（本次完整 UI 未改动、也不应改动）</b><br>
1. Tab 渲染序：钓鱼指数 → 潮流(水位) → 天气 → 水质 → 风浪<br>
2. 免责恒 5 条原文（工程 D11「现场校潮」长文为准） · 3. 五档/六徽标色 = ScoreBands.kt / ConfidenceBadge.kt 冻结单源<br>
4. DaySelector 两行 52dp · 水质 1×3（pH 已删） · 5. 地图优先、无底部 NavigationBar、准星唯一选点真源 F-1.3<br>
6. 双水体六重区分：徽标 / 配色 / 鱼种 / 第二 Tab / 图表单位 / 地点 · 7. 示例数值非业务数据，源更新时间显示「—」<br>
8. 换皮肤(OPT-A-09)只替换视觉 token，以上逐项不变；明暗(OPT-A-03)为与皮肤正交的独立轴 · 皮肤扩展规范见 SKIN-DESIGN-SPEC.md</div>
<div class="foot">视觉概念稿（30 屏）+ 需求清单。详情页已做 Compose 试点（<b>VintageOutdoorTheme.kt</b> 等，工程 <b>tidespot-android/</b>），四态真机对拍见 <b>device-compare/真机对拍报告.md</b>，落地说明见 <b>Compose试点落地说明.md</b>；全 App 换肤已落地（<b>TideSpotSkin.kt</b>：A/D 两款内置，v1.17.0），皮肤入口=设置页下拉。逐条证据 / 验收 / 工程落点见 APP功能优化需求.md。<br>复现：generator/engine.py（早期 base 7 屏）→ engine_full.py（新稿 21 补齐屏 + 改版设置页）→ gen_full_index.py（本索引，含参照稿勘误）</div>
</div></body></html>''')

OUT.write_text("\n".join(html), encoding="utf-8")
print("INDEX_HTML", OUT.name, OUT.stat().st_size // 1024, "KB", "tiles=", sum(len(g[2]) for g in groups))
