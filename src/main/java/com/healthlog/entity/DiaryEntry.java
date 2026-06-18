package com.healthlog.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 健康日記 Entity — 新增資料表 diary_entries。
 * 記錄當日身心狀況的文字日記，可附情緒標籤與症狀標籤。
 */
@Entity
@Table(name = "diary_entries")
public class DiaryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "title", length = 200)
    private String title;

    /** 日記內文 */
    @Column(name = "content", length = 4000)
    private String content;

    /** 情緒標籤，例如 開心/焦慮/疲憊 */
    @Column(name = "mood_tag", length = 50)
    private String moodTag;

    /** 症狀標籤，例如 頭痛/失眠（逗號分隔） */
    @Column(name = "symptom_tags", length = 300)
    private String symptomTags;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public DiaryEntry() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
