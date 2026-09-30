<script setup>
/** 通用下拉：options 支持 [{label,value}] 或 ['A','B'] 两种写法。 */
import { computed } from 'vue';

const props = defineProps({
  modelValue: { type: [String, Number, null], default: '' },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '全部' },
  disabled: { type: Boolean, default: false },
  clearable: { type: Boolean, default: false },
});
const emit = defineEmits(['update:modelValue', 'change']);

const normalized = computed(() =>
  props.options.map((o) => (o && typeof o === 'object' ? o : { label: String(o), value: o })),
);

function onChange(e) {
  const v = e.target.value;
  emit('update:modelValue', v);
  emit('change', v);
}
</script>

<template>
  <select class="ind-select" :value="modelValue" :disabled="disabled" @change="onChange">
    <option value="">{{ placeholder }}</option>
    <option v-for="o in normalized" :key="o.value" :value="o.value">{{ o.label }}</option>
  </select>
</template>
