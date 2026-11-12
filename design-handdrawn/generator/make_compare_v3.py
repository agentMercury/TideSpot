"""V3 / D5 拼图：质感前后、纸纹局部放大、双水体浅深、设计稿对照、边界对照、四态总览。"""
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


# 1) 质感前后
pair(D / "v2-inland-light.png", D / "v3-inland-light.png", D / "v3-before-after.png",
     "D5 前 · 色板已对齐（v2）", "D5 后 · 手绘质感层（v3）",
     "新增纸纹颗粒 / 鱼形角标与水印 / 手绘不规则描边 / 套印错位色边；配色与字族不变")

# 2) 纸纹局部放大（同一纯背景块，8 倍最近邻放大保留像素颗粒）
BOX = (16, 260, 80, 324)
z = 8
left = Image.open(D / "v2-inland-light.png").convert("RGB").crop(BOX).resize((64 * z, 64 * z), Image.NEAREST)
right = Image.open(D / "v3-inland-light.png").convert("RGB").crop(BOX).resize((64 * z, 64 * z), Image.NEAREST)
pad, head = 28, 78
c = Image.new("RGB", (left.width * 2 + pad * 3, 64 * z + head + 64), "#E4D9BC")
d = ImageDraw.Draw(c)
d.text((pad, 18), "D5 前 · 纯色背景 sd=0.00", fill="#16324F", font=font(28))
d.text((pad * 2 + left.width, 18), "D5 后 · 纸纹颗粒 sd=5.59", fill="#16324F", font=font(28))
c.paste(left, (pad, head))
c.paste(right, (pad * 2 + left.width, head))
d.text((pad, head + 64 * z + 8),
       "同一坐标 64×64 纯背景块，8× 最近邻放大；深色态用暖白颗粒，方向相反同样生效",
       fill="#5C5540", font=font(20))
c.save(D / "v3-grain-detail.png", "PNG")
print("SAVED v3-grain-detail.png", c.size)

# 3) 双水体浅 / 深
pair(D / "v3-inland-light.png", D / "v3-sea-light.png", D / "v3-water-light.png",
     "浅色 · 内陆水体", "浅色 · 近海/海洋",
     "纸底苔绿墨绿 vs 米黄藏蓝；第二 Tab「水位（无潮汐）」vs「潮流」；水印山纹 vs 浪纹")
pair(D / "v3-inland-dark.png", D / "v3-sea-dark.png", D / "v3-water-dark.png",
     "深色 · 内陆水体", "深色 · 近海/海洋",
     "深墨绿 vs 深藏蓝；暖白纸纹在两种深色上均可辨；皮肤/水体/明暗三轴正交")

# 4) 参照稿说明（勘误：左为早期 base 旧稿，非真机参照稿）
# 注意：screens/detail-inland.png 是 engine.py 的早期 78/白底旧稿；真机参照的是
# engine_full.py 的 82/苔绿纸底新稿（screens_full/detail-tab-*）+ 工程骨架。
# 两列并非同一张稿的"设计 vs 实现"，只是并列展示两个并存版本，不可当作对齐证据。
pair(A / "screens" / "detail-inland.png", D / "v3-inland-light.png", D / "v3-design-device.png",
     "早期 base 旧稿 · 78/白底（非参照稿）", "真机 D5 · 参照新稿82/苔绿纸底",
     "【勘误】两列非同稿：左=engine.py旧稿，右=真机(参照engine_full.py新稿)；仅并列展示，非对齐证据")

# 5) 试点边界：地图页仍是原样
pair(D / "v3-inland-map.png", D / "v3-inland-light.png", D / "v3-boundary.png",
     "地图页 · 未换皮（原蓝色）", "详情页 · 已换皮（手绘 A）",
     "D5 只作用于详情页一屏；MAP / FAVORITES / SETTINGS 三页零视觉影响")

# 6) 四态总览
items = [
    ("v3-inland-light.png", "内陆浅色 · D5 质感层"),
    ("v3-sea-light.png", "海洋浅色 · D5 质感层"),
    ("v3-inland-dark.png", "内陆深色 · D5 质感层"),
    ("v3-sea-dark.png", "海洋深色 · D5 质感层"),
]
th = 700
thumbs = [(fit(D / p, th), t) for p, t in items]
gap, top = 24, 66
W = thumbs[0][0].width * 2 + gap * 3
H = th * 2 + top + gap * 3
c = Image.new("RGB", (W, H), "#E4D9BC")
d = ImageDraw.Draw(c)
d.text((gap, 16), "D5 复拍 · 双水体 × 明暗 四态", fill="#16324F", font=font(34))
for i, (im, title) in enumerate(thumbs):
    x = gap + (i % 2) * (thumbs[0][0].width + gap)
    y = top + (i // 2) * (th + gap + 34) + gap
    c.paste(im, (x, y))
    d.text((x, y + th + 6), title, fill="#5C5540", font=font(24))
c.save(D / "v3-overview.png", "PNG")
print("SAVED v3-overview.png", c.size)
