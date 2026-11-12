# -*- coding: utf-8 -*-
"""① 阶段拼图：D5 基线(v3) → 本轮(v4) 前后对照、局部放大、双水体、设计稿、边界、四态总览。"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

A = Path(__file__).resolve().parent.parent / "A-vintage-outdoor"
D = A / "device-compare"
FONT = r"C:\Windows\Fonts\msyh.ttc"


def font(sz):
    try:
        return ImageFont.truetype(FONT, sz)
    except OSError:
        return ImageFont.load_default()


def fit(p, h):
    im = Image.open(p).convert("RGB")
    return im.resize((max(1, int(im.width * h / im.height)), h), Image.LANCZOS)


def pair(lp, rp, out, lt, rt, note=""):
    H = 1250
    L, R = fit(lp, H), fit(rp, H)
    pad, head, foot = 28, 78, 48
    W = L.width + R.width + pad * 3
    c = Image.new("RGB", (W, H + head + foot), "#E4D9BC")
    d = ImageDraw.Draw(c)
    d.text((pad, 18), lt, fill="#16324F", font=font(30))
    d.text((pad * 2 + L.width, 18), rt, fill="#16324F", font=font(30))
    c.paste(L, (pad, head))
    c.paste(R, (pad * 2 + L.width, head))
    if note:
        d.text((pad, H + head + 6), note, fill="#5C5540", font=font(21))
    c.save(out, "PNG")
    print("SAVED", Path(out).name, c.size)


pair(D / "v3-inland-light.png", D / "v4-inland-light.png", D / "v4-before-after.png",
     "D5 基线 · 详情页浅色（v3）", "① 阶段 · 详情页浅色（v4）",
     "新增五档鱼数色带（5→1 + 档位文字 + 选中高亮）、Tab 鱼图标、图表手绘笔触；Top10 位置零位移")

# 局部放大：Tab 行 + 分数主卡 + 五档色带，上下叠放便于看细节
BOX = (0, 550, 1272, 1320)
W = 760
top = Image.open(D / "v3-inland-light.png").convert("RGB").crop(BOX).resize(
    (W, int((BOX[3] - BOX[1]) * W / (BOX[2] - BOX[0]))), Image.LANCZOS)
bot = Image.open(D / "v4-inland-light.png").convert("RGB").crop(BOX).resize(
    (W, int((BOX[3] - BOX[1]) * W / (BOX[2] - BOX[0]))), Image.LANCZOS)
pad, head = 28, 62
c = Image.new("RGB", (W + pad * 2, top.height * 2 + head + pad * 3 + 30), "#E4D9BC")
d = ImageDraw.Draw(c)
d.text((pad, 16), "v3 · D5 基线（纯色条 / 纯文字 Tab）", fill="#16324F", font=font(24))
c.paste(top, (pad, head))
d.text((pad, head + top.height + pad), "v4 · ① 阶段（鱼数色带 / 鱼图标 / 手绘笔触）", fill="#16324F", font=font(24))
c.paste(bot, (pad, head + top.height + pad + 34))
c.save(D / "v4-closeup.png", "PNG")
print("SAVED v4-closeup.png", c.size)

pair(D / "v4-inland-light.png", D / "v4-sea-light.png", D / "v4-water-light.png",
     "浅色 · 内陆水体", "浅色 · 近海/海洋",
     "苔绿墨绿 vs 米黄藏蓝；五档色带仍为冻结语义色，两水体一致")
pair(D / "v4-inland-dark.png", D / "v4-sea-dark.png", D / "v4-water-dark.png",
     "深色 · 内陆水体", "深色 · 近海/海洋",
     "深墨绿 vs 深藏蓝；五档色带不派生深色副本，符合冻结契约")
pair(A / "screens" / "detail-inland.png", D / "v4-inland-light.png", D / "v4-design-device.png",
     "设计稿 · 手绘A 内陆浅色", "真机 · ① 阶段内陆浅色",
     "五档鱼数 / Tab 鱼图标 / 手绘笔触已补齐；仍缺手绘地图带（用户本轮明确不做）")
pair(D / "v4-inland-map.png", D / "v4-inland-light.png", D / "v4-boundary.png",
     "地图页 · 未换皮（原蓝色）", "详情页 · 已换皮（手绘 A）",
     "地图页本轮零改动；MAP / FAVORITES / SETTINGS 三页保持原样")

items = [
    ("v4-inland-light.png", "内陆浅色 · ① 阶段"),
    ("v4-sea-light.png", "海洋浅色 · ① 阶段"),
    ("v4-inland-dark.png", "内陆深色 · ① 阶段"),
    ("v4-sea-dark.png", "海洋深色 · ① 阶段"),
]
th = 700
thumbs = [(fit(D / p, th), t) for p, t in items]
gap, top_h = 24, 66
W2 = thumbs[0][0].width * 2 + gap * 3
H2 = th * 2 + top_h + gap * 3
c = Image.new("RGB", (W2, H2), "#E4D9BC")
d = ImageDraw.Draw(c)
d.text((gap, 16), "① 阶段复拍 · 双水体 × 明暗 四态", fill="#16324F", font=font(34))
for i, (im, title) in enumerate(thumbs):
    x = gap + (i % 2) * (thumbs[0][0].width + gap)
    y = top_h + (i // 2) * (th + gap + 34) + gap
    c.paste(im, (x, y))
    d.text((x, y + th + 6), title, fill="#5C5540", font=font(24))
c.save(D / "v4-overview.png", "PNG")
print("SAVED v4-overview.png", c.size)
