package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Getter
@AllArgsConstructor
public class CutoffTrendResponseDto{
    private Integer eventId;
    private List<RankTrendPoint> trendData;

    @Getter
    @AllArgsConstructor
    public static class RankTrendPoint{
        private LocalDateTime timestamp;
        private Map<Integer, Long> rankScores;
    }
}
