const BASE = 'http://localhost:8080/api';
let token = '';

async function call(method, path, body) {
  const r = await fetch(BASE + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await r.text();
  let json;
  try { json = JSON.parse(text); } catch { json = { raw: text.slice(0, 200) }; }
  return { status: r.status, json };
}

function brief(o, depth = 0) {
  if (o === null || o === undefined) return String(o);
  if (Array.isArray(o)) return `[${o.length} items]${o.length ? ' first=' + brief(o[0], depth + 1) : ''}`;
  if (typeof o === 'object') {
    const keys = Object.keys(o).slice(0, 8);
    return '{' + keys.map((k) => `${k}:${brief(o[k], depth + 1)}`).join(', ').slice(0, 240) + (Object.keys(o).length > 8 ? ', …' : '') + '}';
  }
  return String(o).slice(0, 60);
}

const results = [];
async function check(name, method, path, body, expectArrayOrKeys) {
  const { status, json } = await call(method, path, body);
  const ok = status === 200 && json.code === 200;
  results.push(`${ok ? 'PASS' : 'FAIL'} ${method} ${path} → code=${json.code} ${json.msg ?? ''}`);
  if (!ok) {
    console.log(`FAIL ${method} ${path}\n  ${JSON.stringify(json).slice(0, 500)}`);
    return null;
  }
  const d = json.data;
  if (expectArrayOrKeys) {
    if (Array.isArray(d)) {
      if (d.length === 0 && expectArrayOrKeys !== 'mayEmpty') console.log(`  ⚠ ${path} 返回空数组`);
      else console.log(`  ${path} → ${d.length} 条，样例 ${brief(d[0])}`);
    } else if (d && typeof d === 'object') {
      console.log(`  ${path} → ${brief(d)}`);
    } else {
      console.log(`  ${path} → ${brief(d)}`);
    }
  }
  return d;
}

console.log('===== 1. 登录 =====');
const login = await check('login', 'POST', '/auth/login', { username: 'admin', password: 'mes123456' }, false);
if (login?.token) token = login.token;
console.log('token:', token ? token.slice(0, 32) + '…' : 'NONE');

console.log('\n===== 2. 权限与登录失败用例 =====');
const bad = await call('POST', '/auth/login', { username: 'admin', password: 'wrong' });
console.log(`预期失败（code=${bad.json.code}）：${bad.json.msg}`);
const noAuth = await call('GET', '/board/overview');
console.log(`无令牌访问受保护接口（code=${noAuth.json.code}）：${noAuth.json.msg}`);

console.log('\n===== 3. 看板与设备 =====');
await check('board', 'GET', '/board/overview', null, true);
await check('matrix', 'GET', '/board/matrix', null, true);
await check('trend', 'GET', '/board/trend?days=14', null, true);
await check('ranking', 'GET', '/board/ranking', null, true);
await check('machineLive', 'GET', '/machine/live', null, true);
await check('machineDetail', 'GET', '/machine/1/detail', null, true);
await check('telemetry', 'GET', '/machine/1/telemetry?hours=8', null, true);
await check('statusLogs', 'GET', '/machine/1/statusLogs?days=3', null, true);
await check('machineOee', 'GET', '/machine/1/oee?days=7', null, true);
await check('statusDist', 'GET', '/machine/1/statusDistribution', null, true);

console.log('\n===== 4. 生产 =====');
await check('orders', 'GET', '/order/withProgress', null, true);
await check('orderPage', 'GET', '/order/page?page=1&size=5', null, true);
await check('orderDetail', 'GET', '/order/detail/1', null, true);
await check('kitCheck', 'GET', '/order/kitCheck/1', null, true);
await check('gantt', 'GET', '/schedule/gantt', null, true);
await check('machineLoad', 'GET', '/schedule/machineLoad', null, true);
await check('pending', 'GET', '/schedule/pending', null, true);
await check('resources', 'GET', '/schedule/resources', null, true);
await check('dispatchPage', 'GET', '/dispatch/page?page=1&size=5', null, true);
await check('terminal', 'GET', '/terminal/tasks?machineId=1', null, true);
await check('terminalHourly', 'GET', '/terminal/hourly/1', null, true);
await check('stops', 'GET', '/stop/page?page=1&size=5', null, true);
await check('stopStats', 'GET', '/stop/statistics?days=7', null, true);
await check('andon', 'GET', '/andon/open', null, true);
await check('andonStats', 'GET', '/andon/statistics?days=7', null, true);

console.log('\n===== 5. 质量 / 刀具 / 设备 / 追溯 / 报表 =====');
await check('qcPage', 'GET', '/qc/page?page=1&size=5', null, true);
await check('qcStats', 'GET', '/qc/statistics?days=14', null, true);
await check('defectPage', 'GET', '/defect/page?page=1&size=5', null, true);
await check('defectStats', 'GET', '/defect/statistics?days=14', null, true);
await check('spcGroups', 'GET', '/spc/groups', null, true);
await check('spcSeries', 'GET', '/spc/series?machineId=5', null, true);
await check('toolOverview', 'GET', '/tool/overview', null, true);
await check('toolTrace', 'GET', '/tool/trace/T-EM12-01', null, true);
await check('eqOverview', 'GET', '/equipment/overview?days=30', null, true);
await check('eqCheckSheet', 'GET', '/equipment/checkSheet/1', null, true);
await check('traceOverview', 'GET', '/trace/overview', null, true);
await check('traceSnPage', 'GET', '/trace/sn/page?page=1&size=5', null, true);
await check('traceBackwardByTool', 'GET', '/trace/backward?toolCode=T-EM12-01', null, true);
await check('traceBackwardByMachine', 'GET', '/trace/backward?machineCode=MC-001', null, true);
await check('statsOee', 'GET', '/stats/oee?days=7', null, true);
await check('statsOutput', 'GET', '/stats/output?days=14', null, true);
await check('statsLabor', 'GET', '/stats/labor?days=7', null, true);
await check('statsSummary', 'GET', '/stats/summary?days=7', null, true);

console.log('\n===== 6. 主数据与系统 =====');
await check('mdMachine', 'GET', '/md/machine/page?page=1&size=5', null, true);
await check('mdMaterial', 'GET', '/md/material/list', null, true);
await check('mdRoutingSteps', 'GET', '/md/routing/steps/1', null, true);
await check('mdBomItems', 'GET', '/md/bom/items/1', null, true);
await check('dictAll', 'GET', '/system/dict/all', null, true);
await check('users', 'GET', '/system/user/list', null, true);
await check('loginLogs', 'GET', '/system/log/login', null, true);
await check('operLogs', 'GET', '/system/log/oper', null, true);

console.log('\n===== 7. 写操作 =====');
// 空请求体必须被拒绝（否则任何主数据表都可能插出只有 id 的垃圾行）
const emptySave = await call('POST', '/system/dict/save', {});
const emptyMd = await call('POST', '/md/customer/save', {});
const emptySaveOk = emptySave.json.code !== 200;
const emptyMdOk = emptyMd.json.code !== 200;
results.push(`${emptySaveOk ? 'PASS' : 'FAIL'} 空 body 新增字典被拒绝 → code=${emptySave.json.code} ${emptySave.json.msg}`);
results.push(`${emptyMdOk ? 'PASS' : 'FAIL'} 空 body 新增主数据被拒绝 → code=${emptyMd.json.code} ${emptyMd.json.msg}`);
console.log(`  ${emptySaveOk ? '✓' : '✗'} 空 body 新增字典 → code=${emptySave.json.code}：${emptySave.json.msg}`);
console.log(`  ${emptyMdOk ? '✓' : '✗'} 空 body 新增客户 → code=${emptyMd.json.code}：${emptyMd.json.msg}`);

const toolRow = { toolCode: 'T-TEST-001', toolName: '冒烟测试立铣刀', toolType: '立铣刀', spec: 'φ10×72×4F',
  stdLifeMin: 3000, usedLifeMin: 0, stdLifeQty: 400, usedQty: 0, status: '在库', unitPrice: 120, location: 'A区09-09' };
await check('toolSave', 'POST', '/tool/save', toolRow, false);
const t = await call('GET', '/tool/page?page=1&size=50&toolCode=T-TEST-001');
const toolId = t.json?.data?.records?.[0]?.id;
console.log('新建刀具 id =', toolId);
if (toolId) {
  await check('toolMount', 'POST', '/tool/mount', { toolId, machineId: 1, offsetNo: 'H09' }, false);
  await check('toolUnmount', 'POST', '/tool/unmount', { toolId, useMinutes: 120, partQty: 30, unmountReason: '计划换刀' }, false);
  await check('toolGrind', 'POST', '/tool/grind', { toolId, grindAmount: 0.05, cost: 30 }, false);
  await check('toolDelete', 'DELETE', `/tool/delete/${toolId}`, null, false);
}
const firstStep = await call('GET', '/order/detail/2');
const stepId = firstStep.json?.data?.steps?.[0]?.id;
console.log('订单 2 第一个工序任务 id =', stepId);
if (stepId) {
  await check('firstArticle', 'POST', `/qc/firstArticle/${stepId}`, null, false);
  await check('assign', 'POST', '/schedule/assign', { stepId, machineId: 1 }, false);
}
await check('delAndon', 'GET', '/andon/page?page=1&size=3', null, true);

console.log('\n===== 汇总 =====');
const pass = results.filter((r) => r.startsWith('PASS')).length;
console.log(`${pass}/${results.length} 通过`);
results.filter((r) => r.startsWith('FAIL')).forEach((r) => console.log(r));
