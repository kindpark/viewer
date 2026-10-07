package com.example.demo.service;

import com.example.demo.dto.CutoffTrendResponseDto;
import com.example.demo.dto.SnapshotDto;
import com.example.demo.entity.EventRankingSnapshot;
import com.example.demo.entity.Player;
import com.example.demo.repository.EventRankingSnapshotRepository;
import com.example.demo.repository.PlayerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RankingService {

    private final EventRankingSnapshotRepository snapshotRepository;
    private final PlayerRepository playerRepository;

    // 최신 리더보드 조회 (유저 카드 이미지 URL 매핑)
    public List<SnapshotDto> getLatestRanking(Integer eventId) {
        List<EventRankingSnapshot> snapshots = snapshotRepository.findLatestRankingByEventId(eventId);

        if (snapshots.isEmpty()) {
            return List.of();
        }

        // 스냅샷의 userId 리스트 추출 후 Player 1회 Batch 조회 (N+1 방지)
        List<Long> userIds = snapshots.stream()
                .map(EventRankingSnapshot::getUserId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Player> playerMap = playerRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(Player::getUserId, Function.identity(), (p1, p2) -> p1));

        return snapshots.stream().map(s -> {
            Player p = playerMap.get(s.getUserId());
            String name = (p != null && p.getName() != null) ? p.getName() : "Unknown";
            String iconUrl = getDefaultIconUrl();
            return new SnapshotDto(
                    s.getUserId(),
                    name,
                    s.getRankNum(),
                    s.getScore(),
                    s.getRecordedAt(),
                    iconUrl
            );
        }).collect(Collectors.toList());
    }

    // 시간별 주요 컷트라인 추이 조회 (캐싱 적용)
    @Cacheable(value = "singleRankTrend", key = "#eventId + '_' + #targetRanks.toString()")
    @Transactional(readOnly = true)
    public CutoffTrendResponseDto getCutoffTrends(Integer eventId, List<Integer> targetRanks) {
        if (targetRanks == null || targetRanks.isEmpty()) {
            return new CutoffTrendResponseDto(eventId, Collections.emptyList());
        }

        List<EventRankingSnapshot> snapshots =
                snapshotRepository.findCutoffTrendsByEventIdAndRanks(eventId, targetRanks);

        if (snapshots.isEmpty()) {
            return new CutoffTrendResponseDto(eventId, Collections.emptyList());
        }

        // 수집 시각(recordedAt) 기준 그룹핑
        Map<LocalDateTime, Map<Integer, Long>> groupedByTime = snapshots.stream()
                .collect(Collectors.groupingBy(
                        EventRankingSnapshot::getRecordedAt,
                        TreeMap::new,
                        Collectors.toMap(
                                EventRankingSnapshot::getRankNum,
                                EventRankingSnapshot::getScore,
                                (existing, replacement) -> existing
                        )
                ));

        List<LocalDateTime> times = new ArrayList<>(groupedByTime.keySet());
        Collections.sort(times);
        List<LocalDateTime> sampledTimes = sampleTimes(times, 15);

        List<CutoffTrendResponseDto.RankTrendPoint> trendData = sampledTimes.stream()
                .map(time -> new CutoffTrendResponseDto.RankTrendPoint(time, groupedByTime.get(time)))
                .collect(Collectors.toList());

        return new CutoffTrendResponseDto(eventId, trendData);
    }

    // 기본 대체 썸네일 (res001_no001)
    private String getDefaultIconUrl() {
        return "https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/res001_no001_normal.webp";
    }

    // 15개 균등 샘플링
    private List<LocalDateTime> sampleTimes(List<LocalDateTime> times, int maxPoints) {
        if (times.size() <= maxPoints) return times;

        List<LocalDateTime> sampled = new ArrayList<>();
        int total = times.size();
        sampled.add(times.get(0));

        double step = (double) (total - 1) / (maxPoints - 1);
        for (int i = 1; i < maxPoints - 1; i++) {
            sampled.add(times.get((int) Math.round(i * step)));
        }

        LocalDateTime latestTime = times.get(total - 1);
        if (!sampled.contains(latestTime)) {
            sampled.remove(sampled.size() - 1);
            sampled.add(latestTime);
        }
        return sampled;
    }
}