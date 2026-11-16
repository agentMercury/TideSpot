/**
 * 地名搜索 —— 对齐 Android `GeocoderApi`（Open-Meteo Geocoding，CORS `*`）。
 * 只返回候选，不移动地图；选点语义仍由调用方决定（准星是唯一真源，F-1.3）。
 */
import { GEOCODING_BASE, geocodingUrl } from './urls';

export interface PlaceCandidate {
  id: number;
  /** 地名本体（如「青岛」） */
  name: string;
  /** 展示用全名：name · admin1 · country（去重） */
  label: string;
  lat: number;
  lon: number;
  country?: string;
  admin1?: string;
  /** 结果偏远时前端可提示「仅地名，非钓点」 */
  population?: number;
}

interface RawResult {
  id?: number;
  name?: string;
  latitude?: number;
  longitude?: number;
  country?: string;
  admin1?: string;
  population?: number;
}

function toCandidate(raw: RawResult, fallbackName: string, index: number): PlaceCandidate | null {
  if (typeof raw.latitude !== 'number' || typeof raw.longitude !== 'number') return null;
  const name = raw.name?.trim() || fallbackName;
  const parts: string[] = [name];
  for (const part of [raw.admin1, raw.country]) {
    const v = part?.trim();
    if (v && !parts.includes(v)) parts.push(v);
  }
  return {
    id: raw.id ?? index,
    name,
    label: parts.join(' · '),
    lat: raw.latitude,
    lon: raw.longitude,
    country: raw.country,
    admin1: raw.admin1,
    population: raw.population,
  };
}

export async function searchPlaces(
  query: string,
  lang: 'zh' | 'en' = 'zh',
  signal?: AbortSignal,
): Promise<PlaceCandidate[]> {
  const q = query.trim();
  if (q.length < 2) return [];
  const res = await fetch(geocodingUrl(GEOCODING_BASE, q, 8, lang), { signal });
  if (!res.ok) throw new Error(`geocoding HTTP ${res.status}`);
  const body = (await res.json()) as { results?: RawResult[] };
  const list = Array.isArray(body.results) ? body.results : [];
  return list
    .map((raw, i) => toCandidate(raw, q, i))
    .filter((v): v is PlaceCandidate => v != null)
    // 人口多的更可能是用户要找的地方；无该字段的保持原序
    .sort((a, b) => (b.population ?? 0) - (a.population ?? 0));
}