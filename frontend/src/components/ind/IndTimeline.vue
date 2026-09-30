<script setup>
defineProps({
  items: { type: Array, default: () => [] },
});
</script>

<template>
  <div class="ind-timeline">
    <div v-for="(it, i) in items" :key="i" class="ind-timeline__item">
      <span class="ind-timeline__dot" :style="it.color ? { borderColor: it.color } : null" />
      <div class="between">
        <b :class="it.tone ? `c-${it.tone}` : ''">{{ it.title }}</b>
        <span class="ind-timeline__time">{{ it.time }}</span>
      </div>
      <div v-if="it.text" class="dim" style="font-size: 12px">{{ it.text }}</div>
      <slot name="item" :item="it" />
    </div>
  </div>
</template>
