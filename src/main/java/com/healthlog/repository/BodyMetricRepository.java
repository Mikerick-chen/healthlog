package com.healthlog.repository;

import com.healthlog.entity.BodyMetric;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface BodyMetricRepository extends JpaRepository<BodyMetric, Long> {
    List<BodyMetric> findByUserIdOrderByRecordDateDesc(Long userId);
    List<BodyMetric> findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(Long userId, LocalDate start, LocalDate end);
    BodyMetric findFirstByUserIdOrderByRecordDateDescIdDesc(Long userId);
    /** 同日對齊用：取該使用者「指定日期(含)以前」最近一筆 */
    BodyMetric findFirstByUserIdAndRecordDateLessThanEqualOrderByRecordDateDescIdDesc(Long userId, LocalDate date);
    long countByUserId(Long userId);
}
