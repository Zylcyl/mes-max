<script setup>
/** 派工管理：生成派工单、批量派工、打印随工件流转的纸质工票。 */
import { onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndPager from '@/components/ind/IndPager.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndModal from '@/components/ind/IndModal.vue';
import IndTag from '@/components/ind/IndTag.vue';
import { api } from '@/api';
import { DISPATCH_STATUS, label as statusLabel, tone as statusTone } from '@/utils/status';
import { dateTime, num } from '@/utils/format';
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
const machines = ref([]);
const machineFilter = ref('');
const shifts = ref([]);
const ticket = ref(null);
const ticketOpen = ref(false);

const STATUS_OPTIONS = Object.entries(DISPATCH_STATUS).map(([value, v]) => ({ value, label: v.label }));

const columns = [
  { key: 'dispatchNo', title: '派工单号', width: '136px', kind: 'mono' },
  { key: 'orderNo', title: '工单号', width: '128px', kind: 'mono' },
  { key: 'procName', title: '工序', width: '92px', format: (r) => `OP${r.seqNo} ${r.procName}` },
  { key: 'machineCode', title: '机台', width: '78px', kind: 'mono', format: (r) => r.machineCode || '未分配' },
  { key: 'employeeName', title: '操作工', width: '84px', format: (r) => r.employeeName || '未指派' },
  { key: 'teamName', title: '班组', width: '58px', format: (r) => r.teamName || '—' },
  { key: 'shiftName', title: '班次', width: '58px', format: (r) => r.shiftName || '—' },
  { key: 'ncProgramNo', title: 'NC 程序', width: '126px', kind: 'mono', format: (r) => r.ncProgramNo || '—' },
  { key: 'planQty', title: '计划量', width: '66px', kind: 'num' },
  { key: 'planStart', title: '计划开始', width: '120px', kind: 'datetime' },
  { key: 'status', title: '状态', width: '84px', kind: 'tag', tone: (r) => statusTone('dispatch', r.status) },
  { key: 'actions', title: '操作', width: '132px', align: 'right' },
];

async function load() {
  loading.value = true;
  try {
    const res = await api.dispatch.page({
      page: page.value,
      size: size.value,
      keyword: keyword.value,
      status: statusFilter.value,
      machineId: machineFilter.value,
      sort: 'dispatchTime',
      order: 'desc',
    });
    rows.value = res.records || [];
    total.value = res.total || 0;
  } finally {
    loading.value = false;
  }
}

async function batchDispatch() {
  const r = await api.dispatch.batch({ shiftId: shifts.value[0]?.id });
  toast.ok(`批量派工完成：共 ${r.total} 道任务，成功派工 ${r.dispatched} 道`);
  load();
}

async function receive(row) {
  await api.dispatch.receive(row.id);
  toast.ok(`${row.employeeName || '操作工'} 已接单 ${row.dispatchNo}`);
  load();
}

async function openTicket(row) {
  ticket.value = await api.dispatch.ticket(row.id);
  ticketOpen.value = true;
}

/** 打印工票：在新窗口渲染一张 A5 竖版工票，随工件流转用。 */
function printTicket() {
  const t = ticket.value;
  if (!t) return;
  const o = t.order || {};
  const s = t.step || {};
  const d = t.dispatch || {};
  const m = t.machine || {};
  const w = window.open('', '_blank', 'width=780,height=1000');
  w.document.write(`<!DOCTYPE html><html lang="zh-CN"><head><meta charset="utf-8">
  <title>工票 ${d.dispatchNo}</title>
  <style>
    body { font-family: 'Microsoft YaHei', sans-serif; color:#111; padding: 18px; }
    h1 { font-size: 20px; letter-spacing: 4px; text-align:center; margin: 0 0 4px; }
    .sub { text-align:center; font-size:12px; color:#555; margin-bottom:14px; }
    table { width:100%; border-collapse: collapse; font-size: 12px; }
    td,th { border:1px solid #333; padding: 6px 8px; }
    th { background:#f0f0f0; text-align:left; width: 96px; font-weight:600; }
    .big { font-size: 15px; font-weight: 700; letter-spacing: 1px; }
    .sign { margin-top: 18px; display:flex; justify-content:space-between; font-size:12px; }
    .sign div { border-top:1px solid #333; width: 30%; padding-top:6px; text-align:center; }
    .note { margin-top:12px; font-size:11px; color:#666; line-height:1.7; }
  </style></head><body>
  <h1>数 控 加 工 工 票</h1>
  <div class="sub">MES-MAX 制造执行系统 · 打印时间 ${new Date().toLocaleString('zh-CN')}</div>
  <table>
    <tr><th>工票编号</th><td class="big">${d.dispatchNo}</td><th>工单号</th><td class="big">${d.orderNo}</td></tr>
    <tr><th>产品</th><td colspan="3">${o.productName || ''}　${o.productSpec || ''}（${o.productCode || ''}）</td></tr>
    <tr><th>客户</th><td>${o.customerName || ''}</td><th>计划数量</th><td>${d.planQty} 件</td></tr>
    <tr><th>工序</th><td>OP${d.seqNo} ${d.procName}</td><th>工作中心</th><td>${s.wcName || ''}</td></tr>
    <tr><th>指派机台</th><td>${m.macCode || d.machineCode || ''} ${m.macName || ''}</td>
        <th>NC 程序号</th><td>${s.ncProgramNo || ''}（${s.ncProgramVer || ''}）</td></tr>
    <tr><th>刀具要求</th><td colspan="3">${s.toolRequirement || '按工艺卡执行'}</td></tr>
    <tr><th>定额工时</th><td>准备 ${s.setupTime || 0} 分钟 / 单件 ${s.pieceTime || 0} 分钟</td>
        <th>计划时间</th><td>${d.planStart || ''} ~ ${d.planEnd || ''}</td></tr>
    <tr><th>操作工</th><td>${d.employeeName || ''}　${d.teamName || ''}</td>
        <th>班次</th><td>${d.shiftName || ''}</td></tr>
  </table>
  <div class="note">
    现场填写栏：实际开工 ______ 实际完工 ______ 完工数量 ______ 合格 ______ 不良 ______ 报废 ______<br/>
    不良原因：□尺寸超差 □表面粗糙度 □碰伤划伤 □崩刃刀痕 □毛刺未清 □形位公差 □其他 ____________<br/>
    实测关键尺寸填写：______________________________________________
  </div>
  <div class="sign"><div>操作工签名</div><div>质检员签名</div><div>班组长签名</div></div>
  </body></html>`);
  w.document.close();
  setTimeout(() => w.print(), 260);
}

onMounted(async () => {
  load();
  try {
    const [m, s] = await Promise.all([api.masterdata.machine.list(), api.masterdata.shift.list()]);
    machines.value = m || [];
    shifts.value = s || [];
  } catch {
    /* 忽略 */
  }
});
</script>

<template>
  <div class="page">
    <IndPanel title="派工管理" sub="DISPATCH" flush>
      <template #tools>
        <div class="ind-toolbar" style="align-items: flex-end">
          <IndField label="关键字">
            <IndInput v-model="keyword" placeholder="派工单号 / 工单号" @enter="load" />
          </IndField>
          <IndField label="状态" width="118px">
            <IndSelect v-model="statusFilter" :options="STATUS_OPTIONS" placeholder="全部" @change="load" />
          </IndField>
          <IndField label="机台" width="150px">
            <IndSelect
              v-model="machineFilter"
              :options="machines.map((m) => ({ value: m.id, label: `${m.macCode} ${m.macName}` }))"
              placeholder="全部机台"
              @change="load"
            />
          </IndField>
          <IndButton @click="load">查询</IndButton>
          <IndButton v-if="auth.can('dispatch')" variant="primary" @click="batchDispatch">⚙ 批量派工</IndButton>
        </div>
      </template>

      <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
        <IndTable :columns="columns" :rows="rows" :loading="loading" empty-text="没有派工记录">
          <template #cell="{ row, col }">
            <template v-if="col.key === 'actions'">
              <div class="row-actions">
                <button v-if="row.status === 'DISPATCHED'" class="link" @click="receive(row)">接单</button>
                <button class="link" @click="openTicket(row)">工票</button>
              </div>
            </template>
            <template v-else-if="col.kind === 'tag'">
              <IndTag :tone="col.tone(row)" led>{{ statusLabel('dispatch', row.status) }}</IndTag>
            </template>
            <template v-else-if="col.kind === 'num'">
              <span class="mono">{{ num(row[col.key]) }}</span>
            </template>
            <template v-else-if="col.kind === 'datetime'">
              <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
            </template>
            <template v-else>
              <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : row[col.key] || '—' }}</span>
            </template>
          </template>
        </IndTable>
        <IndPager :total="total" v-model:page="page" v-model:size="size" @change="load" />
      </div>
    </IndPanel>

    <IndModal v-model="ticketOpen" title="数控加工工票" :width="700">
      <div v-if="ticket" class="ticket">
        <div class="ticket__title">
          数 控 加 工 工 票
          <span class="mute">MES-MAX · {{ dateTime(ticket.printTime) }} 打印</span>
        </div>
        <table class="ticket__table">
          <tbody>
            <tr><th>工票编号</th><td class="mono strong">{{ ticket.dispatch.dispatchNo }}</td><th>工单号</th><td class="mono strong">{{ ticket.dispatch.orderNo }}</td></tr>
            <tr><th>产品</th><td colspan="3">{{ ticket.order?.productName }} {{ ticket.order?.productSpec }}（{{ ticket.order?.productCode }}）</td></tr>
            <tr><th>客户</th><td>{{ ticket.order?.customerName }}</td><th>计划数量</th><td class="mono">{{ num(ticket.dispatch.planQty) }} 件</td></tr>
            <tr><th>工序</th><td>OP{{ ticket.dispatch.seqNo }} {{ ticket.dispatch.procName }}</td><th>工作中心</th><td>{{ ticket.step?.wcName }}</td></tr>
            <tr><th>指派机台</th><td>{{ ticket.machine?.macName || '未分配' }}</td><th>NC 程序号</th><td class="mono">{{ ticket.step?.ncProgramNo }}（{{ ticket.step?.ncProgramVer }}）</td></tr>
            <tr><th>刀具要求</th><td colspan="3">{{ ticket.step?.toolRequirement || '按工艺卡执行' }}</td></tr>
            <tr><th>定额工时</th><td>准备 {{ num(ticket.step?.setupTime) }} min / 单件 {{ num(ticket.step?.pieceTime, 2) }} min</td><th>计划时间</th><td class="mono" style="font-size: 11px">{{ dateTime(ticket.dispatch.planStart) }} ~ {{ dateTime(ticket.dispatch.planEnd) }}</td></tr>
            <tr><th>操作工</th><td>{{ ticket.dispatch.employeeName || '—' }} {{ ticket.dispatch.teamName || '' }}</td><th>班次</th><td>{{ ticket.dispatch.shiftName || '—' }}</td></tr>
          </tbody>
        </table>
        <div class="ticket__note">
          二维码内容：<span class="mono">{{ ticket.qrContent }}</span>
          <div class="ticket__qr">
            <span v-for="i in 64" :key="i" class="ticket__qr-cell" :class="{ 'is-on': (i * 7 + i * i) % 3 === 0 }" />
          </div>
        </div>
      </div>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">关闭</IndButton>
        <IndButton variant="primary" @click="printTicket">打印工票</IndButton>
      </template>
    </IndModal>
  </div>
</template>

<style scoped>
.row-actions { display: flex; gap: 10px; justify-content: flex-end; }
.link { background: none; border: none; padding: 0; color: var(--c-primary); cursor: pointer; font-size: 12px; }
.link:hover { text-decoration: underline; }

.ticket {
  border: 1px solid var(--line-strong);
  /* 用令牌而不是写死深色：#0f151c 配 var(--text) 在浅色主题下会变成「深字压深底」不可读 */
  background: var(--bg-well);
  padding: 14px;
}
.ticket__title {
  text-align: center;
  font-family: var(--font-display);
  font-size: 18px;
  letter-spacing: 0.28em;
  color: var(--text-strong);
  margin-bottom: 12px;
}
.ticket__title span { display: block; font-size: 11px; letter-spacing: 0.06em; margin-top: 4px; }
.ticket__table { width: 100%; border-collapse: collapse; font-size: 12px; }
.ticket__table th {
  width: 92px;
  text-align: left;
  padding: 6px 8px;
  border: 1px solid var(--line-strong);
  background: var(--table-head-bg);
  color: var(--text-mute);
  font-weight: 500;
}
.ticket__table td { padding: 6px 8px; border: 1px solid var(--line-strong); color: var(--text); }
.ticket__note { margin-top: 12px; font-size: 11.5px; color: var(--text-dim); display: flex; align-items: center; gap: 16px; }
.ticket__qr {
  margin-left: auto;
  width: 72px; height: 72px;
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 1px;
  padding: 4px;
  background: #fff;
}
.ticket__qr-cell { background: #fff; }
.ticket__qr-cell.is-on { background: #0b1118; }
</style>
