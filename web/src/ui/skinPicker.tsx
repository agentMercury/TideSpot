/**
 * 皮肤选择卡（SKIN-1 的可视化入口）。
 * 只展示 + 回调，不持有状态；色板条由 CSS 按 skin id 着色。
 */
import { SKINS, type SkinId } from './skin';
import { IcFishFieldguide, IcFishFieldnotes } from './icons';

export function SkinPicker({
  value,
  onChange,
}: {
  value: SkinId;
  onChange: (id: SkinId) => void;
}) {
  return (
    <div className="skin-picker" role="group" aria-label="皮肤">
      {SKINS.map((item) => {
        const active = value === item.id;
        return (
          <button
            key={item.id}
            type="button"
            className={`skin-card skin-card--${item.id}${active ? ' is-active' : ''}`}
            aria-pressed={active}
            onClick={() => onChange(item.id)}
          >
            <span className="skin-card__swatch" aria-hidden="true">
              <i />
              <i />
              <i />
            </span>
            <span className="skin-card__text">
              <strong className="skin-card__name">{item.name}</strong>
              <span className="skin-card__en">{item.nameEn}</span>
              <span className="skin-card__tag">{item.tagline}</span>
            </span>
            <span className="skin-card__seal">{active ? '已选' : '选用'}</span>
            {item.id === 'd-fieldguide' && <IcFishFieldguide className="skin-card__fish" size={22} />}
            {item.id === 'e-tidejournal' && <IcFishFieldnotes className="skin-card__fish" size={22} />}
          </button>
        );
      })}
    </div>
  );
}