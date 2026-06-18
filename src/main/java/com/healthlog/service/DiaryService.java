package com.healthlog.service;

import com.healthlog.dto.DiaryRequest;
import com.healthlog.entity.DiaryEntry;
import com.healthlog.repository.DiaryEntryRepository;
import com.healthlog.security.CurrentUser;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 健康日記業務服務（依使用者隔離） */
@Service
public class DiaryService {

    private final DiaryEntryRepository repo;

    public DiaryService(DiaryEntryRepository repo) {
        this.repo = repo;
    }

    public List<DiaryEntry> findAll() { return repo.findByUserIdOrderByEntryDateDescIdDesc(CurrentUser.id()); }

    public DiaryEntry create(DiaryRequest req) {
        DiaryEntry e = new DiaryEntry();
        e.setUserId(CurrentUser.id());
        apply(e, req);
        return repo.save(e);
    }

    public DiaryEntry update(Long id, DiaryRequest req) {
        DiaryEntry e = get(id);
        apply(e, req);
        return repo.save(e);
    }

    public void delete(Long id) { repo.delete(get(id)); }

    private DiaryEntry get(Long id) {
        DiaryEntry e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到 id=" + id + " 的日記"));
        if (e.getUserId() != null && !e.getUserId().equals(CurrentUser.id()))
            throw new IllegalArgumentException("無權存取此筆資料");
        return e;
    }

    private void apply(DiaryEntry e, DiaryRequest req) {
        e.setEntryDate(req.getEntryDate() != null ? req.getEntryDate() : LocalDate.now());
        e.setTitle(req.getTitle());
        e.setContent(req.getContent());
        e.setMoodTag(req.getMoodTag());
        e.setSymptomTags(req.getSymptomTags());
    }
}
