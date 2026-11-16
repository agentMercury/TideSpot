import { defineConfig, devices } from '@playwright/test';

/**
 * E2E：只覆盖不依赖外网的路径（壳、三档、收藏持久化、主题）。
 * 地图页依赖第三方瓦片，不放进 CI 冒烟。
 *
 * 本地首次运行：npx playwright install chromium
 */
export default defineConfig({
  testDir: 'e2e',
  timeout: 30_000,
  fullyParallel: true,
  reporter: [['list']],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    trace: 'on-first-retry',
  },
  webServer: {
    command: 'npm run preview -- --port 4173',
    port: 4173,
    reuseExistingServer: true,
    timeout: 60_000,
  },
  projects: [
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
    {
      name: 'desktop',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } },
    },
  ],
});