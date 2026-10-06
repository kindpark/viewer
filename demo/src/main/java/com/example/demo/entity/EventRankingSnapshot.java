package com.example.demo.entity;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_ranking_snapshot", indexes = {
        @Index(name = "idx_event_time", columnList = "eventId, recordedAt"),
        @Index(name = "idx_user_event", columnList = "userId, eventId")
})
@Getter
@Setter
@NoArgsConstructor
public class EventRankingSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;

    private Integer eventId;
    private Long userId;
    private Integer rankNum;
    private Long score;
    private LocalDateTime recordedAt;
    public EventRankingSnapshot(Integer eventId, Long userId, Integer rankNum, Long score, LocalDateTime recordedAt) {
        this.eventId = eventId;
        this.userId = userId;
        this.rankNum = rankNum;
        this.score = score;
        this.recordedAt = recordedAt;
    }
}
