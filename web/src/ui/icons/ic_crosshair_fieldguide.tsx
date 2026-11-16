/**
 * ic_crosshair_fieldguide —— 地图准星的四向刻度 + 虚线环（设计稿贴纸准星）。
 */
export function IcCrosshairFieldguide({
  size = 116,
  className,
  title,
}: {
  size?: number;
  className?: string;
  title?: string;
}) {
  return (
    <svg
      viewBox="0 0 60 60"
      width={size}
      height={size}
      className={className}
      role={title ? 'img' : undefined}
      aria-hidden={title ? undefined : true}
      focusable="false"
    >
      {title && <title>{title}</title>}
      <g stroke="currentColor" strokeWidth="2.4" strokeLinecap="round">
        <path d="M30 2 L30 13 M30 47 L30 58 M2 30 L13 30 M47 30 L58 30" />
      </g>
      <circle
        cx="30"
        cy="30"
        r="19"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeDasharray="4 5"
        opacity="0.9"
      />
    </svg>
  );
}