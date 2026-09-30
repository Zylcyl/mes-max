# 贡献指南

> **这个项目欢迎所有人提出问题、动手改问题。**
> 你不必是 CNC 行业的人，也不必先把整个仓库读完 ——
> 一处报错、一条逻辑漏洞、一句看不懂的文档，都值得开一个 Issue。

## 一、发现的任何问题，都请开 Issue

不必先确认「这是不是我的误解」。**看不懂本身就是问题**（说明文档没写清），
请在 [新建 Issue](https://github.com/Zylcyl/mes-max/issues/new/choose) 时选对应模板。

适合提 Issue 的情况（远不止这些）：

| 类型 | 例子 |
|---|---|
| 缺陷 | 接口返回 500、页面白屏、报工数量对不上、OEE 算错 |
| 数据正确性 | 追溯链断掉、SPC 样本取错、统计区间少了上界 |
| 安全 | 越权可读、令牌可绕过、日志里出现敏感信息 |
| 文档 | 步骤跑不通、命令写错、说明与代码不一致 |
| 设计 | UI 不符合车间实际、工业控件不好用、可访问性问题 |
| 建议 | 缺某个功能、有更好的实现方式 |

**提 Issue 请尽量带上**：复现步骤、预期结果、实际结果。
如果是界面问题，附截图或浏览器控制台报错；如果是后端问题，附启动日志或接口响应。

> 提 Issue 不需要你会写代码。只描述现象也完全可以，后面自然会有人接手。

## 二、改问题：提 Pull Request

想直接动手，**非常欢迎**。流程：

```bash
# 1. Fork 本仓库，然后克隆你自己的 fork
git clone https://github.com/<你的用户名>/mes-max.git
cd mes-max

# 2. 建分支（分支名说明你要做什么）
git checkout -b fix/report-quantity-doubled
```

改完后跑一遍自检（见下一节），再推送并开 PR：

```bash
git push origin fix/report-quantity-doubled
```

PR 描述里请写清三件事：**改了什么、为什么改、怎么验证的**。

### 小改动可以直接开 PR

错别字、注释、文档修正、明显的笔误 —— 不用先开 Issue，直接 PR 更快。

### 大改动请先开 Issue 聊一聊

新增模块、改数据库结构、调整接口契约、重构核心算法 ——
**请先开 Issue 对齐方向再动手**，避免你写完发现方向不对。

## 三、提交前的自检

本项目的所有验收结论都可复跑，脚本在 [`tools/`](tools/README.md)，**无需 `npm install`**。

```bash
# 后端：编译 + 单元测试
cd backend
mvn clean compile
mvn test                              # 16 项：JWT / OEE 口径 / 口令哈希 / 种子口令守卫

# 启动后端（默认 H2，零依赖）
mvn spring-boot:run

# 另开一个终端，在项目根目录跑验证脚本
cd ..
node tools/smoke-api.mjs              # 62 项接口冒烟
node tools/verify-fixes.mjs           # 21 项定向回归
node tools/verify-hardening.mjs       # 32 项安全与数据一致性加固回归

# 前端
cd frontend
npm install
npm run build                         # 生产构建必须通过
cd ..
node tools/validate-frontend.mjs frontend       # SFC 编译 + import 解析
node tools/check-template-scope.mjs frontend    # 模板变量作用域
```

改动涉及的验证脚本**必须跑通**。如果你新修了一个 bug，**最好补一条能复现它的断言** ——
这个仓库里每一轮修复都留了对应的回归脚本，请保持这个习惯。

## 四、代码约定

| 约定 | 说明 |
|---|---|
| **前端不引入通用 UI 组件库** | 不使用 Element Plus / Ant Design / Arco 等，全部用自研的 `ind-*` 工业控件 |
| 视觉语言 | 对标车间 HMI / SCADA / 安灯看板，不是互联网后台；遵循 [工业化 UI 设计规范](docs/04-工业化UI设计规范.md) |
| 后端分层 | `common` / `auth` / `masterdata` / `production` / `quality` / `tooling` / `equipment` / `trace` / `board` / `report` |
| 注释语言 | 中文，说明**为什么**这么做，而不是重复代码在做什么 |
| 安全底线 | 口令不得明文落库或回吐、越权必须拦截、写操作必须留痕、SQL 必须参数化 |
| 不改无关代码 | 一个 PR 只做一件事；不要顺手「优化」不相关的代码或格式 |

## 五、行为准则

- 对事不对人。指出问题时说清「什么现象、为什么是问题」，不做人身评价。
- 新手提问没有「太基础」一说。**任何人都是从看不懂开始的。**
- 评审意见是讨论，不是命令。你有不同看法，请直接说出来并给出理由。
- 不会因为「改得小」而轻视任何贡献 —— 一行文档修正和一次架构重构同样有价值。

## 六、协议

本项目采用 [MIT 协议](LICENSE)。你提交的贡献将同样以 MIT 协议授权。
