import { expect, test } from '@playwright/test';

test('壳与三档布局可切', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByText('潮点 TideSpot').first()).toBeVisible();

  await page.getByRole('button', { name: '手机', exact: true }).click();
  await expect(page.locator('.app--mobile')).toHaveCount(1);

  await page.getByRole('button', { name: '桌面', exact: true }).click();
  await expect(page.locator('.app--desktop')).toHaveCount(1);

  await page.getByRole('button', { name: '自动', exact: true }).click();
});

test('收藏写入 IndexedDB，刷新后仍在', async ({ page }) => {
  // 通过地图页「收藏并查看」写入（旧版收藏页的「收藏示例点」按钮已随改版移除）
  await page.goto('/#/map');
  await page.waitForSelector('.maplibregl-canvas', { timeout: 25_000 });
  await page.click('.map-info__actions button:last-child');
  // 收藏前有确认弹窗（名称可编辑，留空用坐标）
  await page.getByRole('button', { name: '确认收藏' }).click();
  await page.waitForURL(/#\/spot\/\d+/, { timeout: 25_000 });

  await page.goto('/#/favorites');
  await expect(page.locator('.fav-card')).toHaveCount(1);

  await page.reload();
  await expect(page.locator('.fav-card')).toHaveCount(1);
});

test('主题切换写入 data-theme', async ({ page }) => {
  await page.goto('/#/settings');

  // 桌面档是分栏导航：先进入「深色模式」版块；移动档是单列，直接点
  const themeSection = page.locator('.settings-desk__nav button').filter({ hasText: '深色模式' });
  if (await themeSection.count()) await themeSection.first().click();

  await page.getByRole('button', { name: '深色', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'dark');

  await page.getByRole('button', { name: '浅色', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('data-theme', 'light');
});

test('设置页可打开（不含内部验收入口）', async ({ page }) => {
  await page.goto('/#/settings');
  await expect(page.locator('h1').first()).toContainText('设置');
  await expect(page.getByText('组件预览')).toHaveCount(0);
  await expect(page.getByText(/内部验收/)).toHaveCount(0);
});