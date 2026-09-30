<script setup>
/**
 * 生产排产：有限产能排产 + 机台负荷 + 待排任务池。
 *
 * 排产算法（后端）：按「订单加权优先级评分」降序，把每道工序投放到最早空闲的可用机台上
 * （list scheduling 启发式）。计划员可以在甘特图上手动拖动/指派覆盖算法结果。
 */
import { computed, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndGantt from '@/components/ind/IndGantt.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndModal from '@/components/ind/IndModal.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndLoad from '@/components/ind/IndLoad.vue';
import { api } from '@/api';
import { label as statusLabel, tone as statusTone } from '@/utils/status';
import { dateTime, num } from '@/utils/format';
import { useToast } from '@/stores/toast';
import { useAuth } from '@/stores/auth';

const toast = useToast();
const auth = useAuth();

const machines = ref([]);
const workCenters = ref([]);
const gantt = ref([]);
const pending = ref([]);
const loadList = ref([]);
const loading = ref(true);
const wcFilter = ref('');

const days = ref(10);
const assignOpen = ref(false);
const assignForm = ref({});

function iso(d) {
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:00`;
}

const range = computed(() => {
  const from = new Date();
  from.setDate(from.getDate() - 3);
  from.setHours(0, 0, 0, 0);
  const to = new Date();
  to.setDate(to.getDate() + Number(days.value));
  to.setHours(23, 59, 0, 0);
  return { from: iso(from), to: iso(to) };
});

const ganttMachines = computed(() =>
  wcFilter.value ? machines.value.filter((m) => String(m.workCenterId) === String(wcFilter.value)) : machines.value,
);

const visibleGantt = computed(() => {
  if (!wcFilter.value) return gantt.value;
  const ids = new Set(ganttMachines.value.map((m) => m.id));
  return gantt.value.filter((t) => ids.has(t.machineId) || !t.machineId);
});

const loadListFiltered = computed(() =>
  wcFilter.value ? loadList.value.filter((l) => String(l.workCenterId) === String(wcFilter.value)) : loadList.value,
);

const pendingColumns = [
  { key: 'priority', title: '优先级', width: '64px', kind: 'tag', tone: (r) => statusTone('priority', r.priority) },
  { key: 'priorityScore', title: '评分', width: '50px', kind: 'num' },
  { key: 'orderNo', title: '工单', width: '112px', kind: 'mono', format: (r) => r.orderNo.slice(-8) },
  { key: 'procName', title: '工序', width: '82px', format: (r) => `OP${r.seqNo} ${r.procName}` },
  { key: 'wcName', title: '工作中心', width: '104px' },
  { key: 'planQty', title: '数量', width: '56px', kind: 'num' },
  {
    key: 'planMinutes',
    title: '工时',
    width: '66px',
    align: 'right',
    kind: 'mono',
    format: (r) => `${Math.round(Number(r.setupTime || 0) + Number(r.pieceTime || 0) * Number(r.planQty || 0))}min`,
  },
];

async function load() {
  loading.value = true;
  try {
    const [res, g, p, l] = await Promise.all([
      api.schedule.resources(),
      api.schedule.gantt({ from: range.value.from, to: range.value.to }),
      api.schedule.pending(),
      api.schedule.machineLoad({ from: range.value.from, to: range.value.to }),
    ]);
    machines.value = res.machines || [];
    workCenters.value = res.workCenters || [];
    gantt.value = g || [];
    pending.value = p || [];
    loadList.value = l || [];
  } finally {
    loading.value = false;
  }
}

async function autoSchedule() {
  const r = await api.schedule.auto({ from: iso(new Date()) });
  toast.ok(`自动排产完成：安排 ${r.scheduled} 道工序${r.unassigned ? `，${r.unassigned} 道未找到可用机台` : ''}`);
  await load();
}

function openAssign(task, isGantt = false) {
  assignForm.value = {
    stepId: task.stepId || task.id,
    orderNo: task.orderNo,
    procName: task.procName,
    seqNo: task.seqNo,
    machineId: task.machineId || '',
    planStart: task.planStart ? String(task.planStart).replace(' ', 'T') : iso(new Date()),
    planEnd: task.planEnd ? String(task.planEnd).replace(' ', 'T') : '',
    planQty: task.planQty,
    priority: task.priority,
    isGantt,
  };
  assignOpen.value = true;
}

async function submitAssign() {
  const body = {
    stepId: assignForm.value.stepId,
    machineId: assignForm.value.machineId,
    planStart: assignForm.value.planStart,
    planEnd: assignForm.value.planEnd,
  };
  await api.schedule.assign(body);
  toast.ok(`排产已调整：${assignForm.value.orderNo} OP${assignForm.value.seqNo}`);
  assignOpen.value = false;
  await load();
}

const summary = computed(() => {
  const totalTasks = gantt.value.length;
  const running = gantt.value.filter((t) => t.status === 'RUNNING').length;
  const overLoad = loadList.value.filter((l) => l.level === 'OVER').length;
  const highLoad = loadList.value.filter((l) => l.level === 'HIGH').length;
  const totalMinutes = loadList.value.reduce((s, l) => s + Number(l.planMinutes || 0), 0);
  return { totalTasks, running, overLoad, highLoad, totalHours: Math.round(totalMinutes / 6) / 10 };
});

onMounted(load);
</script>

<template>
  <div class="page" style="gap: 12px">
    <IndPanel plain>
      <div class="ind-toolbar">
        <IndField label="工作中心" width="150px">
          <IndSelect
            v-model="wcFilter"
            :options="workCenters.map((w) => ({ value: w.id, label: w.wcName }))"
            placeholder="全部工作中心"
          />
        </IndField>
        <IndField label="排产窗口（天）" width="120px">
          <IndSelect v-model="days" :options="[7, 10, 14, 21]" placeholder="10" @change="load" />
        </IndField>
        <IndButton @click="load">刷新</IndButton>
        <IndButton v-if="auth.can('schedule')" variant="primary" @click="autoSchedule">⚙ 一键自动排产</IndButton>
        <div class="ind-toolbar__spacer" />
        <div class="summary">
          <span>窗口内工序任务 <b class="mono">{{ summary.totalTasks }}</b></span>
          <span>加工中 <b class="mono c-run">{{ summary.running }}</b></span>
          <span>超负荷机台 <b class="mono c-stop">{{ summary.overLoad }}</b></span>
          <span>高负荷机台 <b class="mono c-idle">{{ summary.highLoad }}</b></span>
          <span>计划总工时 <b class="mono">{{ summary.totalHours }}h</b></span>
        </div>
      </div>
    </IndPanel>

    <div class="sched">
      <IndPanel title="排产甘特图" sub="JOBSHOP SCHEDULING GANTT" flush>
        <template #tools>
          <div class="legend">
            <span><i class="dot dot--run" />加工中</span>
            <span><i class="dot dot--info" />已派工</span>
            <span><i class="dot dot--primary" />已完工</span>
            <span><i class="dot dot--maint" />检验中</span>
            <span><i class="dot dot--plain" />待派工</span>
          </div>
        </template>
        <div style="height: 100%; padding: 8px">
          <div v-if="loading" class="ind-loading"><div class="ind-loading__bar" /><span>排产数据加载中</span></div>
          <IndGantt
            v-else
            :items="visibleGantt"
            :machines="ganttMachines"
            :from="range.from.replace('T', ' ')"
            :to="range.to.replace('T', ' ')"
            :px-per-minute="days > 14 ? 0.55 : days > 9 ? 0.9 : 1.4"
            @bar-dblclick="(t) => openAssign(t, true)"
          />
        </div>
      </IndPanel>

      <div class="sched__side">
        <IndPanel title="机台负荷" sub="CAPACITY LOAD" flush bodyStyle="padding:8px">
          <div class="load-list">
            <div v-for="l in loadListFiltered" :key="l.machineId" class="load-item">
              <div class="between">
                <span class="center gap-2">
                  <i class="dot" :class="`dot--${l.status ? statusTone('machine', l.status) : 'offline'}`" />
                  <b class="mono">{{ l.machineCode }}</b>
                  <span class="mute ellipsis" style="font-size: 11px">{{ l.cncType }}</span>
                </span>
                <IndTag :tone="l.level === 'OVER' ? 'stop' : l.level === 'HIGH' ? 'idle' : l.level === 'NORMAL' ? 'run' : 'plain'">
                  {{ l.loadRate }}%
                </IndTag>
              </div>
              <div class="center gap-2" style="margin-top: 5px">
                <IndLoad :rate="l.loadRate" :level="l.level" :width="180" />
                <span class="mono mute" style="font-size: 11px">
                  {{ num(l.planMinutes / 60, 1) }}h / {{ num(l.capacityMinutes / 60, 1) }}h
                </span>
              </div>
              <div class="mute" style="font-size: 10.5px; margin-top: 2px">
                任务 {{ l.taskCount }} 道 · 已完成工时 {{ num(l.doneMinutes / 60, 1) }}h
              </div>
            </div>
          </div>
        </IndPanel>

        <IndPanel title="待排产任务池" :sub="`${pending.length} 道待安排`" flush>
          <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
            <IndTable
              :columns="pendingColumns"
              :rows="pending.slice(0, 120)"
              dense
              clickable
              :loading="loading"
              empty-text="没有待排产任务"
              @row-dblclick="(r) => openAssign(r)"
            />
          </div>
          <template #tools>
            <span class="mute" style="font-size: 11px">双击行可指派机台</span>
          </template>
        </IndPanel>
      </div>
    </div>

    <IndModal v-model="assignOpen" :title="assignForm.isGantt ? '调整排产' : '指派机台与时间'" :width="560">
      <div class="assign-head">
        <b class="mono">{{ assignForm.orderNo }}</b>
        <span>OP{{ assignForm.seqNo }} {{ assignForm.procName }}</span>
        <IndTag :tone="statusTone('priority', assignForm.priority)">{{ assignForm.priority }}</IndTag>
        <span class="mute mono">计划 {{ num(assignForm.planQty) }} 件</span>
      </div>
      <div class="form-grid">
        <IndField label="指派机台" required>
          <IndSelect
            v-model="assignForm.machineId"
            :options="machines.map((m) => ({ value: m.id, label: `${m.macCode} ${m.macName}（${m.cncType}）` }))"
            placeholder="请选择机台"
          />
        </IndField>
        <IndField label="计划开始" required>
          <IndInput v-model="assignForm.planStart" placeholder="2026-09-21T08:00:00" />
        </IndField>
        <IndField label="计划结束" required>
          <IndInput v-model="assignForm.planEnd" placeholder="2026-09-21T16:00:00" />
        </IndField>
      </div>
      <p class="mute" style="font-size: 11.5px; margin-top: 10px">
        手工调整的优先级高于自动排产结果；再次执行「一键自动排产」会重新计算未开工的任务。
      </p>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="primary" @click="submitAssign">确认调整</IndButton>
      </template>
    </IndModal>
  </div>
</template>

<style scoped>
.sched {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 372px;
  gap: 12px;
  flex: 1;
  min-height: 0;
}
.sched__side { display: grid; grid-template-rows: minmax(0, 0.95fr) minmax(0, 1.05fr); gap: 12px; min-height: 0; }

.summary { display: flex; gap: 16px; font-size: 12px; color: var(--text-dim); }
.summary b { color: var(--text-strong); margin-left: 3px; }

.legend { display: flex; gap: 12px; font-size: 11px; color: var(--text-dim); }
.legend span { display: flex; align-items: center; gap: 5px; }
.dot { width: 8px; height: 8px; display: inline-block; border: 1px solid currentColor; }
.dot--run { background: var(--c-run); color: var(--c-run); }
.dot--idle { background: var(--c-idle); color: var(--c-idle); }
.dot--stop { background: var(--c-stop); color: var(--c-stop); }
.dot--alarm { background: var(--c-alarm); color: var(--c-alarm); }
.dot--primary { background: var(--c-primary); color: var(--c-primary); }
.dot--info { background: var(--c-info); color: var(--c-info); }
.dot--maint { background: var(--c-maint); color: var(--c-maint); }
.dot--plain { background: var(--c-offline); color: var(--c-offline); }
.dot--offline { background: var(--c-offline); color: var(--c-offline); }

.load-list { display: flex; flex-direction: column; }
.load-item { padding: 8px 10px; border-bottom: 1px solid var(--line-soft); }
.load-item:last-child { border-bottom: none; }

.assign-head {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  margin-bottom: 12px;
  border: 1px solid var(--line);
  background: rgba(10, 162, 255, 0.06);
  font-size: 12px;
}
.assign-head b { font-size: 14px; color: var(--text-strong); }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
</style>
