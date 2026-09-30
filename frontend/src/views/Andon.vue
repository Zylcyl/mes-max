<script setup>
/** 安灯呼叫看板：一线呼叫 → 班组响应 → 升级 → 关闭，全过程留痕并沉淀效率指标。 */
import { computed, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndKpi from '@/components/ind/IndKpi.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndPager from '@/components/ind/IndPager.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndLed from '@/components/ind/IndLed.vue';
import IndChart from '@/components/ind/IndChart.vue';
import IndModal from '@/components/ind/IndModal.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import { api } from '@/api';
import { barOption, CHART_COLORS, ringOption } from '@/utils/chart';
import { ANDON_TYPE_TONE, label as statusLabel, tone as statusTone } from '@/utils/status';
import { dateTime, fromNow, minutes, num } from '@/utils/format';
import { useToast } from '@/stores/toast';

const toast = useToast();
const stats = ref({ byType: {}, byLevel: {}, byMachine: {}, open: [] });
const history = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(true);
const resolveOpen = ref(false);
const resolveForm = ref({ id: null, remark: '' });
const tick = ref(0);

const open = computed(() => stats.value.open || []);

const typeOption = computed(() =>
  ringOption({
    data: Object.entries(stats.value.byType || {}).map(([name, value], i) => ({
      name,
      value,
      color: CHART_COLORS[ANDON_TYPE_TONE[name] || 'primary'] || CHART_COLORS.primary,
    })),
  }),
);

const levelOption = computed(() => {
  const map = stats.value.byLevel || {};
  return barOption({
    x: Object.keys(map),
    series: [{ name: '呼叫次数', data: Object.values(map), color: CHART_COLORS.idle }],
    legend: false,
    yName: '次',
    grid: { left: 40, right: 14, top: 14, bottom: 22 },
  });
});

const machineOption = computed(() => {
  const pairs = Object.entries(stats.value.byMachine || {}).sort((a, b) => b[1] - a[1]).slice(0, 8);
  return barOption({
    x: pairs.map((p) => p[0]),
    series: [{ name: '呼叫次数', data: pairs.map((p) => p[1]), color: CHART_COLORS.stop }],
    legend: false,
    horizontal: true,
    grid: { left: 74, right: 20, top: 12, bottom: 22 },
  });
});

const columns = [
  { key: 'andonNo', title: '呼叫单号', width: '136px', kind: 'mono' },
  { key: 'andonType', title: '类型', width: '86px', kind: 'tag', tone: (r) => ANDON_TYPE_TONE[r.andonType] || 'plain' },
  { key: 'level', title: '等级', width: '58px', format: (r) => r.level },
  { key: 'machineCode', title: '机台', width: '78px', kind: 'mono', format: (r) => r.machineCode || '—' },
  { key: 'orderNo', title: '工单', width: '128px', kind: 'mono', format: (r) => r.orderNo || '—' },
  { key: 'content', title: '呼叫内容', minWidth: '200px' },
  { key: 'callUser', title: '呼叫', width: '70px' },
  { key: 'callTime', title: '呼叫时间', width: '120px', kind: 'datetime' },
  { key: 'respondUser', title: '响应人', width: '70px', format: (r) => r.respondUser || '—' },
  { key: 'respondMinutes', title: '响应', width: '62px', align: 'right', kind: 'mono', format: (r) => respMinutes(r) },
  { key: 'durationMin', title: '关闭时长', width: '76px', align: 'right', kind: 'mono', format: (r) => (r.durationMin ? minutes(r.durationMin) : '—') },
  { key: 'status', title: '状态', width: '80px', kind: 'tag', tone: (r) => statusTone('andon', r.status) },
  { key: 'actions', title: '操作', width: '120px', align: 'right' },
];

function respMinutes(row) {
  if (!row.callTime || !row.respondTime) return '—';
  const a = new Date(String(row.callTime).replace(' ', 'T')).getTime();
  const b = new Date(String(row.respondTime).replace(' ', 'T')).getTime();
  return `${Math.max(0, Math.round((b - a) / 60000))}m`;
}

function elapsed(rec) {
  void tick.value; // 让「已等待时长」随定时器刷新
  const t = new Date(String(rec.callTime).replace(' ', 'T')).getTime();
  const m = Math.max(0, Math.floor((Date.now() - t) / 60000));
  if (m < 60) return `${m} 分钟`;
  return `${Math.floor(m / 60)} 小时 ${m % 60} 分`;
}

async function loadStats() {
  stats.value = await api.andon.statistics(7);
}

async function loadHistory() {
  loading.value = true;
  try {
    const res = await api.andon.page({ page: page.value, size: size.value, sort: 'callTime', order: 'desc' });
    history.value = res.records || [];
    total.value = res.total || 0;
  } finally {
    loading.value = false;
  }
}

async function respond(rec) {
  await api.andon.respond(rec.id);
  toast.ok(`已响应呼叫 ${rec.andonNo}`);
  await Promise.all([loadStats(), loadHistory()]);
}

async function escalate(rec) {
  const r = await api.andon.escalate(rec.id);
  toast.warn(`呼叫 ${rec.andonNo} 已升级至 ${r.level}`);
  await Promise.all([loadStats(), loadHistory()]);
}

function openResolve(rec) {
  resolveForm.value = { id: rec.id, remark: '' };
  resolveOpen.value = true;
}

async function submitResolve() {
  await api.andon.resolve(resolveForm.value.id, { remark: resolveForm.value.remark });
  toast.ok('呼叫已关闭');
  resolveOpen.value = false;
  await Promise.all([loadStats(), loadHistory()]);
}

onMounted(async () => {
  await Promise.all([loadStats(), loadHistory()]);
  setInterval(() => (tick.value = Date.now()), 30000);
  setInterval(() => loadStats().catch(() => {}), 30000);
});
</script>

<template>
  <div class="page">
    <div class="kpi-row">
      <IndKpi label="未关闭呼叫" :value="stats.openCount" unit="条" tone="stop" led="alarm" foot="需要立即处理" />
      <IndKpi label="近 7 天呼叫" :value="stats.total" unit="次" tone="primary" foot="全部类型合计" />
      <IndKpi label="平均响应时长" :value="stats.avgRespondMinutes" unit="min" :digits="1" tone="idle" foot="呼叫 → 有人响应" />
      <IndKpi label="平均关闭时长" :value="stats.avgCloseMinutes" unit="min" :digits="1" tone="cyan" foot="呼叫 → 问题解决" />
      <IndKpi label="升级呼叫" :value="(stats.byLevel && stats.byLevel['二级']) || 0" unit="条" tone="maint" foot="需车间/厂部介入" />
    </div>

    <div class="andon-board">
      <IndPanel title="未关闭呼叫" :sub="`${open.length} 条待处理`" flush>
        <div class="open-list">
          <div v-if="!open.length" class="mute" style="padding: 30px; text-align: center">当前没有未关闭的安灯呼叫，现场运行正常</div>
          <article
            v-for="a in open"
            :key="a.id"
            class="call"
            :class="`call--${ANDON_TYPE_TONE[a.andonType] || 'plain'}`"
          >
            <header class="call__head">
              <IndLed :tone="a.status === '待响应' ? 'stop' : 'idle'" size="lg" />
              <b>{{ a.andonType }}</b>
              <IndTag :tone="a.level === '三级' ? 'stop' : a.level === '二级' ? 'idle' : 'plain'">{{ a.level }}</IndTag>
              <span class="call__no mono">{{ a.andonNo }}</span>
              <span class="call__timer mono">{{ elapsed(a) }}</span>
            </header>
            <div class="call__content">{{ a.content }}</div>
            <div class="call__meta mono">
              <span>机台 {{ a.machineCode || '—' }}</span>
              <span>工单 {{ a.orderNo || '—' }}</span>
              <span>呼叫 {{ a.callUser }} · {{ dateTime(a.callTime) }}</span>
              <span v-if="a.respondUser">响应 {{ a.respondUser }} · {{ dateTime(a.respondTime) }}</span>
            </div>
            <footer class="call__actions">
              <IndButton size="sm" variant="primary" :disabled="a.status !== '待响应'" @click="respond(a)">响应</IndButton>
              <IndButton size="sm" variant="warn" @click="escalate(a)">升级</IndButton>
              <IndButton size="sm" variant="ok" @click="openResolve(a)">关闭呼叫</IndButton>
            </footer>
          </article>
        </div>
      </IndPanel>

      <div class="andon-side">
        <IndPanel title="呼叫类型分布" sub="BY TYPE">
          <IndChart :option="typeOption" height="150" />
        </IndPanel>
        <IndPanel title="呼叫等级分布" sub="BY LEVEL">
          <IndChart :option="levelOption" height="140" />
        </IndPanel>
        <IndPanel title="呼叫机台 TOP" sub="BY MACHINE">
          <IndChart :option="machineOption" height="150" />
        </IndPanel>
      </div>
    </div>

    <IndPanel title="安灯历史记录" sub="ANDON HISTORY" flush>
      <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
        <IndTable :columns="columns" :rows="history" :loading="loading" dense empty-text="暂无安灯记录">
          <template #cell="{ row, col }">
            <template v-if="col.key === 'actions'">
              <div class="row-actions">
                <button v-if="row.status === '待响应'" class="link" @click="respond(row)">响应</button>
                <button v-if="row.status !== '已关闭'" class="link" @click="openResolve(row)">关闭</button>
                <span v-if="row.status === '已关闭'" class="mute">—</span>
              </div>
            </template>
            <template v-else-if="col.kind === 'tag'">
              <IndTag :tone="col.tone(row)">{{ row[col.key] }}</IndTag>
            </template>
            <template v-else-if="col.kind === 'datetime'">
              <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
            </template>
            <template v-else-if="col.format">
              <span :class="{ mono: col.kind === 'mono' }">{{ col.format(row) }}</span>
            </template>
            <template v-else>
              <span :class="{ mono: col.kind === 'mono' }">{{ row[col.key] || '—' }}</span>
            </template>
          </template>
        </IndTable>
        <IndPager :total="total" v-model:page="page" v-model:size="size" @change="loadHistory" />
      </div>
    </IndPanel>

    <IndModal v-model="resolveOpen" title="关闭安灯呼叫" :width="520">
      <IndField label="处理说明" required>
        <IndInput v-model="resolveForm.remark" placeholder="例如：已更换刀片并重新对刀，首件复检合格" />
      </IndField>
      <p class="mute" style="font-size: 11.5px; margin-top: 10px">
        关闭后系统会记录「呼叫 → 关闭」总时长，用于统计车间支持效率。
      </p>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="ok" @click="submitResolve">确认关闭</IndButton>
      </template>
    </IndModal>
  </div>
</template>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.andon-board { display: grid; grid-template-columns: minmax(0, 1.35fr) 400px; gap: 12px; min-height: 320px; }
.andon-side { display: grid; grid-template-rows: repeat(3, minmax(0, 1fr)); gap: 12px; }

.open-list { display: flex; flex-direction: column; gap: 8px; padding: 10px; }
.call {
  border: 1px solid var(--line);
  border-left: 3px solid var(--c-offline);
  background: var(--sheen);
  padding: 9px 11px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.call--stop { border-left-color: var(--c-stop); background: rgba(255, 77, 79, 0.07); }
.call--idle { border-left-color: var(--c-idle); background: rgba(245, 165, 36, 0.06); }
.call--maint { border-left-color: var(--c-maint); }
.call--info { border-left-color: var(--c-info); }
.call__head { display: flex; align-items: center; gap: 9px; font-size: 13px; }
.call__head b { color: var(--text-strong); }
.call__no { margin-left: auto; font-size: 11px; color: var(--text-mute); }
.call__timer { font-size: 13px; color: var(--c-idle); }
.call__content { font-size: 13px; color: var(--text); }
.call__meta { display: flex; flex-wrap: wrap; gap: 4px 18px; font-size: 11px; color: var(--text-mute); }
.call__actions { display: flex; gap: 8px; justify-content: flex-end; }

.row-actions { display: flex; gap: 10px; justify-content: flex-end; }
.link { background: none; border: none; padding: 0; color: var(--c-primary); cursor: pointer; font-size: 12px; }
.link:hover { text-decoration: underline; }
</style>
