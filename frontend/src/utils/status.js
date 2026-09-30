/**
 * 状态字典与配色。
 *
 * 说明：后端返回的是英文枚举（RUN / IN_PROGRESS …），
 * 前端统一在展示层做「中文名 + 色调」映射，tone 直接对应 ind-tag--{tone} / ind-led--{tone} 样式类。
 */

export const MACHINE_STATUS = {
  RUN: { label: '运行', tone: 'run' },
  IDLE: { label: '待机', tone: 'idle' },
  STOP: { label: '停机', tone: 'stop' },
  ALARM: { label: '报警', tone: 'alarm' },
  OFFLINE: { label: '离线', tone: 'offline' },
  MAINT: { label: '保养中', tone: 'maint' },
};

export const ORDER_STATUS = {
  RELEASED: { label: '已下达', tone: 'primary' },
  SCHEDULED: { label: '已排产', tone: 'info' },
  IN_PROGRESS: { label: '生产中', tone: 'run' },
  FINISHED: { label: '已完工', tone: 'plain' },
  HOLD: { label: '已挂起', tone: 'idle' },
  CANCELED: { label: '已取消', tone: 'stop' },
};

export const STEP_STATUS = {
  PENDING: { label: '待派工', tone: 'plain' },
  DISPATCHED: { label: '已派工', tone: 'info' },
  RUNNING: { label: '加工中', tone: 'run' },
  PAUSED: { label: '已暂停', tone: 'idle' },
  FINISHED: { label: '已完工', tone: 'primary' },
  INSPECTING: { label: '检验中', tone: 'maint' },
};

export const DISPATCH_STATUS = {
  DISPATCHED: { label: '待接单', tone: 'info' },
  RECEIVED: { label: '已接单', tone: 'primary' },
  RUNNING: { label: '加工中', tone: 'run' },
  FINISHED: { label: '已完工', tone: 'plain' },
};

export const INSP_TYPE = {
  FIRST: { label: '首件检验', tone: 'alarm' },
  PATROL: { label: '巡检', tone: 'info' },
  FINAL: { label: '终检', tone: 'primary' },
  WAREHOUSE: { label: '入库检', tone: 'plain' },
};

export const QC_RESULT = {
  PASS: { label: '合格', tone: 'run' },
  FAIL: { label: '不合格', tone: 'stop' },
  PENDING: { label: '待判定', tone: 'idle' },
};

export const PRIORITY = {
  加急: { label: '加急', tone: 'stop' },
  高: { label: '高', tone: 'idle' },
  中: { label: '中', tone: 'info' },
  低: { label: '低', tone: 'plain' },
};

export const TOOL_STATUS = {
  在用: { label: '在用', tone: 'run' },
  在库: { label: '在库', tone: 'primary' },
  预警: { label: '寿命预警', tone: 'idle' },
  待刃磨: { label: '待刃磨', tone: 'stop' },
  已报废: { label: '已报废', tone: 'plain' },
};

export const ANDON_STATUS = {
  待响应: { label: '待响应', tone: 'stop' },
  处理中: { label: '处理中', tone: 'idle' },
  已关闭: { label: '已关闭', tone: 'plain' },
};

export const ANDON_TYPE_TONE = {
  质量异常: 'stop',
  设备异常: 'idle',
  物料短缺: 'plain',
  工艺求助: 'info',
  刀具需求: 'maint',
};

export const HANDLE_TYPE = {
  返工: { label: '返工', tone: 'idle' },
  返修: { label: '返修', tone: 'info' },
  让步接收: { label: '让步接收', tone: 'primary' },
  报废: { label: '报废', tone: 'stop' },
};

export const STOP_REASON = {
  TOOL_CHANGE: '换刀',
  CHANGEOVER: '换型调机',
  FAULT: '设备故障',
  WAIT_MATERIAL: '等待毛坯',
  WAIT_PROGRAM: '等待程序',
  WAIT_INSPECT: '首件检验',
  PLAN_MAINT: '计划保养',
  IDLE: '待料停工',
};

export const FAULT_TYPE_TONE = {
  机械: 'idle',
  电气: 'stop',
  数控系统: 'alarm',
  液压: 'info',
  气动: 'primary',
  刀具干涉: 'maint',
};

const MAPS = {
  machine: MACHINE_STATUS,
  order: ORDER_STATUS,
  step: STEP_STATUS,
  dispatch: DISPATCH_STATUS,
  inspType: INSP_TYPE,
  qcResult: QC_RESULT,
  priority: PRIORITY,
  tool: TOOL_STATUS,
  andon: ANDON_STATUS,
  handle: HANDLE_TYPE,
};

/** 取中文名；未命中则原样返回。 */
export function label(kind, value) {
  if (value === null || value === undefined || value === '') return '—';
  const m = MAPS[kind];
  return m && m[value] ? m[value].label : String(value);
}

/** 取色调；未命中返回 'plain'。 */
export function tone(kind, value) {
  const m = MAPS[kind];
  return (m && m[value] && m[value].tone) || 'plain';
}

/** 机台状态 → 中文（看板专用快捷方法） */
export const machineLabel = (s) => label('machine', s);
export const machineTone = (s) => tone('machine', s);
