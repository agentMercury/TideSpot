# -*- coding: utf-8 -*-
"""
潮点 TideSpot · 手绘风格 UI 概念稿生成器
=========================================================
4 种手绘美术风格 × 2 种水体（海洋/近海 | 内陆/湖泊）
设计侧视觉探索稿，非工程交付；工程事实（Tab 序 / 文案 / 免责 / 结构）锁定一致。

依赖：Python 3 + Pillow（仅拼板用）。渲染用 Edge headless 截自包含 HTML。
所有 HTML 自包含：SVG 内联、字体走系统、无外链、纯相对路径。
"""
import os, sys, json, io, base64
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
WORK = ROOT / "_work"
WORK.mkdir(exist_ok=True)

# ---------------------------------------------------------------------------
# 工程事实锁定（来自 design-handoff/copy/copy.json 的 frozen 单源）
# ---------------------------------------------------------------------------
TAB_SEA = ["钓鱼指数", "潮流", "天气", "水质", "风浪"]
TAB_INLAND = ["钓鱼指数", "水位（无潮汐）", "天气", "水质", "湖面风与水温"]
DISCLAIMERS = [
    "潮位与潮流来自 ~8 km 全球模型，官方声明不适用于近岸航行；本 App 不替代航路指南与官方潮汐表。",
    "雷暴、大风、大浪判定仅供参考。水上安全以当地预警为准，切勿在雷电、台风、大潮汛期间出钓。",
    "指数基于气象与天文因子的经验加权估算，不构成渔获保证。",
    "中国境内多数点位水质为模型估算值，非实测，不作为用水、饮用或接触安全依据。",
    "「现场校潮」为用户自记高潮观测对模型滞后的经验修正（lag 平移），仅用于相对时刻参考；校正不改变数据来源与精度，仍非官方潮汐表。",
]
BAND_LABELS = ["爆护窗口", "很适合", "可以", "较差", "别去"]  # 冻结文案，档位恒带文字
BADGES = ["实测", "卫星", "模型", "估算", "未校正", "低置信"]

# 水体文案（工程单源差异）
WATER_COPY = {
    "sea": {
        "badge": "海洋 / 近海", "badge_en": "COASTAL", "water_short": "海洋",
        "tab2": "潮流", "chart": "潮高 · 潮流", "chart_unit": "潮高 m · 流速 kn",
        "species": "真鲷", "species_en": "SEA BREAM", "place": "青岛 · 小麦岛外海",
        "coord": "36.05°N 120.44°E", "tide_word": "潮", "hi": "高潮", "lo": "低潮",
        "note": "近海潮汐模型 · 8 km 网格",
    },
    "inland": {
        "badge": "内陆 / 湖泊", "badge_en": "INLAND", "water_short": "内陆",
        "tab2": "水位（无潮汐）", "chart": "水位", "chart_unit": "水位 m · 无潮汐",
        "species": "鲤鱼", "species_en": "COMMON CARP", "place": "千岛湖 · 中心湖区",
        "coord": "29.58°N 119.05°E", "tide_word": "位", "hi": "高水位", "lo": "低水位",
        "note": "湖库水位 · 无潮汐",
    },
}

# ---------------------------------------------------------------------------
# 4 种手绘风格配置：字体 / 线条 / 纸张 / 双水体调色板
# ---------------------------------------------------------------------------
STYLES = {
    "a-vintage": {
        "no": "A", "name": "复古户外", "en": "VINTAGE OUTDOOR",
        "from": "图5 MOUNTAIN ADVENTURE + 图6 ADVENTURE IS CALLING",
        "idea": "墨绿做旧纸底 + 藏蓝线描山峦/松林/日轮 + 芥末黄衬线大标题 + 丝网套印错位 + 颗粒噪点",
        "fit": "户外硬朗、复古质感强；适合做品牌调性最鲜明的一版",
        "font": {
            "display": "Georgia, Times New Roman, serif", "display_weight": "700",
            "body": "KaiTi, 楷体, STKaiti, serif", "num": "Georgia, serif",
            "caps": "Georgia, serif", "track": "0.14em",
        },
        "paper": {"grain": 0.16, "tint": True, "base_freq": "0.82"},
        "ink": "1B3A5C", "sketch_scale": 2.2, "radius": "16px 20px 15px 19px / 19px 15px 20px 16px",
        "pal": {
            "sea": {
                "bg": "E4D9BC", "bg2": "D8C9A5", "ink": "16324F", "primary": "1B3A5C",
                "accent": "C15A3A", "accent2": "D9A734", "green": "2E6E7E", "card": "F2EAD4",
                "card2": "E8DDBE", "line": "16324F", "muted": "5C5540", "white": "F7F1DE",
                "band": ["8C6239", "B0813C", "D9A734", "C15A3A", "8A3B2E"],
                "map_land": "D6C6A0", "map_water": "9FB8B4", "map_road": "EFE6CC",
            },
            "inland": {
                "bg": "DCE2C4", "bg2": "CDD6B0", "ink": "213D2A", "primary": "2E5A48",
                "accent": "C15A3A", "accent2": "D9A734", "green": "3E7A54", "card": "F1EED8",
                "card2": "E4E6C6", "line": "213D2A", "muted": "556045", "white": "F7F4E2",
                "band": ["6E7B3E", "8C9A48", "B9A83E", "C57634", "A24432"],
                "map_land": "CBDBA8", "map_water": "7FA98C", "map_road": "EEF0D2",
            },
        },
    },
    "b-gentleman": {
        "no": "B", "name": "拟人鱼绅士", "en": "GENTLEMAN FISH",
        "from": "图4 三条穿西装的鱼",
        "idea": "克莱因亮蓝纯底 + 藏青/暖橙/米白拟人鱼（领带·条纹围巾·纽扣·圆眼·黄嘴）+ 轻纸颗粒 + 圆润 Comic 标题",
        "fit": "亲和可爱、记忆点强；拟人鱼做品牌 IP 潜力最大",
        "font": {
            "display": "Comic Sans MS, KaiTi, 楷体, cursive", "display_weight": "700",
            "body": "KaiTi, 楷体, cursive", "num": "Comic Sans MS, sans-serif",
            "caps": "Comic Sans MS, sans-serif", "track": "0.06em",
        },
        "paper": {"grain": 0.10, "tint": False, "base_freq": "0.9"},
        "ink": "173A63", "sketch_scale": 1.5, "radius": "22px 26px 20px 24px / 24px 20px 26px 22px",
        "pal": {
            "sea": {
                "bg": "2E6FD6", "bg2": "245DC0", "ink": "12305C", "primary": "173A63",
                "accent": "E8792B", "accent2": "F2B33D", "green": "1F8A78", "card": "FAF6EC",
                "card2": "EFE8D6", "line": "12305C", "muted": "4A5E7E", "white": "FFFFFF",
                "band": ["1B6E4A", "3E9E5A", "C9B02E", "E8792B", "CE3B2E"],
                "map_land": "F0E4C4", "map_water": "8FC0EA", "map_road": "FFFFFF",
            },
            "inland": {
                "bg": "2E8B7A", "bg2": "267766", "ink": "143C34", "primary": "1B5A4C",
                "accent": "E8792B", "accent2": "F2B33D", "green": "2FA584", "card": "FAF6EC",
                "card2": "E9EEDC", "line": "143C34", "muted": "46665C", "white": "FFFFFF",
                "band": ["1B6E4A", "3E9E5A", "C9B02E", "E8792B", "CE3B2E"],
                "map_land": "DCE9B8", "map_water": "6FBFA6", "map_road": "FFFFFF",
            },
        },
    },
    "c-watercolor": {
        "no": "C", "name": "水彩几何鱼", "en": "WATERCOLOR GEOMETRY",
        "from": "图8 三条抽象撞色鱼",
        "idea": "亮蓝/湖水底 + 蓝白黄橙红色块拼接鱼 + 水彩毛边晕染 + 大留白 + 极简 Gabriola 标题",
        "fit": "清爽高级、装饰克制；最像现代插画 App，留白 breathing 最好",
        "font": {
            "display": "Gabriola, KaiTi, 楷体, cursive", "display_weight": "400",
            "body": "KaiTi, 楷体, cursive", "num": "Gabriola, Georgia, serif",
            "caps": "Gabriola, serif", "track": "0.10em",
        },
        "paper": {"grain": 0.07, "tint": True, "base_freq": "0.7"},
        "ink": "123E7C", "sketch_scale": 3.0, "radius": "26px 30px 24px 28px / 28px 24px 30px 26px",
        "pal": {
            "sea": {
                "bg": "1E6FD9", "bg2": "175CC0", "ink": "0E346F", "primary": "123E7C",
                "accent": "ED8B3C", "accent2": "F4C430", "green": "17A398", "card": "FFFFFF",
                "card2": "EEF4FC", "line": "0E346F", "muted": "3E628F", "white": "FFFFFF",
                "band": ["1B7A5E", "5CB23C", "F4C430", "ED8B3C", "E5503E"],
                "map_land": "DCEBFA", "map_water": "7FB4EC", "map_road": "FFFFFF",
            },
            "inland": {
                "bg": "F2ECD8", "bg2": "E7E0C4", "ink": "2C4A32", "primary": "2E6E50",
                "accent": "ED8B3C", "accent2": "F4C430", "green": "4FA06E", "card": "FFFFFF",
                "card2": "F4F7E8", "line": "2C4A32", "muted": "5E7458", "white": "FFFFFF",
                "band": ["2E7D52", "6BA83E", "F4C430", "ED8B3C", "E5503E"],
                "map_land": "DCE9B8", "map_water": "8FCBB0", "map_road": "FFFFFF",
            },
        },
    },
    "d-guidebook": {
        "no": "D", "name": "手绘鱼种图鉴", "en": "FIELD GUIDE",
        "from": "图7 路亚鱼种图鉴",
        "idea": "淡蓝/淡绿纸底 + 粗黑描边 Q 版鱼 + 中英双语贴纸标签 + 胶带/图鉴卡片 + 手写笔记 + 马克笔涂色",
        "fit": "信息感强、趣味足；鱼种图鉴与「钓什么鱼」天然契合",
        "font": {
            "display": "Impact, KaiTi, 楷体, sans-serif", "display_weight": "400",
            "body": "KaiTi, 楷体, cursive", "num": "Impact, sans-serif",
            "caps": "Ink Free, KaiTi, cursive", "track": "0.08em",
        },
        "paper": {"grain": 0.12, "tint": True, "base_freq": "0.8"},
        "ink": "1A1A1A", "sketch_scale": 1.9, "radius": "6px 9px 5px 8px / 8px 5px 9px 6px",
        "pal": {
            "sea": {
                "bg": "DCEAF4", "bg2": "CBDEEE", "ink": "1A1A1A", "primary": "1A4E86",
                "accent": "E5503E", "accent2": "F2B33D", "green": "3E9E5A", "card": "FFFFFF",
                "card2": "F0F6FB", "line": "1A1A1A", "muted": "566A7C", "white": "FFFFFF",
                "band": ["2E7D52", "6BA83E", "F2B33D", "ED8B3C", "E5503E"],
                "map_land": "F5E9C8", "map_water": "A6D2EE", "map_road": "FFFFFF",
            },
            "inland": {
                "bg": "E4EFD8", "bg2": "D6E6C4", "ink": "1A1A1A", "primary": "2E6E42",
                "accent": "E5503E", "accent2": "F2B33D", "green": "3E9E5A", "card": "FFFFFF",
                "card2": "F3F8EA", "line": "1A1A1A", "muted": "5E7452", "white": "FFFFFF",
                "band": ["2E7D52", "6BA83E", "F2B33D", "ED8B3C", "E5503E"],
                "map_land": "DCE9B8", "map_water": "8FCBB0", "map_road": "FFFFFF",
            },
        },
    },
}

# 五档分数示意（非业务数据，版式示意）
SCORE = 78
SCORE_BAND = 1  # 很适合


# ---------------------------------------------------------------------------
# 工具
# ---------------------------------------------------------------------------
def _h(hexstr):
    return "#" + hexstr


def pal(style_id, water):
    return STYLES[style_id]["pal"][water]


def esc(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


# ---------------------------------------------------------------------------
# SVG 滤镜定义（手绘纸张 / 抖动 / 水彩毛边）
# ---------------------------------------------------------------------------
def svg_defs(style_id, water):
    s = STYLES[style_id]
    p = pal(style_id, water)
    sc = s["sketch_scale"]
    bf = s["paper"]["base_freq"]
    grain = s["paper"]["grain"]
    return f"""
<svg width="0" height="0" style="position:absolute" aria-hidden="true">
 <defs>
  <filter id="rough" x="-8%" y="-8%" width="116%" height="116%">
    <feTurbulence type="fractalNoise" baseFrequency="0.028" numOctaves="2" seed="7" result="w"/>
    <feDisplacementMap in="SourceGraphic" in2="w" scale="{sc}" xChannelSelector="R" yChannelSelector="G"/>
  </filter>
  <filter id="rough2" x="-8%" y="-8%" width="116%" height="116%">
    <feTurbulence type="fractalNoise" baseFrequency="0.04" numOctaves="2" seed="19" result="w"/>
    <feDisplacementMap in="SourceGraphic" in2="w" scale="{sc*0.6:.1f}" xChannelSelector="G" yChannelSelector="B"/>
  </filter>
  <filter id="watercolor" x="-12%" y="-12%" width="124%" height="124%">
    <feTurbulence type="fractalNoise" baseFrequency="0.035" numOctaves="3" seed="3" result="w"/>
    <feDisplacementMap in="SourceGraphic" in2="w" scale="{sc*1.4:.1f}" xChannelSelector="R" yChannelSelector="G" result="d"/>
    <feGaussianBlur in="d" stdDeviation="0.6"/>
  </filter>
  <filter id="papergrain" x="0" y="0" width="100%" height="100%">
    <feTurbulence type="fractalNoise" baseFrequency="{bf}" numOctaves="3" seed="11" result="n"/>
    <feColorMatrix in="n" type="saturate" values="0" result="ng"/>
    <feComponentTransfer in="ng" result="na"><feFuncA type="linear" slope="{grain}"/></feComponentTransfer>
  </filter>
  <filter id="softshadow" x="-30%" y="-30%" width="160%" height="160%">
    <feDropShadow dx="0" dy="3" stdDeviation="3" flood-color="{p['ink']}" flood-opacity="0.18"/>
  </filter>
 </defs>
</svg>"""


def grain_overlay():
    """独立纸张颗粒覆盖层，mix-blend，不糊文字"""
    return """<svg class="grain" width="100%" height="100%" preserveAspectRatio="none" aria-hidden="true">
  <rect width="100%" height="100%" filter="url(#papergrain)" opacity="1"/>
</svg>"""


# ---------------------------------------------------------------------------
# 鱼形 SVG —— 4 种画风 × 用途（hero 主视觉 / tab / tiny 点缀 / bone 空态 / leap 刷新）
# 全部 viewBox 0 0 120 64，朝右，可缩放换色
# ---------------------------------------------------------------------------
def fish_svg(style_id, water, kind="hero", color=None, w=120, accent=None):
    p = pal(style_id, water)
    ink = _h(p["ink"])
    c = _h(color or p["accent"])
    a = _h(accent or p["accent2"])
    white = _h(p["card"])
    filt = "watercolor" if style_id == "c-watercolor" else "rough2"

    if style_id == "a-vintage":
        # 丝网套印版画鱼：限色块 + 错位描边 + 网点鳍
        if kind == "bone":
            return f"""<svg viewBox="0 0 120 64" width="{w}"><g filter="url(#{filt})">
              <path d="M20 32 L104 32" stroke="{ink}" stroke-width="3" stroke-linecap="round"/>
              <path d="M30 24 L30 40 M44 24 L44 40 M58 24 L58 40 M72 24 L72 40 M86 24 L86 40" stroke="{ink}" stroke-width="2.4" stroke-linecap="round"/>
              <path d="M20 32 L8 22 M20 32 L8 42 M104 32 L116 22 M104 32 L116 42" stroke="{ink}" stroke-width="3" stroke-linecap="round"/>
              <circle cx="26" cy="26" r="5" fill="none" stroke="{ink}" stroke-width="2.6"/></g></svg>"""
        body = f"""<path d="M22 32 Q46 10 92 27 Q100 30 92 34 Q46 54 22 32 Z" fill="{c}" stroke="{ink}" stroke-width="2.6" filter="url(#{filt})"/>
                   <path d="M22 32 L4 16 L10 32 L4 48 Z" fill="{a}" stroke="{ink}" stroke-width="2.6" filter="url(#{filt})"/>"""
        stripe = f"""<path d="M50 20 L44 44 M62 19 L56 45 M74 20 L68 42" stroke="{ink}" stroke-width="2" opacity="0.7" filter="url(#rough2)"/>"""
        eye = f"""<circle cx="36" cy="29" r="4.6" fill="{white}" stroke="{ink}" stroke-width="2.4"/><circle cx="36" cy="29" r="1.9" fill="{ink}"/>"""
        fin = f"""<path d="M60 15 Q66 6 76 14 Z" fill="{a}" stroke="{ink}" stroke-width="2" filter="url(#rough2)"/>"""
        return f"""<svg viewBox="0 0 120 64" width="{w}"><g>{body}{stripe}{fin}{eye}</g></svg>"""

    if style_id == "b-gentleman":
        # 拟人绅士鱼：圆润身 + 西装领带 + 圆眼 + 黄嘴（致敬图4）
        if kind == "bone":
            return f"""<svg viewBox="0 0 120 64" width="{w}"><g filter="url(#rough2)">
              <path d="M24 32 L100 32" stroke="{ink}" stroke-width="3" stroke-linecap="round"/>
              <path d="M34 25 L34 39 M48 25 L48 39 M62 25 L62 39 M76 25 L76 39 M90 26 L90 38" stroke="{ink}" stroke-width="2.4" stroke-linecap="round"/>
              <path d="M24 32 L12 22 M24 32 L12 42 M100 32 L112 22 M100 32 L112 42" stroke="{ink}" stroke-width="3" stroke-linecap="round"/>
              <circle cx="28" cy="27" r="5" fill="none" stroke="{ink}" stroke-width="2.6"/></g></svg>"""
        body = f"""<path d="M26 32 Q44 12 88 25 Q98 28 88 35 Q44 52 26 32 Z" fill="{white}" stroke="{ink}" stroke-width="2.8" filter="url(#rough2)"/>
                   <path d="M26 32 L10 18 L14 32 L10 46 Z" fill="{c}" stroke="{ink}" stroke-width="2.8" filter="url(#rough2)"/>"""
        suit = f"""<path d="M52 16 Q64 30 52 46 L74 44 Q82 32 74 18 Z" fill="{c}" stroke="{ink}" stroke-width="2" filter="url(#rough2)"/>
                   <path d="M58 17 L64 27 L70 17" fill="{a}" stroke="{ink}" stroke-width="1.8"/>
                   <circle cx="64" cy="35" r="1.7" fill="{a}"/><circle cx="64" cy="41" r="1.7" fill="{a}"/>"""
        face = f"""<circle cx="38" cy="27" r="5" fill="{white}" stroke="{ink}" stroke-width="2.6"/><circle cx="38" cy="27" r="2" fill="{ink}"/>
                   <circle cx="30" cy="34" r="3.6" fill="{a}" stroke="{ink}" stroke-width="1.6"/>"""
        return f"""<svg viewBox="0 0 120 64" width="{w}"><g>{body}{suit}{face}</g></svg>"""

    if style_id == "c-watercolor":
        # 几何色块拼接鱼：三角头 + 椭圆身 + 撞色矩形 + 水彩毛边（致敬图8）
        if kind == "bone":
            return f"""<svg viewBox="0 0 120 64" width="{w}"><g filter="url(#watercolor)">
              <path d="M20 32 L100 32" stroke="{ink}" stroke-width="2.6" stroke-linecap="round"/>
              <path d="M32 25 L32 39 M48 25 L48 39 M64 25 L64 39 M80 25 L80 39" stroke="{ink}" stroke-width="2"/>
              <path d="M18 32 L8 24 M18 32 L8 40 M102 32 L112 24 M102 32 L112 40" stroke="{ink}" stroke-width="2.6"/></g></svg>"""
        body = f"""<path d="M18 32 L44 16 L44 48 Z" fill="{c}" filter="url(#watercolor)"/>
                   <path d="M44 16 Q92 12 92 32 Q92 52 44 48 Z" fill="{white}" filter="url(#watercolor)"/>
                   <path d="M56 16 L78 16 L78 48 L56 48 Z" fill="{a}" filter="url(#watercolor)"/>
                   <path d="M92 32 L112 14 L110 32 L112 50 Z" fill="{c}" filter="url(#watercolor)"/>"""
        eye = f"""<circle cx="34" cy="29" r="3.4" fill="{ink}"/><circle cx="34" cy="29" r="1.2" fill="{white}"/>"""
        return f"""<svg viewBox="0 0 120 64" width="{w}"><g>{body}{eye}</g></svg>"""

    # d-guidebook 粗黑描边 Q 版卡通鱼（致敬图7）
    if kind == "bone":
        return f"""<svg viewBox="0 0 120 64" width="{w}"><g filter="url(#rough2)">
          <path d="M22 32 L100 32" stroke="{ink}" stroke-width="3.4" stroke-linecap="round"/>
          <path d="M34 24 L34 40 M48 24 L48 40 M62 24 L62 40 M76 25 L76 39" stroke="{ink}" stroke-width="2.8"/>
          <path d="M20 32 L8 22 M20 32 L8 42 M102 32 L114 22 M102 32 L114 42" stroke="{ink}" stroke-width="3.4"/>
          <circle cx="27" cy="26" r="5.4" fill="{white}" stroke="{ink}" stroke-width="2.8"/><circle cx="27" cy="26" r="1.8" fill="{ink}"/></g></svg>"""
    body = f"""<path d="M24 32 Q42 12 86 24 Q98 27 86 36 Q42 52 24 32 Z" fill="{c}" stroke="{ink}" stroke-width="3" stroke-linejoin="round" filter="url(#rough2)"/>
               <path d="M24 32 L8 18 L12 32 L8 46 Z" fill="{a}" stroke="{ink}" stroke-width="3" stroke-linejoin="round" filter="url(#rough2)"/>"""
    spine = f"""<path d="M44 19 L48 10 L54 18 M58 18 L63 9 L68 18 M72 20 L77 12 L81 21" fill="{a}" stroke="{ink}" stroke-width="2.2" stroke-linejoin="round" filter="url(#rough2)"/>"""
    spots = f"""<circle cx="62" cy="30" r="2.6" fill="{ink}" opacity="0.65"/><circle cx="72" cy="34" r="2.2" fill="{ink}" opacity="0.65"/><circle cx="80" cy="28" r="2" fill="{ink}" opacity="0.65"/>"""
    face = f"""<circle cx="38" cy="27" r="6" fill="{white}" stroke="{ink}" stroke-width="2.8"/><circle cx="38" cy="27" r="2.4" fill="{ink}"/>
               <path d="M28 33 Q33 38 39 33" fill="none" stroke="{ink}" stroke-width="2.4" stroke-linecap="round"/>"""
    return f"""<svg viewBox="0 0 120 64" width="{w}"><g>{body}{spine}{spots}{face}</g></svg>"""


def water_motif(style_id, water, w=60):
    """装饰母题：海洋=双层浪+贝+海鸟；内陆=尖山+日轮+芦苇。深色实描边，浅底徽标上高对比。"""
    p = pal(style_id, water)
    ink = _h(p["ink"]); a = _h(p["accent"]); g = _h(p["green"]); ac2 = _h(p["accent2"]); pr = _h(p["primary"])
    if water == "sea":
        return f"""<svg viewBox="0 0 80 48" width="{w}" aria-hidden="true"><g filter="url(#rough2)" fill="none" stroke="{pr}" stroke-width="2.8" stroke-linecap="round">
          <path d="M2 30 Q12 21 22 30 Q32 39 42 30 Q52 21 62 30 Q70 37 78 30"/>
          <path d="M2 41 Q12 32 22 41 Q32 50 42 41 Q52 32 62 41 Q70 48 78 41" opacity="0.85"/>
          </g>
          <path d="M50 24 Q56 9 62 24 Z" fill="{ac2}" stroke="{pr}" stroke-width="1.8" filter="url(#rough2)"/>
          <path d="M68 20 q5 -4 10 0 q-5 4 -10 0 M78 20 q4 -3 8 0" fill="none" stroke="{pr}" stroke-width="1.8" stroke-linecap="round" filter="url(#rough2)"/></svg>"""
    else:
        return f"""<svg viewBox="0 0 80 48" width="{w}" aria-hidden="true"><g filter="url(#rough2)">
          <path d="M2 42 L20 12 L33 32 L47 6 L66 36 L78 22" fill="none" stroke="{pr}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round"/>
          <circle cx="62" cy="12" r="6.5" fill="{ac2}" stroke="{pr}" stroke-width="2"/>
          </g>
          <path d="M2 45 Q40 39 78 45" fill="none" stroke="{g}" stroke-width="2.4" stroke-linecap="round" filter="url(#rough2)"/>
          <g stroke="{g}" stroke-width="2.2" fill="none" stroke-linecap="round" filter="url(#rough2)"><path d="M12 45 Q9 28 14 18 M14 45 Q18 30 13 21"/></g></svg>"""


def fish_icon(style_id, water, color=None, w=34):
    """Tab / 列表用的小鱼图标"""
    return fish_svg(style_id, water, "hero", color=color, w=w)


# ---------------------------------------------------------------------------
# 页面骨架 CSS（390×844 逻辑，2x 输出）
# ---------------------------------------------------------------------------
def page_css(style_id, water):
    s = STYLES[style_id]; p = pal(style_id, water); f = s["font"]
    return f"""
*{{margin:0;padding:0;box-sizing:border-box;-webkit-font-smoothing:antialiased}}
html,body{{width:390px;height:844px;overflow:hidden}}
body{{position:relative;font-family:{f['body']};color:{_h(p['ink'])};
  background:{_h(p['bg'])};}}
.bgfill{{position:absolute;inset:0;background:linear-gradient(160deg,{_h(p['bg'])} 0%,{_h(p['bg2'])} 100%);z-index:0}}
.grain{{position:absolute;inset:0;z-index:60;pointer-events:none;mix-blend-mode:multiply;opacity:{0.9 if s['paper']['tint'] else 0.55}}}
.screen{{position:relative;z-index:2;width:390px;height:844px;overflow:hidden}}
.statusbar{{height:38px;display:flex;align-items:center;justify-content:space-between;padding:0 20px;font-family:{f['num']};font-size:14px;font-weight:700;color:{_h(p['ink'])};position:relative;z-index:5}}
.statusbar .r{{display:flex;gap:5px;align-items:center;font-size:12px}}
.notch{{position:absolute;left:50%;top:8px;transform:translateX(-50%);width:74px;height:20px;border-radius:12px;background:{_h(p['ink'])};opacity:0.85}}
.disp{{font-family:{f['display']};font-weight:{f['display_weight']};letter-spacing:{f['track']}}}
.bodyf{{font-family:{f['body']}}}
.card{{background:{_h(p['card'])};border:2.2px solid {_h(p['line'])};border-radius:{s['radius']};
  box-shadow:2px 3px 0 {_h(p['line'])}33;position:relative;overflow:hidden}}
.tape{{position:absolute;background:{_h(p['accent2'])};opacity:0.85;transform:rotate(-3deg);z-index:3}}
.tag{{display:inline-flex;align-items:center;gap:4px;border:2px solid {_h(p['line'])};border-radius:20px;padding:2px 9px;font-size:11px;font-weight:700;background:{_h(p['card'])};position:relative;z-index:4}}
.hl{{color:{_h(p['accent'])}}}
.mut{{color:{_h(p['muted'])}}}
.row{{display:flex;align-items:center}}
"""

# ---------------------------------------------------------------------------
# 详情页（核心：每风格 × 每水体各一版）
# ---------------------------------------------------------------------------
def render_detail(style_id, water):
    s = STYLES[style_id]; p = pal(style_id, water); f = s["font"]; wc = WATER_COPY[water]
    ink = _h(p["ink"]); line = _h(p["line"]); card = _h(p["card"]); card2 = _h(p["card2"])
    accent = _h(p["accent"]); accent2 = _h(p["accent2"]); green = _h(p["green"]); white = _h(p["white"])
    primary = _h(p["primary"]); muted = _h(p["muted"])
    tabs = TAB_SEA if water == "sea" else TAB_INLAND
    motif = water_motif(style_id, water, 58)
    hero = fish_svg(style_id, water, "hero", w=104, color=p["accent"], accent=p["accent2"])

    # 状态栏
    status = f"""<div class="statusbar"><span>9:41</span><div class="r"><span>▮▮▮</span><span>◕</span><span>▰</span></div></div><div class="notch"></div>"""

    # 顶部：地点 + 水体徽标（一眼区分①）+ 鱼形收藏
    hero_fish_col = fish_icon(style_id, water, p["accent2"], 26)
    header = f"""
    <div class="row" style="padding:6px 18px 10px;justify-content:space-between;position:relative;z-index:5">
      <div>
        <div class="disp" style="font-size:21px;color:{ink};line-height:1.15">{wc['place']}</div>
        <div class="bodyf" style="font-size:11px;color:{muted};margin-top:3px">⌖ {wc['coord']} · 准星落点</div>
      </div>
      <div style="width:34px;height:34px;border:2.2px solid {line};border-radius:50%;background:{card};display:flex;align-items:center;justify-content:center;box-shadow:1.5px 2px 0 {line}33">{hero_fish_col}</div>
    </div>
    <div style="padding:0 18px 8px;position:relative;z-index:5">
      <span class="tag" style="background:{card};color:{ink};border-color:{line};box-shadow:2px 2px 0 {line}55">{motif} <b style="font-size:12px;letter-spacing:0.04em">{wc['badge']}</b> <i class="disp" style="font-style:normal;font-size:8px;opacity:.6;letter-spacing:.1em">{wc['badge_en']}</i></span>
    </div>"""

    # 地图带 + 准星（中心换鱼，一眼区分④）+ 236dp 信息条（手绘卡）
    crosshair_fish = fish_icon(style_id, water, p["accent"], 30)
    mapband = f"""
    <div style="margin:0 14px;position:relative;z-index:4">
      <div class="card" style="height:150px;background:{_h(p['map_land'])};border-radius:{s['radius']}">
        <!-- 手绘地图底 -->
        <svg width="100%" height="150" viewBox="0 0 362 150" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
          <rect width="362" height="150" fill="{_h(p['map_land'])}"/>
          <path d="M0 96 Q60 78 120 100 Q200 128 268 92 Q320 66 362 84 L362 150 L0 150 Z" fill="{_h(p['map_water'])}" stroke="{line}" stroke-width="1.6" filter="url(#rough2)"/>
          <path d="M-10 40 Q90 60 180 30 Q270 4 380 36" fill="none" stroke="{_h(p['map_road'])}" stroke-width="7" filter="url(#rough2)"/>
          <path d="M60 -10 Q80 70 40 160" fill="none" stroke="{_h(p['map_road'])}" stroke-width="6" filter="url(#rough2)"/>
          <path d="M250 -10 Q230 70 280 160" fill="none" stroke="{_h(p['map_road'])}" stroke-width="5" filter="url(#rough2)"/>
          <g stroke="{line}" stroke-width="1" opacity="0.5" fill="none" filter="url(#rough2)">
            <rect x="120" y="42" width="34" height="24"/><rect x="196" y="56" width="42" height="18"/><rect x="300" y="40" width="30" height="30"/>
          </g>
        </svg>
        <!-- 准星：四向刻度 + 中心鱼（替换原点）-->
        <div style="position:absolute;left:50%;top:44%;transform:translate(-50%,-50%)">
          <svg width="60" height="60" viewBox="0 0 60 60" aria-hidden="true"><g stroke="{white}" stroke-width="2.6" stroke-linecap="round" filter="url(#rough2)">
            <path d="M30 4 L30 15 M30 45 L30 56 M4 30 L15 30 M45 30 L56 30"/></g>
            <circle cx="30" cy="30" r="17" fill="none" stroke="{white}" stroke-width="2" opacity="0.9" filter="url(#rough2)"/>
          </svg>
          <div style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%)">{crosshair_fish}</div>
        </div>
        <div class="tag" style="position:absolute;left:8px;top:8px;font-size:10px;padding:2px 8px">⌖ 拖动地图选点</div>
        <div style="position:absolute;right:8px;bottom:6px;font-size:8.5px;color:{muted}">Map data · OpenStreetMap · CARTO</div>
      </div>
      <!-- 236dp 信息条：分数摘要 + 徽标 + 窗口 -->
      <div class="card" style="margin-top:-14px;height:78px;z-index:6;display:flex;align-items:center;padding:0 14px;gap:12px">
        <div style="text-align:center;min-width:62px">
          <div class="disp" style="font-size:38px;color:{accent};line-height:1">{SCORE}</div>
          <div class="bodyf" style="font-size:10px;color:{muted}">/ 100</div>
        </div>
        <div style="width:1.6px;height:42px;background:{line};opacity:0.35"></div>
        <div style="flex:1">
          <div class="disp" style="font-size:16px;color:{primary}">{BAND_LABELS[SCORE_BAND]} <span class="hl">●</span></div>
          <div class="row" style="gap:5px;margin-top:5px">
            <span class="tag" style="font-size:9px;padding:1px 6px;border-width:1.6px">{BADGES[0]}</span>
            <span class="tag" style="font-size:9px;padding:1px 6px;border-width:1.6px">{BADGES[2]}</span>
            <span class="bodyf mut" style="font-size:10px">{wc['note']}</span>
          </div>
        </div>
        <div style="width:40px;height:40px;border:2px dashed {green};border-radius:12px;display:flex;align-items:center;justify-content:center;transform:rotate(-6deg)">{fish_icon(style_id, water, p['green'], 28)}</div>
      </div>
    </div>"""

    # DaySelector（52dp 两行）+ 五档色带（档位标记用鱼，恒带文字 N-7）
    days = []
    for i, (d, wd) in enumerate([("12","周四"),("13","周五"),("14","周六"),("15","周日"),("16","周一")]):
        sel = i == 1
        days.append(f"""<div style="width:56px;height:52px;border:2px solid {line};border-radius:16px;display:flex;flex-direction:column;align-items:center;justify-content:center;
          background:{accent if sel else card};color:{white if sel else ink};box-shadow:{('2px 2px 0 '+line+'55') if sel else 'none'};flex-shrink:0">
          <b class="disp" style="font-size:17px;line-height:1">{d}</b><span class="bodyf" style="font-size:10px;margin-top:3px;opacity:0.9">{wd}</span></div>""")
    bandfish = ""
    for i in range(5):
        sel = i == SCORE_BAND
        # 档位 = 鱼的数量（5→1 条），选中档加白色手绘描边卡；鱼恒配文字（N-7）
        nfish = 5 - i
        fishe = "".join(f"<span style='display:inline-block;width:13px;height:13px;vertical-align:middle'>{fish_icon(style_id, water, (white if i<3 else ink), 13)}</span>" for _ in range(nfish))
        bgc = _h(p["band"][i])
        txtc = white if i < 3 else ink
        bandfish += f"""<div style='flex:1;display:flex;flex-direction:column;align-items:center;justify-content:center;height:34px;background:{bgc};
          border:{'2.6px solid '+_h(p['accent2']) if sel else '1.6px solid '+line};border-radius:9px;opacity:{1 if sel else 0.72};
          box-shadow:{'2px 2px 0 '+line if sel else 'none'};gap:1px'>
          <span style='line-height:0;height:13px;white-space:nowrap;overflow:hidden'>{fishe}</span>
          <span style='font-size:8.5px;color:{txtc};font-weight:{700 if sel else 600};white-space:nowrap'>{BAND_LABELS[i]}</span></div>"""
    selector = f"""
    <div style="padding:10px 14px 0;position:relative;z-index:4">
      <div class="row" style="gap:6px">{''.join(days)}</div>
      <div class="row" style="gap:4px;margin-top:10px">{bandfish}</div>
      <div class="bodyf mut" style="font-size:9.5px;margin-top:4px;text-align:center">五档 · 档位恒带文字 · 鱼多者优</div>
    </div>"""

    # 五 Tab（渲染序锁定，图标=鱼）
    tabitems = []
    for i, t in enumerate(tabs):
        sel = i == 0
        tabitems.append(f"""<div style="flex:1;display:flex;flex-direction:column;align-items:center;gap:3px;padding:6px 0 4px;border-bottom:{('3px solid '+accent) if sel else '0'};opacity:{1 if sel else 0.72}">
          <div style="height:22px">{fish_icon(style_id, water, (accent if sel else p['muted']), 22)}</div>
          <span class="bodyf" style="font-size:10px;font-weight:{700 if sel else 400};color:{accent if sel else muted}">{t}</span>
        </div>""")
    tabrow = f"""<div style="margin:10px 14px 0;border:2px solid {line};border-radius:14px;background:{card};display:flex;position:relative;z-index:4">{''.join(tabitems)}</div>"""

    # Tab 内容：因子条 + 潮汐/水位曲线（一眼区分⑤⑥）+ 水质 1×3
    chart_bars = "".join(
        f"""<rect x="{18+i*46}" y="{70-int([30,48,70,40,58,86,64,36,52][i]*0.6)}" width="26" height="{int([30,48,70,40,58,86,64,36,52][i]*0.6)}" rx="5" fill="{green if i!=5 else accent}" stroke="{line}" stroke-width="1.8" filter="url(#rough2)"/><text x="{31+i*46}" y="96" font-size="8" fill="{muted}" text-anchor="middle" font-family="KaiTi">{6+i}</text>"""
        for i in range(9))
    chart = f"""
    <div class="card" style="margin:10px 14px 0;padding:8px 12px 6px;position:relative;z-index:4">
      <div class="row" style="justify-content:space-between">
        <span class="disp" style="font-size:13px;color:{primary}">{wc['chart']} <span class="bodyf mut" style="font-size:10px">今天</span></span>
        <span class="tag" style="font-size:9px;padding:1px 7px">{wc['hi']} 14:20 · {wc['lo']} 08:10</span>
      </div>
      <svg width="100%" height="100" viewBox="0 0 420 100" aria-hidden="true">
        <path d="M10 70 L410 70" stroke="{line}" stroke-width="1.4" opacity="0.35" filter="url(#rough2)"/>
        <polyline points="18,60 64,46 110,28 156,44 202,34 248,18 294,30 340,48 386,40" fill="none" stroke="{accent}" stroke-width="2.8" stroke-linecap="round" filter="url(#rough2)"/>
        {chart_bars}
      </svg>
      <div class="bodyf mut" style="font-size:9px;text-align:center">{wc['chart_unit']} · 源更新 — · 本机拉取 09:41</div>
    </div>"""
    watercards = []
    for lab, val, unit in [("水温","19.6","℃"),("溶解氧","7.2","mg/L"),("浊度","3.1","NTU")]:
        watercards.append(f"""<div class="card" style="flex:1;height:66px;padding:7px 8px;background:{card2}">
          <div class="bodyf mut" style="font-size:10px">{lab}</div>
          <div class="disp" style="font-size:20px;color:{primary};margin-top:3px">{val}<span style="font-size:10px;color:{muted}"> {unit}</span></div>
        </div>""")
    waterrow = f"""<div class="row" style="gap:8px;margin:9px 14px 0;position:relative;z-index:4">{''.join(watercards)}</div>"""

    # Top10 入口（鱼形排名）+ 免责入口（恒5条）
    top10 = f"""
    <div style="margin:9px 14px 0;position:relative;z-index:4;border:2.2px solid {line};border-radius:14px;background:{primary};padding:8px 12px;display:flex;align-items:center;gap:10px;box-shadow:2px 3px 0 {line}44">
      <div class="row" style="gap:-2px">{fish_icon(style_id, water, accent2, 20)}{fish_icon(style_id, water, accent2, 24)}{fish_icon(style_id, water, accent2, 20)}</div>
      <div style="flex:1"><div class="disp" style="font-size:13px;color:{white}">全部收藏 Top10</div><div class="bodyf" style="font-size:9.5px;color:{white}AA">时段筛选 · 本周末窗口</div></div>
      <span style="color:{accent2};font-size:18px">›</span>
    </div>
    <div class="bodyf" style="margin:8px 16px 0;font-size:9px;color:{muted};position:relative;z-index:4;text-align:center">ⓘ 免责声明 · 共 5 条 · 数据仅供参考，不构成渔获保证</div>
    <div style="position:absolute;left:0;right:0;bottom:0;height:16px;background:linear-gradient(0deg,{_h(p['bg2'])},transparent);z-index:5"></div>"""

    return f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>TideSpot · {s['name']} · {wc['water_short']}详情页</title>
<style>{page_css(style_id, water)}</style></head><body>
{svg_defs(style_id, water)}
<div class="bgfill"></div>
<div class="screen">{status}{header}{mapband}{selector}{tabrow}{chart}{waterrow}{top10}</div>
{grain_overlay()}
</body></html>"""


# ---------------------------------------------------------------------------
# 地图选点页（地图优先导航：顶部浮层 + 准星 + 底部信息条 + Top10 入口，无底部 Tab）
# ---------------------------------------------------------------------------
def render_map(style_id, water):
    s = STYLES[style_id]; p = pal(style_id, water); f = s["font"]; wc = WATER_COPY[water]
    ink=_h(p["ink"]); line=_h(p["line"]); card=_h(p["card"]); card2=_h(p["card2"])
    accent=_h(p["accent"]); accent2=_h(p["accent2"]); green=_h(p["green"]); white=_h(p["white"])
    primary=_h(p["primary"]); muted=_h(p["muted"])
    motif = water_motif(style_id, water, 54)
    cross = fish_icon(style_id, water, p["accent"], 34)
    status = f"""<div class="statusbar"><span>9:41</span><div class="r"><span>▮▮▮</span><span>◕</span><span>▰</span></div></div><div class="notch"></div>"""
    # 全屏手绘地图底
    mapbg = f"""<svg width="390" height="844" viewBox="0 0 390 844" preserveAspectRatio="xMidYMid slice" aria-hidden="true" style="position:absolute;inset:0;z-index:1">
      <rect width="390" height="844" fill="{_h(p['map_land'])}"/>
      <path d="M-20 470 Q80 430 170 480 Q260 530 340 470 Q390 440 410 470 L410 864 L-20 864 Z" fill="{_h(p['map_water'])}" filter="url(#rough2)"/>
      <path d="M-10 150 Q120 210 230 130 Q320 70 400 150" fill="none" stroke="{_h(p['map_road'])}" stroke-width="14" filter="url(#rough2)"/>
      <path d="M60 -10 Q110 300 40 560 Q10 700 80 860" fill="none" stroke="{_h(p['map_road'])}" stroke-width="12" filter="url(#rough2)"/>
      <path d="M300 -10 Q250 300 330 560" fill="none" stroke="{_h(p['map_road'])}" stroke-width="10" filter="url(#rough2)"/>
      <path d="M-10 320 Q390 300 400 330" fill="none" stroke="{_h(p['map_road'])}" stroke-width="8" filter="url(#rough2)"/>
      <g fill="none" stroke="{line}" stroke-width="1.1" opacity="0.32" filter="url(#rough2)">
        <rect x="150" y="180" width="46" height="34"/><rect x="230" y="220" width="60" height="26"/><rect x="120" y="600" width="52" height="30"/>
        <path d="M0 640 Q100 620 180 660"/><path d="M210 380 Q280 360 340 400"/>
      </g>
      <g fill="{green}" opacity="0.55"><circle cx="105" cy="250" r="9"/><circle cx="270" cy="560" r="11"/><circle cx="200" cy="700" r="8"/></g>
    </svg>"""
    # 顶部搜索浮层 + 水体徽标
    top = f"""<div style="position:absolute;left:0;right:0;top:38px;z-index:5;padding:8px 14px">
      <div class="card" style="height:44px;display:flex;align-items:center;gap:9px;padding:0 13px">
        <span style="font-size:17px;color:{accent}">⌕</span>
        <span class="bodyf" style="font-size:13px;color:{muted};flex:1">搜索地点 / 水域</span>
        <span style="width:1.5px;height:20px;background:{line};opacity:.3"></span>
        <span style="font-size:15px;color:{primary}">☷</span>
      </div>
      <div style="margin-top:8px;display:flex;gap:7px;align-items:center;flex-wrap:wrap">
        <span class="tag" style="background:{card};color:{ink};border-color:{line};box-shadow:2px 2px 0 {line}55">{motif}<b style="font-size:11px">{wc['badge']}</b></span>
        <span class="tag" style="background:{card};color:{muted};border-color:{line};font-size:10px">Map data · OpenStreetMap · CARTO</span>
      </div>
    </div>"""
    # 准星（中心鱼）
    crosshair = f"""<div style="position:absolute;left:50%;top:46%;transform:translate(-50%,-50%);z-index:4">
      <svg width="78" height="78" viewBox="0 0 78 78" aria-hidden="true"><g stroke="{white}" stroke-width="3" stroke-linecap="round" filter="url(#rough2)">
        <path d="M39 2 L39 20 M39 58 L39 76 M2 39 L20 39 M58 39 L76 39"/></g>
        <circle cx="39" cy="39" r="24" fill="none" stroke="{white}" stroke-width="2.4" opacity="0.92" filter="url(#rough2)"/></svg>
      <div style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%)">{cross}</div>
      <div class="bodyf" style="position:absolute;left:50%;top:calc(50% + 46px);transform:translateX(-50%);white-space:nowrap;background:{ink}CC;color:{white};font-size:10px;padding:3px 10px;border-radius:10px">⌖ 准星落点 · 拖动地图选点</div>
    </div>"""
    # 右上工具（定位 / 图层）
    tools = f"""<div style="position:absolute;right:14px;top:150px;z-index:5;display:flex;flex-direction:column;gap:9px">
      <div class="card" style="width:40px;height:40px;display:flex;align-items:center;justify-content:center;font-size:18px;color:{primary}">◎</div>
      <div class="card" style="width:40px;height:40px;display:flex;align-items:center;justify-content:center;font-size:17px;color:{primary}">▤</div>
    </div>"""
    # 底部信息条（236dp 浮条 + Top10 横幅）
    bottom = f"""<div style="position:absolute;left:0;right:0;bottom:0;z-index:5;padding:0 12px 16px">
      <div class="card" style="padding:12px 14px;box-shadow:2px 3px 0 {line}44">
        <div class="row" style="justify-content:space-between;align-items:flex-start">
          <div><div class="disp" style="font-size:16px;color:{ink}">{wc['place']}</div>
               <div class="bodyf mut" style="font-size:10px;margin-top:2px">⌖ {wc['coord']}</div></div>
          <span class="tag" style="font-size:10px;background:{accent2};color:{ink};border-color:{line}">{wc['water_short']}</span>
        </div>
        <div class="row" style="gap:9px;margin-top:10px;align-items:center">
          <div class="disp" style="font-size:30px;color:{accent};line-height:1">{SCORE}</div>
          <div style="flex:1"><div class="disp" style="font-size:14px;color:{primary}">{BAND_LABELS[SCORE_BAND]}</div>
              <div class="bodyf mut" style="font-size:9.5px;margin-top:2px">点击查看 {wc['chart']} · 五档指数</div></div>
          <div style="width:46px;height:46px;border:2px dashed {green};border-radius:12px;display:flex;align-items:center;justify-content:center;transform:rotate(-6deg)">{fish_icon(style_id,water,p['green'],32)}</div>
        </div>
        <div style="margin-top:10px;border-top:1.6px dashed {line}55;padding-top:9px;display:flex;align-items:center;gap:9px">
          <div class="row">{fish_icon(style_id,water,accent2,18)}{fish_icon(style_id,water,accent2,22)}{fish_icon(style_id,water,accent2,18)}</div>
          <div class="bodyf" style="font-size:11px;color:{primary};flex:1;font-weight:700">全部收藏 Top10 · 最佳窗口</div><span style="color:{accent};font-size:16px">›</span>
        </div>
      </div>
    </div>"""
    return f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>TideSpot · {s['name']} · {wc['water_short']}地图选点</title>
<style>{page_css(style_id, water)}</style></head><body>{svg_defs(style_id,water)}<div class="screen">
{mapbg}{status}{top}{crosshair}{tools}{bottom}</div>{grain_overlay()}</body></html>"""


# ---------------------------------------------------------------------------
# 收藏页（搜索/排序/分组/批量 + 鱼形收藏标记 + 空状态鱼骨）
# ---------------------------------------------------------------------------
def render_favorites(style_id, water):
    s=STYLES[style_id]; p=pal(style_id,water); f=s["font"]; wc=WATER_COPY[water]
    ink=_h(p["ink"]); line=_h(p["line"]); card=_h(p["card"]); card2=_h(p["card2"])
    accent=_h(p["accent"]); accent2=_h(p["accent2"]); green=_h(p["green"]); white=_h(p["white"]); primary=_h(p["primary"]); muted=_h(p["muted"])
    status=f"""<div class="statusbar"><span>9:41</span><div class="r"><span>▮▮▮</span><span>◕</span><span>▰</span></div></div><div class="notch"></div>"""
    head=f"""<div style="padding:6px 16px 8px;position:relative;z-index:3">
      <div class="disp" style="font-size:24px;color:{ink}">我的收藏</div>
      <div class="bodyf mut" style="font-size:11px;margin-top:2px">12 个点位 · 3 个分组</div></div>
    <div style="padding:0 14px;position:relative;z-index:3"><div class="card" style="height:40px;display:flex;align-items:center;gap:8px;padding:0 12px">
      <span style="font-size:16px;color:{accent}">⌕</span><span class="bodyf" style="font-size:12px;color:{muted}">搜索收藏点位</span>
      <span style="margin-left:auto;font-size:13px;color:{primary}">☷</span></div></div>
    <div class="row" style="padding:9px 14px 0;gap:6px;position:relative;z-index:3">
      <span class="tag" style="background:{primary};color:{white};border-color:{line};font-size:10px">综合推荐 ▾</span>
      <span class="tag" style="font-size:10px">全部分组 ▾</span>
      <span class="tag" style="font-size:10px;margin-left:auto">⠿ 批量</span>
    </div>"""
    favs=[("小麦岛外海","青岛 · 海域",86,"爆护窗口",0),("鲤鱼湾","千岛湖 · 湖湾",78,"很适合",1),("东澳礁","珠海 · 近海",64,"可以",2)]
    cards=""
    for name,sub,score,band,bi in favs:
        nfish=5-bi
        fishe="".join(f"<span style='display:inline-block;width:12px'>{fish_icon(style_id,water,white,12)}</span>" for _ in range(nfish))
        cards+=f"""<div class="card" style="margin:10px 14px 0;padding:11px 12px;display:flex;align-items:center;gap:11px;border-left:6px solid {_h(p['band'][bi])}">
          <div style="width:46px;height:46px;border:2px solid {line};border-radius:12px;background:{card2};display:flex;align-items:center;justify-content:center;transform:rotate(-4deg)">{fish_icon(style_id,water,p['accent'],34)}</div>
          <div style="flex:1"><div class="disp" style="font-size:14px;color:{ink}">{name}</div>
            <div class="bodyf mut" style="font-size:10px;margin-top:2px">{sub}</div>
            <div class="row" style="gap:5px;margin-top:5px;background:{_h(p['band'][bi])};border-radius:7px;padding:2px 7px;display:inline-flex">{fishe}<span style="font-size:9px;color:{white if bi<3 else ink};font-weight:700;margin-left:3px">{band}</span></div>
          </div>
          <div style="text-align:center"><div class="disp" style="font-size:26px;color:{accent};line-height:1">{score}</div><div class="bodyf mut" style="font-size:9px">分</div></div>
          <div style="width:26px;height:26px;border:2px solid {line};border-radius:50%;display:flex;align-items:center;justify-content:center;background:{accent2}">{fish_icon(style_id,water,p['green'],16)}</div>
        </div>"""
    # Top10 深蓝横幅 + 空态提示（鱼骨）
    extra=f"""<div style="margin:11px 14px 0;border:2.2px solid {line};border-radius:14px;background:{primary};padding:10px 13px;display:flex;align-items:center;gap:9px;box-shadow:2px 3px 0 {line}44">
      <div class="row">{fish_icon(style_id,water,accent2,18)}{fish_icon(style_id,water,accent2,22)}{fish_icon(style_id,water,accent2,18)}</div>
      <div style="flex:1"><div class="disp" style="font-size:13px;color:{white}">全部收藏 Top10</div><div class="bodyf" style="font-size:9.5px;color:{white}AA">本周末 · 时段筛选</div></div><span style="color:{accent2};font-size:17px">›</span></div>
    <div style="margin:10px 14px 0;border:1.8px dashed {line}66;border-radius:12px;padding:8px 12px;display:flex;align-items:center;gap:9px;opacity:.8">
      <div style="width:52px">{fish_svg(style_id,water,'bone',w=52)}</div>
      <div class="bodyf mut" style="font-size:10px;line-height:1.5">当前范围内暂无符合条件的最佳窗口<br>试试切换「全部」或自选时段</div></div>"""
    return f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>TideSpot · {s['name']} · {wc['water_short']}收藏</title>
<style>{page_css(style_id,water)}</style></head><body>{svg_defs(style_id,water)}<div class="screen">{status}{head}{cards}{extra}</div>{grain_overlay()}</body></html>"""


# ---------------------------------------------------------------------------
# 设置页（六版块，凭据区 + API Key 正式启用，逐源隐私披露）
# ---------------------------------------------------------------------------
def render_settings(style_id, water):
    s=STYLES[style_id]; p=pal(style_id,water); f=s["font"]; wc=WATER_COPY[water]
    ink=_h(p["ink"]); line=_h(p["line"]); card=_h(p["card"]); card2=_h(p["card2"])
    accent=_h(p["accent"]); accent2=_h(p["accent2"]); green=_h(p["green"]); white=_h(p["white"]); primary=_h(p["primary"]); muted=_h(p["muted"])
    status=f"""<div class="statusbar"><span>9:41</span><div class="r"><span>▮▮▮</span><span>◕</span><span>▰</span></div></div><div class="notch"></div>"""
    head=f"""<div style="padding:6px 16px 10px;position:relative;z-index:3"><div class="disp" style="font-size:24px;color:{ink}">设置</div>
      <div class="bodyf mut" style="font-size:11px;margin-top:2px">潮点 TideSpot · v1.16.0</div></div>"""
    def section(icon,title,rows, badge=None):
        rr="".join(f"""<div class="row" style="justify-content:space-between;padding:5px 0;border-bottom:1.2px dashed {line}40">
          <span class="bodyf" style="font-size:11.5px;color:{ink}">{r[0]}</span>
          <span class="bodyf" style="font-size:10.5px;color:{muted}">{r[1]}</span></div>""" for r in rows)
        return f"""<div class="card" style="margin:0 14px 8px;padding:8px 12px">
          <div class="row" style="gap:7px;margin-bottom:2px"><span style="width:22px">{fish_icon(style_id,water,accent,20)}</span>
            <span class="disp" style="font-size:12.5px;color:{primary}">{title}</span>
            {f'<span class="tag" style="margin-left:auto;font-size:8px;background:{accent2};color:{ink}">{badge}</span>' if badge else ''}</div>{rr}</div>"""
    secs=""
    secs+=section("","显示",[("深色模式","跟随系统"),("地图样式","彩色 Voyager ▾")])
    secs+=section("","数据与网络",[("离线缓存","已开启"),("源更新提示","3h / 6h TTL"),("双通道定位","已启用")])
    secs+=section("","用例 · 物种 · 权重",[("垂钓用例","海水 · 岸钓"),("物种预设","已解锁 6 种")])
    secs+=section("","API Key（可选）",[("凭据状态","已保存 ✓")],badge="正式启用")
    secs+=section("","开源许可与数据署名",[("底图","OpenStreetMap · CARTO"),("模型","Open-Meteo / Copernicus")])
    # 凭据区（Copernicus）+ 隐私披露
    cred=f"""<div class="card" style="margin:0 14px 8px;padding:8px 12px;border-color:{green}">
      <div class="row" style="gap:7px;margin-bottom:5px"><span style="width:22px">{fish_icon(style_id,water,green,20)}</span>
        <span class="disp" style="font-size:12.5px;color:{primary}">数据源凭据 · Copernicus</span>
        <span class="tag" style="margin-left:auto;font-size:8px;background:{green};color:{white};border-color:{green}">本地保存</span></div>
      <div class="row" style="gap:7px"><div style="flex:1;border:1.5px solid {line};border-radius:8px;padding:5px 8px"><div class="bodyf mut" style="font-size:8.5px">用户名 / 邮箱</div><div class="bodyf" style="font-size:10.5px;color:{ink}">··@··.··</div></div>
        <div style="flex:1;border:1.5px solid {line};border-radius:8px;padding:5px 8px"><div class="bodyf mut" style="font-size:8.5px">密码</div><div class="bodyf" style="font-size:10.5px;color:{ink}">••••••••　👁</div></div></div>
      <div class="row" style="gap:7px;margin-top:6px"><div style="flex:1;border:1.8px solid {line};border-radius:8px;text-align:center;padding:5px;background:{accent};color:{white}"><b class="bodyf" style="font-size:10.5px">校验并保存</b></div>
        <div style="border:1.5px solid {line};border-radius:8px;padding:5px 11px" class="bodyf" style="font-size:10.5px">清除</div></div>
      <div class="bodyf mut" style="font-size:8.5px;line-height:1.45;margin-top:5px">凭据仅本地存储、用于拉取卫星数据；不上传、不共享。</div></div>"""
    privacy=f"""<div class="card" style="margin:0 14px 8px;padding:8px 12px">
      <div class="row" style="gap:7px;margin-bottom:3px"><span style="width:22px">{fish_icon(style_id,water,accent,20)}</span>
        <span class="disp" style="font-size:12.5px;color:{primary}">隐私披露 · 逐源</span></div>
      <div class="bodyf mut" style="font-size:9px;line-height:1.5">天气 / 海洋 / 潮汐 / 水质等请求会发送点位经纬度至对应数据源；逆地理与凭据可分别关闭。不声明零数据传输。</div>
      <div class="bodyf" style="font-size:9.5px;color:{accent};margin-top:4px">关于 · 版本 v1.16.0　|　免责声明 ›</div></div>"""
    return f"""<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8"><title>TideSpot · {s['name']} · 设置</title>
<style>{page_css(style_id,water)}</style></head><body>{svg_defs(style_id,water)}<div class="screen" style="overflow:hidden">{status}{head}{secs}{cred}{privacy}</div>{grain_overlay()}</body></html>"""


# ---------------------------------------------------------------------------
# 深色详情页（每版深色调，验证深色适配）
# ---------------------------------------------------------------------------
def render_detail_dark(style_id, water="sea"):
    """深色模式：单独设计，非机械反转；沿用 v3 深色独立设计原则"""
    s=STYLES[style_id]; f=s["font"]; wc=WATER_COPY[water]
    # 深色 token（独立于浅色，冷暖随水体微调）
    if water=="sea":
        bg="0E1C2B"; bg2="132538"; ink="E6EDF5"; line="3A4C5E"; card="17293B"; card2="1E344A";
        primary="90CAF9"; accent="FF8A65"; accent2="80CBC4"; green="4DB6AC"; muted="9FB0C0"; white="F2F7FB"; map_land="1B3044"; map_water="123A55"; map_road="2A4358"
    else:
        bg="101D18"; bg2="162A20"; ink="E6F0E9"; line="3A5346"; card="172B21"; card2="1E372A";
        primary="A5D6A7"; accent="FFAB91"; accent2="80CBC4"; green="66BB6A"; muted="9DB8A8"; white="F2F8F3"; map_land="1C3326"; map_water="123C2E"; map_road="2C4A38"
    P=dict(bg=bg,bg2=bg2,ink=ink,line=line,card=card,card2=card2,primary=primary,accent=accent,accent2=accent2,green=green,muted=muted,white=white,map_land=map_land,map_water=map_water,map_road=map_road,
           band=["2E7D52","66BB6A","FDD835","FB8C00","E53935"])
    # 临时注入调色板，复用详情页渲染逻辑
    orig=STYLES[style_id]["pal"][water]
    STYLES[style_id]["pal"][water]=P
    try:
        html=render_detail(style_id,water)
        html=html.replace(f"<title>TideSpot · {s['name']} · {wc['water_short']}详情页</title>",
                          f"<title>TideSpot · {s['name']} · 深色 · {wc['water_short']}详情页</title>")
    finally:
        STYLES[style_id]["pal"][water]=orig
    return html


# ---------------------------------------------------------------------------
# 主程序：产出全部屏 HTML
# ---------------------------------------------------------------------------
def build_all(only=None):
    out = ROOT / "_work" / "pages"
    out.mkdir(parents=True, exist_ok=True)
    made=[]
    for sid in STYLES:
        if only and sid != only: continue
        jobs = {
            f"{sid}__detail-sea": render_detail(sid, "sea"),
            f"{sid}__detail-inland": render_detail(sid, "inland"),
            f"{sid}__map-sea": render_map(sid, "sea"),
            f"{sid}__map-inland": render_map(sid, "inland"),
            f"{sid}__favorites": render_favorites(sid, "sea"),
            f"{sid}__settings": render_settings(sid, "sea"),
            f"{sid}__dark-detail": render_detail_dark(sid, "sea"),
        }
        for name,html in jobs.items():
            (out/f"{name}.html").write_text(html,encoding="utf-8")
            made.append(name)
    return made


if __name__ == "__main__":
    arg = sys.argv[1] if len(sys.argv) > 1 else None
    files = build_all(arg)
    print(f"PAGE_HTML={len(files)}")
    for x in files:
        print(" ", x)
