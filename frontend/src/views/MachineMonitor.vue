<script setup>
/**
 * 设备监控：左侧机台列表 + 右侧单机深度监视。
 *
 * 同时展示两条时间尺度：
 *   · 实时趋势：SSE 2 秒推送，窗口约 3 分钟 —— 看「现在稳不稳」
 *   · 历史趋势：每 30 秒落库，窗口 4 小时 —— 看「这一班有没有异常」
 * 这是车间设备工程师最常用的两个视角。
 */
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndLed from '@/components/ind/IndLed.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndGauge from '@/components/ind/IndGauge.vue';
import IndChart from '@/components/ind/IndChart.vue';
import IndBar from '@/components/ind/IndBar.vue';
import IndEmpty from '@/components/ind/IndEmpty.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndField from '@/components/ind/IndField.vue';
import IndTimeline from '@/components/ind/IndTimeline.vue';
import { api } from '@/api';
import { CHART_COLORS, lineOption, ringOption } from '@/utils/chart';
import { MACHINE_STATUS, DISPATCH_STATUS, FAULT_TYPE_TONE, label as statusLabel, tone as statusTone } from '@/utils/status';
import { dateOnly, dateTime, fromNow, num, pct, timeOnly } from '@/utils/format';

const machines = ref([]);
const activeId = ref(null);
const detail = ref(null);
const telemetry = ref([]);
const statusLogs = ref([]);
const statusDist = ref({});
const oee = ref(null);
const liveBuffer = ref([]);
const filterStatus = ref('');
const filterWc = ref('');
const loading = ref(true);
let sse = null;
let timer = null;

const STATUS_OPTIONS = Object.entries(MACHINE_STATUS).map(([value, v]) => ({ value, label: v.label }));

const filtered = computed(() =>
  machines.value.filter(
    (m) => (!filterStatus.value || m.status === filterStatus.value) && (!filterWc.value || String(m.workCenterId) === String(filterWc.value)),
  ),
);

const workCenters = computed(() => {
  const map = new Map();
  machines.value.forEach((m) => {
    if (m.workCenterId) map.set(m.workCenterId, m.workCenterName);
  });
  return [...map.entries()].map(([value, label]) => ({ value, label }));
});

const active = computed(() => machines.value.find((m) => m.id === activeId.value) || null);
const live = computed(() => detail.value?.live || active.value?.live || {});

const realtimeOption = computed(() => {
  const buf = liveBuffer.value;
  return lineOption({
    x: buf.map((p) => p.t),
    series: [
      { name: '主轴转速', data: buf.map((p) => p.speed), color: CHART_COLORS.cyan, area: true },
      { name: '主轴负载 %', data: buf.map((p) => p.load), color: CHART_COLORS.idle },
    ],
    grid: { left: 52, right: 16, top: 32, bottom: 22 },
  });
});

const historyOption = computed(() => {
  const list = telemetry.value.slice(-240);
  return lineOption({
    x: list.map((t) => timeOnly(t.ts)),
    series: [
      { name: '主轴转速', data: list.map((t) => t.spindleSpeed), color: CHART_COLORS.cyan },
      { name: '主轴负载 %', data: list.map((t) => t.spindleLoad), color: CHART_COLORS.idle },
      { name: '功率 kW', data: list.map((t) => t.powerKw), color: CHART_COLORS.maint },
    ],
    grid: { left: 52, right: 16, top: 32, bottom: 22 },
  });
});

const distOption = computed(() => {
  const map = statusDist.value.byStatus || {};
  const data = Object.entries(map).map(([k, v]) => ({
    name: statusLabel('machine', k),
    value: Math.round(v),
    color: CHART_COLORS[statusTone('machine', k)] || CHART_COLORS.primary,
  }));
  return ringOption({ data });
});

const statusTimeline = computed(() =>
  statusLogs.value
    .slice()
    .reverse()
    .slice(0, 12)
    .map((l) => ({
      title: `${statusLabel('machine', l.status)} · ${l.reason || '—'}`,
      time: dateTime(l.startTime),
      text: `${l.orderNo || '无工单'} ${l.ncProgramNo ? `· 程序 ${l.ncProgramNo}` : ''}`,
      tone: statusTone('machine', l.status),
    })),
);

async function loadMachines() {
  machines.value = await api.machine.live();
  if (!activeId.value && machines.value.length) {
    // 默认选中「有在制任务」的机台，让打开页面就能看到有效信息
    const busy = machines.value.find((m) => m.live && m.live.orderNo);
    activeId.value = (busy || machines.value[0]).id;
  }
  loading.value = false;
}

async function loadDetail() {
  if (!activeId.value) return;
  const id = activeId.value;
  detail.value = await api.machine.detail(id);
  telemetry.value = await api.machine.telemetry(id, 4);
  statusLogs.value = await api.machine.statusLogs(id, 3);
  statusDist.value = await api.machine.statusDistribution(id, 7);
  oee.value = await api.machine.oee(id, 7);
  liveBuffer.value = (detail.value.liveHistory || []).slice(-90);
}

function subscribe() {
  try {
    sse = api.board.stream();
    sse.addEventListener('machines', (e) => {
      try {
        const list = JSON.parse(e.data);
        if (Array.isArray(list) && list.length) {
          machines.value = machines.value.map((m) => {
            const hit = list.find((x) => x.machineId === m.id);
            return hit ? { ...m, status: hit.status, live: { ...m.live, ...hit } } : m;
          });
          const me = list.find((x) => x.machineId === activeId.value);
          if (me) {
            liveBuffer.value = [
              ...liveBuffer.value.slice(-89),
              { t: timeOnly(me.ts), speed: Number(me.spindleSpeed || 0), load: Number(me.spindleLoad || 0) },
            ];
          }
        }
      } catch {
        /* 忽略 */
      }
    });
  } catch {
    /* 忽略 */
  }
}

watch(activeId, () => {
  detail.value = null;
  liveBuffer.value = [];
  loadDetail().catch(() => {});
});

onMounted(async () => {
  await loadMachines();
  await loadDetail().catch(() => {});
  subscribe();
  // 历史曲线每 15 秒刷新一次（遥测每 30 秒落库）
  timer = setInterval(() => {
    if (activeId.value) {
      api.machine.telemetry(activeId.value, 4).then((t) => (telemetry.value = t)).catch(() => {});
      api.machine.detail(activeId.value).then((d) => (detail.value = d)).catch(() => {});
    }
  }, 15000);
});

onBeforeUnmount(() => {
  if (sse) sse.close();
  if (timer) clearInterval(timer);
});
</script>

<template>
  <div class="page monitor-page">
    <!-- 机台列表 -->
    <IndPanel title="机台清单" :sub="`共 ${machines.length} 台`" flush>
      <template #tools>
        <IndButton variant="ghost" size="sm" @click="loadMachines">刷新</IndButton>
      </template>
      <div class="mlist-filter">
        <IndField label="工作中心">
          <IndSelect v-model="filterWc" :options="workCenters" placeholder="全部" />
        </IndField>
        <IndField label="运行状态">
          <IndSelect v-model="filterStatus" :options="STATUS_OPTIONS" placeholder="全部" />
        </IndField>
      </div>
      <div class="mlist">
        <button
          v-for="m in filtered"
          :key="m.id"
          class="mlist__item"
          :class="{ 'is-active': m.id === activeId, [`mlist__item--${statusTone('machine', m.status)}`]: true }"
          @click="activeId = m.id"
        >
          <IndLed :tone="statusTone('machine', m.status)" />
          <span class="mlist__code mono">{{ m.macCode }}</span>
          <span class="mlist__name ellipsis">{{ m.macName }}</span>
          <span class="mlist__status">{{ statusLabel('machine', m.status) }}</span>
          <span class="mlist__oc mono">{{ m.live?.orderNo ? m.live.orderNo.slice(-4) : '—' }}</span>
        </button>
        <IndEmpty v-if="!filtered.length" text="没有符合条件的机台" />
      </div>
    </IndPanel>

    <!-- 单机详情 -->
    <div class="monitor" style="display: flex; flex-direction: column; gap: 12px; min-height: 0; overflow: auto">
      <IndPanel v-if="active" plain>
        <div class="mhead">
          <div class="mhead__left">
            <IndLed :tone="statusTone('machine', active.status)" size="lg" />
            <div>
              <div class="mhead__title">
                <b class="mono">{{ active.macCode }}</b>
                <span>{{ active.macName }}</span>
                <IndTag :tone="statusTone('machine', active.status)" led>{{ statusLabel('machine', active.status) }}</IndTag>
              </div>
              <div class="mhead__meta mute mono">
                {{ active.macModel }} · {{ active.brand }} · {{ active.cncType }} ·
                {{ active.axisCount }} 轴 · {{ active.controllerType }} · {{ active.protocol }} · {{ active.ipAddr }}
              </div>
            </div>
          </div>
          <div class="mhead__right">
            <div class="mstat"><span>状态说明</span><b>{{ live.statusReason || '—' }}</b></div>
            <div class="mstat"><span>刀补号</span><b class="mono">{{ live.toolNo || '—' }}</b></div>
            <div class="mstat"><span>采集时间</span><b class="mono">{{ timeOnly(live.ts) || '—' }}</b></div>
          </div>
        </div>
      </IndPanel>

      <!-- 实时量 -->
      <div class="mgrid">
        <IndPanel title="实时工况" sub="REAL-TIME">
          <div class="live-grid">
            <div class="live-cell">
              <span class="live-cell__k">主轴转速</span>
              <b class="live-cell__v mono">{{ num(live.spindleSpeed) }}<em>rpm</em></b>
              <IndBar :value="live.spindleSpeed" :max="active?.maxSpindleSpeed || 12000" tone="run" height="5" />
            </div>
            <div class="live-cell">
              <span class="live-cell__k">进给速度</span>
              <b class="live-cell__v mono">{{ num(live.feedRate) }}<em>mm/min</em></b>
              <IndBar :value="live.feedRate" :max="3000" tone="run" height="5" />
            </div>
            <div class="live-cell">
              <span class="live-cell__k">进给倍率</span>
              <b class="live-cell__v mono">{{ num(live.feedOverride) }}<em>%</em></b>
              <IndBar :value="live.feedOverride" :max="150" tone="primary" height="5" />
            </div>
            <div class="live-cell">
              <span class="live-cell__k">主轴负载</span>
              <b class="live-cell__v mono">{{ num(live.spindleLoad) }}<em>%</em></b>
              <IndBar
                :value="live.spindleLoad"
                tone="idle"
                height="5"
              />
            </div>
            <div class="live-cell">
              <span class="live-cell__k">X 轴</span>
              <b class="live-cell__v mono">{{ num(live.axisX, 3) }}</b>
            </div>
            <div class="live-cell">
              <span class="live-cell__k">Y 轴</span>
              <b class="live-cell__v mono">{{ num(live.axisY, 3) }}</b>
            </div>
            <div class="live-cell">
              <span class="live-cell__k">Z 轴</span>
              <b class="live-cell__v mono">{{ num(live.axisZ, 3) }}</b>
            </div>
            <div class="live-cell">
              <span class="live-cell__k">主轴功率</span>
              <b class="live-cell__v mono">{{ num(live.powerKw, 1) }}<em>kW</em></b>
            </div>
          </div>
          <div class="prog-row">
            <div>
              <span class="mute">当前程序</span>
              <b class="mono c-cyan">{{ live.programNo || '—' }}</b>
            </div>
            <div>
              <span class="mute">当前工单 / 工序</span>
              <b class="mono">{{ live.orderNo || '—' }} / {{ live.procName || '—' }}</b>
            </div>
            <div>
              <span class="mute">已加工</span>
              <b class="mono">{{ num(live.partCount) }} / {{ num(live.planQty) }} 件</b>
            </div>
            <div>
              <span class="mute">报警码</span>
              <b class="mono" :class="live.alarmCode ? 'c-alarm' : ''">{{ live.alarmCode || '无' }}</b>
            </div>
          </div>
        </IndPanel>

        <IndPanel title="单机 OEE（近 7 天）" sub="OEE">
          <div class="oee-wrap">
            <IndGauge
              :value="oee ? oee.oee : 0"
              :target="oee ? oee.target : 85"
              label="OEE"
              :height="150"
              :tone="oee && oee.oee >= oee.target ? 'run' : 'idle'"
            />
            <div class="oee-mini">
              <div class="oee-mini__row"><span>开动率</span><b class="mono">{{ oee ? pct(oee.availability) : '—' }}</b></div>
              <div class="oee-mini__row"><span>性能率</span><b class="mono">{{ oee ? pct(oee.performance) : '—' }}</b></div>
              <div class="oee-mini__row"><span>合格率</span><b class="mono">{{ oee ? pct(oee.quality) : '—' }}</b></div>
              <div class="oee-mini__row"><span>产出</span><b class="mono">{{ oee ? num(oee.qty) : '—' }} 件</b></div>
              <div class="oee-mini__row"><span>与目标差</span><b class="mono" :class="oee && oee.gap >= 0 ? 'c-run' : 'c-stop'">
                {{ oee ? `${oee.gap > 0 ? '+' : ''}${oee.gap}%` : '—' }}
              </b></div>
            </div>
          </div>
        </IndPanel>
      </div>

      <!-- 趋势 -->
      <div class="mgrid">
        <IndPanel title="实时趋势" sub="2 秒采样 · 近 3 分钟">
          <IndChart :option="realtimeOption" height="160" />
        </IndPanel>
        <IndPanel title="历史趋势" sub="30 秒落库 · 近 4 小时">
          <IndChart :option="historyOption" height="160" />
        </IndPanel>
      </div>

      <!-- 工单 / 刀具 / 状态 / 故障 -->
      <div class="mgrid mgrid--three">
        <IndPanel title="在制派工" sub="ACTIVE DISPATCH">
          <div v-if="!detail?.dispatches?.length" class="mute" style="font-size: 12px">当前无在制派工任务</div>
          <div v-for="d in detail?.dispatches || []" :key="d.id" class="dispatch-item">
            <div class="between">
              <b class="mono">{{ d.dispatchNo }}</b>
              <IndTag :tone="statusTone('dispatch', d.status)">{{ statusLabel('dispatch', d.status) }}</IndTag>
            </div>
            <div class="dim" style="font-size: 12px">
              {{ d.orderNo }} · OP{{ d.seqNo }} {{ d.procName }} · {{ d.employeeName }}
            </div>
            <div class="mute mono" style="font-size: 11px">
              程序 {{ d.ncProgramNo || '—' }} · 计划 {{ num(d.planQty) }} 件 · {{ dateTime(d.planStart) }}
            </div>
          </div>
        </IndPanel>

        <IndPanel title="在机刀具" sub="MOUNTED TOOLS">
          <div v-if="!detail?.tools?.length" class="mute" style="font-size: 12px">该机台未挂载刀具记录</div>
          <div v-for="t in detail?.tools || []" :key="t.id" class="tool-item">
            <div class="between">
              <b class="mono">{{ t.toolCode }}</b>
              <IndTag :tone="statusTone('tool', t.status)" led>{{ statusLabel('tool', t.status) }}</IndTag>
            </div>
            <div class="dim" style="font-size: 12px">{{ t.toolName }} · {{ t.spec }} · 刀补 {{ t.offsetNo || '—' }}</div>
            <div class="center gap-2" style="margin-top: 4px">
              <IndBar :value="t.lifeRatio" :tone="t.lifeRatio >= 85 ? 'stop' : t.lifeRatio >= 60 ? 'idle' : 'run'" height="6" />
              <span class="mono" style="font-size: 11px; width: 44px; text-align: right">{{ num(t.lifeRatio, 1) }}%</span>
            </div>
          </div>
        </IndPanel>

        <IndPanel title="今日产出" sub="TODAY OUTPUT">
          <div class="today">
            <div class="today__big">
              <b class="mono">{{ num(detail?.todayOutput || 0) }}</b>
              <span class="mute">件</span>
            </div>
            <div class="today__rows">
              <div><span>不良</span><b class="mono c-stop">{{ num(detail?.todayDefect || 0) }} 件</b></div>
              <div><span>合格率</span><b class="mono c-run">{{ detail?.todayQualifiedRate ? pct(detail.todayQualifiedRate) : '—' }}</b></div>
              <div><span>OEE 目标</span><b class="mono">{{ active?.oeeTarget || '—' }}%</b></div>
            </div>
          </div>
          <div class="ind-section-title" style="margin-top: 12px">近 7 天状态占比</div>
          <IndChart :option="distOption" height="120" />
        </IndPanel>
      </div>

      <div class="mgrid">
        <IndPanel title="状态切换履历" sub="STATE LOG">
          <IndTimeline v-if="statusTimeline.length" :items="statusTimeline" />
          <div v-else class="mute" style="font-size: 12px">暂无状态记录</div>
        </IndPanel>
        <IndPanel title="故障履历" sub="FAULT HISTORY">
          <div v-if="!detail?.faults?.length" class="mute" style="font-size: 12px">近一年无故障记录</div>
          <div v-for="f in detail?.faults || []" :key="f.id" class="fault-item">
            <div class="between">
              <span class="center gap-2">
                <IndTag :tone="FAULT_TYPE_TONE[f.faultType] || 'plain'">{{ f.faultType }}</IndTag>
                <b class="mono">{{ f.faultNo }}</b>
              </span>
              <span class="mono mute" style="font-size: 11px">{{ dateOnly(f.reportTime) }}</span>
            </div>
            <div style="font-size: 12px; margin-top: 3px">{{ f.faultDesc }}</div>
            <div class="mute" style="font-size: 11px">
              处置：{{ f.solution || '处理中' }} · 停机 {{ num(f.downMinutes) }} 分钟 · {{ f.repairUser || '—' }}
            </div>
          </div>
        </IndPanel>
      </div>
    </div>
  </div>
</template>

<style scoped>
.monitor-page {
  display: grid;
  grid-template-columns: 292px 1fr;
  gap: 12px;
  height: 100%;
}

.mlist-filter {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding: 10px;
  border-bottom: 1px solid var(--line);
  background: var(--bg-well);
}
.mlist { display: flex; flex-direction: column; }
.mlist__item {
  display: grid;
  grid-template-columns: 10px 62px 1fr 48px 34px;
  align-items: center;
  gap: 7px;
  height: 34px;
  padding: 0 10px;
  background: transparent;
  border: none;
  border-bottom: 1px solid var(--line-soft);
  border-left: 2px solid transparent;
  cursor: pointer;
  text-align: left;
  font-size: 12px;
  color: var(--text-dim);
}
.mlist__item:hover { background: var(--bg-hover); color: var(--text); }
.mlist__item.is-active {
  background: rgba(10, 162, 255, 0.12);
  border-left-color: var(--c-primary);
  color: var(--text-strong);
}
.mlist__item--run { border-left-color: rgba(31, 201, 107, 0.45); }
.mlist__item--stop,
.mlist__item--alarm { border-left-color: rgba(255, 77, 79, 0.5); }
.mlist__item--idle { border-left-color: rgba(245, 165, 36, 0.45); }
.mlist__code { color: var(--text-strong); }
.mlist__name { font-size: 11.5px; }
.mlist__status { font-size: 11px; }
.mlist__oc { font-size: 10px; color: var(--text-mute); }

.mhead { display: flex; align-items: center; justify-content: space-between; gap: 20px; flex-wrap: wrap; }
.mhead__left { display: flex; align-items: center; gap: 12px; }
.mhead__title { display: flex; align-items: center; gap: 10px; }
.mhead__title b { font-size: 18px; color: var(--text-strong); letter-spacing: 0.06em; }
.mhead__title span { font-size: 14px; }
.mhead__meta { font-size: 11px; margin-top: 3px; }
.mhead__right { display: flex; gap: 22px; }
.mstat { display: flex; flex-direction: column; gap: 2px; font-size: 11px; }
.mstat span { color: var(--text-mute); }
.mstat b { font-size: 12px; color: var(--text); }

.mgrid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.mgrid--three { grid-template-columns: 1fr 1fr 1fr; }

.live-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; }
.live-cell {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 7px 9px;
  border: 1px solid var(--line);
  background: var(--sheen);
  border-radius: var(--radius);
}
.live-cell__k { font-size: 10.5px; color: var(--text-mute); letter-spacing: 0.06em; }
.live-cell__v { font-size: 17px; color: var(--text-strong); }
.live-cell__v em { font-style: normal; font-size: 10px; color: var(--text-mute); margin-left: 3px; }

.prog-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid var(--line);
}
.prog-row > div { display: flex; flex-direction: column; gap: 2px; font-size: 11.5px; }
.prog-row b { font-size: 12.5px; color: var(--text); }

.oee-wrap { display: grid; grid-template-columns: 1fr 120px; align-items: center; }
.oee-mini { display: flex; flex-direction: column; gap: 7px; }
.oee-mini__row { display: flex; justify-content: space-between; font-size: 11.5px; color: var(--text-dim); }
.oee-mini__row b { color: var(--text-strong); }

.dispatch-item,
.tool-item,
.fault-item {
  padding: 8px 0;
  border-bottom: 1px dashed var(--line-soft);
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.dispatch-item:last-child,
.tool-item:last-child,
.fault-item:last-child { border-bottom: none; }

.today { display: flex; align-items: center; gap: 18px; }
.today__big { display: flex; align-items: baseline; gap: 4px; }
.today__big b { font-size: 40px; color: var(--text-strong); line-height: 1; }
.today__rows { display: flex; flex-direction: column; gap: 5px; font-size: 12px; }
.today__rows > div { display: flex; gap: 10px; }
.today__rows span { color: var(--text-mute); width: 60px; }

/* 窄屏（笔记本/平板）改为上下堆叠，避免机台清单把详情区挤到不可用 */
@media (max-width: 1280px) {
  .monitor-page { display: block; }
  .monitor-page > * { margin-bottom: 12px; }
}
</style>
