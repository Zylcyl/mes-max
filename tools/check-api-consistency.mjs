/**
 * 前后端接口一致性检查：
 * 从 frontend/src/api/index.js 提取所有请求路径，与后端 Controller 的
 * @RequestMapping/@GetMapping/@PostMapping/@PutMapping/@DeleteMapping 拼出的路径做匹配。
 */
import fs from 'node:fs';
import path from 'node:path';

const FE = path.resolve(process.argv[2] || 'frontend');
const BE = path.resolve(process.argv[3] || 'backend/src/main/java');

// ---------- 1. 收集后端路由 ----------
const controllers = [];
function walkJava(dir, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) walkJava(p, out);
    else if (e.name.endsWith('.java')) out.push(p);
  }
  return out;
}

const routes = new Set();
for (const file of walkJava(BE)) {
  const src = fs.readFileSync(file, 'utf8');
  const base = src.match(/@RequestMapping\("([^"]+)"\)/);
  const basePath = base ? base[1] : '';
  if (!basePath) continue;
  const re = /@(Get|Post|Put|Delete)Mapping\(\s*(?:value\s*=\s*)?"([^"]*)"/g;
  let m;
  while ((m = re.exec(src))) {
    const full = (basePath + (m[2] || '')).replace(/\/+/g, '/');
    routes.add(`${m[1].toUpperCase()} ${full}`);
  }
  // 基类 BaseCrudController 提供的通用接口
  if (src.includes('extends BaseCrudController')) {
    for (const [method, suffix] of [
      ['GET', '/page'],
      ['GET', '/list'],
      ['POST', '/save'],
      ['PUT', '/update'],
      ['DELETE', '/delete/{id}'],
    ]) {
      routes.add(`${method} ${(basePath + suffix).replace(/\/+/g, '/')}`);
    }
    routes.add(`GET ${(basePath + '/{id}').replace(/\/+/g, '/')}`);
  }
}

// ---------- 2. 收集前端调用路径 ----------
const api = fs.readFileSync(path.join(FE, 'src/api/index.js'), 'utf8');
const calls = [];
const re = /http\.(get|post|put|del)\(\s*[`'"]([^`'"]+)[`'"]/g;
let m;
while ((m = re.exec(api))) {
  const method = m[1] === 'del' ? 'DELETE' : m[1].toUpperCase();
  calls.push({ method, raw: m[2] });
  // 通用 CRUD 工厂生成的 5 个接口
  // 说明：`page(base)` 这个工厂本身不是真实调用，路径含 ${ 的一律不做展开
  // 只对「一级资源路径」展开通用 CRUD；子资源（/md/bom/item/save 之类）不展开
  if (/^\/(md\/[a-zA-Z]+|order|tool|qc|defect|andon|stop|dispatch)$/.test(m[2])) {
    for (const [mm, sfx] of [['GET', '/page'], ['GET', '/list'], ['POST', '/save'], ['PUT', '/update']]) {
      calls.push({ method: mm, raw: m[2] + sfx });
    }
  }
}

// 过滤掉工厂函数自身产生的伪调用
const realCalls = calls.filter((c) => !c.raw.startsWith('${'));
const ignored = calls.length - realCalls.length;
calls.length = 0;
calls.push(...realCalls);

function normalize(p) {
  return p
    .replace(/\$\{[^}]+\}/g, '{id}')
    .replace(/\?.*$/, '')
    .replace(/\/+$/, '');
}

const misses = [];
const matched = [];
for (const c of calls) {
  const p = normalize(c.raw);
  const full = `${c.method} ${p}`;
  if (routes.has(full)) {
    matched.push(full);
    continue;
  }
  // 允许 {id} 占位符差异
  const alt = [...routes].find((r) => {
    if (!r.startsWith(c.method + ' ')) return false;
    const a = r.slice(c.method.length + 1).split('/');
    const b = p.split('/');
    if (a.length !== b.length) return false;
    return a.every((seg, i) => seg === b[i] || /^\{.*\}$/.test(seg) || /^\{.*\}$/.test(b[i]));
  });
  if (alt) matched.push(alt);
  else misses.push(full);
}

console.log(`后端路由 ${routes.size} 条；前端调用 ${calls.length} 条；匹配 ${matched.length} 条`);
if (misses.length) {
  console.log(`\n— 未在后端找到对应路由（${misses.length} 条）—`);
  [...new Set(misses)].forEach((x) => console.log('  ✗ ' + x));
  process.exit(1);
}
console.log('✓ 前端调用的接口在后端全部存在');
