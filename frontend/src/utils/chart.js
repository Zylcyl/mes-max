/**
 * ECharts 工业化主题与常用图表构造器。
 *
 * 统一在这里定义配色与轴线样式，保证全站图表观感一致：
 * 深色底、细网格、小字号、等宽数字、无花哨渐变与阴影。
 */

export const CHART_COLORS = {
  primary: '#0aa2ff',
  cyan: '#16d3d3',
  run: '#1fc96b',
  idle: '#f5a524',
  stop: '#ff4d4f',
  alarm: '#ff2d55',
  maint: '#a855f7',
  offline: '#657586',
  text: '#dbe6f0',
  dim: '#8b9db0',
  mute: '#5d6f81',
  line: '#223040',
  panel: '#131b24',
};

export const SERIES_PALETTE = [
  CHART_COLORS.primary,
  CHART_COLORS.cyan,
  CHART_COLORS.run,
  CHART_COLORS.idle,
  CHART_COLORS.maint,
  CHART_COLORS.stop,
];

/**
 * 深色主题的具体色值 → 浅色主题对应值。
 *
 * <p>为什么需要这张表：ECharts 画在 canvas 上，**不支持 CSS 变量**（`var(--x)` 会被当普通字符串），
 * 所以图表颜色只能在 JS 里给具体值。主题切换时用这张表把已经构建好的 option 里的中性色替换掉：
 * 轴线、网格、文字、tooltip 底色换成浅色版本，**状态色（运行绿/停机红/报警红等）保持不变** ——
 * 它们在浅底上依然可辨识，而且语义色不该随主题变。
 */
export const LIGHT_COLOR_SWAP = {
  '#223040': '#cfd9e3', // 网格线
  '#1a2431': '#e3e9ef', // 次级线条
  '#2b3742': '#c0ccd8', // 刻度线
  '#3a4b5e': '#9aabb9', // 分割线
  '#131b24': '#ffffff', // 面板底色（饼图描边、无数据底）
  '#1b232d': '#f7fafc',
  '#0b1118': '#eaeff4', // 凹槽底色
  '#dbe6f0': '#22303c', // 主文字
  '#8b9db0': '#55677a', // 次级文字（轴线标签、图例）
  '#5d6f81': '#7d8d9c', // 弱化文字
  'rgba(11,17,24,0.96)': 'rgba(255,255,255,0.97)', // tooltip 底
  'rgba(11,17,24,0.9)': 'rgba(255,255,255,0.94)',
};

/**
 * 深度遍历 option，把命中映射表的颜色字符串换成浅色版本。
 * 只替换「完全相等」的字符串，不做模糊匹配 —— 数据里出现的同名颜色（若有）不受影响。
 */
export function swapColorsForTheme(value, light) {
  if (!light) return value;
  if (typeof value === 'string') return LIGHT_COLOR_SWAP[value] ?? value;
  if (Array.isArray(value)) return value.map((v) => swapColorsForTheme(v, light));
  if (value && typeof value === 'object') {
    const out = {};
    for (const [k, v] of Object.entries(value)) out[k] = swapColorsForTheme(v, light);
    return out;
  }
  return value;
}

const numFont = "'Bahnschrift','DIN Alternate','Consolas',monospace";

/** 通用基础配置：网格、坐标轴、提示框、图例。 */
export function base({ grid, legend, xName, yName } = {}) {
  return {
    backgroundColor: 'transparent',
    textStyle: { fontFamily: numFont, color: CHART_COLORS.dim, fontSize: 11 },
    grid: {
      left: 46,
      right: 18,
      top: legend ? 34 : 18,
      bottom: 26,
      containLabel: false,
      ...grid,
    },
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(11,17,24,0.96)',
      borderColor: CHART_COLORS.line,
      borderWidth: 1,
      padding: [8, 10],
      textStyle: { color: CHART_COLORS.text, fontSize: 12, fontFamily: numFont },
      axisPointer: { type: 'line', lineStyle: { color: CHART_COLORS.primary, opacity: 0.5 } },
    },
    legend: legend
      ? {
          top: 2,
          right: 8,
          itemWidth: 10,
          itemHeight: 6,
          itemGap: 14,
          textStyle: { color: CHART_COLORS.dim, fontSize: 11 },
          ...legend,
        }
      : undefined,
    xAxis: {
      type: 'category',
      name: xName,
      nameTextStyle: { color: CHART_COLORS.mute, fontSize: 10 },
      axisLine: { lineStyle: { color: CHART_COLORS.line } },
      axisTick: { show: false },
      axisLabel: { color: CHART_COLORS.dim, fontSize: 10, fontFamily: numFont },
      splitLine: { show: false },
    },
    yAxis: {
      type: 'value',
      name: yName,
      nameTextStyle: { color: CHART_COLORS.mute, fontSize: 10 },
      axisLine: { show: false },
      axisTick: { show: false },
      axisLabel: { color: CHART_COLORS.dim, fontSize: 10, fontFamily: numFont },
      splitLine: { lineStyle: { color: CHART_COLORS.line, type: [3, 3], opacity: 0.55 } },
    },
  };
}

/** 折线/面积图。series: [{ name, data, color, area, smooth, dashed }] */
export function lineOption({ x, series, legend = true, yName = '', grid }) {
  const b = base({ legend, yName, grid });
  return {
    ...b,
    xAxis: { ...b.xAxis, data: x },
    series: series.map((s, i) => ({
      name: s.name,
      type: 'line',
      data: s.data,
      smooth: s.smooth !== false,
      symbol: 'none',
      lineStyle: { width: 1.6, color: s.color || SERIES_PALETTE[i], type: s.dashed ? 'dashed' : 'solid' },
      itemStyle: { color: s.color || SERIES_PALETTE[i] },
      areaStyle: s.area
        ? {
            color: {
              type: 'linear',
              x: 0,
              y: 0,
              x2: 0,
              y2: 1,
              colorStops: [
                { offset: 0, color: hexA(s.color || SERIES_PALETTE[i], 0.32) },
                { offset: 1, color: hexA(s.color || SERIES_PALETTE[i], 0.02) },
              ],
            },
          }
        : undefined,
      ...s.extra,
    })),
  };
}

/** 柱状图（可堆叠）。series: [{ name, data, color, stack }] */
export function barOption({ x, series, legend = true, yName = '', horizontal = false, grid }) {
  const b = base({ legend, yName, grid });
  const option = {
    ...b,
    xAxis: { ...b.xAxis, data: x },
    series: series.map((s, i) => ({
      name: s.name,
      type: 'bar',
      data: s.data,
      stack: s.stack,
      barMaxWidth: s.barMaxWidth || 18,
      itemStyle: { color: s.color || SERIES_PALETTE[i], borderRadius: [1, 1, 0, 0] },
      ...s.extra,
    })),
  };
  if (horizontal) {
    option.xAxis = { ...b.yAxis, type: 'value' };
    option.yAxis = { ...b.xAxis, type: 'category', data: x, inverse: true };
  }
  return option;
}

/** 帕累托图：柱（数量） + 折线（累计占比），停机原因分析的标准表达。 */
export function paretoOption({ categories, values, cumulative, yName = '分钟' }) {
  const b = base({ legend: true, yName });
  return {
    ...b,
    xAxis: { ...b.xAxis, data: categories, axisLabel: { color: CHART_COLORS.dim, fontSize: 10, interval: 0, rotate: 18 } },
    yAxis: [
      { ...b.yAxis, name: yName },
      {
        ...b.yAxis,
        name: '累计 %',
        max: 100,
        min: 0,
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '停机时长',
        type: 'bar',
        data: values,
        barMaxWidth: 26,
        itemStyle: { color: CHART_COLORS.idle },
      },
      {
        name: '累计占比',
        type: 'line',
        yAxisIndex: 1,
        data: cumulative,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { color: CHART_COLORS.primary, width: 1.6 },
        itemStyle: { color: CHART_COLORS.primary },
      },
    ],
  };
}

/** 半圆仪表盘：OEE / 开动率这类「达成率」。 */
export function gaugeOption({ value, max = 100, unit = '%', label = '', target = null, tone = 'primary' }) {
  const color = CHART_COLORS[tone] || CHART_COLORS.primary;
  return {
    backgroundColor: 'transparent',
    series: [
      {
        type: 'gauge',
        startAngle: 210,
        endAngle: -30,
        min: 0,
        max,
        radius: '96%',
        center: ['50%', '62%'],
        progress: { show: true, width: 10, itemStyle: { color } },
        axisLine: {
          lineStyle: {
            width: 10,
            color: [[1, '#1a2431']],
          },
        },
        pointer: { show: false },
        axisTick: { show: true, distance: -16, length: 4, lineStyle: { color: '#2b3742', width: 1 } },
        splitLine: { show: true, distance: -20, length: 8, lineStyle: { color: '#3a4b5e', width: 1.4 } },
        axisLabel: { show: true, distance: -34, color: CHART_COLORS.mute, fontSize: 9, fontFamily: numFont },
        detail: {
          valueAnimation: true,
          offsetCenter: [0, '-6%'],
          fontSize: 26,
          fontFamily: numFont,
          fontWeight: 700,
          color: CHART_COLORS.text,
          formatter: (v) => `${Number(v).toFixed(1)}${unit}`,
        },
        title: {
          offsetCenter: [0, '24%'],
          color: CHART_COLORS.dim,
          fontSize: 11,
        },
        data: [{ value, name: `${label}${target ? `   目标 ${target}${unit}` : ''}` }],
      },
    ],
  };
}

/** 分类占比（环形）：缺陷类型分布等。 */
export function ringOption({ data, centerLabel = '' }) {
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: 'rgba(11,17,24,0.96)',
      borderColor: CHART_COLORS.line,
      textStyle: { color: CHART_COLORS.text, fontSize: 12, fontFamily: numFont },
    },
    legend: {
      orient: 'vertical',
      right: 4,
      top: 'center',
      itemWidth: 9,
      itemHeight: 6,
      textStyle: { color: CHART_COLORS.dim, fontSize: 11 },
    },
    series: [
      {
        type: 'pie',
        radius: ['52%', '74%'],
        center: ['36%', '50%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#131b24', borderWidth: 2 },
        label: { show: false },
        labelLine: { show: false },
        data: data.map((d, i) => ({
          ...d,
          itemStyle: { color: d.color || SERIES_PALETTE[i % SERIES_PALETTE.length] },
        })),
        ...(centerLabel
          ? {}
          : {}),
      },
    ],
  };
}

/**
 * SPC 控制图（I-MR 的单值图）：数据点 + 控制限 + 规格限。
 * 控制限用虚线区分于规格限（实线），符合质量工程师的看图习惯。
 */
export function spcOption({ x, values, ucl, lcl, mean, usl, lsl }) {
  const b = base({ legend: true, yName: '测量值' });
  const markLine = (value, label, color, type) => ({
    yAxis: value,
    lineStyle: { color, type, width: type === 'solid' ? 1.6 : 1 },
    label: {
      formatter: `${label} ${Number(value).toFixed(3)}`,
      position: 'insideEndTop',
      color,
      fontSize: 10,
      fontFamily: numFont,
    },
  });

  return {
    ...b,
    grid: { left: 62, right: 24, top: 34, bottom: 26 },
    xAxis: { ...b.xAxis, data: x, boundaryGap: false },
    series: [
      {
        name: '实测值',
        type: 'line',
        data: values,
        symbol: 'circle',
        symbolSize: 5,
        lineStyle: { width: 1.6, color: CHART_COLORS.cyan },
        itemStyle: { color: CHART_COLORS.cyan },
        markLine: {
          silent: true,
          symbol: 'none',
          // 规格限可能缺失（特性没录公差时后端返回 null），不能画成 0 的那条线
          data: [
            usl === null || usl === undefined ? null : markLine(usl, 'USL', CHART_COLORS.stop, 'solid'),
            lsl === null || lsl === undefined ? null : markLine(lsl, 'LSL', CHART_COLORS.stop, 'solid'),
            ucl === null || ucl === undefined ? null : markLine(ucl, 'UCL', CHART_COLORS.idle, 'dashed'),
            lcl === null || lcl === undefined ? null : markLine(lcl, 'LCL', CHART_COLORS.idle, 'dashed'),
            mean === null || mean === undefined ? null : markLine(mean, 'CL', CHART_COLORS.primary, 'dotted'),
          ].filter(Boolean),
        },
        ...(mean !== undefined ? {} : {}),
      },
    ],
  };
}

/** hex + alpha → rgba() */
export function hexA(hex, alpha) {
  const h = hex.replace('#', '');
  const r = parseInt(h.slice(0, 2), 16);
  const g = parseInt(h.slice(2, 4), 16);
  const b = parseInt(h.slice(4, 6), 16);
  return `rgba(${r},${g},${b},${alpha})`;
}
