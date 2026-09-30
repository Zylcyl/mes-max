/**
 * 第四轮修复（安全与数据一致性加固）的逐条验证脚本。
 *
 * 前置：后端已启动（http://localhost:8080/api），且是「空库首次启动」的演示数据。
 * 用法：node tools/verify-hardening.mjs
 *
 * 说明：本脚本会真实地走一遍「派工 → 开工 → 报工」，因此会在演示库里留下
 * 一张派工单与两条报工记录（与 smoke-api.mjs 的写操作用例性质相同）。
 * 所有探针账号与探针样本都会自清理。
 */
const B = 'http://localhost:8080/api';
const results = [];

function check(name, ok, detail) {
  results.push({ name, ok });
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
    /* 非 JSON 响应 */
  }
  return raw ? { http: res.status, json, text } : { http: res.status, json };
}

async function login(username, password = 'mes123456') {
  const r = await call('POST', '/auth/login', { body: { username, password } });
  return r.json?.data?.token || null;
}

const admin = await login('admin');
const operator = await login('operator');

/** 第 4 节报工过的工序任务 id，留给第 6 节核对报工明细。 */
let probedStepId = null;

// =============================================================== 1. 敏感读接口需要权限
{
  const asOperator = await call('GET', '/system/user/list', { token: operator });
  check('只读角色读「账号列表」返回 403', asOperator.json?.code === 403,
    `code=${asOperator.json?.code} http=${asOperator.http}`);

  const logs = await call('GET', '/system/log/oper?size=5', { token: operator });
  check('只读角色读「操作日志」返回 403', logs.json?.code === 403,
    `code=${logs.json?.code} http=${logs.http}`);

  const asAdmin = await call('GET', '/system/user/list', { token: admin });
  check('系统管理员读「账号列表」仍正常', asAdmin.json?.code === 200 && Array.isArray(asAdmin.json.data),
    `code=${asAdmin.json?.code} users=${asAdmin.json?.data?.length}`);

  // 现场信息（机台/看板）保持对所有登录账号开放，别把「收紧」做过头
  const board = await call('GET', '/board/overview', { token: operator });
  check('车间看板读接口仍对所有登录账号开放', board.json?.code === 200, `code=${board.json?.code}`);

  // 字典是全角色启动时都要拉的（只包含下拉框枚举），不能一起收紧
  const dict = await call('GET', '/system/dict/all', { token: operator });
  check('字典读取仍对所有登录账号开放（前端启动依赖）', dict.json?.code === 200, `code=${dict.json?.code}`);
}

// =============================================================== 2. 查询串令牌只对 SSE 开放
{
  const viaQuery = await call('GET', `/board/overview?token=${encodeURIComponent(operator)}`, { raw: true });
  check('非 SSE 接口用查询串带令牌被拒绝', viaQuery.http === 401,
    `http=${viaQuery.http} code=${viaQuery.json?.code}`);

  const viaHeader = await call('GET', '/board/overview', { token: operator });
  check('同样的令牌走请求头正常', viaHeader.http === 200, `http=${viaHeader.http}`);
}

// =============================================================== 3. 状态机：已完工不可再开工/报工
{
  const page = await call('GET', '/dispatch/page?page=1&size=50&status=FINISHED', { token: admin });
  const done = (page.json?.data?.records || []).find((d) => d.status === 'FINISHED');
  if (!done) {
    check('已完工派工单不能重新开工', false, '演示数据里找不到 FINISHED 派工单');
  } else {
    const start = await call('POST', '/terminal/start', { token: admin, body: { dispatchId: done.id } });
    check('已完工派工单不能重新开工（409）',
      start.json?.code === 409 && start.http === 409,
      `${done.dispatchNo} → code=${start.json?.code} http=${start.http} ${start.json?.msg || ''}`);

    const report = await call('POST', '/terminal/report', {
      token: admin,
      body: { dispatchId: done.id, qty: 2, runMinutes: 10 },
    });
    check('已完工派工单不能再次报工（409）',
      report.json?.code === 409 && report.http === 409,
      `code=${report.json?.code} http=${report.http} ${report.json?.msg || ''}`);
  }
}

// =============================================================== 4. 追溯：按件写、不重复、带毛坯批次
{
  const pending = (await call('GET', '/schedule/pending', { token: admin })).json?.data || [];
  let picked = null;
  for (const t of pending.slice(0, 40)) {
    if (!t.orderNo || !t.stepId) continue;
    const sns = (await call('GET', `/trace/sn/page?page=1&size=50&orderNo=${encodeURIComponent(t.orderNo)}`, { token: admin }))
      .json?.data?.records || [];
    if (sns.length >= 3) {
      picked = { task: t, sns };
      break;
    }
  }

  if (!picked) {
    check('报工按件写序列号追溯记录', false, '找不到「有≥3个序列号且仍有待派工序」的工单');
  } else {
    const { task, sns } = picked;
    const stepId = task.stepId;
    probedStepId = stepId;

    const countStepRows = async (sn) => {
      const f = await call('GET', `/trace/forward/${encodeURIComponent(sn.snCode)}`, { token: admin });
      const timeline = f.json?.data?.timeline || [];
      return timeline.filter((x) => x.log?.stepId === stepId);
    };

    let dispatch = (await call('GET', `/dispatch/page?page=1&size=50&stepId=${stepId}`, { token: admin }))
      .json?.data?.records?.[0];
    if (!dispatch) {
      dispatch = (await call('POST', '/dispatch/create', {
        token: admin,
        body: { stepId, planQty: Number(task.planQty) },
      })).json?.data;
    }
    const started = await call('POST', '/terminal/start', { token: admin, body: { dispatchId: dispatch.id } });
    const first = await call('POST', '/terminal/report', {
      token: admin,
      body: { dispatchId: dispatch.id, qty: 2, defectQty: 0, runMinutes: 20 },
    });
    check('报工成功（工单 ' + task.orderNo + '）', started.json?.code === 200 && first.json?.code === 200,
      `开工 code=${started.json?.code}，报工 code=${first.json?.code}`);

    const afterFirst = [];
    for (const sn of sns) afterFirst.push({ sn, rows: await countStepRows(sn) });
    const covered = afterFirst.filter((x) => x.rows.length > 0);
    const duplicated = afterFirst.filter((x) => x.rows.length > 1);

    check('一次报 2 件 → 恰好 2 个序列号各写 1 条记录（修复前是固定写前 5 个）',
      covered.length === 2 && duplicated.length === 0,
      `覆盖 ${covered.length} 个序列号，其中重复 ${duplicated.length} 个`);
    check('每条追溯记录的数量是 1 件（修复前给每个序列号记整批数量）',
      covered.length > 0 && covered.every((x) => Number(x.rows[0].log.qty) === 1),
      covered.length ? `qty=${covered[0].rows[0].log.qty}` : '无记录');
    check('追溯记录写入了毛坯批次（反向追溯的前提）',
      covered.length > 0 && covered.every((x) => x.rows[0].log.materialBatch === x.sn.batchNo),
      covered.length ? `批次=${covered[0].rows[0].log.materialBatch}（序列号批次=${covered[0].sn.batchNo}）` : '无记录');

    if (covered.length > 0) {
      const batchNo = covered[0].sn.batchNo;
      const back = await call('GET', `/trace/backward?batchNo=${encodeURIComponent(batchNo)}`, { token: admin });
      const snCodes = back.json?.data?.snCodes || [];
      check('按毛坯批次反查能命中刚报工的序列号', snCodes.includes(covered[0].sn.snCode),
        `批次 ${batchNo} → 命中 ${snCodes.length} 个序列号`);
    }

    // 第二次报工：要么被状态机挡住（工序已报满自动完工），要么继续按件补写且不重复
    const state = await call('GET', `/dispatch/${dispatch.id}`, { token: admin });
    const stillOpen = state.json?.data && state.json.data.status !== 'FINISHED';
    if (stillOpen) {
      const second = await call('POST', '/terminal/report', {
        token: admin,
        body: { dispatchId: dispatch.id, qty: 1, defectQty: 0, runMinutes: 10 },
      });
      const afterSecond = [];
      for (const sn of sns) afterSecond.push({ sn, rows: await countStepRows(sn) });
      const covered2 = afterSecond.filter((x) => x.rows.length > 0);
      const duplicated2 = afterSecond.filter((x) => x.rows.length > 1);
      check('第二次报工不重复写同一序列号（修复前每次报工都重写）',
        second.json?.code === 200 && duplicated2.length === 0 && covered2.length >= covered.length,
        `code=${second.json?.code}，覆盖 ${covered2.length} 个序列号，重复 ${duplicated2.length} 个`);
    } else {
      const second = await call('POST', '/terminal/report', {
        token: admin,
        body: { dispatchId: dispatch.id, qty: 1, runMinutes: 10 },
      });
      check('报满计划量的工序自动关闭派工单，再次报工被状态机拒绝',
        second.json?.code === 409,
        `派工单已 FINISHED → code=${second.json?.code} ${second.json?.msg || ''}`);
    }
  }
}

// =============================================================== 5. SPC 不编造过程能力指数
{
  const charCode = 'AUDIT_ND_' + Date.now().toString(36);
  const ids = [];
  const values = [45.0, 45.01, 44.99, 45.02, 45.0, 44.98];
  for (let i = 0; i < values.length; i++) {
    const r = await call('POST', '/spc/save', {
      token: admin,
      body: {
        charName: '加固验证用（无公差）', charCode, machineId: 5, machineCode: 'MC-005',
        sampleValue: values[i], sampleNo: i + 1,
      },
    });
    if (r.json?.data?.id) ids.push(r.json.data.id);
  }
  const noSpec = await call('POST', '/spc/analyze', { token: admin, body: { machineId: 5, charCode } });
  const a = noSpec.json?.data || {};
  check('未录公差时不给假的 Cp/CpK（返回 null + specSource=missing）',
    a.cpk === null && a.cp === null && a.usl === null && a.specSource === 'missing',
    `cp=${a.cp} cpk=${a.cpk} usl=${a.usl} specSource=${a.specSource}`);
  check('未录公差时超差点数不计、也不给「能力不足」的结论',
    a.outOfSpec === 0 && /未录入公差/.test(a.grade || ''),
    `outOfSpec=${a.outOfSpec} grade=${a.grade}`);

  // 对照组：补上上下规格限后必须能算出 Cp/CpK
  const ids2 = [];
  const charCode2 = 'AUDIT_SPEC_' + Date.now().toString(36);
  for (let i = 0; i < values.length; i++) {
    const r = await call('POST', '/spc/save', {
      token: admin,
      body: {
        charName: '加固验证用（有公差）', charCode: charCode2, machineId: 5, machineCode: 'MC-005',
        nominalValue: 45, upperLimit: 45.02, lowerLimit: 44.98,
        sampleValue: values[i], sampleNo: i + 1,
      },
    });
    if (r.json?.data?.id) ids2.push(r.json.data.id);
  }
  const withSpec = await call('POST', '/spc/analyze', { token: admin, body: { machineId: 5, charCode: charCode2 } });
  const b = withSpec.json?.data || {};
  check('录了公差后正常给出 Cp/CpK（判定与展示同源，不再出现 CpK=1 判不足）',
    typeof b.cpk === 'number' && b.cpk > 0 && b.cpk < 1.5 && b.usl === 45.02 && b.lsl === 44.98,
    `cp=${b.cp} cpk=${b.cpk} usl=${b.usl} lsl=${b.lsl} grade=${b.grade}`);

  for (const id of [...ids, ...ids2]) {
    await call('DELETE', `/spc/delete/${id}`, { token: admin });
  }
}

// =============================================================== 6. 异常与参数兜底
{
  const badTime = await call('POST', '/terminal/stop', {
    token: admin,
    body: { machineId: 1, startTime: '不是时间', endTime: '2026-09-30 10:00:00' },
  });
  const msg = String(badTime.json?.msg || '');
  check('时间格式写错返回 400 且提示可读',
    badTime.http === 400 && badTime.json?.code === 400 && /时间格式/.test(msg),
    `http=${badTime.http} code=${badTime.json?.code} msg=${msg}`);
  check('错误响应里不泄露内部异常细节（类名/包名/SQL）',
    !/(Exception|java\.|org\.|select |insert )/i.test(msg),
    `msg=${msg}`);

  const missing = await call('GET', '/md/machine/99999999', { token: admin });
  check('查询不存在的记录返回 404', missing.http === 404 && missing.json?.code === 404,
    `http=${missing.http} code=${missing.json?.code}`);

  const unknown = await call('GET', '/no-such-endpoint-here', { token: admin });
  check('未映射的路径返回 404 而不是 500', unknown.http === 404 && unknown.json?.code === 404,
    `http=${unknown.http} code=${unknown.json?.code}`);

  if (probedStepId) {
    const rows = await call('GET', `/report-task/page?size=50&stepId=${probedStepId}`, { token: admin });
    const records = rows.json?.data?.records || [];
    const startRows = records.filter((r) => r.reportType === '开工');
    const badStart = startRows.filter((r) => Number(r.qty) !== 0 || Number(r.runMinutes) !== 0);
    check('报工明细可按工序任务查询（/report-task）',
      rows.json?.code === 200 && records.length > 0,
      `工序 ${probedStepId} 共 ${records.length} 条报工记录`);
    check('开工占位记录的数量与工时应为 0',
      startRows.length > 0 && badStart.length === 0,
      `开工记录 ${startRows.length} 条，异常 ${badStart.length} 条`);
  } else {
    check('报工明细可按工序任务查询（/report-task）', false, '第 4 节没有报工成功，无法核对');
  }

  const zeroDays = await call('GET', '/stats/oee?days=0', { token: admin });
  const oneDay = await call('GET', '/stats/oee?days=1', { token: admin });
  const week = await call('GET', '/stats/oee?days=7', { token: admin });
  check('统计窗口天数有兜底（days=0 不再算出空报表）',
    zeroDays.json?.code === 200 && zeroDays.json.data.days >= 1,
    `days=0 → 实际 days=${zeroDays.json?.data?.days}`);
  check('停机时长随统计窗口单调递增（区间上下界都卡住了）',
    Number(oneDay.json?.data?.overall?.stopMinutes) <= Number(week.json?.data?.overall?.stopMinutes),
    `1 天=${oneDay.json?.data?.overall?.stopMinutes} ≤ 7 天=${week.json?.data?.overall?.stopMinutes}`);
}

// =============================================================== 7. 登录失败节流
{
  const probe = 'throttleprobe';
  const list = await call('GET', '/system/user/list', { token: admin });
  for (const u of (list.json?.data || []).filter((x) => x.username === probe)) {
    await call('DELETE', `/system/user/delete/${u.id}`, { token: admin });
  }
  await call('POST', '/system/user/save', {
    token: admin,
    body: { username: probe, realName: '节流探针', roleCode: 'viewer', status: 1, password: 'probe-123456' },
  });

  let codes = [];
  for (let i = 0; i < 10; i++) {
    const r = await call('POST', '/auth/login', { body: { username: probe, password: 'wrong-' + i } });
    codes.push(r.json?.code);
    if (r.json?.code === 429) break;
  }
  check('同一账号连续错误口令会被临时锁定（429）', codes.includes(429),
    `尝试 ${codes.length} 次 → ${codes.join(',')}`);
  const locked = await call('POST', '/auth/login', { body: { username: probe, password: 'probe-123456' } });
  check('锁定期间即使口令正确也被拒绝', locked.json?.code === 429,
    `code=${locked.json?.code} ${locked.json?.msg || ''}`);
  const otherUser = await call('POST', '/auth/login', { body: { username: 'admin', password: 'mes123456' } });
  check('节流不影响其它账号（按账号维度而非全局）', otherUser.json?.code === 200,
    `admin code=${otherUser.json?.code}`);

  const list2 = await call('GET', '/system/user/list', { token: admin });
  for (const u of (list2.json?.data || []).filter((x) => x.username === probe)) {
    await call('DELETE', `/system/user/delete/${u.id}`, { token: admin });
  }
  const list3 = await call('GET', '/system/user/list', { token: admin });
  check('节流探针账号已清理', (list3.json?.data || []).every((x) => x.username !== probe),
    `剩余账号 ${(list3.json?.data || []).length} 个`);
}

// =============================================================== 8. 报文与响应不含内部信息
{
  const login = await call('POST', '/auth/login', { body: { username: 'admin', password: 'mes123456' } });
  check('登录响应不含口令字段', !JSON.stringify(login.json).toLowerCase().includes('password'),
    'token / user / role / homePage');
  const badBody = await call('POST', '/order/save', { token: admin });
  const badMsg = String(badBody.json?.msg || '');
  check('空请求体返回 400，且提示里不含内部类名/方法签名',
    badBody.json?.code === 400 && !/(com\.mesmax|public |Exception)/.test(badMsg),
    `code=${badBody.json?.code} msg=${badMsg}`);
}

const pass = results.filter((r) => r.ok).length;
console.log(`\n合计 ${pass}/${results.length} 通过`);
process.exit(pass === results.length ? 0 : 1);
