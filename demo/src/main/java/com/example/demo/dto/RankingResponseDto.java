package com.example.demo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class RankingResponseDto {
    private String status;
    private DataContainer data;
    private List<RankingItem> rankings;
    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataContainer {
        private List<RankingItem> eventRankings;
    }
    @Getter @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RankingItem {
        private Integer eventId;
        private Long userId; // sekai.best 응답 구조에 맞춰 필요시 적용

        @JsonProperty("userName")
        private String name;

        private Integer rank;

        // score가 문자열("96020")로 전달되므로 String으로 받은 뒤 Long 변환 처리
        private String score;

        public Long getScoreAsLong() {
            try {
                return score != null ? Long.parseLong(score) : 0L;
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
    }
}
