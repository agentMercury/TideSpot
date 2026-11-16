import { afterEach, describe, expect, it, vi } from 'vitest';
import { searchPlaces } from './geocoding';

function stubFetch(payload: unknown, status = 200) {
  const spy = vi.fn(async (input: RequestInfo | URL) => {
    void input;
    return new Response(JSON.stringify(payload), { status });
  });
  vi.stubGlobal('fetch', spy);
  return spy;
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('searchPlaces', () => {
  it('查询词少于 2 字符时不发请求', async () => {
    const spy = stubFetch({ results: [] });
    expect(await searchPlaces('青')).toEqual([]);
    expect(spy).not.toHaveBeenCalled();
  });

  it('拼出 language 参数并去掉空白', async () => {
    const spy = stubFetch({ results: [] });
    await searchPlaces('  Qingdao ', 'en');
    const url = String(spy.mock.calls[0]?.[0]);
    expect(url).toContain('name=Qingdao');
    expect(url).toContain('language=en');
  });

  it('过滤缺经纬度的结果，并去重展示名', async () => {
    stubFetch({
      results: [
        { id: 1, name: '万绿湖', latitude: 23.75, longitude: 114.7, admin1: '广东省', country: '中国' },
        { id: 2, name: '坏数据' },
      ],
    });
    const list = await searchPlaces('万绿湖');
    expect(list).toHaveLength(1);
    expect(list[0]).toMatchObject({ name: '万绿湖', lat: 23.75, lon: 114.7 });
    expect(list[0]?.label).toBe('万绿湖 · 广东省 · 中国');
  });

  it('同名地名不重复拼接', async () => {
    stubFetch({
      results: [{ id: 3, name: '青岛', latitude: 36.06, longitude: 120.38, admin1: '青岛', country: '中国' }],
    });
    const list = await searchPlaces('青岛');
    expect(list[0]?.label).toBe('青岛 · 中国');
  });

  it('按人口降序（缺字段视为 0）', async () => {
    stubFetch({
      results: [
        { id: 1, name: '小村', latitude: 1, longitude: 1, population: 100 },
        { id: 2, name: '大城', latitude: 2, longitude: 2, population: 9000 },
        { id: 3, name: '无名', latitude: 3, longitude: 3 },
      ],
    });
    const list = await searchPlaces('测试');
    expect(list.map((p) => p.name)).toEqual(['大城', '小村', '无名']);
  });

  it('HTTP 非 2xx 抛错', async () => {
    stubFetch({}, 503);
    await expect(searchPlaces('青岛')).rejects.toThrow('geocoding HTTP 503');
  });
});