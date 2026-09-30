<script setup>
/**
 * 质量管理：检验单（首件/巡检/终检）与不合格品四态闭环处置。
 * 首件检验是 CNC 多品种小批量场景最重要的一道防线：关键工序未出首件结论不允许批量加工。
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
import IndModal from '@/components/ind/IndModal.vue';
import IndChart from '@/components/ind/IndChart.vue';
import { api } from '@/api';
import { CHART_COLORS, barOption, ringOption } from '@/utils/chart';
import { HANDLE_TYPE, INSP_TYPE, label as statusLabel, tone as statusTone } from '@/utils/status';
import { dateTime, num } from '@/utils/format';
import { useToast } from '@/stores/toast';
import { useAuth } from '@/stores/auth';

const toast = useToast();
const auth = useAuth();
const tab = ref('inspection');

const insp = ref({ rows: [], total: 0, page: 1, size: 20, loading: true, keyword: '', type: '' });
const inspStats = ref({ byType: [], byMachine: [] });
const items = ref([]);
const detailOpen = ref(false);
const active = ref(null);

const defect = ref({ rows: [], total: 0, page: 1, size: 20, loading: true, keyword: '' });
const defectStats = ref({ byType: [], byReason: [], byHandle: [] });
const handleOpen = ref(false);
const handleForm = ref({});

const inspColumns = [
  { key: 'inspNo', title: '检验单号', width: '136px', kind: 'mono' },
  { key: 'inspType', title: '检验类型', width: '92px', kind: 'tag' },
  { key: 'orderNo', title: '工单', width: '128px', kind: 'mono' },
  { key: 'procName', title: '工序', width: '84px' },
  { key: 'machineCode', title: '机台', width: '76px', kind: 'mono', format: (r) => r.machineCode || '—' },
  { key: 'productName', title: '产品', minWidth: '140px' },
  { key: 'sampleQty', title: '抽样', width: '56px', kind: 'num' },
  { key: 'qualifiedQty', title: '合格', width: '56px', kind: 'num' },
  { key: 'defectQty', title: '不良', width: '56px', kind: 'num' },
  { key: 'result', title: '判定', width: '80px', kind: 'tag' },
  { key: 'inspector', title: '检验员', width: '74px' },
  { key: 'inspTime', title: '检验时间', width: '126px', kind: 'datetime' },
  { key: 'actions', title: '操作', width: '104px', align: 'right' },
];

const defectColumns = [
  { key: 'defectNo', title: '不合格单号', width: '136px', kind: 'mono' },
  { key: 'orderNo', title: '工单', width: '128px', kind: 'mono' },
  { key: 'machineCode', title: '机台', width: '76px', kind: 'mono', format: (r) => r.machineCode || '—' },
  { key: 'productName', title: '产品', minWidth: '130px' },
  { key: 'defectType', title: '缺陷类型', width: '92px', kind: 'tag' },
  { key: 'defectDesc', title: '缺陷描述', minWidth: '200px' },
  { key: 'defectQty', title: '数量', width: '58px', kind: 'num' },
  { key: 'reasonType', title: '责任原因', width: '96px', format: (r) => r.reasonType || '—' },
  { key: 'handleType', title: '处置方式', width: '92px', kind: 'tag' },
  { key: 'handleStatus', title: '状态', width: '80px', kind: 'tag' },
  { key: 'costLoss', title: '质量损失', width: '86px', align: 'right', kind: 'mono', format: (r) => (r.costLoss ? `¥${num(r.costLoss, 2)}` : '—') },
  { key: 'actions', title: '操作', width: '86px', align: 'right' },
];

const typeOption = computed(() =>
  ringOption({
    data: (defectStats.value.byType || []).map((d, i) => ({
      name: d.type,
      value: d.qty,
      color: [CHART_COLORS.stop, CHART_COLORS.idle, CHART_COLORS.maint, CHART_COLORS.primary, CHART_COLORS.cyan][i % 5],
    })),
  }),
);

const reasonOption = computed(() =>
  barOption({
    x: (defectStats.value.byReason || []).map((d) => d.reason),
    series: [{ name: '不良数量', data: (defectStats.value.byReason || []).map((d) => d.qty), color: CHART_COLORS.idle }],
    legend: false,
    yName: '件',
    grid: { left: 40, right: 14, top: 14, bottom: 26 },
  }),
);

const inspTypeOption = computed(() =>
  barOption({
    x: (inspStats.value.byType || []).map((d) => d.type),
    series: [
      { name: '合格', data: (inspStats.value.byType || []).map((d) => d.pass), color: CHART_COLORS.run, stack: 'a' },
      { name: '不合格', data: (inspStats.value.byType || []).map((d) => d.total - d.pass), color: CHART_COLORS.stop, stack: 'a' },
    ],
    yName: '单',
    grid: { left: 40, right: 14, top: 30, bottom: 24 },
  }),
);

async function loadInsp() {
  insp.value.loading = true;
  try {
    const res = await api.qc.page({
      page: insp.value.page,
      size: insp.value.size,
      keyword: insp.value.keyword,
      inspType: insp.value.type,
      sort: 'inspTime',
      order: 'desc',
    });
    insp.value.rows = res.records || [];
    insp.value.total = res.total || 0;
  } finally {
    insp.value.loading = false;
  }
}

async function loadInspStats() {
  inspStats.value = await api.qc.statistics(14);
}

async function openInsp(row) {
  active.value = row;
  items.value = await api.qc.items(row.id);
  detailOpen.value = true;
}

async function saveItem(it) {
  await api.qc.saveItem(it);
  toast.ok('检验项已保存并自动判定');
  items.value = await api.qc.items(active.value.id);
}

async function judge() {
  const r = await api.qc.judge(active.value.id, {});
  toast[r.result === 'PASS' ? 'ok' : 'error'](
    `判定结果：${r.result === 'PASS' ? '合格' : '不合格'} —— ${r.conclusion}`,
  );
  detailOpen.value = false;
  await Promise.all([loadInsp(), loadInspStats()]);
}

async function loadDefect() {
  defect.value.loading = true;
  try {
    const res = await api.defect.page({
      page: defect.value.page,
      size: defect.value.size,
      keyword: defect.value.keyword,
      sort: 'createTime',
      order: 'desc',
    });
    defect.value.rows = res.records || [];
    defect.value.total = res.total || 0;
  } finally {
    defect.value.loading = false;
  }
}

async function loadDefectStats() {
  defectStats.value = await api.defect.statistics(14);
}

function openHandle(row) {
  handleForm.value = {
    id: row.id,
    defectNo: row.defectNo,
    handleType: row.handleType || '返工',
    handleStatus: '已关闭',
    causeAnalysis: row.causeAnalysis || '',
    costLoss: row.costLoss || 0,
    remark: row.remark || '',
  };
  handleOpen.value = true;
}

async function submitHandle() {
  await api.defect.handle(handleForm.value.id, handleForm.value);
  toast.ok(`处置完成：${handleForm.value.handleType}`);
  handleOpen.value = false;
  await Promise.all([loadDefect(), loadDefectStats()]);
}

onMounted(() => {
  loadInsp();
  loadInspStats();
  loadDefect();
  loadDefectStats();
});
</script>

<template>
  <div class="page">
    <IndTabs
      v-model="tab"
      :tabs="[
        { key: 'inspection', label: '检验单', badge: insp.total },
        { key: 'defect', label: '不合格品处置', badge: defect.total },
      ]"
    />

    <template v-if="tab === 'inspection'">
      <div class="kpi-row">
        <IndKpi label="近 14 天检验单" :value="inspStats.total" unit="单" tone="primary" />
        <IndKpi label="检验合格率" :value="inspStats.passRate" unit="%" :digits="1" tone="run" foot="合格单 / 总单数" />
        <IndKpi label="不合格单" :value="inspStats.failCount" unit="单" tone="stop" foot="已开出不合格品单" />
        <IndKpi
          label="首件检验"
          :value="(inspStats.byType || []).find((t) => t.type === '首件检验')?.total || 0"
          unit="单"
          tone="idle"
          foot="关键工序强制前置"
        />
        <IndKpi
          label="首件合格率"
          :value="(inspStats.byType || []).find((t) => t.type === '首件检验')?.rate"
          unit="%"
          :digits="1"
          tone="cyan"
          foot="程序与刀补是否正确的最直接体现"
        />
      </div>

      <div class="grid-2">
        <IndPanel title="检验类型构成" sub="BY INSPECTION TYPE" :body-style="{ padding: '10px' }">
          <IndChart :option="inspTypeOption" height="176" />
        </IndPanel>
        <IndPanel title="各机台检验合格率" sub="PASS RATE BY MACHINE" :body-style="{ padding: '8px' }">
          <IndTable
            :columns="[
              { key: 'machine', title: '机台', width: '92px', kind: 'mono' },
              { key: 'total', title: '检验单', width: '76px', kind: 'num' },
              { key: 'pass', title: '合格', width: '66px', kind: 'num' },
              { key: 'rate', title: '合格率', width: '84px', align: 'right', kind: 'mono', format: (r) => r.rate + '%' },
              { key: 'bar', title: '', minWidth: '90px', kind: 'progress', value: (r) => r.rate },
            ]"
            :rows="inspStats.byMachine || []"
            dense
            empty-text="暂无数据"
          />
        </IndPanel>
      </div>

      <IndPanel title="检验单列表" sub="INSPECTION RECORDS" flush>
        <template #tools>
          <IndField label="关键字" width="200px">
            <IndInput v-model="insp.keyword" placeholder="检验单号 / 工单 / 产品" @enter="loadInsp" />
          </IndField>
          <IndField label="类型" width="120px">
            <IndSelect
              v-model="insp.type"
              :options="Object.entries(INSP_TYPE).map(([value, v]) => ({ value, label: v.label }))"
              placeholder="全部"
              @change="loadInsp"
            />
          </IndField>
          <IndButton size="sm" @click="loadInsp">查询</IndButton>
        </template>
        <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
          <IndTable
            :columns="inspColumns"
            :rows="insp.rows"
            :loading="insp.loading"
            dense
            clickable
            empty-text="暂无检验单"
            @row-dblclick="openInsp"
          >
            <template #cell="{ row, col }">
              <template v-if="col.key === 'actions'">
                <button class="link" @click="openInsp(row)">检验明细</button>
              </template>
              <template v-else-if="col.key === 'inspType'">
                <IndTag :tone="statusTone('inspType', row.inspType)" led>{{ statusLabel('inspType', row.inspType) }}</IndTag>
              </template>
              <template v-else-if="col.key === 'result'">
                <IndTag :tone="statusTone('qcResult', row.result)" led>{{ statusLabel('qcResult', row.result) }}</IndTag>
              </template>
              <template v-else-if="col.kind === 'datetime'">
                <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">
                  {{ col.format ? col.format(row) : (row[col.key] ?? '—') }}
                </span>
              </template>
            </template>
          </IndTable>
          <IndPager :total="insp.total" v-model:page="insp.page" v-model:size="insp.size" @change="loadInsp" />
        </div>
      </IndPanel>
    </template>

    <template v-else>
      <div class="kpi-row">
        <IndKpi label="近 14 天不合格单" :value="defectStats.count" unit="单" tone="stop" />
        <IndKpi label="不良数量" :value="defectStats.defectQty" unit="件" tone="idle" />
        <IndKpi label="质量损失" :value="defectStats.costLoss" unit="元" :digits="2" tone="maint" foot="报废与返修直接成本" />
        <IndKpi label="未关闭" :value="defectStats.openCount" unit="单" tone="stop" foot="需质量工程师闭环" />
        <IndKpi
          label="TOP 缺陷"
          :value="defectStats.byType?.[0]?.type || '—'"
          tone="primary"
          :foot="`${num(defectStats.byType?.[0]?.qty)} 件`"
        />
      </div>

      <div class="grid-3">
        <IndPanel title="缺陷类型分布" sub="DEFECT TYPE">
          <IndChart :option="typeOption" height="190" />
        </IndPanel>
        <IndPanel title="责任原因分布" sub="ROOT CAUSE">
          <IndChart :option="reasonOption" height="190" />
        </IndPanel>
        <IndPanel title="处置方式分布" sub="DISPOSITION">
          <div class="handle-list">
            <div v-for="h in defectStats.byHandle || []" :key="h.handle" class="handle-row">
              <IndTag :tone="statusTone('handle', h.handle)">{{ h.handle }}</IndTag>
              <div class="handle-row__bar">
                <div
                  class="handle-row__fill"
                  :style="{ width: `${(h.qty / Math.max(1, defectStats.byHandle[0].qty)) * 100}%` }"
                />
              </div>
              <b class="mono">{{ num(h.qty) }} 件</b>
            </div>
          </div>
          <p class="mute" style="font-size: 11.5px; margin-top: 10px">
            处置必须四态闭环：返工 / 返修 / 让步接收 / 报废。<br />
            「让步接收」需要客户或工艺签署，否则追溯时说不清责任。
          </p>
        </IndPanel>
      </div>

      <IndPanel title="不合格品处置单" sub="NON-CONFORMANCE" flush>
        <template #tools>
          <IndField label="关键字" width="220px">
            <IndInput v-model="defect.keyword" placeholder="单号 / 工单 / 产品 / 缺陷" @enter="loadDefect" />
          </IndField>
          <IndButton size="sm" @click="loadDefect">查询</IndButton>
        </template>
        <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
          <IndTable :columns="defectColumns" :rows="defect.rows" :loading="defect.loading" dense empty-text="暂无不合格品记录">
            <template #cell="{ row, col }">
            <template v-if="col.key === 'actions'">
                <button v-if="auth.can('quality')" class="link" @click="openHandle(row)">处置</button>
                <span v-else class="mute">—</span>
              </template>
              <template v-else-if="col.key === 'defectType'">
                <IndTag tone="stop">{{ row.defectType }}</IndTag>
              </template>
              <template v-else-if="col.key === 'handleType'">
                <IndTag :tone="statusTone('handle', row.handleType)">{{ row.handleType || '未处置' }}</IndTag>
              </template>
              <template v-else-if="col.key === 'handleStatus'">
                <IndTag :tone="row.handleStatus === '已关闭' ? 'plain' : 'idle'">{{ row.handleStatus || '—' }}</IndTag>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">
                  {{ col.format ? col.format(row) : (row[col.key] ?? '—') }}
                </span>
              </template>
            </template>
          </IndTable>
          <IndPager :total="defect.total" v-model:page="defect.page" v-model:size="defect.size" @change="loadDefect" />
        </div>
      </IndPanel>
    </template>

    <IndModal v-model="detailOpen" title="检验项明细与判定" :width="860">
      <div v-if="active" class="insp-head">
        <div><span class="mute">检验单</span><b class="mono">{{ active.inspNo }}</b></div>
        <div><span class="mute">类型</span><b>{{ statusLabel('inspType', active.inspType) }}</b></div>
        <div><span class="mute">工单</span><b class="mono">{{ active.orderNo }}</b></div>
        <div><span class="mute">工序 / 机台</span><b>{{ active.procName }} / {{ active.machineCode || '—' }}</b></div>
        <div><span class="mute">检验时间</span><b class="mono">{{ dateTime(active.inspTime) }}</b></div>
      </div>
      <table class="item-table">
        <thead>
          <tr>
            <th>检验项目</th>
            <th>类型</th>
            <th style="text-align: right">基本尺寸</th>
            <th style="text-align: right">上偏差</th>
            <th style="text-align: right">下偏差</th>
            <th style="text-align: right">实测值</th>
            <th>判定</th>
            <th>量具</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="it in items" :key="it.id" :class="{ 'is-fail': it.result === 'FAIL' }">
            <td>{{ it.itemName }}</td>
            <td class="mute">{{ it.itemType }}</td>
            <td class="num mono">{{ num(it.nominalValue, 4) }}</td>
            <td class="num mono c-run">+{{ num(it.upperTol, 4) }}</td>
            <td class="num mono c-stop">{{ num(it.lowerTol, 4) }}</td>
            <td class="num mono strong">
              <input class="cell-input mono" v-model="it.measureValue" @change="saveItem(it)" />
            </td>
            <td>
              <IndTag :tone="it.result === 'FAIL' ? 'stop' : 'run'">{{ it.result === 'FAIL' ? '不合格' : '合格' }}</IndTag>
            </td>
            <td class="mute">{{ it.gaugeUsed || '—' }}</td>
          </tr>
        </tbody>
      </table>
      <p class="mute" style="font-size: 11.5px; margin-top: 10px">
        修改实测值后会自动按公差带判定；点击「提交判定」汇总结论。首件判定合格后，
        工序任务会由「检验中」恢复为「加工中」，允许批量加工。
      </p>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">关闭</IndButton>
        <IndButton v-if="auth.can('quality')" variant="primary" @click="judge">提交判定</IndButton>
      </template>
    </IndModal>

    <IndModal v-model="handleOpen" title="不合格品处置" :width="600">
      <div class="insp-head" style="grid-template-columns: 1fr">
        <div><span class="mute">不合格单号</span><b class="mono">{{ handleForm.defectNo }}</b></div>
      </div>
      <div class="form-grid">
        <IndField label="处置方式" required>
          <IndSelect v-model="handleForm.handleType" :options="Object.keys(HANDLE_TYPE)" placeholder="请选择" />
        </IndField>
        <IndField label="质量损失（元）">
          <IndInput v-model="handleForm.costLoss" type="number" />
        </IndField>
      </div>
      <IndField label="原因分析与纠正措施" style="margin-top: 12px">
        <IndInput v-model="handleForm.causeAnalysis" placeholder="例如：刀补磨损未及时修正，已把预警阈值调整到 80%" />
      </IndField>
      <IndField label="备注" style="margin-top: 12px">
        <IndInput v-model="handleForm.remark" placeholder="选填" />
      </IndField>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="primary" @click="submitHandle">确认处置</IndButton>
      </template>
    </IndModal>
  </div>
</template>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(5, 1fr); gap: 12px; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.grid-3 { display: grid; grid-template-columns: 1fr 1fr 1fr; gap: 12px; }
.link { background: none; border: none; padding: 0; color: var(--c-primary); cursor: pointer; font-size: 12px; }
.link:hover { text-decoration: underline; }

.insp-head {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  background: rgba(10, 162, 255, 0.05);
  margin-bottom: 12px;
}
.insp-head > div { display: flex; flex-direction: column; gap: 2px; font-size: 12px; }
.insp-head b { color: var(--text); }

.item-table { width: 100%; font-size: 12px; }
.item-table th {
  text-align: left;
  padding: 6px 8px;
  font-size: 11px;
  color: var(--text-mute);
  border-bottom: 1px solid var(--line-strong);
  background: var(--table-head-bg);
}
.item-table td { padding: 5px 8px; border-bottom: 1px solid var(--line-soft); }
.item-table tr.is-fail td { background: rgba(255, 77, 79, 0.07); }
.item-table .num { text-align: right; }
.cell-input {
  width: 106px;
  height: 22px;
  text-align: right;
  padding: 0 6px;
  background: var(--bg-well);
  border: 1px solid var(--line);
  color: var(--text);
  font-size: 12px;
}

.handle-list { display: flex; flex-direction: column; gap: 10px; }
.handle-row { display: grid; grid-template-columns: 76px 1fr 78px; align-items: center; gap: 10px; }
.handle-row__bar { height: 9px; background: var(--bg-well); border: 1px solid var(--line); }
.handle-row__fill { height: 100%; background: linear-gradient(90deg, #0a72ad, var(--c-primary)); }
.handle-row b { text-align: right; font-size: 12px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
</style>
