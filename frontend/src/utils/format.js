/** 展示层格式化工具。 */

export function num(v, digits = 0) {
  if (v === null || v === undefined || v === '') return '—';
  const n = Number(v);
  if (Number.isNaN(n)) return String(v);
  return n.toLocaleString('zh-CN', { minimumFractionDigits: digits, maximumFractionDigits: digits });
}

export function pct(v, digits = 1) {
  if (v === null || v === undefined || v === '') return '—';
  return `${Number(v).toFixed(digits)}%`;
}

export function decimal(v, digits = 2) {
  if (v === null || v === undefined || v === '') return '—';
  return Number(v).toFixed(digits);
}

/** 时间：2026-09-21 19:46 → 也可以只要 HH:mm */
export function dateTime(v, withSeconds = false) {
  if (!v) return '—';
  const s = String(v).replace('T', ' ');
  return withSeconds ? s.slice(0, 19) : s.slice(0, 16);
}

export function timeOnly(v) {
  if (!v) return '—';
  const s = String(v).replace('T', ' ');
  return s.slice(11, 16);
}

export function dateOnly(v) {
  if (!v) return '—';
  return String(v).slice(0, 10);
}

/** 相对时间：「3 分钟前」「2 小时前」 */
export function fromNow(v) {
  if (!v) return '—';
  const t = new Date(String(v).replace(' ', 'T')).getTime();
  if (Number.isNaN(t)) return '—';
  const diff = Math.floor((Date.now() - t) / 1000);
  if (diff < 60) return `${Math.max(diff, 0)} 秒前`;
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`;
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`;
  return `${Math.floor(diff / 86400)} 天前`;
}

/** 分钟 → 「2h35m」工业写法 */
export function minutes(v) {
  if (v === null || v === undefined || v === '') return '—';
  const m = Math.round(Number(v));
  if (m < 60) return `${m}m`;
  return `${Math.floor(m / 60)}h${String(m % 60).padStart(2, '0')}m`;
}

export function hours(v) {
  if (v === null || v === undefined || v === '') return '—';
  return `${(Math.round(Number(v) / 6) / 10).toFixed(1)}h`;
}

/** 进度条色调：按完成率 */
export function progressTone(rate, warnAt = 60, okAt = 90) {
  if (rate >= okAt) return 'run';
  if (rate >= warnAt) return 'idle';
  return 'stop';
}

export function clamp(v, min = 0, max = 100) {
  return Math.min(max, Math.max(min, v));
}

export function truncate(s, n = 18) {
  if (!s) return '—';
  return String(s).length > n ? `${String(s).slice(0, n)}…` : String(s);
}
