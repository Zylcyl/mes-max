<script setup>
import { computed } from 'vue';
import { clamp } from '@/utils/format';

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  max: { type: Number, default: 100 },
  tone: { type: String, default: '' },
  striped: { type: Boolean, default: false },
  label: { type: String, default: '' },
  // 允许字符串形式（如 height="6"）：模板里写成 `height="6"` 会传成字符串，
  // 只声明 Number 会产生大量 prop 类型警告；这里两种都接受并统一转成数字。
  height: { type: [Number, String], default: 0 },
});

const percent = computed(() => clamp((Number(props.value) || 0) / props.max * 100, 0, 100));
const cls = computed(() => ['ind-bar', props.tone ? `ind-bar--${props.tone}` : '', props.striped ? 'ind-bar--striped' : '']);
const heightPx = computed(() => {
  const h = Number(props.height);
  return Number.isFinite(h) && h > 0 ? h : 0;
});
</script>

<template>
  <div :class="cls" :style="heightPx ? { height: `${heightPx}px` } : null">
    <div class="ind-bar__fill" :style="{ width: `${percent}%` }" />
    <span v-if="label" class="ind-bar__label">{{ label }}</span>
  </div>
</template>
