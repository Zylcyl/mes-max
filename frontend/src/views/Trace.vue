<script setup>
/**
 * 谱系追溯。
 *
 * 两个方向都要能查，这是 CNC 行业（尤其汽配、液压、航空配套）的合规底线：
 *   正向：序列号 → 它经历了哪几道工序、哪台机床、谁操作、执行哪个程序、用哪把刀、哪批毛坯、检验结果
 *   反向：毛坯炉号 / 刀具编号 / 机床 / 程序号 → 反查影响了哪些工件 —— 客户投诉与召回用这个方向
 */
import { onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndTabs from '@/components/ind/IndTabs.vue';
import IndKpi from '@/components/ind/IndKpi.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndPager from '@/components/ind/IndPager.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndLed from '@/components/ind/IndLed.vue';
import IndTimeline from '@/components/ind/IndTimeline.vue';
import IndEmpty from '@/components/ind/IndEmpty.vue';
import { api } from '@/api';
import { label as statusLabel, tone as statusTone } from '@/utils/status';
import { dateTime, minutes, num } from '@/utils/format';
import { useToast } from '@/stores/toast';

const toast = useToast();
const tab = ref('forward');

const overview = ref({});
const sn = ref({ rows: [], total: 0, page: 1, size: 15, loading: true, keyword: '' });
const forward = ref(null);
const activeSn = ref('');

const backwardForm = ref({ batchNo: '', toolCode: '', machineCode: '', ncProgramNo: '', operator: '' });
const backward = ref(null);
const batches = ref([]);

const columns = [
  { key: 'snCode', title: '序列号', width: '190px', kind: 'mono' },
  { key: 'productName', title: '产品', minWidth: '150px' },
  { key: 'orderNo', title: '工单', width: '128px', kind: 'mono' },
  { key: 'batchNo', title: '毛坯批次', width: '132px', kind: 'mono', format: (r) => r.batchNo || '—' },
  { key: 'currentProc', title: '当前工序', width: '96px', format: (r) => r.currentProc || '—' },
  { key: 'machineCode', title: '机台', width: '78px', kind: 'mono', format: (r) => r.machineCode || '—' },
  { key: 'qcResult', title: '质量', width: '82px', kind: 'tag', tone: (r) => (r.qcResult === '合格' ? 'run' : 'idle') },
  { key: 'status', title: '状态', width: '80px', kind: 'tag', tone: (r) => (r.status === '完工' ? 'primary' : 'info') },
  { key: 'actions', title: '操作', width: '96px', align: 'right' },
];

async function loadOverview() {
  overview.value = await api.trace.overview();
  batches.value = overview.value.batches || [];
}

async function loadSn() {
  sn.value.loading = true;
  try {
    const res = await api.trace.snPage({ page: sn.value.page, size: sn.value.size, keyword: sn.value.keyword });
    sn.value.rows = res.records || [];
    sn.value.total = res.total || 0;
  } finally {
    sn.value.loading = false;
  }
}

async function doForward(code) {
  if (!code) {
    toast.warn('请输入序列号或毛坯批次号');
    return;
  }
  try {
    forward.value = await api.trace.forward(code);
    activeSn.value = code;
  } catch {
    forward.value = null;
  }
}

async function doBackward() {
  const f = backwardForm.value;
  if (!f.batchNo && !f.toolCode && !f.machineCode && !f.ncProgramNo && !f.operator) {
    toast.warn('请至少填写一个追溯条件');
    return;
  }
  backward.value = await api.trace.backward(f);
}

function clearBackward() {
  backwardForm.value = { batchNo: '', toolCode: '', machineCode: '', ncProgramNo: '', operator: '' };
  backward.value = null;
}

const stepTimeline = () => {
  const list = forward.value?.timeline || [];
  return list.map((t) => ({
    title: `OP${t.log.seqNo} ${t.log.procName}　${t.log.machineCode || '手工工位'}`,
    time: dateTime(t.log.startTime),
    text:
      `操作工 ${t.log.operator || '—'}（${t.skillLevel || '—'}）· 程序 ${t.log.ncProgramNo || '—'}`
      + ` · 刀具 ${t.log.toolCode || '—'}/${t.log.toolOffsetNo || '—'}`
      + ` · 毛坯批次 ${t.log.materialBatch || '—'}`
      + ` · 工时 ${minutes(t.log.runMinutes)} · 检验 ${t.log.qcResult || '—'}`,
    tone: t.log.qcResult === '合格' ? 'run' : t.log.qcResult === '待复检' ? 'idle' : 'stop',
  }));
};

onMounted(async () => {
  await Promise.all([loadOverview(), loadSn()]);
  if (sn.value.rows.length) doForward(sn.value.rows[0].snCode);
});
</script>

<template>
  <div class="page">
    <div class="kpi-row">
      <IndKpi label="序列号总数" :value="overview.total" unit="个" tone="primary" foot="可追溯工件" />
      <IndKpi label="已完工" :value="overview.finished" unit="个" tone="run" />
      <IndKpi label="在制中" :value="overview.inProgress" unit="个" tone="info" />
      <IndKpi label="报废" :value="overview.scrapped" unit="个" tone="stop" />
      <IndKpi label="待复检" :value="overview.retest" unit="个" tone="idle" foot="质量状态待确认" />
      <IndKpi label="毛坯批次" :value="batches.length" unit="批" tone="cyan" foot="含炉号与材质证明" />
    </div>

    <IndTabs
      v-model="tab"
      :tabs="[
        { key: 'forward', label: '正向追溯（序列号 → 全过程）' },
        { key: 'backward', label: '反向追溯（批次/刀具/机床 → 受影响工件）' },
        { key: 'batch', label: '毛坯批次台账' },
      ]"
    />

    <!-- 正向 -->
    <template v-if="tab === 'forward'">
      <div class="trace-grid">
        <IndPanel title="序列号清单" sub="SERIAL NUMBERS" flush>
          <template #tools>
            <IndField width="150px">
              <IndInput v-model="sn.keyword" placeholder="序列号 / 批次" @enter="loadSn" />
            </IndField>
            <IndButton size="sm" @click="loadSn">查询</IndButton>
          </template>
          <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
            <IndTable
              :columns="columns"
              :rows="sn.rows"
              :loading="sn.loading"
              dense
              clickable
              :active-key="activeSn"
              row-key="snCode"
              empty-text="暂无序列号"
              @row-click="(r) => doForward(r.snCode)"
            >
              <template #cell="{ row, col }">
                <template v-if="col.key === 'actions'">
                  <button class="link" @click.stop="doForward(row.snCode)">追溯</button>
                </template>
                <template v-else-if="col.kind === 'tag'">
                  <IndTag :tone="col.tone(row)">{{ row[col.key] }}</IndTag>
                </template>
                <template v-else>
                  <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
                </template>
              </template>
            </IndTable>
            <IndPager :total="sn.total" v-model:page="sn.page" v-model:size="sn.size" @change="loadSn" />
          </div>
        </IndPanel>

        <div class="trace-result">
          <IndPanel title="正向追溯结果" :sub="activeSn || '请选择序列号'">
            <template #tools>
              <IndField width="220px">
                <IndInput v-model="activeSn" placeholder="直接输入序列号查询" @enter="doForward(activeSn)" />
              </IndField>
              <IndButton size="sm" variant="primary" @click="doForward(activeSn)">追溯</IndButton>
            </template>

            <div v-if="!forward" class="trace-empty">
              <IndEmpty text="尚未选择序列号" hint="点击左侧任意序列号，或直接输入序列号后回车" />
            </div>

            <div v-else class="fw">
              <div class="fw__head">
                <div class="fw__sn">
                  <IndLed :tone="forward.qualified ? 'run' : 'stop'" size="lg" />
                  <b class="mono">{{ forward.sn.snCode }}</b>
                  <IndTag :tone="forward.sn.status === '完工' ? 'primary' : 'info'" led>{{ forward.sn.status }}</IndTag>
                  <IndTag :tone="forward.sn.qcResult === '合格' ? 'run' : 'idle'">{{ forward.sn.qcResult }}</IndTag>
                </div>
                <div class="fgrid">
                  <div><span>产品</span><b>{{ forward.sn.productName }}</b></div>
                  <div><span>产品编码</span><b class="mono">{{ forward.sn.productCode }}</b></div>
                  <div><span>工单</span><b class="mono">{{ forward.sn.orderNo }}</b></div>
                  <div><span>毛坯批次</span><b class="mono">{{ forward.sn.batchNo || '—' }}</b></div>
                  <div><span>当前工序</span><b>{{ forward.sn.currentProc || '—' }}</b></div>
                  <div><span>机台</span><b class="mono">{{ forward.sn.machineCode || '—' }}</b></div>
                  <div><span>投产时间</span><b class="mono">{{ dateTime(forward.sn.createTime) }}</b></div>
                  <div><span>完工时间</span><b class="mono">{{ dateTime(forward.sn.completeTime) }}</b></div>
                  <div><span>经历工序</span><b class="mono">{{ forward.stepCount }} 道</b></div>
                  <div><span>使用机台</span><b class="mono">{{ forward.machineCount }} 台</b></div>
                  <div><span>操作工</span><b>{{ (forward.operators || []).join(' / ') || '—' }}</b></div>
                  <div><span>NC 程序</span><b class="mono">{{ (forward.programs || []).join(' / ') || '—' }}</b></div>
                </div>
              </div>

              <div v-if="forward.batch" class="fw__batch">
                <div class="ind-section-title">毛坯批次信息（炉号可追溯到钢厂材质证明）</div>
                <div class="fgrid">
                  <div><span>批次号</span><b class="mono">{{ forward.batch.batchNo }}</b></div>
                  <div><span>材料</span><b>{{ forward.batch.matName }} {{ forward.batch.spec }}</b></div>
                  <div><span>供应商</span><b>{{ forward.batch.supplier }}</b></div>
                  <div><span>炉号</span><b class="mono">{{ forward.batch.heatNo }}</b></div>
                  <div><span>材质证明书</span><b class="mono">{{ forward.batch.certNo }}</b></div>
                  <div><span>入库时间</span><b class="mono">{{ dateTime(forward.batch.inTime) }}</b></div>
                </div>
              </div>

              <div class="ind-section-title" style="margin-top: 14px">加工过程链</div>
              <IndTimeline :items="stepTimeline()">
                <template #item="{ item }">
                  <div class="step-extra">
                    <span class="mute mono" style="font-size: 11px">
                      {{ item.text }}
                    </span>
                  </div>
                </template>
              </IndTimeline>

              <div class="ind-section-title" style="margin-top: 14px">关联检验与不合格记录</div>
              <div v-if="!forward.inspections.length && !forward.defects.length" class="mute" style="font-size: 12px">
                该序列号暂无检验记录
              </div>
              <table v-if="forward.inspections.length" class="mini-table">
                <thead><tr><th>检验单号</th><th>类型</th><th>工序</th><th>结果</th><th>检验员</th><th>时间</th></tr></thead>
                <tbody>
                  <tr v-for="i in forward.inspections" :key="i.id">
                    <td class="mono">{{ i.inspNo }}</td>
                    <td>{{ statusLabel('inspType', i.inspType) }}</td>
                    <td>{{ i.procName }}</td>
                    <td><IndTag :tone="statusTone('qcResult', i.result)">{{ statusLabel('qcResult', i.result) }}</IndTag></td>
                    <td>{{ i.inspector }}</td>
                    <td class="mono">{{ dateTime(i.inspTime) }}</td>
                  </tr>
                </tbody>
              </table>
              <table v-if="forward.defects.length" class="mini-table" style="margin-top: 8px">
                <thead><tr><th>不合格单号</th><th>缺陷类型</th><th>描述</th><th>处置</th><th>状态</th></tr></thead>
                <tbody>
                  <tr v-for="d in forward.defects" :key="d.id">
                    <td class="mono">{{ d.defectNo }}</td>
                    <td>{{ d.defectType }}</td>
                    <td>{{ d.defectDesc }}</td>
                    <td><IndTag :tone="statusTone('handle', d.handleType)">{{ d.handleType }}</IndTag></td>
                    <td>{{ d.handleStatus }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </IndPanel>
        </div>
      </div>
    </template>

    <!-- 反向 -->
    <template v-else-if="tab === 'backward'">
      <IndPanel title="反向追溯条件" sub="TRACE BACKWARD">
        <div class="ind-toolbar">
          <IndField label="毛坯批次 / 炉号" width="180px">
            <IndInput v-model="backwardForm.batchNo" placeholder="例如 B20260801-45S" />
          </IndField>
          <IndField label="刀具编号" width="160px">
            <IndInput v-model="backwardForm.toolCode" placeholder="例如 T-EM12-01" />
          </IndField>
          <IndField label="机台编号" width="150px">
            <IndInput v-model="backwardForm.machineCode" placeholder="例如 MC-001" />
          </IndField>
          <IndField label="NC 程序号" width="180px">
            <IndInput v-model="backwardForm.ncProgramNo" placeholder="例如 O1050_VMC_NC" />
          </IndField>
          <IndField label="操作工" width="130px">
            <IndInput v-model="backwardForm.operator" placeholder="例如 李建军" />
          </IndField>
          <IndButton variant="primary" @click="doBackward">开始追溯</IndButton>
          <IndButton variant="ghost" @click="clearBackward">清空</IndButton>
        </div>
        <p class="mute" style="font-size: 11.5px; margin-top: 10px">
          典型用途：客户投诉某批工件尺寸超差 → 用毛坯炉号反查同炉号的所有工件与工单 → 圈定召回范围；
          或某把刀崩刃 → 反查该刀加工过的全部序列号。
        </p>
      </IndPanel>

      <div v-if="backward" class="kpi-row">
        <IndKpi label="命中追溯记录" :value="backward.recordCount" unit="条" tone="primary" />
        <IndKpi label="受影响序列号" :value="backward.snCount" unit="个" tone="stop" foot="需要重点排查的工件" />
        <IndKpi label="涉及工单" :value="Object.keys(backward.affectedOrders || {}).length" unit="张" tone="idle" />
        <IndKpi label="涉及机台" :value="Object.keys(backward.byMachine || {}).length" unit="台" tone="cyan" />
        <IndKpi label="涉及产品" :value="(backward.products || []).length" unit="种" tone="maint" />
      </div>

      <div v-if="backward" class="grid-2">
        <IndPanel title="受影响的工单与序列号" sub="AFFECTED">
          <div class="ind-section-title">工单分布</div>
          <div class="tag-wrap">
            <IndTag v-for="(cnt, order) in backward.affectedOrders" :key="order" tone="primary">
              {{ order }} × {{ cnt }}
            </IndTag>
          </div>
          <div class="ind-section-title" style="margin-top: 12px">序列号清单（{{ backward.snCodes.length }} 个）</div>
          <div class="sn-list">
            <span v-for="s in backward.snCodes.slice(0, 60)" :key="s" class="sn-chip mono">{{ s }}</span>
          </div>
        </IndPanel>
        <IndPanel title="按工序与机台分布" sub="BY PROCESS / MACHINE">
          <div class="ind-section-title">工序命中</div>
          <div class="tag-wrap">
            <IndTag v-for="(cnt, proc) in backward.byProc" :key="proc">{{ proc }} × {{ cnt }}</IndTag>
          </div>
          <div class="ind-section-title" style="margin-top: 12px">机台命中</div>
          <div class="tag-wrap">
            <IndTag v-for="(cnt, mac) in backward.byMachine" :key="mac" tone="info">{{ mac }} × {{ cnt }}</IndTag>
          </div>
        </IndPanel>
      </div>

      <IndPanel v-if="backward" title="追溯明细" sub="TRACE RECORDS" flush>
        <div style="height: 100%; min-height: 0">
          <IndTable
            :columns="[
              { key: 'snCode', title: '序列号', width: '190px', kind: 'mono' },
              { key: 'orderNo', title: '工单', width: '128px', kind: 'mono' },
              { key: 'procName', title: '工序', width: '92px', format: (r) => `OP${r.seqNo} ${r.procName}` },
              { key: 'machineCode', title: '机台', width: '80px', kind: 'mono' },
              { key: 'ncProgramNo', title: 'NC 程序', width: '132px', kind: 'mono', format: (r) => r.ncProgramNo || '—' },
              { key: 'toolCode', title: '刀具', width: '112px', kind: 'mono', format: (r) => r.toolCode || '—' },
              { key: 'operator', title: '操作工', width: '78px', format: (r) => r.operator || '—' },
              { key: 'materialBatch', title: '毛坯批次', width: '132px', kind: 'mono', format: (r) => r.materialBatch || '—' },
              { key: 'startTime', title: '开始时间', width: '128px', kind: 'datetime' },
              { key: 'qcResult', title: '检验', width: '76px', kind: 'tag' },
            ]"
            :rows="backward.records"
            dense
            empty-text="无记录"
          >
            <template #cell="{ row, col }">
              <template v-if="col.kind === 'tag'">
                <IndTag :tone="row.qcResult === '合格' ? 'run' : 'idle'">{{ row.qcResult || '—' }}</IndTag>
              </template>
              <template v-else-if="col.kind === 'datetime'">
                <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
              </template>
            </template>
          </IndTable>
        </div>
      </IndPanel>
    </template>

    <!-- 批次 -->
    <template v-else>
      <IndPanel title="毛坯批次台账" sub="MATERIAL BATCH" flush>
        <div style="height: 100%; min-height: 0">
          <IndTable
            :columns="[
              { key: 'batch', title: '批次号', width: '144px', kind: 'mono', format: (r) => r.batch.batchNo },
              { key: 'mat', title: '材料', minWidth: '180px', format: (r) => r.batch.matCode + ' ' + r.batch.matName },
              { key: 'spec', title: '规格', width: '140px', format: (r) => r.batch.spec },
              { key: 'supplier', title: '供应商', width: '118px', format: (r) => r.batch.supplier },
              { key: 'heatNo', title: '炉号', width: '110px', kind: 'mono', format: (r) => r.batch.heatNo },
              { key: 'qty', title: '入库数量', width: '98px', align: 'right', kind: 'mono', format: (r) => num(r.batch.qty) + ' ' + (r.batch.unit || '') },
              { key: 'certNo', title: '材质证明', width: '150px', kind: 'mono', format: (r) => r.batch.certNo },
              { key: 'inTime', title: '入库时间', width: '140px', kind: 'datetime', format: (r) => dateTime(r.batch.inTime) },
              { key: 'usedSnCount', title: '已投产工件', width: '110px', align: 'right', kind: 'num' },
              { key: 'usedOrderCount', title: '涉及工单', width: '94px', align: 'right', kind: 'num' },
              { key: 'status', title: '投产状态', width: '96px' },
            ]"
            :rows="batches"
            dense
            empty-text="暂无毛坯批次"
          >
            <template #cell="{ row, col }">
              <template v-if="col.key === 'status'">
                <IndTag :tone="row.status === '已投产' ? 'run' : 'plain'" led>{{ row.status }}</IndTag>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">
                  {{ col.format ? col.format(row) : (row.batch[col.key] ?? '—') }}
                </span>
              </template>
            </template>
          </IndTable>
        </div>
      </IndPanel>
    </template>
  </div>
</template>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(6, 1fr); gap: 12px; }
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.trace-grid { display: grid; grid-template-columns: minmax(0, 520px) minmax(0, 1fr); gap: 12px; flex: 1; min-height: 0; }
.trace-result { min-height: 0; }
.trace-result :deep(.ind-panel) { height: 100%; }
.trace-empty { display: grid; place-items: center; height: 100%; }
.link { background: none; border: none; padding: 0; color: var(--c-primary); cursor: pointer; font-size: 12px; }
.link:hover { text-decoration: underline; }

.fw__head { padding-bottom: 12px; border-bottom: 1px solid var(--line); }
.fw__sn { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.fw__sn b { font-size: 17px; color: var(--text-strong); letter-spacing: 0.05em; }
.fgrid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px 16px; }
.fgrid > div { display: flex; flex-direction: column; gap: 2px; font-size: 12px; }
.fgrid span { color: var(--text-mute); font-size: 11px; }
.fw__batch { margin-top: 14px; padding-top: 12px; border-top: 1px dashed var(--line); }

.step-extra { margin-top: 2px; }
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

.tag-wrap { display: flex; flex-wrap: wrap; gap: 6px; }
.sn-list { display: flex; flex-wrap: wrap; gap: 6px; max-height: 220px; overflow: auto; }
.sn-chip {
  font-size: 11px;
  padding: 2px 6px;
  border: 1px solid var(--line);
  background: var(--sheen);
  color: var(--text-dim);
}
</style>
