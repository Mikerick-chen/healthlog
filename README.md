# 智慧健康管理平台 v2

決策樹健康風險評估 + 多使用者 + 智能引擎（NLP / ROI / 基準線 / 教練 / 診療室 / 環境關聯）
+ 身體數據 / 生命徵象 / 日記 / 趨勢分析 / Excel・醫生級 PDF 報告。
Java 21 + Spring Boot 3.4 + JPA｜本機 SQLite、雲端 SQLite(cloud) 或 PostgreSQL(prod)。

> ⭐ 所有「智能/NLP/診斷」皆為自寫的規則與統計演算法，**未呼叫任何外部 AI API**。

## v2 智能功能一覽

| 功能 | 說明 | 端點 |
|---|---|---|
| 多使用者 | 以名稱切換，資料完全隔離；新使用者自動空白起步 | `/users`（X-User-Id 標頭） |
| 🗣️ 語意智能日誌 | 白話文自動抽取健康關鍵字與數值（咖啡因/睡眠/症狀…） | `POST /nlp/parse` |
| 💡 健康 ROI | 行為(步數/喝水) vs 結果(體重/心率) 本週 vs 上週 | `GET /insights/roi` |
| 📌 動態專屬基準線 | 用你自己的數據算正常範圍(z-score)，偏離才警示 | `GET /insights/baseline` |
| 🤖 自適應情緒教練 | 依生理數據切換溫柔/嚴格/慶祝語氣 | `GET /insights/coach` |
| 🌦️ 環境關聯 | Open-Meteo 抓天氣/氣壓/PM2.5，與症狀交叉揪地雷 | `GET /environment` |
| 🏥 智慧診療室 | 症狀→可能病因/科別/自我照護，結合個人數據 | `POST /clinic/diagnose` |
| 📄 醫生級 PDF | 綜合評估+生命徵象(含參考範圍)+基準線+決策樹+醫囑+教練評語 | `GET /reports/pdf` |

---

## 📁 專案結構

```
v1/
├── README.md              ← 你在這裡（如何執行、結構說明）
├── pom.xml                ← Maven 建置設定
├── Dockerfile             ← Zeabur 部署用（多階段建置）
├── .dockerignore          ← 建置時排除的檔案
├── .gitignore             ← Git 忽略清單
├── src/                   ← 原始碼（後端 Java + 前端靜態檔 + 中文字型）
│   └── main/
│       ├── java/com/healthlog/   ← controller / service / repository / entity / dto
│       └── resources/
│           ├── static/           ← 前端 index.html / app.js / styles.css
│           ├── fonts/cjk.ttf     ← PDF 中文字型（請保留，勿刪）
│           └── application*.properties  ← dev=SQLite / prod=PostgreSQL
├── docs/                  ← 文件
│   ├── 交付說明.md
│   └── ZEABUR部署指南.md
└── _可刪除_部署不需要/      ← ⚠️ 可整個刪除（見下方說明）
```

### ⚠️ `_可刪除_部署不需要/` 是什麼

裡面是**部署 Zeabur 不需要、且佔掉 90+ MB** 的東西，可整個資料夾刪掉：
- `target/`：本機編譯產出（Zeabur 會用 Dockerfile 重新編譯，不需上傳）— 這是超過 50MB 的主因。
- `healthlog.db`：本機 SQLite 測試資料庫。
- `app.log`、`health_logs_seed.sql`、`package-lock.json`：執行期/暫存檔。

> 刪掉後若想在本機重跑，`mvn` 會自動重建 `target/` 與 `healthlog.db`，不影響任何功能。

---

## 🖥️ 本機執行（如何開啟後端）

需求：**JDK 21 以上**、**Maven**（你的環境已具備 JDK 25 + Maven 3.9）。

在本資料夾（`v1/`，即有 `pom.xml` 的這層）開啟終端機，擇一執行：

```bash
# 方式 A：開發模式（最簡單，會自動編譯並啟動）
mvn spring-boot:run

# 方式 B：先打包再執行
mvn clean package
java -jar target/health-log-1.0.0.jar
```

啟動成功後（log 出現 `Started HealthLogApplication`）：

👉 打開瀏覽器 **http://localhost:8080** 即為前端首頁，後端 API 同網域。

- 本機預設用 `dev` profile（SQLite，零設定，資料存在 `healthlog.db`，重啟仍在）。
- 首次啟動會自動寫入 90 天種子資料並依資訊增益計算決策樹門檻。
- 停止：在終端機按 `Ctrl + C`。

> PowerShell 小提醒：請先 `cd` 到此資料夾再執行上述指令。

---

## ☁️ 部署到 Zeabur

完整步驟見 [docs/ZEABUR部署指南.md](docs/ZEABUR部署指南.md)。**最簡單（推薦）**：
1. 先刪除 `_可刪除_部署不需要/`（讓上傳內容 < 50MB）。
2. 推上 GitHub → Zeabur 從 Git 部署（自動偵測 `Dockerfile`）。
3. **不需要建立資料庫、不需要設定任何環境變數** —— 預設用 SQLite（`cloud` profile）部署即可運行。
4. Generate Domain 取得網址即可使用。

> 想用 PostgreSQL 或讓資料永久保存？見部署指南的「方案 B」與「掛載 Volume」。
> 之前 `Unable to determine Dialect` 啟動崩潰的問題（預設連 Postgres 但沒資料庫）已修正。
