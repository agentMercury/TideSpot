/**
 * Web 应用入口。
 *
 * 职责：挂载 React 根 → 注册 PWA Service Worker → 按顺序初始化主题与皮肤 → 渲染 AppShell。
 *
 * 注意初始化顺序：initTheme / initSkin 必须在 render 之前同步执行，
 * 否则首帧会用默认 token 渲染，出现「闪一下再变色」。
 *
 * CSS 引入顺序有语义：tokens 基础变量 → 组件样式 → 皮肤覆盖层（后者依赖前者）。
 * 对应 Android 端：\`app/src/main/kotlin/com/tidespot/app/MainActivity.kt\`。
 */
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { registerSW } from 'virtual:pwa-register';
import { EnvironmentProvider } from './app/environment/EnvironmentProvider';
import { AppShell } from './app/shell/AppShell';
import { initTheme } from './ui/theme';
import { initSkin } from './ui/skin';
import './styles/tokens.generated.css';
import './styles/tokens.css';
import './ui/primitives/primitives.css';
import './app/shell/shell.css';
import './styles/skins.css';
import './styles/fieldguide.css';
import './styles/fieldnotes.css';
import './styles/bluetide.css';
import './styles/vintage.css';

initTheme();
initSkin();
let updateSW: ((reload?: boolean) => Promise<void>) | undefined;
updateSW = registerSW({
  immediate: true,
  // 新 SW 就绪后直接接管并重载：否则用户会一直看到旧 index.html/旧 CSS（看起来「改了没好」）
  onNeedRefresh() {
    void updateSW?.(true);
  },
});

const root = document.getElementById('root');
if (!root) throw new Error('#root not found');

createRoot(root).render(
  <StrictMode>
    <EnvironmentProvider>
      <AppShell />
    </EnvironmentProvider>
  </StrictMode>,
);