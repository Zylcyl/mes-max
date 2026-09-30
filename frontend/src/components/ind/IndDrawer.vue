<script setup>
defineProps({
  modelValue: { type: Boolean, default: false },
  title: { type: String, default: '' },
  width: { type: [String, Number], default: 760 },
});
const emit = defineEmits(['update:modelValue']);
</script>

<template>
  <Teleport to="body">
    <div v-if="modelValue" class="ind-mask" style="place-items: stretch" @click.self="emit('update:modelValue', false)">
      <aside
        class="ind-drawer"
        :style="{ '--drawer-w': typeof width === 'number' ? `${width}px` : width }"
        @click.stop
      >
        <header class="ind-modal__head">
          <span class="ind-panel__accent" />
          <span class="ind-modal__title">{{ title }}</span>
          <slot name="headExtra" />
          <button class="ind-modal__close" @click="emit('update:modelValue', false)">✕</button>
        </header>
        <div class="ind-modal__body">
          <slot />
        </div>
      </aside>
    </div>
  </Teleport>
</template>
