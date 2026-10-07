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
import java.util.*;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class SekaiCrawlerScheduler {

    private final RestTemplate restTemplate = new RestTemplate();
    private final EventRankingSnapshotRepository snapshotRepository;
    private final PlayerRepository playerRepository;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    //cdn 주소에서 에셋명 추출용 정규식 패턴
    private static final Pattern CARD_ASSET_PATTERN =
            Pattern.compile("https://storage\\.sekai\\.best/sekai-jp-assets/thumbnail/chara/([a-zA-Z0-9_]+)\\.webp");
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

            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<RankingResponseDto> response = restTemplate.exchange(
                    rankingApiUrl, HttpMethod.GET, requestEntity, RankingResponseDto.class);

            RankingResponseDto body = response.getBody();

            if (body == null || body.getData() == null || body.getData().getEventRankings() == null) {
                return;
            }

            List<RankingResponseDto.RankingItem> rankings = body.getData().getEventRankings();
            if (rankings.isEmpty()) return;

            Integer currentEventId = rankings.get(0).getEventId();
            if (!eventRepository.existsById(currentEventId)) {
                eventRepository.save(new Event(currentEventId));
            }

            LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

            Set<Integer> targetRanks = new HashSet<>();
            for (int i = 1; i <= 100; i++) targetRanks.add(i);
            targetRanks.addAll(List.of(500, 1000, 5000, 10000, 30000, 50000, 100000));

            List<EventRankingSnapshot> snapshotList = new ArrayList<>();

            for (RankingResponseDto.RankingItem item : rankings) {
                Integer rank = item.getRank();
                if (targetRanks.contains(rank)) {
                    Long userId = item.getUserId() != null ? item.getUserId() : (long) Math.abs(item.getName().hashCode());

                    snapshotList.add(new EventRankingSnapshot(
                            currentEventId,
                            userId,
                            rank,
                            item.getScoreAsLong(),
                            now
                    ));
                    Player player = playerRepository.findById(userId)
                            .orElse(new Player(userId, item.getName(), null));
                    player.setName(item.getName());
                    playerRepository.save(player);
                }
            }

            if (!snapshotList.isEmpty()) {
                snapshotRepository.saveAll(snapshotList);
            }

        } catch (Exception e) {
            log.error("크롤링 중 오류 발생", e);
        }
    }
}