/**
 * 验证「令牌密钥跨重启持久化」与「环境变量优先」。
 * 用法：node verify_jwt_persist.mjs <backendDir> <jarPath>
 */
import fs from 'node:fs';
import path from 'node:path';
import { spawn } from 'node:child_process';

// 相对路径要在这里解析成绝对路径：spawn 时 cwd 会被切到 BACKEND，
// 传相对路径的 jar 会解析到 BACKEND 下面而找不到（"Unable to access jarfile"）
const BACKEND = path.resolve(process.argv[2] || 'backend');
const JAR = path.resolve(process.argv[3] || path.join(BACKEND, 'target/mes-max-backend.jar'));
const JAVA = 'C:\\Program Files\\Java\\jdk-17\\bin\\java.exe';
const OUT = 'C:\\Users\\xiamu\\AppData\\Local\\Temp\\mesmax-jwt-verify.out.log';
const SECRET_FILE = path.join(BACKEND, '.jwt-secret');
/** 之前几轮手工启动留下的日志，也要一并扫出 PID 清理掉，否则端口一直被占着。 */
const KNOWN_LOGS = [
  OUT,
  'C:\\Users\\xiamu\\AppData\\Local\\Temp\\mesmax-run1.out.log',
  'C:\\Users\\xiamu\\AppData\\Local\\Temp\\mesmax-run2.out.log',
  'C:\\Users\\xiamu\\AppData\\Local\\Temp\\mesmax-run3.out.log',
  'C:\\Users\\xiamu\\AppData\\Local\\Temp\\mesmax-run4.out.log',
  'C:\\Users\\xiamu\\AppData\\Local\\Temp\\mesmax-reset.out.log',
  'C:\\Users\\xiamu\\AppData\\Local\\Temp\\mesmax-final.out.log',
];

function startApp(extraEnv = {}) {
  const out = fs.openSync(OUT, 'w');
  const child = spawn(JAVA, ['-Dfile.encoding=UTF-8', '-jar', JAR], {
    cwd: BACKEND,
    env: { ...process.env, ...extraEnv },
    stdio: ['ignore', out, out],
  });
  currentChild = child;
  lastPid = child.pid;
  return child;
}

let lastPid = null;
let currentChild = null;

async function waitUp(timeoutMs = 60000) {
  const t0 = Date.now();
  while (Date.now() - t0 < timeoutMs) {
    try {
      const r = await fetch('http://localhost:8080/api/board/health');
      if (r.status) return true;
    } catch {
      /* 尚未启动完成 */
    }
    await new Promise((r) => setTimeout(r, 1500));
  }
  return false;
}

/**
 * 取本次启动的子进程 PID。
 * 注意：本沙箱里 Node 无法 spawn 任何子进程（EPERM），所以不能用 taskkill/PowerShell 杀进程 ——
 * 只能对自己 spawn 出来的子进程调用 child.kill()（libuv 直接 TerminateProcess，不需要再起进程）。
 */
function pidFromLog() {
  try {
    const txt = fs.readFileSync(OUT, 'utf8');
    // Spring Boot 的文案是 Starting（不是 Started）
    const m = [...txt.matchAll(/Starting MesMaxApplication using .*? PID (\d+)/g)].pop();
    return m ? Number(m[1]) : lastPid;
  } catch {
    return lastPid;
  }
}

async function portFree() {
  try {
    await fetch('http://localhost:8080/api/board/health', { signal: AbortSignal.timeout(1500) });
    return false;
  } catch {
    return true;
  }
}

/** 停掉所有已知实例并等待端口释放；仍占用则直接报错，避免再次出现「假重启」。 */
async function stopPrevious() {
  if (currentChild && !currentChild.killed) {
    currentChild.kill('SIGKILL');
    console.log(`   已停止本次启动的后端进程（PID ${currentChild.pid}）`);
  }
  await new Promise((r) => setTimeout(r, 4000));
  if (!(await portFree())) {
    throw new Error('8080 仍被占用，无法保证本次验证真的重启了后端 —— 请先手工停掉旧实例再重跑');
  }
}

async function login() {
  const r = await fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'mes123456' }),
  });
  const j = await r.json();
  return j.data?.token || null;
}

async function profile(token) {
  const r = await fetch('http://localhost:8080/api/auth/profile', {
    headers: { Authorization: `Bearer ${token}` },
  });
  const j = await r.json();
  return { http: r.status, code: j.code };
}

await stopPrevious();
if (fs.existsSync(SECRET_FILE)) fs.unlinkSync(SECRET_FILE);
console.log(`1) 启动前 .jwt-secret 存在？ ${fs.existsSync(SECRET_FILE)}`);

startApp();
if (!(await waitUp())) {
  console.log(fs.readFileSync(OUT, 'utf8').slice(-1500));
  throw new Error('第 1 次启动失败');
}
const pid1 = pidFromLog();
const size = fs.existsSync(SECRET_FILE) ? fs.readFileSync(SECRET_FILE, 'utf8').trim().length : 0;
console.log(`2) 首次启动（PID ${pid1}）后 .jwt-secret 自动生成？ ${size > 0}（密钥长度 ${size}）`);

const token1 = await login();
console.log(`3) 登录取得令牌：${token1 ? token1.slice(0, 24) + '...' : '失败'}`);
const before = await profile(token1);
console.log(`   重启前用该令牌访问 /auth/profile → HTTP ${before.http} code=${before.code}`);

await stopPrevious();
startApp();
if (!(await waitUp())) throw new Error('第 2 次启动失败');
const pid2 = pidFromLog();
console.log(`   重启为新进程 PID ${pid2}（与上次不同：${pid1 !== pid2}）`);
const after = await profile(token1);
console.log(`4) 重启后同一令牌访问 /auth/profile → HTTP ${after.http} code=${after.code}`);
console.log(after.code === 200 ? '   ✓ 令牌跨重启仍然有效，不需要重新登录' : '   ✗ 令牌失效，持久化未生效');

await stopPrevious();
startApp({ MES_JWT_SECRET: 'env-secret-for-verification-0123456789' });
if (!(await waitUp())) throw new Error('第 3 次启动失败');
console.log(`   第三次启动 PID ${pidFromLog()}`);
const withEnv = await profile(token1);
console.log(`5) 以环境变量密钥启动后，旧令牌 → HTTP ${withEnv.http} code=${withEnv.code}`);
console.log(withEnv.code === 401 ? '   ✓ 环境变量优先；密钥变化后旧令牌按预期失效' : '   ✗ 环境变量未生效');

const token2 = await login();
const afterEnv = await profile(token2);
console.log(`6) 新密钥下重新登录再访问 → HTTP ${afterEnv.http} code=${afterEnv.code}`);

await stopPrevious();
console.log('\n验证结束（后端进程已停止）');
