<script setup>
/** 系统管理：用户、角色、字典、日志，以及制造主数据维护。 */
import { computed, onMounted, ref } from 'vue';
import IndPanel from '@/components/ind/IndPanel.vue';
import IndTabs from '@/components/ind/IndTabs.vue';
import IndTable from '@/components/ind/IndTable.vue';
import IndPager from '@/components/ind/IndPager.vue';
import IndButton from '@/components/ind/IndButton.vue';
import IndField from '@/components/ind/IndField.vue';
import IndInput from '@/components/ind/IndInput.vue';
import IndSelect from '@/components/ind/IndSelect.vue';
import IndTag from '@/components/ind/IndTag.vue';
import IndLed from '@/components/ind/IndLed.vue';
import IndModal from '@/components/ind/IndModal.vue';
import { api } from '@/api';
import { dateTime, num } from '@/utils/format';
import { useToast } from '@/stores/toast';
import { useAuth } from '@/stores/auth';

const toast = useToast();
const auth = useAuth();
const tab = ref('user');

const users = ref([]);
const roles = ref([]);
const dictAll = ref({});
const loginLogs = ref([]);
const operLogs = ref([]);
const pwdForm = ref({ oldPassword: '', newPassword: '' });

const userOpen = ref(false);
const userForm = ref({});
const roleOpen = ref(false);
const roleForm = ref({});
const dictOpen = ref(false);
const dictForm = ref({});

/* ---------------- 制造主数据：字段定义与数据源 ---------------- */
const MD_DEFS = {
  material: {
    label: '物料',
    api: () => api.masterdata.material,
    columns: [
      { key: 'matCode', title: '物料编码', width: '110px', kind: 'mono' },
      { key: 'matName', title: '物料名称', minWidth: '140px' },
      { key: 'spec', title: '规格', width: '132px' },
      { key: 'materialGrade', title: '材质', width: '88px' },
      { key: 'matType', title: '类型', width: '78px', kind: 'tag' },
      { key: 'unit', title: '单位', width: '60px' },
      { key: 'drawingNo', title: '图号', width: '116px', kind: 'mono' },
      { key: 'currentStock', title: '库存', width: '84px', align: 'right', kind: 'num' },
      { key: 'safeStock', title: '安全库存', width: '88px', align: 'right', kind: 'num' },
      { key: 'unitCost', title: '单位成本', width: '88px', align: 'right', kind: 'num' },
      { key: 'standardPrice', title: '标准售价', width: '88px', align: 'right', kind: 'num' },
      { key: 'actions', title: '操作', width: '76px', align: 'right' },
    ],
    fields: [
      { key: 'matCode', label: '物料编码', required: true },
      { key: 'matName', label: '物料名称', required: true },
      { key: 'spec', label: '规格' },
      { key: 'materialGrade', label: '材质' },
      { key: 'unit', label: '单位' },
      { key: 'matType', label: '类型', options: ['毛坯', '半成品', '成品', '标准件', '辅料'] },
      { key: 'drawingNo', label: '图号' },
      { key: 'currentStock', label: '当前库存', type: 'number' },
      { key: 'safeStock', label: '安全库存', type: 'number' },
      { key: 'unitCost', label: '单位成本', type: 'number' },
      { key: 'standardPrice', label: '标准售价', type: 'number' },
    ],
  },
  machine: {
    label: '机床',
    api: () => api.masterdata.machine,
    columns: [
      { key: 'macCode', title: '机台编号', width: '90px', kind: 'mono' },
      { key: 'macName', title: '机台名称', minWidth: '140px' },
      { key: 'macModel', title: '型号', width: '104px' },
      { key: 'brand', title: '品牌', width: '94px' },
      { key: 'cncType', title: '类型', width: '112px' },
      { key: 'controllerType', title: '数控系统', width: '132px' },
      { key: 'protocol', title: '接入协议', width: '94px', kind: 'tag' },
      { key: 'ipAddr', title: 'IP 地址', width: '118px', kind: 'mono' },
      { key: 'axisCount', title: '轴数', width: '58px', align: 'right', kind: 'num' },
      { key: 'maxSpindleSpeed', title: '最高转速', width: '88px', align: 'right', kind: 'num' },
      { key: 'oeeTarget', title: 'OEE 目标', width: '84px', align: 'right', kind: 'num' },
      { key: 'status', title: '状态', width: '84px', kind: 'tag' },
      { key: 'actions', title: '操作', width: '76px', align: 'right' },
    ],
    fields: [
      { key: 'macCode', label: '机台编号', required: true },
      { key: 'macName', label: '机台名称', required: true },
      { key: 'macModel', label: '型号' },
      { key: 'brand', label: '品牌' },
      { key: 'cncType', label: '设备类型', options: ['立式加工中心', '卧式加工中心', '五轴加工中心', '数控车床', '车削中心', '外圆磨床', '内圆磨床', '线切割', '电火花'] },
      { key: 'controllerType', label: '数控系统' },
      { key: 'protocol', label: '接入协议', options: ['FOCAS', 'OPCUA', 'MTConnect', 'MQTT', '无'] },
      { key: 'ipAddr', label: 'IP 地址' },
      { key: 'axisCount', label: '轴数', type: 'number' },
      { key: 'maxSpindleSpeed', label: '最高转速 rpm', type: 'number' },
      { key: 'toolMagazineCapacity', label: '刀库容量', type: 'number' },
      { key: 'travelX', label: 'X 行程 mm', type: 'number' },
      { key: 'travelY', label: 'Y 行程 mm', type: 'number' },
      { key: 'travelZ', label: 'Z 行程 mm', type: 'number' },
      { key: 'oeeTarget', label: 'OEE 目标 %', type: 'number' },
      { key: 'runState', label: '初始运行状态', options: ['RUN', 'IDLE', 'STOP', 'MAINT', 'OFFLINE'] },
    ],
  },
  process: {
    label: '工序',
    api: () => api.masterdata.process,
    columns: [
      { key: 'procCode', title: '工序号', width: '92px', kind: 'mono' },
      { key: 'procName', title: '工序名称', minWidth: '130px' },
      { key: 'procType', title: '工序类型', width: '104px', kind: 'tag' },
      { key: 'defaultWcType', title: '默认工作中心类型', width: '150px' },
      { key: 'isInspection', title: '是否检验', width: '90px', format: (r) => (r.isInspection ? '是' : '否') },
      { key: 'isOutsource', title: '是否外协', width: '90px', format: (r) => (r.isOutsource ? '是' : '否') },
      { key: 'remark', title: '备注', minWidth: '140px', format: (r) => r.remark || '—' },
      { key: 'actions', title: '操作', width: '76px', align: 'right' },
    ],
    fields: [
      { key: 'procCode', label: '工序号', required: true },
      { key: 'procName', label: '工序名称', required: true },
      { key: 'procType', label: '工序类型', options: ['CUT', 'TURN', 'MILL', 'DRILL', 'TAP', 'GRIND', 'WEDM', 'EDM', 'HEAT', 'DEBURR', 'INSPECT'] },
      { key: 'defaultWcType', label: '默认工作中心类型' },
      { key: 'isInspection', label: '是否检验工序', type: 'number' },
      { key: 'isOutsource', label: '是否外协工序', type: 'number' },
      { key: 'sortNo', label: '排序号', type: 'number' },
      { key: 'remark', label: '备注' },
    ],
  },
  routing: {
    label: '工艺路线',
    api: () => api.masterdata.routing,
    columns: [
      { key: 'routingCode', title: '路线编号', width: '112px', kind: 'mono' },
      { key: 'routingName', title: '工艺路线名称', minWidth: '190px' },
      { key: 'productMatId', title: '产品物料 ID', width: '108px', align: 'right', kind: 'mono' },
      { key: 'version', title: '版本', width: '76px', kind: 'mono' },
      { key: 'status', title: '状态', width: '76px', format: (r) => (r.status ? '启用' : '停用') },
      { key: 'actions', title: '操作', width: '76px', align: 'right' },
    ],
    fields: [
      { key: 'routingCode', label: '路线编号', required: true },
      { key: 'routingName', label: '工艺路线名称', required: true },
      { key: 'productMatId', label: '产品物料 ID', type: 'number' },
      { key: 'version', label: '版本' },
    ],
  },
  customer: {
    label: '客户',
    api: () => api.masterdata.customer,
    columns: [
      { key: 'custCode', title: '客户编号', width: '92px', kind: 'mono' },
      { key: 'custName', title: '客户名称', minWidth: '190px' },
      { key: 'contact', title: '联系人', width: '96px' },
      { key: 'phone', title: '电话', width: '124px', kind: 'mono' },
      { key: 'custLevel', title: '等级', width: '72px', kind: 'tag', tone: 'primary' },
      { key: 'industry', title: '行业', width: '112px' },
      { key: 'address', title: '地址', minWidth: '180px', format: (r) => r.address || '—' },
      { key: 'actions', title: '操作', width: '76px', align: 'right' },
    ],
    fields: [
      { key: 'custCode', label: '客户编号', required: true },
      { key: 'custName', label: '客户名称', required: true },
      { key: 'contact', label: '联系人' },
      { key: 'phone', label: '电话' },
      { key: 'custLevel', label: '客户等级', options: ['A', 'B', 'C'] },
      { key: 'industry', label: '所属行业' },
      { key: 'address', label: '地址' },
    ],
  },
  employee: {
    label: '员工',
    api: () => api.masterdata.employee,
    columns: [
      { key: 'empNo', title: '工号', width: '82px', kind: 'mono' },
      { key: 'empName', title: '姓名', width: '90px' },
      { key: 'deptName', title: '部门', width: '132px' },
      { key: 'post', title: '岗位', width: '140px' },
      { key: 'skillLevel', title: '技能等级', width: '94px', kind: 'tag', tone: 'info' },
      { key: 'teamName', title: '班组', width: '72px' },
      { key: 'phone', title: '电话', width: '124px', kind: 'mono' },
      { key: 'hireDate', title: '入职日期', width: '110px', kind: 'date' },
      { key: 'actions', title: '操作', width: '76px', align: 'right' },
    ],
    fields: [
      { key: 'empNo', label: '工号', required: true },
      { key: 'empName', label: '姓名', required: true },
      { key: 'deptName', label: '部门' },
      { key: 'post', label: '岗位' },
      { key: 'skillLevel', label: '技能等级', options: ['初级工', '中级工', '高级工', '技师', '高级技师'] },
      { key: 'teamName', label: '班组' },
      { key: 'phone', label: '电话' },
      { key: 'hireDate', label: '入职日期' },
    ],
  },
  shift: {
    label: '班次',
    api: () => api.masterdata.shift,
    columns: [
      { key: 'shiftCode', title: '班次编码', width: '92px', kind: 'mono' },
      { key: 'shiftName', title: '班次名称', width: '104px' },
      { key: 'startTime', title: '开始时间', width: '92px', kind: 'mono' },
      { key: 'endTime', title: '结束时间', width: '92px', kind: 'mono' },
      { key: 'crossDay', title: '跨天', width: '70px', format: (r) => (r.crossDay ? '是' : '否') },
      { key: 'breakMinutes', title: '休息分钟', width: '88px', align: 'right', kind: 'num' },
      { key: 'actions', title: '操作', width: '76px', align: 'right' },
    ],
    fields: [
      { key: 'shiftCode', label: '班次编码', required: true },
      { key: 'shiftName', label: '班次名称', required: true },
      { key: 'startTime', label: '开始时间' },
      { key: 'endTime', label: '结束时间' },
      { key: 'crossDay', label: '是否跨天', type: 'number' },
      { key: 'breakMinutes', label: '休息分钟', type: 'number' },
    ],
  },
  workshop: {
    label: '车间',
    api: () => api.masterdata.workshop,
    columns: [
      { key: 'wsCode', title: '车间编码', width: '92px', kind: 'mono' },
      { key: 'wsName', title: '车间名称', minWidth: '150px' },
      { key: 'manager', title: '负责人', width: '90px' },
      { key: 'phone', title: '电话', width: '124px', kind: 'mono' },
      { key: 'areaSqm', title: '面积 m²', width: '96px', align: 'right', kind: 'num' },
      { key: 'actions', title: '操作', width: '76px', align: 'right' },
    ],
    fields: [
      { key: 'wsCode', label: '车间编码', required: true },
      { key: 'wsName', label: '车间名称', required: true },
      { key: 'manager', label: '负责人' },
      { key: 'phone', label: '电话' },
      { key: 'areaSqm', label: '面积 m²', type: 'number' },
    ],
  },
};

const mdType = ref('material');
const mdRows = ref([]);
const mdTotal = ref(0);
const mdPage = ref(1);
const mdSize = ref(20);
const mdLoading = ref(false);
const mdOpen = ref(false);
const mdForm = ref({});

const mdDef = computed(() => MD_DEFS[mdType.value]);

const userColumns = [
  { key: 'username', title: '账号', width: '104px', kind: 'mono' },
  { key: 'realName', title: '姓名', width: '94px' },
  { key: 'empNo', title: '工号', width: '82px', kind: 'mono' },
  { key: 'roleCode', title: '角色', width: '92px', kind: 'tag', tone: 'primary' },
  { key: 'deptName', title: '部门', width: '118px' },
  { key: 'phone', title: '电话', width: '124px', kind: 'mono' },
  { key: 'status', title: '状态', width: '80px' },
  { key: 'lastLoginTime', title: '最近登录', width: '140px', kind: 'datetime' },
  { key: 'actions', title: '操作', width: '180px', align: 'right' },
];

const roleColumns = [
  { key: 'roleCode', title: '角色编码', width: '110px', kind: 'mono' },
  { key: 'roleName', title: '角色名称', width: '120px' },
  { key: 'homePage', title: '默认首页', width: '110px', kind: 'mono' },
  { key: 'perms', title: '功能权限', minWidth: '300px' },
  { key: 'remark', title: '说明', width: '150px', format: (r) => r.remark || '—' },
  { key: 'actions', title: '操作', width: '76px', align: 'right' },
];

const loginColumns = [
  { key: 'loginTime', title: '登录时间', width: '150px', kind: 'datetime' },
  { key: 'username', title: '账号', width: '110px', kind: 'mono' },
  { key: 'ip', title: '来源 IP', width: '140px', kind: 'mono' },
  { key: 'status', title: '结果', width: '80px' },
  { key: 'msg', title: '说明', minWidth: '160px' },
  { key: 'browser', title: '客户端', minWidth: '200px', format: (r) => r.browser || '—' },
];

const operColumns = [
  { key: 'operTime', title: '操作时间', width: '150px', kind: 'datetime' },
  { key: 'username', title: '账号', width: '94px', kind: 'mono' },
  { key: 'module', title: '模块', width: '112px' },
  { key: 'method', title: '方法', width: '68px', kind: 'mono' },
  { key: 'uri', title: '接口', minWidth: '240px', kind: 'mono' },
  { key: 'costMs', title: '耗时', width: '76px', align: 'right', kind: 'mono', format: (r) => r.costMs + 'ms' },
  { key: 'status', title: '结果', width: '70px' },
  { key: 'ip', title: 'IP', width: '130px', kind: 'mono' },
];

async function loadUsers() {
  users.value = await api.system.users();
}

async function loadRoles() {
  roles.value = await api.system.roles();
}

async function loadDict() {
  dictAll.value = await api.system.dictAll();
}

async function loadLogs() {
  loginLogs.value = await api.system.loginLogs(80);
  operLogs.value = await api.system.operLogs(80);
}

async function loadMd() {
  mdLoading.value = true;
  try {
    const res = await mdDef.value.api().page({ page: mdPage.value, size: mdSize.value });
    mdRows.value = res.records || [];
    mdTotal.value = res.total || 0;
  } finally {
    mdLoading.value = false;
  }
}

function openUser(row) {
  userForm.value = row ? { ...row } : { status: 1, roleCode: 'operator' };
  userOpen.value = true;
}

async function saveUser() {
  await api.system.saveUser(userForm.value);
  toast.ok('用户已保存');
  userOpen.value = false;
  await loadUsers();
}

async function resetPwd(row) {
  await api.system.resetPassword(row.id);
  toast.warn(`账号 ${row.username} 的密码已重置为 mes123456`);
}

async function delUser(row) {
  if (!confirm(`确认删除账号 ${row.username} ？`)) return;
  await api.system.deleteUser(row.id);
  toast.ok('账号已删除');
  await loadUsers();
}

function openRole(row) {
  roleForm.value = row ? { ...row } : { status: 1, homePage: '/board' };
  roleOpen.value = true;
}

async function saveRole() {
  await api.system.saveRole(roleForm.value);
  toast.ok('角色已保存');
  roleOpen.value = false;
  await loadRoles();
}

function openDict(type, item) {
  dictForm.value = item ? { ...item } : { dictType: type, sortNo: 1, status: 1, color: '#0aa2ff' };
  dictOpen.value = true;
}

async function saveDict() {
  await api.system.saveDict(dictForm.value);
  toast.ok('字典项已保存');
  dictOpen.value = false;
  await loadDict();
}

async function delDict(item) {
  await api.system.deleteDict(item.id);
  toast.ok('字典项已删除');
  await loadDict();
}

function openMd(row) {
  mdForm.value = row ? { ...row } : {};
  mdOpen.value = true;
}

async function saveMd() {
  await mdDef.value.api().save(mdForm.value);
  toast.ok(`${mdDef.value.label}已保存`);
  mdOpen.value = false;
  await loadMd();
}

async function changePassword() {
  if (!pwdForm.value.newPassword || pwdForm.value.newPassword.length < 6) {
    toast.warn('新密码长度不能少于 6 位');
    return;
  }
  await api.auth.changePassword(pwdForm.value.oldPassword, pwdForm.value.newPassword);
  toast.ok('密码已修改，请牢记新密码');
  pwdForm.value = { oldPassword: '', newPassword: '' };
}

onMounted(async () => {
  await Promise.all([loadUsers(), loadRoles(), loadDict(), loadLogs(), loadMd()]);
});
</script>

<template>
  <div class="page">
    <IndTabs
      v-model="tab"
      :tabs="[
        { key: 'user', label: '用户与权限' },
        { key: 'dict', label: '数据字典' },
        { key: 'log', label: '系统日志' },
        { key: 'masterdata', label: '制造主数据' },
        { key: 'me', label: '个人设置' },
      ]"
    />

    <!-- 用户与角色 -->
    <template v-if="tab === 'user'">
      <IndPanel title="系统用户" :sub="`共 ${users.length} 个账号`" flush>
        <template #tools>
          <IndButton size="sm" variant="primary" @click="openUser(null)">＋ 新建用户</IndButton>
        </template>
        <div style="height: 100%; min-height: 0">
          <IndTable :columns="userColumns" :rows="users" dense empty-text="暂无用户">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'actions'">
                <div class="row-actions">
                  <button class="link" @click="openUser(row)">编辑</button>
                  <button class="link" @click="resetPwd(row)">重置密码</button>
                  <button class="link danger" @click="delUser(row)">删除</button>
                </div>
              </template>
              <template v-else-if="col.key === 'status'">
                <IndTag :tone="row.status === 1 ? 'run' : 'plain'" led>{{ row.status === 1 ? '启用' : '停用' }}</IndTag>
              </template>
              <template v-else-if="col.kind === 'datetime'">
                <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ row[col.key] ?? '—' }}</span>
              </template>
            </template>
          </IndTable>
        </div>
      </IndPanel>

      <IndPanel title="角色与功能权限" sub="ROLES" flush>
        <div style="height: 100%; min-height: 0">
          <IndTable :columns="roleColumns" :rows="roles" dense empty-text="暂无角色">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'actions'">
                <button class="link" @click="openRole(row)">编辑</button>
              </template>
              <template v-else-if="col.key === 'perms'">
                <span class="mono" style="font-size: 11px">{{ row.perms === '*' ? '*（全部权限）' : row.perms }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ row[col.key] ?? '—' }}</span>
              </template>
            </template>
          </IndTable>
        </div>
      </IndPanel>
    </template>

    <!-- 字典 -->
    <template v-else-if="tab === 'dict'">
      <div class="dict-grid">
        <IndPanel
          v-for="(items, type) in dictAll"
          :key="type"
          :title="type"
          :sub="`${items.length} 项`"
          flush
        >
          <template #tools>
            <IndButton size="sm" variant="ghost" @click="openDict(type, null)">＋</IndButton>
          </template>
          <div class="dict-list">
            <div v-for="d in items" :key="d.id" class="dict-item">
              <i class="dict-dot" :style="{ background: d.color || 'var(--c-offline)' }" />
              <span class="dict-label">{{ d.dictLabel }}</span>
              <span class="mono mute dict-value">{{ d.dictValue }}</span>
              <button class="link" @click="openDict(type, d)">改</button>
              <button class="link danger" @click="delDict(d)">删</button>
            </div>
          </div>
        </IndPanel>
      </div>
    </template>

    <!-- 日志 -->
    <template v-else-if="tab === 'log'">
      <IndPanel title="登录日志" sub="LOGIN LOG" flush>
        <div style="height: 100%; min-height: 0">
          <IndTable :columns="loginColumns" :rows="loginLogs" dense empty-text="暂无登录日志">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'status'">
                <IndTag :tone="row.status === 1 ? 'run' : 'stop'" led>{{ row.status === 1 ? '成功' : '失败' }}</IndTag>
              </template>
              <template v-else-if="col.kind === 'datetime'">
                <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
              </template>
            </template>
          </IndTable>
        </div>
      </IndPanel>

      <IndPanel title="操作日志（写操作留痕）" sub="OPERATION LOG" flush>
        <template #tools>
          <IndButton size="sm" variant="ghost" @click="loadLogs">刷新</IndButton>
        </template>
        <div style="height: 100%; min-height: 0">
          <IndTable :columns="operColumns" :rows="operLogs" dense empty-text="暂无操作日志（执行写操作后产生）">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'status'">
                <IndTag :tone="row.status === 1 ? 'run' : 'stop'">{{ row.status === 1 ? '成功' : '失败' }}</IndTag>
              </template>
              <template v-else-if="col.kind === 'datetime'">
                <span class="mono" style="font-size: 11px">{{ dateTime(row[col.key]) }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">{{ col.format ? col.format(row) : (row[col.key] ?? '—') }}</span>
              </template>
            </template>
          </IndTable>
        </div>
      </IndPanel>
    </template>

    <!-- 主数据 -->
    <template v-else-if="tab === 'masterdata'">
      <IndPanel :title="`制造主数据 · ${mdDef.label}`" sub="MASTER DATA" flush>
        <template #tools>
          <IndSelect
            v-model="mdType"
            :options="Object.entries(MD_DEFS).map(([key, v]) => ({ value: key, label: v.label }))"
            style="width: 140px"
            @change="loadMd"
          />
          <IndButton size="sm" @click="loadMd">刷新</IndButton>
          <IndButton size="sm" variant="primary" @click="openMd(null)">＋ 新增{{ mdDef.label }}</IndButton>
        </template>
        <div style="height: 100%; display: flex; flex-direction: column; min-height: 0">
          <IndTable :columns="mdDef.columns" :rows="mdRows" :loading="mdLoading" dense empty-text="暂无数据">
            <template #cell="{ row, col }">
              <template v-if="col.key === 'actions'">
                <button class="link" @click="openMd(row)">编辑</button>
              </template>
              <template v-else-if="col.key === 'matType' || col.key === 'procType' || col.key === 'status' || col.key === 'custLevel' || col.key === 'skillLevel' || col.key === 'protocol'">
                <IndTag :tone="col.tone || 'plain'">{{ row[col.key] ?? '—' }}</IndTag>
              </template>
              <template v-else-if="col.kind === 'date'">
                <span class="mono">{{ String(row[col.key] || '—').slice(0, 10) }}</span>
              </template>
              <template v-else>
                <span :class="{ mono: col.kind === 'mono' }">
                  {{ col.format ? col.format(row) : (row[col.key] ?? '—') }}
                </span>
              </template>
            </template>
          </IndTable>
          <IndPager :total="mdTotal" v-model:page="mdPage" v-model:size="mdSize" @change="loadMd" />
        </div>
      </IndPanel>
    </template>

    <!-- 个人设置 -->
    <template v-else>
      <div class="grid-2">
        <IndPanel title="当前登录信息" sub="PROFILE">
          <div class="profile">
            <div class="profile__avatar">{{ (auth.displayName || '?').slice(0, 1) }}</div>
            <div class="profile__info">
              <b>{{ auth.displayName }}</b>
              <span class="mute">{{ auth.roleName }} · {{ auth.user?.deptName || '—' }}</span>
              <span class="mute mono">工号 {{ auth.user?.empNo || '—' }} · 账号 {{ auth.user?.username }}</span>
            </div>
          </div>
          <div class="ind-section-title" style="margin-top: 14px">当前角色权限</div>
          <div class="perm-wrap">
            <IndTag v-if="auth.perms?.includes('*')" tone="primary">全部权限</IndTag>
            <IndTag v-else v-for="p in (auth.perms || '').split(',').filter(Boolean)" :key="p">{{ p }}</IndTag>
          </div>
        </IndPanel>
        <IndPanel title="修改密码" sub="PASSWORD">
          <IndField label="原密码" required>
            <IndInput v-model="pwdForm.oldPassword" type="password" />
          </IndField>
          <IndField label="新密码（至少 6 位）" required style="margin-top: 12px">
            <IndInput v-model="pwdForm.newPassword" type="password" />
          </IndField>
          <IndButton variant="primary" style="margin-top: 14px" @click="changePassword">提交修改</IndButton>
          <p class="mute" style="font-size: 11.5px; margin-top: 12px">
            演示环境统一初始密码为 mes123456；生产部署请通过环境变量 MES_JWT_SECRET 更换令牌密钥，
            并把密码存储改为加盐哈希。
          </p>
        </IndPanel>
      </div>
    </template>

    <IndModal v-model="userOpen" :title="userForm.id ? '编辑用户' : '新建用户'" :width="620">
      <div class="form-grid">
        <IndField label="登录账号" required>
          <IndInput v-model="userForm.username" />
        </IndField>
        <IndField label="姓名" required>
          <IndInput v-model="userForm.realName" />
        </IndField>
        <IndField label="工号">
          <IndInput v-model="userForm.empNo" />
        </IndField>
        <IndField label="角色" required>
          <IndSelect
            v-model="userForm.roleCode"
            :options="roles.map((r) => ({ value: r.roleCode, label: `${r.roleName}（${r.roleCode}）` }))"
            placeholder="请选择角色"
          />
        </IndField>
        <IndField label="部门">
          <IndInput v-model="userForm.deptName" />
        </IndField>
        <IndField label="电话">
          <IndInput v-model="userForm.phone" />
        </IndField>
        <IndField label="初始密码（留空默认 mes123456）">
          <IndInput v-model="userForm.password" type="password" />
        </IndField>
        <IndField label="状态">
          <IndSelect v-model="userForm.status" :options="[{ value: 1, label: '启用' }, { value: 0, label: '停用' }]" placeholder="启用" />
        </IndField>
      </div>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="primary" @click="saveUser">保存</IndButton>
      </template>
    </IndModal>

    <IndModal v-model="roleOpen" title="角色与权限" :width="660">
      <div class="form-grid">
        <IndField label="角色编码" required>
          <IndInput v-model="roleForm.roleCode" />
        </IndField>
        <IndField label="角色名称" required>
          <IndInput v-model="roleForm.roleName" />
        </IndField>
        <IndField label="默认首页">
          <IndInput v-model="roleForm.homePage" placeholder="/board" />
        </IndField>
        <IndField label="状态">
          <IndSelect v-model="roleForm.status" :options="[{ value: 1, label: '启用' }, { value: 0, label: '停用' }]" placeholder="启用" />
        </IndField>
      </div>
      <IndField label="功能权限（英文逗号分隔，* 表示全部）" style="margin-top: 12px">
        <IndInput v-model="roleForm.perms" placeholder="dashboard,order,schedule,dispatch,report,stats" />
      </IndField>
      <p class="mute" style="font-size: 11.5px; margin-top: 10px">
        可用的权限标识：dashboard 车间监控 · order 订单 · schedule 排产 · dispatch 派工 · report 报工 ·
        terminal 终端 · quality 质量 · spc · tool 刀具 · equipment 设备 · fault · maint · trace 追溯 ·
        stats 报表 · respond 安灯 · masterdata 主数据
      </p>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="primary" @click="saveRole">保存</IndButton>
      </template>
    </IndModal>

    <IndModal v-model="dictOpen" :title="`字典项 · ${dictForm.dictType || ''}`" :width="520">
      <div class="form-grid">
        <IndField label="字典类型" required>
          <IndInput v-model="dictForm.dictType" />
        </IndField>
        <IndField label="显示名称" required>
          <IndInput v-model="dictForm.dictLabel" />
        </IndField>
        <IndField label="字典值" required>
          <IndInput v-model="dictForm.dictValue" />
        </IndField>
        <IndField label="排序号">
          <IndInput v-model="dictForm.sortNo" type="number" />
        </IndField>
        <IndField label="标识颜色">
          <IndInput v-model="dictForm.color" placeholder="#0aa2ff" />
        </IndField>
      </div>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="primary" @click="saveDict">保存</IndButton>
      </template>
    </IndModal>

    <IndModal v-model="mdOpen" :title="`${mdForm.id ? '编辑' : '新增'}${mdDef.label}`" :width="700">
      <div class="form-grid">
        <IndField
          v-for="f in mdDef.fields"
          :key="f.key"
          :label="f.label"
          :required="f.required"
        >
          <IndSelect v-if="f.options" v-model="mdForm[f.key]" :options="f.options" placeholder="请选择" />
          <IndInput v-else v-model="mdForm[f.key]" :type="f.type === 'number' ? 'number' : 'text'" />
        </IndField>
      </div>
      <template #foot="{ close }">
        <IndButton variant="ghost" @click="close">取消</IndButton>
        <IndButton variant="primary" @click="saveMd">保存</IndButton>
      </template>
    </IndModal>
  </div>
</template>

<style scoped>
.grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.row-actions { display: flex; gap: 9px; justify-content: flex-end; }
.link { background: none; border: none; padding: 0; color: var(--c-primary); cursor: pointer; font-size: 12px; }
.link:hover { text-decoration: underline; }
.link.danger { color: var(--c-stop); }

.dict-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.dict-list { display: flex; flex-direction: column; padding: 6px 8px; }
.dict-item {
  display: grid;
  grid-template-columns: 10px 1fr 92px 26px 26px;
  align-items: center;
  gap: 8px;
  padding: 5px 4px;
  border-bottom: 1px solid var(--line-soft);
  font-size: 12px;
}
.dict-dot { width: 9px; height: 9px; }
.dict-label { color: var(--text); }
.dict-value { font-size: 11px; }
.dict-item .link { font-size: 11px; }

.profile { display: flex; align-items: center; gap: 14px; }
.profile__avatar {
  width: 52px;
  height: 52px;
  display: grid;
  place-items: center;
  font-size: 22px;
  color: #04131f;
  background: linear-gradient(135deg, var(--c-cyan), var(--c-primary));
  clip-path: polygon(0 0, 100% 0, 100% 72%, 84% 100%, 0 100%);
}
.profile__info { display: flex; flex-direction: column; gap: 3px; font-size: 12.5px; }
.profile__info b { font-size: 16px; color: var(--text-strong); }
.perm-wrap { display: flex; flex-wrap: wrap; gap: 6px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
</style>
