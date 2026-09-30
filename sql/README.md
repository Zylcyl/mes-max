# SQL 脚本说明

| 文件 | 用途 |
|---|---|
| `schema.sql` | 建表脚本，41 张表 + 索引。全部使用 `CREATE TABLE IF NOT EXISTS`，可重复执行 |
| `seed-data.sql` | CNC 车间**静态主数据**种子：车间/工作中心/机床、物料/BOM、工序/工艺路线、客户、员工、班次、刀具台账、点检项、保养计划、毛坯批次 |

## 两个脚本的分工

**为什么业务流水（订单、报工、停机、遥测、检验、追溯）不在这里？**

因为这些数据之间存在强一致性约束：

- 报工数量 → 汇总成工序任务的完工数量 → 再汇总成订单完工数量，三级必须对得上；
- 追溯记录必须引用**真实存在**的机床、NC 程序号、刀具编号与毛坯批次；
- 停机记录要能归因到当时正在加工的那张工单。

手写 SQL 几乎必然出现对不上的数据。因此这部分由后端
`com.mesmax.boot.DemoDataInitializer` 使用固定随机种子（`20260921`）确定性生成，
每次初始化得到完全相同的数据集，便于演示与回归测试。

## 执行方式

正常启动后端时**不需要手工执行**：`SqlBootstrap` 会在启动时自动定位并执行这两个脚本
（建表始终执行，种子数据仅在空库时执行）。

手工执行（例如用 MySQL 客户端初始化生产库）：

```bash
mysql -uroot -p mes_max < schema.sql
mysql -uroot -p mes_max < seed-data.sql
```

## 需要重置演示数据时

```bash
cd backend
set MES_RESET=-Dmesmax.db.reset=true && run.cmd      # Windows
MES_RESET=-Dmesmax.db.reset=true ./run.sh             # Linux/macOS
```

程序启动时会执行 `DROP ALL OBJECTS` 清空库，然后重新建表并灌入种子 + 演示流水。
