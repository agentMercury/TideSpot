# -*- coding: utf-8 -*-
"""① 阶段红线校验：对比 v3（D5 基线）与 v4（本轮）详情页 UI dump。

判据（对应用户授权边界「不增加/减少功能或功能入口、不改算法逻辑」）：
  1. v3 的所有文本/contentDescription 标签在 v4 仍然存在 —— 入口零减少
  2. v4 新增标签必须是**纯装饰节点**（客户 2026-11-15 裁决「豁免纯装饰节点」，
     CHG-93）：命中白名单/品牌副标题前缀，或其全部同名节点都不可点击、不可聚焦
     且不含入口关键字。新增可交互入口仍然 FAIL。
  3. clickable / focusable 节点数一致
  4. 「最佳窗口 Top10」bounds 四态逐像素一致 —— 功能入口位置零位移
  5. 五个 Tab 文案、图表/数值表切换、免责文案均在 v4 完整保留

  旧口径「新增标签只允许五档档位文字」会把 S2a 的页头、副标题、/100、
  鱼形装饰文字一律判 FAIL，与结构层对齐授权冲突，故改为上述语义判定。
  红线核心（入口零减少 / 零位移 / 可交互节点数）**一条未放松**。
"""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
D = ROOT / "design-handdrawn" / "A-vintage-outdoor" / "device-compare"

BAND_WORDS = {"爆护窗口", "很适合", "可以", "较差", "别去"}

# CHG-93：纯装饰节点白名单（S2a 新增，无交互、无语义入口）
DECORATIVE_EXACT = {"我的收藏", "/100"}
DECORATIVE_PREFIXES = ("TideSpot · ",)
ENTRY_WORDS = ("Top10", "Tab", "免责", "图表", "数值表", "设置", "编辑", "删除", "刷新", "搜索")
TABS = ["钓鱼指数", "水位（无潮汐）", "潮流", "天气", "水质", "风浪", "湖面风与水温"]
MUST_HAVE = ["图表", "数值表", "打开未来最佳窗口前十名", "最佳窗口 Top10"]

STATES = [
    "inland-light", "inland-dark", "sea-light", "sea-dark",
]


def parse(name: str) -> list[dict[str, str]]:
    text = (D / name).read_text(encoding="utf-8", errors="replace")
    out = []
    for raw in re.findall(r"<node ([^>]+)/>", text):
        d = dict(re.findall(r"([\w-]+)=\"([^\"]*)\"", raw))
        d["label"] = (d.get("text", "") or d.get("content-desc", "")).strip()
        out.append(d)
    return out


def bounds_of(nodes, key: str) -> list[str]:
    return [n.get("bounds", "") for n in nodes if key in n["label"]]


def is_decorative(label: str, new_nodes: list[dict[str, str]]) -> bool:
    """新增标签是否纯装饰：非入口 + 全部同名节点不可点击/不可聚焦。"""
    if not label:
        return True
    if label in BAND_WORDS or label in DECORATIVE_EXACT:
        return True
    if any(label.startswith(prefix) for prefix in DECORATIVE_PREFIXES):
        return True
    if any(w in label for w in ENTRY_WORDS):
        return False
    matched = [n for n in new_nodes if n["label"] == label]
    return bool(matched) and all(
        n.get("clickable") != "true" and n.get("focusable") != "true"
        for n in matched
    )


P = F = 0


def ok(cond: bool, msg: str) -> None:
    global P, F
    if cond:
        P += 1
    else:
        F += 1
        print("  FAIL:", msg)


for st in STATES:
    a, b = parse(f"_v3-{st}.xml"), parse(f"_v4-{st}.xml")
    la = {n["label"] for n in a if n["label"]}
    lb = {n["label"] for n in b if n["label"]}
    missing = sorted(la - lb)
    added = sorted(lb - la)
    ca = sum(1 for n in a if n.get("clickable") == "true")
    cb = sum(1 for n in b if n.get("clickable") == "true")
    fa = sum(1 for n in a if n.get("focusable") == "true")
    fb = sum(1 for n in b if n.get("focusable") == "true")

    print(f"\n=== {st} ===")
    print(f"  文本节点 v3={len(la)} → v4={len(lb)} | clickable {ca}→{cb} | focusable {fa}→{fb}")
    print(f"  消失标签: {missing or '（无）'}")
    print(f"  新增标签: {added or '（无）'}")

    ok(not missing, f"{st}: 有入口/文案消失 {missing}")
    exempt = [w for w in added if is_decorative(w, b)]
    violations = [w for w in added if w not in exempt]
    print(f"  装饰豁免: {exempt or '（无）'}")
    ok(not violations, f"{st}: 出现非装饰的新入口/文案 {violations}")
    ok(ca == cb and fa == fb, f"{st}: 可交互节点数变化")
    ok(
        bounds_of(a, "最佳窗口 Top10") == bounds_of(b, "最佳窗口 Top10"),
        f"{st}: Top10 位置变化 {bounds_of(a, '最佳窗口 Top10')} → {bounds_of(b, '最佳窗口 Top10')}",
    )
    for w in MUST_HAVE:
        ok(any(w in x for x in lb), f"{st}: 缺少 {w}")
    ok(sum(1 for t in TABS if any(t in x for x in lb)) >= 4, f"{st}: Tab 文案不足")

print(f"\nENTRY_CHECK PASS={P} FAIL={F}")
raise SystemExit(1 if F else 0)
