import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { VitePWA } from 'vite-plugin-pwa';

/**
 * GitHub Pages 项目站点部署在 /<repo>/ 下，必须显式设置 base；
 * 自定义域名或用户站点则设为 '/'。
 * 由 CI 注入 PAGES_BASE，本地默认 '/'。
 */
export default defineConfig({
  base: process.env.PAGES_BASE ?? '/',
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: '潮点 TideSpot',
        short_name: '潮点',
        description: '离线优先的钓鱼指数',
        theme_color: '#1565C0',
        background_color: '#EEF3F8',
        display: 'standalone',
        start_url: '.',
        scope: '.',
        icons: [{ src: 'icon.svg', sizes: 'any', type: 'image/svg+xml', purpose: 'any' }],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,svg,png,woff2}'],
        navigateFallback: 'index.html',
        cleanupOutdatedCaches: true,
        // API 响应不进 Service Worker：缓存一律写 IndexedDB（避免双写与配额浪费）
        runtimeCaching: [],
      },
    }),
  ],
  // maplibre-gl 6 的 worker 与依赖预打包不兼容，dev 下排除
  optimizeDeps: {
    exclude: ['maplibre-gl'],
  },
  build: {
    target: 'es2022',
    sourcemap: true,
    assetsDir: 'assets',
  },
  server: {
    port: 5173,
    strictPort: true,
  },
});