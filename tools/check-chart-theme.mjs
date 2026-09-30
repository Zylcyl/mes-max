/**
 * 图表主题换色的可复现断言。
 *
 * 背景：ECharts 画在 canvas 上，用不了 CSS 变量，所以浅色主题下要把已构建的 option 里的中性色
 * （轴线、网格、文字、tooltip 底色）替换成浅色版本，见 utils/chart.js 的 swapColorsForTheme()。
 * 这段逻辑错了表现是「轴标签看不见 / 网格糊成一片」，页面却不会报错，因此单独用脚本钉住。
 *
 * 用法：node tools/check-chart-theme.mjs
 */
import { pathToFileURL } from 'node:url';
import path from 'node:path';

const chartUrl = pathToFileURL(path.resolve('frontend/src/utils/chart.js')).href;
const { swapColorsForTheme, CHART_COLORS } = await import(chartUrl);

let pass = 0;
let fail = 0;
const check = (name, ok, detail) => {
  if (ok) pass++;
  else fail++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  → ${detail}` : ''}`);
};

const neutral = { axis: '#8b9db0', grid: '#223040', text: '#dbe6f0', tooltip: 'rgba(11,17,24,0.96)' };
const light = swapColorsForTheme(neutral, true);
check('轴线标签色变深（浅底可读）', light.axis === '#55677a', `${neutral.axis} → ${light.axis}`);
check('网格线变浅', light.grid === '#cfd9e3', `${neutral.grid} → ${light.grid}`);
check('正文文字反转', light.text === '#22303c', `${neutral.text} → ${light.text}`);
check('tooltip 底色变白', light.tooltip === 'rgba(255,255,255,0.97)', `${neutral.tooltip} → ${light.tooltip}`);

const status = { run: CHART_COLORS.run, stop: CHART_COLORS.stop, idle: CHART_COLORS.idle, primary: CHART_COLORS.primary };
const statusLight = swapColorsForTheme(status, true);
check('状态色保持不变', JSON.stringify(status) === JSON.stringify(statusLight),
  `run=${statusLight.run} stop=${statusLight.stop}`);

const dark = swapColorsForTheme(neutral, false);
check('深色主题原样返回', JSON.stringify(dark) === JSON.stringify(neutral));

const option = {
  series: [{ data: [1, 2, 3], itemStyle: { color: '#223040' }, markLine: { data: [{ lineStyle: { color: '#8b9db0' } }] } }],
  xAxis: { axisLabel: { color: '#8b9db0' } },
};
const deep = swapColorsForTheme(option, true);
check('嵌套 series/markLine 一并替换',
  deep.series[0].itemStyle.color === '#cfd9e3' && deep.series[0].markLine.data[0].lineStyle.color === '#55677a',
  `itemStyle=${deep.series[0].itemStyle.color}`);
check('数值数据未被误改', JSON.stringify(deep.series[0].data) === '[1,2,3]');
check('原始 option 未被就地修改（避免污染 computed 缓存）', option.series[0].itemStyle.color === '#223040');

console.log(`\n合计 ${pass}/${pass + fail} 通过`);
process.exit(fail === 0 ? 0 : 1);
