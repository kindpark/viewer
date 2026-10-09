package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SnapshotDto {
    private Long userId;
    private String playerName;
    private Integer rankNum;
    private Long score;
    private LocalDateTime recordedAt;
    private String userIconUrl;
    private String attrType;
    private Integer rarity;
    private Boolean isAfterTraining;
    private Integer cardLevel;
}