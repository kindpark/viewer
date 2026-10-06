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
import java.util.List;

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
            @RequestParam(defaultValue = "100,500,1000,5000,10000") List<Integer> ranks) {
        return service.getCutoffTrends(eventId, ranks);
    }
    //테스트
    @PostMapping("/test/init")
    public String initTestData() {
        playerRepository.save(new Player(1001L, "유저A", "{}"));
        playerRepository.save(new Player(1002L, "유저B", "{}"));

        LocalDateTime now = LocalDateTime.now();
        snapshotRepository.save(new EventRankingSnapshot(1, 1001L, 1, 500000L, now.minusMinutes(10)));
        snapshotRepository.save(new EventRankingSnapshot(1, 1002L, 2, 450000L, now.minusMinutes(10)));

        snapshotRepository.save(new EventRankingSnapshot(1, 1001L, 1, 550000L, now));
        snapshotRepository.save(new EventRankingSnapshot(1, 1002L, 2, 480000L, now));

        return "OK";
    }
}
