/**
 * 因子分解的「手帐进度条」形态（v3 图鉴概念）。
 * 仅潮汐手帐皮肤使用；A / D 仍用 DataTable。
 */
export interface FactorBarRow {
  name: string;
  sub: string;
  weight: string;
  contribution: string;
}

export function FactorBars({ rows }: { rows: ReadonlyArray<FactorBarRow> }) {
  if (rows.length === 0) return <p className="hint">暂无因子数据</p>;
  return (
    <div className="fn-factors">
      {rows.map((row) => {
        const value = Number(row.sub);
        const pct = Number.isFinite(value) ? Math.min(100, Math.max(0, value * 100)) : 0;
        return (
          <div className="fn-factor" key={row.name}>
            <span className="fn-factor__name">{row.name}</span>
            <span className="fn-factor__track">
              <i style={{ width: `${pct}%` }} />
            </span>
            <b className="fn-factor__value">{row.sub}</b>
            <em className="fn-factor__weight">w {row.weight}</em>
          </div>
        );
      })}
    </div>
  );
}