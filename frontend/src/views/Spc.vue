<script setup>
/**
 * SPC 过程控制（I-MR 控制图 + 过程能力分析）。
 *
 * 为什么用 I-MR 而不是 Xbar-R：CNC 关键尺寸往往逐件全检，一个样本即一个观测值，
 * 凑不出 4~5 件的子组，I-MR 才是现场真正能落地的做法。
 */
import { computed, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndKpi from '@/components/ind/IndKpi.vue';
import IndField from '@/components/ind/IndField.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndChart from '@/components/ind/IndChart.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndGauge from '@/components/ind/IndGauge.vue';
import { api } from '@/api';
import { CHART_COLORS, lineOption, spcOption } from '@/utils/chart';
import { dateTime, num } from '@/utils/format';

const groups = ref([]);
const machineId = ref('');
const charCode = ref('');
const analysis = ref(null);
const samples = ref([]);

const controlOption = computed(() => {
  const a = analysis.value;
  if (!a) return {};
  return spcOption({
    x: a.points.map((p) => `${p.sampleNo ?? ''}`),
    values: a.points.map((p) => p.value),
    ucl: a.ucl,
    lcl: a.lcl,
    mean: a.mean,
    usl: a.usl,
    lsl: a.lsl,
  });
});

const mrOption = computed(() => {
  const a = analysis.value;
  if (!a) return {};
  const values = samples.value
    .map((s, i, arr) => (i === 0 ? null : Math.abs(Number(s.sampleValue) - Number(arr[i - 1].sampleValue))))
    .filter((v) => v !== null);
  return lineOption({
    x: values.map((_, i) => `${i + 1}`),
    series: [
      { name: '移动极差 MR', data: values, color: CHART_COLORS.maint },
      { name: 'MR 上控制限', data: values.map(() => a.mrUcl), color: CHART_COLORS.stop, dashed: true, smooth: false },
      { name: 'MR 中心线', data: values.map(() => a.mrBar), color: CHART_COLORS.idle, dashed: true, smooth: false },
    ],
    grid: { left: 56, right: 20, top: 32, bottom: 22 },
  });
});

const columns = [
  { key: 'sampleNo', title: '样本号', width: '70px', kind: 'mono' },
  { key: 'sampleValue', title: '实测值', width: '98px', align: 'right', kind: 'mono', format: (r) => num(r.sampleValue, 4) },
  { key: 'nominalValue', title: '基本尺寸', width: '94px', align: 'right', kind: 'mono', format: (r) => num(r.nominalValue, 4) },
  { key: 'upperLimit', title: '上规格限', width: '94px', align: 'right', kind: 'mono', format: (r) => num(r.upperLimit, 4) },
  { key: 'lowerLimit', title: '下规格限', width: '94px', align: 'right', kind: 'mono', format: (r) => num(r.lowerLimit, 4) },
  { key: 'sampleTime', title: '采样时间', width: '142px', kind: 'datetime' },
  { key: 'inspector', title: '检验员', width: '80px' },
  { key: 'judge', title: '判定', width: '86px' },
];

async function loadGroups() {
  groups.value = await api.spc.groups();
  if (groups.value.length) {
    machineId.value = String(groups.value[0].machineId);
    charCode.value = groups.value[0].charCode;
    await analyze();
  }
}

function onGroupChange() {
  const g = groups.value.find((x) => String(x.machineId) === String(machineId.value) && x.charCode === charCode.value)
    || groups.value.find((x) => String(x.machineId) === String(machineId.value));
  if (g) charCode.value = g.charCode;
  analyze();
}

async function analyze() {
  const res = await api.spc.series({ machineId: machineId.value, charCode: charCode.value, size: 100 });
  analysis.value = res.analysis?.enough ? res.analysis : null;
  samples.value = res.samples || [];
}

const cpkTone = computed(() => {
  // 没录公差时后端不给 CpK（null）——此时既不是「好」也不是「差」，用中性色，别用红色误导现场
  const v = analysis.value?.cpk;
  if (v === null || v === undefined) return 'idle';
  return v >= 1.67 ? 'run' : v >= 1.33 ? 'primary' : v >= 1.0 ? 'idle' : 'stop';
});

function isOut(row) {
  return Number(row.sampleValue) > Number(row.upperLimit) || Number(row.sampleValue) < Number(row.lowerLimit);
}

onMounted(loadGroups);
</script>

<template>
  <div class="page">
    <IndPanel plain>
      <div class="ind-toolbar">
        <IndField label="机台与特性" width="320px">
          <IndSelect
            v-model="machineId"
            :options="groups.map((g) => ({ value: String(g.machineId), label: `${g.machineCode} · ${g.charName}` }))"
            placeholder="请选择机台"
            @change="onGroupChange"
          />
        </IndField>
        <IndButton @click="analyze">重新计算</IndButton>
        <div class="ind-toolbar__spacer" />
        <span class="mute" style="font-size: 12px">
          I-MR 控制图：σ 由移动极差均值估计，σ = MR̄ / 1.128（n=2 时 d₂ = 1.128）
        </span>
      </div>
    </IndPanel>

    <div v-if="analysis" class="kpi-row">
      <IndKpi label="样本数" :value="analysis.sampleCount" unit="个" tone="primary" :foot="analysis.charName" />
      <IndKpi label="均值 X̄" :value="analysis.mean" :digits="4" tone="cyan" foot="中心线 CL" />
      <IndKpi label="标准差 σ" :value="analysis.sigma" :digits="5" tone="maint" :foot="'MR̄ = ' + analysis.mrBar" />
      <IndKpi label="Cp 潜在能力" :value="analysis.cp" :digits="3" tone="idle" foot="不考虑偏移" />
      <IndKpi label="CpK 实际能力" :value="analysis.cpk" :digits="3" :tone="cpkTone" foot="考虑过程中心偏移" />
      <IndKpi
        label="超控点数"
        :value="analysis.outOfControl"
        unit="个"
        :tone="analysis.outOfControl ? 'stop' : 'run'"
        foot="超出 ±3σ 控制限"
      />
      <IndKpi
        label="超差点数"
        :value="analysis.outOfSpec"
        unit="个"
        :tone="analysis.outOfSpec ? 'stop' : 'run'"
        foot="超出图纸规格限"
      />
    </div>

    <div v-if="analysis" class="grid-2">
      <IndPanel title="单值控制图（I 图）" sub="INDIVIDUAL CHART" :body-style="{ padding: '10px' }">
        <IndChart :option="controlOption" height="272" />
      </IndPanel>
      <div class="side">
        <IndPanel title="过程能力评价" sub="PROCESS CAPABILITY">
          <div class="cap">
            <IndGauge v-if="analysis.cpk !== null && analysis.cpk !== undefined"
                      :value="analysis.cpk" :max="3" unit="" label="CpK" :height="150" :tone="cpkTone" />
            <div v-else class="cap__nogauge">
              <b>未录入公差</b>
              <span class="dim">缺少 USL / LSL，不计算 CpK</span>
            </div>
            <div class="cap__rows">
              <div><span>USL</span><b class="mono">{{ num(analysis.usl, 4) }}</b></div>
              <div><span>LSL</span><b class="mono">{{ num(analysis.lsl, 4) }}</b></div>
              <div><span>UCL</span><b class="mono">{{ num(analysis.ucl, 4) }}</b></div>
              <div><span>LCL</span><b class="mono">{{ num(analysis.lcl, 4) }}</b></div>
              <div>
                <span>CPU / CPL</span>
                <b class="mono">{{ num(analysis.cpu, 3) }} / {{ num(analysis.cpl, 3) }}</b>
              </div>
            </div>
          </div>
          <div class="grade">
            <IndTag :tone="cpkTone" size="lg">{{ analysis.grade }}</IndTag>
            <p class="dim" style="font-size: 12.5px">{{ analysis.suggestion }}</p>
          </div>
        </IndPanel>
        <IndPanel title="移动极差图（MR 图）" sub="MOVING RANGE" :body-style="{ padding: '8px' }">
          <IndChart :option="mrOption" height="152" />
        </IndPanel>
      </div>
    </div>

    <IndPanel title="采样数据" sub="SAMPLES" flush>
      <div style="height: 100%; min-height: 0">
        <IndTable :columns="columns" :rows="samples" dense empty-text="暂无采样数据">
          <template #cell="{ row, col }">
            <template v-if="col.key === 'judge'">
              <IndTag :tone="isOut(row) ? 'stop' : 'run'">{{ isOut(row) ? '超差' : '合格' }}</IndTag>
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
      </div>
    </IndPanel>
  </div>
</template>

<style scoped>
.kpi-row { display: grid; grid-template-columns: repeat(7, 1fr); gap: 12px; }
.grid-2 { display: grid; grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr); gap: 12px; }
.side { display: grid; grid-template-rows: minmax(0, 1.1fr) minmax(0, 0.9fr); gap: 12px; }
.cap { display: grid; grid-template-columns: 1fr 148px; align-items: center; }
.cap__nogauge {
  height: 150px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border: 1px dashed var(--line);
  font-size: 12px;
  text-align: center;
}
.cap__nogauge b { color: var(--text-dim); font-size: 13px; }
.cap__rows { display: flex; flex-direction: column; gap: 6px; font-size: 12px; }
.cap__rows > div { display: flex; justify-content: space-between; color: var(--text-dim); }
.cap__rows b { color: var(--text); }
.grade { margin-top: 10px; padding-top: 10px; border-top: 1px solid var(--line); display: flex; flex-direction: column; gap: 8px; }
</style>
