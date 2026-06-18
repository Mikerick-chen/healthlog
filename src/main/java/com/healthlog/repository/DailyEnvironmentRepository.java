package com.healthlog.repository;

import com.healthlog.entity.DailyEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface DailyEnvironmentRepository extends JpaRepository<DailyEnvironment, Long> {
    Optional<DailyEnvironment> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);
}
