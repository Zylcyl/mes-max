<script setup>
/**
 * 车间实时看板（大屏）。
 *
 * 信息优先级（从上到下、从左到右）：
 *   1. 机台状态矩阵 —— 车间主任第一眼要看的就是「哪台机停了」
 *   2. OEE 达成与今日产量 —— 今天到底干得怎么样
 *   3. 安灯呼叫与实时报警 —— 有什么需要立刻处理
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndKpi from '@/components/ind/IndKpi.vue';
import IndLed from '@/components/ind/IndLed.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndGauge from '@/components/ind/IndGauge.vue';
import IndChart from '@/components/ind/IndChart.vue';
import MachineCard from '@/components/MachineCard.vue';
import IndEmpty from '@/components/ind/IndEmpty.vue';
import { api } from '@/api';
import { barOption, CHART_COLORS } from '@/utils/chart';
import { ANDON_TYPE_TONE, machineTone } from '@/utils/status';
import { fromNow, num, pct } from '@/utils/format';
import { useToast } from '@/stores/toast';

const toast = useToast();
const loading = ref(true);
const kpi = ref({});
const statusCount = ref({});
const machines = ref([]);
const orders = ref([]);
const andon = ref([]);
const alarms = ref([]);
const hourly = ref([]);
const oee = ref(null);
const stopTop = ref([]);
const activeMachine = ref(null);
let sse = null;

const latestAlarm = computed(() => alarms.value.slice(0, 6));

const outputOption = computed(() => {
  const data = hourly.value || [];
  return barOption({
    x: data.map((d) => d.label),
    series: [
      { name: '产量(件)', data: data.map((d) => d.qty), color: CHART_COLORS.primary },
      { name: '不良(件)', data: data.map((d) => d.defect), color: CHART_COLORS.stop, barMaxWidth: 10 },
    ],
    yName: '件',
  });
});

const statusBars = computed(() =>
  ['RUN', 'IDLE', 'STOP', 'ALARM', 'MAINT', 'OFFLINE'].map((k) => ({
    key: k,
    count: statusCount.value[k] || 0,
  })),
);

async function load() {
  try {
    const ov = await api.board.overview();
    kpi.value = ov.kpi || {};
    statusCount.value = ov.statusCount || {};
    machines.value = ov.machineMatrix || [];
    orders.value = ov.orderProgress || [];
    andon.value = ov.openAndon || [];
    alarms.value = ov.alarms || [];
    hourly.value = ov.hourlyOutput || [];
  } catch {
    /* 网络异常已由 http 层提示 */
  } finally {
    loading.value = false;
  }
  try {
    const o = await api.stats.oee(1);
    oee.value = o.overall;
  } catch {
    /* 忽略 */
  }
  try {
    const r = await api.board.ranking(7);
    stopTop.value = r.stop || [];
  } catch {
    /* 忽略 */
  }
}

function subscribe() {
  try {
    sse = api.board.stream();
    sse.addEventListener('machines', (e) => {
      try {
        const list = JSON.parse(e.data);
        if (Array.isArray(list) && list.length) machines.value = list;
      } catch {
        /* 忽略解析失败 */
      }
    });
  } catch {
    /* 忽略 */
  }
}

onMounted(() => {
  load();
  subscribe();
  const t = setInterval(() => {
    // SSE 断了就退回轮询，保证大屏永远不会变成一潭死水
    if (!sse) load();
  }, 30000);
  onBeforeUnmount(() => clearInterval(t));
});

onBeforeUnmount(() => {
  if (sse) sse.close();
});
</script>

<template>
  <div class="board">
    <!-- KPI 带 -->
    <div class="board__kpi">
      <IndKpi label="机台总数" :value="kpi.machineCount" unit="台" tone="cyan" foot="含离线与保养机台" />
      <IndKpi label="运行中" :value="kpi.runningCount" unit="台" tone="run" led="run" foot="正在切削加工" />
      <IndKpi label="待机" :value="kpi.idleCount" unit="台" tone="idle" led="idle" foot="等待派工 / 待料" />
      <IndKpi
        label="停机/报警"
        :value="(kpi.stopCount || 0) + (kpi.alarmCount || 0)"
        unit="台"
        tone="stop"
        led="alarm"
        :foot="`停机 ${kpi.stopCount || 0} · 报警 ${kpi.alarmCount || 0}`"
      />
      <IndKpi label="今日计划" :value="kpi.todayPlanQty" unit="件" tone="primary" foot="按工单计划开工量" />
      <IndKpi label="今日产出" :value="kpi.todayOutput" unit="件" tone="cyan" foot="车间报工实时累计" />
      <IndKpi label="今日合格率" :value="kpi.todayQualifiedRate" unit="%" tone="run" :digits="1" foot="合格 / 报工总数" />
      <IndKpi label="计划达成" :value="kpi.planRate" unit="%" tone="maint" :digits="1" foot="产出 / 今日计划" />
    </div>

    <!-- 机台状态矩阵 -->
    <IndPanel class="board__matrix" title="机台状态矩阵" sub="MACHINE STATUS MATRIX">
      <template #tools>
        <div class="status-legend">
          <span v-for="s in statusBars" :key="s.key" class="status-legend__item">
            <IndLed :tone="machineTone(s.key)" />
            <span class="mono">{{ num(s.count) }}</span>
          </span>
        </div>
      </template>
      <div v-if="loading" class="ind-loading"><div class="ind-loading__bar" /><span>看板数据加载中</span></div>
      <IndEmpty v-else-if="!machines.length" text="暂无设备数据" hint="请确认采集网关已启动" />
      <div v-else class="matrix-grid">
        <MachineCard
          v-for="m in machines"
          :key="m.machineId"
          :live="m"
          @click="activeMachine = m"
        />
      </div>
    </IndPanel>

    <!-- OEE -->
    <IndPanel class="board__oee" title="今日 OEE" sub="OVERALL EQUIPMENT EFFECTIVENESS">
      <div class="oee">
        <IndGauge
          :value="oee ? oee.oee : 0"
          :label="'综合效率 OEE'"
          :target="85"
          :height="168"
          :tone="oee && oee.oee >= 85 ? 'run' : oee && oee.oee >= 70 ? 'idle' : 'stop'"
        />
        <div class="oee__rows">
          <div class="oee__row">
            <span>时间开动率</span>
            <b class="mono">{{ oee ? pct(oee.availability) : '—' }}</b>
          </div>
          <div class="oee__row">
            <span>性能开动率</span>
            <b class="mono">{{ oee ? pct(oee.performance) : '—' }}</b>
          </div>
          <div class="oee__row">
            <span>合格率</span>
            <b class="mono">{{ oee ? pct(oee.quality) : '—' }}</b>
          </div>
          <div class="oee__row oee__row--dim">
            <span>今日开机时长</span>
            <b class="mono">{{ oee ? num(oee.runMinutes / 60, 1) : '—' }} h</b>
          </div>
          <div class="oee__row oee__row--dim">
            <span>今日停机时长</span>
            <b class="mono">{{ oee ? num(oee.stopMinutes / 60, 1) : '—' }} h</b>
          </div>
        </div>
      </div>
    </IndPanel>

    <!-- 安灯与报警 -->
    <IndPanel class="board__andon" title="安灯呼叫与实时报警" sub="ANDON & ALARM">
      <template #tools>
        <IndTag :tone="andon.length ? 'stop' : 'plain'" :led="andon.length ? 'stop' : ''">
          待处理 {{ andon.length }}
        </IndTag>
      </template>
      <div class="andon-list">
        <div v-if="!andon.length" class="andon-empty mute">当前无未关闭的安灯呼叫</div>
        <div
          v-for="a in andon.slice(0, 7)"
          :key="a.id"
          class="andon"
          :class="`andon--${ANDON_TYPE_TONE[a.andonType] || 'plain'}`"
        >
          <div class="andon__top">
            <IndTag :tone="ANDON_TYPE_TONE[a.andonType] || 'plain'" led>{{ a.andonType }}</IndTag>
            <span class="mono andon__level">{{ a.level }}</span>
            <span class="andon__machine mono">{{ a.machineCode || '—' }}</span>
            <span class="andon__time mono">{{ fromNow(a.callTime) }}</span>
          </div>
          <div class="andon__content ellipsis" :title="a.content">{{ a.content }}</div>
          <div class="andon__foot">
            <span class="mute">{{ a.callUser }}</span>
            <IndTag :tone="a.status === '待响应' ? 'stop' : 'idle'">{{ a.status }}</IndTag>
          </div>
        </div>
      </div>

      <div class="ind-section-title" style="margin-top: 10px">最新报警</div>
      <div v-if="!latestAlarm.length" class="mute" style="font-size: 12px">近 30 分钟无新增报警</div>
      <div v-else class="alarm-list">
        <div v-for="(al, i) in latestAlarm" :key="i" class="alarm">
          <IndLed tone="alarm" />
          <b class="mono">{{ al.machineCode }}</b>
          <span class="alarm__code mono">{{ al.alarmCode }}</span>
          <span class="mute alarm__time mono">{{ fromNow(al.time) }}</span>
        </div>
      </div>
    </IndPanel>

    <!-- 小时节拍 -->
    <IndPanel class="board__output" title="今日小时产出与不良" sub="HOURLY OUTPUT">
      <IndChart :option="outputOption" height="176" />
    </IndPanel>

    <!-- 停机 TOP -->
    <IndPanel class="board__stop" title="近 7 天停机时长 TOP" sub="STOPPAGE RANKING">
      <div v-if="!stopTop.length" class="mute" style="font-size: 12px; padding: 8px 0">暂无停机记录</div>
      <div v-else class="rank">
        <div v-for="(s, i) in stopTop" :key="s.machineCode" class="rank__row">
          <span class="rank__no mono">{{ i + 1 }}</span>
          <b class="mono">{{ s.machineCode }}</b>
          <div class="rank__bar">
            <div
              class="rank__bar-fill"
              :style="{ width: `${(s.stopMinutes / stopTop[0].stopMinutes) * 100}%` }"
            />
          </div>
          <span class="mono rank__val">{{ num(s.stopMinutes / 60, 1) }}h</span>
        </div>
      </div>
    </IndPanel>

    <!-- 机台详情浮层（大屏上点击机台卡） -->
    <Teleport to="body">
      <div v-if="activeMachine" class="mask-inline" @click.self="activeMachine = null">
        <div class="focus-card">
          <header class="between">
            <span class="center gap-2">
              <IndLed :tone="machineTone(activeMachine.status)" size="lg" />
              <b class="mono" style="font-size: 16px">{{ activeMachine.machineCode }}</b>
              <span class="dim">{{ activeMachine.machineName }}</span>
            </span>
            <button class="ind-modal__close" @click="activeMachine = null">✕</button>
          </header>
          <div class="focus-grid">
            <div class="focus-item"><span>状态</span><b>{{ activeMachine.status }}</b></div>
            <div class="focus-item"><span>程序号</span><b class="mono">{{ activeMachine.programNo || '—' }}</b></div>
            <div class="focus-item"><span>刀补号</span><b class="mono">{{ activeMachine.toolNo || '—' }}</b></div>
            <div class="focus-item"><span>工单</span><b class="mono">{{ activeMachine.orderNo || '—' }}</b></div>
            <div class="focus-item"><span>工序</span><b>{{ activeMachine.procName || '—' }}</b></div>
            <div class="focus-item"><span>主轴转速</span><b class="mono">{{ num(activeMachine.spindleSpeed) }} rpm</b></div>
            <div class="focus-item"><span>进给</span><b class="mono">{{ num(activeMachine.feedRate) }} mm/min</b></div>
            <div class="focus-item"><span>主轴负载</span><b class="mono">{{ num(activeMachine.spindleLoad) }} %</b></div>
            <div class="focus-item"><span>坐标 X/Y/Z</span><b class="mono">{{ num(activeMachine.axisX, 2) }} / {{ num(activeMachine.axisY, 2) }} / {{ num(activeMachine.axisZ, 2) }}</b></div>
            <div class="focus-item"><span>已加工件数</span><b class="mono">{{ num(activeMachine.partCount) }} / {{ num(activeMachine.planQty) }}</b></div>
            <div class="focus-item"><span>功率</span><b class="mono">{{ num(activeMachine.powerKw, 1) }} kW</b></div>
            <div class="focus-item"><span>状态说明</span><b>{{ activeMachine.statusReason || '—' }}</b></div>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<style scoped>
.board {
  height: 100%;
  padding: 12px;
  display: grid;
  gap: 12px;
  grid-template-columns: minmax(0, 1.55fr) minmax(0, 1fr) minmax(0, 1.05fr);
  grid-template-rows: 64px minmax(0, 1.08fr) minmax(0, 0.92fr);
  grid-template-areas:
    'kpi kpi kpi'
    'matrix oee andon'
    'matrix output stop';
}

.board__kpi {
  grid-area: kpi;
  display: grid;
  grid-template-columns: repeat(8, minmax(0, 1fr));
  gap: 12px;
}
.board__matrix { grid-area: matrix; }
.board__oee { grid-area: oee; }
.board__andon { grid-area: andon; }
.board__output { grid-area: output; }
.board__stop { grid-area: stop; }

.status-legend { display: flex; gap: 12px; }
.status-legend__item { display: flex; align-items: center; gap: 5px; font-size: 11px; color: var(--text-dim); }

.matrix-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(186px, 1fr));
  gap: 9px;
}

.oee { display: grid; grid-template-columns: 1fr 138px; gap: 10px; align-items: center; }
.oee__rows { display: flex; flex-direction: column; gap: 7px; }
.oee__row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  padding-bottom: 5px;
  border-bottom: 1px dashed var(--line-soft);
  font-size: 12px;
  color: var(--text-dim);
}
.oee__row b { color: var(--text-strong); font-size: 13px; }
.oee__row--dim { border-bottom: none; color: var(--text-mute); }
.oee__row--dim b { color: var(--text-dim); font-size: 12px; }

.andon-list { display: flex; flex-direction: column; gap: 7px; }
.andon-empty { font-size: 12px; padding: 6px 0; }
.andon {
  border: 1px solid var(--line);
  border-left: 2px solid var(--c-offline);
  background: var(--sheen);
  padding: 6px 8px;
  border-radius: var(--radius);
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.andon--stop { border-left-color: var(--c-stop); background: rgba(255, 77, 79, 0.06); }
.andon--idle { border-left-color: var(--c-idle); background: rgba(245, 165, 36, 0.06); }
.andon--maint { border-left-color: var(--c-maint); }
.andon--info { border-left-color: var(--c-info); }
.andon__top { display: flex; align-items: center; gap: 7px; }
.andon__level { font-size: 10px; color: var(--c-idle); }
.andon__machine { font-size: 11px; color: var(--text-strong); }
.andon__time { margin-left: auto; font-size: 10px; color: var(--text-mute); }
.andon__content { font-size: 12px; color: var(--text); }
.andon__foot { display: flex; align-items: center; justify-content: space-between; font-size: 10.5px; }

.alarm-list { display: flex; flex-direction: column; gap: 5px; }
.alarm { display: flex; align-items: center; gap: 8px; font-size: 11.5px; }
.alarm__code { color: var(--c-alarm); }
.alarm__time { margin-left: auto; font-size: 10px; }

.rank { display: flex; flex-direction: column; gap: 8px; }
.rank__row { display: grid; grid-template-columns: 18px 62px 1fr 46px; align-items: center; gap: 8px; font-size: 11.5px; }
.rank__no { color: var(--text-mute); }
.rank__bar { height: 8px; background: var(--bg-well); border: 1px solid var(--line); }
.rank__bar-fill { height: 100%; background: linear-gradient(90deg, #a32127, var(--c-stop)); }
.rank__val { text-align: right; color: var(--text); }

.mask-inline {
  position: fixed;
  inset: 0;
  background: var(--mask);
  z-index: 1200;
  display: grid;
  place-items: center;
}
.focus-card {
  width: 560px;
  border: 1px solid var(--line-strong);
  background: linear-gradient(180deg, var(--bg-panel-2), var(--bg-panel));
  padding: 14px 16px 16px;
  box-shadow: 0 24px 60px rgba(0, 0, 0, 0.6);
}
.focus-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px 18px;
}
.focus-item { display: flex; align-items: baseline; justify-content: space-between; gap: 10px; font-size: 12px; border-bottom: 1px dashed var(--line-soft); padding-bottom: 5px; }
.focus-item span { color: var(--text-mute); }
.focus-item b { color: var(--text); font-weight: 500; }

/*
 * 自适应：大屏是设计目标（1920+），但值班室笔记本、平板也会打开这个页面。
 * 不做降级的话，三列布局会把面板压到几十像素宽，图表容器直接变 0 宽画不出来。
 */
@media (max-width: 1560px) {
  .board {
    grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
    grid-template-rows: auto minmax(320px, 1.1fr) minmax(260px, 0.9fr) minmax(260px, 0.9fr);
    grid-template-areas:
      'kpi kpi'
      'matrix oee'
      'matrix andon'
      'output stop';
    overflow: auto;
  }
  .board__kpi { grid-template-columns: repeat(4, minmax(0, 1fr)); }
  .matrix-grid { grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); }
}

@media (max-width: 1024px) {
  .board {
    display: flex;
    flex-direction: column;
    height: auto;
    overflow: auto;
  }
  .board > * { min-height: 0; }
  .board__kpi { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .board__matrix { min-height: 460px; }
  .board__output, .board__stop, .board__andon { min-height: 300px; }
  .oee { grid-template-columns: 1fr; }
}
</style>
