package com.healthlog.repository;

import com.healthlog.entity.VitalSign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface VitalSignRepository extends JpaRepository<VitalSign, Long> {
    List<VitalSign> findByUserIdOrderByRecordDateDesc(Long userId);
    List<VitalSign> findByUserIdAndRecordDateBetweenOrderByRecordDateAsc(Long userId, LocalDate start, LocalDate end);
    VitalSign findFirstByUserIdOrderByRecordDateDescIdDesc(Long userId);
    /** 同日對齊用：取該使用者「指定日期(含)以前」最近一筆 */
    VitalSign findFirstByUserIdAndRecordDateLessThanEqualOrderByRecordDateDescIdDesc(Long userId, LocalDate date);
    long countByUserId(Long userId);
}
