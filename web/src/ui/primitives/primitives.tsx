/**
 * 12 个基础组件（对应 design-handoff/components 的落地子集）。
 * 只做展示，不含业务；所有颜色/圆角/字号来自 tokens.generated.css。
 */
import { useEffect, useId, useState, type ReactNode } from 'react';
import { SCORE_BANDS, confidenceBadge, type ConfidenceBadgeSpec, type ScoreBand } from '../tokens/bands';
import { IcFishFieldguide, IcFishFieldnotes, IcFishVintage } from '../icons';

export type ButtonVariant = 'primary' | 'ghost' | 'quiet';
export type ControlSize = 'sm' | 'md';

export function Button({
  children,
  onClick,
  variant = 'ghost',
  size = 'md',
  disabled,
  type = 'button',
}: {
  children: ReactNode;
  onClick?: () => void;
  variant?: ButtonVariant;
  size?: ControlSize;
  disabled?: boolean;
  type?: 'button' | 'submit';
}) {
  return (
    <button
      type={type}
      className={`ts-btn ts-btn--${variant} ts-btn--${size}`}
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  );
}

export function Card({
  children,
  level = 1,
  className = '',
  ariaLabel,
}: {
  children: ReactNode;
  level?: 0 | 1 | 2;
  className?: string;
  ariaLabel?: string;
}) {
  return (
    <section className={`ts-card ts-card--l${level} ${className}`} aria-label={ariaLabel}>
      {children}
    </section>
  );
}

export function Chip({
  children,
  selected = false,
  tone = 'default',
  onClick,
}: {
  children: ReactNode;
  selected?: boolean;
  tone?: 'default' | 'mint' | 'band';
  onClick?: () => void;
}) {
  const interactive = onClick != null;
  const cls = `ts-chip ts-chip--${tone} ${selected ? 'is-selected' : ''}`;
  return interactive ? (
    <button type="button" className={cls} aria-pressed={selected} onClick={onClick}>
      {children}
    </button>
  ) : (
    <span className={cls}>{children}</span>
  );
}

export function ConfidenceBadge({ kind, label }: { kind: string; label?: string }) {
  const spec: ConfidenceBadgeSpec | null = confidenceBadge(kind);
  const text = label ?? spec?.label ?? kind;
  const style = spec
    ? { background: spec.color, color: spec.onColor }
    : { background: 'var(--color-surface-variant)', color: 'var(--color-text-dim)' };
  return (
    <span className="ts-badge" style={style} title={`数据置信：${text}`}>
      {text}
    </span>
  );
}

export function SegmentedTabs({
  items,
  value,
  onChange,
  scrollable = false,
}: {
  items: ReadonlyArray<{ id: string; label: string }>;
  value: string;
  onChange: (id: string) => void;
  scrollable?: boolean;
}) {
  return (
    <div className={`ts-tabs ${scrollable ? 'ts-tabs--scroll' : ''}`} role="tablist">
      {items.map((item) => (
        <button
          key={item.id}
          type="button"
          role="tab"
          aria-selected={value === item.id}
          className={value === item.id ? 'is-active' : ''}
          onClick={() => onChange(item.id)}
        >
          {item.label}
        </button>
      ))}
    </div>
  );
}

export interface DayChip {
  key: string;
  topLabel: string;
  weekdayLabel: string;
  score?: number | null;
}

export function DaySelector({
  days,
  value,
  onChange,
}: {
  days: ReadonlyArray<DayChip>;
  value: string;
  onChange: (key: string) => void;
}) {
  return (
    <div className="ts-day" role="tablist" aria-label="日期选择">
      {days.map((day) => (
        <button
          key={day.key}
          type="button"
          role="tab"
          aria-selected={value === day.key}
          className={`ts-day__chip ${value === day.key ? 'is-active' : ''}`}
          onClick={() => onChange(day.key)}
        >
          <span className="ts-day__top">{day.topLabel}</span>
          <span className="ts-day__week">{day.weekdayLabel}</span>
          {day.score != null && <span className="ts-day__score">{day.score}</span>}
        </button>
      ))}
    </div>
  );
}

export function ScoreCard({
  total,
  band,
  confidence,
  windowLabel,
  segments = 5,
  activeSegment = 2,
}: {
  total: number;
  band: ScoreBand;
  confidence: string;
  windowLabel?: string;
  segments?: number;
  activeSegment?: number;
}) {
  // 兼容旧调用：五档现在由 SCORE_BANDS 固定渲染，选中档按 band.key 判定
  void segments;
  void activeSegment;
  return (
    <div className="ts-score" aria-label={`指数 ${total}，${band.label}`}>
      <div className="ts-score__glow" />
      <div className="ts-score__stamp" aria-hidden="true">
        <IcFishFieldguide className="ts-score__stamp-guide" size={26} />
        <IcFishVintage className="ts-score__stamp-vintage" size={26} />
      </div>
      <div className="ts-score__head">
        <span className="ts-score__band">{band.label}</span>
        <ConfidenceBadge kind={confidence} />
      </div>
      <div className="ts-score__value-row">
        <span className="ts-score__value">{total}</span>
        <span className="ts-score__denom">/ 100</span>
      </div>
      {windowLabel && <div className="ts-score__window">{windowLabel}</div>}
      {/* 五档色带：与 Android `DetailScreen` 一致 —— 真实档位色 + 选中档加粗描边 + 档位文字 */}
      <div className="ts-score__segments">
        {SCORE_BANDS.map((b, i) => {
          const selected = b.key === band.key;
          return (
            <span
              key={b.key}
              className={'ts-seg' + (selected ? ' is-active' : '')}
              style={{ background: b.color, color: b.onColor }}
            >
              <span className="ts-seg__figures" aria-hidden="true">
                {Array.from({ length: 5 - i }).map((_, k) => (
                  <i key={k} />
                ))}
              </span>
              <span className="ts-seg__label">{b.label}</span>
            </span>
          );
        })}
      </div>
    </div>
  );
}

export function BottomSheet({
  open,
  title,
  onClose,
  children,
}: {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}) {
  useEffect(() => {
    if (!open) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  if (!open) return null;
  return (
    <div className="ts-sheet" role="dialog" aria-modal="true" aria-label={title}>
      <button type="button" className="ts-sheet__scrim" aria-label="关闭" onClick={onClose} />
      <div className="ts-sheet__panel">
        <div className="ts-sheet__handle" />
        <div className="ts-sheet__head">
          <strong>{title}</strong>
          <Button variant="quiet" size="sm" onClick={onClose}>
            关闭
          </Button>
        </div>
        <div className="ts-sheet__body">{children}</div>
      </div>
    </div>
  );
}

export interface TableColumn {
  key: string;
  label: string;
  align?: 'left' | 'right';
}

export function DataTable({
  columns,
  rows,
  onRowClick,
  emptyLabel = '暂无数据',
}: {
  columns: ReadonlyArray<TableColumn>;
  rows: ReadonlyArray<Record<string, ReactNode>>;
  onRowClick?: (index: number) => void;
  emptyLabel?: string;
}) {
  if (rows.length === 0) return <p className="ts-hint">{emptyLabel}</p>;
  return (
    <table className="ts-table">
      <thead>
        <tr>
          {columns.map((c) => (
            <th key={c.key} style={{ textAlign: c.align ?? 'left' }}>
              {c.label}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, i) => (
          <tr
            key={i}
            className={onRowClick ? 'is-clickable' : ''}
            onClick={onRowClick ? () => onRowClick(i) : undefined}
          >
            {columns.map((c) => (
              <td key={c.key} className={c.align === 'right' ? 'is-num' : undefined} style={{ textAlign: c.align ?? 'left' }}>
                {row[c.key]}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}

/** 空态：贴纸鱼插画只在 D 皮肤显示；A 皮肤退化为纯文案（保持原样） */
export function EmptyState({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="fg-empty">
      <IcFishFieldguide className="fg-empty__art fg-empty__art--guide" size={74} />
      <IcFishFieldnotes className="fg-empty__art fg-empty__art--notes" size={86} />
      <IcFishVintage className="fg-empty__art fg-empty__art--vintage" size={74} />
      <p className="hint fg-empty__title">{title}</p>
      {hint && <p className="fg-empty__hint">{hint}</p>}
    </div>
  );
}

export function Tooltip({ label, children }: { label: string; children: ReactNode }) {
  return (
    <span className="ts-tip">
      {children}
      <span className="ts-tip__bubble" role="tooltip">
        {label}
      </span>
    </span>
  );
}

export function Switch({
  checked,
  onChange,
  label,
}: {
  checked: boolean;
  onChange: (next: boolean) => void;
  label: string;
}) {
  return (
    <label className="ts-switch">
      <span>{label}</span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-label={label}
        className={`ts-switch__track ${checked ? 'is-on' : ''}`}
        onClick={() => onChange(!checked)}
      >
        <span className="ts-switch__thumb" />
      </button>
    </label>
  );
}

export function Field({
  label,
  value,
  onChange,
  placeholder,
  type = 'text',
  error,
}: {
  label: string;
  value: string;
  onChange: (next: string) => void;
  placeholder?: string;
  type?: 'text' | 'password';
  error?: string | null;
}) {
  const id = useId();
  return (
    <div className="ts-field">
      <label htmlFor={id}>{label}</label>
      <input
        id={id}
        type={type}
        value={value}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value)}
        aria-invalid={error != null}
      />
      {error && <span className="ts-field__error">{error}</span>}
    </div>
  );
}

/** 组件预览（仅用于内部验收；不进任何产品流程） */
export function PrimitivesPreview() {
  const [tab, setTab] = useState('score');
  const [day, setDay] = useState('d1');
  const [sheetOpen, setSheetOpen] = useState(false);
  const [checked, setChecked] = useState(true);
  const [text, setText] = useState('');

  return (
    <div className="ts-preview">
      <Card ariaLabel="按钮">
        <strong>Button</strong>
        <div className="ts-preview__row">
          <Button variant="primary">主要</Button>
          <Button variant="ghost">次要</Button>
          <Button variant="quiet">静默</Button>
          <Button variant="primary" size="sm">
            小号
          </Button>
        </div>
      </Card>

      <Card ariaLabel="Chip 与徽标">
        <strong>Chip / ConfidenceBadge</strong>
        <div className="ts-preview__row">
          <Chip>默认</Chip>
          <Chip selected>选中</Chip>
          <Chip tone="mint">薄荷</Chip>
          <ConfidenceBadge kind="MEASURED" />
          <ConfidenceBadge kind="MODEL" />
          <ConfidenceBadge kind="ESTIMATED" />
          <ConfidenceBadge kind="UNCALIBRATED" />
        </div>
      </Card>

      <Card ariaLabel="分段 Tab 与日期">
        <strong>SegmentedTabs / DaySelector</strong>
        <SegmentedTabs
          items={[
            { id: 'score', label: '钓鱼指数' },
            { id: 'tide', label: '潮流' },
            { id: 'weather', label: '天气' },
            { id: 'water', label: '水质' },
            { id: 'wave', label: '风浪' },
          ]}
          value={tab}
          onChange={setTab}
          scrollable
        />
        <div style={{ marginTop: 12 }}>
          <DaySelector
            days={[
              { key: 'd1', topLabel: '今天', weekdayLabel: '周三', score: 78 },
              { key: 'd2', topLabel: '11-12', weekdayLabel: '周四', score: 65 },
              { key: 'd3', topLabel: '11-13', weekdayLabel: '周五', score: 42 },
            ]}
            value={day}
            onChange={setDay}
          />
        </div>
      </Card>

      <div className="ts-preview__row" style={{ alignItems: 'stretch' }}>
        <ScoreCard total={78} band={{ key: 'GREAT', min: 65, max: 79, label: '很适合', color: '#43A047', onColor: '#FFFFFF' }} confidence="MODEL" windowLabel="最佳窗 05:00–08:00" />
      </div>

      <Card ariaLabel="表格">
        <strong>DataTable</strong>
        <DataTable
          columns={[
            { key: 'name', label: '名称' },
            { key: 'score', label: '指数', align: 'right' },
            { key: 'window', label: '最佳窗口' },
          ]}
          rows={[
            { name: '青岛·示例', score: 78, window: '05:00–08:00' },
            { name: '千岛湖', score: 61, window: '06:00–09:00' },
          ]}
        />
      </Card>

      <Card ariaLabel="其它">
        <strong>Tooltip / Switch / Field / Sheet</strong>
        <div className="ts-preview__row">
          <Tooltip label="数据来源：Open-Meteo">
            <Chip>来源</Chip>
          </Tooltip>
          <Switch checked={checked} onChange={setChecked} label="跟随系统深浅" />
          <Button variant="ghost" onClick={() => setSheetOpen(true)}>
            打开 Sheet
          </Button>
        </div>
        <Field label="Copernicus 用户名" value={text} onChange={setText} placeholder="仅本人可见" />
      </Card>

      <BottomSheet open={sheetOpen} title="免责声明" onClose={() => setSheetOpen(false)}>
        <p className="ts-hint">示例 Sheet：顶部 20px 圆角、Escape 可关闭。</p>
      </BottomSheet>
    </div>
  );
}