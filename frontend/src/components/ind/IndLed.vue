<script setup>
import { computed } from 'vue';
import { machineTone } from '@/utils/status';

const props = defineProps({
  status: { type: String, default: '' },
  tone: { type: String, default: '' },
  size: { type: String, default: '' },
  title: { type: String, default: '' },
});

const cls = computed(() => {
  const t = props.tone || (props.status ? machineTone(props.status) : 'offline');
  return ['ind-led', `ind-led--${t}`, props.size ? `ind-led--${props.size}` : ''];
});
</script>

<template>
  <i :class="cls" :title="title" />
</template>
