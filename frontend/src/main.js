import { createApp } from 'vue';
import { createPinia } from 'pinia';
import App from './App.vue';
import router from './router';
import { initTheme } from './stores/theme';

import './design/tokens.css';
import './design/base.css';
import './design/ind.css';

// 先定主题再挂载：否则首屏会用默认深色渲染一次，浅色用户会看到闪烁
initTheme();

const app = createApp(App);
app.use(createPinia());
app.use(router);
app.mount('#app');
