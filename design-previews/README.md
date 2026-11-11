# UI 预览图汇总

本目录每张 PNG 由工作区对应的 `preview.html` 整页渲染而成（自动裁掉底部空白），可直接双击查看。

| 图片 | 来源 preview.html | 说明 | 尺寸 |
|---|---|---|---|
| `01-v3-official-design-handoff.png` | `design-handoff/preview.html` | v3.0.0 正式交付预览（当前正式基线） | 1500×10818 |
| `02-handdrawn-A-vintage-outdoor.png` | `design-handdrawn/A-vintage-outdoor/preview.html` | 手绘 A · 复古户外（图5+图6） | 1500×1645 |
| `03-handdrawn-B-gentleman-fish.png` | `design-handdrawn/B-gentleman-fish/preview.html` | 手绘 B · 拟人鱼绅士（图4） | 1500×1645 |
| `04-handdrawn-C-watercolor-geometry.png` | `design-handdrawn/C-watercolor-geometry/preview.html` | 手绘 C · 水彩几何鱼（图8） | 1500×1646 |
| `05-handdrawn-D-field-guide.png` | `design-handdrawn/D-field-guide/preview.html` | 手绘 D · 手绘鱼种图鉴（图7） | 1500×1645 |

生成方式：本地静态服务 + Edge headless 整页截图 + PIL 去底部空白。
若原 preview.html 内容有更新，重跑 `.m4tmp/render_previews.py` 即可重新生成。
