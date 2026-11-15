# -*- coding: utf-8 -*-
"""重渲 30 屏后，只同步 spec.json 的文件哈希与 index-full.html 缩略图。

**为什么不能直接跑 `build_style.build_spec()`**：它会用 `json.dumps` 整体重写
`spec.json`，抹掉 CHG-93 起追加的 `authoritative_design` / `visual_tokens` 等定稿节点，
以及历史上手工维护的 `frozen_contract` / `water_differentiators` 等字段。

本脚本只做字符串级替换：
  - 按 spec 里已登记的 png/html 相对路径重算 sha256，逐个替换 64 位 hex 值；
  - 按 build_style.BASE_NAMES + FULL_NAMES 的顺序重新生成 30 张 190px JPEG 缩略图，
    依次替换 index 里的 base64 段，保留索引页的全部手写文案。
因此 spec.json / index-full.html 的行尾与格式原封不动（两者都是 CRLF）。

用法（在仓库根）：
    python design-handdrawn/generator/sync_spec_assets.py

前置：`build_style.build_html(...)` + `build_style.render_png(...)` 已跑完。
验收：`python design-handdrawn/generator/selfcheck_full.py` 应为 276 PASS / 0 FAIL。
"""
from __future__ import annotations

import base64
import hashlib
import io
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_style as BS  # noqa: E402

A = HERE.parent / "A-vintage-outdoor"

# spec 里的四类哈希字段 → 各自对应的路径字段
SHA_KEYS = {
    "sha256": "png",
    "html_sha256": "html",
    "sha256_png": "png",
    "sha256_html": "html",
}


def sha(rel: str) -> str:
    return hashlib.sha256((A / rel).read_bytes()).hexdigest()


def sync_spec() -> int:
    p = A / "spec.json"
    raw = p.read_text(encoding="utf-8", newline="")  # 保留 CRLF
    spec = json.loads(raw)
    items = list(spec["screens"]) + list(spec["full_ui"]["added_screens"])
    changed = 0
    for e in items:
        for key, path_key in SHA_KEYS.items():
            if key not in e or path_key not in e:
                continue
            old, new = e[key], sha(e[path_key])
            if old != new:
                if old not in raw:
                    raise SystemExit(f"spec 中找不到旧哈希 {old[:12]}（{e.get('key')}.{key}）")
                raw = raw.replace(old, new, 1)
                changed += 1
            e[key] = new
    p.write_text(raw, encoding="utf-8", newline="")
    json.loads(p.read_text(encoding="utf-8"))
    return changed


def sync_index() -> int:
    order = [(n, A / "screens") for n in BS.BASE_NAMES]
    order += [(n, A / "screens_full") for n in BS.FULL_NAMES]
    if len(order) != 30:
        raise SystemExit(f"屏幕清单应为 30，实为 {len(order)}")

    from PIL import Image

    thumbs = []
    for name, folder in order:
        im = Image.open(folder / f"{name}.png").convert("RGB")
        im = im.resize((190, int(im.height * 190 / im.width)))
        buf = io.BytesIO()
        im.save(buf, "JPEG", quality=82)
        thumbs.append(base64.b64encode(buf.getvalue()).decode("ascii"))

    p = A / "index-full.html"
    html = p.read_text(encoding="utf-8", newline="")  # 保留 CRLF
    pat = re.compile(r"data:image/jpeg;base64,([A-Za-z0-9+/=]+)")
    if len(pat.findall(html)) != 30:
        raise SystemExit("index-full.html 的 base64 缩略图数量不是 30")
    it = iter(thumbs)
    html = pat.sub(lambda m: "data:image/jpeg;base64," + next(it), html)
    p.write_text(html, encoding="utf-8", newline="")
    return 30


def main() -> None:
    print(f"spec 哈希更新 {sync_spec()} 项")
    print(f"索引缩略图更新 {sync_index()} 张")
    print("完成。请跑 selfcheck_full.py 确认为 276 PASS / 0 FAIL。")


if __name__ == "__main__":
    main()
