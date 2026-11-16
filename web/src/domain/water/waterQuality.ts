/**
 * 与 Android `water/WaterQualityProxy.kt` 逐行等价（SRS §6.2j）。
 * cloud/rain 缺失按中性 0 处理并保持「估算」语义；CHL 缺失走水温+光照推导。
 */
import { chlorophyllIndex, turbidityIndex, waterQuality } from '../subScores';

export function waterQualityScore(
  sOxygen: number,
  cloudCoverPct: number | null | undefined,
  rain24hMm: number | null | undefined,
  chlMgm3: number | null | undefined,
  waterTempC: number | null | undefined,
): number {
  const turbidity = turbidityIndex(cloudCoverPct, rain24hMm ?? 0.0);
  const chl = chlorophyllIndex(chlMgm3, waterTempC, cloudCoverPct);
  return waterQuality(sOxygen, turbidity, chl);
}