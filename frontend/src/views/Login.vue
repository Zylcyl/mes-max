<script setup>
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import IndButton from '@/components/ind/IndButton.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import IndLed from '@/components/ind/IndLed.vue';
import { useAuth } from '@/stores/auth';
import { useToast } from '@/stores/toast';
import { api } from '@/api';

const auth = useAuth();
const toast = useToast();
const router = useRouter();
const route = useRoute();

/*
 * 刻意不在输入框里预填账号口令：登录页经常被投到大屏或截图进答辩 PPT，
 * 预填等于把演示口令印在屏幕上。演示账号用下面的一键填充按钮即可（口令见 README）。
 */
const username = ref('');
const password = ref('');
const loading = ref(false);
const error = ref('');
const clock = ref(new Date());
const online = ref(null);

const DEMO_ACCOUNTS = [
  { u: 'admin', label: '系统管理员', desc: '全部功能' },
  { u: 'planner', label: '生产计划员', desc: '订单 / 排产 / 派工' },
  { u: 'operator', label: '机床操作工', desc: '车间报工终端' },
  { u: 'quality', label: '质量检验员', desc: '检验 / 追溯' },
  { u: 'keeper', label: '刀具库管员', desc: '刀具台账 / 刃磨' },
  { u: 'device', label: '设备工程师', desc: '点检 / 保养 / 故障' },
  { u: 'board', label: '车间看板', desc: '只读大屏' },
];

onMounted(() => {
  setInterval(() => (clock.value = new Date()), 1000);
  /*
   * 探测后端是否在线：登录页就能看出「服务没起来」还是「密码错」。
   * 用受保护的 GET 接口而不是「拿假账号试登录」——后者每次打开登录页都会在登录日志里
   * 留一条失败记录，还会被登录节流计数。
   */
  fetch('/api/board/health')
    .then((r) => {
      online.value = r.status === 401 || r.ok;
    })
    .catch(() => {
      online.value = false;
    });
});

function fill(u) {
  username.value = u;
  password.value = 'mes123456';
}

async function submit() {
  error.value = '';
  if (!username.value || !password.value) {
    error.value = '请输入账号与密码';
    return;
  }
  loading.value = true;
  try {
    const data = await auth.login(username.value.trim(), password.value);
    toast.ok(`欢迎回来，${data.user.realName || data.user.username}`);
    router.push(route.query.redirect || data.homePage || '/board');
  } catch (e) {
    error.value = e.message || '登录失败';
  } finally {
    loading.value = false;
  }
}

const hhmmss = () =>
  [clock.value.getHours(), clock.value.getMinutes(), clock.value.getSeconds()]
    .map((v) => String(v).padStart(2, '0'))
    .join(':');
</script>

<template>
  <div class="login">
    <!-- 左半屏：品牌与技术规格 -->
    <section class="login__hero">
      <div class="hero__grid" />
      <div class="hero__machine">
        <div class="machine__head">CNC</div>
        <div class="machine__body">
          <div class="machine__door">
            <div class="machine__spindle" />
            <div class="machine__table" />
            <div class="machine__part" />
          </div>
          <div class="machine__panel">
            <span class="mono">X 320.118</span>
            <span class="mono">Y -184.502</span>
            <span class="mono">Z -76.330</span>
            <span class="mono c-run">S 6200 rpm</span>
            <span class="mono c-idle">F 1180 mm/min</span>
          </div>
        </div>
      </div>

      <div class="hero__text">
        <div class="hero__logo">
          <span class="hero__logo-mark">MES</span>
          <div>
            <b>MES-MAX</b>
            <span>Manufacturing Execution System</span>
          </div>
        </div>
        <h1>面向数控加工行业的<br />制造执行系统</h1>
        <p class="dim">
          工单派工 · 有限产能排产 · 设备联网采集 · 刀具寿命管控 · 首件与 SPC · 全程谱系追溯
        </p>
        <div class="hero__specs">
          <div class="spec"><span class="mute">设备接入</span><b class="mono">FOCAS / OPC UA / MTConnect / MQTT</b></div>
          <div class="spec"><span class="mute">数据刷新</span><b class="mono">2 秒级实时推送（SSE）</b></div>
          <div class="spec"><span class="mute">车间对象</span><b class="mono">机床 · 刀具 · 程序 · 毛坯批次 · 序列号</b></div>
        </div>
      </div>

      <div class="hero__foot mono">
        <span>SHOP FLOOR CONTROL · CNC MACHINING</span>
        <span>{{ hhmmss() }}</span>
      </div>
    </section>

    <!-- 右半屏：登录表单 -->
    <section class="login__panel">
      <div class="panel-box">
        <div class="panel-box__head">
          <span class="ind-panel__accent" />
          <b>系统登录</b>
          <div class="panel-box__status">
            <IndLed :tone="online === null ? 'idle' : online ? 'run' : 'stop'" />
            <span class="mono mute">{{ online === null ? '检测服务中' : online ? '服务在线' : '服务未启动' }}</span>
          </div>
        </div>

        <form class="panel-box__body" @submit.prevent="submit">
          <IndField label="操作账号" required>
            <IndInput v-model="username" placeholder="请输入账号" />
          </IndField>
          <IndField label="登录密码" required>
            <IndInput v-model="password" type="password" placeholder="请输入密码" @enter="submit" />
          </IndField>

          <div v-if="error" class="panel-box__error mono">
            <IndLed tone="stop" />{{ error }}
          </div>

          <IndButton variant="primary" size="lg" block :disabled="loading" @click="submit">
            {{ loading ? '正在验证…' : '登 录 系 统' }}
          </IndButton>
        </form>

        <div class="panel-box__accounts">
          <div class="ind-section-title">演示账号（密码统一 mes123456）</div>
          <button v-for="a in DEMO_ACCOUNTS" :key="a.u" class="account" @click="fill(a.u)">
            <span class="account__role">{{ a.label }}</span>
            <span class="account__user mono">{{ a.u }}</span>
            <span class="account__desc mute">{{ a.desc }}</span>
          </button>
        </div>
      </div>
      <p class="login__copy mute">
        MES-MAX v1.0 · 本系统为数控机加车间专用 MES，界面为车间 HMI 风格设计
      </p>
    </section>
  </div>
</template>

<style scoped>
.login {
  height: 100%;
  display: grid;
  grid-template-columns: 1.25fr 1fr;
  position: relative;
  z-index: 1;
  background: var(--login-bg);
}

/* ---------------- 左屏 ---------------- */
.login__hero {
  position: relative;
  overflow: hidden;
  padding: 46px 54px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  border-right: 1px solid var(--line);
}
.hero__grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(to right, rgba(10, 162, 255, 0.07) 1px, transparent 1px),
    linear-gradient(to bottom, rgba(10, 162, 255, 0.07) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(circle at 30% 40%, #000 30%, transparent 78%);
}

/* 用纯 CSS 画一台加工中心的正面示意 */
.hero__machine {
  position: absolute;
  right: -30px;
  top: 50%;
  transform: translateY(-58%);
  width: 330px;
  opacity: 0.5;
}
.machine__head {
  font-family: var(--font-display);
  font-size: 13px;
  letter-spacing: 0.4em;
  color: var(--c-cyan);
  border: 1px solid rgba(22, 211, 211, 0.4);
  border-bottom: none;
  padding: 4px 10px;
  width: 90px;
}
.machine__body {
  border: 1px solid rgba(10, 162, 255, 0.35);
  background: rgba(10, 162, 255, 0.04);
  display: flex;
  height: 210px;
}
.machine__door {
  flex: 1;
  position: relative;
  border-right: 1px solid rgba(10, 162, 255, 0.28);
  margin: 12px;
}
.machine__spindle {
  position: absolute;
  top: 6px;
  left: 50%;
  transform: translateX(-50%);
  width: 26px;
  height: 44px;
  border: 1px solid rgba(22, 211, 211, 0.55);
  background: rgba(22, 211, 211, 0.1);
}
.machine__table {
  position: absolute;
  bottom: 26px;
  left: 8%;
  right: 8%;
  height: 12px;
  border: 1px solid rgba(10, 162, 255, 0.5);
  background: rgba(10, 162, 255, 0.08);
}
.machine__part {
  position: absolute;
  bottom: 38px;
  left: 34%;
  width: 32%;
  height: 26px;
  border: 1px solid rgba(245, 165, 36, 0.6);
  background: rgba(245, 165, 36, 0.1);
  animation: indPulse 3s ease-in-out infinite;
}
.machine__panel {
  width: 108px;
  padding: 14px 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 10px;
  color: var(--text-dim);
}

.hero__text { position: relative; max-width: 520px; }
.hero__logo { display: flex; align-items: center; gap: 12px; margin-bottom: 26px; }
.hero__logo-mark {
  display: grid;
  place-items: center;
  width: 52px;
  height: 34px;
  font-family: var(--font-display);
  font-size: 16px;
  letter-spacing: 0.18em;
  color: #04131f;
  background: linear-gradient(135deg, var(--c-cyan), var(--c-primary));
  clip-path: polygon(0 0, 100% 0, 100% 72%, 84% 100%, 0 100%);
}
.hero__logo b {
  display: block;
  font-family: var(--font-display);
  font-size: 20px;
  letter-spacing: 0.16em;
  color: var(--text-strong);
}
.hero__logo span { font-size: 10px; letter-spacing: 0.14em; color: var(--text-mute); }

.hero__text h1 {
  font-size: 34px;
  line-height: 1.34;
  letter-spacing: 0.04em;
  font-weight: 600;
  color: var(--text-strong);
  margin-bottom: 14px;
}
.hero__text p { font-size: 13px; letter-spacing: 0.06em; }

.hero__specs {
  margin-top: 34px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-left: 2px solid var(--c-primary-dim);
  padding-left: 14px;
}
.spec { display: flex; gap: 12px; font-size: 12px; }
.spec span { width: 76px; flex: none; }
.spec b { color: var(--text); font-weight: 500; }

.hero__foot {
  position: absolute;
  left: 54px;
  right: 54px;
  bottom: 22px;
  display: flex;
  justify-content: space-between;
  font-size: 10px;
  letter-spacing: 0.16em;
  color: var(--text-mute);
}

/* ---------------- 右屏 ---------------- */
.login__panel {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 40px 34px;
  background: linear-gradient(180deg, var(--sheen), transparent);
}
.panel-box {
  width: 100%;
  max-width: 380px;
  border: 1px solid var(--line-strong);
  background: linear-gradient(180deg, var(--bg-panel-2), var(--bg-panel));
  box-shadow: 0 22px 50px rgba(0, 0, 0, 0.5);
  position: relative;
}
.panel-box::before {
  content: '';
  position: absolute;
  top: -1px; left: -1px; right: -1px;
  height: 2px;
  background: linear-gradient(90deg, var(--c-primary), transparent 70%);
}
.panel-box__head {
  height: 42px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
}
.panel-box__head b { font-family: var(--font-display); letter-spacing: 0.12em; color: var(--text-strong); }
.panel-box__status { margin-left: auto; display: flex; align-items: center; gap: 6px; font-size: 11px; }

.panel-box__body {
  padding: 18px 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.panel-box__error {
  display: flex;
  align-items: center;
  gap: 7px;
  font-size: 12px;
  color: var(--c-stop);
  border: 1px solid rgba(255, 77, 79, 0.4);
  background: rgba(255, 77, 79, 0.08);
  padding: 7px 10px;
}

.panel-box__accounts { border-top: 1px solid var(--line); padding: 14px 16px 16px; }
.account {
  display: grid;
  grid-template-columns: 84px 78px 1fr;
  align-items: center;
  gap: 6px;
  width: 100%;
  padding: 6px 8px;
  background: transparent;
  border: 1px solid transparent;
  border-radius: var(--radius);
  cursor: pointer;
  text-align: left;
  font-size: 11.5px;
  color: var(--text-dim);
}
.account:hover { background: var(--bg-hover); border-color: var(--line); color: var(--text); }
.account__role { color: var(--text); }
.account__user { color: var(--c-primary); }

.login__copy { font-size: 11px; text-align: center; }

@media (max-width: 1080px) {
  .login { grid-template-columns: 1fr; }
  .login__hero { display: none; }
}
</style>
