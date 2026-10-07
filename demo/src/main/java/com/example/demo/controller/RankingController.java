package com.example.demo.controller;

import com.example.demo.dto.CutoffTrendResponseDto;
import com.example.demo.dto.SnapshotDto;
import com.example.demo.entity.EventRankingSnapshot;
import com.example.demo.entity.Player;
import com.example.demo.repository.EventRankingSnapshotRepository;
import com.example.demo.repository.PlayerRepository;
import com.example.demo.service.RankingService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RankingController {
    private final RankingService service;
    private final PlayerRepository playerRepository;
    private final EventRankingSnapshotRepository snapshotRepository;
    @GetMapping("/{eventId}/latest")
    public List<SnapshotDto> getLastestRanking(@PathVariable Integer eventId){
        return service.getLatestRanking(eventId);
    }
    @GetMapping("/{eventId}/cutoffs")
    public CutoffTrendResponseDto getCutoffTrends(
            @PathVariable Integer eventId,
            @RequestParam(value="ranks") List<String> ranksParam) {

        List<Integer> ranks = ranksParam.stream()
                .flatMap(r -> Arrays.stream(r.split(",")))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .distinct()
                .collect(Collectors.toList());

        return service.getCutoffTrends(eventId, ranks);
    }
    @GetMapping("/test/player/{userId}")
    public String getRawPlayerData(@PathVariable Long userId) {
        return playerRepository.findById(userId)
                .map(Player::getRowJson)
                .orElse("해당 유저 데이터가 DB에 없습니다.");
    }
}
