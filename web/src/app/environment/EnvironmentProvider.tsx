/**
 * 环境自适应（ADP）Provider —— 手机 / 平板 / 桌面三档布局。
 *
 * 判定依据：视口宽度 + 用户手动覆盖（后者优先，存 localStorage \`LAYOUT_MODE_KEY\`）。
 * 判定逻辑本身在 \`detect.ts\`（纯函数，有单测）；本文件只负责监听 resize、
 * 持久化用户选择、通过 Context 下发 \`env\` 与 \`setLayoutMode\`。
 *
 * 这是 **Web 独有**能力：Android 一期只做手机布局（PARITY_GAP-WEB-ADP-01，客户裁定不做）。
 * 因此不要试图在 Android 侧找对应实现。
 */
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import {
  BREAKPOINT_DESKTOP,
  BREAKPOINT_TABLET,
  LAYOUT_MODE_KEY,
  detectEnvironment,
  normalizeLayoutMode,
  type Environment,
  type LayoutMode,
} from './detect';

interface EnvironmentContextValue {
  env: Environment;
  mode: LayoutMode;
  setMode: (mode: LayoutMode) => void;
}

const EnvironmentContext = createContext<EnvironmentContextValue | null>(null);

function readStoredMode(): LayoutMode {
  try {
    return normalizeLayoutMode(window.localStorage.getItem(LAYOUT_MODE_KEY));
  } catch {
    return 'auto';
  }
}

function probe() {
  return {
    width: window.innerWidth,
    hasHover: window.matchMedia('(hover: hover)').matches,
    pointerFine: window.matchMedia('(pointer: fine)').matches,
  };
}

export function EnvironmentProvider({ children }: { children: ReactNode }) {
  const [mode, setModeState] = useState<LayoutMode>(readStoredMode);
  const [raw, setRaw] = useState(probe);

  useEffect(() => {
    const update = () => setRaw(probe());
    const queries = [
      window.matchMedia(`(min-width: ${BREAKPOINT_TABLET}px)`),
      window.matchMedia(`(min-width: ${BREAKPOINT_DESKTOP}px)`),
      window.matchMedia('(hover: hover)'),
      window.matchMedia('(pointer: fine)'),
    ];
    queries.forEach((q) => q.addEventListener('change', update));
    window.addEventListener('resize', update);
    window.addEventListener('orientationchange', update);
    return () => {
      queries.forEach((q) => q.removeEventListener('change', update));
      window.removeEventListener('resize', update);
      window.removeEventListener('orientationchange', update);
    };
  }, []);

  const setMode = useCallback((next: LayoutMode) => {
    setModeState(next);
    try {
      if (next === 'auto') window.localStorage.removeItem(LAYOUT_MODE_KEY);
      else window.localStorage.setItem(LAYOUT_MODE_KEY, next);
    } catch {
      // 隐私模式下 localStorage 可能抛错，忽略即可：本次会话仍然生效
    }
  }, []);

  const env = useMemo(() => detectEnvironment({ ...raw, mode }), [raw, mode]);
  const value = useMemo(() => ({ env, mode, setMode }), [env, mode, setMode]);

  return <EnvironmentContext.Provider value={value}>{children}</EnvironmentContext.Provider>;
}

export function useEnvironment(): EnvironmentContextValue {
  const ctx = useContext(EnvironmentContext);
  if (!ctx) throw new Error('useEnvironment must be used within <EnvironmentProvider>');
  return ctx;
}