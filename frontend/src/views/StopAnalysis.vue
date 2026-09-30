<script setup>
/**
 * 停机分析：把机加工车间最贵的东西 —— 机床停机时间 —— 按原因、机台、日期拆开看。
 * 帕累托图是现场最有效的改进工具：先干掉占 80% 时长的那两三个原因。
 */
import { computed, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndKpi from '@/components/ind/IndKpi.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndPager from '@/components/ind/IndPager.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndChart from '@/components/ind/IndChart.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndField from '@/components/ind/IndField.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import { api } from '@/api';
import { barOption, CHART_COLORS, lineOption, paretoOption } from '@/utils/chart';
import { STOP_REASON } from '@/utils/status';
import { dateTime, minutes, num } from '@/utils/format';
import { useToast } from '@/stores/toast';

const toast = useToast();
const days = ref(7);
const stats = ref({ byReason: [], byType: [], byMachine: [], byDay: [] });
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(true);
const machineFilter = ref('');
const machines = ref([]);

const pareto = computed(() =>
  paretoOption({
    categories: (stats.value.byReason || []).map((r) => r.reason),
    values: (stats.value.byReason || []).map((r) => r.minutes),
    cumulative: (stats.value.byReason || []).map((r) => r.cumulative),
  }),
);

const trend = computed(() =>
  lineOption({
    x: (stats.value.byDay || []).map((d) => d.day.slice(5)),
    series: [{ name: '停机时长(分钟)', data: (stats.value.byDay || []).map((d) => d.minutes), color: CHART_COLORS.stop, area: true }],
    legend: false,
    yName: '分钟',
  }),
);

const byMachine = computed(() =>
  barOption({
    x: (stats.value.byMachine || []).slice(0, 10).map((m) => m.machine),
    series: [{ name: '停机时长(分钟)', data: (stats.value.byMachine || []).slice(0, 10).map((m) => m.minutes), color: CHART_COLORS.idle }],
    legend: false,
    horizontal: true,
    grid: { left: 78, right: 20, top: 12, bottom: 22 },
  }),
);

const typeSplit = computed(() => {
  const list = stats.value.byType || [];
  return barOption({
    x: list.map((t) => t.type),
    series: [{ name: '停机时长(分钟)', data: list.map((t) => t.minutes), color: CHART_COLORS.primary }],
    legend: false,
    yName: '分钟',
  });
});

const unplannedRate = computed(() => {
  const list = stats.value.byType || [];
  const totalMin = list.reduce((s, t) => s + Number(t.minutes || 0), 0);
  const unplanned = list.filter((t) => t.type === '非计划停机').reduce((s, t) => s + Number(t.minutes || 0), 0);
  return totalMin ? Math.round((unplanned / totalMin) * 1000) / 10 : 0;
});

const avgDuration = computed(() => (stats.value.count ? Math.round((stats.value.totalMinutes / stats.value.count) * 10) / 10 : 0));

const columns = [
  { key: 'machineCode', title: '机台', width: '78px', kind: 'mono' },
  { key: 'orderNo', title: '工单', width: '128px', kind: 'mono', format: (r) => r.orderNo || '—' },
  { key: 'reasonCode', title: '停机原因', width: '96px', format: (r) => STOP_REASON[r.reasonCode] || r.reasonCode || '—' },
  { key: 'stopType', title: '类别', width: '84px', kind: 'tag', tone: (r) => (r.stopType === '计划停机' ? 'primary' : 'stop') },
  { key: 'startTime', title: '开始', width: '128px', kind: 'datetime' },
  { key: 'endTime', title: '结束', width: '128px', kind: 'datetime' },
  { key: 'durationMin', title: '时长', width: '74px', align: 'right', kind: 'mono', format: (r) => minutes(r.durationMin) },
  { key: 'handler', title: '处理人', width: '74px', format: (r) => r.handler || '—' },
  { key: 'shiftName', title: '班次', width: '64px', format: (r) => r.shiftName || '—' },
  { key: 'reasonDesc', title: '说明', minWidth: '220px', format: (r) => r.reasonDesc || '—' },
];

async function loadStats() {
  stats.value = await api.stop.statistics(days.value);
}

async function loadRows() {
  loading.value = true;
  try {
    const res = await api.stop.page({
      page: page.value,
      size: size.value,
      machineId: machineFilter.value,
      sort: 'startTime',
      order: 'desc',
    });
    rows.value = res.records || [];
    total.value = res.total || 0;
  } finally {
    loading.value = false;
  }
}

async function closeStop(row) {
  await api.stop.close(row.id, { remark: '运维台手动结束' });
  toast.ok('停机已结束');
  await Promise.all([loadStats(), loadRows()]);
}

onMounted(async () => {
  machines.value = await api.masterdata.machine.list().catch(() => []);
  await Promise.all([loadStats(), loadRows()]);
});
</script>

<template>
  <div class="page">
    <div class="kpi-row">
      <IndKpi label="停机总时长" :value="stats.totalHours" unit="h" :digits="1" tone="stop" foot="近 7 天全部机台" />
      <IndKpi label="停机次数" :value="stats.count" unit="次" tone="idle" foot="含计划与非计划" />
      <IndKpi label="平均单次停机" :value="avgDuration" unit="min" :digits="1" tone="cyan" foot="总时长 / 次数" />
      <IndKpi label="非计划停机占比" :value="unplannedRate" unit="%" :digits="1" tone="maint" foot="越高越需要改善" />
      <IndKpi label="TOP 原因" :value="stats.byReason?.[0]?.reason || '—'" tone="primary" :foot="`占 ${stats.byReason?.[0]?.ratio || 0}% 的停机时长`" />
    </div>

    <div class="grid-2">
      <IndPanel title="停机原因帕累托分析" sub="PARETO ANALYSIS" :body-style="{ padding: '10px' }">
        <template #tools>
          <IndSelect v-model="days" :options="[7, 14, 30]" style="width: 96px" @change="loadStats" />
        </template>
        <IndChart :option="pareto" height="236" />
      </IndPanel>
      <IndPanel title="停机时长趋势" sub="DAILY TREND" :body-style="{ padding: '10px' }">
        <IndChart :option="trend" height="236" />
      </IndPanel>
    </div>

    <div class="grid-2">
      <IndPanel title="机台停机时长排名" sub="BY MACHINE" :body-style="{ padding: '10px' }">
        <IndChart :option="byMachine" height="200" />
      </IndPanel>
      <IndPanel title="计划 / 非计划停机构成" sub="PLANNED VS UNPLANNED" :body-style="{ padding: '10px' }">
        <IndChart :option="typeSplit" height="200" />
      </IndPanel>
    </div>

    <IndPanel title="停机明细" sub="STOPPAGE RECORDS" flush>
      <template #tools>
        <IndField label="机台" width="150px">
          <IndSelect
            v-model="machineFilter"
            :options="machines.map((m) => ({ value: m.id, label: `${m.macCode} ${m.macName}` }))"
            placeholder="全部机台"
            @change="loadRows"
          />
        </IndField>
        <IndButton variant="ghost" size="sm" @click="loadRows">刷新</IndButton>
      </template>
      <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
        <IndTable :columns="columns" :rows="rows" :loading="loading" dense empty-text="暂无停机记录">
          <template #cell="{ row, col }">
            <template v-if="col.key === 'stopType'">
              <IndTag :tone="row.stopType === '计划停机' ? 'primary' : 'stop'">{{ row.stopType }}</IndTag>
            </template>
            <template v-else-if="col.kind === 'datetime'">
              <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
            </template>
            <template v-else-if="col.key === 'actions'">
              <button class="link" @click="closeStop(row)">结束停机</button>
            </template>
            <template v-else>
              <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : row[col.key] || '—' }}</span>
            </template>
          </template>
        </IndTable>
        <IndPager :total="total" v-model:page="page" v-model:size="size" @change="loadRows" />
      </div>
    </IndPanel>
  </div>
</template>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.link { background: none; border: none; padding: 0; color: var(--c-primary); cursor: pointer; font-size: 12px; }
</style>
