<script setup>
defineProps({
  modelValue: { type: [String, Number], default: '' },
  type: { type: String, default: 'text' },
  placeholder: { type: String, default: '' },
  disabled: { type: Boolean, default: false },
  step: { type: [String, Number], default: undefined },
  min: { type: [String, Number], default: undefined },
});
const emit = defineEmits(['update:modelValue', 'enter']);
</script>

<template>
  <input
    class="ind-input num"
    :type="type"
    :value="modelValue"
    :placeholder="placeholder"
    :disabled="disabled"
    :step="step"
    :min="min"
    @input="emit('update:modelValue', $event.target.value)"
    @keyup.enter="emit('enter')"
  />
</template>
