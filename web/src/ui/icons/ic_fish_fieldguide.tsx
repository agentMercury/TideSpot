/**
 * ic_fish_fieldguide —— 路亚图鉴贴纸鱼（D 皮肤的重复母题）。
 * 内联 SVG，无外链；描边取 `--fg-line`，本体取 currentColor。
 */
export function IcFishFieldguide({
  size = 28,
  className,
  title,
}: {
  size?: number;
  className?: string;
  title?: string;
}) {
  const ink = 'var(--fg-line, #101820)';
  return (
    <svg
      viewBox="0 0 120 64"
      width={size}
      height={(size * 64) / 120}
      className={className}
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      focusable="false"
    >
      {title && <title>{title}</title>}
      <path
        d="M24 32 Q42 12 86 24 Q98 27 86 36 Q42 52 24 32 Z"
        fill="currentColor"
        stroke={ink}
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path
        d="M24 32 L8 18 L12 32 L8 46 Z"
        fill="currentColor"
        stroke={ink}
        strokeWidth="3"
        strokeLinejoin="round"
      />
      <path
        d="M44 19 L48 10 L54 18 M58 18 L63 9 L68 18 M72 20 L77 12 L81 21"
        fill="none"
        stroke={ink}
        strokeWidth="2.2"
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      <circle cx="62" cy="30" r="2.6" fill={ink} opacity="0.65" />
      <circle cx="72" cy="34" r="2.2" fill={ink} opacity="0.65" />
      <circle cx="80" cy="28" r="2" fill={ink} opacity="0.65" />
      <circle cx="38" cy="27" r="6" fill="var(--color-card, #FFFDF7)" stroke={ink} strokeWidth="2.8" />
      <circle cx="38" cy="27" r="2.4" fill={ink} />
      <path d="M28 33 Q33 38 39 33" fill="none" stroke={ink} strokeWidth="2.4" strokeLinecap="round" />
    </svg>
  );
}