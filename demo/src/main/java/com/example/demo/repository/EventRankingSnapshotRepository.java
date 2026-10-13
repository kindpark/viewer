package com.example.demo.repository;

import com.example.demo.entity.EventRankingSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface EventRankingSnapshotRepository extends JpaRepository<EventRankingSnapshot, Long> {

    List<EventRankingSnapshot> findByEventIdAndUserIdOrderByRecordedAtAsc(Integer eventId, Long userId);

    //최신 스냅샷만 단일 테이블에서 조회
    @Query("SELECT s FROM EventRankingSnapshot s " +
            "WHERE s.eventId = :eventId AND s.recordedAt = " +
            "(SELECT MAX(s2.recordedAt) FROM EventRankingSnapshot s2 WHERE s2.eventId = :eventId) " +
            "ORDER BY s.rankNum ASC")
    List<EventRankingSnapshot> findLatestRankingByEventId(@Param("eventId") Integer eventId);
    /*
    //Cutoff 추이 조회
    @Query("SELECT s FROM EventRankingSnapshot s " +
            "WHERE s.eventId = :eventId AND s.rankNum IN :ranks " +
            "ORDER BY s.recordedAt ASC, s.rankNum ASC")
    List<EventRankingSnapshot> findCutoffTrendsByEventIdAndRanks(
            @Param("eventId") Integer eventId,
            @Param("ranks") List<Integer> ranks);
*/
    //데이터가 실제로 수집된 이벤트 번호 목록 (드롭다운용)
    @Query("SELECT DISTINCT s.eventId FROM EventRankingSnapshot s")
    List<Integer> findCollectedEventIds();
    boolean existsByEventIdAndRecordedAt(Integer eventId, LocalDateTime recordedAt);
    // 이벤트 종료 후 최종 스냅샷이 저장됐는지 확인 (수집 중단 판단용)
    boolean existsByEventIdAndRecordedAtGreaterThanEqual(Integer eventId, LocalDateTime recordedAt);


    //이벤트 수집 시점 목록만 조회
    @Query("select distinct s.recordedAt from EventRankingSnapshot s " +
            "WHERE s.eventId = :eventId ORDER BY s.recordedAt ASC")
    List<LocalDateTime> findDistinctRecordedAtByEventId(@Param("eventId") Integer eventId);
    //샘플링 된 시점 x 요청 등수만 조회
    List<EventRankingSnapshot> findByEventIdAndRankNumInAndRecordedAtIn
    (Integer eventId, Collection<Integer> ranks, Collection<LocalDateTime> times);

}