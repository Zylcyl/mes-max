<script setup>
/**
 * 大屏布局：无侧边导航、深色、信息密度高，用于挂在车间墙上的显示器。
 * 与办公电脑上的控制台是两种完全不同的使用场景，因此刻意分开。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { RouterView, useRoute, useRouter } from 'vue-router';
import IndLed from '@/components/ind/IndLed.vue';
import IndIcon from '@/components/ind/IndIcon.vue';
import { useAuth } from '@/stores/auth';
import { useTheme } from '@/stores/theme';
import { api } from '@/api';

const route = useRoute();
const router = useRouter();
const auth = useAuth();
const theme = useTheme();
const clock = ref(new Date());
const online = ref(false);
const subscribers = ref(0);
let sse = null;
let timer = null;

const timeText = computed(() =>
  [clock.value.getHours(), clock.value.getMinutes(), clock.value.getSeconds()]
    .map((v) => String(v).padStart(2, '0'))
    .join(':'),
);
const dateText = computed(() => {
  const d = clock.value;
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 星期${week}`;
});
const shift = computed(() => {
  const h = clock.value.getHours();
  if (h >= 8 && h < 17) return '白班';
  if (h >= 17 || h < 1) return '中班';
  return '夜班';
});

onMounted(async () => {
  timer = setInterval(() => {
    clock.value = new Date();
  }, 1000);
  try {
    sse = api.board.stream();
    sse.addEventListener('connected', () => (online.value = true));
    sse.addEventListener('machines', () => (online.value = true));
    sse.onerror = () => (online.value = false);
  } catch {
    online.value = false;
  }
  try {
    const h = await api.board.health();
    subscribers.value = h.subscribers;
  } catch {
    /* 忽略 */
  }
});

onBeforeUnmount(() => {
  clearInterval(timer);
  if (sse) sse.close();
});
</script>

<template>
  <div class="board-shell">
    <header class="board-head">
      <div class="board-head__brand">
        <span class="board-head__mark">MES</span>
        <div>
          <b>MES-MAX 车间实时看板</b>
          <span class="mute">CNC 数控加工 · 精密机加一车间</span>
        </div>
      </div>
      <div class="board-head__center">
        <span class="board-head__tag">{{ route.meta.title }}</span>
      </div>
      <div class="board-head__right">
        <div class="board-chip">
          <IndLed :tone="online ? 'run' : 'stop'" size="lg" />
          <span class="mono">{{ online ? '数据实时刷新中' : '数据通道断开' }}</span>
        </div>
        <div class="board-chip">
          <span class="mute">当前班次</span>
          <b class="mono">{{ shift }}</b>
        </div>
        <div class="board-clock">
          <b class="mono">{{ timeText }}</b>
          <span class="mono">{{ dateText }}</span>
        </div>
        <button class="board-exit" :title="theme.isLight ? '切换为深色（车间默认）' : '切换为浅色背景'" @click="theme.toggle()">
          <IndIcon :name="theme.isLight ? 'moon' : 'sun'" :size="13" />
          {{ theme.isLight ? '深色' : '浅色' }}
        </button>
        <button class="board-exit" @click="router.push('/monitor')">进入控制台</button>
      </div>
    </header>

    <main class="board-main">
      <RouterView v-slot="{ Component }">
        <component :is="Component" />
      </RouterView>
    </main>

    <footer class="board-foot">
      <span class="mono mute">MES-MAX v1.0 · 数据源：CNC 设备采集网关（FOCAS / OPC UA / MTConnect / MQTT）</span>
      <span class="mono mute">登录账号：{{ auth.displayName }} · 大屏订阅数 {{ subscribers }}</span>
    </footer>
  </div>
</template>

<style scoped>
.board-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--board-bg);
  position: relative;
  z-index: 1;
}

.board-head {
  height: 58px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 0 18px;
  border-bottom: 1px solid var(--line-strong);
  background: linear-gradient(180deg, rgba(10, 162, 255, 0.09), transparent);
}
.board-head__brand { display: flex; align-items: center; gap: 12px; }
.board-head__mark {
  display: grid;
  place-items: center;
  width: 46px;
  height: 28px;
  font-family: var(--font-display);
  font-size: 14px;
  letter-spacing: 0.16em;
  color: #04131f;
  background: linear-gradient(135deg, var(--c-cyan), var(--c-primary));
  clip-path: polygon(0 0, 100% 0, 100% 70%, 86% 100%, 0 100%);
}
.board-head__brand b {
  display: block;
  font-family: var(--font-display);
  font-size: 18px;
  letter-spacing: 0.1em;
  color: var(--text-strong);
}
.board-head__brand span { font-size: 11px; letter-spacing: 0.08em; }

.board-head__center { flex: 1; display: flex; justify-content: center; }
.board-head__tag {
  font-family: var(--font-display);
  font-size: 13px;
  letter-spacing: 0.24em;
  color: var(--c-cyan);
  padding: 3px 18px;
  border: 1px solid rgba(22, 211, 211, 0.35);
  background: rgba(22, 211, 211, 0.07);
}

.board-head__right { display: flex; align-items: center; gap: 14px; }
.board-chip {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  padding: 4px 10px;
  border: 1px solid var(--line);
  background: var(--sheen);
}
.board-clock { display: flex; flex-direction: column; align-items: flex-end; line-height: 1.1; }
.board-clock b { font-size: 22px; letter-spacing: 0.1em; color: var(--text-strong); }
.board-clock span { font-size: 10px; color: var(--text-mute); }
.board-exit {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  background: transparent;
  border: 1px solid var(--line-strong);
  color: var(--text-dim);
  font-size: 12px;
  cursor: pointer;
}
.board-exit:hover { color: var(--text); border-color: var(--c-primary-dim); }

/*
 * 用 auto 而不是 hidden：大屏尺寸下看板正好铺满、不会出现滚动条；
 * 而笔记本/平板等窄视口会降级成纵向排布，内容比视口高，hidden 会把下面的面板直接裁掉看不见。
 */
.board-main { flex: 1; min-height: 0; overflow: auto; }

.board-foot {
  height: 26px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-top: 1px solid var(--line);
  font-size: 10px;
  letter-spacing: 0.06em;
}
</style>
