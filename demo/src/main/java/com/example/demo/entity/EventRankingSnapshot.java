package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_ranking_snapshot", indexes = {
        @Index(name = "idx_event_time", columnList = "eventId, recordedAt"),
        @Index(name = "idx_user_event", columnList = "userId, eventId")
})
@Getter
@NoArgsConstructor
public class EventRankingSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long snapshotId;
    private String userName;
    private Integer eventId;
    private Long userId;
    private Integer rankNum;
    private Long score;
    private LocalDateTime recordedAt;

    // ★ 수집 시점의 리더 카드 정보 (이벤트별 기록 보존용, 기존 행은 null)
    private Long cardId;
    private Integer cardLevel;
    private Boolean afterTraining;

    public EventRankingSnapshot(Integer eventId, Long userId, String userName, Integer rankNum,
                                Long score, LocalDateTime recordedAt,
                                Long cardId, Integer cardLevel, Boolean afterTraining) {
        this.eventId = eventId;
        this.userId = userId;
        this.userName = userName;
        this.rankNum = rankNum;
        this.score = score;
        this.recordedAt = recordedAt;
        this.cardId = cardId;
        this.cardLevel = cardLevel;
        this.afterTraining = afterTraining;
    }
}