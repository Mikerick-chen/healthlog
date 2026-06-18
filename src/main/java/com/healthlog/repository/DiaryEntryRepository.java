package com.healthlog.repository;

import com.healthlog.entity.DiaryEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, Long> {
    List<DiaryEntry> findAllByOrderByEntryDateDescIdDesc();
    List<DiaryEntry> findByEntryDateBetweenOrderByEntryDateDesc(LocalDate start, LocalDate end);
}
