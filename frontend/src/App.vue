<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';
import { RouterView } from 'vue-router';
import IndToastHost from '@/components/ind/IndToastHost.vue';
import ConsoleLayout from '@/layouts/ConsoleLayout.vue';
import BoardLayout from '@/layouts/BoardLayout.vue';

const route = useRoute();

/**
 * 三种外壳：
 *   · 登录页：无外壳
 *   · 大屏页（/board）：BoardLayout（无侧栏、深色、常驻时钟与数据通道状态）
 *   · 其余业务页：ConsoleLayout（左侧导航 + 顶部状态条）
 */
const shell = computed(() => {
  if (route.meta.public) return null;
  return route.meta.full ? BoardLayout : ConsoleLayout;
});
</script>

<template>
  <component :is="shell" v-if="shell" />
  <RouterView v-else />
  <IndToastHost />
</template>
