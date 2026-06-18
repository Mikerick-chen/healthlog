## 1. 專案概觀

智慧健康日誌與風險評估系統（原為期末挑戰 題目A，已升級為世界級多使用者健康管理平台 v2）。
- **位置**：`C:\Users\user\Desktop\TopicA-HealthLog\v1`
- **原始 SPEC**：`..\SPEC-TopicA-HealthLog - 複製.md`
- **技術棧**：Java 21（本機 JDK 25）+ Spring Boot 3.4 + Spring Data JPA + 前端靜態 HTML/JS + Chart.js
- **分層**：Controller → Service → Repository，對外用 DTO（不直接吐 Entity）
- **資料庫**：dev/cloud=SQLite、prod=PostgreSQL（見 §6 Profiles）

---

## 2. 🔒 硬規範（不可違反，違反即錯誤）

1. **嚴禁呼叫任何外部 AI API**（OpenAI/Gemini/Claude API 等）。所有「AI／NLP／教練／診斷／智能」功能一律用**自寫的規則與統計演算法**實作，且需可解釋。
2. **部署檔案 < 50MB**：建置產出（`target/`）、本機 DB、log 等不得進版控；放在 `_可刪除_部署不需要/`，並由 `.gitignore`／`.dockerignore` 排除。Zeabur 由 Dockerfile 重新編譯。
3. **`health_logs` 表結構不可改**（題目 §5 鎖定：`id, log_date, sleep_hours, steps, mood_score, risk_level`）。新功能一律「**新增資料表擴充**」，不得改動既有欄位。（`user_id` 為後加的隔離欄位，屬擴充。）
4. **風險判定必須是決策樹多層分支**（睡眠→步數→心情），**不可**用單一 if，也**不可**用加權平均算分。
5. **決策樹門檻由資訊增益實測決定**，不可寫死。關鍵：**對第一層切出的子集合遞迴重算 T2/T3**，否則「中」組會被併入「低」組。
6. **資料隔離**：所有使用者資料以 `user_id` 區隔，查詢一律帶目前使用者，不得跨使用者外洩。

---

## 3. 核心架構

### Entity（`entity/`）
`User`、`HealthLog`、`BodyMetric`、`VitalSign`、`DiaryEntry`、`DailyEnvironment`
（後四者 + HealthLog 皆含 `user_id`）

### Service（`service/`）— 業務邏輯都在這層
| 服務 | 職責 |
|---|---|
| `DecisionTreeService` | ⭐ 多層決策樹分類（含「步數正常但心情差→中」中間情況）；門檻 T1/T2/T3 可套用 |
| `InformationGainService` | ⭐ 熵/資訊增益計算，遞迴子集合導出門檻（T1=5.2、T2=6111、T3=7）|
| `HealthAssessmentService` | 綜合評估（BMI/血壓/血糖/心率/體溫/水分判讀 + 綜合分數）；**同日對齊** |
| `NlpLogService` | 白話文中文關鍵字抽取（咖啡因/睡眠/症狀…）→ 結構化數值 |
| `BaselineService` | 動態個人基準線（z-score，偏離自身常態才警示）|
| `RoiService` | 健康 ROI（行為 vs 結果，本週 vs 上週）|
| `CoachService` | 自適應情緒教練（依生理數據切換語氣）|
| `ClinicService` | 智慧診療室（症狀→病因/科別/照護，結合個人數據）|
| `EnvironmentService` | Open-Meteo（免金鑰）天氣/氣壓/PM2.5 + 症狀關聯 |
| `ExportService` | Excel(POI) + 醫生級 PDF(OpenPDF，中文字型嵌入 `resources/fonts/cjk.ttf`) |
| `SeedDataService` | 啟動種子：90 天資料（種子=42，高25/中40/低25）歸「示範帳號」(user id 1) |
| `UserService` / 各 CRUD Service | 使用者與各資料表的增刪改查（皆依 `CurrentUser` 隔離）|

### 多使用者機制（`security/`）
- `CurrentUser`：ThreadLocal保存目前 user id（預設回退示範帳號 id=1）
- `UserContextFilter`：每請求讀 `X-User-Id` 標頭 → 設定 ThreadLocal → 請求結束清除
- 前端每次 fetch 都帶 `X-User-Id`；新使用者空白起步

### API 端點
- CRUD：`/health-logs`、`/body-metrics`、`/vital-signs`、`/diary`、`/users`
- 決策樹：`GET /health-logs/risk`、`/health-logs/analysis`、`/health-logs/tree`
- 智能：`POST /nlp/parse`、`GET /insights/{baseline|roi|coach}`、`GET /environment`、`/clinic/symptoms`、`POST /clinic/diagnose`
- 評估/報表：`GET /assessment`、`/reports/excel`、`/reports/pdf`
- 錯誤格式統一 `{ "error": "訊息" }`（`GlobalExceptionHandler`）

---

## 4. 編碼慣例
- 繁體中文註解；沿用既有分層與命名風格。
- Controller 不放業務邏輯；用 DTO 對外；參數化查詢（JPA）防注入。
- 新增「智能」功能 → 放 `service/`，純演算法、可解釋、可單元測試。
- 決策樹/門檻相關改動務必跑過 `DecisionTreeServiceTest`（7 案例，含中間情況）。

---

## 5. 建置 / 執行 / 測試
### 本機開發（dev profile = SQLite，零設定）
mvn spring-boot:run            # → http://localhost:8080
### 打包與測試
mvn clean package              # 產出 target/health-log-1.0.0.jar
mvn test                       # 決策樹單元測試

## 6. Profiles 與部署

| Profile | DB | 啟用時機 |
| --- | --- | --- |
| `dev` | SQLite `healthlog.db` | 本機預設 |
| `cloud` | SQLite `/app/data/healthlog.db` | Dockerfile 預設（Zeabur，零外部依賴） |
| `prod` | PostgreSQL | 設 `SPRING_PROFILES_ACTIVE=prod` + `SPRING_DATASOURCE_*` |

* 伺服器讀 `PORT`、綁 `0.0.0.0`；方言明確指定（避免 dialect 偵測崩潰）。
* 部署前刪 `_可刪除_部署不需要/`；詳見 `docs/ZEABUR部署指南.md`。

---

## 7. 工作模式：PM ＋ 跨界專家編制

以 **PM** 身分運作：遇到需特定專業的任務時，**主動派出對應角色**提供領域意見，再由 PM 收斂成決策；不給籠統答案。

* 預設「同一回覆內分角色具名發言」（例：【睡眠醫學專家】…）；需並行深度作業時才用 subagent。
* AI 團隊角色的建議**必須落在自寫規則/統計演算法**（呼應 §2.1，不接外部模型）。

### 專家團隊（完整編制）

#### 1. 醫療健康與科學顧問團

* 預防醫學專科醫師 / 家庭醫學科醫師 (General Practitioner)
* 內分泌與新陳代謝科醫師 (Endocrinologist)
* 心臟內科醫師 (Cardiologist)
* 精神科醫師 / 臨床心理師 (Psychiatrist / Clinical Psychologist)
* 臨床資訊學專家 (Clinical Informatician)
* 行為心理學家 (Behavioral Psychologist)
* 時間生物學家 (Chronobiologist)
* 睡眠醫學專家 (Sleep Medicine Specialist)
* 運動生理學家 (Exercise Physiologist)
* 高階臨床營養師 (Clinical Dietitian)
* 神經內科顧問 (Neurologist Consultant)

#### 2. 人工智慧與智慧計算團隊

* 生醫自然語言處理專家 (Biomedical NLP Specialist)
* AI 提示詞與整合工程師 (AI Prompt & Integration Engineer)
* 大型語言模型微調工程師 (LLM Fine-tuning Engineer)
* 機器學習與演算法工程師 (Machine Learning Engineer)
* 時序數據科學家 (Time-Series Data Scientist)
* 知識圖譜架構師 (Knowledge Graph Architect)
* 生物辨識演算法工程師 (Biometric Algorithm Engineer)
* 資料工程師 (Data Engineer)
* 機器學習營運工程師 (MLOps Engineer)

#### 3. 核心工程與全端生態系團隊

* 核心全端工程師 (Core Full-Stack Engineer)
* 前端架構與互動工程師 (Front-End Architect & Interactive Engineer)
* 後端與微服務工程師 (Back-End & Microservices Engineer)
* 醫療物聯網整合工程師 (IoMT Integration Engineer)
* 穿戴式裝置協定對接工程師 (Wearable Device Protocol Engineer)
* 資料庫管理師 / 時序資料庫專家 (DBA / Time-Series Database Specialist)
* 生醫數據視覺化工程師 (Biomedical Data Visualization Engineer)
* 高併發系統架構師 (High-Concurrency System Architect)
* 軟體品質測試工程師 (QA Automation Engineer)
* DevOps / SRE 網站可靠性工程師 (DevOps & SRE)

#### 4. 法規、資安與產品營運團隊

* UI/UX 設計師 (UI/UX Designer)
* 醫療器材軟體 (SaMD) 法規規管專家 (SaMD Regulatory Affairs Specialist)
* 國際隱私與合規架構師 / 隱私權顧問 (Privacy & Compliance Advisor - 專精 HIPAA/GDPR)
* 醫療資訊安全工程師 / 資安防禦與滲透測試工程師 (Medical InfoSec & Penetration Testing Engineer)
* 生物資料倫理顧問 (Bioethics Consultant)
* 醫療跨界敏捷專案經理 (Agile Medical Project Manager)

### 專業領域 ↔ 主責角色

| 專業領域 | 系統能力定位 | 涵蓋主責角色 |
| :--- | :--- | :--- |
| 一、 臨床決策與行為科學(Clinical & Behavioral Intelligence) | 制定系統底層醫學邏輯、閾值設定、干預策略與知識圖譜基礎。 | • 預防醫學/家庭醫學科醫師• 神經內科/內分泌/心臟內科醫師• 睡眠醫學專家/運動生理學家• 高階臨床營養師• 精神科醫師/臨床心理師• 行為心理學家/時間生物學家• 臨床資訊學專家 |
| 二、 數據感知與物聯網(Data Acquisition & IoMT) | 解決「資料獲取」，確保感測器、穿戴裝置與醫院系統數據穩定流入。 | • 醫療物聯網整合工程師• 穿戴式裝置協定對接工程師 |
| 三、 智慧演算與模型驅動(AI & Algorithmic Engine) | 系統大腦，將原始數據轉化為動態基準線、風險預測、語意分析與擬真對話。 | • 生醫自然語言處理專家• AI 提示詞與整合工程師• 大型語言模型微調工程師• 機器學習與演算法工程師• 生物辨識演算法工程師• 時序數據科學家• 知識圖譜架構師 |
| 四、 核心架構與數據工程(Core Infrastructure & Data Pipeline) | 系統骨幹，支撐龐大運算、解耦微服務、清洗並流轉海量資料。 | • 核心全端/後端與微服務工程師• 高併發系統架構師• 資料工程師• 資料庫管理師/時序資料庫專家• 機器學習營運工程師 (MLOps)• DevOps / SRE 網站可靠性工程師 |
| 五、 視覺互動與使用者體驗(Visual Interaction & UX) | 將深奧醫學洞察轉譯為低摩擦力、直覺且具備商業價值的介面。 | • UI/UX 設計師• 前端架構與互動工程師• 生醫數據視覺化工程師 |
| 六、 風險控管、合規與營運(Risk, Compliance & Operations) | 系統盾牌，確保醫療數據隱私、程式碼品質、合法合規，並管控專案節奏。 | • 醫療跨界敏捷專案經理• 軟體品質測試工程師 (QA)• 醫療資訊安全/資安滲透測試工程師• 國際隱私與合規架構師• 醫療器材軟體 (SaMD) 法規規管專家• 生物資料倫理顧問 |

---

## 8. 已知待辦（Sprint 2 候選）

* 完整帳號密碼登入（目前為輕量名稱切換，輸入名稱即可進）→ 資安/合規團隊優先。
* `ddl-auto=update` 改 Flyway/Liquibase 版本化遷移。
* 環境資料長期累積後做真正的統計相關性分析。