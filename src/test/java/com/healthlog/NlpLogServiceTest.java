package com.healthlog;

import com.healthlog.dto.NlpParseResult;
import com.healthlog.service.KnowledgeBaseService;
import com.healthlog.service.NlpLogService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NLP 優化測試：B1 否定詞處理、B2 咖啡因劑量量化、B5/B6 外部知識庫。
 */
class NlpLogServiceTest {

    // 直接載入外部化知識圖譜（classpath:knowledge/health-knowledge.json）
    private final NlpLogService nlp = new NlpLogService(new KnowledgeBaseService());

    @Test
    void B1_否定詞_沒有頭痛_不應判為症狀() {
        NlpParseResult r = nlp.parse("今天精神不錯，沒有頭痛也不焦慮");
        assertFalse(r.extracted.symptoms.contains("頭痛"), "「沒有頭痛」不應抽出頭痛");
        assertTrue(r.tags.stream().noneMatch(t -> t.keyword.equals("頭痛")), "不應有頭痛標籤");
        assertTrue(r.tags.stream().noneMatch(t -> t.keyword.equals("焦慮")), "「不焦慮」不應有焦慮標籤");
    }

    @Test
    void B1_正常陳述_頭痛_應判為症狀() {
        NlpParseResult r = nlp.parse("今天頭痛得厲害");
        assertTrue(r.extracted.symptoms.contains("頭痛"), "正常陳述應抽出頭痛");
    }

    @Test
    void B2_三杯美式_估算咖啡因約285mg_未過量() {
        NlpParseResult r = nlp.parse("今天為了趕報告喝了三杯美式");
        assertEquals(3, r.extracted.caffeineCups);
        assertEquals(285, r.extracted.caffeineMg);
        assertTrue(r.tags.stream().noneMatch(t -> t.keyword.equals("過量")), "285mg 未達過量");
    }

    @Test
    void B2_五杯美式_超過400mg_標記過量() {
        NlpParseResult r = nlp.parse("今天喝了五杯美式咖啡");
        assertEquals(475, r.extracted.caffeineMg);
        assertTrue(r.tags.stream().anyMatch(t -> t.keyword.equals("過量") && t.status.equals("danger")),
                "475mg 應標記咖啡因過量(danger)");
    }

    @Test
    void B2_一杯水_不應被當成咖啡因() {
        NlpParseResult r = nlp.parse("今天只喝了一杯水");
        assertNull(r.extracted.caffeineMg, "一杯水不應計入咖啡因");
    }
}
