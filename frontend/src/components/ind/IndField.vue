<script setup>
defineProps({
  label: { type: String, default: '' },
  required: { type: Boolean, default: false },
  error: { type: String, default: '' },
  width: { type: [String, Number], default: '' },
});
</script>

<template>
  <label class="ind-field" :style="width ? { width: typeof width === 'number' ? `${width}px` : width } : null">
    <span v-if="label" class="ind-field__label">
      {{ label }}<i v-if="required" class="req">*</i>
    </span>
    <slot />
    <span v-if="error" class="ind-field__error">{{ error }}</span>
  </label>
</template>
