/** 按业务域划分的接口封装。 */
import { http } from './http';

const page = (base) => ({
  page: (params) => http.get(`${base}/page`, params),
  list: (params) => http.get(`${base}/list`, params),
  get: (id) => http.get(`${base}/${id}`),
  save: (row) => http.post(`${base}/save`, row),
  update: (row) => http.put(`${base}/update`, row),
  del: (id) => http.del(`${base}/delete/${id}`),
});

export const api = {
  auth: {
    login: (username, password) => http.post('/auth/login', { username, password }),
    logout: () => http.post('/auth/logout', {}),
    profile: () => http.get('/auth/profile'),
    changePassword: (oldPassword, newPassword) => http.post('/auth/password', { oldPassword, newPassword }),
  },

  board: {
    overview: () => http.get('/board/overview'),
    matrix: () => http.get('/board/matrix'),
    trend: (days = 14) => http.get('/board/trend', { days }),
    ranking: (days = 7) => http.get('/board/ranking', { days }),
    stream: () => http.sse('/board/stream'),
  },

  machine: {
    live: (params) => http.get('/machine/live', params),
    detail: (id) => http.get(`/machine/${id}/detail`),
    telemetry: (id, hours = 4) => http.get(`/machine/${id}/telemetry`, { hours }),
    statusLogs: (id, days = 3) => http.get(`/machine/${id}/statusLogs`, { days }),
    statusDistribution: (id, days = 7) => http.get(`/machine/${id}/statusDistribution`, { days }),
    oee: (id, days = 7) => http.get(`/machine/${id}/oee`, { days }),
  },

  order: {
    ...page('/order'),
    withProgress: (params) => http.get('/order/withProgress', params),
    detail: (id) => http.get(`/order/detail/${id}`),
    release: (id) => http.post(`/order/release/${id}`, {}),
    expand: (id) => http.post(`/order/expand/${id}`, {}),
    kitCheck: (id) => http.get(`/order/kitCheck/${id}`),
    status: (id, status) => http.post(`/order/status/${id}?status=${encodeURIComponent(status)}`, {}),
  },

  schedule: {
    gantt: (params) => http.get('/schedule/gantt', params),
    machineLoad: (params) => http.get('/schedule/machineLoad', params),
    pending: () => http.get('/schedule/pending'),
    resources: () => http.get('/schedule/resources'),
    auto: (body) => http.post('/schedule/auto', body || {}),
    assign: (body) => http.post('/schedule/assign', body),
  },

  dispatch: {
    ...page('/dispatch'),
    create: (body) => http.post('/dispatch/create', body),
    batch: (body) => http.post('/dispatch/batch', body),
    receive: (id) => http.post(`/dispatch/receive/${id}`, {}),
    ticket: (id) => http.get(`/dispatch/ticket/${id}`),
    byMachine: (machineId) => http.get(`/dispatch/byMachine/${machineId}`),
  },

  terminal: {
    tasks: (machineId, employeeId) => http.get('/terminal/tasks', { machineId, employeeId }),
    start: (body) => http.post('/terminal/start', body),
    report: (body) => http.post('/terminal/report', body),
    finish: (body) => http.post('/terminal/finish', body),
    stop: (body) => http.post('/terminal/stop', body),
    andon: (body) => http.post('/terminal/andon', body),
    machineState: (body) => http.post('/terminal/machineState', body),
    hourly: (machineId) => http.get(`/terminal/hourly/${machineId}`),
  },

  stop: {
    ...page('/stop'),
    statistics: (days = 7) => http.get('/stop/statistics', { days }),
    ongoing: () => http.get('/stop/ongoing'),
    close: (id, body) => http.post(`/stop/close/${id}`, body || {}),
  },

  andon: {
    ...page('/andon'),
    open: () => http.get('/andon/open'),
    respond: (id) => http.post(`/andon/respond/${id}`, {}),
    resolve: (id, body) => http.post(`/andon/resolve/${id}`, body || {}),
    escalate: (id) => http.post(`/andon/escalate/${id}`, {}),
    statistics: (days = 7) => http.get('/andon/statistics', { days }),
  },

  qc: {
    ...page('/qc'),
    items: (id) => http.get(`/qc/items/${id}`),
    saveItem: (row) => http.post('/qc/item/save', row),
    deleteItem: (id) => http.del(`/qc/item/delete/${id}`),
    firstArticle: (stepId) => http.post(`/qc/firstArticle/${stepId}`, {}),
    judge: (id, body) => http.post(`/qc/judge/${id}`, body || {}),
    statistics: (days = 14) => http.get('/qc/statistics', { days }),
  },

  defect: {
    ...page('/defect'),
    handle: (id, body) => http.post(`/defect/handle/${id}`, body),
    statistics: (days = 14) => http.get('/defect/statistics', { days }),
  },

  spc: {
    groups: () => http.get('/spc/groups'),
    series: (params) => http.get('/spc/series', params),
    analyze: (body) => http.post('/spc/analyze', body),
    save: (row) => http.post('/spc/save', row),
  },

  tool: {
    ...page('/tool'),
    overview: () => http.get('/tool/overview'),
    mount: (body) => http.post('/tool/mount', body),
    unmount: (body) => http.post('/tool/unmount', body),
    grind: (body) => http.post('/tool/grind', body),
    measure: (body) => http.post('/tool/measure', body),
    usage: (id) => http.get(`/tool/usage/${id}`),
    grindRecords: (id) => http.get(`/tool/grindRecords/${id}`),
    measureRecords: (id) => http.get(`/tool/measureRecords/${id}`),
    trace: (code) => http.get(`/tool/trace/${code}`),
  },

  equipment: {
    overview: (days = 30) => http.get('/equipment/overview', { days }),
    checkItems: () => http.get('/equipment/checkItem/list'),
    saveCheckItem: (row) => http.post('/equipment/checkItem/save', row),
    deleteCheckItem: (id) => http.del(`/equipment/checkItem/delete/${id}`),
    checkRecords: (params) => http.get('/equipment/checkRecord/page', params),
    saveCheckRecord: (row) => http.post('/equipment/checkRecord/save', row),
    checkSheet: (machineId) => http.get(`/equipment/checkSheet/${machineId}`),
    maintPlans: (params) => http.get('/equipment/maintPlan/page', params),
    saveMaintPlan: (row) => http.post('/equipment/maintPlan/save', row),
    generateMaint: (id) => http.post(`/equipment/maintPlan/generate/${id}`, {}),
    maintOrders: (params) => http.get('/equipment/maintOrder/page', params),
    finishMaint: (id, body) => http.post(`/equipment/maintOrder/finish/${id}`, body || {}),
    faults: (params) => http.get('/equipment/fault/page', params),
    saveFault: (row) => http.post('/equipment/fault/save', row),
    respondFault: (id) => http.post(`/equipment/fault/respond/${id}`, {}),
    closeFault: (id, body) => http.post(`/equipment/fault/close/${id}`, body),
  },

  trace: {
    overview: () => http.get('/trace/overview'),
    snPage: (params) => http.get('/trace/sn/page', params),
    forward: (sn) => http.get(`/trace/forward/${encodeURIComponent(sn)}`),
    backward: (params) => http.get('/trace/backward', params),
    batches: () => http.get('/trace/batch/list'),
  },

  stats: {
    oee: (days = 7) => http.get('/stats/oee', { days }),
    output: (days = 14) => http.get('/stats/output', { days }),
    labor: (days = 7) => http.get('/stats/labor', { days }),
    summary: (days = 7) => http.get('/stats/summary', { days }),
  },

  system: {
    users: (params) => http.get('/system/user/list', params),
    saveUser: (row) => http.post('/system/user/save', row),
    deleteUser: (id) => http.del(`/system/user/delete/${id}`),
    resetPassword: (id) => http.post(`/system/user/resetPassword/${id}`, {}),
    roles: () => http.get('/system/role/list'),
    saveRole: (row) => http.post('/system/role/save', row),
    dictAll: () => http.get('/system/dict/all'),
    dictList: (dictType) => http.get('/system/dict/list', { dictType }),
    saveDict: (row) => http.post('/system/dict/save', row),
    deleteDict: (id) => http.del(`/system/dict/delete/${id}`),
    loginLogs: (size = 100) => http.get('/system/log/login', { size }),
    operLogs: (size = 100) => http.get('/system/log/oper', { size }),
  },

  masterdata: {
    workshop: page('/md/workshop'),
    workCenter: page('/md/workCenter'),
    machine: page('/md/machine'),
    material: page('/md/material'),
    bom: page('/md/bom'),
    bomItems: (bomId) => http.get(`/md/bom/items/${bomId}`),
    saveBomItem: (row) => http.post('/md/bom/item/save', row),
    deleteBomItem: (id) => http.del(`/md/bom/item/delete/${id}`),
    process: page('/md/process'),
    routing: page('/md/routing'),
    routingSteps: (id) => http.get(`/md/routing/steps/${id}`),
    saveRoutingStep: (row) => http.post('/md/routing/step/save', row),
    deleteRoutingStep: (id) => http.del(`/md/routing/step/delete/${id}`),
    customer: page('/md/customer'),
    employee: page('/md/employee'),
    shift: page('/md/shift'),
  },
};
