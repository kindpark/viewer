/*
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
*/package com.example.demo.dto; // 기존 파일의 package 줄에 맞추세요

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

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DataContainer {
        private List<RankingItem> eventRankings;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RankingItem {
        private Integer eventId;
        private Long userId;

        @JsonProperty("userName")
        private String name;

        private Integer rank;

        // score가 문자열("96020")로 전달되므로 String으로 받은 뒤 Long 변환
        private String score;

        // ★ 기존에는 이 필드가 없어서 리더 카드 정보가 통째로 버려지고 있었음
        private UserCard userCard;

        public Long getScoreAsLong() {
            try {
                return score != null ? Long.parseLong(score) : 0L;
            } catch (NumberFormatException e) {
                return 0L;
            }
        }

        /** 리더 카드가 특훈 후 일러스트인지 (유저가 실제로 설정한 이미지 기준) */
        public boolean isAfterTrainingImage() {
            if (userCard == null) return false;
            if (userCard.getDefaultImage() != null) {
                return "special_training".equalsIgnoreCase(userCard.getDefaultImage());
            }
            return "done".equalsIgnoreCase(userCard.getSpecialTrainingStatus());
        }
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UserCard {
        private Integer level;
        private Long cardId;
        private Integer masterRank;
        private String defaultImage;           // "original" | "special_training"
        private String specialTrainingStatus;  // "done" | ...
    }
}
