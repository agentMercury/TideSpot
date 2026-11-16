/**
 * G-01 地图地名搜索：输入 → 候选（防抖 350ms + AbortController）→ 选中后由 MapPage flyTo。
 * 搜索只移动地图，不改变「准星即选点」的真源语义。
 */
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { searchPlaces, type PlaceCandidate } from '../../data/geocoding';
import { t, useLang } from '../../ui/i18n';

export function MapSearch({ onPick }: { onPick: (place: PlaceCandidate) => void }) {
  const [lang] = useLang();
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [items, setItems] = useState<PlaceCandidate[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [active, setActive] = useState(-1);
  const rootRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  // 语言切换后旧候选的地名语种会变，直接清空
  useEffect(() => {
    setItems([]);
    setActive(-1);
  }, [lang]);

  useEffect(() => {
    const q = query.trim();
    if (!open || q.length < 2) {
      setItems([]);
      setBusy(false);
      setError(null);
      return;
    }
    const controller = new AbortController();
    setBusy(true);
    const timer = setTimeout(() => {
      void searchPlaces(q, lang, controller.signal)
        .then((list) => {
          if (controller.signal.aborted) return;
          setItems(list);
          setError(null);
          setActive(list.length > 0 ? 0 : -1);
        })
        .catch((e: unknown) => {
          if (controller.signal.aborted) return;
          setItems([]);
          setError(e instanceof Error ? e.message : String(e));
        })
        .finally(() => {
          if (!controller.signal.aborted) setBusy(false);
        });
    }, 350);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [query, open, lang]);

  // 点外部收起
  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', onDown);
    return () => document.removeEventListener('pointerdown', onDown);
  }, [open]);

  useEffect(() => {
    if (open) inputRef.current?.focus();
  }, [open]);

  const pick = useCallback(
    (place: PlaceCandidate) => {
      onPick(place);
      setOpen(false);
      setQuery('');
      setItems([]);
      setActive(-1);
    },
    [onPick],
  );

  const onKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (items.length) setActive((i) => (i + 1) % items.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (items.length) setActive((i) => (i <= 0 ? items.length - 1 : i - 1));
    } else if (e.key === 'Enter') {
      if (active >= 0 && items[active]) pick(items[active]);
    } else if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  const hintText = useMemo(() => t('ts_map_search_hint', lang), [lang]);

  return (
    <div className={`map-search ${open ? 'is-open' : ''}`} ref={rootRef}>
      {!open ? (
        <button
          type="button"
          className="map-search__trigger"
          onClick={() => setOpen(true)}
          aria-label={hintText}
        >
          <span className="map-search__icon" aria-hidden="true">
            ⌕
          </span>
          <span className="map-search__trigger-text">{hintText}</span>
        </button>
      ) : (
        <div className="map-search__panel">
          <input
            ref={inputRef}
            className="map-search__input"
            type="search"
            role="combobox"
            aria-expanded={items.length > 0}
            aria-controls="map-search-results"
            aria-autocomplete="list"
            value={query}
            placeholder={hintText}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKeyDown}
          />
          {query && (
            <button
              type="button"
              className="map-search__clear"
              aria-label={t('ts_map_search_clear', lang)}
              onClick={() => {
                setQuery('');
                setItems([]);
                inputRef.current?.focus();
              }}
            >
              ×
            </button>
          )}
          <button
            type="button"
            className="map-search__close"
            onClick={() => setOpen(false)}
          >
            {t('ts_map_star_close', lang)}
          </button>
        </div>
      )}

      {open && (busy || error || items.length > 0) && (
        <div className="map-search__results" id="map-search-results" role="listbox">
          {busy && <p className="map-search__status">{t('ts_detail_loading', lang)}</p>}
          {error && <p className="map-search__status map-search__status--error">{error}</p>}
          {!busy &&
            !error &&
            items.map((place, i) => (
              <button
                key={`${place.id}-${place.lat}-${place.lon}`}
                type="button"
                role="option"
                aria-selected={i === active}
                className={`map-search__item ${i === active ? 'is-active' : ''}`}
                onMouseEnter={() => setActive(i)}
                onClick={() => pick(place)}
              >
                <span className="map-search__item-name">{place.name}</span>
                <span className="map-search__item-label">{place.label}</span>
              </button>
            ))}
        </div>
      )}
    </div>
  );
}