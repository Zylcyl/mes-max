import { defineStore } from 'pinia';
import { ref } from 'vue';

let seq = 0;

export const useToast = defineStore('toast', () => {
  const items = ref([]);

  function push(type, msg, duration = 2600) {
    const id = ++seq;
    items.value.push({ id, type, msg });
    setTimeout(() => {
      items.value = items.value.filter((t) => t.id !== id);
    }, duration);
  }

  return {
    items,
    ok: (m) => push('ok', m),
    warn: (m) => push('warn', m),
    error: (m) => push('error', m, 4200),
    info: (m) => push('info', m),
  };
});
