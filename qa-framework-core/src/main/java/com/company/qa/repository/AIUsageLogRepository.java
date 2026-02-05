package com.company.qa.repository;

import com.company.qa.model.entity.AIUsageLog;
import com.company.qa.model.enums.AIProvider;
import com.company.qa.model.enums.AITaskType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AIUsageLogRepository extends JpaRepository<AIUsageLog, UUID> {

    // Find by user
    List<AIUsageLog> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // Find by provider
    List<AIUsageLog> findByProviderOrderByCreatedAtDesc(AIProvider provider);

    // Find by task type
    List<AIUsageLog> findByTaskTypeOrderByCreatedAtDesc(AITaskType taskType);

    // Find by date range
    List<AIUsageLog> findByCreatedAtBetweenOrderByCreatedAtDesc(Instant start, Instant end);

    // Find by user and date range
    List<AIUsageLog> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID userId, Instant start, Instant end);

    // Count by provider
    @Query("SELECT COUNT(a) FROM AIUsageLog a WHERE a.provider = :provider")
    Long countByProvider(@Param("provider") AIProvider provider);

    // Sum total cost by provider
    @Query("SELECT COALESCE(SUM(a.totalCost), 0) FROM AIUsageLog a WHERE a.provider = :provider")
    BigDecimal sumCostByProvider(@Param("provider") AIProvider provider);

    // Sum total cost by user
    @Query("SELECT COALESCE(SUM(a.totalCost), 0) FROM AIUsageLog a WHERE a.userId = :userId")
    BigDecimal sumCostByUser(@Param("userId") UUID userId);

    // Sum total cost by date range
    @Query("SELECT COALESCE(SUM(a.totalCost), 0) FROM AIUsageLog a " +
            "WHERE a.createdAt BETWEEN :start AND :end")
    BigDecimal sumCostByDateRange(@Param("start") Instant start, @Param("end") Instant end);

    // Sum total cost by user and date range
    @Query("SELECT COALESCE(SUM(a.totalCost), 0) FROM AIUsageLog a " +
            "WHERE a.userId = :userId AND a.createdAt BETWEEN :start AND :end")
    BigDecimal sumCostByUserAndDateRange(
            @Param("userId") UUID userId,
            @Param("start") Instant start,
            @Param("end") Instant end);

    // Get top users by cost
    @Query("SELECT a.userId, a.userName, COALESCE(SUM(a.totalCost), 0) as totalCost, COUNT(a) as requestCount " +
            "FROM AIUsageLog a " +
            "WHERE a.createdAt BETWEEN :start AND :end " +
            "GROUP BY a.userId, a.userName " +
            "ORDER BY totalCost DESC")
    List<Object[]> findTopUsersByCost(@Param("start") Instant start, @Param("end") Instant end);

    // Get cost by provider (grouped)
    @Query("SELECT a.provider, COALESCE(SUM(a.totalCost), 0) as totalCost, " +
            "COUNT(a) as requestCount, COALESCE(SUM(a.totalTokens), 0) as totalTokens " +
            "FROM AIUsageLog a " +
            "WHERE a.createdAt BETWEEN :start AND :end " +
            "GROUP BY a.provider")
    List<Object[]> findCostByProvider(@Param("start") Instant start, @Param("end") Instant end);

    // Get cost by task type (grouped)
    @Query("SELECT a.taskType, COALESCE(SUM(a.totalCost), 0) as totalCost, " +
            "COUNT(a) as requestCount, COALESCE(AVG(a.totalTokens), 0) as avgTokens " +
            "FROM AIUsageLog a " +
            "WHERE a.createdAt BETWEEN :start AND :end " +
            "GROUP BY a.taskType")
    List<Object[]> findCostByTaskType(@Param("start") Instant start, @Param("end") Instant end);

    // Get daily cost trends
    @Query("SELECT DATE(a.createdAt), COALESCE(SUM(a.totalCost), 0) as totalCost, COUNT(a) as requestCount " +
            "FROM AIUsageLog a " +
            "WHERE a.createdAt BETWEEN :start AND :end " +
            "GROUP BY DATE(a.createdAt) " +
            "ORDER BY DATE(a.createdAt)")
    List<Object[]> findDailyCostTrends(@Param("start") Instant start, @Param("end") Instant end);

    // Count total requests
    @Query("SELECT COUNT(a) FROM AIUsageLog a WHERE a.createdAt BETWEEN :start AND :end")
    Long countByDateRange(@Param("start") Instant start, @Param("end") Instant end);

    // Sum total tokens
    @Query("SELECT COALESCE(SUM(a.totalTokens), 0) FROM AIUsageLog a " +
            "WHERE a.createdAt BETWEEN :start AND :end")
    Long sumTokensByDateRange(@Param("start") Instant start, @Param("end") Instant end);
}