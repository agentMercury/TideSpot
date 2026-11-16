import { describe, expect, it } from 'vitest';
import {
  BREAKPOINT_DESKTOP,
  BREAKPOINT_TABLET,
  detectEnvironment,
  kindForWidth,
  normalizeLayoutMode,
} from './detect';

describe('kindForWidth', () => {
  const cases: Array<[number, string]> = [
    [0, 'mobile'],
    [360, 'mobile'],
    [767, 'mobile'],
    [768, 'tablet'],
    [1024, 'tablet'],
    [1199, 'tablet'],
    [1200, 'desktop'],
    [1440, 'desktop'],
    [1920, 'desktop'],
  ];

  it.each(cases)('%i px -> %s', (width, expected) => {
    expect(kindForWidth(width)).toBe(expected);
  });

  it('断点常量与判定一致', () => {
    expect(kindForWidth(BREAKPOINT_TABLET - 1)).toBe('mobile');
    expect(kindForWidth(BREAKPOINT_TABLET)).toBe('tablet');
    expect(kindForWidth(BREAKPOINT_DESKTOP - 1)).toBe('tablet');
    expect(kindForWidth(BREAKPOINT_DESKTOP)).toBe('desktop');
  });
});

describe('detectEnvironment', () => {
  it('auto：按宽度判定，且标记 source=auto', () => {
    const env = detectEnvironment({ width: 1440, hasHover: true, pointerFine: true });
    expect(env).toEqual({ kind: 'desktop', input: 'mouse', width: 1440, source: 'auto' });
  });

  it('auto：手机宽度 + 触屏', () => {
    const env = detectEnvironment({ width: 390, hasHover: false, pointerFine: false });
    expect(env.kind).toBe('mobile');
    expect(env.input).toBe('touch');
    expect(env.source).toBe('auto');
  });

  it('手动 mobile 覆盖大屏', () => {
    const env = detectEnvironment({ width: 1920, hasHover: true, pointerFine: true, mode: 'mobile' });
    expect(env.kind).toBe('mobile');
    expect(env.source).toBe('manual');
  });

  it('手动 desktop 覆盖小屏', () => {
    const env = detectEnvironment({ width: 360, hasHover: false, pointerFine: false, mode: 'desktop' });
    expect(env.kind).toBe('desktop');
    expect(env.source).toBe('manual');
  });

  it('hover 但 pointer 非 fine（触屏笔记本）判为 touch', () => {
    const env = detectEnvironment({ width: 1440, hasHover: true, pointerFine: false });
    expect(env.input).toBe('touch');
  });

  it('无 hover（纯触屏）判为 touch', () => {
    const env = detectEnvironment({ width: 1440, hasHover: false, pointerFine: true });
    expect(env.input).toBe('touch');
  });
});

describe('normalizeLayoutMode', () => {
  it('合法值原样返回', () => {
    expect(normalizeLayoutMode('mobile')).toBe('mobile');
    expect(normalizeLayoutMode('desktop')).toBe('desktop');
    expect(normalizeLayoutMode('auto')).toBe('auto');
  });

  it('脏值回退 auto', () => {
    expect(normalizeLayoutMode(null)).toBe('auto');
    expect(normalizeLayoutMode(undefined)).toBe('auto');
    expect(normalizeLayoutMode('')).toBe('auto');
    expect(normalizeLayoutMode('tablet')).toBe('auto');
    expect(normalizeLayoutMode('DESKTOP')).toBe('auto');
  });
});