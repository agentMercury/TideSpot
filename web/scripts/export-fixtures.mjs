#!/usr/bin/env node
/**
 * 从 Android 的 Kotlin 单测中「启发式」提取断言候选，供 M1 建立对拍 fixture。
 * 只读：不修改 Android 任何文件。
 *
 * 输出：web/test/fixtures/extracted-kotlin.json（候选，不是可直接断言的真相）
 *
 * 用法：npm run export:fixtures
 */
import { readdir, readFile, writeFile, mkdir } from 'node:fs/promises';
import path from 'node:path';
import { androidRoot, webRoot } from './lib/strings.mjs';

const TARGETS = ['core/domain', 'core/data'];

// assertEquals(<expected literal>, <actual expression...>)
const ASSERT_RE = /assertEquals\(\s*(-?\d+(?:\.\d+)?|"[^"]{0,120}")\s*,\s*([^,\n)]+)/g;

async function walkKotlin(dir, out) {
  let entries;
  try {
    entries = await readdir(dir, { withFileTypes: true });
  } catch {
    return;
  }
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (entry.name === 'build' || entry.name === '.gradle') continue;
      await walkKotlin(full, out);
    } else if (entry.name.endsWith('.kt')) {
      out.push(full);
    }
  }
}

async function main() {
  const files = [];
  for (const target of TARGETS) {
    await walkKotlin(path.join(androidRoot, target, 'src', 'test'), files);
  }

  const cases = [];
  for (const file of files) {
    const text = await readFile(file, 'utf8');
    let match;
    while ((match = ASSERT_RE.exec(text)) !== null) {
      const [whole, expectedLiteral, actualExpression] = match;
      const before = text.slice(0, match.index);
      const line = before.split('\n').length;
      cases.push({
        module: path.relative(androidRoot, file).replace(/\\/g, '/'),
        line,
        expected: expectedLiteral.startsWith('"') ? expectedLiteral.slice(1, -1) : Number(expectedLiteral),
        actualExpression: actualExpression.trim(),
        raw: whole.trim(),
      });
    }
  }

  const outDir = path.join(webRoot, 'test', 'fixtures');
  await mkdir(outDir, { recursive: true });
  const payload = {
    generatedAt: new Date().toISOString(),
    note: '启发式提取，仅作 M1 对拍候选；经人工确认后才能写入 domain.json 作为断言',
    filesScanned: files.length,
    count: cases.length,
    cases,
  };
  await writeFile(path.join(outDir, 'extracted-kotlin.json'), JSON.stringify(payload, null, 2) + '\n', 'utf8');

  console.log(`[export-fixtures] files=${files.length} cases=${cases.length} -> test/fixtures/extracted-kotlin.json`);
}

main().catch((err) => {
  console.error(`[export-fixtures] FAIL: ${err.message}`);
  process.exit(1);
});