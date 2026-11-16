/** 与 `algorithm/MathKit.kt` 逐行等价；禁止改名改义（M1 对拍基线）。 */

export function clip01(x: number): number {
  return Math.min(1, Math.max(0, x));
}

export function gauss(x: number, mu: number, sigma: number): number {
  const d = (x - mu) / sigma;
  return Math.exp(-0.5 * d * d);
}

export function trap(x: number, a: number, b: number, c: number, d: number): number {
  if (x <= a || x >= d) return 0;
  if (x < b) return (x - a) / (b - a);
  if (x <= c) return 1;
  return (d - x) / (d - c);
}

export function sig(x: number, mid: number, k: number): number {
  return 1 / (1 + Math.exp(k * (x - mid)));
}

/** JS 风格取余（保留被除数符号），供天文周期取模用 */
export function jsMod(x: number, m: number): number {
  const r = x % m;
  return r < 0 ? r + m : r;
}