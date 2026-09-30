<script setup>
/**
 * 单台机床实时卡。
 * 车间看板的最小信息单元：一眼看出「这台机在干什么、干得顺不顺、做了多少」。
 */
import { computed } from 'vue';
import IndLed from '@/components/ind/IndLed.vue';
import IndBar from '@/components/ind/IndBar.vue';
import { MACHINE_STATUS, machineTone } from '@/utils/status';
import { num } from '@/utils/format';

const props = defineProps({
  live: { type: Object, required: true },
  compact: { type: Boolean, default: false },
});
const emit = defineEmits(['click']);

const tone = computed(() => machineTone(props.live.status));
const statusText = computed(() => (MACHINE_STATUS[props.live.status] || {}).label || props.live.status || '未知');

const runMinutes = computed(() => {
  if (props.live.statusMinutes !== undefined) return Math.round(props.live.statusMinutes);
  if (!props.live.statusSince) return null;
  const t = new Date(String(props.live.statusSince).replace(' ', 'T')).getTime();
  if (Number.isNaN(t)) return null;
  return Math.max(0, Math.round((Date.now() - t) / 60000));
});

const spindle = computed(() => Number(props.live.spindleSpeed || 0));
const load = computed(() => Number(props.live.spindleLoad || 0));
const isRunning = computed(() => props.live.status === 'RUN');

function holdText(m) {
  if (m === null) return '—';
  return m < 60 ? `${m}m` : `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}m`;
}
</script>

<template>
  <article class="mcard" :class="[`mcard--${tone}`, { 'mcard--compact': compact }]" @click="emit('click', live)">
    <header class="mcard__head">
      <IndLed :tone="tone" size="lg" />
      <b class="mono mcard__code">{{ live.machineCode }}</b>
      <span class="mcard__status">{{ statusText }}</span>
      <span class="mcard__hold mono">{{ holdText(runMinutes) }}</span>
    </header>

    <div class="mcard__name ellipsis" :title="`${live.machineName || ''} ${live.cncType || ''}`">
      {{ live.machineName || '—' }}
      <span class="mute">{{ live.cncType }}</span>
    </div>

    <div class="mcard__prog">
      <span class="mute">程序</span>
      <b class="mono ellipsis">{{ live.programNo || (live.status === 'OFFLINE' ? '通讯中断' : '—') }}</b>
      <span v-if="live.toolNo" class="mcard__tool mono">{{ live.toolNo }}</span>
    </div>

    <div v-if="isRunning" class="mcard__bars">
      <div class="mbar">
        <span class="mbar__k">S</span>
        <IndBar :value="spindle" :max="12000" tone="run" height="6" />
        <span class="mbar__v mono">{{ num(spindle) }}</span>
      </div>
      <div class="mbar">
        <span class="mbar__k">L</span>
        <IndBar :value="load" :max="100" :tone="load > 85 ? 'stop' : load > 65 ? 'idle' : 'run'" height="6" />
        <span class="mbar__v mono">{{ num(load) }}%</span>
      </div>
    </div>
    <div v-else class="mcard__idle mono">
      {{ live.statusReason || statusText }}
    </div>

    <footer class="mcard__foot">
      <span class="ellipsis" :title="live.orderNo">
        {{ live.orderNo ? live.orderNo.slice(-8) : '无在制工单' }}
        <em v-if="live.procName">{{ live.procName }}</em>
      </span>
      <span class="mono">{{ num(live.partCount) }}/{{ num(live.planQty) }}</span>
    </footer>
  </article>
</template>

<style scoped>
.mcard {
  position: relative;
  border: 1px solid var(--line);
  border-left: 3px solid var(--c-offline);
  border-radius: var(--radius);
  background: linear-gradient(160deg, var(--sheen), transparent 55%), var(--bg-panel);
  padding: 8px 10px 7px;
  display: flex;
  flex-direction: column;
  gap: 5px;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, transform 0.08s;
  min-width: 0;
}
.mcard:hover { background: var(--bg-hover); transform: translateY(-1px); }

.mcard--run { border-left-color: var(--c-run); }
.mcard--idle { border-left-color: var(--c-idle); }
.mcard--stop { border-left-color: var(--c-stop); background: linear-gradient(160deg, rgba(255, 77, 79, 0.07), transparent 55%), var(--bg-panel); }
.mcard--alarm {
  border-left-color: var(--c-alarm);
  background: linear-gradient(160deg, rgba(255, 45, 85, 0.1), transparent 55%), var(--bg-panel);
  animation: mcardAlarm 1.6s ease-in-out infinite;
}
.mcard--maint { border-left-color: var(--c-maint); }
.mcard--offline { border-left-color: var(--c-offline); opacity: 0.72; }

@keyframes mcardAlarm {
  0%, 100% { box-shadow: inset 0 0 0 1px rgba(255, 45, 85, 0.25); }
  50% { box-shadow: inset 0 0 0 1px rgba(255, 45, 85, 0.65); }
}

.mcard__head { display: flex; align-items: center; gap: 7px; }
.mcard__code { font-size: 13px; letter-spacing: 0.06em; color: var(--text-strong); }
.mcard__status {
  font-size: 11px;
  padding: 1px 6px;
  border: 1px solid currentColor;
  border-radius: var(--radius);
  opacity: 0.92;
}
.mcard--run .mcard__status { color: var(--c-run); }
.mcard--idle .mcard__status { color: var(--c-idle); }
.mcard--stop .mcard__status { color: var(--c-stop); }
.mcard--alarm .mcard__status { color: var(--c-alarm); }
.mcard--maint .mcard__status { color: var(--c-maint); }
.mcard--offline .mcard__status { color: var(--c-offline); }
.mcard__hold { margin-left: auto; font-size: 11px; color: var(--text-mute); }

.mcard__name { font-size: 12px; color: var(--text); }
.mcard__name .mute { font-size: 11px; }

.mcard__prog {
  display: flex;
  align-items: baseline;
  gap: 6px;
  font-size: 11px;
  min-width: 0;
}
.mcard__prog b { color: var(--c-cyan); font-size: 11.5px; }
.mcard__tool { margin-left: auto; color: var(--text-mute); font-size: 10px; }

.mcard__bars { display: flex; flex-direction: column; gap: 3px; }
.mbar { display: grid; grid-template-columns: 12px 1fr 52px; align-items: center; gap: 6px; }
.mbar__k { font-family: var(--font-num); font-size: 10px; color: var(--text-mute); }
.mbar__v { font-size: 10px; color: var(--text-dim); text-align: right; }

.mcard__idle {
  font-size: 10.5px;
  color: var(--text-mute);
  height: 22px;
  display: flex;
  align-items: center;
}

.mcard__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 5px;
  border-top: 1px solid var(--line-soft);
  font-size: 10.5px;
  color: var(--text-dim);
  min-width: 0;
}
.mcard__foot em { font-style: normal; color: var(--text-mute); margin-left: 5px; }
.mcard__foot .mono { color: var(--text); }

.mcard--compact { padding: 6px 8px; gap: 3px; }
.mcard--compact .mcard__name { font-size: 11px; }
</style>
