<script setup>
/** 技术面板：工业界面的基础容器，带左上/右下角标与标题栏。 */
defineProps({
  title: { type: String, default: '' },
  sub: { type: String, default: '' },
  plain: { type: Boolean, default: false },
  flush: { type: Boolean, default: false },
  bodyStyle: { type: [String, Object], default: '' },
});
</script>

<template>
  <section class="ind-panel" :class="{ 'ind-panel--plain': plain }">
    <header v-if="title || $slots.tools || $slots.titleRight" class="ind-panel__head">
      <span class="ind-panel__accent" />
      <span v-if="title" class="ind-panel__title">{{ title }}</span>
      <span v-if="sub" class="ind-panel__sub">{{ sub }}</span>
      <slot name="titleRight" />
      <div class="ind-panel__tools">
        <slot name="tools" />
      </div>
    </header>
    <div class="ind-panel__body" :class="{ 'ind-panel__body--flush': flush }" :style="bodyStyle">
      <slot />
    </div>
  </section>
</template>
