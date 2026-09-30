<script setup>
/**
 * 刀具管理：台账 + 寿命链路 + 刃磨/对刀 + 反向追溯。
 *
 * 刀具是 CNC 车间唯一「既影响质量、又影响停机、还直接烧钱」的对象，
 * 所以这里把「装刀 → 加工累计 → 预警 → 卸刀 → 刃磨复位」做成一条闭环。
 */
import { computed, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndKpi from '@/components/ind/IndKpi.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndPager from '@/components/ind/IndPager.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndBar from '@/components/ind/IndBar.vue';
import IndModal from '@/components/ind/IndModal.vue';
import IndDrawer from '@/components/ind/IndDrawer.vue';
import IndChart from '@/components/ind/IndChart.vue';
import { api } from '@/api';
import { CHART_COLORS, barOption } from '@/utils/chart';
import { label as statusLabel, tone as statusTone } from '@/utils/status';
import { dateTime, num } from '@/utils/format';
import { useToast } from '@/stores/toast';
import { useAuth } from '@/stores/auth';

const toast = useToast();
const auth = useAuth();
const overview = ref({ byStatus: {}, byType: {}, warning: [] });
const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(true);
const keyword = ref('');
const statusFilter = ref('');
const machines = ref([]);

const actOpen = ref(false);
const actMode = ref('mount');
const actForm = ref({});
const detailOpen = ref(false);
const detail = ref({ tool: null, usage: [], grinds: [], measures: [] });
const traceOpen = ref(false);
const trace = ref(null);

const columns = [
  { key: 'toolCode', title: '刀具编号', width: '110px', kind: 'mono' },
  { key: 'toolName', title: '名称', width: '114px' },
  { key: 'toolType', title: '类型', width: '86px' },
  { key: 'spec', title: '规格', minWidth: '140px' },
  { key: 'materialGrade', title: '材质', width: '82px' },
  { key: 'coating', title: '涂层', width: '68px' },
  { key: 'machineCode', title: '所在机台', width: '86px', kind: 'mono', format: (r) => r.machineCode || '库房' },
  { key: 'offsetNo', title: '刀补号', width: '68px', kind: 'mono', format: (r) => r.offsetNo || '—' },
  { key: 'usedLifeMin', title: '已用寿命', width: '82px', align: 'right', kind: 'mono', format: (r) => num(r.usedLifeMin) + ' min' },
  { key: 'stdLifeMin', title: '标准寿命', width: '82px', align: 'right', kind: 'mono', format: (r) => num(r.stdLifeMin) + ' min' },
  { key: 'lifeRatio', title: '寿命占用', minWidth: '140px', kind: 'progress', value: (r) => Math.min(100, r.lifeRatio) },
  { key: 'status', title: '状态', width: '86px', kind: 'tag' },
  { key: 'location', title: '存放位置', width: '94px', format: (r) => r.location || '—' },
  { key: 'actions', title: '操作', width: '210px', align: 'right' },
];

const typeOption = computed(() => {
  const map = overview.value.byType || {};
  return barOption({
    x: Object.keys(map),
    series: [{ name: '数量', data: Object.values(map), color: CHART_COLORS.primary }],
    legend: false,
    horizontal: true,
    grid: { left: 76, right: 20, top: 12, bottom: 22 },
  });
});

async function loadOverview() {
  overview.value = await api.tool.overview();
}

async function loadRows() {
  loading.value = true;
  try {
    const res = await api.tool.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value,
      status: statusFilter.value,
      sort: 'lifeRatio',
      order: 'desc',
    });
    rows.value = res.records || [];
    total.value = res.total || 0;
  } finally {
    loading.value = false;
  }
}

function openAction(mode, row) {
  actMode.value = mode;
  actForm.value = {
    toolId: row.id,
    toolCode: row.toolCode,
    machineId: row.machineId || '',
    offsetNo: row.offsetNo || '',
    useMinutes: '',
    partQty: '',
    unmountReason: '计划换刀',
    grindAmount: 0.05,
    cost: '',
    afterSpec: row.spec,
    remark: '',
  };
  actOpen.value = true;
}

const actTitle = computed(() => {
  switch (actMode.value) {
    case 'mount': return '装刀登记';
    case 'unmount': return '卸刀登记';
    case 'grind': return '刃磨登记';
    default: return '对刀测量登记';
  }
});

async function submitAction() {
  const f = actForm.value;
  if (actMode.value === 'mount') {
    await api.tool.mount({ toolId: f.toolId, machineId: f.machineId, offsetNo: f.offsetNo });
    toast.ok(`刀具 ${f.toolCode} 已装到机台`);
  } else if (actMode.value === 'unmount') {
    const r = await api.tool.unmount({
      toolId: f.toolId,
      useMinutes: f.useMinutes === '' ? null : Number(f.useMinutes),
      partQty: f.partQty === '' ? 0 : Number(f.partQty),
      unmountReason: f.unmountReason,
    });
    toast[r.status === '待刃磨' ? 'error' : 'ok'](`卸刀完成，当前寿命 ${r.lifeRatio}%（${r.status}）`);
  } else if (actMode.value === 'grind') {
    await api.tool.grind({
      toolId: f.toolId,
      grindAmount: Number(f.grindAmount),
      afterSpec: f.afterSpec,
      cost: f.cost === '' ? null : Number(f.cost),
      remark: f.remark,
    });
    toast.ok('刃磨登记完成，寿命计数器已复位');
  } else {
    await api.tool.measure({
      toolId: f.toolId,
      machineId: f.machineId || null,
      offsetNo: f.offsetNo,
      radiusValue: f.radiusValue ? Number(f.radiusValue) : null,
      lengthValue: f.lengthValue ? Number(f.lengthValue) : null,
      deviation: f.deviation ? Number(f.deviation) : null,
      remark: f.remark,
    });
    toast.ok('对刀数据已登记');
  }
  actOpen.value = false;
  await Promise.all([loadOverview(), loadRows()]);
}

async function openDetail(row) {
  const [usage, grinds, measures] = await Promise.all([
    api.tool.usage(row.id),
    api.tool.grindRecords(row.id),
    api.tool.measureRecords(row.id),
  ]);
  detail.value = { tool: row, usage: usage || [], grinds: grinds || [], measures: measures || [] };
  detailOpen.value = true;
}

async function openTrace(row) {
  trace.value = await api.tool.trace(row.toolCode);
  traceOpen.value = true;
}

onMounted(async () => {
  machines.value = await api.masterdata.machine.list().catch(() => []);
  await Promise.all([loadOverview(), loadRows()]);
});
</script>

<template>
  <div class="page">
    <div class="kpi-row">
      <IndKpi label="刀具总数" :value="overview.total" unit="把" tone="primary" foot="含在库与在机" />
      <IndKpi label="在机使用" :value="overview.inUse" unit="把" tone="run" led="run" foot="已挂载到机台" />
      <IndKpi label="库存可用" :value="overview.inStock" unit="把" tone="cyan" foot="可直接装刀" />
      <IndKpi label="寿命预警" :value="overview.warningCount" unit="把" tone="idle" led="idle" foot="寿命占用 ≥ 85%" />
      <IndKpi label="待刃磨" :value="overview.pendingGrind" unit="把" tone="stop" led="stop" foot="寿命已耗尽，禁止装刀" />
      <IndKpi label="平均寿命占用" :value="overview.avgLifeRatio" unit="%" :digits="1" tone="maint" />
    </div>

    <div class="grid-2">
      <IndPanel title="寿命预警刀具" sub="LIFE WARNING" flush>
        <template #tools>
          <IndTag :tone="overview.warningCount ? 'stop' : 'run'" led>{{ overview.warning?.length || 0 }} 把</IndTag>
        </template>
        <div class="warn-list">
          <div v-if="!overview.warning?.length" class="mute" style="padding: 20px; text-align: center">全部刀具寿命正常</div>
          <div v-for="t in overview.warning || []" :key="t.id" class="warn-item">
            <div class="between">
              <span class="center gap-2">
                <b class="mono">{{ t.toolCode }}</b>
                <span class="mute" style="font-size: 11px">{{ t.toolName }} · {{ t.spec }}</span>
              </span>
              <IndTag :tone="t.lifeRatio >= 100 ? 'stop' : 'idle'">{{ num(t.lifeRatio, 1) }}%</IndTag>
            </div>
            <div class="center gap-2" style="margin-top: 5px">
              <IndBar :value="Math.min(100, t.lifeRatio)" :tone="t.lifeRatio >= 100 ? 'stop' : 'idle'" height="7" />
              <span class="mono mute" style="font-size: 11px; width: 130px; text-align: right">
                {{ num(t.usedLifeMin) }} / {{ num(t.stdLifeMin) }} min
              </span>
            </div>
            <div class="center gap-2" style="margin-top: 6px">
              <span class="mute" style="font-size: 11px">{{ t.machineCode || '库房' }} · 刀补 {{ t.offsetNo || '—' }}</span>
            <div v-if="auth.can('tool')" style="margin-left: auto; display: flex; gap: 6px">
                <IndButton size="sm" variant="ghost" @click="openDetail(t)">履历</IndButton>
                <IndButton size="sm" variant="warn" @click="openAction('grind', t)">刃磨</IndButton>
              </div>
            </div>
          </div>
        </div>
      </IndPanel>

      <IndPanel title="刀具类型构成" sub="BY TYPE" :body-style="{ padding: '10px' }">
        <IndChart :option="typeOption" height="230" />
      </IndPanel>
    </div>

    <IndPanel title="刀具台账" sub="TOOL LEDGER" flush>
      <template #tools>
        <IndField label="关键字" width="200px">
          <IndInput v-model="keyword" placeholder="刀具编号 / 名称 / 规格" @enter="loadRows" />
        </IndField>
        <IndField label="状态" width="118px">
          <IndSelect
            v-model="statusFilter"
            :options="['在用', '在库', '预警', '待刃磨', '已报废']"
            placeholder="全部"
            @change="loadRows"
          />
        </IndField>
        <IndButton size="sm" @click="loadRows">查询</IndButton>
      </template>
      <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
        <IndTable :columns="columns" :rows="rows" :loading="loading" dense empty-text="暂无刀具记录">
          <template #cell="{ row, col }">
            <template v-if="col.key === 'lifeRatio'">
              <div class="center gap-2">
                <IndBar
                  :value="Math.min(100, row.lifeRatio)"
                  :tone="row.lifeRatio >= 100 ? 'stop' : row.lifeRatio >= 85 ? 'idle' : 'run'"
                  height="7"
                />
                <span class="mono" style="font-size: 11px; width: 44px; text-align: right">{{ num(row.lifeRatio, 1) }}%</span>
              </div>
            </template>
            <template v-else-if="col.key === 'status'">
              <IndTag :tone="statusTone('tool', row.status)" led>{{ statusLabel('tool', row.status) }}</IndTag>
            </template>
            <template v-else-if="col.key === 'actions'">
              <div v-if="auth.can('tool')" class="row-actions">
                <button v-if="!row.machineId" class="link" @click="openAction('mount', row)">装刀</button>
                <button v-else class="link" @click="openAction('unmount', row)">卸刀</button>
                <button class="link" @click="openAction('grind', row)">刃磨</button>
                <button class="link" @click="openAction('measure', row)">对刀</button>
                <button class="link" @click="openTrace(row)">追溯</button>
              </div>
              <button v-else class="link" @click="openTrace(row)">追溯</button>
            </template>
            <template v-else>
              <span :class="{ mono: col.kind === 'mono' }">
                {{ col.format ? col.format(row) : (row[col.key] ?? '—') }}
              </span>
            </template>
          </template>
        </IndTable>
        <IndPager :total="total" v-model:page="page" v-model:size="size" @change="loadRows" />
      </div>
    </IndPanel>

    <IndModal v-model="actOpen" :title="actTitle" :width="560">
      <div class="confirm">
        <div class="confirm__row"><span>刀具</span><b class="mono">{{ actForm.toolCode }}</b></div>
      </div>
      <div v-if="actMode === 'mount'" class="form-grid">
        <IndField label="装到机台" required>
          <IndSelect
            v-model="actForm.machineId"
            :options="machines.map((m) => ({ value: m.id, label: `${m.macCode} ${m.macName}` }))"
            placeholder="请选择机台"
          />
        </IndField>
        <IndField label="刀补号" required>
          <IndInput v-model="actForm.offsetNo" placeholder="例如 H01" />
        </IndField>
      </div>
      <div v-else-if="actMode === 'unmount'" class="form-grid">
        <IndField label="本次加工时长（分钟）">
          <IndInput v-model="actForm.useMinutes" type="number" placeholder="留空按装刀时间自动计算" />
        </IndField>
        <IndField label="本次加工件数">
          <IndInput v-model="actForm.partQty" type="number" />
        </IndField>
        <IndField label="卸刀原因">
          <IndSelect
            v-model="actForm.unmountReason"
            :options="['计划换刀', '寿命到期', '换型', '加工质量异常']"
            placeholder="计划换刀"
          />
        </IndField>
      </div>
      <div v-else-if="actMode === 'grind'" class="form-grid">
        <IndField label="刃磨量（mm）">
          <IndInput v-model="actForm.grindAmount" type="number" step="0.01" />
        </IndField>
        <IndField label="刃磨费用（元）">
          <IndInput v-model="actForm.cost" type="number" />
        </IndField>
        <IndField label="刃磨后规格">
          <IndInput v-model="actForm.afterSpec" />
        </IndField>
      </div>
      <div v-else class="form-grid">
        <IndField label="机台">
          <IndSelect
            v-model="actForm.machineId"
            :options="machines.map((m) => ({ value: m.id, label: `${m.macCode} ${m.macName}` }))"
            placeholder="请选择机台"
          />
        </IndField>
        <IndField label="刀补号">
          <IndInput v-model="actForm.offsetNo" placeholder="例如 H01" />
        </IndField>
        <IndField label="刀尖圆弧半径 R">
          <IndInput v-model="actForm.radiusValue" type="number" step="0.001" />
        </IndField>
        <IndField label="刀具长度 L">
          <IndInput v-model="actForm.lengthValue" type="number" step="0.001" />
        </IndField>
        <IndField label="测量偏差">
          <IndInput v-model="actForm.deviation" type="number" step="0.0001" />
        </IndField>
      </div>
      <IndField label="备注" style="margin-top: 12px">
        <IndInput v-model="actForm.remark" placeholder="选填" />
      </IndField>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="primary" @click="submitAction">确认登记</IndButton>
      </template>
    </IndModal>

    <IndDrawer v-model="detailOpen" title="刀具履历" :width="760">
      <div v-if="detail.tool">
        <div class="tool-head">
          <div>
            <b class="mono" style="font-size: 17px">{{ detail.tool.toolCode }}</b>
            <span class="dim">{{ detail.tool.toolName }} · {{ detail.tool.spec }}</span>
          </div>
          <IndTag :tone="statusTone('tool', detail.tool.status)" led>{{ statusLabel('tool', detail.tool.status) }}</IndTag>
        </div>
        <div class="tool-kpis">
          <div><span>标准寿命</span><b class="mono">{{ num(detail.tool.stdLifeMin) }} min</b></div>
          <div><span>已用寿命</span><b class="mono">{{ num(detail.tool.usedLifeMin) }} min</b></div>
          <div><span>寿命占用</span><b class="mono">{{ num(detail.tool.lifeRatio, 1) }}%</b></div>
          <div><span>加工件数</span><b class="mono">{{ num(detail.tool.usedQty) }} 件</b></div>
          <div><span>采购单价</span><b class="mono">¥{{ num(detail.tool.unitPrice, 2) }}</b></div>
        </div>

        <div class="ind-section-title" style="margin-top: 14px">使用记录（{{ detail.usage.length }}）</div>
        <table class="mini-table">
          <thead>
            <tr><th>装刀时间</th><th>卸刀时间</th><th>机台</th><th>工单</th><th>程序</th><th>时长</th><th>件数</th><th>卸刀原因</th></tr>
          </thead>
          <tbody>
            <tr v-for="u in detail.usage" :key="u.id">
              <td class="mono">{{ dateTime(u.mountTime) }}</td>
              <td class="mono">{{ dateTime(u.unmountTime) }}</td>
              <td class="mono">{{ u.machineCode || '—' }}</td>
              <td class="mono">{{ u.orderNo || '—' }}</td>
              <td class="mono">{{ u.ncProgramNo || '—' }}</td>
              <td class="mono">{{ num(u.useMinutes) }}min</td>
              <td class="mono">{{ num(u.partQty) }}</td>
              <td>{{ u.unmountReason || '—' }}</td>
            </tr>
          </tbody>
        </table>

        <div class="ind-section-title" style="margin-top: 14px">刃磨记录（{{ detail.grinds.length }}）</div>
        <table class="mini-table">
          <thead><tr><th>刃磨单号</th><th>时间</th><th>刃磨量</th><th>刃磨人</th><th>结果</th><th>费用</th></tr></thead>
          <tbody>
            <tr v-for="g in detail.grinds" :key="g.id">
              <td class="mono">{{ g.grindNo }}</td>
              <td class="mono">{{ dateTime(g.grindTime) }}</td>
              <td class="mono">{{ num(g.grindAmount, 3) }}mm</td>
              <td>{{ g.grinder }}</td>
              <td><IndTag :tone="g.result === '合格' ? 'run' : 'stop'">{{ g.result }}</IndTag></td>
              <td class="mono">¥{{ num(g.cost, 2) }}</td>
            </tr>
          </tbody>
        </table>

        <div class="ind-section-title" style="margin-top: 14px">对刀记录（{{ detail.measures.length }}）</div>
        <table class="mini-table">
          <thead><tr><th>时间</th><th>机台</th><th>刀补号</th><th>半径 R</th><th>长度 L</th><th>偏差</th><th>测量人</th></tr></thead>
          <tbody>
            <tr v-for="m in detail.measures" :key="m.id">
              <td class="mono">{{ dateTime(m.measureTime) }}</td>
              <td class="mono">{{ m.machineCode || '—' }}</td>
              <td class="mono">{{ m.offsetNo }}</td>
              <td class="mono">{{ num(m.radiusValue, 3) }}</td>
              <td class="mono">{{ num(m.lengthValue, 3) }}</td>
              <td class="mono">{{ num(m.deviation, 4) }}</td>
              <td>{{ m.measurer }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </IndDrawer>

    <IndModal v-model="traceOpen" title="刀具反向追溯" :width="760">
      <div v-if="trace" class="trace-head">
        <div><span>刀具</span><b class="mono">{{ trace.toolCode }}</b></div>
        <div><span>影响序列号</span><b class="mono">{{ trace.snCount }} 个</b></div>
        <div><span>涉及工单</span><b class="mono">{{ trace.orderCount }} 张</b></div>
        <div><span>使用机台</span><b class="mono">{{ (trace.machines || []).join(' / ') }}</b></div>
      </div>
      <div class="ind-section-title">受影响的工单</div>
      <div class="tag-wrap">
        <IndTag v-for="o in trace?.orders || []" :key="o" tone="primary">{{ o }}</IndTag>
      </div>
      <div class="ind-section-title" style="margin-top: 14px">追溯明细（{{ trace?.records?.length || 0 }} 条）</div>
      <table class="mini-table">
        <thead><tr><th>序列号</th><th>工单</th><th>工序</th><th>机台</th><th>程序</th><th>操作工</th><th>时间</th></tr></thead>
        <tbody>
          <tr v-for="r in (trace?.records || []).slice(0, 60)" :key="r.id">
            <td class="mono">{{ r.snCode }}</td>
            <td class="mono">{{ r.orderNo }}</td>
            <td>OP{{ r.seqNo }} {{ r.procName }}</td>
            <td class="mono">{{ r.machineCode }}</td>
            <td class="mono">{{ r.ncProgramNo || '—' }}</td>
            <td>{{ r.operator || '—' }}</td>
            <td class="mono">{{ dateTime(r.startTime) }}</td>
          </tr>
        </tbody>
      </table>
    </IndModal>
  </div>
</template>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.grid-2 { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(0, 1fr); gap: 12px; }
.row-actions { display: flex; gap: 9px; justify-content: flex-end; }
.link { background: none; border: none; padding: 0; color: var(--c-primary); cursor: pointer; font-size: 12px; }
.link:hover { text-decoration: underline; }

.warn-list { padding: 8px 10px; display: flex; flex-direction: column; gap: 8px; }
.warn-item { border: 1px solid var(--line); border-left: 3px solid var(--c-idle); padding: 8px 10px; background: var(--sheen); }

.confirm { display: flex; flex-direction: column; gap: 5px; margin-bottom: 12px; }
.confirm__row { display: flex; gap: 10px; font-size: 12.5px; }
.confirm__row span { color: var(--text-mute); width: 76px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }

.tool-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.tool-kpis {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  margin-top: 12px;
  padding: 12px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.tool-kpis > div { display: flex; flex-direction: column; gap: 3px; font-size: 11px; }
.tool-kpis span { color: var(--text-mute); }
.tool-kpis b { font-size: 15px; color: var(--text-strong); }

.mini-table { width: 100%; font-size: 11.5px; }
.mini-table th {
  text-align: left;
  padding: 5px 7px;
  font-size: 11px;
  color: var(--text-mute);
  border-bottom: 1px solid var(--line-strong);
  background: var(--table-head-bg);
}
.mini-table td { padding: 5px 7px; border-bottom: 1px solid var(--line-soft); }

.trace-head { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; padding: 10px 12px; border: 1px solid var(--line); background: rgba(10, 162, 255, 0.05); }
.trace-head > div { display: flex; flex-direction: column; gap: 2px; font-size: 12px; }
.trace-head span { color: var(--text-mute); }
.tag-wrap { display: flex; flex-wrap: wrap; gap: 6px; }
</style>
