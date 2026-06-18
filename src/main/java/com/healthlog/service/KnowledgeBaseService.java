package com.healthlog.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康知識圖譜服務（B5 詞庫外部化 + B6 單一真相來源）。
 *
 * 啟動時載入 classpath:knowledge/health-knowledge.json，
 * 同時供 {@link NlpLogService}（關鍵字→訊號）與 {@link ClinicService}（症狀→科別）使用，
 * 兩者共用同一份知識，避免維護分歧；醫療團隊只需改 JSON。
 */
@Service
public class KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseService.class);

    /** 知識節點：一個健康概念，含 NLP 欄位（全部）與診療室欄位（僅症狀節點）。 */
    public record Concept(
            String category, List<String> keywords, String status, String message,
            boolean symptom, String symptomName,
            String specialty, List<String> causes, String advice, boolean redFlag) {}

    private final List<Concept> concepts = new ArrayList<>();
    private final List<Concept> symptomConcepts = new ArrayList<>();
    private final List<String> commonSymptoms = new ArrayList<>();

    public KnowledgeBaseService() {
        load();
    }

    private void load() {
        try (InputStream is = new ClassPathResource("knowledge/health-knowledge.json").getInputStream()) {
            JsonNode root = new ObjectMapper().readTree(is);
            for (JsonNode n : root.path("concepts")) {
                List<String> keywords = toList(n.path("keywords"));
                boolean symptom = n.path("symptom").asBoolean(false);
                Concept c = new Concept(
                        n.path("category").asText(),
                        keywords,
                        n.path("status").asText("info"),
                        n.path("message").asText(""),
                        symptom,
                        n.path("symptomName").asText(null),
                        n.path("specialty").asText(null),
                        toList(n.path("causes")),
                        n.path("advice").asText(null),
                        n.path("redFlag").asBoolean(false));
                concepts.add(c);
                if (symptom) {
                    symptomConcepts.add(c);
                    if (c.symptomName() != null) commonSymptoms.add(c.symptomName());
                }
            }
            log.info("已載入健康知識圖譜：{} 個概念（其中 {} 個症狀節點）", concepts.size(), symptomConcepts.size());
        } catch (Exception e) {
            throw new IllegalStateException("無法載入健康知識圖譜 knowledge/health-knowledge.json：" + e.getMessage(), e);
        }
    }

    private List<String> toList(JsonNode arr) {
        List<String> list = new ArrayList<>();
        if (arr != null && arr.isArray()) arr.forEach(x -> list.add(x.asText()));
        return list;
    }

    public List<Concept> concepts() { return concepts; }

    public List<Concept> symptomConcepts() { return symptomConcepts; }

    public List<String> commonSymptoms() { return commonSymptoms; }

    /** 以症狀名稱為鍵的對照表（診療室用） */
    public Map<String, Concept> symptomByName() {
        Map<String, Concept> m = new LinkedHashMap<>();
        for (Concept c : symptomConcepts) if (c.symptomName() != null) m.put(c.symptomName(), c);
        return m;
    }
}
