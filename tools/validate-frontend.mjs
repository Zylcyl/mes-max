/**
 * 前端静态校验（无需子进程，适配受限沙箱）：
 *   1. 用 @vue/compiler-sfc 编译每个 .vue（解析 SFC、编译模板、编译 <script setup>）
 *   2. 校验所有相对/别名 import 是否指向真实存在的文件
 *   3. 校验 .vue 模板里用到的 Ind* 组件是否已 import
 */
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire('file:///D:/x/');
// 允许传相对路径（例如 `node tools/validate-frontend.mjs frontend`）：
// require() 需要绝对路径，这里统一解析
const ROOT = path.resolve(process.argv[2] || 'frontend');
const sfc = require(path.join(ROOT, 'node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js'));
const SRC = path.join(ROOT, 'src');

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (/\.(vue|js|mjs)$/.test(e.name)) out.push(p);
  }
  return out;
}

const files = walk(SRC);
const errors = [];
const warnings = [];
let okCount = 0;

function resolveImport(target, fromFile) {
  let base;
  if (target.startsWith('@/')) base = path.join(SRC, target.slice(2));
  else if (target.startsWith('.')) base = path.resolve(path.dirname(fromFile), target);
  else return 'external';
  const candidates = [base, base + '.js', base + '.mjs', base + '.vue', path.join(base, 'index.js'), path.join(base, 'index.vue')];
  return candidates.some((c) => fs.existsSync(c)) ? 'ok' : 'missing';
}

/** 从 script 内容里提取 import 语句。 */
function extractImports(code) {
  const list = [];
  const re = /import\s+(?:[\w*{}\s,]+?\s+from\s+)?['"]([^'"]+)['"]/g;
  let m;
  while ((m = re.exec(code))) list.push(m[1]);
  const re2 = /import\(\s*['"]([^'"]+)['"]\s*\)/g;
  while ((m = re2.exec(code))) list.push(m[1]);
  return list;
}

for (const file of files) {
  const rel = path.relative(ROOT, file).replace(/\\/g, '/');
  const source = fs.readFileSync(file, 'utf8');

  if (file.endsWith('.vue')) {
    const { descriptor, errors: parseErrors } = sfc.parse(source, { filename: file });
    if (parseErrors.length) {
      parseErrors.forEach((e) => errors.push(`${rel}: SFC 解析失败 → ${e.message}`));
      continue;
    }
    try {
      if (descriptor.scriptSetup || descriptor.script) {
        sfc.compileScript(descriptor, { id: rel, inlineTemplate: false });
      }
    } catch (e) {
      errors.push(`${rel}: <script setup> 编译失败 → ${e.message}`);
    }
    if (descriptor.template) {
      const r = sfc.compileTemplate({
        source: descriptor.template.content,
        filename: file,
        id: rel,
        scoped: descriptor.styles.some((s) => s.scoped),
        compilerOptions: { expressionPlugins: ['typescript'] },
      });
      if (r.errors && r.errors.length) {
        r.errors.forEach((e) => errors.push(`${rel}: 模板编译失败 → ${e.message || e}`));
      }
    }
    // 模板里出现的 Ind*/Machine*/ 大驼峰组件是否已在 script 中导入
    const template = descriptor.template ? descriptor.template.content : '';
    const scriptAll = [descriptor.script?.content || '', descriptor.scriptSetup?.content || ''].join('\n');
    const used = new Set([...template.matchAll(/<([A-Z][A-Za-z0-9]*)[\s/>]/g)].map((m) => m[1]));
    for (const comp of used) {
      if (/^(RouterView|RouterLink|Teleport|Transition|TransitionGroup|KeepAlive|Component|Suspense)$/.test(comp)) continue;
      if (!new RegExp(`\\b${comp}\\b`).test(scriptAll)) {
        errors.push(`${rel}: 模板使用了 <${comp}>，但 <script setup> 中没有导入或定义`);
      }
    }
  }

  for (const target of extractImports(source)) {
    const res = resolveImport(target, file);
    if (res === 'missing') errors.push(`${rel}: 无法解析 import "${target}"`);
  }
  okCount++;
}

console.log(`扫描文件：${files.length} 个（.vue / .js）`);
if (warnings.length) {
  console.log('\n— 提示 —');
  warnings.forEach((w) => console.log('  ' + w));
}
if (errors.length) {
  console.log(`\n— 发现问题 ${errors.length} 个 —`);
  errors.forEach((e) => console.log('  ✗ ' + e));
  process.exit(1);
}
console.log('\n✓ 全部 .vue 单文件组件编译通过，import 路径与组件引用完整');
