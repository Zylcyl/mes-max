<script setup>
import { computed } from 'vue';

const props = defineProps({
  total: { type: Number, default: 0 },
  page: { type: Number, default: 1 },
  size: { type: Number, default: 20 },
  sizeOptions: { type: Array, default: () => [10, 20, 50, 100] },
});
const emit = defineEmits(['update:page', 'update:size', 'change']);

const pages = computed(() => Math.max(1, Math.ceil(props.total / props.size)));

const window = computed(() => {
  const total = pages.value;
  const cur = props.page;
  const list = [];
  let start = Math.max(1, cur - 2);
  let end = Math.min(total, start + 4);
  start = Math.max(1, end - 4);
  for (let i = start; i <= end; i++) list.push(i);
  return list;
});

function go(p) {
  if (p < 1 || p > pages.value || p === props.page) return;
  emit('update:page', p);
  emit('change');
}

function changeSize(e) {
  emit('update:size', Number(e.target.value));
  emit('update:page', 1);
  emit('change');
}
</script>

<template>
  <div class="ind-pager">
    <span>共 <b class="mono strong">{{ total }}</b> 条</span>
    <span class="mute">|</span>
    <span class="mono">第 {{ page }} / {{ pages }} 页</span>
    <select class="ind-select" style="width: 78px; height: 22px" :value="size" @change="changeSize">
      <option v-for="s in sizeOptions" :key="s" :value="s">{{ s }} 条/页</option>
    </select>
    <div class="ind-pager__pages">
      <span class="ind-pager__num" :class="{ 'is-disabled': page <= 1 }" @click="go(page - 1)">‹</span>
      <span
        v-for="p in window"
        :key="p"
        class="ind-pager__num"
        :class="{ 'is-active': p === page }"
        @click="go(p)"
      >{{ p }}</span>
      <span class="ind-pager__num" :class="{ 'is-disabled': page >= pages }" @click="go(page + 1)">›</span>
    </div>
  </div>
</template>
