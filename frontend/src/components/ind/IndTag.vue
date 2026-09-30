<script setup>
import { computed } from 'vue';

const props = defineProps({
  tone: { type: String, default: 'plain' },
  led: { type: String, default: '' },
  size: { type: String, default: '' },
});

const cls = computed(() => ['ind-tag', `ind-tag--${props.tone}`, props.size ? `ind-tag--${props.size}` : '']);
const ledCls = computed(() => ['ind-led', 'ind-led--sm', `ind-led--${props.led || props.tone}`]);
</script>

<template>
  <span :class="cls">
    <i v-if="led" :class="ledCls" />
    <slot />
  </span>
</template>
