/**
 * 前端开发服务器（Vite JS API 版）。
 *
 * 为什么不直接用 `npm run dev`：
 *   1) 某些受限环境里 Node 无法创建子进程（`spawn EPERM`），npm 脚本自己就要 spawn 一个 shell；
 *   2) 即便绕开 npm，Vite 默认会做两件依赖 esbuild（=需要 spawn 子进程）的事：
 *      · 用 esbuild 打包 vite.config.js —— 这里改用内联配置（configFile: false）绕开；
 *      · 用 esbuild 预构建依赖（optimizeDeps）—— 这里关掉发现（noDiscovery + 空 include）。
 *   代价：依赖不做预打包，首次加载页面稍慢；功能不受影响。
 *
 * 用法：node tools/dev-server.mjs           （默认 5173，代理 /api → 8080）
 */
import path from 'node:path';
import fs from 'node:fs';
import { createRequire } from 'node:module';
import { pathToFileURL } from 'node:url';

/*
 * Vite 的 optimizeSafeRealPathSync() 会先做一次「原生 realpath」探测：
 *   若抛 EISDIR → 直接采用非原生实现并 return（不 spawn 任何进程）；
 *   否则调用 exec('net use') 探测网络映射盘 —— 这一步在禁止创建子进程的环境里会抛 spawn EPERM，
 *   而该异常发生在 Vite 的模块初始化期，会导致此后所有模块请求 500。
 *
 * 因此这里把「对当前工作目录的那一次探测」定向改成抛 EISDIR，让 Vite 走早退分支。
 * 只拦截针对工作目录本身的调用，其它 realpath 一律透传，不影响正常模块解析。
 */
{
  const originalNative = fs.realpathSync.native;
  const cwd = process.cwd();
  const cwdWithSep = cwd.endsWith(path.sep) ? cwd : cwd + path.sep;
  fs.realpathSync.native = function patchedRealpathNative(p, ...rest) {
    if (typeof p === 'string' && (p === cwd || p === cwdWithSep)) {
      const err = new Error('EISDIR: illegal operation on a directory, read');
      err.code = 'EISDIR';
      throw err;
    }
    return originalNative.call(fs.realpathSync, p, ...rest);
  };
  process.on('exit', () => {
    fs.realpathSync.native = originalNative;
  });
}

const ROOT = path.resolve('frontend');
const PORT = Number(process.env.MES_WEB_PORT || 5173);
const API_TARGET = process.env.MES_API_TARGET || 'http://localhost:8080';

/*
 * 依赖要从 frontend/ 里解析：本脚本位于 tools/，而 vite / @vitejs/plugin-vue
 * 装在 frontend/node_modules 下，直接 `import 'vite'` 会 ERR_MODULE_NOT_FOUND。
 */
const require = createRequire(path.join(ROOT, 'package.json'));
// Windows 下 require.resolve 返回的是 "D:\..." 形式，import() 只接受 URL，必须先转成 file://
const viteMod = await import(pathToFileURL(require.resolve('vite')).href);
const createServer = viteMod.createServer || viteMod.default.createServer;
const vueMod = await import(pathToFileURL(require.resolve('@vitejs/plugin-vue')).href);
const vue = vueMod.default || vueMod;

const server = await createServer({
  configFile: false, // 不让 Vite 去打包配置文件（那一步会用到 esbuild）
  root: ROOT,
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.join(ROOT, 'src'),
      /*
       * 把依赖指向**浏览器版 ESM**：
       * 默认解析到 vue.runtime.esm-bundler.js 这类「bundler 版」，里面有 `process.env.NODE_ENV`，
       * 而 Vite 是调用 esbuild 去替换这个表达式的 —— 在禁止 spawn 的环境里就会 500。
       * 浏览器版是打包好的自包含 ESM，既没有 process.env，也不需要预构建。
       */
      vue: path.join(ROOT, 'node_modules/vue/dist/vue.runtime.esm-browser.js'),
      'vue-router': path.join(ROOT, 'node_modules/vue-router/dist/vue-router.esm-browser.js'),
      pinia: path.join(ROOT, 'node_modules/pinia/dist/pinia.esm-browser.js'),
      echarts: path.join(ROOT, 'node_modules/echarts/dist/echarts.esm.js'),
    },
    extensions: ['.mjs', '.js', '.json', '.vue'],
  },
  esbuild: false, // 关闭 esbuild 转译插件（本项目只有 .js/.vue，无需 TS 转译）
  optimizeDeps: {
    noDiscovery: true, // 不做依赖预构建（预构建 = esbuild = 需要 spawn）
    include: [],
  },
  server: {
    port: PORT,
    host: '0.0.0.0',
    strictPort: true,
    // 关闭 HMR 客户端注入：该插件内部同样用 esbuild 做 define 替换，
    // 关掉之后页面不再需要额外注入的客户端代码，功能不受影响（只是没有热更新）
    hmr: false,
    proxy: {
      '/api': { target: API_TARGET, changeOrigin: true },
    },
  },
  css: { devSourcemap: true },
});

await server.listen();
server.printUrls();
console.log(`\n[MES-MAX] 前端已启动：http://localhost:${PORT}`);
console.log(`[MES-MAX] 接口代理：/api → ${API_TARGET}`);
