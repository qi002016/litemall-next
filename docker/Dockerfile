# L8：微服务通用镜像（一个 Dockerfile 覆盖 12 个可运行模块，用 MODULE 区分）
#
# 构建上下文 = backend/litemall-next（父 POM 所在目录），因此能读到各模块 target 下的 jar。
# 用法：
#   mvn -B -DskipTests package
#   docker build -f docker/Dockerfile --build-arg MODULE=litemall-gateway -t litemall-gateway:local .
#
# 说明：本文件不在 maven 构建内执行 docker build（不引入 jib/docker-maven-plugin 等新构建范式），
# 由 docker-compose / CI 负责编排。

FROM eclipse-temurin:25-jre

# 运行模块：litemall-{gateway,user,goods,marketing,order,pay,message,storage,admin}
ARG MODULE
ARG JAR_FILE

# JAR_FILE 未显式传入时按 MODULE 推导；两者都缺则给出可读错误而不是"找不到文件"
RUN test -n "${MODULE}${JAR_FILE}" || (echo "构建时必须传 --build-arg MODULE=litemall-xxx" && exit 1)

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Duser.timezone=Asia/Shanghai -Dfile.encoding=UTF-8" \
    MODULE=${MODULE} \
    JAR_PATH=""

WORKDIR /app

# 优先使用显式 JAR_FILE，否则用 MODULE 推导的路径
COPY ${JAR_FILE:-${MODULE}/target/${MODULE}.jar} /app/app.jar

# 业务端口由 compose/环境决定；这里只声明文档端口
EXPOSE 8000-8008

# 可观测性：容器内以 stdout 输出日志（logging.file.path 由 HM_LOG_FILE_PATH 覆盖为空 → 不落文件）
ENTRYPOINT ["/bin/sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
