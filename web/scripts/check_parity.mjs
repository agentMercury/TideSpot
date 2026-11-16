#!/usr/bin/env node
/**
 * 双端文案一致性门禁：
 *   1) web 的 zh/en key 集合必须完全一致；
 *   2) web 的 key 集合必须与 Android strings.xml 完全一致（单一真相源）。
 *
 * 用法：npm run check:parity（CI 必过）
 */
import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { collectAll, diffKeys, webRoot } from './lib/strings.mjs';

async function loadJson(name) {
  const file = path.join(webRoot, 'src', 'i18n', name);
  if (!existsSync(file)) {
    throw new Error(`缺少 ${file}；先运行 npm run gen:i18n`);
  }
  return JSON.parse(await readFile(file, 'utf8'));
}

function report(label, expected, actual) {
  const { missing, extra } = diffKeys(expected, actual);
  if (missing.length === 0 && extra.length === 0) {
    console.log(`[check:parity] ${label}: OK (${Object.keys(expected).length})`);
    return true;
  }
  console.error(`[check:parity] ${label}: FAIL`);
  if (missing.length) console.error(`  缺少 ${missing.length}: ${missing.slice(0, 20).join(', ')}${missing.length > 20 ? ' …' : ''}`);
  if (extra.length) console.error(`  多余 ${extra.length}: ${extra.slice(0, 20).join(', ')}${extra.length > 20 ? ' …' : ''}`);
  return false;
}

async function main() {
  const { zh: sourceZh, en: sourceEn } = await collectAll();
  const webZh = await loadJson('zh.json');
  const webEn = await loadJson('en.json');

  console.log(
    `[check:parity] source zh=${Object.keys(sourceZh).length} en=${Object.keys(sourceEn).length} | ` +
      `web zh=${Object.keys(webZh).length} en=${Object.keys(webEn).length}`,
  );

  const okZh = report('zh vs Android', sourceZh, webZh);
  const okEn = report('en vs Android', sourceEn, webEn);
  const okBi = report('zh vs en', webZh, webEn);

  if (!okZh || !okEn || !okBi) {
    console.error('[check:parity] FAIL：文案 key 不一致，禁止合并');
    process.exit(1);
  }
  console.log('[check:parity] PASS');
}

main().catch((err) => {
  console.error(`[check:parity] FAIL: ${err.message}`);
  process.exit(1);
});