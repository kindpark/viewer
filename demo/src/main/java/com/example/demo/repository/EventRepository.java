package com.example.demo.repository;

import com.example.demo.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Integer> {
    // 💡 현재 시각 기준 진행 중인 이벤트 자동 조회 (startAt <= now <= aggregateAt)
    @Query("SELECT e FROM Event e WHERE :now BETWEEN e.startAt AND e.aggregateAt")
    Optional<Event> findActiveEvent(@Param("now") LocalDateTime now);

    // 프론트엔드 셀렉트 박스용 (최신 이벤트 순 정렬)
    List<Event> findAllByOrderByEventIdDesc();
}
