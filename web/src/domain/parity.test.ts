/**
 * M1 对拍：TS 平移必须与 Android 逐位一致。
 * fixture 由 web/.tmp/Dump.kt 调用真实 Kotlin 实现生成（只读 Android 源码）。
 */
import { readFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import * as mathkit from './mathkit';
import * as solunar from './solunar';
import * as sub from './subScores';
import { doPotential, doRef, oxygenScore } from './water/doPhysics';
import { fuse } from './water/waterTemp';
import { waterQualityScore } from './water/waterQuality';
import { evaluate as evaluateVeto } from './veto';
import { calculate } from './fishScore';
import { FRESH_LURE, SEA_SHORE } from './presets';
import type { HourlyMarine, HourlyScoreInput, ScenePreset } from './types';

const fixture = JSON.parse(readFileSync('test/fixtures/domain.json', 'utf8')) as any;

const REL = 1e-9;
function close(actual: number, expected: number | null, label: string): void {
  if (expected == null) {
    expect(actual, label).toBeNaN();
    return;
  }
  const tol = REL * Math.max(1, Math.abs(expected));
  expect(Math.abs(actual - expected), `${label} actual=${actual} expected=${expected}`).toBeLessThanOrEqual(tol);
}

describe('mathkit', () => {
  it('clip01', () => {
    const inputs = [-1, 0, 0.5, 1, 2];
    fixture.mathkit.clip01.forEach((expected: number, i: number) => close(mathkit.clip01(inputs[i]), expected, `clip01[${i}]`));
  });

  it('gauss', () => {
    const inputs = [0, 15, 30, 45];
    fixture.mathkit.gauss.forEach((expected: number, i: number) => close(mathkit.gauss(inputs[i], 15, 13), expected, `gauss[${i}]`));
  });

  it('trap', () => {
    const inputs = [-0.3, 0, 0.3, 0.8, 1.2, 1.5, 2, 2.2];
    fixture.mathkit.trap.forEach((expected: number, i: number) =>
      close(mathkit.trap(inputs[i], -0.2, 0.5, 1.2, 2.1), expected, `trap[${i}]`),
    );
  });

  it('sig', () => {
    const inputs = [0, 20, 34, 45, 60];
    fixture.mathkit.sig.forEach((expected: number, i: number) => close(mathkit.sig(inputs[i], 34, 0.3), expected, `sig[${i}]`));
  });

  it('jsMod', () => {
    const inputs = [-25, -12.4, 0, 3, 24, 30];
    fixture.mathkit.jsMod.forEach((expected: number, i: number) => close(mathkit.jsMod(inputs[i], 12.4206), expected, `jsMod[${i}]`));
  });
});

describe('subScores', () => {
  it('tide', () => {
    for (const [dh, amp, expected] of fixture.subScores.tide) {
      close(sub.tide(dh, amp), expected, `tide(${dh},${amp})`);
    }
  });

  it('pressure', () => {
    for (const [p, d3, d24, expected] of fixture.subScores.pressure) {
      close(sub.pressure(p, d3, d24), expected, `pressure(${p},${d3},${d24})`);
    }
  });

  it('wind', () => {
    for (const [speed, gust, expected] of fixture.subScores.wind) {
      close(sub.wind(speed, gust), expected, `wind(${speed},${gust})`);
    }
  });

  it('weather', () => {
    for (const [code, rain, cloud, expected] of fixture.subScores.weather) {
      close(sub.weather(code, rain, cloud), expected, `weather(${code},${rain},${cloud})`);
    }
  });

  it('daypart', () => {
    for (const [hour, sr, ss, night, expected] of fixture.subScores.daypart) {
      close(sub.daypart(hour, sr, ss, night), expected, `daypart(${hour},${sr},${ss},${night})`);
    }
  });

  it('tempMatch', () => {
    for (const [tw, opt, sigma, expected] of fixture.subScores.tempMatch) {
      close(sub.tempMatch(tw, opt, sigma), expected, `tempMatch(${tw},${opt},${sigma})`);
    }
  });

  it('comfort', () => {
    for (const [apparent, expected] of fixture.subScores.comfort) {
      close(sub.comfort(apparent), expected, `comfort(${apparent})`);
    }
  });

  it('waterQuality', () => {
    for (const [so, cloud, rain, tw, expected] of fixture.subScores.waterQuality) {
      close(waterQualityScore(so, cloud, rain, null, tw), expected, `waterQuality(${so},${cloud},${rain},${tw})`);
    }
  });
});

describe('solunar', () => {
  it('lunarAge', () => {
    for (const [epochSec, expected] of fixture.solunar.age) {
      close(solunar.lunarAge(epochSec * 1000), expected, `age(${epochSec})`);
    }
  });

  it('illumination', () => {
    for (const [age, expected] of fixture.solunar.illumination) {
      close(solunar.illumination(age), expected, `illum(${age})`);
    }
  });

  it('lunarTransitClock', () => {
    for (const [age, lon, tz, expected] of fixture.solunar.transit) {
      close(solunar.lunarTransitClock(age, lon, tz), expected, `transit(${age},${lon},${tz})`);
    }
  });

  it('score', () => {
    for (const [hour, transit, age, expected] of fixture.solunar.score) {
      close(solunar.score(hour, transit, age), expected, `solunar(${hour},${transit},${age})`);
    }
  });
});

describe('doPhysics', () => {
  it('doPotential', () => {
    for (const [tw, p, sal, expected] of fixture.doPhysics.potential) {
      close(doPotential(tw, p, sal), expected, `potential(${tw},${p},${sal})`);
    }
  });

  it('doRef', () => {
    for (const [tw, base, expected] of fixture.doPhysics.doRef) {
      close(doRef(tw, base), expected, `doRef(${tw},${base})`);
    }
  });

  it('oxygenScore', () => {
    for (const [dop, ref, expected] of fixture.doPhysics.oxygenScore) {
      close(oxygenScore(dop, ref), expected, `oxygenScore(${dop},${ref})`);
    }
  });
});

describe('waterTemp', () => {
  it('fuse', () => {
    const series = Array.from({ length: 41 }, (_, i) => 18 + 0.2 * i);
    for (const [index, sst, expected] of fixture.waterTemp) {
      const actual = fuse(index === 5 && sst == null ? [null, null] : series, index, 18.0, sst);
      if (expected == null) expect(actual).toBeNull();
      else close(actual as number, expected, `fuse(${index},${sst})`);
    }
  });
});

describe('veto', () => {
  it('value 与 codes 数量', () => {
    for (const [wmo, wind, gust, dp3, rain, wave, dop, expectedValue, codesLen] of fixture.veto) {
      const actual = evaluateVeto({
        wmoCode: wmo,
        windSpeedKmh: wind,
        gustKmh: gust,
        dP3: dp3,
        rainMmPerH: rain,
        waveHeightM: wave,
        doPotentialMgL: dop,
      });
      close(actual.value, expectedValue, `veto(${wmo},${wind},${gust},${dp3},${rain},${wave},${dop})`);
      expect(actual.codes.length).toBe(codesLen);
    }
  });
});

describe('fishScore（端到端）', () => {
  function buildInputs(raw: any[]): HourlyScoreInput[] {
    return raw.map((row) => {
      const [, temp, pressure, cloud, rain, showers, code, wind, gust, apparent, wave, sl, sst] = row;
      const marine: HourlyMarine | null =
        wave == null && sl == null && sst == null
          ? null
          : { timeEpochSec: row[0], waveHeightM: wave, seaLevelHeightMslM: sl, seaSurfaceTempC: sst };
      return {
        timeEpochSec: row[0],
        weather: {
          timeEpochSec: row[0],
          temperatureC: temp,
          pressureMslHpa: pressure,
          cloudCoverPct: cloud,
          rainMm: rain,
          showersMm: showers,
          weatherCode: code,
          windSpeedKmh: wind,
          windGustKmh: gust,
          apparentC: apparent,
        },
        marine,
        sunriseLocalHour: 6.2,
        sunsetLocalHour: 17.3,
        tzOffsetHours: 8.0,
      };
    });
  }

  function checkCase(name: string, preset: ScenePreset): void {
    const c = fixture.fishScore[name];
    const inputs = buildInputs(c.inputs);
    const results = calculate(inputs, preset, c.lonDeg);

    expect(results.length).toBe(c.results.length);
    results.forEach((r, i) => {
      const [, total, confidence, codesLen, factorsLen, subSum] = c.results[i];
      expect(r.total, `${name}[${i}] total`).toBe(total);
      expect(r.confidence, `${name}[${i}] confidence`).toBe(confidence);
      expect(r.vetoCodes.length, `${name}[${i}] codes`).toBe(codesLen);
      expect(r.factors.length, `${name}[${i}] factors`).toBe(factorsLen);
      close(
        r.factors.reduce((acc, f) => acc + f.sub, 0),
        subSum,
        `${name}[${i}] subSum`,
      );
    });
  }

  it('sea shore（有 marine）', () => checkCase('seaShore', SEA_SHORE));
  it('fresh lure（marine 全 null）', () => checkCase('freshLure', FRESH_LURE));
});