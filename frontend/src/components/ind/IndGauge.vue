<script setup>
import { computed } from 'vue';
import IndChart from './IndChart.vue';
import { gaugeOption } from '@/utils/chart';

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  max: { type: Number, default: 100 },
  unit: { type: String, default: '%' },
  label: { type: String, default: '' },
  target: { type: [Number, String], default: null },
  tone: { type: String, default: 'primary' },
  height: { type: [String, Number], default: 150 },
});

const option = computed(() =>
  gaugeOption({
    value: Number(props.value) || 0,
    max: props.max,
    unit: props.unit,
    label: props.label,
    target: props.target === null || props.target === '' ? null : Number(props.target),
    tone: props.tone,
  }),
);
</script>

<template>
  <IndChart :option="option" :height="height" />
</template>
