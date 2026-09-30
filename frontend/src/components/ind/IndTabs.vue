<script setup>
/** 轻量标签页：通过 v-model 绑定当前 key，tabs = [{key,label,badge}]。 */
defineProps({
  modelValue: { type: String, default: '' },
  tabs: { type: Array, default: () => [] },
});
const emit = defineEmits(['update:modelValue']);
</script>

<template>
  <div class="ind-tabs">
    <div
      v-for="t in tabs"
      :key="t.key"
      class="ind-tab"
      :class="{ 'is-active': modelValue === t.key }"
      @click="emit('update:modelValue', t.key)"
    >
      {{ t.label }}
      <span v-if="t.badge !== undefined && t.badge !== null" class="ind-tab__badge">{{ t.badge }}</span>
    </div>
  </div>
</template>
