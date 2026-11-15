# -*- coding: utf-8 -*-
"""
潮点 TideSpot · 手绘 A（复古户外）【完整 UI】扩展引擎
=========================================================
在 engine.py（4 风格基础 7 屏）之上，为 A-vintage 补齐到「覆盖全功能」的完整 UI：
  · 详情页 5 个 Tab 变体（指数已有 → 补 潮流/水位、天气、水质、风浪/湖面风）
  · 因子分解展开态（S03）
  · 6 个叠层/对话框/Sheet（Top10、免责5条、确认收藏、水质录入、星标气泡、删除确认）
  · 状态态（离线/陈旧/48h、分维降级、空态、多选批量）
  · 深色地图 / 深色收藏

全部复用 engine.py 的 A-vintage 调色板、手绘滤镜、鱼形 SVG、页面 CSS —— 视觉语言零偏差。
工程事实锁定：Tab 序 / 免责恒5条 / 五档六徽标 / DaySelector 两行 / 水质1×3 / 无底部导航 / 地图优先 / 双水体六重区分。
设计侧「完整 UI」清单来源：design-handoff/screens/screens.json（9屏+6叠层+4深色）、components/components.json（25组件）。

输出：A-vintage-outdoor/sources_full/*.html（自包含）+ 供渲染脚本批量出 2x PNG。
"""
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import engine as E

SID = "a-vintage"                 # 默认风格：手绘 A 复古户外
SITE = HERE.parent / "A-vintage-outdoor"
OUT = SITE / "sources_full"
OUT.mkdir(parents=True, exist_ok=True)

C = lambda water: E.pal(SID, water)
H = E._h


def configure(style_id, site_dir):
    """切换到指定皮肤与站点目录。

    所有渲染函数在调用时读全局 SID / OUT / C，因此只需在 build() 前调用一次；
    默认不调用时保持 A-vintage 行为（`python engine_full.py` 零参数兼容旧命令）。
    """
    global SID, SITE, OUT, C
    SID = style_id
    SITE = Path(site_dir)
    OUT = SITE / "sources_full"
    OUT.mkdir(parents=True, exist_ok=True)
    C = lambda water: E.pal(SID, water)


# ---------------------------------------------------------------------------
# 通用页面骨架（390×844），复刻 engine.py 的 page_css + svg_defs + grain
# ---------------------------------------------------------------------------
def shell(body, water, title, bg_override=None):
    p = C(water)
    css = E.page_css(SID, water)
    if bg_override:
        css += f"\n.bgfill{{background:{bg_override}!important}}"
    return f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>TideSpot · {E.STYLES[SID]["name"]} · {title}</title>
<style>{css}</style></head><body>
{E.svg_defs(SID, water)}
<div class="bgfill"></div>
<div class="screen">{body}</div>
{E.grain_overlay()}
</body></html>"""


def statusbar(p):
    return f"""<div class="statusbar"><span>9:41</span><div class="r"><span>▮▮▮</span><span>◕</span><span>▰</span></div></div><div class="notch"></div>"""


def screen_inner_of(html):
    """稳健提取 <div class="screen">…</div> 的内部内容（到 grain 覆盖层之前）。
    engine.py 结构：<div class="screen">{...}</div>\n{grain_overlay()}\n</body>"""
    if '<div class="screen">' not in html:
        return html
    inner = html.split('<div class="screen">', 1)[1]
    # grain 覆盖层是 screen 之后的第一个 <svg class="grain">
    if '<svg class="grain"' in inner:
        inner = inner.split('<svg class="grain"', 1)[0]
    # 去掉结尾处 screen 的闭合 div 与换行
    inner = inner.rstrip()
    if inner.endswith('</div>'):
        inner = inner[: -len('</div>')]
    return inner


def topbar_back(p, title, sub=None, right=None):
    """详情页/叠层通用顶栏：返回 + 标题 + 副标 + 右操作"""
    ink, muted, line, card = H(p['ink']), H(p['muted']), H(p['line']), H(p['card'])
    return f"""<div class="row" style="padding:6px 16px 8px;position:relative;z-index:5;border-bottom:1.6px dashed {line}55">
      <span style="font-size:22px;color:{ink};width:26px">‹</span>
      <div style="flex:1"><div class="disp" style="font-size:18px;color:{ink};line-height:1.15">{title}</div>
        {f'<div class="bodyf" style="font-size:10px;color:{muted};margin-top:2px">{sub}</div>' if sub else ''}</div>
      <div class="row" style="gap:8px">{right or ''}</div>
    </div>"""


def sec_title(p, text, note=None, accent=True):
    ink, muted, accent_c = H(p['ink']), H(p['muted']), H(p['accent'])
    return f"""<div class="row" style="justify-content:space-between;margin:1px 16px 0;position:relative;z-index:4">
      <span class="disp" style="font-size:14px;color:{ink}">{'❦ ' if accent else ''}{text}</span>
      {f'<span class="bodyf" style="font-size:9.5px;color:{muted}">{note}</span>' if note else ''}</div>"""


def mkcard(p, inner, style_extra=""):
    return f"""<div class="card" style="margin:8px 14px 0;padding:10px 12px;position:relative;z-index:4;{style_extra}">{inner}</div>"""


def badge(p, text, tone=None):
    """六徽标色（冻结语义，落地回归 ConfidenceBadge.kt）"""
    tone = tone or p['green']
    return f"""<span class="tag" style="font-size:9px;padding:1px 7px;border-width:1.6px;border-color:{H(p['line'])};background:{H(tone)};color:{H(p['white'])}">{text}</span>"""


def disclaimer_entry(p, items_idx="1–5", docked=False):
    """免责常驻入口条（恒5条）。docked=True 时绝对值钉在屏幕底部，作为页面收尾。"""
    pos = "position:absolute;left:0;right:0;bottom:14px;" if docked else "position:relative;z-index:6;"
    mg = "margin:0 14px;" if docked else "margin:9px 16px 0;"
    return f"""<div class="row" style="{mg}{pos}padding:8px 12px;border:1.8px dashed {H(p['line'])}88;border-radius:11px;background:{H(p['card'])}EE;z-index:6;gap:8px;box-shadow:0 -2px 8px {H(p['bg2'])}AA">
      <span style="font-size:14px;color:{H(p['accent'])}">ⓘ</span>
      <span class="bodyf" style="font-size:10.5px;color:{H(p['muted'])};flex:1">免责声明 · 共 5 条 · 数据仅供参考</span>
      <span style="color:{H(p['primary'])};font-size:13px">›</span></div>"""


def tab_footer(p, water, disclaimer_idx="1–5"):
    """详情 Tab 页统一底部收尾：图表⇄数值表切换 + 来源行 + 钉底免责条 + 手势区。
    放在内容之下，让下半部有完整落地感（F-3.2 / F-3.7 / F-3.6 功能）。"""
    line, card, card2, muted, primary, ink = (
        H(p['line']), H(p['card']), H(p['card2']), H(p['muted']), H(p['primary']), H(p['ink']))
    switch = f"""<div class="row" style="margin:12px 14px 0;gap:8px;position:relative;z-index:4">
      <span class="tag" style="font-size:9.5px;background:{primary};color:{H(p['white'])};border-color:{line}">▮ 逐小时曲线</span>
      <span class="tag" style="font-size:9.5px">☷ 等价数值表</span>
      <span class="bodyf" style="font-size:8.5px;color:{muted};margin-left:auto">24 行 · TalkBack 可读</span></div>"""
    source = f"""<div class="bodyf" style="margin:9px 16px 0;font-size:9px;color:{muted};text-align:center;line-height:1.6;position:relative;z-index:4">
      来源 Open-Meteo · 网格 8 km · UTC 更新 — · 本机拉取 09:41<br>null 断线不画 0 · 缺维按有效权重重算（F-3.6）</div>"""
    return switch + source + disclaimer_entry(p, disclaimer_idx, docked=True)


# ---------------------------------------------------------------------------
# 详情 Tab 通用骨架：顶栏 + 水体徽标 + DaySelector + 五 Tab（选中位可变）+ 内容
# ---------------------------------------------------------------------------
def day_selector(p):
    ink, line, card, accent, white = H(p['ink']), H(p['line']), H(p['card']), H(p['accent']), H(p['white'])
    days = []
    for i, (d, wd) in enumerate([("今天", "周四"), ("13", "周五"), ("14", "周六"), ("15", "周日"), ("16", "周一")]):
        sel = i == 0
        days.append(f"""<div style="width:60px;height:52px;border:2px solid {line};border-radius:16px;display:flex;flex-direction:column;align-items:center;justify-content:center;
          background:{accent if sel else card};color:{white if sel else ink};box-shadow:{('2px 2px 0 '+line+'55') if sel else 'none'};flex-shrink:0">
          <b class="disp" style="font-size:{14 if sel else 16}px;line-height:1">{d}</b><span class="bodyf" style="font-size:9.5px;margin-top:3px;opacity:0.9">{wd}</span></div>""")
    return f"""<div class="row" style="gap:6px;padding:8px 14px 0;position:relative;z-index:4">{''.join(days)}</div>
    <div class="bodyf mut" style="font-size:9px;margin:3px 16px 0;position:relative;z-index:4;text-align:right">源更新 — · 本机拉取 09:41</div>"""


def tab_bar(p, water, active):
    """五维 Tab 渲染序锁定；图标=鱼；选中位由 active 索引决定"""
    tabs = E.TAB_SEA if water == "sea" else E.TAB_INLAND
    line, card, accent, muted = H(p['line']), H(p['card']), H(p['accent']), H(p['muted'])
    items = []
    for i, t in enumerate(tabs):
        sel = i == active
        items.append(f"""<div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:2px;padding:6px 0 4px;border-bottom:{('3px solid '+accent) if sel else '0'};opacity:{1 if sel else 0.72}">
          <div style="height:20px">{E.fish_icon(SID, water, (accent if sel else p['muted']), 20)}</div>
          <span class="bodyf" style="font-size:9.5px;font-weight:{700 if sel else 400};color:{accent if sel else muted};white-space:nowrap">{t}</span>
        </div>""")
    return f"""<div style="margin:8px 14px 0;border:2px solid {line};border-radius:14px;background:{card};display:flex;position:relative;z-index:4">{''.join(items)}</div>"""


def detail_head(p, water, active, coord="36.05°N 120.44°E", place="青岛 · 小麦岛外海"):
    """详情页上半：状态栏 + 顶栏 + 水体徽标 + DaySelector + Tab"""
    wc = E.WATER_COPY[water]
    motif = E.water_motif(SID, water, 52)
    right = f"""<span style="width:30px;height:30px;border:2px solid {H(p['line'])};border-radius:50%;display:flex;align-items:center;justify-content:center;background:{H(p['card'])}">{E.fish_icon(SID, water, p['accent2'], 20)}</span>"""
    head = topbar_back(p, place, f"⌖ {coord} · 准星落点", right=right)
    tagline = f"""<div style="padding:6px 16px 0;position:relative;z-index:5">
      <span class="tag" style="background:{H(p['card'])};color:{H(p['ink'])};border-color:{H(p['line'])};box-shadow:2px 2px 0 {H(p['line'])}55">{motif}<b style="font-size:11px">{wc['badge']}</b><i class="disp" style="font-style:normal;font-size:8px;opacity:.6;letter-spacing:.1em;margin-left:4px">{wc['badge_en']}</i></span></div>"""
    return statusbar(p) + head + tagline + day_selector(p) + tab_bar(p, water, active)


def mini_chart(p, water, mode="line", series=None, h=96, labels=True):
    """手绘折线/柱状图；mode=wave/temp/oxygen/turbidity/rain/tide"""
    ink, line, accent, green, muted = H(p['ink']), H(p['line']), H(p['accent']), H(p['green']), H(p['muted'])
    series = series or [30, 48, 70, 40, 58, 86, 64, 36, 52]
    if mode == "bar":
        bars = "".join(
            f"""<rect x="{18+i*44}" y="{h-8-int(v*0.6)}" width="26" height="{int(v*0.6)}" rx="5" fill="{green if i!=5 else accent}" stroke="{line}" stroke-width="1.8" filter="url(#rough2)"/>"""
            f"""{f'<text x="{31+i*44}" y="{h+4}" font-size="8" fill="{muted}" text-anchor="middle" font-family="KaiTi">{6+i}</text>' if labels else ''}"""
            for i, v in enumerate(series))
        inner = bars + f"""<path d="M10 {h-8} L410 {h-8}" stroke="{line}" stroke-width="1.3" opacity="0.35" filter="url(#rough2)"/>"""
    else:
        pts = " ".join(f"{20+i*46},{h-12-int(v*0.72)}" for i, v in enumerate(series))
        inner = f"""<path d="M10 {h-8} L410 {h-8}" stroke="{line}" stroke-width="1.3" opacity="0.35" filter="url(#rough2)"/>
          <polyline points="{pts}" fill="none" stroke="{accent}" stroke-width="2.8" stroke-linecap="round" filter="url(#rough2)"/>"""
        if labels:
            inner += "".join(f"""<text x="{20+i*46}" y="{h+4}" font-size="8" fill="{muted}" text-anchor="middle" font-family="KaiTi">{6+i}</text>""" for i in range(len(series)))
    return f"""<svg width="100%" height="{h+10}" viewBox="0 0 420 {h+10}" aria-hidden="true">{inner}</svg>"""


def fish_footer(p, n=3, color=None):
    c = color or p['accent2']
    return "".join(E.fish_icon(SID, "sea", c, sz) for sz in (18, 22, 18)) if n == 3 else E.fish_icon(SID, "sea", c, 20)


# ---------------------------------------------------------------------------
# 详情 Tab 变体 1：潮流 / 水位（S07）— 海洋潮高曲线 vs 内陆无潮汐
# ---------------------------------------------------------------------------
def render_tab_tide(water):
    p = C(water); wc = E.WATER_COPY[water]
    ink, line, card, card2, accent, accent2, green, muted, primary, white = (
        H(p['ink']), H(p['line']), H(p['card']), H(p['card2']), H(p['accent']), H(p['accent2']),
        H(p['green']), H(p['muted']), H(p['primary']), H(p['white']))
    if water == "sea":
        note = "未校正"  # 未校正不假装已校正
        chart_note = f"{wc['hi']} 14:20 · {wc['lo']} 08:10"
        body = sec_title(p, "潮高 · 潮流", "海洋 · dh/dt 副轴")
        body += mkcard(p, f"""
          <div class="row" style="justify-content:space-between"><span class="disp" style="font-size:12px;color:{primary}">潮高 m · 流速 kn</span>{badge(p, "模型", p['line'])}{f'<span class="tag" style="font-size:8.5px;border-color:{line}">{note}</span>'}</div>
          {mini_chart(p, water, 'line', [30,46,72,88,70,44,30,52,78], 92)}
          <div class="row" style="gap:5px;margin-top:2px">{badge(p,'未校正','8A3B2E')}<span class="bodyf mut" style="font-size:9px">涨 ↓ · 退 ↑ 标记 · 校正前后对照</span></div>""")
        body += mkcard(p, f"""
          <div class="disp" style="font-size:12px;color:{primary}">现场校潮 · 校正面板</div>
          <div class="bodyf mut" style="font-size:9.5px;line-height:1.6;margin-top:5px">无实测参考源时，可记录一次涨潮时刻，用 lag 平移经验修正模型滞后。</div>
          <div class="row" style="gap:8px;margin-top:8px"><div style="flex:1;border:2px solid {line};border-radius:9px;text-align:center;padding:7px;background:{accent};color:{white}"><b class="bodyf" style="font-size:11px">⌖ 记录一次涨潮时刻</b></div>
            <div style="border:1.8px solid {line};border-radius:9px;padding:7px 12px" class="bodyf" style="font-size:11px">选时间</div></div>""", f"border-color:{accent}88")
    else:
        body = sec_title(p, "水位（无潮汐）", "内陆 · 不画潮汐曲线")
        body += mkcard(p, f"""
          <div class="row" style="justify-content:space-between"><span class="disp" style="font-size:12px;color:{primary}">水位 m · 无潮汐</span>{badge(p, "估算", '8A6D2E')}</div>
          {mini_chart(p, water, 'line', [42,44,41,40,39,41,43,42,40], 92)}
          <div class="bodyf mut" style="font-size:9.5px;line-height:1.5;margin-top:2px">湖库水位无潮汐涨落；本 Tab 不显示潮高 / 涨退箭头，避免误导。</div>""")
        body += mkcard(p, f"""
          <div class="row" style="gap:8px"><div style="width:40px">{E.water_motif(SID, water, 40)}</div>
            <div class="bodyf mut" style="font-size:9.5px;line-height:1.55">内陆水体判定：山+日+芦苇母题 · 咸水鱼换淡水鱼 · 第二 Tab 由「潮流」变「水位（无潮汐）」</div></div>""", f"background:{card2}")
    body += tab_footer(p, water, "1/5")
    return shell(statusbar(p) + detail_head(p, water, 1) + body, water, "潮流/水位 Tab")


# ---------------------------------------------------------------------------
# 详情 Tab 变体 2：天气（S05）— 气温/降水/云量/雷暴 + 安全摘要
# ---------------------------------------------------------------------------
def render_tab_weather(water="sea"):
    p = C(water)
    ink, line, card, card2, accent, accent2, green, muted, primary, white = (
        H(p['ink']), H(p['line']), H(p['card']), H(p['card2']), H(p['accent']), H(p['accent2']),
        H(p['green']), H(p['muted']), H(p['primary']), H(p['white']))
    body = sec_title(p, "逐小时天气", "气温 · 降水 · 云量")
    body += mkcard(p, f"""
      <div class="row" style="justify-content:space-between"><span class="disp" style="font-size:12px;color:{primary}">气温 ℃</span>{badge(p,'模型',p['line'])}</div>
      {mini_chart(p, water, 'line', [22,24,27,29,28,25,22,20,19], 80)}
      <div class="row" style="gap:6px;margin-top:4px">{badge(p,'降水 2.1 mm')}{badge(p,'云量 68%', p['green'])}<span class="tag" style="font-size:9px">WMO 803 · 多云</span></div>""")
    body += mkcard(p, f"""
      <div class="row" style="gap:8px"><span style="font-size:17px;color:{accent}">⚠</span><span class="disp" style="font-size:12.5px;color:{primary}">安全摘要</span>{badge(p,'低置信','8A6D2E')}</div>
      <div class="bodyf" style="font-size:10.5px;line-height:1.6;margin-top:6px;color:{ink}">雷暴风险 <b class="hl">中</b> · 阵风 &gt; 40 km/h · 午后有短时强降水。<br>水上安全以当地预警为准，切勿在雷电、台风、大潮汛期间出钓。</div>""", f"border-color:{accent}99;background:{card2}")
    body += tab_footer(p, water, "2")
    return shell(statusbar(p) + detail_head(p, water, 2) + body, water, "天气 Tab")


# ---------------------------------------------------------------------------
# 详情 Tab 变体 3：水质（S06）— 1×3 指标卡（pH 已删）+ 录入实测
# ---------------------------------------------------------------------------
def render_tab_water(water="sea"):
    p = C(water)
    ink, line, card, card2, accent, accent2, green, muted, primary, white = (
        H(p['ink']), H(p['line']), H(p['card']), H(p['card2']), H(p['accent']), H(p['accent2']),
        H(p['green']), H(p['muted']), H(p['primary']), H(p['white']))
    body = sec_title(p, "逐小时水温", "模型估算 · 卫星未接入")
    body += mkcard(p, f"""
      <div class="row" style="justify-content:space-between"><span class="disp" style="font-size:12px;color:{primary}">水温 ℃</span>{badge(p,'估算','8A6D2E')}</div>
      {mini_chart(p, water, 'line', [18,18,19,19,20,19,19,18,18], 74)}""")
    # 估算说明条
    body += f"""<div class="row" style="margin:8px 16px 0;gap:7px;position:relative;z-index:4;background:{card2}CC;border:1.5px dashed {line}77;border-radius:10px;padding:7px 10px">
      <span style="font-size:13px;color:{accent}">ⓘ</span><span class="bodyf mut" style="font-size:9.5px;line-height:1.5">模型估算 · 卫星水质数据未接入（仅供水体判定）</span></div>"""
    # 1 行 × 3 卡（112dp，clipsContent=false，徽标不被裁）
    cards = []
    for lab, val, unit, bd, tone in [("水温","19.6","℃","估算","8A6D2E"),("溶解氧","7.2","mg/L","模型",p['line']),("浊度","3.1","NTU","估算","8A6D2E")]:
        cards.append(f"""<div class="card" style="flex:1;min-height:112px;padding:9px 8px;overflow:visible;background:{card2}">
          <div class="row" style="justify-content:space-between"><span class="bodyf mut" style="font-size:10px">{lab}</span><span style="font-size:8.5px;color:{white};background:{H(tone)};border-radius:5px;padding:1px 4px">{bd}</span></div>
          <div class="disp" style="font-size:22px;color:{primary};margin-top:8px">{val}<span style="font-size:10px;color:{muted}"> {unit}</span></div>
          <div class="bodyf mut" style="font-size:8.5px;margin-top:6px">源更新 —</div></div>""")
    body += f"""<div class="row" style="gap:8px;margin:9px 14px 0;position:relative;z-index:4">{''.join(cards)}</div>"""
    body += f"""<div style="width:140px;margin:11px 14px 0;position:relative;z-index:4;border:2px dashed {green};border-radius:11px;text-align:center;padding:9px;background:{card}"><b class="bodyf" style="font-size:11px;color:{primary}">＋ 录入实测</b><div class="bodyf mut" style="font-size:8.5px;margin-top:2px">离线可录</div></div>"""
    body += tab_footer(p, water, "4")
    return shell(statusbar(p) + detail_head(p, water, 3) + body, water, "水质 Tab（1×3）")


# ---------------------------------------------------------------------------
# 详情 Tab 变体 4：风浪 / 湖面风与水温（S04）— 分维降级 + 内陆引导
# ---------------------------------------------------------------------------
def render_tab_wave(water="sea"):
    p = C(water); wc = E.WATER_COPY[water]
    ink, line, card, card2, accent, accent2, green, muted, primary, white = (
        H(p['ink']), H(p['line']), H(p['card']), H(p['card2']), H(p['accent']), H(p['accent2']),
        H(p['green']), H(p['muted']), H(p['primary']), H(p['white']))
    title = "风速 · 阵风 · 水温" if water == "inland" else "浪高 · 周期 · 风向"
    body = sec_title(p, title, "null 断线不画 0")
    body += mkcard(p, f"""
      <div class="row" style="justify-content:space-between"><span class="disp" style="font-size:12px;color:{primary}">{wc['chart']}</span>{badge(p,'模型',p['line'])}</div>
      {mini_chart(p, water, 'line', [34,42,58,72,66,50,38,46,60], 84)}
      <div class="row" style="gap:6px;margin-top:2px">{badge(p,'风速 5.2 m/s', p['green'])}{badge(p,'阵风 9.1 m/s', p['line'])}{badge(p,'水温 19.6 ℃','8A6D2E')}</div>""")
    # 分维降级卡（维度缺失时）
    body += mkcard(p, f"""
      <div class="row" style="gap:8px"><span style="font-size:15px;color:{accent}">◇</span><span class="disp" style="font-size:12px;color:{primary}">部分维度不可用</span></div>
      <div class="bodyf mut" style="font-size:9.5px;line-height:1.55;margin-top:5px">海洋接口超时：已按有效权重重算，天气 / 指数照常；缺失维度显示「—」而非 0，保留最近缓存时间。</div>""", f"border-color:{accent}99;background:{card2}")
    if water == "inland":
        body += mkcard(p, f"""
          <div class="row" style="gap:8px"><div style="width:34px">{E.water_motif(SID, water, 34)}</div><span class="disp" style="font-size:12px;color:{primary}">配置 Copernicus 账号（可选）</span></div>
          <div class="bodyf mut" style="font-size:9.5px;line-height:1.5;margin-top:4px">卫星数据可改善内陆水体判定精度。凭据仅本地保存，不上传。</div>
          <div class="row" style="gap:8px;margin-top:6px"><span class="tag" style="font-size:9px;background:{accent};color:{white};border-color:{line}">去设置配置</span><span class="tag" style="font-size:9px">暂不配置</span></div>""")
    body += tab_footer(p, water, "1/2")
    return shell(statusbar(p) + detail_head(p, water, 4) + body, water, "风浪/湖面风与水温 Tab")


# ---------------------------------------------------------------------------
# 详情 Tab 变体 0：钓鱼指数（S01/S02）— 首 Tab 新稿（此前缺失，旧稿在 base detail）
#   大分数 + 五档色带（档位=鱼数，恒带文字 N-7，冻结 ScoreBands.kt 语义）+ 潮高曲线 + 水质 1×3 + Top10
# ---------------------------------------------------------------------------
def score_body(p, water):
    """钓鱼指数(SCORE) 首 Tab 纯内容体（detail_head 之下、tab_footer 在内）。
    状态态亦复用本函数，保证新稿骨架 100% 一致（用户要求：新组件保持手绘 A 风格）。"""
    wc = E.WATER_COPY[water]
    ink, line, card, card2, accent, accent2, green, muted, primary, white = (
        H(p['ink']), H(p['line']), H(p['card']), H(p['card2']), H(p['accent']), H(p['accent2']),
        H(p['green']), H(p['muted']), H(p['primary']), H(p['white']))
    band = p['band']
    # 头部：大分数 + 档位
    body = sec_title(p, "钓鱼指数", "今日 · 综合评分")
    body += mkcard(p, f"""
      <div class="row" style="align-items:center;gap:12px">
        <div style="text-align:center;min-width:62px">
          <div class="disp" style="font-size:38px;color:{accent};line-height:1">{E.SCORE}</div>
          <div class="bodyf" style="font-size:10px;color:{muted}">/ 100</div></div>
        <div style="width:1.6px;height:42px;background:{line};opacity:.35"></div>
        <div style="flex:1">
          <div class="disp" style="font-size:16px;color:{primary}">{E.BAND_LABELS[E.SCORE_BAND]} <span class="hl">●</span></div>
          <div class="row" style="gap:5px;margin-top:5px">{badge(p, E.BADGES[0])}{badge(p, E.BADGES[2])}<span class="bodyf mut" style="font-size:10px">{wc['note']}</span></div>
        </div>
        <div style="width:40px;height:40px;border:2px dashed {green};border-radius:12px;display:flex;align-items:center;justify-content:center;transform:rotate(-6deg)">{E.fish_icon(SID, water, green, 28)}</div>
      </div>""")
    # 五档色带（档位=鱼数量 5→1，选中档加手绘描边；鱼恒配文字 N-7）
    bandfish = ""
    for i in range(5):
        sel = i == E.SCORE_BAND
        nfish = 5 - i
        fishe = "".join(
            f"<span style='display:inline-block;width:13px;height:13px;vertical-align:middle'>{E.fish_icon(SID, water, (white if i < 3 else ink), 13)}</span>"
            for _ in range(nfish))
        bgc = H(band[i]); txtc = white if i < 3 else ink
        bandfish += f"""<div style='flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;height:34px;background:{bgc};
          border:{'2.6px solid '+accent2 if sel else '1.6px solid '+line};border-radius:9px;opacity:{1 if sel else 0.72};
          box-shadow:{'2px 2px 0 '+line if sel else 'none'};gap:1px'>
          <span style='line-height:0;height:13px;white-space:nowrap;overflow:hidden'>{fishe}</span>
          <span style='font-size:8.5px;color:{txtc};font-weight:{700 if sel else 600};white-space:nowrap'>{E.BAND_LABELS[i]}</span></div>"""
    body += f"""<div style="margin:9px 16px 0;position:relative;z-index:4">
      <div class="row" style="gap:4px">{bandfish}</div>
      <div class="bodyf mut" style="font-size:9.5px;margin-top:4px;text-align:center">五档 · 档位恒带文字 · 鱼多者优</div></div>"""
    # CHG-94 / S0 定稿：SCORE 首 Tab 画「24 小时钓鱼指数柱」，与工程实现一致。
    # 旧稿在此处画潮高/水位折线（属 TIDE Tab 内容），造成「文件名 SCORE、图内 TIDE」的稿内不一致。
    body += sec_title(p, "24 小时钓鱼指数", "模型估算 · 逐小时")
    body += mkcard(p, f"""
      <div class="row" style="justify-content:space-between"><span class="disp" style="font-size:12px;color:{primary}">逐小时评分 / 100</span>{badge(p,'模型',p['line'])}</div>
      {mini_chart(p, water, 'bar', [42,55,68,74,62,86,79,58,46], 88)}
      <div class="bodyf mut" style="font-size:9px;margin-top:2px">柱高＝该小时综合分 · 高峰 14:00 · 源更新 —</div>""")
    # 水质 1×3（水温/溶解氧/浊度，pH 已删）
    cards = []
    for lab, val, unit, bd, tone in [("水温","19.6","℃","估算","8A6D2E"),("溶解氧","7.2","mg/L","模型",p['line']),("浊度","3.1","NTU","估算","8A6D2E")]:
        cards.append(f"""<div class="card" style="flex:1;min-height:112px;padding:9px 8px;overflow:visible;background:{card2}">
          <div class="row" style="justify-content:space-between"><span class="bodyf mut" style="font-size:10px">{lab}</span><span style="font-size:8.5px;color:{white};background:{H(tone)};border-radius:5px;padding:1px 4px">{bd}</span></div>
          <div class="disp" style="font-size:22px;color:{primary};margin-top:8px">{val}<span style="font-size:10px;color:{muted}"> {unit}</span></div>
          <div class="bodyf mut" style="font-size:8.5px;margin-top:6px">源更新 —</div></div>""")
    body += f"""<div class="row" style="gap:8px;margin:9px 14px 0;position:relative;z-index:4">{''.join(cards)}</div>"""
    # Top10 入口（鱼形排名）
    body += f"""<div style="margin:9px 14px 0;position:relative;z-index:4;border:2.2px solid {line};border-radius:14px;background:{primary};padding:8px 12px;display:flex;align-items:center;gap:10px;box-shadow:2px 3px 0 {line}44">
      <div class="row" style="gap:-2px">{E.fish_icon(SID,water,accent2,20)}{E.fish_icon(SID,water,accent2,24)}{E.fish_icon(SID,water,accent2,20)}</div>
      <div style="flex:1"><div class="disp" style="font-size:13px;color:{white}">全部收藏 Top10</div><div class="bodyf" style="font-size:9.5px;color:{white}AA">时段筛选 · 本周末窗口</div></div>
      <span style="color:{accent2};font-size:18px">›</span></div>"""
    body += tab_footer(p, water, "1/5")
    return body


def render_tab_score(water="sea"):
    p = C(water)
    return shell(statusbar(p) + detail_head(p, water, 0) + score_body(p, water), water, "钓鱼指数 Tab")


# ---------------------------------------------------------------------------
# 详情展开态：因子分解（S03）— 子分/权重/贡献条 → ΠV → 原始输入
# ---------------------------------------------------------------------------
def render_breakdown(water="sea"):
    p = C(water)
    ink, line, card, card2, accent, accent2, green, muted, primary, white = (
        H(p['ink']), H(p['line']), H(p['card']), H(p['card2']), H(p['accent']), H(p['accent2']),
        H(p['green']), H(p['muted']), H(p['primary']), H(p['white']))
    body = topbar_back(p, "因子分解", "所选小时 13:00 · 78 分 · 很适合", right='<span style="font-size:16px;color:' + primary + '">✕</span>')
    body += f"""<div class="row" style="margin:9px 16px 0;gap:6px;position:relative;z-index:4">{badge(p,'实测',p['line'])}{badge(p,'模型',p['line'])}<span class="bodyf mut" style="font-size:9px">禁止 parse 否决文案</span></div>"""
    # 子分表：名称 / 权重 / 得分 / 贡献条
    rows = [("溶氧 DO_p", "0.21", "0.95", 0.95), ("气压 P", "0.14", "0.79", 0.79),
            ("Solunar", "0.17", "0.62", 0.62), ("水温适温", "0.18", "0.88", 0.88),
            ("天气窗口", "0.15", "0.70", 0.70), ("潮汐相位", "0.15", "0.55", 0.55)]
    trs = ""
    for nm, w, sc, frac in rows:
        trs += f"""<div style="display:flex;align-items:center;gap:7px;padding:5px 0;border-bottom:1.2px dashed {line}44">
          <span class="bodyf" style="font-size:10.5px;color:{ink};width:74px">{nm}</span>
          <span class="bodyf" style="font-size:10px;color:{muted};width:30px">×{w}</span>
          <span class="disp" style="font-size:11px;color:{primary};width:30px">{sc}</span>
          <div style="flex:1;height:9px;border:1.4px solid {line}88;border-radius:6px;background:{card2};overflow:hidden"><div style="height:100%;width:{int(frac*100)}%;background:{green}"></div></div></div>"""
    body += mkcard(p, f"""<div class="disp" style="font-size:12.5px;color:{primary};margin-bottom:4px">子分 · 权重 · 贡献</div>{trs}""")
    # ΠV 明细
    body += mkcard(p, f"""
      <div class="disp" style="font-size:12.5px;color:{primary}">安全 / 可作钓性衰减 ΠV</div>
      <div class="bodyf" style="font-size:10px;line-height:1.7;margin-top:5px;color:{ink}">V = <b class="hl">0.95</b>（阵风 40 km/h）<br>V = <b class="hl">0.88</b>（雷暴风险 中）<br>ΠV = 0.95 × 0.88 = <b class="hl">0.836</b></div>
      <div class="bodyf mut" style="font-size:8.8px;margin-top:4px">连续 sigmoid，非硬阈值；触发行以芥末黄标记。</div>""", f"border-color:{accent2}")
    # 原始输入
    body += mkcard(p, f"""
      <div class="disp" style="font-size:12.5px;color:{primary}">原始输入</div>
      <div class="bodyf mut" style="font-size:9.5px;line-height:1.7;margin-top:4px">Tw 19.6℃ · DO_p 0.95 · P 1013 / ΔP3 −1.2 / ΔP24 +2.1<br>风 5.2 / 阵风 9.1 · 雨 2.1 · 云 68% · WMO 803<br>浪 0.8m · 潮位 1.4m · dh/dt +0.21 · 流速 0.6kn · 月龄 0.42 · 中天 12:48</div>""", f"background:{card2}")
    body += tab_footer(p, water, "3")
    return shell(statusbar(p) + body, water, "因子分解展开")


# ---------------------------------------------------------------------------
# 叠层通用：半透明遮罩 + 底部 Sheet / 居中 Dialog
# ---------------------------------------------------------------------------
def scrim(p, top=0):
    return f"""<div style="position:absolute;left:0;right:0;top:{top}px;bottom:0;background:{H(p['ink'])}99;z-index:40"></div>"""


def sheet_frame(p, inner, title, height=430):
    line, card, ink = H(p['line']), H(p['card']), H(p['ink'])
    return f"""<div style="position:absolute;left:0;right:0;bottom:0;height:{height}px;background:{card};border:2.4px solid {line};border-radius:26px 26px 0 0;z-index:41;box-shadow:0 -5px 0 {line}33;overflow:hidden">
      <div style="width:52px;height:5px;border-radius:4px;background:{line};opacity:.4;margin:9px auto 2px"></div>
      <div class="disp" style="font-size:17px;color:{ink};padding:4px 18px 8px;border-bottom:1.6px dashed {line}55">{title}</div>
      <div style="padding:10px 16px">{inner}</div></div>"""


def dialog_frame(p, inner, title, height=300):
    line, card, ink = H(p['line']), H(p['card']), H(p['ink'])
    return f"""<div style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:340px;min-height:{height}px;background:{card};border:2.4px solid {line};border-radius:20px;z-index:41;box-shadow:4px 6px 0 {line}44;overflow:hidden">
      <div class="disp" style="font-size:17px;color:{ink};padding:14px 18px 8px">{title}</div>
      <div style="padding:0 18px 14px">{inner}</div></div>"""


def dialog_buttons(p, confirm="确认", cancel="取消", tone="ok"):
    line, card, ink = H(p['line']), H(p['card']), H(p['ink'])
    bg = H(p['accent']) if tone == "ok" else H(p['accent2'])
    tc = H(p['white']) if tone in ("ok", "danger") else ink
    return f"""<div class="row" style="gap:10px;margin-top:14px">
      <div style="flex:1;border:2px solid {line};border-radius:11px;text-align:center;padding:10px;background:{card}"><b class="bodyf" style="font-size:12px;color:{ink}">{cancel}</b></div>
      <div style="flex:1;border:2px solid {line};border-radius:11px;text-align:center;padding:10px;background:{bg};color:{tc}"><b class="bodyf" style="font-size:12px">{confirm}</b></div></div>"""


# ---------------------------------------------------------------------------
# 新稿底页：地图（保留手绘地图底图）/ 收藏。供 OV 叠层与 #84 主屏共用
# ---------------------------------------------------------------------------
def map_canvas(p, water):
    """手绘地图底图（保留原稿）：land/water/road 三色 + 路网，不重绘。"""
    return f"""<svg width="390" height="844" viewBox="0 0 390 844" preserveAspectRatio="xMidYMid slice" aria-hidden="true" style="position:absolute;inset:0;z-index:1">
      <rect width="390" height="844" fill="{H(p['map_land'])}"/>
      <path d="M-20 470 Q80 430 170 480 Q260 530 340 470 Q390 440 410 470 L410 864 L-20 864 Z" fill="{H(p['map_water'])}" filter="url(#rough2)"/>
      <path d="M-10 150 Q120 210 230 130 Q320 70 400 150" fill="none" stroke="{H(p['map_road'])}" stroke-width="14" filter="url(#rough2)"/>
      <path d="M60 -10 Q110 300 40 560 Q10 700 80 860" fill="none" stroke="{H(p['map_road'])}" stroke-width="12" filter="url(#rough2)"/>
      <path d="M300 -10 Q250 300 330 560" fill="none" stroke="{H(p['map_road'])}" stroke-width="10" filter="url(#rough2)"/>
      <path d="M-10 320 Q390 300 400 330" fill="none" stroke="{H(p['map_road'])}" stroke-width="8" filter="url(#rough2)"/>
      <g fill="none" stroke="{H(p['line'])}" stroke-width="1.1" opacity="0.32" filter="url(#rough2)">
        <rect x="150" y="180" width="46" height="34"/><rect x="230" y="220" width="60" height="26"/><rect x="120" y="600" width="52" height="30"/>
        <path d="M0 640 Q100 620 180 660"/><path d="M210 380 Q280 360 340 400"/></g>
      <g fill="{H(p['green'])}" opacity="0.55"><circle cx="105" cy="250" r="9"/><circle cx="270" cy="560" r="11"/><circle cx="200" cy="700" r="8"/></g>
    </svg>"""


def render_map_new(water="sea"):
    """地图页（新稿骨架）：保留手绘地图底图，周边组件用新稿原语重画。"""
    p = C(water); wc = E.WATER_COPY[water]
    ink, line, card, card2, accent, accent2, green, muted, primary, white = (
        H(p['ink']), H(p['line']), H(p['card']), H(p['card2']), H(p['accent']), H(p['accent2']),
        H(p['green']), H(p['muted']), H(p['primary']), H(p['white']))
    motif = E.water_motif(SID, water, 54)
    cross = E.fish_icon(SID, water, p['accent'], 34)
    body = map_canvas(p, water)
    top = f"""<div style="position:absolute;left:0;right:0;top:38px;z-index:5;padding:8px 14px">
      <div class="card" style="height:44px;display:flex;align-items:center;gap:9px;padding:0 13px">
        <span style="font-size:17px;color:{accent}">⌕</span>
        <span class="bodyf" style="font-size:13px;color:{muted};flex:1">搜索地点 / 水域</span>
        <span style="width:1.5px;height:20px;background:{line};opacity:.3"></span>
        <span style="font-size:15px;color:{primary}">☷</span></div>
      <div style="margin-top:8px;display:flex;gap:7px;align-items:center;flex-wrap:wrap">
        <span class="tag" style="background:{card};color:{ink};border-color:{line};box-shadow:2px 2px 0 {line}55">{motif}<b style="font-size:11px">{wc['badge']}</b></span>
        <span class="tag" style="background:{card};color:{muted};border-color:{line};font-size:10px">Map data · OpenStreetMap · CARTO</span></div></div>"""
    crosshair = f"""<div style="position:absolute;left:50%;top:46%;transform:translate(-50%,-50%);z-index:4">
      <svg width="78" height="78" viewBox="0 0 78 78" aria-hidden="true"><g stroke="{white}" stroke-width="3" stroke-linecap="round" filter="url(#rough2)">
        <path d="M39 2 L39 20 M39 58 L39 76 M2 39 L20 39 M58 39 L76 39"/></g>
        <circle cx="39" cy="39" r="24" fill="none" stroke="{white}" stroke-width="2.4" opacity="0.92" filter="url(#rough2)"/></svg>
      <div style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%)">{cross}</div>
      <div class="bodyf" style="position:absolute;left:50%;top:calc(50% + 46px);transform:translateX(-50%);white-space:nowrap;background:{ink}CC;color:{white};font-size:10px;padding:3px 10px;border-radius:10px">⌖ 准星落点 · 拖动地图选点</div></div>"""
    tools = f"""<div style="position:absolute;right:14px;top:150px;z-index:5;display:flex;flex-direction:column;gap:9px">
      <div class="card" style="width:40px;height:40px;display:flex;align-items:center;justify-content:center;font-size:18px;color:{primary}">◎</div>
      <div class="card" style="width:40px;height:40px;display:flex;align-items:center;justify-content:center;font-size:17px;color:{primary}">▤</div></div>"""
    bottom = f"""<div style="position:absolute;left:0;right:0;bottom:0;z-index:5;padding:0 12px 16px">
      <div class="card" style="padding:12px 14px;box-shadow:2px 3px 0 {line}44">
        <div class="row" style="justify-content:space-between;align-items:flex-start">
          <div><div class="disp" style="font-size:16px;color:{ink}">{wc['place']}</div>
               <div class="bodyf mut" style="font-size:10px;margin-top:2px">⌖ {wc['coord']}</div></div>
          <span class="tag" style="font-size:10px;background:{accent2};color:{ink};border-color:{line}">{wc['water_short']}</span></div>
        <div class="row" style="gap:9px;margin-top:10px;align-items:center">
          <div class="disp" style="font-size:30px;color:{accent};line-height:1">{E.SCORE}</div>
          <div style="flex:1"><div class="disp" style="font-size:14px;color:{primary}">{E.BAND_LABELS[E.SCORE_BAND]}</div>
              <div class="bodyf mut" style="font-size:9.5px;margin-top:2px">点击查看 {wc['chart']} · 五档指数</div></div>
          <div style="width:46px;height:46px;border:2px dashed {green};border-radius:12px;display:flex;align-items:center;justify-content:center;transform:rotate(-6deg)">{E.fish_icon(SID, water, green, 32)}</div></div>
        <div style="margin-top:10px;border-top:1.6px dashed {line}55;padding-top:9px;display:flex;align-items:center;gap:9px">
          <div class="row">{E.fish_icon(SID,water,accent2,18)}{E.fish_icon(SID,water,accent2,22)}{E.fish_icon(SID,water,accent2,18)}</div>
          <div class="bodyf" style="font-size:11px;color:{primary};flex:1;font-weight:700">全部收藏 Top10 · 最佳窗口</div><span style="color:{accent};font-size:16px">›</span></div></div></div>"""
    return shell(statusbar(p) + body + top + crosshair + tools + bottom, water, "地图选点 · 新稿")


def render_favorites_new(water="sea"):
    """收藏页（新稿骨架）：列表行用 fav_item 新稿原语，与多选/空态同源。"""
    p = C(water)
    line, card, card2, ink, muted, primary, white, accent2, green = (
        H(p['line']), H(p['card']), H(p['card2']), H(p['ink']), H(p['muted']),
        H(p['primary']), H(p['white']), H(p['accent2']), H(p['green']))
    head = f"""<div style="padding:6px 16px 8px;position:relative;z-index:3">
      <div class="disp" style="font-size:24px;color:{ink}">我的收藏</div>
      <div class="bodyf mut" style="font-size:11px;margin-top:2px">12 个点位 · 3 个分组</div></div>
    <div style="padding:0 14px;position:relative;z-index:3"><div class="card" style="height:40px;display:flex;align-items:center;gap:8px;padding:0 12px">
      <span style="font-size:16px;color:{accent2}">⌕</span><span class="bodyf" style="font-size:12px;color:{muted}">搜索收藏点位</span>
      <span style="margin-left:auto;font-size:13px;color:{primary}">☷</span></div></div>
    <div class="row" style="padding:9px 14px 0;gap:6px;position:relative;z-index:3">
      <span class="tag" style="background:{primary};color:{white};border-color:{line};font-size:10px">综合推荐 ▾</span>
      <span class="tag" style="font-size:10px">全部分组 ▾</span>
      <span class="tag" style="font-size:10px;margin-left:auto">⠿ 批量</span></div>"""
    items = [
        ("小麦岛外海", "青岛 · 海域", "86", "爆护窗口", 0),
        ("鲤鱼湾", "千岛湖 · 湖湾", "78", "很适合", 1),
        ("东澳礁", "珠海 · 近海", "64", "可以", 2),
    ]
    rows = "".join(fav_item(p, name, sub, score, band, False) for (name, sub, score, band, bi) in items)
    extra = f"""<div style="margin:11px 14px 0;border:2.2px solid {line};border-radius:14px;background:{primary};padding:10px 13px;display:flex;align-items:center;gap:9px;box-shadow:2px 3px 0 {line}44">
      <div class="row">{E.fish_icon(SID,water,accent2,18)}{E.fish_icon(SID,water,accent2,22)}{E.fish_icon(SID,water,accent2,18)}</div>
      <div style="flex:1"><div class="disp" style="font-size:13px;color:{white}">全部收藏 Top10</div><div class="bodyf" style="font-size:9.5px;color:{white}AA">本周末 · 时段筛选</div></div><span style="color:{accent2};font-size:17px">›</span></div>"""
    return shell(statusbar(p) + head + rows + extra, water, "收藏 · 新稿")


# ---------------------------------------------------------------------------
# OV01 Top10 BottomSheet — 时段筛选四 Chip + 排名(鱼群) + 空态禁止兜底造假
# ---------------------------------------------------------------------------
def render_ov_top10(water="sea"):
    p = C(water); line, card, card2, accent, accent2, green, muted, primary, ink = (
        H(p['line']), H(p['card']), H(p['card2']), H(p['accent']), H(p['accent2']), H(p['green']), H(p['muted']), H(p['primary']), H(p['ink']))
    base = render_tab_score(water)  # 背景：新稿详情指数页
    screen_inner = screen_inner_of(base)
    chips = "".join(f"""<span class="tag" style="font-size:9.5px;{'background:'+primary+';color:'+H(p['white']) if i==1 else ''}">{t}</span>"""
                    for i, t in enumerate(["全部", "接下来 24h", "本周末", "自选时段"]))
    ranks = [("1", "明日 17:00", "4.0 h", "92", "爆护窗口", 0), ("2", "周六 06:00", "2.5 h", "86", "爆护窗口", 0), ("3", "周日 18:00", "3.0 h", "81", "窗口", 0)]
    rhtml = ""
    for rk, tm, dur, sc, band, bi in ranks:
        fishline = "".join(E.fish_icon(SID, water, H(p['white']), 11) for _ in range(5 - bi))
        rhtml += f"""<div class="row" style="gap:9px;padding:8px 0;border-bottom:1.3px dashed {line}55">
          <span class="disp" style="font-size:17px;color:{accent2};width:20px">{rk}</span>
          <div style="flex:1"><div class="disp" style="font-size:12px;color:{ink}">{tm} · {dur}</div>
            <div class="row" style="gap:4px;margin-top:3px"><span style="display:inline-flex;background:{H(p['band'][bi])};border-radius:6px;padding:1px 6px;gap:1px">{fishline}<span class="bodyf" style="font-size:8.5px;color:{H(p['white'])};margin-left:3px">{band}</span></span>
              <span class="bodyf mut" style="font-size:8.5px">Top2 主因：溶氧 · 潮汐</span></div></div>
          <span class="disp" style="font-size:20px;color:{accent}">{sc}</span></div>"""
    inner = f"""<div class="row" style="gap:6px;margin-bottom:4px">{chips}</div>{rhtml}
      <div class="bodyf mut" style="font-size:9px;text-align:center;margin-top:8px">无 ≥60 分窗口时显示空态，禁止兜底造假 · 可滚动到 24 行数值表</div>"""
    body = screen_inner + scrim(p, 0) + sheet_frame(p, inner, "全部收藏 Top10 · 时段筛选", 440)
    return shell(body, water, "OV01 Top10 时段筛选")


# ---------------------------------------------------------------------------
# OV02 免责 BottomSheet — 恒 5 条、顺序 1..5、原文完整、可滚动
# ---------------------------------------------------------------------------
def render_ov_disclaimer(water="sea"):
    p = C(water); line, card, ink, muted, accent = H(p['line']), H(p['card']), H(p['ink']), H(p['muted']), H(p['accent'])
    base = render_tab_score(water)  # 新稿详情页
    screen_inner = screen_inner_of(base)
    items = "".join(f"""<div class="row" style="gap:8px;align-items:flex-start;padding:7px 0;border-bottom:1.2px dashed {line}44">
        <span class="disp" style="font-size:13px;color:{accent};width:16px">{i}.</span>
        <span class="bodyf" style="font-size:10px;line-height:1.65;color:{ink}">{E.DISCLAIMERS[i-1]}</span></div>""" for i in range(1, 6))
    inner = f"""{items}<div style="height:24px"></div><div class="bodyf mut" style="font-size:9px;text-align:center">模型署名 · Open-Meteo / Copernicus · 以上原文照用，顺序恒 1..5</div>"""
    body = screen_inner + scrim(p, 0) + sheet_frame(p, inner, "免责声明 · 共 5 条", 560)
    return shell(body, water, "OV02 免责 5 条")


# ---------------------------------------------------------------------------
# OV03 确认收藏对话框 — 预填逆地理名 + 不遮挡信息条
# ---------------------------------------------------------------------------
def render_ov_confirm_fav(water="sea"):
    p = C(water); line, card, card2, ink, muted, primary, white = (
        H(p['line']), H(p['card']), H(p['card2']), H(p['ink']), H(p['muted']), H(p['primary']), H(p['white']))
    base = render_map_new(water)  # 新稿地图（保留手绘底图）
    screen_inner = screen_inner_of(base)
    inner = f"""<div class="bodyf mut" style="font-size:9.5px;margin-bottom:4px">⌖ 36.050000, 120.440000 · 青岛 · 小麦岛外海</div>
      <div style="border:1.8px solid {line};border-radius:10px;padding:8px 10px;margin-bottom:7px"><div class="bodyf mut" style="font-size:8.5px">名称（必填）</div><div class="bodyf" style="font-size:12px;color:{ink};margin-top:2px">小麦岛外海</div></div>
      <div class="row" style="gap:7px"><div style="flex:1;border:1.5px solid {line};border-radius:9px;padding:7px 9px"><div class="bodyf mut" style="font-size:8.5px">备注 / 标签</div><div class="bodyf" style="font-size:10.5px;color:{muted}">海水 · 岸钓</div></div>
        <div style="border:1.5px solid {line};border-radius:9px;padding:7px 9px"><div class="bodyf mut" style="font-size:8.5px">物种</div><div class="bodyf" style="font-size:10.5px;color:{ink}">真鲷 ▾</div></div></div>
      {dialog_buttons(p, confirm='确认收藏', cancel='取消')}
      <div class="bodyf mut" style="font-size:8.5px;text-align:center;margin-top:7px">对话框位于准星信息条上方，不遮挡 C03</div>"""
    body = screen_inner + scrim(p, 0) + dialog_frame(p, inner, "收藏该点位", 320)
    return shell(body, water, "OV03 确认收藏")


# ---------------------------------------------------------------------------
# OV04 水质实测录入 Sheet — 全展开按钮常驻可点、离线可录
# ---------------------------------------------------------------------------
def render_ov_water_entry(water="sea"):
    p = C(water); line, card, card2, ink, muted, primary, white, accent = (
        H(p['line']), H(p['card']), H(p['card2']), H(p['ink']), H(p['muted']), H(p['primary']), H(p['white']), H(p['accent']))
    base = render_tab_water(water)
    screen_inner = screen_inner_of(base)
    def field(label, hint):
        return f"""<div style="border:1.7px solid {line};border-radius:10px;padding:8px 10px;background:{card2}"><div class="bodyf mut" style="font-size:9px">{label}</div><div class="bodyf" style="font-size:12px;color:{ink};margin-top:3px">{hint}</div></div>"""
    inner = f"""<div class="row" style="gap:9px">{field('水温　　℃','19.6')}{field('溶解氧　mg/L','7.2')}</div>
      <div class="row" style="gap:9px;margin-top:9px">{field('浊度　　NTU','3.1')}{field('测量时刻','今天 13:20')}</div>
      <div style="margin-top:14px;border:2px solid {line};border-radius:11px;text-align:center;padding:11px;background:{accent};color:{white}"><b class="bodyf" style="font-size:13px">保存实测</b></div>
      <div class="row" style="margin-top:8px;gap:6px;align-items:center;justify-content:center">{badge(p,'离线可录入',p['green'])}<span class="bodyf mut" style="font-size:9px">保存后指标卡刷新为实测徽标</span></div>"""
    body = screen_inner + scrim(p, 0) + sheet_frame(p, inner, "录入实测水质", 400)
    return shell(body, water, "OV04 水质实测录入")


# ---------------------------------------------------------------------------
# OV05 星标气泡卡 — 点星标弹卡但不动准星；查看详情先收藏再进详情
# ---------------------------------------------------------------------------
def render_ov_star_bubble(water="sea"):
    p = C(water); line, card, card2, ink, muted, primary, white, accent2 = (
        H(p['line']), H(p['card']), H(p['card2']), H(p['ink']), H(p['muted']), H(p['primary']), H(p['white']), H(p['accent2']))
    base = render_map_new(water)  # 新稿地图（保留手绘底图）
    screen_inner = screen_inner_of(base)
    bubble = f"""<div style="position:absolute;left:40px;top:250px;width:250px;background:{card};border:2.4px solid {line};border-radius:16px;z-index:42;box-shadow:3px 5px 0 {line}44;padding:12px 13px">
      <div style="position:absolute;right:24px;bottom:-11px;width:18px;height:18px;background:{card};border-right:2.4px solid {line};border-bottom:2.4px solid {line};transform:rotate(45deg)"></div>
      <div class="row" style="gap:8px"><span style="width:26px">{E.fish_icon(SID, water, p['accent'], 24)}</span><span class="disp" style="font-size:14px;color:{ink}">小麦岛外海</span></div>
      <div class="bodyf" style="font-size:10px;color:{muted};margin-top:6px;line-height:1.6">最佳窗口：明日 17:00 · 92 分 · 爆护窗口</div>
      <div class="row" style="gap:8px;margin-top:9px"><span class="tag" style="font-size:9.5px;background:{card2}">关闭</span><span class="tag" style="font-size:9.5px;background:{accent2};color:{ink}">查看详情 ›</span></div>
      <div class="bodyf mut" style="font-size:8px;margin-top:6px">点星标仅收气泡，准星落点不变</div></div>"""
    body = screen_inner + bubble
    return shell(body, water, "OV05 星标气泡卡")


# ---------------------------------------------------------------------------
# OV06 删除二次确认 — 批量/单条均二次确认，取消无副作用
# ---------------------------------------------------------------------------
def render_ov_delete_confirm(water="sea"):
    p = C(water); line, card, ink, muted = H(p['line']), H(p['card']), H(p['ink']), H(p['muted'])
    base = render_favorites_new(water)  # 新稿收藏页
    screen_inner = screen_inner_of(base)
    inner = f"""<div class="bodyf" style="font-size:12px;color:{ink};line-height:1.7">确定删除已选的 <b class="hl">3</b> 个收藏点？<br><span class="bodyf mut" style="font-size:10px">此操作不可撤销；取消不会产生任何副作用。</span></div>
      {dialog_buttons(p, confirm='删除', cancel='取消', tone='danger')}"""
    body = screen_inner + scrim(p, 0) + dialog_frame(p, inner, "确认删除", 250)
    return shell(body, water, "OV06 删除二次确认")


# ---------------------------------------------------------------------------
# 状态条组件（离线 / 陈旧 / 48h 不可用，插入在 DaySelector 上方 pushes 下移）
# ---------------------------------------------------------------------------
def state_bar(p, kind):
    line, card2, ink, accent2 = H(p['line']), H(p['card2']), H(p['ink']), H(p['accent2'])
    cfg = {
        "offline": ("⛆", "网络不可用 · 显示本地缓存数据", p['muted']),
        "stale": ("◷", "数据已陈旧（>6h）· 显示缓存于 昨日 21:10", '8A6D2E'),
        "unusable": ("⊘", "数据超过 48h · 不可用，请刷新或换点", p['accent']),
        "degraded": ("◇", "部分维度不可用 · 已按有效权重重算", p['accent']),
    }
    icon, text, tone = cfg[kind]
    return f"""<div class="row" style="margin:6px 14px 0;gap:8px;border:1.8px solid {H(tone)};border-radius:11px;background:{card2};padding:8px 11px;position:relative;z-index:6">
      <span style="font-size:15px;color:{H(tone)}">{icon}</span><span class="bodyf" style="font-size:10.5px;color:{ink};flex:1;line-height:1.5">{text}</span>
      <span class="tag" style="font-size:9px;border-color:{H(tone)}">重试</span></div>"""


def render_state_detail(water, kind="unusable"):
    """状态态：在「钓鱼指数(新稿)」详情骨架顶部注入状态条，其余 100% 复用新稿组件。
    不再叠加旧 base 详情页，确保与第②组 Tab 同源同风（用户要求：新组件保持手绘 A 风格）。"""
    p = C(water)
    body = state_bar(p, kind) + score_body(p, water)
    return shell(statusbar(p) + detail_head(p, water, 0) + body, water, f"状态态 · {kind}")


def render_favorites_empty(water="sea"):
    p = C(water); line, card, ink, muted, primary, accent2 = (
        H(p['line']), H(p['card']), H(p['ink']), H(p['muted']), H(p['primary']), H(p['accent2']))
    status = statusbar(p)
    head = f"""<div class="row" style="padding:6px 16px 10px;position:relative;z-index:3"><span class="disp" style="font-size:24px;color:{ink}">我的收藏</span>
      <span class="bodyf mut" style="font-size:10.5px;margin-left:auto">0 个点位</span></div>
    <div style="margin:0 14px;position:relative;z-index:3"><div class="card" style="height:40px;display:flex;align-items:center;gap:8px;padding:0 12px">
      <span style="font-size:16px;color:{H(p['accent'])}">⌕</span><span class="bodyf" style="font-size:12px;color:{muted}">搜索收藏点位</span></div></div>"""
    empty = f"""<div style="margin:60px 30px 0;text-align:center;position:relative;z-index:3;display:flex;flex-direction:column;align-items:center;gap:12px">
      <div style="width:130px;opacity:.8">{E.fish_svg(SID, water, 'bone', w=130)}</div>
      <div class="disp" style="font-size:16px;color:{primary}">还没有收藏点</div>
      <div class="bodyf mut" style="font-size:10.5px;line-height:1.7">在地图上拖动准星选点，点「收藏」<br>把常去的水域存到这里，随时回看。</div>
      <div style="border:2px solid {line};border-radius:11px;text-align:center;padding:10px 22px;background:{accent2};margin-top:6px"><b class="bodyf" style="font-size:12px;color:{ink}">＋ 回地图选点</b></div></div>"""
    body = status + head + empty + disclaimer_entry(p, "1–5")
    return shell(body, water, "收藏空态 · 鱼骨引导")


def fav_item(p, place, sub, score, band_label, checked):
    """收藏列表单行（新稿骨架）：勾选圈 + 点位名 + 坐标/更新时间 + 分数 + 鱼标。"""
    line, card, card2, ink, muted, primary, accent2, white, green = (
        H(p['line']), H(p['card']), H(p['card2']), H(p['ink']), H(p['muted']),
        H(p['primary']), H(p['accent2']), H(p['white']), H(p['green']))
    chk = (f'<div style="width:24px;height:24px;border-radius:50%;border:2px solid {primary};display:flex;align-items:center;justify-content:center;background:{primary}"><span style="font-size:13px;color:{white}">✓</span></div>'
           if checked else
           f'<div style="width:24px;height:24px;border-radius:50%;border:2px solid {line};display:flex;align-items:center;justify-content:center;background:{card}"></div>')
    return f"""<div class="card" style="margin:8px 14px 0;padding:9px 11px;position:relative;z-index:4;display:flex;gap:11px;align-items:center">
      {chk}
      <div style="flex:1;min-width:0">
        <div class="disp" style="font-size:13px;color:{ink}">{place}</div>
        <div class="bodyf mut" style="font-size:9px;margin-top:2px;white-space:nowrap;overflow:hidden;text-overflow:ellipsis">{sub}</div></div>
      <div style="text-align:center;min-width:46px"><div class="disp" style="font-size:18px;color:{primary}">{score}</div><div class="bodyf mut" style="font-size:8px">{band_label}</div></div>
      <span style="width:22px">{E.fish_icon(SID, 'sea', accent2, 20)}</span></div>"""


def render_favorites_multiselect(water="sea"):
    """多选批量：新稿收藏列表骨架 + 顶部「已选N」栏 + 底部批量操作栏。
    不再叠加旧 base 收藏页，与第②组同源同风（用户要求：新组件保持手绘 A 风格）。"""
    p = C(water)
    line, card, card2, ink, muted, primary, white, accent2 = (
        H(p['line']), H(p['card']), H(p['card2']), H(p['ink']), H(p['muted']),
        H(p['primary']), H(p['white']), H(p['accent2']))
    topbar = f"""<div class="row" style="padding:6px 16px 10px;position:relative;z-index:8;gap:8px;border-bottom:2px solid {line};background:{card}">
      <span style="font-size:16px;color:{primary}">✓</span><span class="disp" style="font-size:14px;color:{ink}">已选 3 项</span>
      <span class="tag" style="font-size:9px;margin-left:auto">全选</span><span class="tag" style="font-size:9px">反选</span><span class="tag" style="font-size:9px">退出</span></div>"""
    items = [
        ("小麦岛外海", "青岛 · 36.05°N 120.44°E · 更新 昨日 21:10", "82", "很适合", True),
        ("灵山湾", "青岛 · 35.92°N 120.21°E · 更新 今日 09:12", "76", "很适合", True),
        ("唐岛湾", "青岛 · 35.98°N 120.18°E · 更新 今日 08:40", "64", "可以", True),
        ("鳌山湾", "青岛 · 36.38°N 120.71°E · 更新 11-09", "58", "可以", False),
    ]
    rows = "".join(fav_item(p, *it) for it in items)
    batch = f"""<div style="position:absolute;left:0;right:0;bottom:0;z-index:9;background:{card};border-top:2.4px solid {line};padding:11px 14px 18px;display:flex;gap:9px;justify-content:space-around">
      {''.join(f'<div style="text-align:center;min-width:50px"><span style="font-size:17px;color:{primary}">{ic}</span><div class="bodyf" style="font-size:10px;color:{ink};margin-top:2px">{lb}</div></div>' for ic, lb in [('✕','删除'),('⌁','改标签'),('▤','改分组'),('✓','全选')])}</div>"""
    return shell(statusbar(p) + topbar + rows + batch, water, "收藏多选批量栏")


# ---------------------------------------------------------------------------
# 深色变体：深色地图 / 深色收藏（深色 token 独立于浅色，非机械反转）
# ---------------------------------------------------------------------------
DARK = {
    "sea": dict(bg="0E1C2B", bg2="132538", ink="E6EDF5", line="3A4C5E", card="17293B", card2="1E344A",
                primary="90CAF9", accent="FF8A65", accent2="80CBC4", green="4DB6AC", muted="9FB0C0", white="F2F7FB",
                map_land="1B3044", map_water="123A55", map_road="2A4358", band=["2E7D52","66BB6A","FDD835","FB8C00","E53935"]),
    "inland": dict(bg="101D18", bg2="162A20", ink="E6F0E9", line="3A5346", card="172B21", card2="1E372A",
                primary="A5D6A7", accent="FFAB91", accent2="80CBC4", green="66BB6A", muted="9DB8A8", white="F2F8F3",
                map_land="1C3326", map_water="123C2E", map_road="2C4A38", band=["2E7D52","66BB6A","FDD835","FB8C00","E53935"]),
}

# D 路亚图鉴：深色独立调色（与工程 d-fieldguide 的 dark scheme 同源）
DARK_D = {
    "sea": dict(bg="0B1620", bg2="0F1D28", ink="F2EFE6", line="3A4A54", card="12222E", card2="17394C",
                primary="6FB6E8", accent="FF8A70", accent2="9FB6C4", green="67C2A5", muted="9FB6C4", white="F2EFE6",
                map_land="16303F", map_water="17394C", map_road="2A4A5C", band=["2E7D32","66BB6A","FDD835","FB8C00","EF5350"]),
    "inland": dict(bg="0C1A14", bg2="0F2018", ink="F2EFE6", line="34483C", card="12261C", card2="16382A",
                primary="67C2A5", accent="FFC15E", accent2="9DB8A8", green="67C2A5", muted="9DB8A8", white="F2EFE6",
                map_land="18382A", map_water="16382A", map_road="26503E", band=["2E7D32","66BB6A","FDD835","FB8C00","EF5350"]),
}


def _dark_pal():
    return DARK_D if SID == "d-guidebook" else DARK


def render_dark(base_fn, water="sea", label="深色"):
    """临时注入深色 palette，复用现有渲染函数"""
    P = _dark_pal()[water]
    orig = E.STYLES[SID]["pal"][water]
    E.STYLES[SID]["pal"][water] = P
    try:
        html = base_fn(SID, water) if base_fn in (E.render_map, E.render_favorites) else base_fn(water)
    finally:
        E.STYLES[SID]["pal"][water] = orig
    return html


# ---------------------------------------------------------------------------
# 主程序
# ---------------------------------------------------------------------------
JOBS = {}


def revise_settings():
    """需求 OPT-A-01/02/09（2026-11-12，用户确认）：在 A 版设置页源稿上
       · 删除「用例·物种·权重」与「API Key（可选）」两个版块
       · 新增「外观·皮肤」入口（首版仅 A + 敬请期待占位；独立选择页本轮不做）
       幂等：检测到「外观 · 皮肤」即跳过。只改 A 版 sources/settings.html，
       不碰 engine.py / B/C/D。"""
    p = E.pal(SID, "sea")
    h = E._h
    ink, line, primary, muted = h(p["ink"]), h(p["line"]), h(p["primary"]), h(p["muted"])
    accent2 = h(p["accent2"])
    skin_name = f'{E.STYLES[SID]["no"]} · {E.STYLES[SID]["name"]}'
    # CHG-94：历史手写稿与 selfcheck_full.py 用「手绘 A · 复古户外」。
    # 旧生成器只拼 no/name 得到「A · 复古户外」，重跑一遍就会把文案冲掉（生成管线非幂等）。
    # 这里补齐前缀，使 `build_style.build_html` 可从源码完整重建且自检保持 276 PASS。
    if SID == "a-vintage":
        skin_name = "手绘 " + skin_name
    target = OUT.parent / "sources" / "settings.html"
    html = target.read_text(encoding="utf-8")
    if "外观 · 皮肤" in html:
        return "already-revised"

    # 1) 按 card 切分，丢弃两个下线版块（card 边界以下一个 <div class="card" 为界）
    parts = html.split('<div class="card"')
    kept, removed = [parts[0]], []
    for seg in parts[1:]:
        if "用例 · 物种 · 权重</span>" in seg:
            removed.append("用例物种权重"); continue
        if "API Key（可选）</span>" in seg:
            removed.append("API Key"); continue
        kept.append(seg)
    html = '<div class="card"'.join(kept)
    if len(removed) != 2:
        raise RuntimeError(f"settings revise: expected 2 removed cards, got {removed}")

    # 2) 构造「外观·皮肤」入口卡（紧凑，避免底部隐私/关于行被裁；与同页 card 视觉一致）
    fish = E.fish_icon(SID, "sea", h(p["accent"]), 20)
    swatches = "".join(
        f'<span style="display:inline-block;width:10px;height:10px;border-radius:2px;border:1.2px solid {line};background:{c}"></span>'
        for c in (h(p["bg"]), h(p["primary"]), h(p["accent"]), h(p["accent2"]), h(p["green"]))
    )
    skin_card = f'''<div class="card" style="margin:0 14px 8px;padding:6px 12px;border-color:{accent2}">
          <div class="row" style="gap:7px;margin-bottom:4px"><span style="width:22px">{fish}</span>
            <span class="disp" style="font-size:12.5px;color:{primary}">外观 · 皮肤</span>
            <span class="tag" style="margin-left:auto;font-size:8px;background:{accent2};color:{ink}">OPT-A-09</span></div>
          <div class="row" style="justify-content:space-between;padding:4px 0;border-bottom:1.2px dashed {line}40">
            <span class="bodyf" style="font-size:11.5px;color:{ink}">当前皮肤</span>
            <span class="row" style="gap:4px">{swatches}<span class="bodyf" style="font-size:9.5px;color:{muted};margin-left:3px">{skin_name} ✓</span></span></div>
          <div class="row" style="gap:6px;margin-top:5px"><span class="bodyf mut" style="font-size:9px">切换仅改视觉，不影响数据</span>
            <span class="tag" style="margin-left:auto;font-size:8px;border-style:dashed;color:{muted}">更多皮肤 · 敬请期待</span></div></div>'''

    # 3) 插到「数据与网络」card 之前（即「显示」card 之后）
    idx = html.find("数据与网络</span>")
    if idx < 0:
        raise RuntimeError("settings revise: 数据与网络 anchor missing")
    marker = '<div class="card" style="margin:0 14px 8px;padding:8px 12px">'
    start = html.rfind(marker, 0, idx)
    if start < 0:
        raise RuntimeError("settings revise: card start missing")
    html = html[:start] + skin_card + html[start:]
    target.write_text(html, encoding="utf-8")
    return "revised"


def render_base_redraw():
    """#84 认新稿：地图/收藏/设置主屏其他组件重绘（地图底图保留手绘原稿）。
    覆盖 base sources/ 的 map-sea/inland、favorites、settings 为 new-draft；
    PNG 由 Edge 渲到 base screens/。dark-detail 留 #85 深色统一。"""
    base_src = OUT.parent / "sources"
    base_src.mkdir(parents=True, exist_ok=True)
    jobs = {
        "map-sea": render_map_new("sea"),
        "map-inland": render_map_new("inland"),
        "favorites": render_favorites_new("sea"),
        "settings": E.render_settings(SID, "sea"),
        # #85 认新稿：深色详情（base 屏）用新稿 钓鱼指数骨架 + DARK 调色板，替换旧稿 E.render_detail_dark
        "dark-detail": render_dark(render_tab_score, "sea", "深色详情"),
    }
    for name, html in jobs.items():
        (base_src / f"{name}.html").write_text(html, encoding="utf-8")
    return sorted(jobs.keys())


def build():
    jobs = {
        # 详情 5 Tab 变体（含 SCORE 首 Tab，海/陆；其余补齐）
        "detail-tab-score-sea": lambda: render_tab_score("sea"),
        "detail-tab-score-inland": lambda: render_tab_score("inland"),
        "detail-tab-tide-sea": lambda: render_tab_tide("sea"),
        "detail-tab-tide-inland": lambda: render_tab_tide("inland"),
        "detail-tab-weather": lambda: render_tab_weather("sea"),
        "detail-tab-water": lambda: render_tab_water("sea"),
        "detail-tab-wave-sea": lambda: render_tab_wave("sea"),
        "detail-tab-wave-inland": lambda: render_tab_wave("inland"),
        "detail-breakdown": lambda: render_breakdown("sea"),
        # 6 叠层
        "ov01-top10": lambda: render_ov_top10("sea"),
        "ov02-disclaimer": lambda: render_ov_disclaimer("sea"),
        "ov03-confirm-favorite": lambda: render_ov_confirm_fav("sea"),
        "ov04-water-entry": lambda: render_ov_water_entry("sea"),
        "ov05-star-bubble": lambda: render_ov_star_bubble("sea"),
        "ov06-delete-confirm": lambda: render_ov_delete_confirm("sea"),
        # 状态态
        "state-offline": lambda: render_state_detail("sea", "offline"),
        "state-stale": lambda: render_state_detail("sea", "stale"),
        "state-unusable-48h": lambda: render_state_detail("sea", "unusable"),
        "state-degraded": lambda: render_state_detail("sea", "degraded"),
        "favorites-empty": lambda: render_favorites_empty("sea"),
        "favorites-multiselect": lambda: render_favorites_multiselect("sea"),
        # 深色变体（#85 认新稿：新稿骨架 + DARK 调色板；dark-detail（base 屏）见 render_base_redraw）
        "dark-map": lambda: render_dark(render_map_new, "sea", "深色地图"),
        "dark-favorites": lambda: render_dark(render_favorites_new, "sea", "深色收藏"),
    }
    made = []
    for name, fn in jobs.items():
        try:
            html = fn()
            (OUT / f"{name}.html").write_text(html, encoding="utf-8")
            made.append(name)
        except Exception as e:
            print(f"  FAIL {name}: {type(e).__name__}: {e}")
    # #84 认新稿：地图/收藏/设置主屏其他组件重绘（地图底图保留）
    try:
        print("  BASE_REDRAW", render_base_redraw())
    except Exception as e:
        print(f"  FAIL base redraw: {type(e).__name__}: {e}")
    # 需求 OPT-A-01/02/09：覆盖改版 A 版设置页（幂等，不动 engine.py / B/C/D）
    try:
        print("  SETTINGS_REVISED", revise_settings())
    except Exception as e:
        print(f"  FAIL settings revise: {type(e).__name__}: {e}")
    return made


def main(argv=None):
    argv = list(sys.argv[1:] if argv is None else argv)
    style_id = argv[0] if argv else "a-vintage"
    site = Path(argv[1]) if len(argv) > 1 else HERE.parent / "A-vintage-outdoor"
    configure(style_id, site)
    made = build()
    print(f"FULL_UI_HTML={len(made)}")
    print(f"STYLE={style_id} SITE={site.name}")
    for m in made:
        print("  ", m)
    return made


if __name__ == "__main__":
    main()



