# Zeabur 部署指南

本專案已針對雲端部署調整：
- 伺服器埠改讀環境變數 `PORT`（Zeabur 自動注入），並綁定 `0.0.0.0`。
- 附 `Dockerfile`（多階段建置），Zeabur 自動偵測並建置。
- **預設用 `cloud` profile（SQLite）→ 不需另外建立資料庫服務，部署即可運行。**

> ⚠️ 之前部署失敗（`Unable to determine Dialect` / `Application run failed`）的原因：
> 舊版預設 `prod`(PostgreSQL)，但沒有連到資料庫服務就會在啟動時崩潰。
> 現已改為預設 SQLite，零外部依賴，這個問題已解決。

---

## 方案 A：最簡單（SQLite，推薦先用這個）

**完全不需要建立資料庫服務、不需要設定任何環境變數。**

1. 先刪除 `_可刪除_部署不需要/` 整個資料夾（讓上傳 < 50MB）。
2. 把 `v1/` 推上 GitHub。
3. Zeabur → Add Service → **Deploy from Git** → 選此 repo。
4. Zeabur 偵測 `Dockerfile` 自動建置、啟動。
5. Networking → **Generate Domain** 取得網址，打開即可使用。

- 首次啟動自動建表 + 寫入 90 天種子資料 + 用資訊增益算決策樹門檻。
- 資料存在容器內 `/app/data/healthlog.db`。

### 想讓資料「重新部署也不消失」？（選用）
為此服務掛載一個 **Volume**，掛載路徑設為 **`/app/data`** 即可。不掛則每次重新部署會重置（作業／展示足夠）。

---

## 方案 B：改用 PostgreSQL（需要永久、可擴充資料庫時）

1. 在同一 Project Add Service → **PostgreSQL**。
2. 到 Java 服務的 **Variables**，新增 4 個變數：

| 變數 | 值 |
|-|-|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://${POSTGRES_HOST}:${POSTGRES_PORT}/${POSTGRES_DATABASE}` |
| `SPRING_DATASOURCE_USERNAME` | `${POSTGRES_USERNAME}` |
| `SPRING_DATASOURCE_PASSWORD` | `${POSTGRES_PASSWORD}` |

3. 重新部署。`prod` profile 會改連 PostgreSQL（方言已明確指定，不會再出現 dialect 錯誤）。

> `${POSTGRES_*}` 是 Zeabur 變數引用語法，會自動帶入你那個 PostgreSQL 服務的實際值。
> 若你的 Postgres 變數名稱不同，`application-prod.properties` 也會自動退而嘗試 `PG*`。

---

## 本機開發（不變）

```bash
mvn spring-boot:run        # 預設 dev profile（SQLite，存 healthlog.db）
# → http://localhost:8080
```

詳見專案根目錄 `README.md`。

---

## Profile 一覽

| Profile | 用途 | 資料庫 | 何時啟用 |
|-|-|-|-|
| `dev` | 本機開發 | SQLite `healthlog.db` | 預設（`mvn spring-boot:run`） |
| `cloud` | 雲端零設定 | SQLite `/app/data/healthlog.db` | Dockerfile 已預設（Zeabur 方案 A） |
| `prod` | 正式 DB | PostgreSQL | 自行設 `SPRING_PROFILES_ACTIVE=prod`（方案 B） |

## 已知注意事項
1. 自動建表用 `ddl-auto=update`；長期維運建議改 Flyway/Liquibase。
2. 種子資料僅在資料表為空時寫入。
3. PDF 中文字型 `cjk.ttf` 已打包進 jar，Linux 容器也能正確輸出中文。
4. 目前為單人情境、無登入機制；若要公開多人使用建議加上身分驗證。
