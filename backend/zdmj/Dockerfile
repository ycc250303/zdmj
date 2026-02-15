# 使用多阶段构建
# 阶段1: 构建应用
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# 复制 pom.xml 和源代码
COPY pom.xml .
COPY src ./src

# 构建应用（跳过测试，因为CI阶段已经运行过）
RUN mvn clean package -DskipTests

# 阶段2: 运行应用
FROM eclipse-temurin:21-jre-alpine
# 设置工作目录为 /opt/zdmj
WORKDIR /opt/zdmj

# 设置时区为Asia/Shanghai（UTC+8）
# 这确保JVM和应用程序使用正确的时区
ENV TZ=Asia/Shanghai
RUN apk add --no-cache tzdata && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone

# 创建非root用户
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 从构建阶段复制jar文件到 /opt/zdmj 目录
COPY --from=build /app/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 健康检查（可选，如果后端有健康检查接口可以启用）
# HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
#   CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/health || exit 1

# 启动应用
# -Duser.timezone=Asia/Shanghai: 显式设置JVM时区（与ENV TZ保持一致）
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
