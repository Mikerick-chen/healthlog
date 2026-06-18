# Zeabur 部署指南

本專案已針對雲端部署調整（不再只能本機跑）：
- 伺服器埠改讀環境變數 `PORT`（Zeabur 會自動注入），並綁定 `0.0.0.0`。
- 以 Spring profiles 區分：本機 `dev`（SQLite，零設定）／正式 `prod`（PostgreSQL）。
- 附 `Dockerfile`（多階段建置），Zeabur 會自動偵測並建置。

---

## 一、前置：把專案推上 Git

Zeabur 從 Git repo 部署。請將 `v1/` 內容推到 GitHub（或 GitLab）。
> 注意 `.dockerignore` 已排除 `target/`、`*.db`、`*.log`，不會把本機資料庫帶上雲。

## 二、在 Zeabur 建立服務

1. 建立 Project → Add Service → **Deploy from Git**，選擇本 repo。
2. Zeabur 偵測到 `Dockerfile` 後自動建置（多階段：Maven 編譯 → JRE 執行）。
3. 再 Add Service → **PostgreSQL**（Zeabur 內建資料庫）。

## 三、設定環境變數（關鍵）

到 Java 服務的 **Variables** 頁，新增以下三個（值用 Zeabur 變數引用語法指向 PostgreSQL 服務）：

| 變數 | 建議值（引用 Postgres 服務） |
|-|-|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DATABASE}` |
| `SPRING_DATASOURCE_USERNAME` | `${POSTGRES_USERNAME}` |
| `SPRING_DATASOURCE_PASSWORD` | `${POSTGRES_PASSWORD}` |

> `SPRING_PROFILES_ACTIVE=prod` 已寫死在 Dockerfile，不需另外設定。
> `PORT` 由 Zeabur 自動注入，亦不需手動設定。
> 若你的 Postgres 變數名稱不同，`application-prod.properties` 也會自動退而嘗試 `POSTGRES_*` / `PG*`，但直接設定上表三個最保險。

## 四、啟動與驗證

- 部署完成後 Zeabur 會給一個網址（Networking → Generate Domain）。
- 首次啟動會自動建立資料表並寫入 90 天種子資料（決策樹門檻仍由資訊增益實測決定）。
- 開網址即為健康管理平台首頁；`/assessment`、`/health-logs` 等 API 同網域可用。

---

## 五、本機開發（不變）

```bash
mvn spring-boot:run         # 預設 dev profile，使用 SQLite，零設定
# → http://localhost:8080
```

## 六、已知部署注意事項

1. **資料表自動建立**：目前用 `spring.jpa.hibernate.ddl-auto=update`。正式長期維運建議改用 Flyway/Liquibase 做版本化遷移（列為後續加值）。
2. **種子資料**：僅在資料表為空時寫入；正式環境第一次部署會自動帶入示範資料，如不需要可於 `SeedDataService` 加開關。
3. **PDF 中文字型**：已將 `cjk.ttf` 打包進 jar，Linux 容器也能正確輸出中文，不依賴系統字型。
4. **無登入機制**：目前為單人使用情境，未含帳號/權限；若要多人或公開，建議再加上身分驗證。
