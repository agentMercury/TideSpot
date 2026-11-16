/**
 * ic_fish_fieldnotes —— 潮汐手帐的水彩鱼群剪影（无描边，纯色块）。
 * 用于品牌标记、收藏卡与空态；尺寸由 className / size 控制。
 */
export function IcFishFieldnotes({
  size = 28,
  className,
  title,
}: {
  size?: number;
  className?: string;
  title?: string;
}) {
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
      <path d="M24 32 L6 17 L10 32 L6 47 Z" fill="currentColor" opacity="0.72" />
      <path d="M24 32 Q44 12 88 23 Q100 27 88 37 Q44 52 24 32 Z" fill="currentColor" opacity="0.92" />
      <path d="M42 33 Q50 42 58 33" fill="none" stroke="var(--color-card, #FFFDF7)" strokeWidth="2.4" strokeLinecap="round" opacity="0.85" />
      <circle cx="78" cy="29" r="2.2" fill="var(--color-card, #FFFDF7)" opacity="0.9" />
    </svg>
  );
}