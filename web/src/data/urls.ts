/**
 * 与 Android `core/data/remote/TideSpotUrls.kt` 对齐的唯一 URL 构造器。
 * 红线：外部 URL 字面量只允许出现在本文件（与 Android 侧同一条约束）。
 */

export const WEATHER_BASE = 'https://api.open-meteo.com';
export const MARINE_BASE = 'https://marine-api.open-meteo.com';
export const GEOCODING_BASE = 'https://geocoding-api.open-meteo.com';
export const BIG_DATA_CLOUD_BASE = 'https://api.bigdatacloud.net';
export const PHOTON_BASE = 'https://photon.komoot.io';
export const COPERNICUS_AUTH_BASE = 'https://auth.marine.copernicus.eu';
export const WMTS_BASE = 'https://wmts.marine.copernicus.eu';
export const COPERNICUS_REGISTER_URL = 'https://data.marine.copernicus.eu/register';

export const OPENFREEMAP_COLOR_STYLE_URL = 'https://tiles.openfreemap.org/styles/liberty';
export const OPENFREEMAP_PLAIN_STYLE_URL = 'https://tiles.openfreemap.org/styles/positron';
export const OPENFREEMAP_DARK_STYLE_URL = 'https://tiles.openfreemap.org/styles/dark';

export const CARTO_COLOR_STYLE_URL = 'https://basemaps.cartocdn.com/gl/voyager-gl-style/style.json';
export const CARTO_PLAIN_STYLE_URL = 'https://basemaps.cartocdn.com/gl/positron-gl-style/style.json';
export const CARTO_DARK_STYLE_URL = 'https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json';

/** §5.1 hourly 参数（顺序与契约一致，21 项） */
export const WEATHER_HOURLY = [
  'temperature_2m',
  'relative_humidity_2m',
  'dew_point_2m',
  'apparent_temperature',
  'pressure_msl',
  'surface_pressure',
  'cloud_cover',
  'cloud_cover_low',
  'cloud_cover_mid',
  'cloud_cover_high',
  'visibility',
  'precipitation',
  'rain',
  'showers',
  'snowfall',
  'weather_code',
  'wind_speed_10m',
  'wind_direction_10m',
  'wind_gusts_10m',
  'cape',
  'freezing_level_height',
] as const;

/** §5.1 daily 参数 */
export const WEATHER_DAILY = [
  'sunrise',
  'sunset',
  'temperature_2m_max',
  'temperature_2m_min',
  'precipitation_sum',
  'wind_speed_10m_max',
  'wind_gusts_10m_max',
  'weather_code',
] as const;

/** §5.2 hourly 参数（16 项）；潮位字段 = sea_level_height_msl（写成 mssh → 400） */
export const MARINE_HOURLY = [
  'wave_height',
  'wave_period',
  'wave_direction',
  'wave_peak_period',
  'wind_wave_height',
  'wind_wave_period',
  'wind_wave_direction',
  'swell_wave_height',
  'swell_wave_period',
  'swell_wave_direction',
  'secondary_swell_wave_height',
  'tertiary_swell_wave_height',
  'ocean_current_velocity',
  'ocean_current_direction',
  'sea_level_height_msl',
  'sea_surface_temperature',
] as const;

export const OSTIA_DEFAULT_ZOOM = 7;
export const WEB_MERCATOR_TILE_SIZE = 256;
export const WEB_MERCATOR_MAX_LAT = 85.05112878;
export const OSTIA_LAYER =
  'SST_GLO_SST_L4_NRT_OBSERVATIONS_010_001/' +
  'METOFFICE-GLO-SST-L4-NRT-OBS-SST-V2/analysed_sst';

function build(base: string, path: string, query: Array<[string, string]>): string {
  const url = new URL(path, base);
  for (const [key, value] of query) url.searchParams.set(key, value);
  return url.toString();
}

/** §5.1 天气 240h 整窗 */
export function weatherUrl(base: string, lat: number, lon: number): string {
  return build(base, '/v1/forecast', [
    ['latitude', String(lat)],
    ['longitude', String(lon)],
    ['hourly', WEATHER_HOURLY.join(',')],
    ['daily', WEATHER_DAILY.join(',')],
    ['past_days', '7'],
    ['forecast_days', '10'],
    ['timezone', 'auto'],
  ]);
}

/** §5.2 海洋 240h 整窗 */
export function marineUrl(base: string, lat: number, lon: number): string {
  return build(base, '/v1/marine', [
    ['latitude', String(lat)],
    ['longitude', String(lon)],
    ['hourly', MARINE_HOURLY.join(',')],
    ['past_days', '1'],
    ['forecast_days', '10'],
    ['timezone', 'auto'],
  ]);
}

/** §5.9 正地理搜索 */
export function geocodingUrl(
  base: string,
  query: string,
  count: number,
  lang: 'zh' | 'en' = 'zh',
): string {
  const n = Math.min(50, Math.max(1, Math.trunc(count)));
  return build(base, '/v1/search', [
    ['name', query],
    ['count', String(n)],
    ['language', lang],
    ['format', 'json'],
  ]);
}

/** §5.8 RG-A BigDataCloud 逆地理 */
export function bigDataCloudReverseUrl(base: string, lat: number, lon: number): string {
  return build(base, '/data/reverse-geocode-client', [
    ['latitude', String(lat)],
    ['longitude', String(lon)],
    ['localityLanguage', 'zh'],
  ]);
}

/** §5.8 RG-B Photon 逆地理（禁止 lang 参数） */
export function photonReverseUrl(base: string, lat: number, lon: number): string {
  return build(base, '/reverse', [
    ['lat', String(lat)],
    ['lon', String(lon)],
    ['limit', '1'],
    ['layer', 'locality'],
  ]);
}

/** §6.9 Copernicus OIDC token 端点 */
export function copernicusTokenUrl(base: string): string {
  return new URL('/realms/MIS/protocol/openid-connect/token', base).toString();
}

/** OSTIA WMTS GetFeatureInfo（Web Mercator 瓦片号与 256px 像素换算） */
export function ostiaFeatureInfoUrl(base: string, lat: number, lon: number, zoom = OSTIA_DEFAULT_ZOOM): string {
  const n = 1 << zoom;
  const xNorm = (lon + 180) / 360;
  const clampedLat = Math.min(WEB_MERCATOR_MAX_LAT, Math.max(-WEB_MERCATOR_MAX_LAT, lat));
  const rad = (clampedLat * Math.PI) / 180;
  const yNorm = (1 - Math.log(Math.tan(rad) + 1 / Math.cos(rad)) / Math.PI) / 2;

  const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, Math.trunc(v)));
  const col = clamp(xNorm * n, 0, n - 1);
  const row = clamp(yNorm * n, 0, n - 1);
  const i = clamp((xNorm * n - col) * WEB_MERCATOR_TILE_SIZE, 0, WEB_MERCATOR_TILE_SIZE - 1);
  const j = clamp((yNorm * n - row) * WEB_MERCATOR_TILE_SIZE, 0, WEB_MERCATOR_TILE_SIZE - 1);

  return build(base, '/teroWmts', [
    ['SERVICE', 'WMTS'],
    ['REQUEST', 'GetFeatureInfo'],
    ['VERSION', '1.0.0'],
    ['LAYER', OSTIA_LAYER],
    ['STYLE', 'default'],
    ['FORMAT', 'image/png'],
    ['TILEMATRIXSET', 'EPSG:3857'],
    ['TILEMATRIX', String(zoom)],
    ['TILEROW', String(row)],
    ['TILECOL', String(col)],
    ['I', String(i)],
    ['J', String(j)],
    ['INFOFORMAT', 'application/json'],
  ]);
}

/** OSTIA 栅格瓦片模板（MapLibre raster source 用；tileSize 必须 256） */
export function ostiaTileTemplate(date: string): string {
  return (
    `${WMTS_BASE}/teroWmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0` +
    `&LAYER=${encodeURIComponent(OSTIA_LAYER)}&STYLE=default&FORMAT=image/png` +
    `&TILEMATRIXSET=EPSG:3857&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&TIME=${date}`
  );
}