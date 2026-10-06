package com.example.demo.repository;

import com.example.demo.entity.EventRankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventRankingSnapshotRepository extends JpaRepository<EventRankingSnapshot, Long> {
    List<EventRankingSnapshot> findByEventIdAndUserIdOrderByRecordedAtAsc(Integer eventId, Long userId);
    // 1. [복구 및 유지] 특정 이벤트의 가장 최신 시간대 랭킹 스냅샷 목록 조회 (리더보드용)
    @Query("SELECT s FROM EventRankingSnapshot s " +
            "WHERE s.eventId = :eventId AND s.recordedAt = " +
            "(SELECT MAX(s2.recordedAt) FROM EventRankingSnapshot s2 WHERE s2.eventId = :eventId) " +
            "ORDER BY s.rankNum ASC")
    List<EventRankingSnapshot> findLatestRankingByEventId(@Param("eventId") Integer eventId);

    // 2. [신규 추가] 특정 이벤트의 주요 컷트라인 등수들(100위, 1000위 등)의 시간별 점수 추이 조회 (차트용)
    @Query("SELECT s FROM EventRankingSnapshot s " +
            "WHERE s.eventId = :eventId AND s.rankNum IN :ranks " +
            "ORDER BY s.recordedAt ASC, s.rankNum ASC")
    List<EventRankingSnapshot> findCutoffTrendsByEventIdAndRanks(
            @Param("eventId") Integer eventId,
            @Param("ranks") List<Integer> ranks);
}
