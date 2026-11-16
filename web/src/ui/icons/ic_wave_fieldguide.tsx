/**
 * ic_wave_fieldguide —— 手绘波纹 + 小鱼，用于水体胶囊（海洋 / 内陆）。
 */
export function IcWaveFieldguide({
  size = 34,
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
      viewBox="0 0 80 48"
      width={size}
      height={(size * 48) / 80}
      className={className}
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      focusable="false"
    >
      {title && <title>{title}</title>}
      <g fill="none" stroke="currentColor" strokeWidth="2.8" strokeLinecap="round">
        <path d="M2 30 Q12 21 22 30 Q32 39 42 30 Q52 21 62 30 Q70 37 78 30" />
        <path d="M2 41 Q12 32 22 41 Q32 50 42 41 Q52 32 62 41 Q70 48 78 41" opacity="0.85" />
      </g>
      <path d="M50 24 Q56 9 62 24 Z" fill="currentColor" stroke={ink} strokeWidth="1.8" strokeLinejoin="round" />
      <path
        d="M68 20 q5 -4 10 0 q-5 4 -10 0 M78 20 q4 -3 8 0"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.8"
        strokeLinecap="round"
      />
    </svg>
  );
}