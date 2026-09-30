<script setup>
/**
 * 控制台布局：左侧固定导航 + 顶部状态条 + 内容区。
 *
 * 顶栏常驻三样车间最关心的信息：当前班次、实时时钟、实时通道连接状态，
 * 这样无论计划员停留在哪个页面，都能感知「现在车间是什么班、数据是不是活的」。
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { RouterView, useRoute, useRouter } from 'vue-router';
import IndIcon from '@/components/ind/IndIcon.vue';
import IndLed from '@/components/ind/IndLed.vue';
import IndButton from '@/components/ind/IndButton.vue';
import { useAuth } from '@/stores/auth';
import { useDict } from '@/stores/dict';
import { useTheme } from '@/stores/theme';
import { api } from '@/api';

const route = useRoute();
const router = useRouter();
const auth = useAuth();
const dict = useDict();
const theme = useTheme();

const collapsed = ref(false);
const clock = ref(new Date());
const online = ref(false);
const openAndon = ref(0);
const showUserMenu = ref(false);
let timer = null;
let pollTimer = null;
let sse = null;

const NAV = [
  {
    group: '车间监控',
    items: [
      { path: '/board', title: '车间看板', icon: 'board', module: 'dashboard' },
      { path: '/monitor', title: '设备监控', icon: 'monitor', module: 'dashboard' },
      { path: '/andon', title: '安灯呼叫', icon: 'andon', module: 'respond', badgeKey: 'andon' },
    ],
  },
  {
    group: '生产执行',
    items: [
      { path: '/order', title: '生产订单', icon: 'order', module: 'order' },
      { path: '/schedule', title: '生产排产', icon: 'schedule', module: 'schedule' },
      { path: '/dispatch', title: '派工管理', icon: 'dispatch', module: 'dispatch' },
      { path: '/terminal', title: '车间报工', icon: 'terminal', module: 'terminal' },
      { path: '/stop', title: '停机分析', icon: 'stop', module: 'report' },
    ],
  },
  {
    group: '质量与工艺',
    items: [
      { path: '/quality', title: '质量检验', icon: 'quality', module: 'quality' },
      { path: '/spc', title: 'SPC 过程控制', icon: 'spc', module: 'spc' },
      { path: '/tool', title: '刀具管理', icon: 'tool', module: 'tool' },
    ],
  },
  {
    group: '设备维护',
    items: [{ path: '/equipment', title: '设备维保', icon: 'equipment', module: 'equipment' }],
  },
  {
    group: '追溯与报表',
    items: [
      { path: '/trace', title: '谱系追溯', icon: 'trace', module: 'trace' },
      { path: '/stats', title: '统计报表', icon: 'stats', module: 'stats' },
    ],
  },
  {
    group: '系统',
    items: [{ path: '/system', title: '系统管理', icon: 'system', module: 'system' }],
  },
];

/** 只渲染当前角色有权限的菜单项；整组为空时连组标题一起隐藏。 */
const nav = computed(() =>
  NAV.map((g) => ({ ...g, items: g.items.filter((it) => auth.can(it.module)) }))
    .filter((g) => g.items.length > 0),
);

const shiftName = computed(() => {
  const h = clock.value.getHours();
  if (h >= 8 && h < 17) return '白班 08:00-17:00';
  if (h >= 17 || h < 1) return '中班 17:00-01:00';
  return '夜班 01:00-08:00';
});

const dateText = computed(() => {
  const d = clock.value;
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()];
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} 周${week}`;
});

const timeText = computed(() =>
  [clock.value.getHours(), clock.value.getMinutes(), clock.value.getSeconds()]
    .map((v) => String(v).padStart(2, '0'))
    .join(':'),
);

function connectSse() {
  try {
    sse = api.board.stream();
    sse.addEventListener('connected', () => {
      online.value = true;
    });
    sse.addEventListener('machines', () => {
      online.value = true;
    });
    sse.onerror = () => {
      online.value = false;
    };
  } catch {
    online.value = false;
  }
}

async function pollAndon() {
  if (!auth.can('respond') && !auth.can('dashboard')) {
    openAndon.value = 0;
    return;
  }
  try {
    const list = await api.andon.open();
    openAndon.value = Array.isArray(list) ? list.length : 0;
  } catch {
    /* 静默失败，不打扰操作 */
  }
}

onMounted(async () => {
  timer = setInterval(() => {
    clock.value = new Date();
  }, 1000);
  connectSse();
  await dict.load().catch(() => {});
  await auth.loadProfile().catch(() => {});
  pollAndon();
  pollTimer = setInterval(pollAndon, 20000);
});

onBeforeUnmount(() => {
  clearInterval(timer);
  clearInterval(pollTimer);
  if (sse) sse.close();
});

async function doLogout() {
  await auth.logout();
  router.push('/login');
}
</script>

<template>
  <div class="console" :class="{ 'console--collapsed': collapsed }">
    <header class="topbar">
      <div class="brand">
        <div class="brand__mark">
          <span class="brand__mark-a" />
          <span class="brand__mark-b" />
        </div>
        <div class="brand__text">
          <b>MES-MAX</b>
          <span>CNC 制造执行系统</span>
        </div>
      </div>

      <div class="topbar__divider" />

      <div class="topbar__title">
        <span class="mute">{{ route.meta.group || '车间' }}</span>
        <i class="mute">/</i>
        <b>{{ route.meta.title }}</b>
      </div>

      <div class="topbar__right">
        <div class="chip">
          <IndLed :tone="online ? 'run' : 'stop'" />
          <span class="mono">{{ online ? '实时通道在线' : '实时通道断开' }}</span>
        </div>
        <div class="chip">
          <span class="mute">班次</span>
          <b class="mono">{{ shiftName }}</b>
        </div>
        <div v-if="openAndon" class="chip chip--alarm" @click="router.push('/andon')">
          <IndLed tone="alarm" />
          <span class="mono">安灯 {{ openAndon }} 条待处理</span>
        </div>
        <div class="clock">
          <b class="mono">{{ timeText }}</b>
          <span class="mute mono">{{ dateText }}</span>
        </div>
        <button
          class="chip chip--btn"
          :title="theme.isLight ? '切换为深色（车间默认）' : '切换为浅色背景'"
          @click="theme.toggle()"
        >
          <IndIcon :name="theme.isLight ? 'moon' : 'sun'" :size="13" />
          <span class="mono">{{ theme.isLight ? '深色' : '浅色' }}</span>
        </button>
        <IndButton variant="ghost" size="sm" @click="router.push('/board')">全屏看板</IndButton>
        <div class="user" @click="showUserMenu = !showUserMenu">
          <IndIcon name="user" :size="14" />
          <span>{{ auth.displayName }}</span>
          <span class="mute">{{ auth.roleName }}</span>
        </div>
        <div v-if="showUserMenu" class="user-menu" @mouseleave="showUserMenu = false">
          <div class="user-menu__head">
            <b>{{ auth.displayName }}</b>
            <span class="mute">{{ auth.user?.empNo }} · {{ auth.user?.deptName }}</span>
          </div>
          <button class="user-menu__item" @click="router.push('/system'); showUserMenu = false">
            <IndIcon name="system" :size="13" /> 系统管理
          </button>
          <button class="user-menu__item" @click="doLogout">
            <IndIcon name="logout" :size="13" /> 退出登录
          </button>
        </div>
      </div>
    </header>

    <div class="console__body">
      <nav class="nav">
        <button class="nav__collapse" @click="collapsed = !collapsed">
          <span class="mono">{{ collapsed ? '»' : '«' }}</span>
          <span v-if="!collapsed">收起导航</span>
        </button>
        <div v-for="g in nav" :key="g.group" class="nav__group">
          <div v-if="!collapsed" class="nav__group-title">{{ g.group }}</div>
          <RouterLink
            v-for="it in g.items"
            :key="it.path"
            :to="it.path"
            class="nav__item"
            :class="{ 'is-active': route.path === it.path }"
            :title="it.title"
          >
            <IndIcon :name="it.icon" />
            <span v-if="!collapsed" class="nav__item-text">{{ it.title }}</span>
            <span
              v-if="it.badgeKey === 'andon' && openAndon && !collapsed"
              class="nav__badge mono"
            >{{ openAndon }}</span>
          </RouterLink>
        </div>
      </nav>

      <main class="console__content">
        <RouterView v-slot="{ Component }">
          <component :is="Component" />
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.console {
  height: 100%;
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
}

/* ---------------- 顶栏 ---------------- */
.topbar {
  height: var(--h-topbar);
  flex: none;
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 14px 0 0;
  background: var(--topbar-bg);
  border-bottom: 1px solid var(--line-strong);
  position: relative;
}
.topbar::after {
  content: '';
  position: absolute;
  left: 0; right: 0; bottom: -1px;
  height: 1px;
  background: linear-gradient(90deg, var(--c-primary) 0%, transparent 45%);
  opacity: 0.7;
}

.brand {
  width: 210px;
  flex: none;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 14px;
  background: linear-gradient(90deg, rgba(10, 162, 255, 0.16), transparent);
  border-right: 1px solid var(--line);
}
.brand__mark {
  position: relative;
  width: 26px;
  height: 26px;
  flex: none;
}
.brand__mark::before {
  content: '';
  position: absolute;
  inset: 0;
  border: 1.5px solid var(--c-primary);
  clip-path: polygon(0 0, 100% 0, 100% 68%, 68% 100%, 0 100%);
}
.brand__mark-a,
.brand__mark-b {
  position: absolute;
  background: var(--c-cyan);
}
.brand__mark-a { left: 6px; top: 7px; width: 13px; height: 2px; }
.brand__mark-b { left: 6px; top: 12px; width: 8px; height: 2px; }
.brand__text { display: flex; flex-direction: column; line-height: 1.15; }
.brand__text b {
  font-family: var(--font-display);
  font-size: 15px;
  letter-spacing: 0.14em;
  color: var(--text-strong);
}
.brand__text span { font-size: 10px; letter-spacing: 0.14em; color: var(--text-mute); }

.topbar__divider { width: 1px; height: 24px; background: var(--line); }
.topbar__title { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.topbar__title b { color: var(--text-strong); letter-spacing: 0.06em; }

.topbar__right {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 10px;
}
.chip {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 24px;
  padding: 0 9px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--sheen);
  font-size: 11px;
}
.chip--alarm {
  border-color: rgba(255, 45, 85, 0.5);
  color: var(--c-alarm);
  cursor: pointer;
  animation: indPulse 2s infinite;
}
.chip--btn {
  cursor: pointer;
  transition: border-color 0.12s, color 0.12s, background 0.12s;
}
.chip--btn:hover {
  border-color: var(--c-primary-dim);
  color: var(--text);
  background: var(--bg-hover);
}

.clock {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  line-height: 1.15;
}
.clock b { font-size: 15px; color: var(--text-strong); letter-spacing: 0.08em; }
.clock span { font-size: 10px; }

.user {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius);
  background: var(--sheen);
  cursor: pointer;
  font-size: 12px;
}
.user:hover { border-color: var(--line-strong); }
.user span:first-of-type { color: var(--text); }

.user-menu {
  position: absolute;
  top: calc(var(--h-topbar) - 6px);
  right: 14px;
  width: 190px;
  background: var(--bg-panel-2);
  border: 1px solid var(--line-strong);
  border-radius: var(--radius);
  box-shadow: 0 14px 34px rgba(0, 0, 0, 0.55);
  z-index: 500;
  overflow: hidden;
}
.user-menu__head {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--line);
  font-size: 12px;
}
.user-menu__item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 9px 12px;
  background: transparent;
  border: none;
  color: var(--text-dim);
  font-size: 12px;
  cursor: pointer;
  text-align: left;
}
.user-menu__item:hover { background: var(--bg-hover); color: var(--text); }

/* ---------------- 主体 ---------------- */
.console__body { flex: 1; display: flex; min-height: 0; }

.nav {
  width: 186px;
  flex: none;
  background: var(--nav-bg);
  border-right: 1px solid var(--line);
  overflow-y: auto;
  overflow-x: hidden;
  padding-bottom: 12px;
}
.console--collapsed .nav { width: 52px; }

.nav__collapse {
  width: 100%;
  height: 30px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 14px;
  background: var(--sheen);
  border: none;
  border-bottom: 1px solid var(--line);
  color: var(--text-mute);
  font-size: 11px;
  cursor: pointer;
}
.nav__collapse:hover { color: var(--text); }

.nav__group { padding: 10px 0 2px; }
.nav__group-title {
  padding: 0 14px 6px;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--text-mute);
}

.nav__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 10px;
  height: var(--h-nav);
  margin: 1px 6px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: var(--radius);
  color: var(--text-dim);
  font-size: 12.5px;
  transition: background 0.12s, color 0.12s, border-color 0.12s;
}
.nav__item:hover { background: var(--bg-hover); color: var(--text); }
.nav__item.is-active {
  color: var(--text-strong);
  background: linear-gradient(90deg, rgba(10, 162, 255, 0.18), rgba(10, 162, 255, 0.02));
  border-color: rgba(10, 162, 255, 0.28);
}
.nav__item.is-active::before {
  content: '';
  position: absolute;
  left: 0; top: 6px; bottom: 6px;
  width: 3px;
  background: var(--c-primary);
  box-shadow: var(--glow-primary);
}
.console--collapsed .nav__item { justify-content: center; padding: 0; }
.nav__item-text { flex: 1; }
.nav__badge {
  min-width: 18px;
  height: 16px;
  padding: 0 4px;
  display: grid;
  place-items: center;
  font-size: 10px;
  border-radius: 8px;
  background: rgba(255, 45, 85, 0.2);
  color: var(--c-alarm);
  border: 1px solid rgba(255, 45, 85, 0.45);
}

.console__content {
  flex: 1;
  min-width: 0;
  position: relative;
  background: var(--bg-page);
  overflow: hidden;
}

/* 窄屏（笔记本/平板）自动收起导航文字，把宽度让给内容区 */
@media (max-width: 1100px) {
  .nav { width: 52px; }
  .nav__item { justify-content: center; padding: 0; }
  .nav__item-text,
  .nav__group-title,
  .nav__badge,
  .nav__collapse span:last-child { display: none; }
  .brand { width: 132px; }
  .brand__text span { display: none; }
  .topbar__title { display: none; }
  .chip { display: none; }
  .chip--alarm { display: flex; }
}
</style>
