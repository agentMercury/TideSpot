/**
 * 桌面三栏：左收藏列表 / 中详情 / 右摘要（右栏可收起）。
 * 复用移动端 DetailPage（embedded），不复制页面逻辑。
 */
import { useEffect, useState } from 'react';
import { repository } from '../container';
import { navigate } from '../router';
import { DetailPage } from './DetailPage';
import { Button, Card, Chip } from '../../ui/primitives/primitives';
import { resolveScoreBand } from '../../ui/tokens/bands';
import type { SpotRow } from '../../data/db';
import type { SpotDetail } from '../../data/repository';

function DetailAside({ spotId, onClose }: { spotId: number; onClose: () => void }) {
  const [detail, setDetail] = useState<SpotDetail | null>(null);

  useEffect(() => {
    let alive = true;
    void (async () => {
      const cached = await repository.readCachedDetail(spotId);
      if (alive) setDetail(cached);
      // 稍后等详情页写入缓存后再读一次
      const timer = setTimeout(() => {
        void repository.readCachedDetail(spotId).then((d) => {
          if (alive && d) setDetail(d);
        });
      }, 1200);
      return () => clearTimeout(timer);
    })();
    return () => {
      alive = false;
    };
  }, [spotId]);

  const scores = detail?.scores ?? [];
  const peak = scores.reduce((best, cur) => (cur.total > best.total ? cur : best), scores[0]);
  const band = peak ? resolveScoreBand(peak.total) : null;
  const top = peak
    ? [...peak.factors].sort((a, b) => b.sub * b.weight - a.sub * a.weight).slice(0, 3)
    : [];

  return (
    <div className="desk-aside">
      <div className="desk-aside__head">
        <strong>详情预览</strong>
        <Button variant="quiet" size="sm" onClick={onClose}>
          收起
        </Button>
      </div>

      {peak && band ? (
        <>
          <div className="desk-aside__score" style={{ color: band.color }}>
            {peak.total}
            <span>{band.label}</span>
          </div>
          <Card ariaLabel="摘要">
            <p className="hint">
              时段 {new Date((peak.timeEpochSec + 8 * 3600) * 1000).getUTCHours()}:00 · 因子 {peak.factors.length}
            </p>
            <p className="hint">{detail?.weatherDegraded || detail?.marineDegraded ? '部分维度降级' : '数据正常'}</p>
          </Card>
          <Card ariaLabel="前三因子">
            <strong>贡献前三</strong>
            <div className="row-gap">
              {top.map((f) => (
                <Chip key={f.id}>
                  {f.id} · {(f.sub * f.weight).toFixed(2)}
                </Chip>
              ))}
            </div>
          </Card>
        </>
      ) : (
        <p className="hint">暂无缓存摘要；打开详情并等待一次刷新后显示。</p>
      )}
    </div>
  );
}

export function DesktopSpotLayout({ spotId }: { spotId: number }) {
  const [items, setItems] = useState<SpotRow[]>([]);
  const [asideOpen, setAsideOpen] = useState(true);

  useEffect(() => {
    void repository.listFavorites().then(setItems);
  }, [spotId]);

  return (
    <div className="desk-spot">
      <aside className="desk-spot__list" aria-label="收藏列表">
        <div className="desk-spot__list-head">
          <strong>收藏</strong>
          <Button variant="quiet" size="sm" onClick={() => navigate('#/')}>
            返回
          </Button>
        </div>
        {items.length === 0 ? (
          <p className="hint">暂无收藏。回到列表页添加示例点。</p>
        ) : (
          items.map((row) => (
            <button
              key={row.id}
              type="button"
              className={`desk-spot__item ${row.id === spotId ? 'is-active' : ''}`}
              onClick={() => row.id != null && navigate(`#/spot/${row.id}`)}
            >
              <strong>{row.name}</strong>
              <span className="hint">
                {row.lat.toFixed(3)}, {row.lon.toFixed(3)}
              </span>
            </button>
          ))
        )}
      </aside>

      <main className="desk-spot__main">
        <DetailPage spotId={spotId} embedded />
      </main>

      {asideOpen ? (
        <aside className="desk-spot__side" aria-label="右栏详情">
          <DetailAside spotId={spotId} onClose={() => setAsideOpen(false)} />
        </aside>
      ) : (
        <div className="desk-spot__rail">
          <Button variant="ghost" size="sm" onClick={() => setAsideOpen(true)}>
            展开
          </Button>
        </div>
      )}
    </div>
  );
}