<script setup>
/** 生产订单（车间工单）管理：下达、展开工序、齐套检查、执行跟踪。 */
import { computed, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndPager from '@/components/ind/IndPager.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndDrawer from '@/components/ind/IndDrawer.vue';
import IndModal from '@/components/ind/IndModal.vue';
import IndTimeline from '@/components/ind/IndTimeline.vue';
import IndBar from '@/components/ind/IndBar.vue';
import IndLed from '@/components/ind/IndLed.vue';
import { api } from '@/api';
import { ORDER_STATUS, PRIORITY, label as statusLabel, tone as statusTone, STEP_STATUS } from '@/utils/status';
import { dateTime, num, pct } from '@/utils/format';
import { useToast } from '@/stores/toast';
import { useAuth } from '@/stores/auth';

const toast = useToast();
const auth = useAuth();

const rows = ref([]);
const total = ref(0);
const page = ref(1);
const size = ref(20);
const loading = ref(true);
const keyword = ref('');
const statusFilter = ref('');
const priorityFilter = ref('');

const detailOpen = ref(false);
const detail = ref(null);
const kit = ref(null);
const editOpen = ref(false);
const form = ref({});
const materials = ref([]);
const routings = ref([]);
const customers = ref([]);

const STATUS_OPTIONS = Object.entries(ORDER_STATUS).map(([value, v]) => ({ value, label: v.label }));
const PRIORITY_OPTIONS = Object.keys(PRIORITY).map((k) => ({ value: k, label: k }));

const columns = [
  { key: 'orderNo', title: '工单号', width: '132px', kind: 'mono' },
  { key: 'customerName', title: '客户', width: '150px' },
  {
    key: 'productName',
    title: '产品',
    minWidth: '150px',
    format: (r) => r.productName,
    sub: (r) => r.productCode,
  },
  { key: 'priority', title: '优先级', width: '70px', kind: 'tag', tone: (r) => statusTone('priority', r.priority) },
  { key: 'priorityScore', title: '评分', width: '56px', kind: 'num' },
  { key: 'planQty', title: '计划', width: '64px', kind: 'num' },
  { key: 'finishedQty', title: '完成', width: '64px', kind: 'num' },
  { key: 'progress', title: '进度', width: '110px', kind: 'progress', value: (r) => r.progress },
  {
    key: 'stepDone',
    title: '工序',
    width: '70px',
    format: (r) => `${r.stepDone}/${r.stepCount}`,
    align: 'right',
    kind: 'mono',
  },
  { key: 'currentProc', title: '当前工序', width: '96px', format: (r) => r.currentProc || '—' },
  { key: 'planEnd', title: '计划交期', width: '120px', kind: 'datetime' },
  { key: 'status', title: '状态', width: '86px', kind: 'tag', tone: (r) => statusTone('order', r.status) },
  { key: 'actions', title: '操作', width: '150px', align: 'right' },
];

async function load() {
  loading.value = true;
  try {
    const res = await api.order.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value,
      status: statusFilter.value,
      priority: priorityFilter.value,
      sort: 'priorityScore',
      order: 'desc',
    });
    rows.value = res.records || [];
    total.value = res.total || 0;
  } finally {
    loading.value = false;
  }
}

async function openDetail(row) {
  detailOpen.value = true;
  detail.value = null;
  kit.value = null;
  try {
    detail.value = await api.order.detail(row.id);
    kit.value = await api.order.kitCheck(row.id);
  } catch {
    /* 提示已由 http 层处理 */
  }
}

async function refreshDetail() {
  if (!detail.value) return;
  detail.value = await api.order.detail(detail.value.order.id);
  kit.value = await api.order.kitCheck(detail.value.order.id);
  load();
}

async function doRelease(row) {
  const r = await api.order.release(row.id);
  toast.ok(`工单 ${row.orderNo} 已下达，展开 ${r.createdSteps || 0} 道工序`);
  if (detail.value?.order?.id === row.id) refreshDetail();
  else load();
}

async function doExpand(row) {
  const r = await api.order.expand(row.id);
  toast.ok(`已展开 ${r.created} 道工序`);
  load();
}

async function changeStatus(row, status, text) {
  await api.order.status(row.id, status);
  toast.warn(`工单 ${row.orderNo} 已${text}`);
  if (detail.value?.order?.id === row.id) refreshDetail();
  else load();
}

function openEdit(row) {
  form.value = row
    ? { ...row }
    : { priority: '中', orderType: '量产', planQty: 50, status: 'RELEASED', source: '手工录入' };
  editOpen.value = true;
}

async function saveForm() {
  await api.order.save(form.value);
  toast.ok('工单已保存');
  editOpen.value = false;
  load();
}

async function batchKit() {
  const ids = rows.value.filter((r) => r.status !== 'FINISHED').map((r) => r.id);
  if (!ids.length) {
    toast.warn('当前页没有可检查的工单');
    return;
  }
  const res = await api.order.kitCheckBatch(ids);
  const notReady = res.filter((r) => r.ready === false);
  if (notReady.length) {
    toast.warn(`${res.length} 张工单中 ${notReady.length} 张物料不齐套，请先补料`);
  } else {
    toast.ok(`${res.length} 张工单全部齐套`);
  }
}

const stepTimeline = computed(() => {
  const steps = detail.value?.steps || [];
  return steps.map((s) => ({
    title: `OP${s.seqNo} ${s.procName}　${statusLabel('step', s.status)}`,
    time: dateTime(s.planStart),
    text: `${s.machineCode || '未分配机台'} · 计划 ${num(s.planQty)} 件 · 完成 ${num(s.finishedQty)} 件`
      + (s.ncProgramNo ? ` · 程序 ${s.ncProgramNo}(${s.ncProgramVer || '-'})` : '')
      + (s.isKeyProc ? ' · 关键工序' : ''),
    tone: statusTone('step', s.status),
  }));
});

onMounted(async () => {
  load();
  try {
    const [mat, rt, cus] = await Promise.all([
      api.masterdata.material.list({ matType: '成品' }),
      api.masterdata.routing.list(),
      api.masterdata.customer.list(),
    ]);
    materials.value = mat || [];
    routings.value = rt || [];
    customers.value = cus || [];
  } catch {
    /* 主数据加载失败不影响列表 */
  }
});
</script>

<template>
  <div class="page">
    <IndPanel title="生产订单" sub="PRODUCTION ORDER" flush>
      <template #tools>
        <div class="ind-toolbar" style="align-items: flex-end">
          <IndField label="关键字">
            <IndInput v-model="keyword" placeholder="工单号 / 产品 / 客户" @enter="load" />
          </IndField>
          <IndField label="状态" width="118px">
            <IndSelect v-model="statusFilter" :options="STATUS_OPTIONS" placeholder="全部" @change="load" />
          </IndField>
          <IndField label="优先级" width="96px">
            <IndSelect v-model="priorityFilter" :options="PRIORITY_OPTIONS" placeholder="全部" @change="load" />
          </IndField>
          <IndButton @click="load">查询</IndButton>
          <IndButton variant="ghost" @click="batchKit">批量齐套检查</IndButton>
          <IndButton v-if="auth.can('order')" variant="primary" @click="openEdit(null)">＋ 新建工单</IndButton>
        </div>
      </template>

      <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
        <IndTable
          :columns="columns"
          :rows="rows"
          :loading="loading"
          clickable
          empty-text="没有符合条件的工单"
          :row-class="(r) => (r.status === 'HOLD' ? 'is-warn' : '')"
          @row-dblclick="openDetail"
        >
          <template #cell="{ row, col }">
            <template v-if="col.key === 'actions'">
              <div v-if="auth.can('order')" class="row-actions">
                <button class="link" @click.stop="openDetail(row)">详情</button>
                <button v-if="row.stepCount === 0" class="link" @click.stop="doRelease(row)">下达</button>
                <button v-if="row.status === 'HOLD'" class="link" @click.stop="changeStatus(row, 'IN_PROGRESS', '恢复生产')">恢复</button>
                <button v-else-if="row.status === 'IN_PROGRESS'" class="link warn" @click.stop="changeStatus(row, 'HOLD', '挂起')">挂起</button>
                <button class="link" @click.stop="openEdit(row)">编辑</button>
              </div>
              <button v-else class="link" @click.stop="openDetail(row)">详情</button>
            </template>
            <template v-else-if="col.kind === 'tag'">
              <IndTag :tone="col.tone ? col.tone(row) : 'plain'">{{ row[col.key] }}</IndTag>
            </template>
            <template v-else-if="col.kind === 'progress'">
              <div class="center gap-2">
                <IndBar :value="row.progress" :tone="row.progress >= 100 ? 'run' : row.progress > 0 ? 'primary' : 'idle'" height="7" striped />
                <span class="mono" style="width: 38px; text-align: right; font-size: 11px">{{ row.progress }}%</span>
              </div>
            </template>
            <template v-else-if="col.kind === 'num'">
              <span class="mono">{{ num(row[col.key]) }}</span>
            </template>
            <template v-else-if="col.kind === 'datetime'">
              <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
            </template>
            <template v-else>
              <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : row[col.key] || '—' }}</span>
              <div v-if="col.sub" class="mute" style="font-size: 10.5px">{{ col.sub(row) }}</div>
            </template>
          </template>
        </IndTable>
        <IndPager :total="total" v-model:page="page" v-model:size="size" @change="load" />
      </div>
    </IndPanel>

    <!-- 工单执行跟踪 -->
    <IndDrawer v-model="detailOpen" title="工单执行跟踪" :width="860">
      <div v-if="!detail" class="ind-loading"><div class="ind-loading__bar" /><span>加载中</span></div>
      <div v-else class="detail">
        <div class="detail__head">
          <div>
            <div class="detail__title">
              <b class="mono">{{ detail.order.orderNo }}</b>
              <IndTag :tone="statusTone('order', detail.order.status)" led>
                {{ statusLabel('order', detail.order.status) }}
              </IndTag>
              <IndTag :tone="statusTone('priority', detail.order.priority)">{{ detail.order.priority }}</IndTag>
              <IndTag tone="plain">{{ detail.order.orderType }}</IndTag>
            </div>
            <div class="dim" style="font-size: 12px; margin-top: 4px">
              {{ detail.order.customerName }} · {{ detail.order.productName }} {{ detail.order.productSpec }}
            </div>
          </div>
          <div class="detail__actions">
            <IndButton v-if="auth.can('order')" variant="primary" size="sm" @click="doRelease(detail.order)">下达并展开工序</IndButton>
            <IndButton variant="ghost" size="sm" @click="refreshDetail">刷新</IndButton>
          </div>
        </div>

        <div class="detail__kpis">
          <div class="dmini"><span>计划数量</span><b class="mono">{{ num(detail.order.planQty) }}</b></div>
          <div class="dmini"><span>已完成</span><b class="mono">{{ num(detail.order.finishedQty) }}</b></div>
          <div class="dmini"><span>合格数</span><b class="mono c-run">{{ num(detail.order.qualifiedQty) }}</b></div>
          <div class="dmini"><span>不良数</span><b class="mono c-stop">{{ num(detail.order.defectQty) }}</b></div>
          <div class="dmini"><span>完成率</span><b class="mono">{{ detail.progress }}%</b></div>
          <div class="dmini"><span>计划交期</span><b class="mono" style="font-size: 12px">{{ dateTime(detail.order.planEnd) }}</b></div>
        </div>

        <div class="ind-section-title" style="margin-top: 14px">工序执行时间线</div>
        <IndTimeline v-if="stepTimeline.length" :items="stepTimeline" />
        <div v-else class="mute" style="font-size: 12px">该工单尚未展开工序任务，点击「下达并展开工序」即可按工艺路线生成。</div>

        <div v-if="kit" class="kit">
          <div class="ind-section-title">
            齐套检查
            <IndTag :tone="kit.ready ? 'run' : 'stop'" led style="margin-left: 8px">
              {{ kit.ready ? '齐套' : '不齐套' }}
            </IndTag>
          </div>
          <div class="mute" style="font-size: 12px; margin-bottom: 8px">{{ kit.message }}</div>
          <table v-if="kit.hasBom" class="kit-table">
            <thead>
              <tr>
                <th>物料</th><th>类型</th><th style="text-align: right">单耗</th>
                <th style="text-align: right">需求量</th><th style="text-align: right">库存</th><th>结论</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="it in kit.items" :key="it.matCode">
                <td><b class="mono">{{ it.matCode }}</b> {{ it.matName }}</td>
                <td class="mute">{{ it.matType }}</td>
                <td class="num mono">{{ num(it.qtyPer, 4) }}</td>
                <td class="num mono">{{ num(it.needQty, 2) }} {{ it.unit }}</td>
                <td class="num mono">{{ num(it.stockQty, 2) }}</td>
                <td>
                  <IndTag :tone="it.enough ? 'run' : 'stop'">{{ it.enough ? '齐套' : `缺 ${num(it.gapQty, 2)}` }}</IndTag>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="ind-section-title" style="margin-top: 14px">派工单</div>
        <table v-if="detail.dispatches.length" class="kit-table">
          <thead>
            <tr><th>派工单号</th><th>工序</th><th>机台</th><th>操作工</th><th>班次</th><th>状态</th><th>计划量</th></tr>
          </thead>
          <tbody>
            <tr v-for="d in detail.dispatches" :key="d.id">
              <td class="mono">{{ d.dispatchNo }}</td>
              <td>OP{{ d.seqNo }} {{ d.procName }}</td>
              <td class="mono">{{ d.machineCode || '—' }}</td>
              <td>{{ d.employeeName || '—' }}</td>
              <td>{{ d.shiftName || '—' }}</td>
              <td><IndTag :tone="statusTone('dispatch', d.status)">{{ statusLabel('dispatch', d.status) }}</IndTag></td>
              <td class="num mono">{{ num(d.planQty) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else class="mute" style="font-size: 12px">尚无派工记录</div>
      </div>
    </IndDrawer>

    <!-- 新建 / 编辑 -->
    <IndModal v-model="editOpen" :title="form.id ? '编辑工单' : '新建生产订单'" :width="700">
      <div class="form-grid">
        <IndField label="产品（成品）" required>
          <IndSelect
            v-model="form.productMatId"
            :options="materials.map((m) => ({ value: m.id, label: `${m.matCode} ${m.matName}` }))"
            placeholder="请选择产品"
          />
        </IndField>
        <IndField label="工艺路线">
          <IndSelect
            v-model="form.routingId"
            :options="routings.map((r) => ({ value: r.id, label: `${r.routingCode} ${r.routingName}` }))"
            placeholder="自动匹配"
          />
        </IndField>
        <IndField label="客户">
          <IndSelect
            v-model="form.customerId"
            :options="customers.map((c) => ({ value: c.id, label: c.custName }))"
            placeholder="请选择客户"
          />
        </IndField>
        <IndField label="计划数量" required>
          <IndInput v-model="form.planQty" type="number" />
        </IndField>
        <IndField label="优先级">
          <IndSelect v-model="form.priority" :options="PRIORITY_OPTIONS" placeholder="中" />
        </IndField>
        <IndField label="订单类型">
          <IndSelect
            v-model="form.orderType"
            :options="['量产', '试制', '返修', '补件']"
            placeholder="量产"
          />
        </IndField>
        <IndField label="计划开工">
          <IndInput v-model="form.planStart" placeholder="2026-09-21 08:00:00" />
        </IndField>
        <IndField label="计划完工">
          <IndInput v-model="form.planEnd" placeholder="2026-09-23 17:00:00" />
        </IndField>
        <IndField label="销售单号">
          <IndInput v-model="form.salesNo" placeholder="可选" />
        </IndField>
        <IndField label="备注">
          <IndInput v-model="form.remark" placeholder="可选" />
        </IndField>
      </div>
      <p class="mute" style="font-size: 11.5px; margin-top: 10px">
        提示：选择产品后系统会自动带出该产品的工艺路线；工单下达时将按工艺路线展开工序任务，
        并把当时的工时定额与 NC 程序号「快照」到工序任务上。
      </p>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="primary" @click="saveForm">保存工单</IndButton>
      </template>
    </IndModal>
  </div>
</template>

<style scoped>
.row-actions { display: flex; gap: 8px; justify-content: flex-end; }
.link {
  background: none;
  border: none;
  padding: 0;
  color: var(--c-primary);
  cursor: pointer;
  font-size: 12px;
}
.link:hover { text-decoration: underline; }
.link.warn { color: var(--c-idle); }

.detail { display: flex; flex-direction: column; }
.detail__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.detail__title { display: flex; align-items: center; gap: 10px; }
.detail__title b { font-size: 17px; color: var(--text-strong); letter-spacing: 0.05em; }
.detail__actions { display: flex; gap: 8px; }

.detail__kpis {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10px;
  margin-top: 14px;
  padding: 12px 0;
  border-top: 1px solid var(--line);
  border-bottom: 1px solid var(--line);
}
.dmini { display: flex; flex-direction: column; gap: 3px; }
.dmini span { font-size: 11px; color: var(--text-mute); }
.dmini b { font-size: 17px; color: var(--text-strong); }

.kit-table { width: 100%; font-size: 12px; }
.kit-table th {
  text-align: left;
  font-size: 11px;
  color: var(--text-mute);
  padding: 5px 8px;
  border-bottom: 1px solid var(--line-strong);
  background: var(--table-head-bg);
}
.kit-table td { padding: 6px 8px; border-bottom: 1px solid var(--line-soft); }
.kit-table .num { text-align: right; font-family: var(--font-num); }

.kit { margin-top: 16px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
</style>
