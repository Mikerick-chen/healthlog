package com.healthlog.repository;

import com.healthlog.entity.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {
    List<VitalSign> findAllByOrderByRecordDateDesc();
    List<VitalSign> findByRecordDateBetweenOrderByRecordDateAsc(LocalDate start, LocalDate end);
    VitalSign findFirstByOrderByRecordDateDescIdDesc();
}
