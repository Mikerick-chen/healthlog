package com.healthlog.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 自適應情緒 AI 教練訊息（§4）。
 * 依使用者當下生理數據切換語氣（溫柔鼓勵 / 嚴格督導 / 熱情慶祝 / 穩定陪伴）。
 */
public class CoachMessage {
    public String tone;        // gentle / strict / celebrate / steady
    public String toneLabel;   // 中文語氣標籤
    public String emoji;
    public String title;
    public String message;     // 具「人味」的個人化訊息
    public List<String> suggestions = new ArrayList<>();
    public String stateReason; // 判斷依據（透明化）
}
