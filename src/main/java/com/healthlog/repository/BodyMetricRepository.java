package com.healthlog.repository;

import com.healthlog.entity.BodyMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BodyMetricRepository extends JpaRepository<BodyMetric, Long> {
    List<BodyMetric> findAllByOrderByRecordDateDesc();
    List<BodyMetric> findByRecordDateBetweenOrderByRecordDateAsc(LocalDate start, LocalDate end);
    BodyMetric findFirstByOrderByRecordDateDescIdDesc();
}
