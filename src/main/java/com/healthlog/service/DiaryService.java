package com.healthlog.service;

import com.healthlog.dto.DiaryRequest;
import com.healthlog.entity.DiaryEntry;
import com.healthlog.repository.DiaryEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** 健康日記業務服務 */
@Service
public class DiaryService {

    private final DiaryEntryRepository repo;

    public DiaryService(DiaryEntryRepository repo) {
        this.repo = repo;
    }

    public List<DiaryEntry> findAll() { return repo.findAllByOrderByEntryDateDescIdDesc(); }

    public DiaryEntry create(DiaryRequest req) {
        DiaryEntry e = new DiaryEntry();
        apply(e, req);
        return repo.save(e);
    }

    public DiaryEntry update(Long id, DiaryRequest req) {
        DiaryEntry e = repo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到 id=" + id + " 的日記"));
        apply(e, req);
        return repo.save(e);
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) throw new IllegalArgumentException("找不到 id=" + id + " 的日記");
        repo.deleteById(id);
    }

    private void apply(DiaryEntry e, DiaryRequest req) {
        e.setEntryDate(req.getEntryDate() != null ? req.getEntryDate() : LocalDate.now());
        e.setTitle(req.getTitle());
        e.setContent(req.getContent());
        e.setMoodTag(req.getMoodTag());
        e.setSymptomTags(req.getSymptomTags());
    }
}
