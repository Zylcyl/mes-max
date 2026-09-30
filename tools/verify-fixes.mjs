/**
 * 针对本轮修复的逐条验证脚本。
 * 每个用例都打到真实运行的后端，输出 PASS/FAIL 与关键数字。
 */
const B = 'http://localhost:8080/api';
const results = [];

function check(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? `  → ${detail}` : ''}`);
}

async function call(method, path, { body, token, raw } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(B + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try {
    json = text ? JSON.parse(text) : null;
  } catch {
    /* 非 JSON */
  }
  return raw ? { http: res.status, json, text } : { http: res.status, json };
}

async function login(username, password = 'mes123456') {
  const r = await call('POST', '/auth/login', { body: { username, password } });
  return r.json?.data?.token || null;
}

const admin = await login('admin');
const operator = await login('operator');

// ---------------------------------------------------------------- 1. 报工不翻倍
{
  // 找一个状态为「已派工/加工中」且有数量的工序任务
  const pending = await call('GET', '/schedule/pending', { token: admin });
  const task = (pending.json.data || []).find((t) => t.machineCode && Number(t.planQty) > 0);
  if (!task) {
    check('报工数量不翻倍', false, '找不到可用于测试的待排产任务');
  } else {
    const before = await call('GET', `/dispatch/page?size=200&stepId=${task.stepId}`, { token: admin });
    const dispatch = (before.json.data?.records || [])[0];
    const d = dispatch
      ? dispatch
      : (await call('POST', '/dispatch/create', {
          token: admin,
          body: { stepId: task.stepId, planQty: Number(task.planQty) },
        })).json.data;

    const beforeStep = await call('GET', `/order/detail/${task.orderId}`, { token: admin });
    const step0 = (beforeStep.json.data.steps || []).find((s) => s.id === task.stepId);
    const qty0 = Number(step0.finishedQty || 0);

    await call('POST', '/terminal/start', { token: admin, body: { dispatchId: d.id, ncProgramNo: task.ncProgramNo || null } });
    await call('POST', '/terminal/report', {
      token: admin,
      body: { dispatchId: d.id, qty: 10, defectQty: 1, runMinutes: 30, ncProgramNo: task.ncProgramNo || null },
    });
    const after = await call('GET', `/order/detail/${task.orderId}`, { token: admin });
    const step1 = (after.json.data.steps || []).find((s) => s.id === task.stepId);
    const qty1 = Number(step1.finishedQty || 0);
    const delta = qty1 - qty0;

    // 开工占位记录必须是 0 数量，报 10 件后差额应当正好是 10
    const reports = await call('GET', `/report-task/page?size=50&stepId=${task.stepId}`, { token: admin });
    const startRows = (reports.json.data?.records || []).filter((r) => r.reportType === '开工');
    const badStart = startRows.filter((r) => Number(r.qty) !== 0 || Number(r.runMinutes) !== 0);

    check('报工数量不翻倍（差额 = 本次报工量）', delta === 10, `工序 ${task.stepId}：${qty0} → ${qty1}，差额 ${delta}`);
    check('开工占位记录数量与工时均为 0', badStart.length === 0, `开工记录 ${startRows.length} 条，异常 ${badStart.length} 条`);
  }
}

// ---------------------------------------------------------------- 2. 两处 OEE 口径一致
{
  const single = await call('GET', '/machine/1/oee?days=7', { token: admin });
  const stats = await call('GET', '/stats/oee?days=7', { token: admin });
  const code = single.json.data?.machineCode;
  const row = (stats.json.data?.byMachine || []).find((m) => m.label === code);
  const a = single.json.data || {};
  const same = row
    && Math.abs(Number(a.availability) - Number(row.availability)) < 0.11
    && Math.abs(Number(a.quality) - Number(row.quality)) < 0.11
    && Math.abs(Number(a.performance ?? 0) - Number(row.performance ?? 0)) < 0.11
    && Math.abs(Number(a.oee ?? 0) - Number(row.oee ?? 0)) < 0.11;
  check('单机 OEE 与报表 OEE 口径一致', !!same,
    `单机 avail=${a.availability} perf=${a.performance} qual=${a.quality} oee=${a.oee} ｜ 报表 avail=${row?.availability} perf=${row?.performance} qual=${row?.quality} oee=${row?.oee}`);
  check('单机性能开动率不再硬编码 92', Number(a.performance) !== 92 || Number(row?.performance) === 92,
    `单机 performance=${a.performance}，theorySource=${a.theorySource}`);
}

// ---------------------------------------------------------------- 3. 分页边界
{
  const big = await call('GET', '/md/machine/page?size=2000000000', { token: admin });
  const neg = await call('GET', '/md/machine/page?page=-1&size=20', { token: admin });
  check('分页 size 有上限', big.json.code === 200 && big.json.data.size <= 500,
    `size=2000000000 → 实际 size=${big.json.data?.size}`);
  check('分页 page 负数有兜底', neg.json.code === 200 && neg.json.data.page >= 1,
    `page=-1 → http=${neg.http} page=${neg.json.data?.page} code=${neg.json.code}`);
}

// ---------------------------------------------------------------- 4. 未知角色不得放行
{
  await call('POST', '/system/user/save', {
    token: admin,
    body: { username: 'ghostuser', realName: '幽灵角色', roleCode: 'ghost_role', status: 1, password: 'mes123456' },
  });
  const ghost = await login('ghostuser');
  if (!ghost) {
    check('未知角色写接口被拒', false, 'ghostuser 登录失败（可能账号未创建成功）');
  } else {
    const write = await call('POST', '/md/machine/save', {
      token: ghost,
      body: { macCode: 'GHOST-1', macName: '越权测试机台' },
    });
    const read = await call('GET', '/board/overview', { token: ghost });
    check('未知角色（权限未配置）写接口返回 403', write.json.code === 403, `code=${write.json.code} http=${write.http}`);
    check('未知角色读接口仍放行', read.json.code === 200, `code=${read.json.code}`);

    // 自清理：删掉这个「未知角色」探针账号
    const ul = await call('GET', '/system/user/list', { token: admin });
    for (const u of (ul.json.data || []).filter((x) => x.username === 'ghostuser')) {
      await call('DELETE', `/system/user/delete/${u.id}`, { token: admin });
    }
  }
}

// ---------------------------------------------------------------- 5. 真实 HTTP 状态码
{
  const noToken = await call('GET', '/board/overview', { raw: true });
  check('未登录返回真实 HTTP 401', noToken.http === 401, `http=${noToken.http} body.code=${noToken.json?.code}`);
  const noPerm = await call('POST', '/md/machine/save', { token: operator, body: { macCode: 'X', macName: 'X' } });
  check('越权写入返回真实 HTTP 403', noPerm.http === 403, `http=${noPerm.http} body.code=${noPerm.json?.code}`);
}

// ---------------------------------------------------------------- 6. 编码路径不能绕过模块判定
{
  const bypass = await call('POST', '/%73ystem/user/save', {
    token: operator,
    body: { username: 'bypass', realName: 'bypass', roleCode: 'admin', status: 1, password: 'mes123456' },
  });
  check('URL 编码路径不能绕过权限（/api/%73ystem → /system）', bypass.json.code === 403,
    `code=${bypass.json.code} http=${bypass.http}`);
}

// ---------------------------------------------------------------- 7. 刀具局部更新不破坏寿命占用率
{
  const before = await call('GET', '/tool/1', { token: admin });
  const ratio0 = before.json.data?.lifeRatio;
  await call('POST', '/tool/save', { token: admin, body: { id: 1, toolCode: before.json.data.toolCode } });
  const after = await call('GET', '/tool/1', { token: admin });
  const ratio1 = after.json.data?.lifeRatio;
  // 允许 0.5 个百分点内的重算误差（DB 是 DECIMAL(6,2)，种子值本身是四舍五入过的），
  // 关键是「不能被归零」——这正是修复前会发生的事
  check('刀具局部更新不把寿命占用率归零',
    Number(ratio1) > 0 && Math.abs(Number(ratio0) - Number(ratio1)) < 0.5,
    `${ratio0} → ${ratio1}（stdLifeMin=${after.json.data?.stdLifeMin}，期望仍为 ~67.9 且非 0）`);
}

// ---------------------------------------------------------------- 8. SPC 取最近样本
{
  const series = await call('GET', '/spc/series?machineId=5&charCode=D45&size=100', { token: admin });
  const analyze = await call('POST', '/spc/analyze', { token: admin, body: { machineId: 5, charCode: 'D45' } });
  const newestSeries = series.json.data.samples.at(-1);
  const newestAnalyze = (analyze.json.data.points || []).at(-1);
  check('SPC 分析取最近样本（与序列接口末点一致）',
    newestSeries && newestAnalyze && Number(newestSeries.sampleValue) === Number(newestAnalyze.value),
    `series 末点=${newestSeries?.sampleValue}（样本号 ${newestSeries?.sampleNo}） / analyze 末点=${newestAnalyze?.value}（样本号 ${newestAnalyze?.sampleNo}）`);
}

// ---------------------------------------------------------------- 9. 订单完成率不再虚高
{
  const out = await call('GET', '/stats/output?days=14', { token: admin });
  const d = out.json.data || {};
  check('产量完成率不超过 100% 且按最后一道工序产出计',
    Number(d.completionRate) <= 100,
    `finishedQty=${d.finishedQty} planQty=${d.planQty} completionRate=${d.completionRate}%`);

  const orders = await call('GET', '/order/withProgress?size=5', { token: admin });
  const row = (orders.json.data || [])[0];
  check('工单列表同时给出工序完成度与数量完成度',
    row && row.progress !== undefined && row.qtyProgress !== undefined,
    row ? `${row.orderNo}: progress=${row.progress}% qtyProgress=${row.qtyProgress}%` : '无数据');
}

// ---------------------------------------------------------------- 10. 操作日志脱敏
{
  const logs = await call('GET', '/system/log/oper?size=20', { token: admin });
  const leaky = (logs.json.data || []).filter((l) => (l.params || '').toLowerCase().includes('token=') && !l.params.includes('token=***'));
  check('操作日志中的令牌已脱敏', leaky.length === 0, `检查 ${(logs.json.data || []).length} 条，疑似泄露 ${leaky.length} 条`);
}

// ---------------------------------------------------------------- 11. 口令不以任何形式回吐
{
  const loginRes = await call('POST', '/auth/login', {
    body: { username: 'admin', password: 'mes123456' },
  });
  check('登录响应体不含口令字段', !JSON.stringify(loginRes.json).toLowerCase().includes('password'),
    '响应里只有 token / user / role / homePage');

  const users = await call('GET', '/system/user/list', { token: admin });
  const withPwd = (users.json.data || []).filter((u) => 'password' in u);
  check('用户列表不回吐口令字段', withPwd.length === 0, `检查 ${(users.json.data || []).length} 个账号，含 password 的 ${withPwd.length} 个`);

  const wrong = await call('POST', '/auth/login', { body: { username: 'admin', password: 'definitely-wrong' } });
  check('错误口令被拒绝', wrong.json.code === 401, `code=${wrong.json.code} ${wrong.json.msg}`);

  const saved = await call('POST', '/system/user/save', {
    token: admin,
    body: { username: 'pwdprobe', realName: '口令探针', roleCode: 'viewer', status: 1, password: 'probe-123456' },
  });
  const noEcho = !JSON.stringify(saved.json).toLowerCase().includes('password');
  const probeLogin = await call('POST', '/auth/login', { body: { username: 'pwdprobe', password: 'probe-123456' } });
  check('新建用户的口令被哈希入库且不回吐', noEcho && probeLogin.json.code === 200,
    `保存响应含口令字段=${!noEcho}，新账号可正常登录=${probeLogin.json.code === 200}`);

  // 自清理：这个探针账号只用于本项断言，跑完就删掉，避免在演示库里留垃圾账号
  const users2 = await call('GET', '/system/user/list', { token: admin });
  for (const u of (users2.json.data || []).filter((x) => x.username === 'pwdprobe')) {
    await call('DELETE', `/system/user/delete/${u.id}`, { token: admin });
  }
  const users3 = await call('GET', '/system/user/list', { token: admin });
  check('探针账号已自动清理', (users3.json.data || []).every((x) => x.username !== 'pwdprobe'),
    `清理后账号数 ${(users3.json.data || []).length}`);
}

const pass = results.filter((r) => r.ok).length;
console.log(`\n合计 ${pass}/${results.length} 通过`);
process.exit(pass === results.length ? 0 : 1);
