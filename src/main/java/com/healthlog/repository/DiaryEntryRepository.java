package com.healthlog.repository;

import com.healthlog.entity.DiaryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {
    List<DiaryEntry> findByUserIdOrderByEntryDateDescIdDesc(Long userId);
    List<DiaryEntry> findByUserIdAndEntryDateBetweenOrderByEntryDateDesc(Long userId, LocalDate start, LocalDate end);
    long countByUserId(Long userId);
}
