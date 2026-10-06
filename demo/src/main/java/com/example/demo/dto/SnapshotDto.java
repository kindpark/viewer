package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter @Setter
@AllArgsConstructor
public class SnapshotDto {
    private Long userId;
    private String playerName;
    private Integer rankNum;
    private Long score;
    private LocalDateTime recordedAt;
    private String userIconUrl;
}