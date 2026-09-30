<script setup>
/**
 * 设备维保：点检 / 保养 / 故障三件套。
 *
 * 三个可靠性指标在这里闭环：
 *   MTTR（平均修复时间）— 修得快不快
 *   MTBF（平均故障间隔）— 坏得勤不勤
 *   点检异常率 — 隐患发现得早不早
 */
import { computed, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndTabs from '@/components/ind/IndTabs.vue';
import IndKpi from '@/components/ind/IndKpi.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndPager from '@/components/ind/IndPager.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndLed from '@/components/ind/IndLed.vue';
import IndModal from '@/components/ind/IndModal.vue';
import IndChart from '@/components/ind/IndChart.vue';
import { api } from '@/api';
import { CHART_COLORS, barOption } from '@/utils/chart';
import { FAULT_TYPE_TONE, label as statusLabel, tone as statusTone } from '@/utils/status';
import { dateTime, minutes, num } from '@/utils/format';
import { useToast } from '@/stores/toast';
import { useAuth } from '@/stores/auth';

const toast = useToast();
const auth = useAuth();
const tab = ref('check');
const overview = ref({});
const machines = ref([]);
const machineId = ref('');
const sheet = ref(null);
const checkRecords = ref({ rows: [], total: 0, page: 1, size: 20, loading: true, result: '' });
const plans = ref({ rows: [], total: 0, page: 1, size: 20, loading: true });
const orders = ref({ rows: [], total: 0, page: 1, size: 20, loading: true, status: '' });
const faults = ref({ rows: [], total: 0, page: 1, size: 20, loading: true, status: '' });
const faultOpen = ref(false);
const closeOpen = ref(false);
const faultForm = ref({});

const planColumns = [
  { key: 'planCode', title: '计划编号', width: '112px', kind: 'mono' },
  { key: 'planName', title: '保养计划', minWidth: '170px' },
  { key: 'machineCode', title: '机台', width: '84px', kind: 'mono' },
  { key: 'maintType', title: '类型', width: '92px' },
  { key: 'periodType', title: '周期', width: '70px', format: (r) => `${r.periodValue}${r.periodType}` },
  { key: 'lastDate', title: '上次保养', width: '106px', kind: 'date' },
  { key: 'nextDate', title: '下次保养', width: '106px', kind: 'date' },
  { key: 'principal', title: '负责人', width: '82px' },
  { key: 'status', title: '状态', width: '82px', kind: 'tag', tone: (r) => (r.status === '逾期' ? 'stop' : 'run') },
  { key: 'actions', title: '操作', width: '104px', align: 'right' },
];

const orderColumns = [
  { key: 'maintNo', title: '保养单号', width: '128px', kind: 'mono' },
  { key: 'machineCode', title: '机台', width: '84px', kind: 'mono' },
  { key: 'maintType', title: '类型', width: '92px' },
  { key: 'maintainer', title: '负责人', width: '82px' },
  { key: 'planStart', title: '计划开始', width: '128px', kind: 'datetime' },
  { key: 'actualEnd', title: '实际完成', width: '128px', kind: 'datetime' },
  { key: 'downMinutes', title: '停机时长', width: '84px', align: 'right', kind: 'mono', format: (r) => (r.downMinutes ? minutes(r.downMinutes) : '—') },
  { key: 'cost', title: '费用', width: '78px', align: 'right', kind: 'mono', format: (r) => (r.cost ? '¥' + num(r.cost, 2) : '—') },
  { key: 'status', title: '状态', width: '82px', kind: 'tag', tone: (r) => (r.status === '已完成' ? 'run' : 'idle') },
  { key: 'actions', title: '操作', width: '86px', align: 'right' },
];

const faultColumns = [
  { key: 'faultNo', title: '故障单号', width: '120px', kind: 'mono' },
  { key: 'machineCode', title: '机台', width: '80px', kind: 'mono' },
  { key: 'faultType', title: '类型', width: '86px', kind: 'tag' },
  { key: 'faultCode', title: '报警码', width: '82px', kind: 'mono', format: (r) => r.faultCode || '—' },
  { key: 'faultDesc', title: '故障现象', minWidth: '200px' },
  { key: 'reportTime', title: '报修时间', width: '128px', kind: 'datetime' },
  { key: 'respondTime', title: '响应时间', width: '128px', kind: 'datetime' },
  { key: 'downMinutes', title: '停机时长', width: '84px', align: 'right', kind: 'mono', format: (r) => (r.downMinutes ? minutes(r.downMinutes) : '—') },
  { key: 'repairUser', title: '维修人', width: '74px', format: (r) => r.repairUser || '—' },
  { key: 'status', title: '状态', width: '82px', kind: 'tag', tone: (r) => (r.status === '已修复' ? 'run' : r.status === '维修中' ? 'idle' : 'stop') },
  { key: 'actions', title: '操作', width: '118px', align: 'right' },
];

const checkColumns = [
  { key: 'checkTime', title: '点检时间', width: '128px', kind: 'datetime' },
  { key: 'machineCode', title: '机台', width: '80px', kind: 'mono' },
  { key: 'itemName', title: '点检项目', minWidth: '150px' },
  { key: 'checker', title: '点检人', width: '74px' },
  { key: 'shiftName', title: '班次', width: '64px' },
  { key: 'result', title: '结果', width: '76px', kind: 'tag' },
  { key: 'abnormalDesc', title: '异常描述', minWidth: '200px', format: (r) => r.abnormalDesc || '—' },
  { key: 'handleStatus', title: '处理状态', width: '86px', format: (r) => r.handleStatus || '—' },
];

const faultTypeOption = computed(() =>
  barOption({
    x: Object.keys(overview.value.faultByType || {}),
    series: [{ name: '故障次数', data: Object.values(overview.value.faultByType || {}), color: CHART_COLORS.stop }],
    legend: false,
    yName: '次',
    grid: { left: 40, right: 14, top: 14, bottom: 24 },
  }),
);

const downOption = computed(() => {
  const pairs = Object.entries(overview.value.downByMachine || {}).sort((a, b) => b[1] - a[1]).slice(0, 10);
  return barOption({
    x: pairs.map((p) => p[0]),
    series: [{ name: '停机时长(分钟)', data: pairs.map((p) => Math.round(p[1])), color: CHART_COLORS.idle }],
    legend: false,
    horizontal: true,
    grid: { left: 78, right: 20, top: 12, bottom: 22 },
  });
});

async function loadOverview() {
  overview.value = await api.equipment.overview(30);
}

async function loadSheet() {
  if (!machineId.value) return;
  sheet.value = await api.equipment.checkSheet(machineId.value);
}

async function submitCheck(row, result) {
  await api.equipment.saveCheckRecord({
    machineId: Number(machineId.value),
    machineCode: sheet.value.machine.macCode,
    itemId: row.item.id,
    itemName: row.item.itemName,
    checkTime: new Date().toISOString().slice(0, 19),
    shiftName: '白班',
    result,
    abnormalDesc: result === '异常' ? '现场点检发现异常，已通知设备工程师' : null,
    handleStatus: result === '异常' ? '待处理' : null,
  });
  toast[result === '异常' ? 'warn' : 'ok'](`${row.item.itemName}：${result}`);
  await Promise.all([loadSheet(), loadCheckRecords()]);
}

async function loadCheckRecords() {
  checkRecords.value.loading = true;
  try {
    const res = await api.equipment.checkRecords({
      page: checkRecords.value.page,
      size: checkRecords.value.size,
      machineId: machineId.value || undefined,
      result: checkRecords.value.result || undefined,
    });
    checkRecords.value.rows = res.records || [];
    checkRecords.value.total = res.total || 0;
  } finally {
    checkRecords.value.loading = false;
  }
}

async function loadPlans() {
  plans.value.loading = true;
  try {
    const res = await api.equipment.maintPlans({ page: plans.value.page, size: plans.value.size });
    plans.value.rows = res.records || [];
    plans.value.total = res.total || 0;
  } finally {
    plans.value.loading = false;
  }
}

async function loadOrders() {
  orders.value.loading = true;
  try {
    const res = await api.equipment.maintOrders({
      page: orders.value.page,
      size: orders.value.size,
      status: orders.value.status || undefined,
    });
    orders.value.rows = res.records || [];
    orders.value.total = res.total || 0;
  } finally {
    orders.value.loading = false;
  }
}

async function loadFaults() {
  faults.value.loading = true;
  try {
    const res = await api.equipment.faults({
      page: faults.value.page,
      size: faults.value.size,
      status: faults.value.status || undefined,
    });
    faults.value.rows = res.records || [];
    faults.value.total = res.total || 0;
  } finally {
    faults.value.loading = false;
  }
}

async function generatePlan(row) {
  const o = await api.equipment.generateMaint(row.id);
  toast.ok(`已生成保养工单 ${o.maintNo}`);
  await Promise.all([loadOrders(), loadOverview()]);
}

async function finishOrder(row) {
  await api.equipment.finishMaint(row.id, { result: '保养完成，设备精度校验合格' });
  toast.ok(`保养工单 ${row.maintNo} 已完成`);
  await Promise.all([loadOrders(), loadPlans()]);
}

async function respondFault(row) {
  await api.equipment.respondFault(row.id);
  toast.warn(`已响应故障 ${row.faultNo}，开始维修`);
  await loadFaults();
}

function openCloseFault(row) {
  faultForm.value = {
    id: row.id,
    faultNo: row.faultNo,
    cause: row.cause || '',
    solution: row.solution || '',
    spareParts: row.spareParts || '',
  };
  closeOpen.value = true;
}

async function submitClose() {
  await api.equipment.closeFault(faultForm.value.id, faultForm.value);
  toast.ok('故障已修复关闭，同步生成停机记录');
  closeOpen.value = false;
  await Promise.all([loadFaults(), loadOverview()]);
}

function openNewFault() {
  faultForm.value = {
    machineId: machineId.value || (machines.value[0] && machines.value[0].id),
    machineCode: '',
    faultType: '机械',
    faultDesc: '',
    faultCode: '',
  };
  faultOpen.value = true;
}

async function submitFault() {
  const m = machines.value.find((x) => String(x.id) === String(faultForm.value.machineId));
  await api.equipment.saveFault({
    machineId: Number(faultForm.value.machineId),
    machineCode: m ? m.macCode : null,
    faultType: faultForm.value.faultType,
    faultDesc: faultForm.value.faultDesc,
    faultCode: faultForm.value.faultCode,
  });
  toast.warn('故障已登记，请设备工程师响应');
  faultOpen.value = false;
  await loadFaults();
}

onMounted(async () => {
  const [ov, mac] = await Promise.all([api.equipment.overview(30), api.masterdata.machine.list()]);
  overview.value = ov;
  machines.value = mac || [];
  machineId.value = String(machines.value[0]?.id || '');
  await Promise.all([loadSheet(), loadCheckRecords(), loadPlans(), loadOrders(), loadFaults()]);
});
</script>

<template>
  <div class="page">
    <div class="kpi-row">
      <IndKpi label="在册设备" :value="overview.machineCount" unit="台" tone="primary" />
      <IndKpi label="近 30 天故障" :value="overview.faultCount" unit="次" tone="stop" foot="含已修复与处理中" />
      <IndKpi label="MTTR 平均修复时间" :value="overview.mttr" unit="min" :digits="1" tone="idle" foot="总停机 / 故障次数" />
      <IndKpi label="MTBF 平均故障间隔" :value="overview.mtbf" unit="min" :digits="1" tone="run" foot="累计运行 / 故障次数" />
      <IndKpi label="点检异常率" :value="overview.checkAbnormalRate" unit="%" :digits="1" tone="maint" :foot="`异常 ${overview.abnormalCheckCount} / 点检 ${overview.checkCount}`" />
      <IndKpi label="保养到期" :value="overview.maintDue?.length || 0" unit="项" tone="idle" foot="未来 7 天内到期" />
    </div>

    <div class="grid-2">
      <IndPanel title="故障类型分布" sub="FAULT TYPE" :body-style="{ padding: '10px' }">
        <IndChart :option="faultTypeOption" height="186" />
      </IndPanel>
      <IndPanel title="各机台停机时长" sub="DOWNTIME BY MACHINE" :body-style="{ padding: '10px' }">
        <IndChart :option="downOption" height="186" />
      </IndPanel>
    </div>

    <IndTabs
      v-model="tab"
      :tabs="[
        { key: 'check', label: '班前点检' },
        { key: 'maint', label: '保养计划与工单' },
        { key: 'fault', label: '故障台账', badge: overview.faultCount },
      ]"
    />

    <!-- 点检 -->
    <template v-if="tab === 'check'">
      <div class="grid-2">
        <IndPanel title="班前点检表" :sub="sheet ? `${sheet.machine.macCode} ${sheet.machine.macName}` : ''">
          <template #tools>
            <IndSelect
              v-model="machineId"
              :options="machines.map((m) => ({ value: String(m.id), label: `${m.macCode} ${m.macName}` }))"
              style="width: 200px"
              @change="loadSheet"
            />
          </template>
          <div v-if="sheet" class="sheet">
            <div class="sheet__progress">
              <span>已点检 <b class="mono">{{ sheet.doneCount }}</b> / {{ sheet.total }} 项</span>
              <IndTag :tone="sheet.doneCount >= sheet.total ? 'run' : 'idle'">
                {{ sheet.doneCount >= sheet.total ? '点检完成' : '待点检' }}
              </IndTag>
            </div>
            <div v-for="r in sheet.rows" :key="r.item.id" class="sheet__row">
              <div class="sheet__item">
                <span class="center gap-2">
                  <IndLed :tone="r.checked ? (r.record.result === '异常' ? 'stop' : 'run') : 'offline'" />
                  <b>{{ r.item.itemName }}</b>
                  <IndTag v-if="r.item.isKey === 1" tone="alarm">关键</IndTag>
                </span>
                <span class="mute" style="font-size: 11px">
                  {{ r.item.checkPart }} · {{ r.item.checkMethod }} · 标准：{{ r.item.standard }}
                </span>
              </div>
              <div class="sheet__actions">
                <IndButton size="sm" variant="ok" @click="submitCheck(r, '正常')">正常</IndButton>
                <IndButton size="sm" variant="danger" @click="submitCheck(r, '异常')">异常</IndButton>
              </div>
            </div>
          </div>
        </IndPanel>
        <IndPanel title="点检记录" sub="CHECK RECORDS" flush>
          <template #tools>
            <IndSelect
              v-model="checkRecords.result"
              :options="['正常', '异常']"
              placeholder="全部结果"
              style="width: 120px"
              @change="loadCheckRecords"
            />
          </template>
          <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
            <IndTable :columns="checkColumns" :rows="checkRecords.rows" :loading="checkRecords.loading" dense empty-text="暂无点检记录">
              <template #cell="{ row, col }">
                <template v-if="col.key === 'result'">
                  <IndTag :tone="row.result === '异常' ? 'stop' : 'run'" led>{{ row.result }}</IndTag>
                </template>
                <template v-else-if="col.kind === 'datetime'">
                  <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
                </template>
                <template v-else>
                  <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
                </template>
              </template>
            </IndTable>
            <IndPager :total="checkRecords.total" v-model:page="checkRecords.page" v-model:size="checkRecords.size" @change="loadCheckRecords" />
          </div>
        </IndPanel>
      </div>
    </template>

    <!-- 保养 -->
    <template v-else-if="tab === 'maint'">
      <IndPanel title="保养计划" sub="MAINTENANCE PLAN" flush>
        <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
          <IndTable :columns="planColumns" :rows="plans.rows" :loading="plans.loading" dense empty-text="暂无保养计划">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'actions'">
                <button v-if="auth.can('equipment')" class="link" @click="generatePlan(row)">生成保养工单</button>
                <span v-else class="mute">—</span>
              </template>
              <template v-else-if="col.key === 'status'">
                <IndTag :tone="row.status === '逾期' ? 'stop' : 'run'" led>{{ row.status }}</IndTag>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
              </template>
            </template>
          </IndTable>
          <IndPager :total="plans.total" v-model:page="plans.page" v-model:size="plans.size" @change="loadPlans" />
        </div>
      </IndPanel>

      <IndPanel title="保养工单" sub="MAINTENANCE ORDER" flush>
        <template #tools>
          <IndSelect
            v-model="orders.status"
            :options="['待执行', '已完成']"
            placeholder="全部状态"
            style="width: 130px"
            @change="loadOrders"
          />
        </template>
        <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
          <IndTable :columns="orderColumns" :rows="orders.rows" :loading="orders.loading" dense empty-text="暂无保养工单">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'actions'">
                <button v-if="row.status !== '已完成' && auth.can('equipment')" class="link" @click="finishOrder(row)">完成保养</button>
                <span v-else class="mute">—</span>
              </template>
              <template v-else-if="col.key === 'status'">
                <IndTag :tone="row.status === '已完成' ? 'run' : 'idle'" led>{{ row.status }}</IndTag>
              </template>
              <template v-else-if="col.kind === 'datetime'">
                <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
              </template>
            </template>
          </IndTable>
          <IndPager :total="orders.total" v-model:page="orders.page" v-model:size="orders.size" @change="loadOrders" />
        </div>
      </IndPanel>
    </template>

    <!-- 故障 -->
    <template v-else>
      <IndPanel title="故障台账" sub="FAULT LEDGER" flush>
        <template #tools>
          <IndSelect
            v-model="faults.status"
            :options="['待维修', '维修中', '已修复']"
            placeholder="全部状态"
            style="width: 130px"
            @change="loadFaults"
          />
          <IndButton v-if="auth.can('equipment')" size="sm" variant="danger" @click="openNewFault">＋ 登记故障</IndButton>
        </template>
        <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
          <IndTable :columns="faultColumns" :rows="faults.rows" :loading="faults.loading" dense empty-text="暂无故障记录">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'actions'">
                <div class="row-actions">
                  <button v-if="row.status === '待维修' && auth.can('equipment')" class="link" @click="respondFault(row)">响应</button>
                  <button v-if="row.status !== '已修复' && auth.can('equipment')" class="link" @click="openCloseFault(row)">修复关闭</button>
                  <span v-if="row.status === '已修复'" class="mute">已闭环</span>
                </div>
              </template>
              <template v-else-if="col.key === 'faultType'">
                <IndTag :tone="FAULT_TYPE_TONE[row.faultType] || 'plain'">{{ row.faultType }}</IndTag>
              </template>
              <template v-else-if="col.key === 'status'">
                <IndTag :tone="row.status === '已修复' ? 'run' : row.status === '维修中' ? 'idle' : 'stop'" led>
                  {{ row.status }}
                </IndTag>
              </template>
              <template v-else-if="col.kind === 'datetime'">
                <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
              </template>
            </template>
          </IndTable>
          <IndPager :total="faults.total" v-model:page="faults.page" v-model:size="faults.size" @change="loadFaults" />
        </div>
      </IndPanel>
    </template>

    <IndModal v-model="faultOpen" title="登记设备故障" :width="580">
      <div class="form-grid">
        <IndField label="机台" required>
          <IndSelect
            v-model="faultForm.machineId"
            :options="machines.map((m) => ({ value: m.id, label: `${m.macCode} ${m.macName}` }))"
            placeholder="请选择机台"
          />
        </IndField>
        <IndField label="故障类型">
          <IndSelect
            v-model="faultForm.faultType"
            :options="['机械', '电气', '数控系统', '液压', '气动', '刀具干涉']"
            placeholder="机械"
          />
        </IndField>
        <IndField label="报警码">
          <IndInput v-model="faultForm.faultCode" placeholder="例如 SV0432" />
        </IndField>
      </div>
      <IndField label="故障现象" style="margin-top: 12px" required>
        <IndInput v-model="faultForm.faultDesc" placeholder="例如：主轴异响，转速不稳，加工表面出现振纹" />
      </IndField>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="danger" @click="submitFault">提交故障单</IndButton>
      </template>
    </IndModal>

    <IndModal v-model="closeOpen" title="故障修复关闭" :width="620">
      <div class="confirm">
        <div class="confirm__row"><span>故障单号</span><b class="mono">{{ faultForm.faultNo }}</b></div>
      </div>
      <IndField label="故障原因" required>
        <IndInput v-model="faultForm.cause" placeholder="例如：丝杠预紧力不足，轴承磨损" />
      </IndField>
      <IndField label="处理措施" style="margin-top: 12px" required>
        <IndInput v-model="faultForm.solution" placeholder="例如：更换角接触轴承并重新预紧，复检定位精度" />
      </IndField>
      <IndField label="更换备件" style="margin-top: 12px">
        <IndInput v-model="faultForm.spareParts" placeholder="例如：角接触轴承 7205 ×2" />
      </IndField>
      <p class="mute" style="font-size: 11.5px; margin-top: 10px">
        关闭后系统会自动按「报修时间 → 修复时间」生成一条停机记录，保证 OEE 的时间损失口径一致。
      </p>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="ok" @click="submitClose">确认修复</IndButton>
      </template>
    </IndModal>
  </div>
</template>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.link { background: none; border: none; padding: 0; color: var(--c-primary); cursor: pointer; font-size: 12px; }
.link:hover { text-decoration: underline; }
.row-actions { display: flex; gap: 10px; justify-content: flex-end; }

.sheet { display: flex; flex-direction: column; gap: 8px; }
.sheet__progress { display: flex; align-items: center; gap: 12px; font-size: 12.5px; padding-bottom: 8px; border-bottom: 1px solid var(--line); }
.sheet__row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px dashed var(--line-soft);
}
.sheet__item { display: flex; flex-direction: column; gap: 3px; flex: 1; min-width: 0; font-size: 12.5px; }
.sheet__actions { display: flex; gap: 7px; }

.confirm { display: flex; flex-direction: column; gap: 5px; margin-bottom: 12px; }
.confirm__row { display: flex; gap: 10px; font-size: 12.5px; }
.confirm__row span { color: var(--text-mute); width: 76px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
</style>
