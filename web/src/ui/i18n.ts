/**
 * 极简 i18n：文案来自 Android strings.xml（src/i18n/*.json，生成物）。
 * - key 缺失时回退 key 本身，便于发现漏译；
 * - 支持 Android 风格 %1$s / %1$d 占位；
 * - 语言是**全局单例 + 订阅**（G-06）：切换后所有页面同时重渲染，
 *   不能再是每个组件各自 useState（那样只有设置页会变语言）。
 */
import { useEffect, useState } from 'react';
import zh from '../i18n/zh.json';
import en from '../i18n/en.json';

export type Lang = 'zh' | 'en';
type Dict = Record<string, string>;
type Listener = () => void;

const DICTS: Record<Lang, Dict> = { zh: zh as Dict, en: en as Dict };
const STORAGE_KEY = 'tidespot.lang';
const listeners = new Set<Listener>();

function readStored(): Lang {
  try {
    return window.localStorage.getItem(STORAGE_KEY) === 'en' ? 'en' : 'zh';
  } catch {
    return 'zh';
  }
}

let current: Lang = readStored();

function syncDocumentLang(lang: Lang): void {
  if (typeof document === 'undefined') return;
  document.documentElement.lang = lang === 'en' ? 'en' : 'zh-CN';
  document.documentElement.setAttribute('data-lang', lang);
}

syncDocumentLang(current);

export function getLang(): Lang {
  return current;
}

export function setLang(lang: Lang): void {
  if (lang === current) return;
  current = lang;
  try {
    window.localStorage.setItem(STORAGE_KEY, lang);
  } catch {
    // 忽略（隐私模式）
  }
  syncDocumentLang(lang);
  for (const listener of listeners) listener();
}

export function t(key: string, lang: Lang = current): string {
  return DICTS[lang][key] ?? key;
}

export function format(key: string, args: Array<string | number>, lang: Lang = current): string {
  let out = t(key, lang);
  args.forEach((arg, i) => {
    const token = new RegExp(`%${i + 1}\\$[sd]`, 'g');
    out = out.replace(token, String(arg));
  });
  return out;
}

/** [当前语言, 切换函数]；所有调用方共享同一份状态（订阅式）。 */
export function useLang(): [Lang, (lang: Lang) => void] {
  const [lang, setLocal] = useState<Lang>(current);
  useEffect(() => {
    const listener = () => setLocal(current);
    listeners.add(listener);
    // 挂载时对齐一次，避免首帧与全局状态不一致
    if (lang !== current) setLocal(current);
    return () => {
      listeners.delete(listener);
    };
  }, [lang]);
  return [lang, setLang];
}