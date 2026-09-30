<script setup>
import { computed } from 'vue';
import { num } from '@/utils/format';

const props = defineProps({
  label: { type: String, default: '' },
  value: { type: [Number, String], default: null },
  unit: { type: String, default: '' },
  foot: { type: String, default: '' },
  tone: { type: String, default: '' },
  led: { type: String, default: '' },
  digits: { type: Number, default: 0 },
});

const display = computed(() => {
  if (props.value === null || props.value === undefined || props.value === '') return '—';
  if (typeof props.value === 'string') return props.value;
  return num(props.value, props.digits);
});
</script>

<template>
  <div class="ind-kpi" :class="tone ? `ind-kpi--${tone}` : ''">
    <div class="ind-kpi__label">
      <i v-if="led" class="ind-led" :class="`ind-led--${led}`" />
      {{ label }}
    </div>
    <div class="ind-kpi__value">
      {{ display }}
      <span v-if="unit" class="ind-kpi__unit">{{ unit }}</span>
    </div>
    <div v-if="foot" class="ind-kpi__foot">{{ foot }}</div>
  </div>
</template>
