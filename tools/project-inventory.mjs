import fs from 'node:fs';
import path from 'node:path';

const ROOT = path.resolve(process.argv[2] || '.');
const SKIP = new Set(['node_modules', 'target', 'dist', 'data', '.git']);
const EXCLUDE_FILES = new Set(['app_out.log', 'app_err.log', 'cp.txt', 'cp2.txt', 'cp3.txt']);

const byExt = {};
let totalFiles = 0;
let totalLines = 0;
const tree = [];

function walk(dir, prefix = '') {
  const entries = fs
    .readdirSync(dir, { withFileTypes: true })
    .filter((e) => !SKIP.has(e.name) && !EXCLUDE_FILES.has(e.name))
    .sort((a, b) => (a.isDirectory() === b.isDirectory() ? a.name.localeCompare(b.name) : a.isDirectory() ? -1 : 1));

  entries.forEach((e, i) => {
    const last = i === entries.length - 1;
    const p = path.join(dir, e.name);
    tree.push(`${prefix}${last ? '└── ' : '├── '}${e.name}${e.isDirectory() ? '/' : ''}`);
    if (e.isDirectory()) {
      walk(p, prefix + (last ? '    ' : '│   '));
      return;
    }
    const ext = path.extname(e.name) || '(无扩展名)';
    const content = fs.readFileSync(p, 'utf8');
    const lines = content.split(/\r?\n/).length;
    byExt[ext] = byExt[ext] || { files: 0, lines: 0 };
    byExt[ext].files++;
    byExt[ext].lines += lines;
    totalFiles++;
    totalLines += lines;
  });
}

walk(ROOT);

console.log(tree.join('\n'));
console.log('\n===== 代码量统计（不含 node_modules / target / dist / data）=====');
Object.entries(byExt)
  .sort((a, b) => b[1].lines - a[1].lines)
  .forEach(([ext, v]) => {
    if (v.lines < 20 && v.files < 2) return;
    console.log(`${ext.padEnd(14)} ${String(v.files).padStart(4)} 个文件  ${String(v.lines).padStart(7)} 行`);
  });
console.log(`\n合计：${totalFiles} 个文件，${totalLines} 行`);
