package com.healthlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthlog.dto.EnvironmentInfo;
import com.healthlog.entity.DailyEnvironment;
import com.healthlog.entity.DiaryEntry;
import com.healthlog.entity.User;
import com.healthlog.repository.DailyEnvironmentRepository;
import com.healthlog.repository.DiaryEntryRepository;
import com.healthlog.repository.UserRepository;
import com.healthlog.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

/**
 * 「跨維度環境關聯」服務（§3b）。
 *
 * 由 Open-Meteo（免金鑰、非 AI API）抓取當日天氣、氣壓、濕度、PM2.5，
 * 保存後與使用者健康日記的症狀（偏頭痛/失眠等）交叉比對，主動揪出「隱藏健康地雷」。
 * 外部呼叫有逾時與容錯，失敗不會中斷服務。
 */
@Service
public class EnvironmentService {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentService.class);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();

    private final DailyEnvironmentRepository envRepo;
    private final DiaryEntryRepository diaryRepo;
    private final UserRepository userRepo;
    private final ObjectMapper mapper = new ObjectMapper();

    public EnvironmentService(DailyEnvironmentRepository envRepo, DiaryEntryRepository diaryRepo, UserRepository userRepo) {
        this.envRepo = envRepo;
        this.diaryRepo = diaryRepo;
        this.userRepo = userRepo;
    }

    public EnvironmentInfo today(Double lat, Double lon) {
        Long uid = CurrentUser.id();
        EnvironmentInfo info = new EnvironmentInfo();
        info.date = LocalDate.now();

        // 決定座標：參數 > 使用者設定 > 預設台北
        User user = userRepo.findById(uid).orElse(null);
        double latitude = lat != null ? lat : (user != null && user.getLatitude() != null ? user.getLatitude() : 25.04);
        double longitude = lon != null ? lon : (user != null && user.getLongitude() != null ? user.getLongitude() : 121.56);
        info.location = (user != null && user.getLocation() != null) ? user.getLocation()
                : String.format("%.2f, %.2f", latitude, longitude);

        try {
            // 1) 天氣 + 氣壓 + 濕度
            JsonNode weather = get("https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current=temperature_2m,surface_pressure,relative_humidity_2m,weather_code&timezone=auto")
                    .path("current");
            info.temperature = weather.path("temperature_2m").isMissingNode() ? null : weather.path("temperature_2m").asDouble();
            info.pressure = weather.path("surface_pressure").isMissingNode() ? null : weather.path("surface_pressure").asDouble();
            info.humidity = weather.path("relative_humidity_2m").isMissingNode() ? null : weather.path("relative_humidity_2m").asDouble();
            info.weatherText = weatherText(weather.path("weather_code").asInt(-1));

            // 2) PM2.5
            JsonNode air = get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + latitude
                    + "&longitude=" + longitude + "&current=pm2_5&timezone=auto").path("current");
            info.pm25 = air.path("pm2_5").isMissingNode() ? null : air.path("pm2_5").asDouble();

            info.available = true;
            persist(uid, info);
            buildAdvisories(info);
            correlateSymptoms(uid, info);
        } catch (Exception e) {
            log.warn("環境資料抓取失敗：{}", e.getMessage());
            info.available = false;
            info.message = "目前無法取得環境資料（可能是網路或外部服務暫時不可用），稍後再試。";
        }
        return info;
    }

    private JsonNode get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(6)).GET().build();
        HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("HTTP " + resp.statusCode());
        return mapper.readTree(resp.body());
    }

    private void persist(Long uid, EnvironmentInfo info) {
        DailyEnvironment e = envRepo.findByUserIdAndRecordDate(uid, info.date).orElseGet(DailyEnvironment::new);
        e.setUserId(uid);
        e.setRecordDate(info.date);
        e.setLocation(info.location);
        e.setTemperature(info.temperature);
        e.setPressure(info.pressure);
        e.setHumidity(info.humidity);
        e.setPm25(info.pm25);
        e.setWeatherText(info.weatherText);
        envRepo.save(e);
    }

    /** 環境健康提醒 */
    private void buildAdvisories(EnvironmentInfo info) {
        if (info.pm25 != null) {
            if (info.pm25 >= 54) info.advisories.add(new EnvironmentInfo.Advisory("danger",
                    "PM2.5 " + info.pm25 + " µg/m³ 對所有人不健康，避免戶外運動、外出戴口罩。"));
            else if (info.pm25 >= 35) info.advisories.add(new EnvironmentInfo.Advisory("warn",
                    "PM2.5 " + info.pm25 + " µg/m³ 偏高，過敏與呼吸道敏感者減少戶外活動。"));
        }
        if (info.pressure != null && info.pressure < 1005)
            info.advisories.add(new EnvironmentInfo.Advisory("warn",
                    "氣壓偏低（" + info.pressure + " hPa），偏頭痛/關節敏感者今天請特別留意。"));
        if (info.temperature != null) {
            if (info.temperature >= 32) info.advisories.add(new EnvironmentInfo.Advisory("warn",
                    "高溫 " + info.temperature + "°C，多補水、避免正午曝曬以防中暑。"));
            else if (info.temperature <= 12) info.advisories.add(new EnvironmentInfo.Advisory("warn",
                    "低溫 " + info.temperature + "°C，注意保暖，心血管疾病者尤須當心。"));
        }
        if (info.humidity != null && info.humidity >= 85)
            info.advisories.add(new EnvironmentInfo.Advisory("info",
                    "濕度高（" + info.humidity + "%），體感悶熱，過敏體質注意黴菌與塵蟎。"));
        if (info.advisories.isEmpty())
            info.advisories.add(new EnvironmentInfo.Advisory("info", "今日環境條件大致良好，適合戶外活動。"));
    }

    /** 與近 14 天日記症狀交叉，揪出隱藏地雷 */
    private void correlateSymptoms(Long uid, EnvironmentInfo info) {
        List<DiaryEntry> recent = diaryRepo.findByUserIdOrderByEntryDateDescIdDesc(uid);
        String symptomBlob = recent.stream().limit(14)
                .map(d -> (d.getSymptomTags() == null ? "" : d.getSymptomTags()) + (d.getContent() == null ? "" : d.getContent()))
                .reduce("", String::concat);

        if (info.pressure != null && info.pressure < 1005
                && (symptomBlob.contains("頭痛") || symptomBlob.contains("偏頭痛")))
            info.correlations.add("🔍 你近期曾記錄頭痛，而今天氣壓偏低——低氣壓常誘發偏頭痛，建議提早休息、避免咖啡因。");
        if (info.pm25 != null && info.pm25 >= 35
                && (symptomBlob.contains("咳嗽") || symptomBlob.contains("喉嚨") || symptomBlob.contains("過敏")))
            info.correlations.add("🔍 你近期有呼吸道相關不適，今天 PM2.5 偏高——可能是隱藏誘因，外出請戴口罩。");
        if (info.humidity != null && info.humidity >= 85
                && (symptomBlob.contains("失眠") || symptomBlob.contains("睡不")))
            info.correlations.add("🔍 高濕悶熱可能影響睡眠品質，你近期有睡眠困擾，建議調整室內除濕與通風。");
        if (info.temperature != null && info.temperature >= 32
                && (symptomBlob.contains("頭暈") || symptomBlob.contains("疲憊")))
            info.correlations.add("🔍 高溫易造成頭暈與疲倦，與你近期症狀吻合，請加強補水與避暑。");

        if (info.correlations.isEmpty())
            info.correlations.add("目前未發現環境與你症狀的明顯關聯，持續記錄日記能讓分析更準確。");
    }

    private String weatherText(int code) {
        if (code < 0) return "未知";
        if (code == 0) return "晴朗";
        if (code <= 3) return "多雲";
        if (code <= 48) return "有霧";
        if (code <= 67) return "下雨";
        if (code <= 77) return "下雪";
        if (code <= 82) return "陣雨";
        if (code <= 99) return "雷雨";
        return "未知";
    }
}
