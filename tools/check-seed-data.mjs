import fs from 'node:fs';
import path from 'node:path';

const file = path.resolve(process.argv[2] || 'sql/seed-data.sql');
const raw = fs.readFileSync(file, 'utf8');

// 去掉注释行，再把整个文件按分号切成语句
const cleaned = raw
  .split(/\r?\n/)
  .filter((l) => !l.trim().startsWith('--'))
  .join('\n');
const statements = cleaned.split(';').map((s) => s.trim()).filter(Boolean);

/** 统计一段 VALUES 片段里处于「第一层括号」的值的个数。 */
function valueGroups(text) {
  const groups = [];
  let depth = 0;
  let inStr = false;
  let n = 0;
  let started = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (c === "'") { inStr = !inStr; continue; }
    if (inStr) continue;
    if (c === '(') {
      depth++;
      if (depth === 1) { n = 1; started = true; }
      continue;
    }
    if (c === ')') {
      depth--;
      if (depth === 0 && started) { groups.push(n); started = false; }
      continue;
    }
    if (c === ',' && depth === 1) n++;
  }
  return groups;
}

let bad = 0;
let checked = 0;
for (const st of statements) {
  const m = st.match(/INSERT\s+INTO\s+([\w.]+)\s*\(([\s\S]*?)\)\s*VALUES([\s\S]*)$/i);
  if (!m) continue;
  const table = m[1];
  const cols = m[2].split(',').filter((s) => s.trim()).length;
  const groups = valueGroups(m[3]);
  groups.forEach((n, i) => {
    checked++;
    if (n !== cols) {
      console.log(`!! ${table} 第 ${i + 1} 行数据：values=${n} cols=${cols}`);
      bad++;
    }
  });
}
console.log(`已校验 ${checked} 行数据`);
console.log(bad === 0 ? 'OK: all seed rows match column counts' : `FOUND ${bad} mismatched rows`);
