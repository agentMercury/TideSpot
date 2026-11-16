import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));

/** web/scripts/lib -> web */
export const webRoot = path.resolve(here, '..', '..');
/** 仓库根 */
export const repoRoot = path.resolve(webRoot, '..');
/** Android 工程（只读） */
export const androidRoot = path.join(repoRoot, 'tidespot-android');

/** 文案来源：与 Android 模块一一对应 */
export const MODULES = ['app', 'core/ui', 'feature/map', 'feature/detail', 'feature/favorites', 'feature/settings'];

const STRING_RE = /<string\s+name="([^"]+)"[^>]*>([\s\S]*?)<\/string>/g;

const ENTITIES = {
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&apos;': "'",
};

export function decodeAndroidString(raw) {
  let out = raw;
  for (const [entity, char] of Object.entries(ENTITIES)) {
    out = out.split(entity).join(char);
  }
  return out.replace(/\\'/g, "'").replace(/\\"/g, '"').trim();
}

export async function readStringsFile(file) {
  const xml = await readFile(file, 'utf8');
  const entries = {};
  let match;
  while ((match = STRING_RE.exec(xml)) !== null) {
    const [, name, value] = match;
    if (name in entries) throw new Error(`${file}: 重复 key "${name}"`);
    entries[name] = decodeAndroidString(value);
  }
  return entries;
}

export function stringsPath(moduleName, locale) {
  const dir = locale === 'zh' ? 'values' : 'values-en';
  return path.join(androidRoot, moduleName, 'src', 'main', 'res', dir, 'strings.xml');
}

export async function collectLocale(locale) {
  if (locale !== 'zh' && locale !== 'en') throw new Error(`unknown locale: ${locale}`);
  const merged = {};
  const owners = new Map();

  for (const moduleName of MODULES) {
    const file = stringsPath(moduleName, locale);
    if (!existsSync(file)) continue;
    const entries = await readStringsFile(file);
    for (const [key, value] of Object.entries(entries)) {
      if (key in merged) {
        throw new Error(
          `key 冲突："${key}" 同时出现在 ${owners.get(key)} 与 ${moduleName}（web 使用扁平 key，需先改名）`,
        );
      }
      merged[key] = value;
      owners.set(key, moduleName);
    }
  }
  return merged;
}

export async function collectAll() {
  return { zh: await collectLocale('zh'), en: await collectLocale('en') };
}

export function diffKeys(expected, actual) {
  const expectedKeys = new Set(Object.keys(expected));
  const actualKeys = new Set(Object.keys(actual));
  return {
    missing: [...expectedKeys].filter((k) => !actualKeys.has(k)).sort(),
    extra: [...actualKeys].filter((k) => !expectedKeys.has(k)).sort(),
  };
}