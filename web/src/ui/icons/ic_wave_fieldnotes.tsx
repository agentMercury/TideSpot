/**
 * ic_wave_fieldnotes —— 潮汐手帐的极简波浪线（用于水体贴纸）。
 */
export function IcWaveFieldnotes({
  size = 24,
  className,
  title,
}: {
  size?: number;
  className?: string;
  title?: string;
}) {
  return (
    <svg
      viewBox="0 0 24 24"
      width={size}
      height={size}
      className={className}
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      focusable="false"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    >
      {title && <title>{title}</title>}
      <path d="M2 10 q5 -6 10 0 t10 0" />
      <path d="M2 16 q5 -6 10 0 t10 0" opacity="0.8" />
    </svg>
  );
}