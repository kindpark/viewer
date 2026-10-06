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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class SekaiCrawlerScheduler {
    private final RestTemplate restTemplate = new RestTemplate();
    private final EventRankingSnapshotRepository snapshotRepository;
    private final PlayerRepository playerRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EventRepository eventRepository;

    //10분마다 프로그램이 실행되게
    @Scheduled(initialDelay = 3000, fixedRate = 600000)
    public void collectRankingData() {
        log.info("===수집 시작===");
        String rankingApiUrl = "https://api.sekai.best/event/live?region=jp";
        //Integer currentEventId = getCurrentLiveEventId();
        try {
            //String rankingApiUrl = "https://sekai.best/api/event/";
            //API 응답 객체
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
                //랭킹 정보 탐색
                for (RankingResponseDto.RankingItem item : rankings) {
                    //json
                    String rawJson = objectMapper.writeValueAsString(item);
                    //유저 정보 최신화
                    Long userId = item.getUserId() != null ? item.getUserId() : (long) item.getName().hashCode();
                    playerRepository.save(new Player(userId, item.getName(), rawJson));
                    //10분 단위로 정보 저장함
                    EventRankingSnapshot snapshot = new EventRankingSnapshot(
                            currentEventId,
                            userId,
                            item.getRank(),
                            item.getScoreAsLong(),
                            now
                    );

                    snapshotRepository.save(snapshot);
                    fetchAndSavePlayerProfile(userId, item.getName(), headers);
                }
                log.info("데이터 저장이 {}에 완료됨.", now);
            }
        } catch (Exception e) {
            log.error("sekai.best로부터 랭킹 정보를 가져오는데 실패했습니다.", e);
        }
    }
    private void fetchAndSavePlayerProfile(Long userId, String playerName, HttpHeaders headers) {
        if (userId == null || userId < 0) return; // 식별 불가능한 유저는 패스

        try {
            String profileApiUrl = "https://api.sekai.best/user/" + userId + "/profile?region=jp";
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<String> profileResponse = restTemplate.exchange(
                    profileApiUrl, HttpMethod.GET, requestEntity, String.class);

            String profileJson = profileResponse.getBody();

            // 가져온 프로필 JSON(덱/카드 어셋 포함)으로 Player 엔티티 저장/업데이트
            if (profileJson != null && !profileJson.isBlank()) {
                Player player = playerRepository.findById(userId)
                        .orElse(new Player(userId, playerName, profileJson));

                player.setName(playerName);
                player.setRowJson(profileJson); // ★ 카드 정보가 포함된 원본 JSON 적재
                playerRepository.save(player);
            }

            // sekai.best API 차단 방지를 위한 30ms 딜레이
            Thread.sleep(30);

        } catch (Exception e) {
            log.warn("유저 [{}] 프로필 수집 실패: {}", userId, e.getMessage());
            // 프로필 API 호출 실패 시 이름만 기본 저장 처리 (Fallback)
            if (!playerRepository.existsById(userId)) {
                playerRepository.save(new Player(userId, playerName, null));
            }
        }
    }
}