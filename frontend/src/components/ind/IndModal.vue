<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: [String, Number], default: 640 },
  closeOnMask: { type: Boolean, default: true },
});
const emit = defineEmits(['update:modelValue', 'close']);

function close() {
  emit('update:modelValue', false);
  emit('close');
}
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="ind-mask" @click.self="closeOnMask && close()">
      <div class="ind-modal" :style="{ '--modal-w': typeof width === 'number' ? `${width}px` : width }">
        <header class="ind-modal__head">
          <span class="ind-panel__accent" />
          <span class="ind-modal__title">{{ title }}</span>
          <slot name="headExtra" />
          <button class="ind-modal__close" @click="close">✕</button>
        </header>
        <div class="ind-modal__body">
          <slot />
        </div>
        <footer v-if="$slots.foot" class="ind-modal__foot">
          <slot name="foot" :close="close" />
        </footer>
      </div>
    </div>
  </Teleport>
</template>
