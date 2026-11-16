#!/usr/bin/env node
/**
 * 只读 Android 的 strings.xml，生成 web 的 zh/en 语言包。
 * 单一真相源在 Android：web 不得手改 key，只能通过本脚本重生成。
 *
 * 用法：npm run gen:i18n
 */
import { writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { collectAll, webRoot } from './lib/strings.mjs';

async function main() {
  const { zh, en } = await collectAll();
  const outDir = path.join(webRoot, 'src', 'i18n');

  await mkdir(outDir, { recursive: true });
  await writeFile(path.join(outDir, 'zh.json'), JSON.stringify(zh, null, 2) + '\n', 'utf8');
  await writeFile(path.join(outDir, 'en.json'), JSON.stringify(en, null, 2) + '\n', 'utf8');

  console.log(`[gen-i18n] zh=${Object.keys(zh).length} en=${Object.keys(en).length} -> src/i18n/`);
}

main().catch((err) => {
  console.error(`[gen-i18n] FAIL: ${err.message}`);
  process.exit(1);
});