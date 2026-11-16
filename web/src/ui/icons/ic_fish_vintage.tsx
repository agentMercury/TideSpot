/**
 * ic_fish_vintage —— A·复古户外的版画套印鱼。
 * 限色块 + 粗描边 + 鳃线 + 芥末黄鳍，用于品牌章、评分盖章、收藏卡与空态。
 */
export function IcFishVintage({
  size = 28,
  className,
  title,
}: {
  size?: number;
  className?: string;
  title?: string;
}) {
  const ink = 'var(--vt-ink, #16324F)';
  const paper = 'var(--vt-paper, #F2EAD4)';
  const mustard = 'var(--vt-mustard, #D9A734)';
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
        d="M22 32 Q46 10 92 27 Q100 30 92 34 Q46 54 22 32 Z"
        fill="currentColor"
        stroke={ink}
        strokeWidth="2.6"
        strokeLinejoin="round"
      />
      <path
        d="M22 32 L4 16 L10 32 L4 48 Z"
        fill="currentColor"
        stroke={ink}
        strokeWidth="2.6"
        strokeLinejoin="round"
      />
      <path
        d="M50 20 L44 44 M62 19 L56 45 M74 20 L68 42"
        stroke={ink}
        strokeWidth="2"
        opacity="0.7"
        strokeLinecap="round"
      />
      <path d="M60 15 Q66 6 76 14 Z" fill={mustard} stroke={ink} strokeWidth="2" strokeLinejoin="round" />
      <circle cx="36" cy="29" r="4.6" fill={paper} stroke={ink} strokeWidth="2.4" />
      <circle cx="36" cy="29" r="1.9" fill={ink} />
    </svg>
  );
}