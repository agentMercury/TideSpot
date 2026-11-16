import { lazy, Suspense, useEffect, useState } from 'react';
import { useEnvironment } from '../environment/EnvironmentProvider';
import type { LayoutMode } from '../environment/detect';
import { APP_VERSION } from '../container';
import { Button, Card } from '../../ui/primitives/primitives';
import { useThemeMode, type ThemeMode } from '../../ui/theme';
import { format, t, useLang } from '../../ui/i18n';
import { updateSettings, useSettings } from '../../ui/settings';
import { MARINE_BASE, WEATHER_BASE, marineUrl, weatherUrl } from '../../data/urls';
import { getWater, skinMeta, useSkin } from '../../ui/skin';
import { SkinPicker } from '../../ui/skinPicker';
import { IcFishFieldguide, IcFishFieldnotes, IcFishVintage } from '../../ui/icons';
import { FieldnotesWatermark } from '../../ui/fieldnotesMarks';
import { navigate, useHashRoute } from '../router';
import { DetailPage } from '../pages/DetailPage';
import { DesktopSpotLayout } from '../pages/DesktopSpotLayout';
import { FavoritesPage } from '../pages/FavoritesPage';

const MapPage = lazy(() => import('../pages/MapPage').then((m) => ({ default: m.MapPage })));

const TABS = [{ id: 'map' }, { id: 'favorites' }, { id: 'settings' }] as const;

type TabId = (typeof TABS)[number]['id'];

/** 地图/收藏/设置三 tab 的显示名：收藏与设置复用 Android 文案 key，地图为 Web 独有标签 */
function tabLabel(id: TabId, lang: 'zh' | 'en'): string {
  if (id === 'map') return lang === 'en' ? 'Map' : '地图';
  return t(id === 'favorites' ? 'ts_app_favorites' : 'ts_app_settings', lang);
}

const MODES: Array<{ id: LayoutMode; label: string }> = [
  { id: 'auto', label: '自动' },
  { id: 'mobile', label: '手机' },
  { id: 'desktop', label: '桌面' },
];

function ModeSwitch() {
  const { mode, setMode } = useEnvironment();
  return (
    <div className="mode-switch" role="group" aria-label="布局模式">
      {MODES.map((item) => (
        <button
          key={item.id}
          type="button"
          className={mode === item.id ? 'is-active' : ''}
          aria-pressed={mode === item.id}
          onClick={() => setMode(item.id)}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}

/** 用真实评分内核跑一组固定样例（与 test/fixtures/domain.json 的 seaShore 序列一致） */
/** G-09 开源许可与数据署名（与 Android SettingsLogic.LICENSES 同源） */
const LICENSES: Array<{ holder: string; roleKey: string; licenseKey: string }> = [
  { holder: 'Open-Meteo', roleKey: 'ts_license_open_meteo_role', licenseKey: 'ts_license_open_meteo_license' },
  { holder: 'Copernicus Marine', roleKey: 'ts_license_copernicus_role', licenseKey: 'ts_license_copernicus_license' },
  { holder: 'NASA', roleKey: 'ts_license_nasa_role', licenseKey: 'ts_license_nasa_license' },
  { holder: 'NOAA', roleKey: 'ts_license_noaa_role', licenseKey: 'ts_license_noaa_license' },
  { holder: 'USGS', roleKey: 'ts_license_usgs_role', licenseKey: 'ts_license_usgs_license' },
  { holder: 'OpenStreetMap / MapLibre', roleKey: 'ts_license_osm_role', licenseKey: 'ts_license_osm_license' },
  { holder: 'BigDataCloud', roleKey: 'ts_license_bdc_role', licenseKey: 'ts_license_bdc_license' },
  { holder: 'Photon (komoot)', roleKey: 'ts_license_photon_role', licenseKey: 'ts_license_photon_license' },
];

const DISCLAIMER_KEYS = [
  'ts_disclaimer_score',
  'ts_disclaimer_weather',
  'ts_disclaimer_water_quality',
  'ts_disclaimer_wave_tide',
  'ts_disclaimer_tide_correction',
];

/** 网络自检：把「浏览器能否拿到源站」变成界面上可见的事实（不依赖 DevTools） */
function NetworkSelfTest() {
  const [rows, setRows] = useState<Array<{ label: string; url: string; text: string }>>([]);
  const [busy, setBusy] = useState(false);

  const run = async () => {
    setBusy(true);
    setRows([]);
    const targets = [
      { label: 'weather · api.open-meteo.com', url: weatherUrl(WEATHER_BASE, 36.06, 120.38) },
      { label: 'marine · marine-api.open-meteo.com', url: marineUrl(MARINE_BASE, 36.06, 120.38) },
    ];
    const out: Array<{ label: string; url: string; text: string }> = [];
    for (const target of targets) {
      const started = performance.now();
      try {
        const res = await fetch(target.url, { method: 'GET' });
        const body = await res.text();
        out.push({
          label: target.label,
          url: target.url,
          text: `HTTP ${res.status} · ${Math.round(performance.now() - started)}ms · ${body.slice(0, 180)}`,
        });
      } catch (e) {
        out.push({
          label: target.label,
          url: target.url,
          text: `网络错误 · ${Math.round(performance.now() - started)}ms · ${(e as Error).message}`,
        });
      }
      setRows([...out]);
    }
    setBusy(false);
  };

  return (
    <div className="stack">
      <Button variant="primary" size="sm" onClick={run} disabled={busy}>
        {busy ? '自检中…' : '运行网络自检'}
      </Button>
      {rows.map((row) => (
        <div key={row.label} className="stack" style={{ gap: 4 }}>
          <strong style={{ fontSize: 13 }}>{row.label}</strong>
          <p className="ts-hint" style={{ wordBreak: 'break-all', margin: 0 }}>
            {row.text}
          </p>
          <p className="ts-hint" style={{ wordBreak: 'break-all', margin: 0, opacity: 0.7 }}>
            {row.url}
          </p>
        </div>
      ))}
    </div>
  );
}

function SettingsPanel() {
  const { mode, setMode, env } = useEnvironment();
  const [usage, setUsage] = useState<string>('—');
  const [themeMode, setThemeMode, resolved] = useThemeMode();
  const [skin, changeSkin] = useSkin();
  const [lang, changeLang] = useLang();
  const settings = useSettings();

  useEffect(() => {
    void (async () => {
      if (!navigator.storage?.estimate) return;
      const est = await navigator.storage.estimate();
      const used = est.usage ?? 0;
      const quota = est.quota ?? 0;
      setUsage(`${(used / 1024).toFixed(1)} KB / ${(quota / 1024 / 1024).toFixed(0)} MB`);
    })();
  }, []);

  const themes: Array<{ id: ThemeMode; label: string }> = [
    { id: 'auto', label: '跟随系统' },
    { id: 'light', label: '浅色' },
    { id: 'dark', label: '深色' },
  ];

  const sections = [
    { id: 'language', label: t('ts_settings_display', lang) + ' / Language' },
    { id: 'appearance', label: '布局' },
    { id: 'skin', label: t('ts_settings_skin', lang) },
    { id: 'theme', label: t('ts_settings_theme_mode', lang) },
    { id: 'map', label: t('ts_settings_map_source', lang) },
    { id: 'privacy', label: t('ts_settings_privacy', lang) },
    { id: 'licenses', label: t('ts_settings_licenses', lang) },
    { id: 'disclaimer', label: t('ts_disclaimer_title', lang) },
    { id: 'storage', label: '存储' },
    { id: 'diag', label: '诊断 / Diagnostics' },
    { id: 'about', label: t('ts_settings_title', lang) },
  ] as const;
  const [section, setSection] = useState<(typeof sections)[number]['id']>('appearance');
  const isDesktop = env.kind === 'desktop';


  const cards = {
    diag: (
      <Card key="diag" ariaLabel="Diagnostics">
        <strong>网络自检 / Network self-test</strong>
        <p className="ts-hint">
          直接请求两个源站并显示 HTTP 状态、耗时与响应片段。若这里显示「网络错误」，说明浏览器层拿不到源站（扩展拦截 / DNS / 防火墙），与指数算法无关。
        </p>
        <NetworkSelfTest />
      </Card>
    ),
    language: (
      <Card key="language" ariaLabel="Language">
        <strong>语言 / Language</strong>
        <p className="ts-hint">
          切换后所有页面立即生效，并会记住你的选择。
        </p>
        <div className="row-gap">
          <button
            type="button"
            className={'ghost-button' + (lang === 'zh' ? ' is-active' : '')}
            onClick={() => changeLang('zh')}
          >
            中文
          </button>
          <button
            type="button"
            className={'ghost-button' + (lang === 'en' ? ' is-active' : '')}
            onClick={() => changeLang('en')}
          >
            English
          </button>
        </div>
      </Card>
    ),
    map: (
      <Card key="map" ariaLabel={t('ts_settings_map_source', lang)}>
        <strong>{t('ts_settings_map_source', lang)}</strong>
        <p className="ts-hint">{t('ts_settings_map_source_note', lang)}</p>
        <div className="row-gap">
          {(['openfreemap', 'carto'] as const).map((provider) => (
            <button
              key={provider}
              type="button"
              className={'ghost-button' + (settings.mapProvider === provider ? ' is-active' : '')}
              onClick={() => updateSettings({ mapProvider: provider })}
            >
              {provider === 'openfreemap' ? 'OpenFreeMap' : 'CARTO'}
            </button>
          ))}
        </div>
        <p className="ts-hint">
          {format(
            'ts_settings_map_style_desc',
            [t('ts_settings_style_' + settings.mapStyle, lang), settings.mapProvider],
            lang,
          )}
        </p>
      </Card>
    ),
    privacy: (
      <Card key="privacy" ariaLabel={t('ts_settings_privacy', lang)}>
        <strong>{t('ts_settings_privacy', lang)}</strong>
        <ul className="ts-hint">
          <li>
            {t('ts_privacy_geocode_source', lang)} — {t('ts_privacy_geocode_sends', lang)}
          </li>
          <li>
            {t('ts_privacy_weather_source', lang)} — {t('ts_privacy_coords_period', lang)}
          </li>
          <li>
            {t('ts_privacy_marine_source', lang)} — {t('ts_privacy_coords_period', lang)}
          </li>
          <li>
            {t('ts_privacy_rga_source', lang)} — {t('ts_privacy_rg_coords', lang)}
          </li>
          <li>
            {t('ts_privacy_rgb_source', lang)} — {t('ts_privacy_rg_coords', lang)}
          </li>
          <li>
            {t('ts_privacy_wq_source', lang)} — {t('ts_privacy_wq_sends', lang)}
          </li>
        </ul>
      </Card>
    ),
    licenses: (
      <Card key="licenses" ariaLabel={t('ts_settings_licenses', lang)}>
        <strong>{t('ts_settings_licenses', lang)}</strong>
        <ul className="ts-hint">
          {LICENSES.map((item) => (
            <li key={item.roleKey}>
              {item.holder} · {t(item.roleKey, lang)}（{t(item.licenseKey, lang)}）
            </li>
          ))}
        </ul>
      </Card>
    ),
    disclaimer: (
      <Card key="disclaimer" ariaLabel={t('ts_disclaimer_title', lang)}>
        <strong>{t('ts_disclaimer_title', lang)}</strong>
        <ol className="ts-hint">
          {DISCLAIMER_KEYS.map((key) => (
            <li key={key}>{t(key, lang)}</li>
          ))}
        </ol>
      </Card>
    ),
    appearance: (
      <Card key="appearance" ariaLabel="外观">
        <strong>布局模式</strong>
        <p className="ts-hint">当前：{mode}（实际档位 {env.kind}）；「自动」按视口宽度切换。</p>
        <div className="row-gap">
          {MODES.map((m) => (
            <button key={m.id} type="button" className={`ghost-button ${mode === m.id ? 'is-active' : ''}`} onClick={() => setMode(m.id)}>
              {m.label}
            </button>
          ))}
        </div>
</Card>
    ),
    skin: (
      <Card key="skin" ariaLabel="皮肤">
        <strong>皮肤</strong>
        <p className="ts-hint">
          当前：{skinMeta(skin).name}（{skinMeta(skin).nameEn}）· 水体色板 {getWater() === 'inland' ? '内陆' : '海洋'} · {resolved === 'dark' ? '深色' : '浅色'}
        </p>
        <SkinPicker value={skin} onChange={changeSkin} />
        <p className="ts-hint">
          只改变颜色、描边、圆角与字体；水体色板随点位自动切换。
        </p>
      </Card>
    ),
    theme: (
      <Card key="theme" ariaLabel="主题">
        <strong>主题</strong>
        <p className="ts-hint">当前：{themeMode}（解析为 {resolved}）；与 Android follow_system_dark 一致。</p>
        <div className="row-gap">
          {themes.map((item) => (
            <button key={item.id} type="button" className={`ghost-button ${themeMode === item.id ? 'is-active' : ''}`} onClick={() => setThemeMode(item.id)}>
              {item.label}
            </button>
          ))}
        </div>
      </Card>
    ),
    storage: (
      <Card key="storage" ariaLabel="存储">
        <strong>本地存储</strong>
        <p className="ts-hint">IndexedDB（收藏与缓存）+ localStorage（设置）。估算占用：{usage}</p>
        <p className="ts-hint">换域名或清除站点数据会导致本地收藏丢失；本期不提供导出/导入。</p>
      </Card>
    ),
    about: (
      <Card key="about" ariaLabel="版本">
        <strong>版本</strong>
        <p className="ts-hint">TideSpot Web {APP_VERSION}（版本号独立于 Android）</p>
        <p className="ts-hint">
          {lang === 'en'
            ? 'If the page still shows an old build, clear caches below (this also unregisters the Service Worker).'
            : '如果仍显示旧版本，点下面「清除缓存并重载」（会注销 Service Worker 并清空缓存）。'}
        </p>
        <Button
          variant="primary"
          size="sm"
          onClick={() => {
            void (async () => {
              try {
                const keys = await caches.keys();
                await Promise.all(keys.map((k) => caches.delete(k)));
                const regs = await navigator.serviceWorker?.getRegistrations?.();
                await Promise.all((regs ?? []).map((r) => r.unregister()));
              } finally {
                window.location.reload();
              }
            })();
          }}
        >
          {lang === 'en' ? 'Clear cache & reload' : '清除缓存并重载'}
        </Button>
      </Card>
    ),
  };

  return (
    <div className={isDesktop ? 'settings-desk' : 'stack'}>
      {isDesktop && (
        <nav className="settings-desk__nav" aria-label="设置分类">
          {sections.map((s) => (
            <button key={s.id} type="button" className={section === s.id ? 'is-active' : ''} onClick={() => setSection(s.id)}>
              {s.label}
            </button>
          ))}
        </nav>
      )}
      <div className="stack">
        {isDesktop ? cards[section] : Object.values(cards)}
      </div>
    </div>
  );
}

export function AppShell() {
  const [lang] = useLang();
  const [skin] = useSkin();
  const route = useHashRoute();
  const { env } = useEnvironment();

  if (route.name === 'spot') {
    return (
      <>
        <FieldnotesWatermark />
        {env.kind === 'desktop' ? <DesktopSpotLayout spotId={route.id} /> : <DetailPage spotId={route.id} />}
      </>
    );
  }

  const tab: TabId = route.tab;
  const setTab = (id: TabId) => navigate(`#/${id}`);

  const isMobile = env.kind === 'mobile';

  const content =
    tab === 'map' ? (
      <Suspense fallback={<div className="map-loading">地图加载中…</div>}>
        <MapPage />
      </Suspense>
) : tab === 'favorites' ? <FavoritesPage /> : <SettingsPanel />;

  return (
    <div className={`app app--${env.kind}`}>
      <FieldnotesWatermark />
      {isMobile ? (
        <header className="mobile-header">
          <span className="brand">
            <span className="brand__mark" aria-hidden="true">
              <IcFishFieldguide className="brand__fish brand__fish--guide" size={20} />
              <IcFishFieldnotes className="brand__fish brand__fish--notes" size={20} />
              <IcFishVintage className="brand__fish brand__fish--vintage" size={20} />
            </span>
            <span className="brand__text">
              <span className="brand__name">潮点 TideSpot</span>
              <span className="brand__sub">{skinMeta(skin).nameEn}</span>
            </span>
          </span>
          <ModeSwitch />
        </header>
      ) : (
        <header className="topbar">
          <span className="brand">
            <span className="brand__mark" aria-hidden="true">
              <IcFishFieldguide className="brand__fish brand__fish--guide" size={20} />
              <IcFishFieldnotes className="brand__fish brand__fish--notes" size={20} />
              <IcFishVintage className="brand__fish brand__fish--vintage" size={20} />
            </span>
            <span className="brand__text">
              <span className="brand__name">潮点 TideSpot</span>
              <span className="brand__sub">{skinMeta(skin).nameEn}</span>
            </span>
          </span>
          <nav className="topbar__nav" aria-label="主导航">
            {TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                className={tab === item.id ? 'is-active' : ''}
                onClick={() => setTab(item.id)}
              >
                {tabLabel(item.id, lang)}
              </button>
            ))}
          </nav>
          <ModeSwitch />
        </header>
      )}

      <div className="app__body">
        {env.kind === 'tablet' && (
          <nav className="sidebar sidebar--collapsed" aria-label="侧栏">
            {TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                className={tab === item.id ? 'is-active' : ''}
                onClick={() => setTab(item.id)}
              >
                {tabLabel(item.id, lang)}
              </button>
            ))}
          </nav>
        )}

        {env.kind === 'desktop' && (
          <nav className="sidebar" aria-label="侧栏">
            <div className="sidebar__title">导航</div>
            {TABS.map((item) => (
              <button
                key={item.id}
                type="button"
                className={tab === item.id ? 'is-active' : ''}
                onClick={() => setTab(item.id)}
              >
                {tabLabel(item.id, lang)}
              </button>
            ))}
            <div className="sidebar__title">说明</div>
            <p className="hint">收藏与缓存存放于浏览器 IndexedDB，刷新不丢。</p>
          </nav>
        )}

        <main className={`main${tab === 'map' ? ' main--map' : ''}`}>
          <div className="main__head">
            <h1>{tabLabel(tab, lang)}</h1>
          </div>
          {content}
        </main>

      </div>

      {isMobile && (
        <nav className="tabbar" aria-label="底部导航">
          {TABS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={tab === item.id ? 'is-active' : ''}
              onClick={() => setTab(item.id)}
            >
              {tabLabel(item.id, lang)}
            </button>
          ))}
        </nav>
      )}
    </div>
  );
}