<script setup>
/**
 * 统计报表：OEE / 产量 / 质量 / 工时。
 *
 * OEE 口径（与车间实际管理口径一致）：
 *   时间开动率 = 实际加工时间 /（实际加工时间 + 停机时间）
 *   性能开动率 = 理论节拍时间 / 实际加工时间，理论节拍取自工艺路线的单件工时定额
 *   合格率     = 合格数量 / 报工总数
 *   OEE        = 三者相乘
 */
import { computed, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndKpi from '@/components/ind/IndKpi.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndGauge from '@/components/ind/IndGauge.vue';
import IndChart from '@/components/ind/IndChart.vue';
import IndField from '@/components/ind/IndField.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndBar from '@/components/ind/IndBar.vue';
import { api } from '@/api';
import { CHART_COLORS, barOption, lineOption } from '@/utils/chart';
import { num, pct } from '@/utils/format';

const days = ref(7);
const oee = ref({ overall: {}, byMachine: [], byDay: [], byWorkCenter: [] });
const output = ref({ daily: [] });
const labor = ref({ operators: [], byShift: {} });
const loading = ref(true);

async function load() {
  loading.value = true;
  try {
    const [o, out, lab] = await Promise.all([api.stats.oee(days.value), api.stats.output(14), api.stats.labor(7)]);
    oee.value = o;
    output.value = out;
    labor.value = lab;
  } finally {
    loading.value = false;
  }
}

const oeeTrend = computed(() => {
  const list = oee.value.byDay || [];
  return lineOption({
    x: list.map((d) => d.label.slice(5)),
    series: [
      { name: 'OEE %', data: list.map((d) => d.oee), color: CHART_COLORS.run, area: true },
      { name: '时间开动率 %', data: list.map((d) => d.availability), color: CHART_COLORS.primary },
      { name: '性能开动率 %', data: list.map((d) => d.performance), color: CHART_COLORS.maint },
      { name: '合格率 %', data: list.map((d) => d.quality), color: CHART_COLORS.idle },
    ],
    yName: '%',
  });
});

const outputOption = computed(() => {
  const list = output.value.daily || [];
  return barOption({
    x: list.map((d) => d.day.slice(5)),
    series: [
      { name: '产量(件)', data: list.map((d) => d.qty), color: CHART_COLORS.primary },
      { name: '合格(件)', data: list.map((d) => d.qualified), color: CHART_COLORS.run },
      { name: '不良(件)', data: list.map((d) => d.defect), color: CHART_COLORS.stop, barMaxWidth: 9 },
    ],
    yName: '件',
  });
});

const shiftOption = computed(() => {
  const map = labor.value.byShift || {};
  return barOption({
    x: Object.keys(map),
    series: [{ name: '加工工时(分钟)', data: Object.values(map).map((v) => Math.round(v)), color: CHART_COLORS.cyan }],
    legend: false,
    yName: '分钟',
    grid: { left: 50, right: 14, top: 14, bottom: 24 },
  });
});

const machineColumns = [
  { key: 'label', title: '机台', width: '88px', kind: 'mono' },
  { key: 'machineName', title: '名称', minWidth: '130px', format: (r) => r.machineName || '手工/检测工位' },
  { key: 'cncType', title: '类型', width: '104px', format: (r) => r.cncType || '—' },
  { key: 'qty', title: '产量', width: '70px', kind: 'num' },
  { key: 'runMinutes', title: '加工工时', width: '90px', align: 'right', kind: 'mono', format: (r) => num(r.runMinutes / 60, 1) + 'h' },
  { key: 'stopMinutes', title: '停机工时', width: '90px', align: 'right', kind: 'mono', format: (r) => num(r.stopMinutes / 60, 1) + 'h' },
  { key: 'availability', title: '时间开动率', width: '104px', align: 'right', kind: 'mono', format: (r) => pct(r.availability) },
  { key: 'performance', title: '性能开动率', width: '104px', align: 'right', kind: 'mono', format: (r) => pct(r.performance) },
  { key: 'quality', title: '合格率', width: '86px', align: 'right', kind: 'mono', format: (r) => pct(r.quality) },
  { key: 'oee', title: 'OEE', width: '76px', align: 'right', kind: 'mono', format: (r) => pct(r.oee) },
  { key: 'target', title: '目标', width: '66px', align: 'right', kind: 'mono', format: (r) => pct(r.target, 0) },
  { key: 'gap', title: '差距', width: '76px', align: 'right', kind: 'mono', format: (r) => `${r.oee - r.target > 0 ? '+' : ''}${(r.oee - r.target).toFixed(1)}%` },
  { key: 'bar', title: '', minWidth: '110px', kind: 'progress', value: (r) => r.oee },
];

const wcColumns = [
  { key: 'label', title: '工作中心', minWidth: '150px' },
  { key: 'wcCode', title: '编号', width: '82px', kind: 'mono' },
  { key: 'qty', title: '产量', width: '70px', kind: 'num' },
  { key: 'runMinutes', title: '加工工时', width: '90px', align: 'right', kind: 'mono', format: (r) => num(r.runMinutes / 60, 1) + 'h' },
  { key: 'availability', title: '时间开动率', width: '104px', align: 'right', kind: 'mono', format: (r) => pct(r.availability) },
  { key: 'performance', title: '性能开动率', width: '104px', align: 'right', kind: 'mono', format: (r) => pct(r.performance) },
  { key: 'quality', title: '合格率', width: '86px', align: 'right', kind: 'mono', format: (r) => pct(r.quality) },
  { key: 'oee', title: 'OEE', width: '78px', align: 'right', kind: 'mono', format: (r) => pct(r.oee) },
  { key: 'bar', title: '', minWidth: '110px', kind: 'progress', value: (r) => r.oee },
];

const laborColumns = [
  { key: 'operator', title: '操作工', width: '90px' },
  { key: 'skillLevel', title: '技能等级', width: '88px', format: (r) => r.skillLevel || '—' },
  { key: 'teamName', title: '班组', width: '66px', format: (r) => r.teamName || '—' },
  { key: 'hours', title: '加工工时', width: '86px', align: 'right', kind: 'mono', format: (r) => num(r.hours, 1) + 'h' },
  { key: 'qty', title: '完成件数', width: '86px', align: 'right', kind: 'num' },
  { key: 'defect', title: '不良', width: '64px', align: 'right', kind: 'num' },
  { key: 'rate', title: '合格率', width: '82px', align: 'right', kind: 'mono', format: (r) => (r.rate === null ? '—' : pct(r.rate)) },
  { key: 'efficiency', title: '小时产出', width: '88px', align: 'right', kind: 'mono', format: (r) => num(r.efficiency, 1) + ' 件/h' },
  { key: 'bar', title: '', minWidth: '110px', kind: 'progress', value: (r) => Math.min(100, r.rate || 0) },
];

onMounted(load);
</script>

<template>
  <div class="page">
    <IndPanel plain>
      <div class="ind-toolbar">
        <IndField label="统计窗口" width="130px">
          <IndSelect v-model="days" :options="[1, 7, 14, 30]" placeholder="7" @change="load" />
        </IndField>
        <IndButton @click="load">重新统计</IndButton>
        <div class="ind-toolbar__spacer" />
        <span class="mute" style="font-size: 12px">
          OEE = 时间开动率 × 性能开动率 × 合格率；性能开动率的理论节拍取自工艺路线的单件工时定额
        </span>
      </div>
    </IndPanel>

    <div class="kpi-row">
      <IndKpi label="综合 OEE" :value="oee.overall.oee" unit="%" :digits="1" tone="run" led="run" :foot="`目标 85%`" />
      <IndKpi label="时间开动率" :value="oee.overall.availability" unit="%" :digits="1" tone="primary" foot="加工 /(加工+停机)" />
      <IndKpi label="性能开动率" :value="oee.overall.performance" unit="%" :digits="1" tone="maint" foot="理论节拍 / 实际用时" />
      <IndKpi label="合格率" :value="oee.overall.quality" unit="%" :digits="1" tone="idle" foot="合格数 / 报工总数" />
      <IndKpi label="累计产量" :value="output.finishedQty" unit="件" tone="cyan" :foot="`计划 ${num(output.planQty)} 件`" />
      <IndKpi label="计划完成率" :value="output.completionRate" unit="%" :digits="1" tone="primary" />
      <IndKpi label="交期达成率" :value="output.onTimeRate" unit="%" :digits="1" :tone="output.onTimeRate >= 95 ? 'run' : 'idle'" :foot="`逾期 ${output.tardyOrders} 张工单`" />
      <IndKpi label="累计停机" :value="oee.overall.stopMinutes ? Math.round(oee.overall.stopMinutes / 60 * 10) / 10 : 0" unit="h" :digits="1" tone="stop" />
    </div>

    <div class="grid-3">
      <IndPanel title="OEE 仪表" sub="OVERALL">
        <div class="gauge-wrap">
          <IndGauge :value="oee.overall.oee || 0" label="综合 OEE" :target="85" :height="170"
            :tone="(oee.overall.oee || 0) >= 85 ? 'run' : (oee.overall.oee || 0) >= 70 ? 'idle' : 'stop'" />
          <div class="gauge-rows">
            <div><span>时间开动率</span><b class="mono">{{ pct(oee.overall.availability) }}</b></div>
            <div><span>性能开动率</span><b class="mono">{{ pct(oee.overall.performance) }}</b></div>
            <div><span>合格率</span><b class="mono">{{ pct(oee.overall.quality) }}</b></div>
            <div><span>理论工时</span><b class="mono">{{ num(oee.overall.theoryMinutes / 60, 1) }}h</b></div>
            <div><span>实际工时</span><b class="mono">{{ num(oee.overall.runMinutes / 60, 1) }}h</b></div>
          </div>
        </div>
      </IndPanel>
      <IndPanel title="OEE 趋势" sub="TREND" :body-style="{ padding: '10px' }" class="span-2">
        <IndChart :option="oeeTrend" height="200" />
      </IndPanel>
    </div>

    <div class="grid-2">
      <IndPanel title="产量与质量趋势" sub="OUTPUT" :body-style="{ padding: '10px' }">
        <IndChart :option="outputOption" height="220" />
      </IndPanel>
      <IndPanel title="班次工时分布" sub="BY SHIFT" :body-style="{ padding: '10px' }">
        <IndChart :option="shiftOption" height="220" />
      </IndPanel>
    </div>

    <IndPanel title="机台 OEE 明细" sub="OEE BY MACHINE" flush>
      <div style="height: 100%; min-height: 0">
        <IndTable :columns="machineColumns" :rows="oee.byMachine || []" dense :loading="loading" empty-text="暂无数据">
          <template #cell="{ row, col }">
            <template v-if="col.key === 'bar'">
              <div class="center gap-2">
                <IndBar :value="row.oee" :tone="row.oee >= row.target ? 'run' : row.oee >= 60 ? 'idle' : 'stop'" height="7" />
                <span class="mono" style="font-size: 11px; width: 42px; text-align: right">{{ pct(row.oee) }}</span>
              </div>
            </template>
            <template v-else-if="col.key === 'gap'">
              <span class="mono" :class="row.oee - row.target >= 0 ? 'c-run' : 'c-stop'">{{ col.format(row) }}</span>
            </template>
            <template v-else>
              <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
            </template>
          </template>
        </IndTable>
      </div>
    </IndPanel>

    <div class="grid-2">
      <IndPanel title="工作中心 OEE" sub="BY WORK CENTER" flush>
        <div style="height: 100%; min-height: 0">
          <IndTable :columns="wcColumns" :rows="oee.byWorkCenter || []" dense empty-text="暂无数据">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'bar'">
                <div class="center gap-2">
                  <IndBar :value="row.oee" :tone="row.oee >= 75 ? 'run' : row.oee >= 55 ? 'idle' : 'stop'" height="7" />
                  <span class="mono" style="font-size: 11px; width: 42px; text-align: right">{{ pct(row.oee) }}</span>
                </div>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
              </template>
            </template>
          </IndTable>
        </div>
      </IndPanel>
      <IndPanel title="人员工时与产出" sub="LABOR" flush>
        <div style="height: 100%; min-height: 0">
          <IndTable :columns="laborColumns" :rows="(labor.operators || []).slice(0, 30)" dense empty-text="暂无数据">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'bar'">
                <div class="center gap-2">
                  <IndBar :value="row.rate || 0" tone="run" height="7" />
                  <span class="mono" style="font-size: 11px; width: 42px; text-align: right">{{ row.rate === null ? '—' : pct(row.rate) }}</span>
                </div>
              </template>
              <template v-else-if="col.key === 'operator'">
                <span class="strong">{{ row.operator }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
              </template>
            </template>
          </IndTable>
        </div>
      </IndPanel>
    </div>
  </div>
</template>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(8, 1fr); gap: 12px; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.grid-3 { display: grid; grid-template-columns: 1fr 2fr; gap: 12px; }
.span-2 { min-width: 0; }
.gauge-wrap { display: grid; grid-template-columns: 1fr 130px; align-items: center; gap: 8px; }
.gauge-rows { display: flex; flex-direction: column; gap: 7px; font-size: 12px; }
.gauge-rows > div { display: flex; justify-content: space-between; gap: 8px; color: var(--text-dim); }
.gauge-rows b { color: var(--text-strong); }
</style>
