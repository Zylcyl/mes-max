import { defineStore } from 'pinia';
import { computed, ref } from 'vue';
import { api } from '@/api';
import { getToken, setToken } from '@/api/http';

const USER_KEY = 'mesmax_user';

export const useAuth = defineStore('auth', () => {
  const token = ref(getToken());
  const user = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'));
  const role = ref(null);
  const perms = ref('');
  /** 权限是否已成功加载：未加载完成前不隐藏菜单与按钮，避免刷新时菜单闪空。 */
  const permsLoaded = ref(false);

  const isLogin = computed(() => !!token.value);
  const displayName = computed(() => user.value?.realName || user.value?.username || '未登录');
  const roleName = computed(() => role.value?.roleName || user.value?.roleCode || '');
  const homePage = computed(() => role.value?.homePage || '/board');

  function can(module) {
    if (!module) return true;
    if (!permsLoaded.value) return true;
    const p = perms.value || '';
    return p.includes('*') || p.split(',').map((s) => s.trim()).includes(module);
  }

  async function login(username, password) {
    const data = await api.auth.login(username, password);
    token.value = data.token;
    setToken(data.token);
    user.value = data.user;
    localStorage.setItem(USER_KEY, JSON.stringify(data.user));
    role.value = data.role;
    perms.value = data.role?.perms || '';
    permsLoaded.value = true;
    return data;
  }

  async function loadProfile() {
    const data = await api.auth.profile();
    role.value = data.role;
    perms.value = data.perms || '';
    permsLoaded.value = true;
    return data;
  }

  async function logout() {
    try {
      await api.auth.logout();
    } catch {
      /* 忽略登出接口异常 */
    }
    token.value = '';
    user.value = null;
    role.value = null;
    perms.value = '';
    permsLoaded.value = false;
    setToken('');
    localStorage.removeItem(USER_KEY);
  }

  return {
    token, user, role, perms, permsLoaded,
    isLogin, displayName, roleName, homePage,
    can, login, logout, loadProfile,
  };
});
