#!/usr/bin/env node
/**
 * 性能预算门禁：按 gzip 后的体积卡住首屏与地图 chunk。
 * 预算来自 WEB_PROJECT_PLAN §8.3（P1：首屏 ≤350KB gzip，地图懒加载）。
 *
 * 用法：npm run check:budget（需先 build）
 */
import { readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { gzipSync } from 'node:zlib';
import { webRoot } from './lib/strings.mjs';

const ASSETS = path.join(webRoot, 'dist', 'assets');

const RULES = [
  { pattern: /^index-.*\.js$/, limit: 350 * 1024, label: '主包 JS' },
  { pattern: /^MapPage-.*\.js$/, limit: 400 * 1024, label: '地图 chunk' },
  { pattern: /\.css$/, limit: 30 * 1024, label: '单文件 CSS' },
];

const KB = (n) => `${(n / 1024).toFixed(1)} KB`;

async function main() {
  let files;
  try {
    files = await readdir(ASSETS);
  } catch {
    throw new Error('dist/assets 不存在：先运行 npm run build');
  }

  const rows = [];
  for (const name of files) {
    if (name.endsWith('.map')) continue;
    const raw = await readFile(path.join(ASSETS, name));
    const gz = gzipSync(raw).length;
    const rule = RULES.find((r) => r.pattern.test(name));
    rows.push({ name, gz, limit: rule?.limit ?? null, label: rule?.label ?? '未设预算' });
  }

  const total = rows.reduce((acc, r) => acc + r.gz, 0);
  const fails = rows.filter((r) => r.limit != null && r.gz > r.limit);

  for (const r of rows.sort((a, b) => b.gz - a.gz)) {
    const mark = r.limit == null ? 'INFO' : r.gz > r.limit ? 'FAIL' : 'OK  ';
    console.log(`${mark} ${KB(r.gz).padStart(9)}  ${r.name}${r.limit ? `（预算 ${KB(r.limit)}，${r.label}）` : ''}`);
  }

  console.log(`\n[check:budget] 总 gzip ${KB(total)}，${rows.length} 个文件，${fails.length} 项超预算`);
  if (fails.length > 0) {
    console.error('[check:budget] FAIL：存在超出预算的产物');
    process.exit(1);
  }
  console.log('[check:budget] PASS');
}

main().catch((err) => {
  console.error(`[check:budget] FAIL: ${err.message}`);
  process.exit(1);
});