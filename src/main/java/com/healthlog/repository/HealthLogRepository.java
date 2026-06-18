package com.healthlog.repository;

import com.healthlog.entity.HealthLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository 分層 — 健康日誌資料存取。
 * 透過 Spring Data JPA，自動以參數化查詢避免 SQL Injection（§8 安全）。
 */
public interface HealthLogRepository extends JpaRepository<HealthLog, Long> {

    /** 依日期由新到舊排序取得全部 */
    List<HealthLog> findAllByOrderByLogDateDesc();

    /** 依日期區間查詢（§10.1 加值：日期區間查詢） */
    List<HealthLog> findByLogDateBetweenOrderByLogDateAsc(LocalDate start, LocalDate end);

    /** 取最新一筆（供 /health-logs/risk 預設計算對象） */
    HealthLog findFirstByOrderByLogDateDescIdDesc();
}
