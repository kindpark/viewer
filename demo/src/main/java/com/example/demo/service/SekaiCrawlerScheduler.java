package com.example.demo.service;

import com.example.demo.dto.RankingResponseDto;
import com.example.demo.entity.Event;
import com.example.demo.entity.EventRankingSnapshot;
import com.example.demo.entity.Player;
import com.example.demo.repository.EventRankingSnapshotRepository;
import com.example.demo.repository.EventRepository;
import com.example.demo.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class SekaiCrawlerScheduler {

    private final RestTemplate restTemplate = new RestTemplate();
    private final EventRankingSnapshotRepository snapshotRepository;
    private final PlayerRepository playerRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 10분마다 랭킹 및 유저 프로필 데이터 수집
    @Scheduled(fixedRate = 600000)
    @CacheEvict(value = "singleRankTrend", allEntries = true)
    public void collectRankingData() {
        log.info("=== 랭킹 데이터 수집 시작 ===");
        String rankingApiUrl = "https://api.sekai.best/event/live?region=jp";

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json, text/plain, */*");
            headers.set("Referer", "https://sekai.best/");
            headers.set("Origin", "https://sekai.best");
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<RankingResponseDto> response = restTemplate.exchange(
                    rankingApiUrl, HttpMethod.GET, requestEntity, RankingResponseDto.class);

            RankingResponseDto body = response.getBody();

            if (body != null && body.getData() != null && body.getData().getEventRankings() != null) {
                LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
                List<RankingResponseDto.RankingItem> rankings = body.getData().getEventRankings();

                if (rankings.isEmpty()) {
                    log.warn("랭킹 데이터가 비어있습니다.");
                    return;
                }

                Integer currentEventId = rankings.get(0).getEventId();

                if (!eventRepository.existsById(currentEventId)) {
                    eventRepository.save(new Event(currentEventId));
                    log.info("부모 이벤트 테이블에 이벤트 ID [{}] 등록 완료", currentEventId);
                }

                Set<Integer> targetRanks = new HashSet<>();
                for (int i = 1; i <= 100; i++) {
                    targetRanks.add(i);
                }
                targetRanks.addAll(List.of(500, 1000, 5000, 10000, 30000, 50000, 100000));

                List<EventRankingSnapshot> snapshotList = new ArrayList<>();

                for (RankingResponseDto.RankingItem item : rankings) {
                    Integer rank = item.getRank();
                    if (targetRanks.contains(rank)) {
                        String rawJson = objectMapper.writeValueAsString(item);
                        // null 체크 및 양수 해시코드 변환으로 식별자 보장
                        Long userId = item.getUserId() != null ? item.getUserId() : Math.abs((long) item.getName().hashCode());

                        // 1. 랭킹 스냅샷 추가
                        snapshotList.add(new EventRankingSnapshot(
                                currentEventId,
                                userId,
                                rank,
                                item.getScoreAsLong(),
                                now
                        ));

                        // 2. 유저 상세 프로필(카드/어셋) 수집 및 DB 저장 (요약 JSON으로 덮어쓰는 문제 원천 차단)
                        fetchAndSavePlayerProfile(userId, item.getName(), rawJson, headers);
                    }
                }

                // 3. 스냅샷 일괄 저장 (Batch Insert)
                if (!snapshotList.isEmpty()) {
                    snapshotRepository.saveAll(snapshotList);
                }
                log.info("데이터 저장이 완료되었습니다. (시각: {})", now);
            }
        } catch (Exception e) {
            log.error("sekai.best로부터 랭킹 정보를 가져오는데 실패했습니다.", e);
        }
    }

    private void fetchAndSavePlayerProfile(Long userId, String playerName, String fallbackRawJson, HttpHeaders headers) {
        if (userId == null) return;

        try {
            String profileApiUrl = "https://api.sekai.best/user/" + userId + "/profile?region=jp";

            HttpHeaders customHeaders = new HttpHeaders();
            customHeaders.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            customHeaders.set("Accept", "application/json, text/plain, */*");
            customHeaders.set("Referer", "https://sekai.best/");
            customHeaders.set("Origin", "https://sekai.best");

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> profileResponse = restTemplate.exchange(
                    profileApiUrl, HttpMethod.GET, requestEntity, String.class);

            String profileJson = profileResponse.getBody();
            if (profileJson != null && !profileJson.isBlank() && (profileJson.contains("userCards") || profileJson.contains("userDecks"))) {
                Player player = playerRepository.findById(userId)
                        .orElse(new Player(userId, playerName, profileJson));

                player.setName(playerName);
                player.setRowJson(profileJson); //카드 정보 수정
                playerRepository.save(player);
                log.info("★ [수집 성공] 유저 [{}] ({}) 프로필 DB 저장 완료!", playerName, userId);
                return;
            }

            // sekai.best API 차단 방지를 위한 30ms 딜레이
            Thread.sleep(30);

        } catch (Exception e) {
            log.warn("유저 [{}] ({}) 상세 프로필 수집 실패: {}", playerName, userId, e.getMessage());
            //saveFallbackPlayer(userId, playerName, fallbackRawJson);
        }
        saveFallbackPlayer(userId, playerName, fallbackRawJson);
    }

    private void saveFallbackPlayer(Long userId, String playerName, String fallbackRawJson) {
        // 프로필 상세 API 호출 실패 시 기존 유저 데이터가 없을 때만 Fallback 저장
        Player player = playerRepository.findById(userId)
                .orElse(new Player(userId, playerName, fallbackRawJson));

        player.setName(playerName);
        // 기존 rowJson이 비어있을 경우에만 요약 JSON 적용
        if (player.getRowJson() == null || player.getRowJson().isBlank()) {
            player.setRowJson(fallbackRawJson);
        }

        playerRepository.save(player);
        log.info("▶ [Fallback 저장] 유저 [{}] ({}) 레코드 보장 완료", playerName, userId);
    }
}