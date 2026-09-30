# tools/ —— 可复现的验证脚本

> 这些脚本就是 [docs/07-测试与验收报告.md](../docs/07-测试与验收报告.md) 里所有结论的**原始执行脚本**，
> 不是事后补写的说明。全部只用 Node 标准库（`fetch` / `fs` / `child_process`），**无需 npm install**。

## 前置

| 脚本 | 前置条件 |
|---|---|
| `smoke-api.mjs`、`verify-fixes.mjs`、`verify-hardening.mjs`、`verify-sse.mjs` | 后端已启动（`http://localhost:8080/api`） |
| `verify-jwt-persist.mjs` | 后端**未**启动；脚本会自己反复启停（仅 Windows：内部使用 `java.exe` 绝对路径） |
| `validate-frontend.mjs`、`check-template-scope.mjs` | `frontend/node_modules` 已安装（用到 `@vue/compiler-sfc`） |
| `check-api-consistency.mjs`、`check-seed-data.mjs`、`project-inventory.mjs` | 无（纯静态分析） |
| `check-chart-theme.mjs` | 无（纯函数断言：浅色主题下图表换色） |
| `dev-server.mjs` | `frontend/node_modules` 已安装；后端已启动（代理 `/api`） |

Node 版本建议 18+（用到内置 `fetch`）。

## 一键跑（后端已启动时）

```bash
cd mes-max

node tools/smoke-api.mjs                # 62 项接口冒烟（含权限、空 body、写操作链路）
node tools/verify-fixes.mjs             # 21 项定向回归（报工不翻倍 / OEE 口径 / 越权 / 分页 …）
node tools/verify-hardening.mjs         # 32 项加固回归（状态机 / 序列号追溯 / SPC / 读写权限 / 登录节流）
node tools/verify-sse.mjs               # SSE 推送节奏（15 秒内应收到约 7 帧）

node tools/validate-frontend.mjs    frontend      # 53 个文件的 SFC 编译 + import 解析
node tools/check-template-scope.mjs frontend      # 42 个模板的变量作用域
node tools/check-api-consistency.mjs frontend backend/src/main/java   # 后端 227 条路由 vs 前端 111 个调用
node tools/check-seed-data.mjs      sql/seed-data.sql                 # 229 行种子数据的列数一致性
node tools/check-chart-theme.mjs                      # 图表深浅主题换色 9 项断言
node tools/project-inventory.mjs    .             # 目录树与代码量统计
```

## 单独验证「令牌跨重启仍然有效」

```bash
# 需要先停掉后端：脚本要自己启停三次以制造真实重启
node tools/verify-jwt-persist.mjs backend backend/target/mes-max-backend.jar
```

预期输出（详见 docs/07 附录第 4 节）：

```
2) 首次启动后 .jwt-secret 自动生成？ true（密钥长度 44）
4) 重启后同一个令牌访问 /auth/profile → HTTP 200   ✓ 不需要重新登录
5) 以环境变量 MES_JWT_SECRET 启动后，旧令牌 → HTTP 401   ✓ 环境变量优先
```

> 该脚本内置一条「启动前断言 8080 已释放」——没有它就会在旧实例还占着端口时
> 得到一份「重启成功」的假象（这个坑真踩过，见 docs/07）。

## 后端单元测试

```bash
cd backend
mvn test        # 16 项：JWT 签发/篡改/过期/无 exp、OEE 口径、口令哈希、种子口令守卫、业务码约定
```

## 前端开发服务器（受限环境替代方案）

```bash
node tools/dev-server.mjs          # http://localhost:5173，/api 代理到 8080
```

正常情况下直接 `cd frontend && npm run dev` 即可。`tools/dev-server.mjs` 是**受限环境的等效替代**：
某些环境禁止创建子进程，而 Vite 默认有三处依赖子进程 ——
① 用 esbuild 打包 `vite.config.js`、② 依赖预构建（optimizeDeps）也用 esbuild、③ Windows 上 `exec('net use')` 探测网络盘。
该脚本分别用「内联配置」「关闭预构建」「让 realpath 探测走早退分支」绕开它们，并把依赖指向浏览器版 ESM。
代价：没有 HMR（改代码需手动刷新），首次加载稍慢。功能与 `npm run dev` 一致。

## 说明

- 脚本里的断言都带**具体数字**（例如「差额 = 10」「size 夹到 500」），失败时能直接看出差在哪；
- `verify-jwt-persist.mjs` 会读日志文件里的 PID 来确认「确实换了进程」，
  不会把「请求打到了旧实例」误判成验证通过；
- 沙箱/受限环境里如果 Node 无法创建子进程，`verify-jwt-persist.mjs` 会失败 —— 这是环境限制，不是产品问题。
