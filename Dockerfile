# ---- 第一階段：build（有 Maven 與 JDK，只用來編譯）----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# 先只複製 pom.xml 下載依賴：pom 沒變時這層會吃快取，重 build 快很多
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# 測試已在 CI 的 build-test job 跑過，這裡只負責打包
RUN mvn -B -q package -DskipTests

# ---- 第二階段：runtime（只放 JRE 與 jar，不帶編譯工具）----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 用非 root 使用者執行；USER 用數字 UID，K8s 的 runAsNonRoot 才能驗證
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
COPY --from=build /src/target/rack-monitor.jar /app/app.jar
USER 10001

EXPOSE 8080
# 依容器記憶體上限自動調整 heap，避免被 K8s 因超用記憶體砍掉
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
