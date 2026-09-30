import { defineStore } from 'pinia';
import { ref } from 'vue';
import { api } from '@/api';

/** 字典缓存：登录后一次性拉全量字典（数据量极小），供各页面直接取标签与颜色。 */
export const useDict = defineStore('dict', () => {
  const all = ref({});
  const loaded = ref(false);

  async function load(force = false) {
    if (loaded.value && !force) return all.value;
    all.value = await api.system.dictAll();
    loaded.value = true;
    return all.value;
  }

  function items(type) {
    return all.value[type] || [];
  }

  function label(type, value) {
    const hit = items(type).find((d) => d.dictValue === value);
    return hit ? hit.dictLabel : value;
  }

  function color(type, value) {
    const hit = items(type).find((d) => d.dictValue === value);
    return hit ? hit.color : null;
  }

  return { all, loaded, load, items, label, color };
});
