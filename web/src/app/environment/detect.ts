/**
 * 环境自适应（ADP）判定 —— 纯函数，不读 window，便于单测。
 *
 * 优先级：手动选择 > 视口宽度 > 交互能力（hover / pointer）。
 * UA 不参与判定：iPad 伪装、PC 微信内置浏览器、折叠屏都会误判。
 */

export type EnvKind = 'mobile' | 'tablet' | 'desktop';
export type EnvInput = 'touch' | 'mouse';
export type LayoutMode = 'auto' | 'mobile' | 'desktop';

export const BREAKPOINT_TABLET = 768;
export const BREAKPOINT_DESKTOP = 1200;

export const LAYOUT_MODE_KEY = 'tidespot.layoutMode';

export interface Environment {
  /** 布局档位 */
  kind: EnvKind;
  /** 交互能力：决定 hover / 右键 / 快捷键是否启用 */
  input: EnvInput;
  /** 视口 CSS 像素宽 */
  width: number;
  /** 档位来源：自动断点 or 用户手动指定 */
  source: 'auto' | 'manual';
}

export interface EnvironmentProbe {
  width: number;
  hasHover: boolean;
  pointerFine: boolean;
  mode?: LayoutMode;
}

export function kindForWidth(width: number): EnvKind {
  if (width >= BREAKPOINT_DESKTOP) return 'desktop';
  if (width >= BREAKPOINT_TABLET) return 'tablet';
  return 'mobile';
}

export function detectEnvironment(probe: EnvironmentProbe): Environment {
  const input: EnvInput = probe.hasHover && probe.pointerFine ? 'mouse' : 'touch';
  const mode = probe.mode ?? 'auto';

  if (mode === 'mobile') {
    return { kind: 'mobile', input, width: probe.width, source: 'manual' };
  }
  if (mode === 'desktop') {
    return { kind: 'desktop', input, width: probe.width, source: 'manual' };
  }
  return { kind: kindForWidth(probe.width), input, width: probe.width, source: 'auto' };
}

/** localStorage 里的脏值一律回退 auto，避免用户被锁死在错误档位 */
export function normalizeLayoutMode(raw: string | null | undefined): LayoutMode {
  return raw === 'mobile' || raw === 'desktop' ? raw : 'auto';
}