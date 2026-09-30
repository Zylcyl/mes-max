import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

const KEY = 'mesmax_theme';
const DEFAULT = 'dark';

/**
 * 主题：深色（车间默认） / 浅色（白色背景）。
 *
 * <p>实现方式是把主题写成 `data-theme` 属性挂在 `<html>` 上，
 * 由 design/tokens.css 里的 `:root[data-theme='light']` 覆盖整套令牌 ——
 * 因此组件不需要知道自己运行在哪个主题下，全部走 CSS 变量。
 * 唯一的例外是 ECharts：它在 canvas 上绘制，用不了 CSS 变量，
 * 所以额外维护一个 version，供图表在切换后重新着色（见 utils/chart.js）。
 */

/** 在应用挂载前调用，避免首屏闪一下深色再变浅色。 */
export function initTheme() {
  const saved = localStorage.getItem(KEY);
  document.documentElement.dataset.theme = saved === 'light' ? 'light' : DEFAULT;
}

export const useTheme = defineStore('theme', () => {
  const theme = ref(document.documentElement.dataset.theme === 'light' ? 'light' : DEFAULT);
  const isLight = computed(() => theme.value === 'light');
  /** 图表依赖它感知主题变化（+1 就重新 setOption） */
  const version = ref(0);

  function set(next) {
    theme.value = next;
    document.documentElement.dataset.theme = next;
    localStorage.setItem(KEY, next);
    version.value += 1;
  }

  function toggle() {
    set(isLight.value ? 'dark' : 'light');
  }

  return { theme, isLight, version, set, toggle };
});
