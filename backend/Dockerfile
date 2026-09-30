# =====================================================================
#  后端镜像：多阶段构建（编译 -> 运行）
#
#  ⚠️ 构建上下文必须是**项目根目录**，不是 backend/：
#      docker build -f backend/Dockerfile -t mes-max-backend .
#      或由 docker-compose.yml 指定 context: . / dockerfile: backend/Dockerfile
#  原因：SQL 建表脚本在仓库根的 sql/ 下，而 Docker 的 COPY 不允许越出构建上下文
#  （曾写成 COPY ../sql 且 context 为 ./backend，构建必然失败）。
# =====================================================================
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY backend/src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms512m -Xmx1024m -Dfile.encoding=UTF-8"

COPY --from=build /build/target/mes-max-backend.jar ./mes-max-backend.jar
# SQL 脚本随镜像发布：程序按 ./sql/schema.sql 与 ./sql/seed-data.sql 查找
COPY sql /app/sql

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar mes-max-backend.jar"]
