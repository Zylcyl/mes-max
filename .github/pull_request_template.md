## 这个 PR 做了什么

<!-- 一句话概括。例如：修复报工数量翻倍。 -->

## 关联 Issue

<!-- 例如 Closes #12。没有关联 Issue 也可以，说明一下背景即可。 -->

## 为什么这么改

<!--
  说清根因，而不是只描述改动。
  「把 ×2 去掉了」不如「报工接口在写入后又被聚合查询重复统计了一次」有价值。
-->

## 怎么验证的

<!-- 贴出实际跑过的命令与结果。没跑通的项请如实说明，不要留空。 -->

- [ ] `cd backend && mvn clean compile`
- [ ] `cd backend && mvn test`
- [ ] `node tools/smoke-api.mjs`
- [ ] `node tools/verify-fixes.mjs`
- [ ] `node tools/verify-hardening.mjs`
- [ ] `cd frontend && npm run build`
- [ ] `node tools/validate-frontend.mjs frontend`

<!-- 涉及的验证脚本必须跑通；与此改动无关的可注明「不适用」。 -->

## 是否补了回归断言

<!--
  这个仓库的习惯是：修一个 bug，就留一条能复现它的断言。
  没有补的话，说明一下原因（例如属于纯文档改动）。
-->

- [ ] 已在 `tools/` 或 `backend/src/test/` 补上能复现此问题的断言
- [ ] 不适用（纯文档 / 纯样式 / 其它，理由：）

## 检查

- [ ] 改动只针对这一个问题，没有顺手改无关代码
- [ ] 前端未引入 Element Plus / Ant Design / Arco 等通用组件库
- [ ] 涉及安全的部分（口令、越权、日志脱敏、SQL 参数化）已自查
- [ ] 新增/修改的配置项已在 `README.md` 或 `docs/` 中说明
