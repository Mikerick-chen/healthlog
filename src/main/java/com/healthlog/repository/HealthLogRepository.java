package com.healthlog.repository;

import com.healthlog.entity.HealthLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository 分層 — 健康日誌資料存取（已依 user_id 隔離，§9）。
 * 透過 Spring Data JPA，自動以參數化查詢避免 SQL Injection（§8 安全）。
 */
public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {

    /** 某使用者全部（新→舊） */
    List<HealthLog> findByUserIdOrderByLogDateDesc(Long userId);

    /** 某使用者日期區間 */
    List<HealthLog> findByUserIdAndLogDateBetweenOrderByLogDateAsc(Long userId, LocalDate start, LocalDate end);

    /** 某使用者最新一筆 */
    HealthLog findFirstByUserIdOrderByLogDateDescIdDesc(Long userId);

    long countByUserId(Long userId);
}
