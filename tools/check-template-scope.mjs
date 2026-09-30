/**
 * 模板作用域检查：把 .vue 模板里用到的标识符，与 <script setup> 中声明/导入的名字做交叉比对，
 * 提前发现「模板用了但脚本没定义」这类只会在运行期报错的问题。
 */
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';

const ROOT = path.resolve(process.argv[2] || 'frontend');
const require = createRequire('file:///D:/x/');
const sfc = require(path.join(ROOT, 'node_modules/@vue/compiler-sfc/dist/compiler-sfc.cjs.js'));
const SRC = path.join(ROOT, 'src');

const GLOBALS = new Set([
  'true', 'false', 'null', 'undefined', 'Math', 'Date', 'Object', 'Array', 'Number', 'String', 'Boolean',
  'JSON', 'console', 'window', 'document', 'confirm', 'alert', 'parseInt', 'parseFloat', 'isNaN', 'NaN',
  'Set', 'Map', 'Promise', 'RegExp', 'Error', 'encodeURIComponent', 'decodeURIComponent', 'setTimeout',
  'setInterval', 'clearInterval', 'clearTimeout', 'Infinity',
  // Vue 内置与 JS 关键字（模板里合法出现）
  'event', 'slots', 'attrs', 'refs', 'emit', 'props', 'in', 'of', 'typeof', 'instanceof', 'new', 'return',
  'if', 'else', 'await', 'async', 'this', 'void', 'delete', 'function', 'key', 'index',
]);

function walk(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walk(p, out);
    else if (e.name.endsWith('.vue')) out.push(p);
  }
  return out;
}

function declaredNames(code) {
  const names = new Set();
  const add = (n) => n && names.add(n);

  // import { a, b as c } from ...
  for (const m of code.matchAll(/import\s*\{([^}]+)\}\s*from/g)) {
    m[1].split(',').forEach((part) => {
      const seg = part.trim().split(/\s+as\s+/);
      add((seg[1] || seg[0] || '').trim());
    });
  }
  for (const m of code.matchAll(/import\s+([A-Za-z_$][\w$]*)\s*(?:,|from)/g)) add(m[1]);
  for (const m of code.matchAll(/import\s+\*\s+as\s+([A-Za-z_$][\w$]*)\s+from/g)) add(m[1]);

  // const/let/var 声明（含解构）
  for (const m of code.matchAll(/\b(?:const|let|var)\s+([A-Za-z_$][\w$]*)/g)) add(m[1]);
  for (const m of code.matchAll(/\b(?:const|let|var)\s*\{([^}]+)\}/g)) {
    m[1].split(',').forEach((part) => {
      const seg = part.trim().split(':');
      add((seg[seg.length - 1] || '').trim().replace(/=.*$/, '').trim());
    });
  }
  for (const m of code.matchAll(/\b(?:const|let|var)\s*\[([^\]]+)\]/g)) {
    m[1].split(',').forEach((part) => add(part.trim().replace(/=.*$/, '').trim()));
  }

  for (const m of code.matchAll(/function\s+([A-Za-z_$][\w$]*)/g)) add(m[1]);
  for (const m of code.matchAll(/\b(?:async\s+)?function\s+([A-Za-z_$][\w$]*)/g)) add(m[1]);

  // defineProps({ a: {...}, b: {...} }) 里的属性名会暴露到模板。
  // 这里用花括号配平来截取，避免内部 `() => ({})` 这类写法提前截断。
  const dpIdx = code.indexOf('defineProps(');
  if (dpIdx >= 0) {
    let i = code.indexOf('{', dpIdx);
    let depth = 0;
    let end = -1;
    for (let j = i; j < code.length; j++) {
      if (code[j] === '{') depth++;
      else if (code[j] === '}') {
        depth--;
        if (depth === 0) {
          end = j;
          break;
        }
      }
    }
    if (end > i) {
      const body = code.slice(i + 1, end);
      let d = 0;
      for (const line of body.split('\n')) {
        if (d === 0) {
          const m = line.match(/^\s*([A-Za-z_$][\w$]*)\s*:/);
          if (m) add(m[1]);
        }
        d += (line.match(/\{/g) || []).length - (line.match(/\}/g) || []).length;
      }
    }
  }
  return names;
}

/** 抽取模板中的表达式文本。 */
function templateExpressions(tpl) {
  const exprs = [];
  for (const m of tpl.matchAll(/\{\{([\s\S]*?)\}\}/g)) exprs.push(m[1]);
  for (const m of tpl.matchAll(/\s(?::|v-bind:|@|v-on:|v-if|v-else-if|v-show|v-for|v-model(?::\w+)?)[\w.:-]*\s*=\s*"([^"]*)"/g)) {
    exprs.push(m[1]);
  }
  return exprs;
}

/** 从表达式抽取可能被求值的标识符。 */
function identifiers(expr) {
  const cleaned = expr
    .replace(/'(?:[^'\\]|\\.)*'/g, ' ')
    .replace(/"(?:[^"\\]|\\.)*"/g, ' ')
    // 模板字符串（含嵌套）连清两遍
    .replace(/`[^`]*`/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    // 去掉「箭头函数参数」，形如 (r) => 或 r => 或 (a, b) =>
    .replace(/\(([^()]*)\)\s*=>/g, ' ')
    .replace(/\b[A-Za-z_$][\w$]*\s*=>/g, ' ')
    // 去掉对象字面量的键，形如 { key: 或 { minWidth:
    .replace(/([{,]\s*)[A-Za-z_$][\w$]*\s*:/g, '$1')
    .replace(/(['"])[^'"]*\1/g, ' ');
  const out = [];
  const re = /(\.\s*)?(\$?\b[A-Za-z_][\w$]*)/g;
  let m;
  while ((m = re.exec(cleaned))) {
    if (m[1]) continue; // 属性访问，跳过
    const id = m[2].replace(/^\$/, '');
    // 形如 ` / OP${t.seqNo}` 这种嵌套模板字符串的残留片段，不是真实标识符
    if (/\$$/.test(id)) continue;
    out.push(id);
  }
  return out;
}

const problems = [];
let checked = 0;

for (const file of walk(SRC)) {
  const rel = path.relative(SRC, file).replace(/\\/g, '/');
  const src = fs.readFileSync(file, 'utf8');
  const { descriptor } = sfc.parse(src, { filename: file });
  if (!descriptor.template) continue;
  const code = [descriptor.script?.content || '', descriptor.scriptSetup?.content || ''].join('\n');
  const names = declaredNames(code);

  // v-for 局部变量：形如 v-for="(a, b) in list" / "x in y"
  const tpl = descriptor.template.content;
  const locals = new Set();
  for (const m of tpl.matchAll(/v-for\s*=\s*"\s*\(?([^)"]*?)\)?\s+(?:in|of)\s/g)) {
    m[1].split(',').forEach((v) => locals.add(v.trim()));
  }
  // 模板内联箭头函数参数：(r) => ... / r => ...
  for (const m of tpl.matchAll(/\(([^()]*?)\)\s*=>/g)) {
    m[1]
      .replace(/[[\]{}]/g, ' ')
      .split(',')
      .forEach((v) => locals.add(v.trim()));
  }
  for (const m of tpl.matchAll(/[\s"'{(]([A-Za-z_$][\w$]*)\s*=>/g)) locals.add(m[1]);
  // <slot name="x" :row="row" ...> 的插槽作用域参数由使用方提供，这里做保守处理
  for (const m of tpl.matchAll(/#\w+\s*=\s*"\{([^}]+)\}"/g)) {
    m[1].split(',').forEach((v) => locals.add(v.trim().split(':')[0].trim()));
  }
  for (const m of tpl.matchAll(/v-slot(?::\w+)?\s*=\s*"\{([^}]+)\}"/g)) {
    m[1].split(',').forEach((v) => locals.add(v.trim().split(':')[0].trim()));
  }

  const unknown = new Set();
  const context = [];
  for (const expr of templateExpressions(tpl)) {
    for (const id of identifiers(expr)) {
      if (GLOBALS.has(id) || names.has(id) || locals.has(id)) continue;
      unknown.add(id);
      if (process.env.DEBUG_SCOPE) context.push(`${id} :: ${expr.replace(/\s+/g, ' ').slice(0, 150)}`);
    }
  }
  checked++;
  if (unknown.size) {
    problems.push(`${rel}: 模板引用了未定义的标识符 → ${[...unknown].join(', ')}`);
    context.slice(0, 4).forEach((c) => problems.push(`      ↳ ${c}`));
  }
}

console.log(`已检查 ${checked} 个含模板的 .vue 文件`);
if (problems.length) {
  console.log(`\n— 可疑引用 ${problems.length} 处 —`);
  problems.forEach((p) => console.log('  ✗ ' + p));
  process.exit(1);
}
console.log('✓ 模板中引用的变量与函数均能在脚本作用域内找到定义');
