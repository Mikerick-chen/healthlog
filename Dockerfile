# ====== 多階段建置：Zeabur 部署用（繁體中文註解）======

# --- 階段一：以 Maven + JDK 21 編譯打包 ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# 先複製 pom 以利相依快取
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
# 再複製原始碼編譯（跳過測試加速部署；CI 另跑測試）
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- 階段二：精簡 JRE 執行 ---
FROM eclipse-temurin:21-jre
WORKDIR /app
# 複製打包好的 jar
COPY --from=build /app/target/health-log-1.0.0.jar app.jar
# 建立 SQLite 資料目錄（cloud profile 預設把 DB 放這裡；可掛 Volume 永久保存）
RUN mkdir -p /app/data
# 預設啟用 cloud profile（SQLite，零外部依賴，部署即可運行）
# 若要改用 PostgreSQL，於 Zeabur 把此變數改為 prod，並設定 SPRING_DATASOURCE_* 三個變數即可
ENV SPRING_PROFILES_ACTIVE=cloud
# Zeabur 會注入 PORT；Spring 已設定讀取 ${PORT:8080}
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
