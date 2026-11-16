/**
 * 真实网络冒烟（默认跳过）：
 *   PowerShell:  $env:SMOKE='1'; npm run test
 * 附：跑一次会把前 48h 的映射结果写入 test/fixtures/open-meteo-sample.json，
 *     供后续离线测试/回归使用（不依赖网络）。
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { describe, expect, it } from 'vitest';
import { OpenMeteoClient } from './client';
import { isMarineAllNull } from './dto';

const enabled = process.env.SMOKE === '1';

describe.skipIf(!enabled)('real Open-Meteo（SMOKE=1）', () => {
  it('青岛近岸：weather + marine 均可用', async () => {
    const client = new OpenMeteoClient({ callTimeoutMillis: 20_000 });
    // 串行 + 60s：本机 Node 的 happy-eyeballs 偶发先试不可达地址族，浏览器无此问题
    const weather = await client.fetchWeatherHourly(36.06, 120.38);
    const marine = await client.fetchMarineHourly(36.06, 120.38);

    expect(weather.length).toBeGreaterThan(200);
    expect(marine.length).toBeGreaterThan(200);
    expect(weather[0].temperatureC).not.toBeNull();
    expect(marine.some((m) => m.waveHeightM != null)).toBe(true);

    mkdirSync('test/fixtures', { recursive: true });
    writeFileSync(
      'test/fixtures/open-meteo-sample.json',
      JSON.stringify(
        {
          fetchedAt: new Date().toISOString(),
          note: '真实响应的映射结果（前 48h），供离线回归；由 SMOKE=1 生成',
          offshore: { latitude: 36.06, longitude: 120.38 },
          weatherHourly: weather.slice(0, 48),
          marineHourly: marine.slice(0, 48),
        },
        null,
        2,
      ) + '\n',
      'utf8',
    );

    // 登录可用性证据：首小时潮位与海温
    console.log(
      `[smoke] weather=${weather.length}h marine=${marine.length}h ` +
        `sl@0=${marine[0].seaLevelHeightMslM} sst@0=${marine[0].seaSurfaceTempC}`,
    );
  }, 60_000);

  it('内陆点（千岛湖）：marine 200 但全 null', async () => {
    const client = new OpenMeteoClient({ callTimeoutMillis: 20_000 });
    const marine = await client.fetchMarineHourly(29.6, 119.04);
    expect(marine.length).toBeGreaterThan(0);
    expect(marine.every(isMarineAllNull)).toBe(true);
    console.log(`[smoke] inland marine=${marine.length}h allNull=true`);
  }, 60_000);
});