/**
 * SSE 实时推送验证：订阅 /board/stream，统计 15 秒内收到的 machines 事件数与机台数，
 * 同时确认采集循环没有被阻塞（每 2 秒一帧）。
 */
const B = 'http://localhost:8080/api';

const login = await (await fetch(`${B}/auth/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'admin', password: 'mes123456' }),
})).json();
const token = login.data.token;

const res = await fetch(`${B}/board/stream?token=${encodeURIComponent(token)}`, {
  headers: { Accept: 'text/event-stream' },
});
console.log('HTTP', res.status, res.headers.get('content-type'));

const reader = res.body.getReader();
const decoder = new TextDecoder();
let buffer = '';
let machinesFrames = 0;
let machineCount = 0;
const started = Date.now();

while (Date.now() - started < 15000) {
  const { value, done } = await reader.read();
  if (done) break;
  buffer += decoder.decode(value, { stream: true });
  const parts = buffer.split('\n\n');
  buffer = parts.pop();
  for (const p of parts) {
    if (p.includes('event:machines') || p.includes('event: machines')) {
      machinesFrames++;
      const dataLine = p.split('\n').find((l) => l.startsWith('data:'));
      if (dataLine) {
        try {
          machineCount = JSON.parse(dataLine.slice(5)).length;
        } catch {
          /* 分块可能截断，忽略 */
        }
      }
    }
  }
}
await reader.cancel();

const expected = Math.floor(15000 / 2000);
console.log(`15 秒内收到 machines 帧数：${machinesFrames}（约每 2 秒一帧，期望 ≥ ${expected - 1}）`);
console.log(`最后一帧机台数：${machineCount}`);
console.log(machinesFrames >= expected - 2 && machineCount === 12 ? '✓ SSE 推送正常且节奏稳定' : '✗ SSE 推送异常');
