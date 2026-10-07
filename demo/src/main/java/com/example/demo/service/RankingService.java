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
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RankingService {

    private final EventRankingSnapshotRepository snapshotRepository;
    private final PlayerRepository playerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

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
            String iconUrl = extractUserIconUrl(p);
            CardMeta meta = parseCardMeta(p);
            return new SnapshotDto(
                    s.getUserId(),
                    name,
                    s.getRankNum(),
                    s.getScore(),
                    s.getRecordedAt(),
                    meta.getIconUrl(),
                    meta.getRarity(),
                    meta.getAttrType(),
                    meta.getCardLevel(),
                    meta.getIsAfterTraining()
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

    // 유저 카드 어셋 기반 공식 썸네일 URL 추출
    private String extractUserIconUrl(Player player) {
        if (player == null || player.getRowJson() == null || player.getRowJson().isBlank()) {
            log.info(">>> [DEBUG] Player가 null이거나 rowJson이 비어 있음 (userId: {})", player != null ? player.getUserId() : "null");
            return getDefaultIconUrl();
        }

        try {
            JsonNode rootNode = objectMapper.readTree(player.getRowJson());

            List<String> fieldNames = new ArrayList<>();
            rootNode.fieldNames().forEachRemaining(fieldNames::add);
            log.info(">>> [DEBUG] userId: {} / root keys: {}", player.getUserId(), fieldNames);
            String assetbundleName = null;
            boolean isAfterTraining = false;

            // 1. userCards[0] 탐색
            JsonNode userCardsNode = rootNode.path("userCards");
            if (userCardsNode.isArray() && !userCardsNode.isEmpty()) {
                JsonNode leaderCard = userCardsNode.get(0);

                if (leaderCard.has("specialTrainingStatus")) {
                    isAfterTraining = "DONE".equalsIgnoreCase(leaderCard.path("specialTrainingStatus").asText());
                }

                JsonNode cardNode = leaderCard.path("card");
                if (!cardNode.isMissingNode() && cardNode.has("assetbundleName")) {
                    assetbundleName = cardNode.path("assetbundleName").asText();
                }
            }

            // 2. Fallback: userDecks/0/leader 탐색
            if (assetbundleName == null || assetbundleName.isBlank()) {
                JsonNode leaderNode = rootNode.at("/userDecks/0/leader");
                if (!leaderNode.isMissingNode() && leaderNode.has("assetbundleName")) {
                    assetbundleName = leaderNode.path("assetbundleName").asText();
                }
            }

            // 3. Fallback: userCards/0 직접 탐색
            if (assetbundleName == null || assetbundleName.isBlank()) {
                JsonNode leaderCard = rootNode.at("/userCards/0");
                if (!leaderCard.isMissingNode() && leaderCard.has("assetbundleName")) {
                    assetbundleName = leaderCard.path("assetbundleName").asText();
                }
            }

            // sekai.best 공식 CDN WebP 주소 생성
            if (assetbundleName != null && !assetbundleName.isBlank()) {
                String statusSuffix = isAfterTraining ? "after_training" : "normal";
                String generatedUrl = String.format(
                        "https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/%s_%s.webp",
                        assetbundleName,
                        statusSuffix
                );
                log.info(">>> [DEBUG] 파싱 성공 (userId: {}) -> URL: {}", player.getUserId(), generatedUrl);
                return generatedUrl;
            }else{
                log.warn(">>> [DEBUG] assetbundleName 찾기 실패 (userId: {}) / rawJson: {}", player.getUserId(), player.getRowJson());
            }

        } catch (Exception e) {
            // ignore
            log.error(">>> [DEBUG] 파싱 중 예외 발생 (userId: {}): {}", player.getUserId(), e.getMessage());
        }

        return getDefaultIconUrl();
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
    private CardMeta parseCardMeta(Player player) {
        if (player == null || player.getRowJson() == null || player.getRowJson().isBlank()) {
            return getDefaultCardMeta();
        }

        String rawJson = player.getRowJson();

// 1. SVG/HTML 태그 내 URL 정규식 추출 시도
        if (rawJson.contains("storage.sekai.best")) {
            Pattern pattern = Pattern.compile("https://storage\\.sekai\\.best/sekai-jp-assets/thumbnail/chara/([a-zA-Z0-9_]+)\\.webp");
            Matcher matcher = pattern.matcher(rawJson);

            if (matcher.find()) {
                String fullAsset = matcher.group(1);
                String iconUrl = "https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/" + fullAsset + ".webp";
                boolean isAfter = fullAsset.endsWith("after_training");
                return new CardMeta(iconUrl, 4, "cute", 60, isAfter);
            }
        }
        try {
            JsonNode rootNode = objectMapper.readTree(player.getRowJson());
            JsonNode dataNode = rootNode.has("data") ? rootNode.path("data") : rootNode;

            String assetbundleName = null;
            int rarity = 4;
            String attrType = "cool";
            int cardLevel = 1;
            boolean isAfterTraining = false;

            // 1. userCards 배열 탐색
            JsonNode userCardsNode = dataNode.path("userCards");
            if (userCardsNode.isArray() && !userCardsNode.isEmpty()) {
                JsonNode leaderCard = userCardsNode.get(0);

                if (leaderCard.has("level")) cardLevel = leaderCard.path("level").asInt(1);
                if (leaderCard.has("specialTrainingStatus")) {
                    isAfterTraining = "DONE".equalsIgnoreCase(leaderCard.path("specialTrainingStatus").asText());
                }

                JsonNode cardNode = leaderCard.path("card");
                if (!cardNode.isMissingNode()) {
                    if (cardNode.has("assetbundleName")) assetbundleName = cardNode.path("assetbundleName").asText();
                    if (cardNode.has("attr")) attrType = cardNode.path("attr").asText().toLowerCase();
                    if (cardNode.has("rarity")) rarity = cardNode.path("rarity").asInt(4);
                }
            }

            // 2. userDecks Fallback
            if (assetbundleName == null || assetbundleName.isBlank()) {
                JsonNode leaderNode = dataNode.at("/userDecks/0/leader");
                if (!leaderNode.isMissingNode() && leaderNode.has("assetbundleName")) {
                    assetbundleName = leaderNode.path("assetbundleName").asText();
                }
            }

            // 정상적으로 assetbundleName을 찾았을 때
            if (assetbundleName != null && !assetbundleName.isBlank()) {
                String statusSuffix = isAfterTraining ? "after_training" : "normal";
                String iconUrl = String.format("https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/%s_%s.webp",
                        assetbundleName, statusSuffix);
                return new CardMeta(iconUrl, rarity, attrType, cardLevel, isAfterTraining);
            }

        } catch (Exception e) {
            // ignore
        }

        // 파싱 실패 또는 요약 데이터만 있을 때 안전한 동적 기본 썸네일 생성
        return getDefaultCardMeta();
    }
    private CardMeta getDefaultCardMeta() {
        return new CardMeta(
                "https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/res006_no012_normal.webp",
                4,
                "cool",
                4,
                false
        );
    }
}