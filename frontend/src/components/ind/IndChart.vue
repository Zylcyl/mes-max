<script setup>
/** ECharts 容器：负责实例生命周期、窗口自适应与选项热更新。 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts';
import { swapColorsForTheme } from '@/utils/chart';
import { useTheme } from '@/stores/theme';

const themeStore = useTheme();

const props = defineProps({
  option: { type: Object, default: () => ({}) },
  height: { type: [String, Number], default: '100%' },
  notMerge: { type: Boolean, default: true },
});

const el = ref(null);
let chart = null;
let observer = null;

/**
 * 高度归一化。
 *
 * <p>模板里常见的写法是 `height="176"`（字符串），如果直接塞进 style 会得到 `height:176`
 * —— 没有单位，浏览器视为非法值直接忽略，容器高度退化成 0，图表既画不出来也不报错。
 * 这里统一把「纯数字字符串」补成 px，其余（如 "100%"）原样传递。
 */
const heightStyle = computed(() => {
  const h = props.height;
  if (h === null || h === undefined || h === '') return '100%';
  const s = String(h).trim();
  return /^\d+(\.\d+)?$/.test(s) ? `${s}px` : s;
});

function render() {
  if (!chart) return;
  chart.setOption(themedOption(), props.notMerge);
}

/** ECharts 用不了 CSS 变量，浅色主题下需要把 option 里的中性色替换成浅色版本。 */
function themedOption() {
  return swapColorsForTheme(props.option || {}, themeStore.isLight);
}

/**
 * 容器必须有尺寸才能初始化 ECharts。
 * 布局尚未稳定（或视口很窄导致容器被压成 0 宽）时直接 init 会报
 * "Can't get DOM width or height" 并且画不出来；这里改为「等有尺寸再初始化」，
 * 由 ResizeObserver 在尺寸出现时补上。
 */
function ensureChart() {
  if (chart) return true;
  const dom = el.value;
  if (!dom || dom.clientWidth === 0 || dom.clientHeight === 0) return false;
  chart = echarts.init(dom, null, { renderer: 'canvas' });
  render();
  return true;
}

onMounted(() => {
  ensureChart();
  observer = new ResizeObserver(() => {
    if (!ensureChart()) return;
    chart.resize();
  });
  observer.observe(el.value);
});

watch(
  () => props.option,
  () => {
    if (ensureChart()) render();
  },
  { deep: true },
);

// 主题切换：重新着色（option 对象本身没变，但其色值需要按主题重算）
watch(
  () => themeStore.version,
  () => {
    if (chart) render();
  },
);

onBeforeUnmount(() => {
  if (observer) observer.disconnect();
  if (chart) {
    chart.dispose();
    chart = null;
  }
});

defineExpose({ getInstance: () => chart });
</script>

<template>
  <div
    ref="el"
    class="ind-chart"
    :style="{ height: heightStyle }"
  />
</template>
