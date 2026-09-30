<script setup>
defineProps({
  text: { type: String, default: '暂无数据' },
  hint: { type: String, default: '' },
});
</script>

<template>
  <div class="ind-table-empty">
    <div class="ind-table-empty__icon">∅</div>
    <div>{{ text }}</div>
    <div v-if="hint" class="mute" style="font-size: 11px">{{ hint }}</div>
  </div>
</template>
