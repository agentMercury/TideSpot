/**
 * 潮汐手帐的页面装饰：鱼群水印 + 海底波带。
 * 纯展示，颜色全部走 --fn-* / --color-* 变量；只在新皮肤下显示（CSS 控制）。
 */
export function FieldnotesWatermark() {
  return (
    <div className="fn-watermark" aria-hidden="true">
      <svg viewBox="0 0 260 320" width="260" height="320" focusable="false">
        <circle cx="192" cy="78" r="92" fill="var(--fn-water, #B7DAEE)" opacity="0.56" />
        <g fill="var(--fn-water2, #8AC1E3)" opacity="0.34">
          <g transform="translate(124 158) scale(1.05) rotate(-8)">
            <path d="M0 8 L-15 18 L-11 8 L-15 -2 Z" />
            <ellipse cx="13" cy="8" rx="18" ry="7.6" />
          </g>
          <g transform="translate(160 196) scale(0.9) rotate(4)">
            <path d="M0 8 L-15 18 L-11 8 L-15 -2 Z" />
            <ellipse cx="13" cy="8" rx="18" ry="7.6" />
          </g>
          <g transform="translate(112 232) scale(0.82) rotate(-12)">
            <path d="M0 8 L-15 18 L-11 8 L-15 -2 Z" />
            <ellipse cx="13" cy="8" rx="18" ry="7.6" />
          </g>
          <g transform="translate(176 260) scale(0.62) rotate(9)">
            <path d="M0 8 L-15 18 L-11 8 L-15 -2 Z" />
            <ellipse cx="13" cy="8" rx="18" ry="7.6" />
          </g>
        </g>
      </svg>
    </div>
  );
}

export function FieldnotesSeabed() {
  return (
    <div className="fn-seabed" aria-hidden="true">
      <svg viewBox="0 0 390 60" preserveAspectRatio="none" focusable="false">
        <path
          d="M0 22 C 44 10, 88 34, 140 20 C 192 8, 234 32, 286 18 C 336 6, 362 26, 390 14 L390 60 L0 60 Z"
          fill="var(--fn-water, #B7DAEE)"
          opacity="0.9"
        />
        <path
          d="M0 38 C 46 26, 92 50, 144 36 C 196 24, 240 46, 292 34 C 342 24, 366 40, 390 32 L390 60 L0 60 Z"
          fill="var(--fn-water2, #8AC1E3)"
          opacity="0.45"
        />
      </svg>
    </div>
  );
}