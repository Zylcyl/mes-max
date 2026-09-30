#!/usr/bin/env bash
# =====================================================================
#  MES-MAX 后端启动脚本（Linux / macOS）
#  用法：
#    ./run.sh                      默认 H2 文件库，首次启动自动建表并灌入演示数据
#    MES_RESET=-Dmesmax.db.reset=true ./run.sh    重置演示数据
#  令牌密钥策略同 run.cmd：环境变量 > .jwt-secret 文件 > 随机生成并落盘。
# =====================================================================
set -e
cd "$(dirname "$0")"

export MAVEN_OPTS="${MAVEN_OPTS:-} -Dfile.encoding=UTF-8"

# 令牌密钥由后端自行解析：环境变量 MES_JWT_SECRET 优先，否则读取/生成 .jwt-secret。
# 这里不做生成逻辑，保证 mvn / java -jar / IDE 三种启动方式行为一致。

if [ -n "${MES_RESET:-}" ]; then
  mvn -q clean spring-boot:run -Dspring-boot.run.jvmArguments="$MES_RESET -Dfile.encoding=UTF-8" "$@"
else
  mvn -q clean spring-boot:run "$@"
fi
