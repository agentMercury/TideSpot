/**
 * 逆地理（§5.8）：Photon 主源，失败回退「6 位坐标」文本。
 * 只发一次请求，节流由调用方控制。
 */
import { PHOTON_BASE, photonReverseUrl } from './urls';
import { DataError } from './errors';

export interface PlaceName {
  displayName: string;
  subLabel: string | null;
  source: 'photon' | 'coordinate';
}

interface PhotonFeature {
  properties?: {
    name?: string;
    city?: string;
    county?: string;
    state?: string;
    country?: string;
  };
}

export async function reverseGeocode(
  lat: number,
  lon: number,
  options: { fetchFn?: typeof fetch; timeoutMs?: number; base?: string } = {},
): Promise<PlaceName> {
  const fetchFn = options.fetchFn ?? fetch;
  const base = options.base ?? PHOTON_BASE;
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), options.timeoutMs ?? 6000);

  try {
    const res = await fetchFn(photonReverseUrl(base, lat, lon), {
      signal: controller.signal,
      redirect: 'follow',
    });
    if (res.status !== 200) throw new DataError('BadRequest', `Photon ${res.status}`, { httpStatus: res.status });
    const json = (await res.json()) as { features?: PhotonFeature[] };
    const props = json.features?.[0]?.properties;
    const name = props?.name ?? props?.city ?? props?.county;
    if (!name) return coordinateFallback(lat, lon);
    const sub = props?.state ?? props?.country ?? null;
    return { displayName: name, subLabel: sub, source: 'photon' };
  } catch {
    return coordinateFallback(lat, lon);
  } finally {
    clearTimeout(timer);
  }
}

export function coordinateFallback(lat: number, lon: number): PlaceName {
  return { displayName: `${lat.toFixed(6)}, ${lon.toFixed(6)}`, subLabel: null, source: 'coordinate' };
}