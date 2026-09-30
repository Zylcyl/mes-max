<script setup>
/** 机台负荷条：按 level 分级配色（OVER 红 / HIGH 黄 / NORMAL 绿 / LOW 灰）。 */
import { computed } from 'vue';
import { clamp } from '@/utils/format';

const props = defineProps({
  rate: { type: [Number, String], default: 0 },
  level: { type: String, default: 'NORMAL' },
  width: { type: Number, default: 80 },
  showText: { type: Boolean, default: false },
});

const percent = computed(() => clamp(Math.round(Number(props.rate) || 0), 0, 100));
const cls = computed(() => `ind-load--${(props.level || 'normal').toLowerCase()}`);
</script>

<template>
  <span class="ind-load" :class="cls">
    <span class="ind-load__track" :style="{ width: `${width}px` }">
      <span class="ind-load__fill" :style="{ width: `${percent}%` }" />
    </span>
    <span v-if="showText" class="mono">{{ percent }}%</span>
  </span>
</template>
