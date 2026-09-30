<script setup>
import { computed } from 'vue';

const props = defineProps({
  variant: { type: String, default: 'default' }, // default | primary | danger | warn | ok | ghost
  size: { type: String, default: '' }, // sm | lg | xl
  block: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
});

const cls = computed(() => [
  'ind-btn',
  props.variant !== 'default' ? `ind-btn--${props.variant}` : '',
  props.size ? `ind-btn--${props.size}` : '',
  props.block ? 'ind-btn--block' : '',
]);
</script>

<template>
  <button type="button" :class="cls" :disabled="disabled">
    <slot />
  </button>
</template>
