<script setup>
/**
 * 车间报工终端（机台旁触屏一体机）。
 *
 * 交互原则：以「我这台机床现在该干什么」为第一视角，按钮大、步骤少、反馈明确。
 * 所有录入都必须在 3 次点击内完成 —— 操作工手上还戴着沾油的手套。
 */
import { computed, onMounted, ref, watch } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndModal from '@/components/ind/IndModal.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndLed from '@/components/ind/IndLed.vue';
import IndBar from '@/components/ind/IndBar.vue';
import IndChart from '@/components/ind/IndChart.vue';
import IndEmpty from '@/components/ind/IndEmpty.vue';
import { api } from '@/api';
import { barOption, CHART_COLORS } from '@/utils/chart';
import { STOP_REASON, label as statusLabel, tone as statusTone } from '@/utils/status';
import { dateTime, fromNow, num, pct, timeOnly } from '@/utils/format';
import { useToast } from '@/stores/toast';
import { useAuth } from '@/stores/auth';

const toast = useToast();
const auth = useAuth();
const machines = ref([]);
const machineId = ref(null);
const tasks = ref({ pending: [], reports: [], tools: [], openAndons: [] });
const hourly = ref([]);

const startOpen = ref(false);
const reportOpen = ref(false);
const reportMode = ref('report');
const stopOpen = ref(false);
const andonOpen = ref(false);

const startForm = ref({ ncProgramNo: '' });
const reportForm = ref({});
const stopForm = ref({});
const andonForm = ref({});

const machine = computed(() => machines.value.find((m) => m.id === machineId.value) || null);
const live = computed(() => machine.value?.live || {});
const currentTask = computed(() => (tasks.value.pending || [])[0] || null);
const queued = computed(() => (tasks.value.pending || []).slice(1));

const progress = computed(() => {
  const t = currentTask.value;
  if (!t) return 0;
  const plan = Number(t.planQty || 0);
  return plan ? Math.min(100, Math.round((Number(t.finishedQty || 0) / plan) * 100)) : 0;
});

const hourlyOption = computed(() =>
  barOption({
    x: hourly.value.map((d) => `${d.hour}`),
    series: [{ name: '今日产出', data: hourly.value.map((d) => d.qty), color: CHART_COLORS.run }],
    legend: false,
    yName: '件',
    grid: { left: 40, right: 12, top: 14, bottom: 22 },
  }),
);

async function loadMachines() {
  machines.value = await api.machine.live();
  if (!machineId.value && machines.value.length) {
    const busy = machines.value.find((m) => m.live && m.live.orderNo);
    machineId.value = (busy || machines.value[0]).id;
  }
}

async function loadTasks() {
  if (!machineId.value) return;
  tasks.value = await api.terminal.tasks(machineId.value);
  hourly.value = await api.terminal.hourly(machineId.value);
}

function openStart() {
  startForm.value = { ncProgramNo: currentTask.value?.ncProgramNo || '' };
  startOpen.value = true;
}

async function doStart() {
  await api.terminal.start({ dispatchId: currentTask.value.id, ncProgramNo: startForm.value.ncProgramNo });
  toast.ok('已开工，开始计时');
  startOpen.value = false;
  await loadTasks();
  await loadMachines();
}

function openReport(mode) {
  reportMode.value = mode;
  const remain = currentTask.value
    ? Math.max(0, Number(currentTask.value.planQty || 0) - Number(currentTask.value.finishedQty || 0))
    : 0;
  reportForm.value = {
    dispatchId: currentTask.value?.id,
    qty: remain || 1,
    defectQty: 0,
    scrapReason: '',
    runMinutes: '',
    stopMinutes: 0,
    ncProgramNo: currentTask.value?.ncProgramNo || '',
    toolCode: tasks.value.tools?.[0]?.toolCode || '',
    toolOffsetNo: tasks.value.tools?.[0]?.offsetNo || '',
    remark: '',
  };
  reportOpen.value = true;
}

async function submitReport() {
  const body = {
    dispatchId: reportForm.value.dispatchId,
    qty: Number(reportForm.value.qty),
    defectQty: Number(reportForm.value.defectQty || 0),
    runMinutes: reportForm.value.runMinutes === '' ? null : Number(reportForm.value.runMinutes),
    stopMinutes: Number(reportForm.value.stopMinutes || 0),
    scrapReason: reportForm.value.scrapReason || null,
    ncProgramNo: reportForm.value.ncProgramNo || null,
    toolCode: reportForm.value.toolCode || null,
    toolOffsetNo: reportForm.value.toolOffsetNo || null,
    remark: reportForm.value.remark || null,
  };
  const fn = reportMode.value === 'finish' ? api.terminal.finish : api.terminal.report;
  await fn(body);
  toast.ok(reportMode.value === 'finish' ? '完工已提交' : '报工成功');
  reportOpen.value = false;
  await loadTasks();
  await loadMachines();
}

function openStop() {
  stopForm.value = {
    reasonCode: 'TOOL_CHANGE',
    durationMin: 15,
    startTime: new Date(Date.now() - 15 * 60000).toISOString().slice(0, 19),
    endTime: new Date().toISOString().slice(0, 19),
    remark: '',
  };
  stopOpen.value = true;
}

async function submitStop() {
  const text = STOP_REASON[stopForm.value.reasonCode] || '停机';
  await api.terminal.stop({
    machineId: machineId.value,
    machineCode: machine.value?.macCode,
    orderId: currentTask.value?.orderId || null,
    orderNo: currentTask.value?.orderNo || null,
    stepId: currentTask.value?.stepId || null,
    stopType: ['PLAN_MAINT', 'WAIT_INSPECT'].includes(stopForm.value.reasonCode) ? '计划停机' : '非计划停机',
    reasonCode: stopForm.value.reasonCode,
    reasonDesc: stopForm.value.remark || text,
    startTime: stopForm.value.startTime,
    endTime: stopForm.value.endTime,
    durationMin: stopForm.value.durationMin,
  });
  toast.warn('停机已记录，将计入 OEE 时间损失');
  stopOpen.value = false;
  await loadTasks();
}

function openAndon(type) {
  andonForm.value = { andonType: type || '设备异常', level: '一级', content: '' };
  andonOpen.value = true;
}

async function submitAndon() {
  await api.terminal.andon({
    machineId: machineId.value,
    machineCode: machine.value?.macCode,
    orderId: currentTask.value?.orderId || null,
    orderNo: currentTask.value?.orderNo || null,
    andonType: andonForm.value.andonType,
    level: andonForm.value.level,
    content: andonForm.value.content || `${andonForm.value.andonType}，请支援`,
  });
  toast.ok('已呼叫安灯，等待响应');
  andonOpen.value = false;
  await loadTasks();
}

async function setState(state, reason) {
  await api.terminal.machineState({ machineId: machineId.value, machineCode: machine.value?.macCode, state, reason });
  toast.ok(`机台状态已切换为 ${statusLabel('machine', state)}`);
  await loadMachines();
}

watch(machineId, () => loadTasks());

onMounted(async () => {
  await loadMachines();
  await loadTasks();
  setInterval(() => {
    loadMachines().catch(() => {});
    loadTasks().catch(() => {});
  }, 20000);
});
</script>

<template>
  <div class="terminal">
    <div class="terminal__machines">
      <button
        v-for="m in machines"
        :key="m.id"
        class="mchip"
        :class="[`mchip--${statusTone('machine', m.status)}`, { 'is-active': m.id === machineId }]"
        @click="machineId = m.id"
      >
        <IndLed :tone="statusTone('machine', m.status)" />
        <b class="mono">{{ m.macCode }}</b>
        <span>{{ statusLabel('machine', m.status) }}</span>
      </button>
    </div>

    <div class="terminal__body">
      <section class="terminal__main">
        <IndPanel :title="`当前加工任务 · ${machine?.macName || ''}`" :sub="live.programNo || '无程序'">
          <template #tools>
            <IndTag :tone="statusTone('machine', machine?.status)" led>
              {{ statusLabel('machine', machine?.status) }}
            </IndTag>
            <span class="mono mute" style="font-size: 11px">采集于 {{ timeOnly(live.ts) || '—' }}</span>
          </template>

          <div v-if="!currentTask" class="terminal__idle">
            <IndEmpty text="本机台当前没有待加工任务" hint="请到「派工管理」为本机台派工" />
          </div>

          <div v-else class="task">
            <div class="task__head">
              <div>
                <div class="task__order mono">{{ currentTask.orderNo }}</div>
                <div class="task__proc">
                  OP{{ currentTask.seqNo }} {{ currentTask.procName }}
                  <IndTag v-if="currentTask.isKeyProc" tone="alarm">关键工序</IndTag>
                  <IndTag v-if="currentTask.isInspection" tone="maint">需检验</IndTag>
                </div>
              </div>
              <div class="task__program">
                <span class="mute">NC 程序</span>
                <b class="mono">{{ currentTask.ncProgramNo || '—' }}</b>
                <span class="mute mono">{{ currentTask.ncProgramVer || '' }}</span>
              </div>
            </div>

            <div class="task__numbers">
              <div class="tnum">
                <span>计划数量</span>
                <b class="mono">{{ num(currentTask.planQty) }}</b>
              </div>
              <div class="tnum">
                <span>已完成</span>
                <b class="mono c-run">{{ num(currentTask.finishedQty) }}</b>
              </div>
              <div class="tnum">
                <span>剩余</span>
                <b class="mono c-idle">{{ num(Math.max(0, currentTask.planQty - currentTask.finishedQty)) }}</b>
              </div>
              <div class="tnum">
                <span>完成率</span>
                <b class="mono">{{ progress }}%</b>
              </div>
              <div class="tnum">
                <span>定额单件</span>
                <b class="mono">{{ num(currentTask.pieceTime, 2) }}<em>min</em></b>
              </div>
            </div>

            <IndBar :value="progress" :tone="progress >= 100 ? 'run' : 'primary'" height="10" :label="`${progress}%`" />

            <div class="task__meta">
              <span>指派机台 <b class="mono">{{ currentTask.machineCode || '—' }}</b></span>
              <span>操作工 <b>{{ currentTask.employeeName || '—' }}</b></span>
              <span>班次 <b>{{ currentTask.shiftName || '—' }}</b></span>
              <span>计划开始 <b class="mono">{{ dateTime(currentTask.planStart) }}</b></span>
              <span>刀具要求 <b>{{ currentTask.toolRequirement || '按工艺卡' }}</b></span>
            </div>

            <div v-if="auth.can('terminal')" class="task__actions">
              <IndButton variant="primary" size="xl" :disabled="currentTask.status === 'RUNNING'" @click="openStart">
                ▶ 开 工
              </IndButton>
              <IndButton variant="ok" size="xl" @click="openReport('report')">报 工</IndButton>
              <IndButton variant="warn" size="xl" @click="openReport('finish')">完 工</IndButton>
              <IndButton variant="danger" size="xl" @click="openStop">停 机</IndButton>
            </div>
            <div v-else class="task__actions-lock mute">当前账号为只读权限，报工操作请使用操作工账号（operator）</div>
          </div>

          <div v-if="queued.length" class="queue">
            <div class="ind-section-title">后续排队任务（{{ queued.length }} 道）</div>
            <div v-for="t in queued" :key="t.id" class="queue__item">
              <b class="mono">{{ t.orderNo }}</b>
              <span>OP{{ t.seqNo }} {{ t.procName }}</span>
              <span class="mute mono">{{ num(t.planQty) }} 件</span>
              <span class="mute mono">{{ dateTime(t.planStart) }}</span>
              <IndTag :tone="statusTone('dispatch', t.status)">{{ statusLabel('dispatch', t.status) }}</IndTag>
            </div>
          </div>
        </IndPanel>
      </section>

      <aside class="terminal__side">
        <IndPanel title="安灯呼叫" sub="ANDON">
          <div v-if="tasks.openAndons?.length" class="open-andon">
            <div v-for="a in tasks.openAndons" :key="a.id" class="open-andon__item">
              <IndLed tone="alarm" />
              <span>{{ a.andonType }}</span>
              <span class="mute ellipsis">{{ a.content }}</span>
              <span class="mono mute">{{ fromNow(a.callTime) }}</span>
            </div>
          </div>
          <div v-if="auth.can('terminal')" class="andon-btns">
            <IndButton variant="danger" size="lg" block @click="openAndon('质量异常')">质量异常</IndButton>
            <IndButton variant="warn" size="lg" block @click="openAndon('设备异常')">设备异常</IndButton>
            <IndButton size="lg" block @click="openAndon('物料短缺')">物料短缺</IndButton>
            <IndButton size="lg" block @click="openAndon('工艺求助')">工艺求助</IndButton>
            <IndButton size="lg" block @click="openAndon('刀具需求')">刀具需求</IndButton>
          </div>
        </IndPanel>

        <IndPanel title="机台状态" sub="STATE">
          <div v-if="auth.can('terminal')" class="state-btns">
            <IndButton size="lg" @click="setState('IDLE', '终端手动置待机')">待 机</IndButton>
            <IndButton size="lg" @click="setState('MAINT', '终端发起保养')">保 养</IndButton>
            <IndButton size="lg" @click="setState('OFFLINE', '终端置离线')">离 线</IndButton>
            <IndButton variant="ok" size="lg" @click="setState('RUN', '终端恢复运行')">恢复运行</IndButton>
          </div>
          <div class="state-info mono">
            <div><span class="mute">当前状态</span><b>{{ statusLabel('machine', machine?.status) }}</b></div>
            <div>
              <span class="mute">状态时长</span>
              <b>{{ live.statusMinutes !== undefined ? `${Math.round(live.statusMinutes)} 分钟` : '—' }}</b>
            </div>
            <div><span class="mute">主轴 / 负载</span><b>{{ num(live.spindleSpeed) }} rpm / {{ num(live.spindleLoad) }}%</b></div>
          </div>
        </IndPanel>

        <IndPanel title="在机刀具寿命" sub="TOOL LIFE">
          <div v-if="!tasks.tools?.length" class="mute" style="font-size: 12px">该机台暂无刀具记录</div>
          <div v-for="t in tasks.tools || []" :key="t.id" class="toolrow">
            <div class="between">
              <b class="mono">{{ t.toolCode }}</b>
              <IndTag :tone="t.lifeRatio >= 85 ? 'stop' : t.lifeRatio >= 60 ? 'idle' : 'run'">
                {{ num(t.lifeRatio, 1) }}%
              </IndTag>
            </div>
            <div class="mute" style="font-size: 11px">{{ t.toolName }} · 刀补 {{ t.offsetNo || '—' }}</div>
            <IndBar :value="t.lifeRatio" :tone="t.lifeRatio >= 85 ? 'stop' : t.lifeRatio >= 60 ? 'idle' : 'run'" height="6" />
          </div>
        </IndPanel>

        <IndPanel title="本班产出" sub="SHIFT OUTPUT">
          <div class="shift-out">
            <div class="shift-out__big">
              <b class="mono">{{ num(tasks.todayQty || 0) }}</b>
              <span class="mute">件</span>
            </div>
            <div class="shift-out__sub">
              <span>不良 <b class="mono c-stop">{{ num(tasks.todayDefect || 0) }}</b></span>
              <span>合格率 <b class="mono c-run">{{ tasks.todayQualifiedRate ? pct(tasks.todayQualifiedRate) : '—' }}</b></span>
            </div>
          </div>
          <IndChart :option="hourlyOption" height="104" />
        </IndPanel>
      </aside>
    </div>

    <IndModal v-model="startOpen" title="开工确认" :width="520">
      <div class="confirm">
        <div class="confirm__row"><span>工单</span><b class="mono">{{ currentTask?.orderNo }}</b></div>
        <div class="confirm__row"><span>工序</span><b>OP{{ currentTask?.seqNo }} {{ currentTask?.procName }}</b></div>
        <div class="confirm__row"><span>机台</span><b class="mono">{{ machine?.macCode }}</b></div>
      </div>
      <IndField label="机床实际执行的 NC 程序号" required>
        <IndInput v-model="startForm.ncProgramNo" placeholder="例如 O1050_VMC_NC" />
      </IndField>
      <p class="mute" style="font-size: 11.5px; margin-top: 10px">
        系统会校验该程序号与工艺路线要求是否一致：不一致将拒绝开工，防止「程序错版」造成批量报废。
      </p>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="primary" @click="doStart">确认开工</IndButton>
      </template>
    </IndModal>

    <IndModal v-model="reportOpen" :title="reportMode === 'finish' ? '完工报工' : '加工报工'" :width="600">
      <div class="confirm">
        <div class="confirm__row"><span>派工单</span><b class="mono">{{ currentTask?.dispatchNo || currentTask?.id }}</b></div>
        <div class="confirm__row">
          <span>工单/工序</span>
          <b>{{ currentTask?.orderNo }} OP{{ currentTask?.seqNo }} {{ currentTask?.procName }}</b>
        </div>
      </div>
      <div class="form-grid">
        <IndField label="本次报工数量" required>
          <IndInput v-model="reportForm.qty" type="number" />
        </IndField>
        <IndField label="不良数量">
          <IndInput v-model="reportForm.defectQty" type="number" />
        </IndField>
        <IndField label="不良原因">
          <IndSelect
            v-model="reportForm.scrapReason"
            :options="['尺寸超差', '表面粗糙度', '碰伤划伤', '崩刃刀痕', '毛刺未清', '形位公差']"
            placeholder="无不良"
          />
        </IndField>
        <IndField label="本次加工时长（分钟）">
          <IndInput v-model="reportForm.runMinutes" type="number" placeholder="留空按开工时间自动计算" />
        </IndField>
        <IndField label="实际使用刀具">
          <IndSelect
            v-model="reportForm.toolCode"
            :options="(tasks.tools || []).map((t) => ({ value: t.toolCode, label: `${t.toolCode} ${t.toolName}` }))"
            placeholder="未记录"
          />
        </IndField>
        <IndField label="刀补号">
          <IndInput v-model="reportForm.toolOffsetNo" placeholder="例如 H01" />
        </IndField>
      </div>
      <IndField label="备注" style="margin-top: 12px">
        <IndInput v-model="reportForm.remark" placeholder="选填" />
      </IndField>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton :variant="reportMode === 'finish' ? 'warn' : 'ok'" @click="submitReport">
          {{ reportMode === 'finish' ? '提交完工' : '提交报工' }}
        </IndButton>
      </template>
    </IndModal>

    <IndModal v-model="stopOpen" title="停机登记" :width="560">
      <div class="form-grid">
        <IndField label="停机原因" required>
          <IndSelect
            v-model="stopForm.reasonCode"
            :options="Object.entries(STOP_REASON).map(([value, text]) => ({ value, label: text }))"
            placeholder="请选择"
          />
        </IndField>
        <IndField label="停机时长（分钟）" required>
          <IndInput v-model="stopForm.durationMin" type="number" />
        </IndField>
        <IndField label="开始时间">
          <IndInput v-model="stopForm.startTime" />
        </IndField>
        <IndField label="结束时间">
          <IndInput v-model="stopForm.endTime" />
        </IndField>
      </div>
      <IndField label="补充说明" style="margin-top: 12px">
        <IndInput v-model="stopForm.remark" placeholder="例如：更换 φ12 立铣刀并重新对刀" />
      </IndField>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="danger" @click="submitStop">记录停机</IndButton>
      </template>
    </IndModal>

    <IndModal v-model="andonOpen" title="呼叫安灯" :width="560">
      <div class="form-grid">
        <IndField label="呼叫类型" required>
          <IndSelect
            v-model="andonForm.andonType"
            :options="['质量异常', '设备异常', '物料短缺', '工艺求助', '刀具需求']"
            placeholder="请选择"
          />
        </IndField>
        <IndField label="呼叫等级">
          <IndSelect v-model="andonForm.level" :options="['一级', '二级', '三级']" placeholder="一级" />
        </IndField>
      </div>
      <IndField label="呼叫内容" style="margin-top: 12px">
        <IndInput v-model="andonForm.content" placeholder="简要描述现场问题" />
      </IndField>
      <p class="mute" style="font-size: 11.5px; margin-top: 10px">
        一级呼叫通知班组、二级通知车间、三级通知厂部；响应与关闭时长会进入安灯效率统计。
      </p>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="danger" @click="submitAndon">立即呼叫</IndButton>
      </template>
    </IndModal>
  </div>
</template>

<style scoped>
.terminal { height: 100%; display: flex; flex-direction: column; gap: 10px; padding: 10px; }
.terminal__machines { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 2px; flex: none; }
.mchip {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 40px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-bottom: 3px solid var(--c-offline);
  background: var(--bg-panel);
  color: var(--text-dim);
  font-size: 12.5px;
  cursor: pointer;
  white-space: nowrap;
  border-radius: var(--radius);
}
.mchip b { color: var(--text-strong); font-size: 13px; }
.mchip--run { border-bottom-color: var(--c-run); }
.mchip--idle { border-bottom-color: var(--c-idle); }
.mchip--stop,
.mchip--alarm { border-bottom-color: var(--c-stop); }
.mchip--maint { border-bottom-color: var(--c-maint); }
.mchip.is-active {
  background: linear-gradient(180deg, rgba(10, 162, 255, 0.18), rgba(10, 162, 255, 0.04));
  border-color: var(--c-primary-dim);
  color: var(--text-strong);
}

.terminal__body {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) 330px;
  gap: 10px;
}
.terminal__main { min-height: 0; }
.terminal__main :deep(.ind-panel) { height: 100%; }
.terminal__side { display: grid; grid-template-rows: auto auto minmax(0, 1fr) auto; gap: 10px; overflow: auto; }
.terminal__idle { display: grid; place-items: center; height: 100%; }

.task { display: flex; flex-direction: column; gap: 14px; height: 100%; }
.task__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.task__order { font-size: 18px; letter-spacing: 0.08em; color: var(--text-strong); }
.task__proc { display: flex; align-items: center; gap: 8px; font-size: 15px; margin-top: 3px; }
.task__program { text-align: right; display: flex; flex-direction: column; gap: 2px; }
.task__program b { font-size: 17px; color: var(--c-cyan); }

.task__numbers { display: grid; grid-template-columns: repeat(5, 1fr); gap: 10px; }
.tnum {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-left: 3px solid var(--c-primary-dim);
  background: var(--sheen);
}
.tnum span { font-size: 11px; color: var(--text-mute); }
.tnum b { font-size: 26px; line-height: 1.1; color: var(--text-strong); }
.tnum em { font-style: normal; font-size: 11px; color: var(--text-mute); margin-left: 3px; }

.task__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  font-size: 12px;
  color: var(--text-dim);
  padding-top: 10px;
  border-top: 1px dashed var(--line);
}
.task__meta b { color: var(--text); }

.task__actions { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin-top: auto; }

.queue { margin-top: 16px; padding-top: 12px; border-top: 1px solid var(--line); }
.queue__item {
  display: grid;
  grid-template-columns: 132px 150px 78px 130px 88px;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  font-size: 12px;
  border-bottom: 1px solid var(--line-soft);
}
.queue__item b { color: var(--text); }

.open-andon { display: flex; flex-direction: column; gap: 5px; margin-bottom: 10px; }
.open-andon__item {
  display: grid;
  grid-template-columns: 10px 66px 1fr 62px;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  padding: 5px 7px;
  border: 1px solid rgba(255, 45, 85, 0.4);
  background: rgba(255, 45, 85, 0.06);
}
.andon-btns { display: flex; flex-direction: column; gap: 7px; }
.state-btns { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; }
.state-info { display: flex; flex-direction: column; gap: 5px; margin-top: 10px; font-size: 11.5px; }
.state-info > div { display: flex; justify-content: space-between; }
.state-info b { color: var(--text); }

.toolrow { padding: 7px 0; border-bottom: 1px dashed var(--line-soft); display: flex; flex-direction: column; gap: 3px; }
.toolrow:last-child { border-bottom: none; }

.shift-out { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.shift-out__big { display: flex; align-items: baseline; gap: 4px; }
.shift-out__big b { font-size: 34px; color: var(--text-strong); line-height: 1; }
.shift-out__sub { display: flex; flex-direction: column; gap: 3px; font-size: 12px; align-items: flex-end; }

.confirm { display: flex; flex-direction: column; gap: 5px; margin-bottom: 12px; }
.confirm__row { display: flex; gap: 10px; font-size: 12.5px; }
.confirm__row span { color: var(--text-mute); width: 76px; }
.confirm__row b { color: var(--text); }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
</style>
