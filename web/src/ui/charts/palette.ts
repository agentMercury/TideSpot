/**
 * 图表取色：D 皮肤使用 CSS 变量（Canvas 内解析），A 皮肤保持原有固定色。
 * 只影响折线/柱状图的线条颜色；五档色带仍是冻结语义色。
 */
export function chartColor(variable: string, fallback: string): string {
  if (typeof document === 'undefined') return fallback;
  const skin = document.documentElement.dataset.skin;
  return skin === 'd-fieldguide' || skin === 'e-tidejournal' ? `var(${variable})` : fallback;
}