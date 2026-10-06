package com.example.demo.service;

import com.example.demo.dto.CutoffTrendResponseDto;
import com.example.demo.dto.SnapshotDto;
import com.example.demo.entity.EventRankingSnapshot;
import com.example.demo.entity.Player;
import com.example.demo.repository.EventRankingSnapshotRepository;
import com.example.demo.repository.PlayerRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RankingService {
    private final EventRankingSnapshotRepository snapshotRepository;
    private final PlayerRepository playerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    //지난 랭킹
    public List<SnapshotDto> getLatestRanking(Integer eventId){
        List<EventRankingSnapshot> snapshots = snapshotRepository.findLatestRankingByEventId(eventId);
        //에러가 날수 있는 지점 stream으로 전달?
        Map<Long, Player> playerMap = playerRepository.findAllById(
                snapshots.stream().map(EventRankingSnapshot::getUserId).collect(Collectors.toList())
        ).stream().collect(Collectors.toMap(Player::getUserId, Function.identity()));

        return snapshots.stream().map(s->{
            Player p = playerMap.get(s.getUserId());
            String name = (p != null) ? p.getName():"Unknown";
            String iconUrl = extractUserIconUrl(p);
            return new SnapshotDto(s.getUserId(), name, s.getRankNum(), s.getScore(), s.getRecordedAt(), iconUrl);
        }).collect(Collectors.toList());
    }

    public CutoffTrendResponseDto getCutoffTrends(Integer eventId, List<Integer> targetRanks){
        List<EventRankingSnapshot> snapshots =
                snapshotRepository.findCutoffTrendsByEventIdAndRanks(eventId, targetRanks);
        Map<LocalDateTime, Map<Integer, Long>> timeGrouped = snapshots.stream()
                .collect(Collectors.groupingBy(
                        EventRankingSnapshot::getRecordedAt,
                        TreeMap::new,
                        Collectors.toMap(
                                EventRankingSnapshot::getRankNum,
                                EventRankingSnapshot::getScore,
                                (existing, replacement) -> existing
                        )
                ));
        List<CutoffTrendResponseDto.RankTrendPoint> trendPoints = timeGrouped.entrySet().stream()
                .map(entry -> new CutoffTrendResponseDto.RankTrendPoint(entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());
        return new CutoffTrendResponseDto(eventId, trendPoints);
    }
    private String extractUserIconUrl(Player player) {
        if (player == null || player.getRowJson() == null || player.getRowJson().isBlank()) {
            return getDefaultIconUrl();
        }

        try {
            JsonNode rootNode = objectMapper.readTree(player.getRowJson());
            String assetName = null;

            // 1. userDecks -> leader -> assetbundleName 파싱
            JsonNode leaderNode = rootNode.at("/userDecks/0/leader");
            if (!leaderNode.isMissingNode() && leaderNode.has("assetbundleName")) {
                assetName = leaderNode.get("assetbundleName").asText();
            }

            if (assetName != null && !assetName.isBlank()) {
                // 카드 썸네일 공식 CDN URL 생성 (기본 normal 이미지 사용)
                return "https://asset.sekai.best/asset/v1/png/thumbnail/chara_rip/" + assetName + "_normal.png";
            }

        } catch (Exception e) {
            //log.warn("유저 [{}] 프로필 파싱 실패", player.getUserId());
        }

        return getDefaultIconUrl();
    }

    private String getDefaultIconUrl() {
        return "https://sekai.best/assets/rarity_star_afterTraining-CUlLhfpl.png";
    }
}
