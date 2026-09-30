# 一、GitHub 同类开源系统调研报告

> 调研目的：在动手编码之前，先摸清开源 MES 生态里「已经被验证过的领域模型、菜单结构、技术栈和踩坑点」，
> 避免闭门造车，也避免直接照搬一套通用后台模板。
>
> 调研方式：通过 GitHub Search API 按关键词、语言、star 数检索，逐个拉取仓库元数据、README、完整文件树，
> 再从文件树反推其**真实的功能模块划分**（README 常有夸大，文件树不会骗人）。

---

## 1. 检索过程

使用的检索式（`sort=stars`）：

| 检索式 | 命中总数 | 说明 |
|---|---|---|
| `manufacturing execution system in:name,description stars:>30` | 10 | 英文命名的高星 MES |
| `MES in:name,description language:Java stars:>10` | 483 | Java 系 MES，噪声多（大量 Messenger 类项目） |
| `mes system in:name,description language:Vue stars:>5` | 4 | 前端 Vue 的 MES，非常稀缺 |
| `开源MES in:name,description,readme` | 57331 | 中文关键词，召回噪声大，需人工过滤 |
| `cnc machine monitoring in:name,description stars:>60` | 1 | 说明「CNC 专用开源 MES」几乎空白 |
| `OPC UA cnc in:name,description stars:>20` | 1 | 设备接入层是独立赛道，不在 MES 内 |

**第一个重要结论**：`cnc + MES` 的开源项目极少（高星项目仅个位数），
说明**「面向 CNC 行业的 MES」存在真实的产品空白**，本课题的选题方向是有价值的。

## 2. 重点参考项目画像

### 2.1 ricefishtech/industry4.0-mes ★514 · Java · 开源 MES

基于 Qcadoo MES 的二次封装版本。文件树共 **9154 个路径**，`mes/mes-plugins/` 下是完整的插件化领域模块，
这是本次调研中**领域模型最完整**的一份资料，实际插件清单（去重后）如下：

```
orders                               订单
technologies / technologies-generator 工艺 / 工艺生成器
work-plans                           工作计划
production-scheduling                生产排产
production-counting                  生产报工计数
production-per-shift                 按班次产量
production-balance-per-shift         班次产能平衡
production-lines                     生产线
material-requirements                物料需求
material-requirement-coverage-for-order 订单物料齐套覆盖
simple-material-balance              简易物料平衡
material-flow / -multitransfers / -resources 物料流转
sub-orders                           子订单
operation-time-calculations          工序工时计算
time-norms-for-operations            工序时间定额
operation-cost-calculations          工序成本计算
cost-norms-for-materials/-operation/-product  成本定额
avg-labor-cost-calc-for-order        订单平均人工成本
wage-groups                          工资组
advanced-genealogy / -for-orders     高级谱系 / 订单谱系追溯
stoppage                             停机
deviation-causes-reporting           偏差原因上报
cmms-machine-parts                   设备维护 CMMS
line-changeover-norms                换型定额
assignment-to-shift                  班次排班
gantt-for-operation / -for-shifts    甘特图（工序 / 班次）
deliveries / deliveries-to-material-flow 交付
states                               状态机
column-extension / product-characteristics  扩展字段 / 产品特性
minimal-affordable-quantity          最小可负担数量
warehouse-minimal-state              仓库最小库存
```

**可复用经验**：

1. **状态机（`states`）被单独抽成一个插件**——说明工单/工序任务的流转状态必须集中建模，
   不能散落在各个 Service 的 `if` 里。本设计据此抽出统一的状态常量与流转校验。
2. **「物料需求覆盖 · 齐套分析」单独成模块**——机加工最大的停工原因就是「毛坯没到、料没备齐」，
   排产时必须先做齐套检查。本系统将其落为「排产前齐套校验」。
3. **成本/工时定额体系（time-norms + cost-norms）分产品、工序、物料三层**——
   工时定额不是拍脑袋写死，而是「产品级 → 工序级 → 订单级」可继承可覆盖的三层结构。
   **本系统采纳**：工艺路线工序上带 `setupTime / pieceTime`，工单任务生成时快照定额，报工后回写实际工时。
4. **`stoppage`（停机）与 `deviation-causes-reporting`（偏差原因）独立建模**——
   OEE 的时间损失必须按原因归类才能改进。**本系统采纳**：停机记录带原因字典。
5. **`gantt-for-operation` / `gantt-for-shifts` 双甘特**——排产视图与排班视图要分开，不能混成一张图。
6. 反面教训：该项目基于 Struts/Spring 3/JPA 老栈，`.idea/` 目录和 `lib/` 本地 jar 都提交进了仓库，
   **构建极其困难**。→ 本项目坚持「不提交 IDE 文件、不提交本地 jar、依赖全部走 Maven 坐标」。

### 2.2 metaxk-company/free-mes ★424 · SpringBoot2 + Vue3 + MySQL + Redis + MinIO

当前**维护最活跃的中文开源 MES**（持续更新到 2026 年）。README 给出的功能截图包括：

```
排班日历 → 排班计划 → 生产订单 → 生产排产 → 生产任务 → 生产报工 → 数据大屏
```

其技术架构：`SpringBoot2 + Vue3 + MySQL8 + Redis + Minio`（另有 SpringCloud 版）。

**可复用经验**：

1. 这基本是**中国中小制造企业 MES 的标准菜单序列**：排班 → 排班计划 → 订单 → 排产 → 任务 → 报工 → 大屏。
   本系统保留这条主链路，并在其之上补足 CNC 行业特性（刀具、程序、首件、停机原因）。
2. **「数据大屏」是独立交付物**，不是后台首页。车间挂墙的看板与办公电脑上的管理界面是两种完全不同的设计。
   **本系统采纳**：看板页独立路由、独立布局、无侧边栏、纯深色、大字号。
3. 该项目的开源策略是「开源版无演示无支持」，说明**部署可复现性**是开源 MES 的最大门槛。
   → 本系统默认内置 H2 文件库 + 自动建表 + 自动灌种子数据，`mvn spring-boot:run` 一条命令即可跑通。

### 2.3 SMEWebify/WebErpMesv2 ★217 · PHP/Laravel · 面向钣金与工业机械的 ERP+MES

**与本次课题最贴近**的一个项目（钣金、精密切削、模具制造、工业外协）。
从 `resources/views/` 目录可反推其真实模块：

```
methods/      工艺方法主数据：工序、工位、资源、刀具、单位、标准 BOM、BOM 族
workflow/     订单、报价、预订单、任务、看板、甘特、日历、交付、发票、退货
production/   生产追踪 production-trace
workshop/     车间看板、安灯(andon)、车间任务、车间库存、扫码报工
quality/      检验项目、不合格品(NC)、让步接收(derogations)、质量措施、过程图、AMDEC(FMEA)
gmao/         维护计划、维护工单
planning/     排版套料 nesting
osh/          风险、事件、隐患、技能矩阵、培训
times/        工时、班次
products/     物料、批次、序列号、库存
```

**可复用经验**：

1. `production-trace.blade.php` 与 `trace-show.blade.php` 说明**追溯是一等公民**，
   且追溯页面是「以追溯号为入口、展示全链路」的独立视图。本系统采纳并做成正/反向双向追溯。
2. `quality-derogations`（让步接收）——**不合格品的处置不是只有报废**，
   标准四态是：返工 / 返修 / 让步接收 / 报废。本系统直接采用这四态闭环。
3. `methods-tools.blade.php` + `methods-ressources.blade.php`：**刀具是工艺主数据的一部分**，
   与「工序-资源」同级。这提醒：刀具管理不能做成孤立台账，要与工序、机床、工单联动。
4. `workshop-andon.blade.php` 独立安灯看板：安灯（Andon）不是「消息通知」，是**车间大屏上的呼叫看板**。
5. 反面教训：Laravel 单体 + Blade 模板，前端体验偏传统表单。→ 本项目采用前后端分离 + 自研工业控件。

### 2.4 sindohmes/mes4u ★61 · SpringBoot + Vue2 · 韩国 Sindoh 制造现场沉淀

由制造业企业把自家在用的 MES 开源。其 `features.md` 明确划分 4 大管理域：

```
System Management            系统管理：用户、日志
Master Data Management       主数据管理：制造主数据
Production Management        生产管理：派工 + 工票打印
Operation Execution & Monitoring  作业执行与监控：工序作业、线内检验、返修
```

**可复用经验**：

1. 「**派工单 + 工票打印**」在 v1 里被放在生产管理的核心位置——
   **CNC 车间至今仍需要纸质工票随工件流转**（贴工件上、随托盘走）。
   本系统保留「派工单」为独立实体，并提供可打印的工票视图。
2. 4 大域划分非常干净，比按部门划分更合理。本系统的后端包结构参照此思路：
   `auth / masterdata / production / quality / tooling / equipment / trace / board / report`。
3. 其 v1 明确列出「未实现清单」：质量管理、库存、实时状态看板、制造订单管理……
   说明**一个 MES 不可能一次做完**，必须按「主数据 → 派工执行 → 监控 → 质量 → 统计」的依赖顺序推进。

### 2.5 jukbot/smart-industry (IMES) ★441 · Polymer/HTML · JobShop 排产

面向**单件小批（JobShop）** 机加工厂，最大亮点在于排产：

- `Weight and Priority Calculation for JobShop`：工单权重与优先级计算
- `Production Scheduling Algorithm`：排产算法
- `Integrate Production Optimization (Linear Programming)`：接入线性规划做生产优化
- `Production Performance Board`：生产绩效看板

**可复用经验**：

1. **JobShop 与流水线是两种排产模型**。CNC 机加工绝大多数是 JobShop（一台机床换不同活），
   所以排产必须支持「按机床维度的任务队列 + 优先级插单」，而不是按流水线节拍。
2. 优先级不能只按交期，应是**加权评分**。本系统实现为：
   `优先级 = 交期紧迫度权重 + 客户等级权重 + 订单类型权重`，并允许人工插单覆盖。
3. 反面教训：项目因预算问题停更（2021），说明**技术选型要选长寿生态**（React/Vue + Spring）。

### 2.6 s-pms/SPMS-Web ★117 · Vue3 + TS + Vite

把 MES / WMS / ERP / QMS / IoTS 五系统一体化，前端为 Vue3 + ElementPlus。

**可复用经验**：它印证了「MES 天然会向 WMS/QMS/IoTS 蔓延」，
因此**主数据（物料、批次、库位）和检验数据必须从第一天就设计成可扩展的**，否则后期接 WMS 要重写。
反面参考点：其界面是典型 ElementPlus 通用后台风格（这也是本项目要刻意规避的方向）。

### 2.7 其他扫描到的项目

| 项目 | Star | 语言 | 可借鉴点 |
|---|---|---|---|
| `osess/mes` | 170 | Python/Django | Django Admin 起家，验证「先跑通再谈体验」 |
| `cloud-mes/cloud-mes` | 77 | Rails 5 | 云化 MES 的多租户设想 |
| `CaSkade-Automation/CaSkade-MES` | 44 | TS | **基于机器 Skill 语义建模 + BPMN 编排工艺**（前沿方向） |
| `IOT-CS/IOTCS` | 215 | C# | 工业网关：PLC/CNC/OPC UA/MQTT 多驱动接入，**设备接入层应与 MES 解耦** |
| `boschresearch/CNC_Machining` | 141 | Jupyter | CNC 过程监控数据集，说明**振动/电流/负载信号**是 CNC 特有可采集量 |

## 3. 横向对比矩阵

| 维度 | industry4.0-mes | free-mes | WebErpMesv2 | mes4u | IMES | **MES-MAX（本项目）** |
|---|---|---|---|---|---|---|
| 行业定位 | 通用离散 | 通用离散 | 钣金/机加/模具 | 办公设备装配 | JobShop 机加 | **CNC 数控加工** |
| 工艺路线 | ✅ 三层定额 | ✅ | ✅ | ✅ | ✅ | ✅ 工序 + 机床 + 程序号 + 定额 |
| 有限产能排产 | ✅ | ✅ | ✅ 按机床 | ✖ | ✅ 加权+LP | ✅ 机台负荷 + 优先级评分 + 甘特 |
| 报工 | ✅ | ✅ | ✅ | ✅ 派工工票 | ✅ | ✅ 触屏终端 + 自动工时/OEE |
| 设备采集 | ✖ | ✔ 网关 | ✖ | ✖ | ✖ | ✅ 状态机 + 遥测时序 + SSE 推送 |
| 刀具管理 | ✖ | ✖ | ✔ 工艺主数据 | ✖ | ✖ | ✅ 台账 + 寿命预警 + 刃磨对刀 |
| 首件/SPC | ✔ 简化 | ✔ | ✅ 检验项目 | ✔ 线内检验 | ✖ | ✅ 首件/巡检/终检 + CPK |
| 停机和原因 | ✅ stoppage | ✔ | ✔ | ✖ | ✖ | ✅ 原因字典 + OEE 时间损失 |
| 安灯 Andon | ✖ | ✖ | ✅ | ✖ | ✖ | ✅ 呼叫看板 + 分级响应 |
| 谱系追溯 | ✅ 高级谱系 | ✔ | ✅ | ✔ | ✖ | ✅ SN/批次正反向追溯 |
| 设备维保 | ✅ CMMS | ✖ | ✅ GMAO | ✖ | ✖ | ✅ 点检/保养/故障 MTTR·MTBF |
| 开箱即跑 | ✖ 极难 | ✔ | ✔ | ✔ | ✖ 停更 | ✅ H2 零依赖一条命令 |
| 工业风 UI | ✖ | ✔ 大屏 | 通用后台 | 通用后台 | Polymer | ✅ **全自研工业控件，无通用组件库** |

## 4. 调研结论：本项目的 8 条设计决策

| # | 决策 | 依据 |
|---|---|---|
| 1 | 菜单主链路：主数据 → 订单 → 排产 → 派工 → 报工 → 看板 → 质量 → 报表 | free-mes / mes4u 的现场验证序列 |
| 2 | 工时定额做「产品 → 工序 → 订单」三层继承，任务生成时**快照** | industry4.0-mes 的 cost/time-norms 插件 |
| 3 | 工单与工序任务**状态机集中建模**，禁止散落 if | industry4.0-mes 的 `states` 插件 |
| 4 | 排产采用 JobShop 模型（按机床队列 + 加权优先级插单） | IMES 的 JobShop 定位与优先级加权 |
| 5 | 不合格品处置四态闭环：返工/返修/让步接收/报废 | WebErpMesv2 的 quality-derogations |
| 6 | 刀具作为与「工序-机床」联动的对象，而非孤立台账 | WebErpMesv2 的 methods-tools |
| 7 | 追溯做成独立视图，支持正反向双向查询 | WebErpMesv2 的 production-trace |
| 8 | 默认 H2 + 自动建表 + 自动灌种子，一条命令可跑 | free-mes 开源版「部署难」的主要痛点 |

## 5. 面向 CNC 行业、开源项目普遍缺失的能力（本系统的差异化重点）

调研发现：**通用 MES 都不管数控加工的特有对象**，这正是本系统的发力点：

1. **程序号管理**：每个工序关联 NC 程序号与版本，报工时校验「机床实际执行的程序号」与工艺要求是否一致
   （`boschresearch/CNC_Machining` 与各类 DNC 实践都指向这一点：**程序错版是批量报废的头号原因**）。
2. **刀具号 / 刀补联动**：报工时记录实际使用的刀具编号与刀补号，实现「一把刀加工了哪些工件」的反向追溯。
3. **试切与首件**：CNC 小批量多品种，首件检验必须强制前置，未通过不允许批量加工。
4. **换刀 / 换型时间损失**：CNC 的停机时间中换刀与换型占比高，必须与加工停机区分统计。
5. **多轴/多坐标实时量**：主轴转速、进给倍率、各轴坐标、主轴负载、报警码，这些是 CNC 特有遥测量。
6. **毛坯齐套**：机加工的毛坯（棒料/板料/铸件）齐套直接影响能否开工，排产前必须校验。

> 以上 6 点已全部落到 MES-MAX 的数据模型与页面中，见
> [需求规格与功能架构](02-需求规格与功能架构.md) 与
> [技术架构与数据库设计](03-技术架构与数据库设计.md)。
