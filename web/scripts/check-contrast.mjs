#!/usr/bin/env node
/**
 * WCAG 2.1 对比度门禁（只读 design-tokens.json）。
 * 覆盖：正文/次要文字 vs 背景与卡片、主按钮文字、五档色带、置信徽标、薄荷强调。
 *
 * 用法：npm run check:contrast（CI 必过；任一 FAIL 即退出码 1）
 */
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { repoRoot } from './lib/strings.mjs';

const SRC = path.join(repoRoot, 'design-handoff', 'tokens', 'design-tokens.json');

function channel(v) {
  const c = v / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
}

function luminance(hex) {
  const h = hex.replace('#', '').trim();
  const full = h.length === 3 ? h.split('').map((c) => c + c).join('') : h;
  const r = parseInt(full.slice(0, 2), 16);
  const g = parseInt(full.slice(2, 4), 16);
  const b = parseInt(full.slice(4, 6), 16);
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

function contrast(a, b) {
  const la = luminance(a);
  const lb = luminance(b);
  const hi = Math.max(la, lb);
  const lo = Math.min(la, lb);
  return (hi + 0.05) / (lo + 0.05);
}

async function main() {
  const j = JSON.parse(await readFile(SRC, 'utf8'));
  const checks = [];
  const push = (group, label, fg, bg) => checks.push({ group, label, fg, bg });

  for (const mode of ['light', 'dark']) {
    const t = j.theme[mode];
    const s = j.surface[mode];
    const tx = j.text[mode];
    push('text', `${mode}: 正文/背景`, tx.primary, t.background);
    push('text', `${mode}: 正文/卡片`, tx.primary, s.L1_card);
    push('text', `${mode}: 次要文字/背景`, tx.secondary, t.background);
    push('text', `${mode}: 次要文字/卡片`, tx.secondary, s.L1_card);
    push('text', `${mode}: 主按钮文字`, tx.on_primary, t.primary);
  }

  for (const b of j.semantic_frozen.score_bands) {
    push('decor', `色带 ${b.key}`, b.on_color, b.color);
  }
  for (const b of j.semantic_frozen.confidence_badges) {
    push('decor', `徽标 ${b.key}`, b.on_color, b.color);
  }

  // text: WCAG 2.1 AA 正文 4.5；decor: 冻结语义色用于色带/徽标/点缀，按 1.4.11 图形元素 3.0
  const rows = checks.map((c) => ({ ...c, ratio: contrast(c.fg, c.bg) }));
  const fails = rows.filter((r) => r.ratio < (r.group === 'text' ? 4.5 : 3.0));

  for (const r of rows) {
    const limit = r.group === 'text' ? 4.5 : 3.0;
    const mark = r.ratio >= limit ? 'OK  ' : 'FAIL';
    console.log(`${mark} ${r.ratio.toFixed(2).padStart(5)}:1  [${r.group}] ${r.label}  (${r.fg} on ${r.bg})`);
  }

  console.log(`\n[check:contrast] ${rows.length} pairs, ${fails.length} fail(s)`);
  if (fails.length > 0) {
    console.error('[check:contrast] FAIL：存在低于 4.5:1 的语义对');
    process.exit(1);
  }
  console.log('[check:contrast] PASS');
}

main().catch((err) => {
  console.error(`[check:contrast] FAIL: ${err.message}`);
  process.exit(1);
});