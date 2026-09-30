import { createRouter, createWebHashHistory } from 'vue-router';
import { getToken } from '@/api/http';
import { useAuth } from '@/stores/auth';
import { useToast } from '@/stores/toast';

const routes = [
  { path: '/login', name: 'login', component: () => import('@/views/Login.vue'), meta: { public: true, title: '登录' } },
  { path: '/', redirect: '/board' },
  {
    path: '/board',
    name: 'board',
    component: () => import('@/views/Board.vue'),
    meta: { title: '车间实时看板', full: true, group: '车间监控', module: 'dashboard' },
  },
  {
    path: '/monitor',
    name: 'monitor',
    component: () => import('@/views/MachineMonitor.vue'),
    meta: { title: '设备监控', group: '车间监控', module: 'dashboard' },
  },
  {
    path: '/andon',
    name: 'andon',
    component: () => import('@/views/Andon.vue'),
    meta: { title: '安灯呼叫', group: '车间监控', module: 'respond' },
  },
  {
    path: '/order',
    name: 'order',
    component: () => import('@/views/Order.vue'),
    meta: { title: '生产订单', group: '生产执行', module: 'order' },
  },
  {
    path: '/schedule',
    name: 'schedule',
    component: () => import('@/views/Schedule.vue'),
    meta: { title: '生产排产', group: '生产执行', module: 'schedule' },
  },
  {
    path: '/dispatch',
    name: 'dispatch',
    component: () => import('@/views/Dispatch.vue'),
    meta: { title: '派工管理', group: '生产执行', module: 'dispatch' },
  },
  {
    path: '/terminal',
    name: 'terminal',
    component: () => import('@/views/Terminal.vue'),
    meta: { title: '车间报工终端', group: '生产执行', module: 'terminal' },
  },
  {
    path: '/stop',
    name: 'stop',
    component: () => import('@/views/StopAnalysis.vue'),
    meta: { title: '停机分析', group: '生产执行', module: 'report' },
  },
  {
    path: '/quality',
    name: 'quality',
    component: () => import('@/views/Quality.vue'),
    meta: { title: '质量检验', group: '质量与工艺', module: 'quality' },
  },
  {
    path: '/spc',
    name: 'spc',
    component: () => import('@/views/Spc.vue'),
    meta: { title: 'SPC 过程控制', group: '质量与工艺', module: 'spc' },
  },
  {
    path: '/tool',
    name: 'tool',
    component: () => import('@/views/Tool.vue'),
    meta: { title: '刀具管理', group: '质量与工艺', module: 'tool' },
  },
  {
    path: '/equipment',
    name: 'equipment',
    component: () => import('@/views/Equipment.vue'),
    meta: { title: '设备维保', group: '设备维护', module: 'equipment' },
  },
  {
    path: '/trace',
    name: 'trace',
    component: () => import('@/views/Trace.vue'),
    meta: { title: '谱系追溯', group: '追溯与报表', module: 'trace' },
  },
  {
    path: '/stats',
    name: 'stats',
    component: () => import('@/views/Stats.vue'),
    meta: { title: '统计报表', group: '追溯与报表', module: 'stats' },
  },
  {
    path: '/system',
    name: 'system',
    component: () => import('@/views/System.vue'),
    meta: { title: '系统管理', group: '系统', module: 'system' },
  },
  { path: '/:pathMatch(.*)*', redirect: '/board' },
];

const router = createRouter({
  history: createWebHashHistory(),
  routes,
});

router.beforeEach((to, from, next) => {
  const logged = !!getToken();
  if (!to.meta.public && !logged) {
    next({ path: '/login', query: { redirect: to.fullPath } });
    return;
  }
  if (to.path === '/login' && logged) {
    next({ path: '/board' });
    return;
  }
  /*
   * 路由级权限：菜单不显示只是「看不见」，直接把地址栏改成 #/system 仍然能进来。
   * 这里做一次前端校验（真正的防线仍是后端对写接口的模块拦截），
   * 无权访问时回到该角色的默认首页。
   */
  if (logged && to.meta.module && !useAuth().can(to.meta.module)) {
    const home = useAuth().homePage || '/board';
    if (to.path !== home) {
      useToast().warn('当前账号没有该功能权限');
      next({ path: home });
      return;
    }
  }
  next();
});

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · MES-MAX` : 'MES-MAX · CNC 制造执行系统';
});

export default router;
