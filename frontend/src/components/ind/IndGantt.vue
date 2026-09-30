<script setup>
/**
 * 自研排产甘特图（机台泳道）。
 *
 * 为什么不用 ECharts 的 custom 系列：排产甘特需要横向滚动、泳道固定列、
 * 拖拽吸附与秒级实时刷新，用 DOM + CSS 绝对定位比图表库更可控，
 * 这也是多数 MES 排产模块的真实做法。
 */
import { computed, onMounted, ref, watch } from 'vue';
import { dateTime, timeOnly } from '@/utils/format';
import { STEP_STATUS, label as statusLabel, tone as statusTone } from '@/utils/status';

const props = defineProps({
  items: { type: Array, default: () => [] },
  machines: { type: Array, default: () => [] },
  from: { type: String, required: true },
  to: { type: String, required: true },
  pxPerMinute: { type: Number, default: 1.2 },
  rowHeight: { type: Number, default: 36 },
});
const emit = defineEmits(['bar-click', 'bar-dblclick']);

const scrollEl = ref(null);

const startMs = computed(() => new Date(props.from.replace(' ', 'T')).getTime());
const endMs = computed(() => new Date(props.to.replace(' ', 'T')).getTime());
const totalMinutes = computed(() => Math.max(60, (endMs.value - startMs.value) / 60000));
const totalWidth = computed(() => Math.max(600, totalMinutes.value * props.pxPerMinute));

/** 时间刻度：跨天时显示日期分隔线，每天内每 4 小时一个刻度。 */
const ticks = computed(() => {
  const list = [];
  const step = totalMinutes.value > 2880 ? 360 : totalMinutes.value > 960 ? 120 : 60;
  const first = new Date(startMs.value);
  first.setMinutes(0, 0, 0);
  if (totalMinutes.value > 960) first.setHours(Math.ceil(first.getHours() / 4) * 4);
  for (let t = first.getTime(); t <= endMs.value; t += step * 60000) {
    const d = new Date(t);
    const isDayStart = d.getHours() === 0;
    list.push({
      x: ((t - startMs.value) / 60000) * props.pxPerMinute,
      label: d.getHours() === 0 ? `${d.getMonth() + 1}/${d.getDate()}` : `${String(d.getHours()).padStart(2, '0')}:00`,
      dayLine: isDayStart,
      strong: d.getHours() % 4 === 0,
    });
  }
  return list;
});

function offset(task) {
  const t = new Date(String(task.planStart).replace(' ', 'T')).getTime();
  return ((t - startMs.value) / 60000) * props.pxPerMinute;
}

function width(task) {
  const a = new Date(String(task.planStart).replace(' ', 'T')).getTime();
  const b = new Date(String(task.planEnd).replace(' ', 'T')).getTime();
  return Math.max(6, ((b - a) / 60000) * props.pxPerMinute);
}

/** 机台泳道：没有机台的工序任务（手工工位）归入「未分配」泳道。 */
const lanes = computed(() => {
  const list = props.machines.map((m) => ({
    key: `m-${m.id}`,
    title: m.macCode,
    subtitle: `${m.macName} · ${m.cncType}`,
    status: m.status || m.runState,
    tasks: props.items
      .filter((t) => t.machineId === m.id)
      .sort((a, b) => new Date(String(a.planStart).replace(' ', 'T')) - new Date(String(b.planStart).replace(' ', 'T'))),
  }));
  const unassigned = props.items.filter((t) => !t.machineId);
  if (unassigned.length) {
    list.push({
      key: 'm-none',
      title: '未分配',
      subtitle: '手工工位 / 待排产',
      status: 'IDLE',
      tasks: unassigned.sort((a, b) => new Date(String(a.planStart).replace(' ', 'T')) - new Date(String(b.planStart).replace(' ', 'T'))),
    });
  }
  return list;
});

const nowX = computed(() => {
  const x = ((Date.now() - startMs.value) / 60000) * props.pxPerMinute;
  return x >= 0 && x <= totalWidth.value ? x : -1;
});

/** 首次渲染把视图滚动到「现在」附近，让计划员一进来就看到当前时刻。 */
function scrollToNow() {
  if (!scrollEl.value || nowX.value < 0) return;
  scrollEl.value.scrollLeft = Math.max(0, nowX.value - 240);
}
onMounted(scrollToNow);
watch(() => props.from, () => setTimeout(scrollToNow, 0));

function barTone(task) {
  return statusTone('step', task.status);
}

function progress(task) {
  return Math.min(100, Number(task.progress) || 0);
}
</script>

<template>
  <div class="gantt">
    <div class="gantt__lanes">
      <div class="gantt__lane-head">机台 / 工作中心</div>
      <div v-for="lane in lanes" :key="lane.key" class="gantt__lane" :style="{ height: `${rowHeight}px` }">
        <i class="ind-led" :class="`ind-led--${lane.status ? statusTone('machine', lane.status) : 'offline'}`" />
        <div class="gantt__lane-text">
          <b class="mono">{{ lane.title }}</b>
          <span class="mute ellipsis">{{ lane.subtitle }}</span>
        </div>
        <span class="gantt__lane-count mono">{{ lane.tasks.length }}</span>
      </div>
    </div>

    <div ref="scrollEl" class="gantt__scroll">
      <div class="gantt__canvas" :style="{ width: `${totalWidth}px` }">
        <div class="gantt__axis">
          <div
            v-for="(t, i) in ticks"
            :key="i"
            class="gantt__tick"
            :class="{ 'is-day': t.dayLine, 'is-strong': t.strong }"
            :style="{ left: `${t.x}px` }"
          >
            <span class="gantt__tick-label">{{ t.label }}</span>
          </div>
        </div>

        <div class="gantt__rows">
          <div v-for="lane in lanes" :key="lane.key" class="gantt__row" :style="{ height: `${rowHeight}px` }">
            <div
              v-for="t in lane.tasks"
              :key="t.id"
              class="gbar"
              :class="`gbar--${barTone(t)}`"
              :style="{ left: `${offset(t)}px`, width: `${width(t)}px`, top: '4px', height: `${rowHeight - 10}px` }"
              :title="`工单 ${t.orderNo}${t.seqNo ? ` / OP${t.seqNo}` : ''} ${t.procName || ''}
产品：${t.productName || '—'}
计划：${dateTime(t.planStart)} → ${timeOnly(t.planEnd)}
状态：${statusLabel('step', t.status)}　进度：${progress(t)}%
程序：${t.ncProgramNo || '—'}`"
              @click="emit('bar-click', t)"
              @dblclick="emit('bar-dblclick', t)"
            >
              <span class="gbar__prog" :style="{ width: `${progress(t)}%` }" />
              <span class="gbar__text">
                <b class="mono">{{ t.orderNo.slice(-6) }}</b>
                <em v-if="t.seqNo">OP{{ t.seqNo }}</em>
                <em>{{ t.procName }}</em>
              </span>
            </div>
          </div>
        </div>

        <div v-if="nowX >= 0" class="gantt__now" :style="{ left: `${nowX}px` }">
          <span class="gantt__now-flag">NOW</span>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gantt {
  display: flex;
  height: 100%;
  min-height: 0;
  border: 1px solid var(--line);
  background: var(--bg-well);
  overflow: hidden;
}

.gantt__lanes {
  width: 190px;
  flex: none;
  border-right: 1px solid var(--line-strong);
  background: var(--bg-panel-2);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.gantt__lane-head {
  height: 26px;
  display: flex;
  align-items: center;
  padding: 0 8px;
  font-size: 11px;
  letter-spacing: 0.06em;
  color: var(--text-mute);
  border-bottom: 1px solid var(--line-strong);
  background: var(--table-head-bg);
  flex: none;
}

.gantt__lane {
  display: flex;
  align-items: center;
  gap: 7px;
  padding: 0 8px;
  border-bottom: 1px solid var(--line-soft);
  flex: none;
}

.gantt__lane-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  font-size: 11px;
  line-height: 1.25;
}
.gantt__lane-text b { color: var(--text); }
.gantt__lane-text span { font-size: 10px; }
.gantt__lane-count { margin-left: auto; color: var(--text-mute); font-size: 11px; }

.gantt__scroll { overflow: auto; flex: 1; }
.gantt__canvas { position: relative; min-height: 100%; }

.gantt__axis {
  position: sticky;
  top: 0;
  height: 26px;
  border-bottom: 1px solid var(--line-strong);
  background: var(--table-head-bg);
  z-index: 3;
}
.gantt__tick {
  position: absolute;
  top: 0;
  bottom: 0;
  border-left: 1px solid var(--line-soft);
}
.gantt__tick.is-strong { border-left-color: var(--line); }
.gantt__tick.is-day { border-left: 1px solid var(--c-primary-dim); }
.gantt__tick-label {
  position: absolute;
  left: 3px;
  top: 5px;
  font-family: var(--font-num);
  font-size: 10px;
  color: var(--text-mute);
  white-space: nowrap;
}
.gantt__tick.is-day .gantt__tick-label { color: var(--c-primary); }

.gantt__rows { position: relative; }
.gantt__row {
  position: relative;
  border-bottom: 1px solid var(--line-soft);
}
.gantt__row:nth-child(odd) { background: var(--row-zebra); }

/* 背景刻度线（随时间轴延伸） */
.gantt__rows::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image: linear-gradient(to right, var(--grid-line) 1px, transparent 1px);
  background-size: 120px 100%;
}

.gbar {
  position: absolute;
  border: 1px solid;
  border-radius: 2px;
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: center;
  transition: filter 0.12s, transform 0.08s;
}
.gbar:hover { filter: brightness(1.35); transform: translateY(-1px); }

.gbar__prog {
  position: absolute;
  left: 0; top: 0; bottom: 0;
  background: rgba(255, 255, 255, 0.18);
}

.gbar__text {
  position: relative;
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0 6px;
  font-size: 10px;
  color: var(--gantt-bar-text);
  white-space: nowrap;
  overflow: hidden;
}
.gbar__text b { font-size: 10px; letter-spacing: 0.04em; }
.gbar__text em {
  font-style: normal;
  opacity: 0.85;
}

.gbar--run { background: rgba(31, 201, 107, 0.22); border-color: var(--c-run); }
.gbar--idle { background: rgba(245, 165, 36, 0.2); border-color: var(--c-idle); }
.gbar--stop { background: rgba(255, 77, 79, 0.2); border-color: var(--c-stop); }
.gbar--alarm { background: rgba(255, 45, 85, 0.24); border-color: var(--c-alarm); }
.gbar--primary { background: rgba(10, 162, 255, 0.2); border-color: var(--c-primary); }
.gbar--info { background: rgba(56, 189, 248, 0.18); border-color: var(--c-info); }
.gbar--maint { background: rgba(168, 85, 247, 0.2); border-color: var(--c-maint); }
.gbar--plain { background: rgba(101, 117, 134, 0.16); border-color: var(--c-offline); }
.gbar--offline { background: rgba(101, 117, 134, 0.16); border-color: var(--c-offline); }

.gantt__now {
  position: absolute;
  top: 0; bottom: 0;
  width: 1px;
  background: var(--c-stop);
  box-shadow: 0 0 8px rgba(255, 77, 79, 0.8);
  z-index: 4;
  pointer-events: none;
}
.gantt__now-flag {
  position: absolute;
  top: 30px;
  left: 2px;
  font-family: var(--font-num);
  font-size: 9px;
  color: var(--c-stop);
  background: rgba(11, 17, 24, 0.9);
  padding: 0 3px;
  border: 1px solid var(--c-stop);
}
</style>
