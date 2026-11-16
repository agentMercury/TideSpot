/**
 * 极简 hash 路由（GitHub Pages 无 SPA 回退，hash 是唯一零配置方案）。
 * 路由形如：#/map 、#/favorites 、#/settings 、#/spot/12
 * Tab 进路由是为了深链/刷新保持（与 Android S01/S08/S09 对齐）。
 */
import { useEffect, useState } from 'react';

export type TabRoute = 'map' | 'favorites' | 'settings';

export type Route = { name: 'tab'; tab: TabRoute } | { name: 'spot'; id: number };

export type NavTarget = '#/' | '#/map' | '#/favorites' | '#/settings' | `#/spot/${number}`;

const TABS: readonly string[] = ['map', 'favorites', 'settings'];

export function parseHash(hash: string): Route {
  const raw = hash.replace(/^#\/?/, '');
  const m = /^spot\/(\d+)$/.exec(raw);
  if (m) return { name: 'spot', id: Number(m[1]) };
  if (TABS.includes(raw)) return { name: 'tab', tab: raw as TabRoute };
  return { name: 'tab', tab: 'map' };
}

export function navigate(target: NavTarget): void {
  if (window.location.hash === target) return;
  window.location.hash = target;
}

export function useHashRoute(): Route {
  const [route, setRoute] = useState<Route>(() => parseHash(window.location.hash));
  useEffect(() => {
    const onChange = () => setRoute(parseHash(window.location.hash));
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);
  return route;
}