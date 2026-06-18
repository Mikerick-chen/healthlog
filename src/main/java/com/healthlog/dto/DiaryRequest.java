package com.healthlog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 健康日記輸入 DTO */
public class DiaryRequest {
    private LocalDate entryDate;

    @Size(max = 200, message = "標題過長")
    private String title;

    @NotBlank(message = "日記內容不可為空")
    @Size(max = 4000, message = "內容過長")
    private String content;

    private String moodTag;
    private String symptomTags;

    public LocalDate getEntryDate() { return entryDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMoodTag() { return moodTag; }
    public void setMoodTag(String moodTag) { this.moodTag = moodTag; }
    public String getSymptomTags() { return symptomTags; }
    public void setSymptomTags(String symptomTags) { this.symptomTags = symptomTags; }
}
