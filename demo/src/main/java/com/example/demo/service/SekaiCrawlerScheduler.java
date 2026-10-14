package com.example.demo.service; // 기존 파일의 package 줄에 맞추세요

import com.example.demo.dto.CardJsonDto;
import com.example.demo.dto.EventJsonDto;
import com.example.demo.dto.RankingResponseDto;
import com.example.demo.entity.CardMaster;
import com.example.demo.entity.Event;
import com.example.demo.entity.EventRankingSnapshot;
import com.example.demo.entity.Player;
import com.example.demo.repository.CardMasterRepository;
import com.example.demo.repository.EventRankingSnapshotRepository;
import com.example.demo.repository.EventRepository;
import com.example.demo.repository.PlayerRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.event.EventListener;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class SekaiCrawlerScheduler {

    private final RestTemplate restTemplate = new RestTemplate();
    private final EventRankingSnapshotRepository snapshotRepository;
    private final PlayerRepository playerRepository;
    private final EventRepository eventRepository;
    private final CardMasterRepository cardMasterRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final CacheManager cacheManager;
    private static final String EVENTS_JSON_URL =
            "https://raw.githubusercontent.com/Sekai-World/sekai-master-db-diff/main/events.json";
    private static final String CARDS_JSON_URL =
            "https://raw.githubusercontent.com/Sekai-World/sekai-master-db-diff/main/cards.json";
    private static final String CDN_THUMB_URL =
            "https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/";
    private static final String RANKING_API_URL = "https://api.sekai.best/event/live?region=jp";

    //이벤트 종료(aggregateAt) 후 이 시간이 지난 시점의 스냅샷을 최종본으로 보고 수집을 멈춤
    private static final int FINAL_SNAPSHOT_DELAY_MINUTES = 30;

    @EventListener(ApplicationReadyEvent.class)
    public void initDataOnStartup() {
        log.info("=== 애플리케이션 가동: 마스터 데이터 및 랭킹 초기 동기화 시작 ===");
        syncMasterData();
        collectRankingData();
    }

    @Scheduled(cron = "0 0 4 * * *")
    public void syncMasterData() {
        syncEvents();
        syncCards();
    }

    public void syncEvents() {
        try {
            String json = restTemplate.getForObject(EVENTS_JSON_URL, String.class);
            if (json == null || json.isBlank()) return;

            EventJsonDto[] events = objectMapper.readValue(json, EventJsonDto[].class);
            for (EventJsonDto dto : events) {
                if (dto.getId() == null || dto.getStartAt() == null || dto.getAggregateAt() == null) continue;

                LocalDateTime start = toLocal(dto.getStartAt());
                LocalDateTime aggregate = toLocal(dto.getAggregateAt());

                Event event = eventRepository.findById(dto.getId())
                        .orElse(new Event(dto.getId(), dto.getName(), start, aggregate));
                event.updateInfo(dto.getName(), start, aggregate);
                eventRepository.save(event);
            }
            log.info("이벤트 마스터 동기화 완료 ({}건)", events.length);
        } catch (Exception e) {
            log.error("이벤트 마스터 동기화 에러", e);
        }
    }

    /** cards.json → card_master 테이블 (이미지는 다운로드하지 않고 CDN URL만 저장) */
    public void syncCards() {
        try {
            String json = restTemplate.getForObject(CARDS_JSON_URL, String.class);
            if (json == null || json.isBlank()) return;

            CardJsonDto[] cards = objectMapper.readValue(json, CardJsonDto[].class);
            List<CardMaster> entities = new ArrayList<>();

            for (CardJsonDto dto : cards) {
                if (dto.getId() == null || dto.getAssetbundleName() == null) continue;
                String asset = dto.getAssetbundleName();

                entities.add(new CardMaster(
                        dto.getId(),
                        dto.getCharacterId(),
                        dto.getAttrType(),
                        dto.resolveRarity(),
                        asset,
                        CDN_THUMB_URL + asset + "_normal.webp",
                        CDN_THUMB_URL + asset + "_after_training.webp"
                ));
            }
            cardMasterRepository.saveAll(entities);
            log.info("카드 마스터 동기화 완료 ({}건)", entities.size());
        } catch (Exception e) {
            log.error("카드 마스터 동기화 에러", e);
        }
    }
    //10분마다 랭킹 수집함
    @Scheduled(fixedRate = 600000, initialDelay=600000)
    @Transactional
    public void collectRankingData() {
        log.info("=== 랭킹 데이터 수집 시작 ===");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/154.0.0.0 Safari/537.36");
            headers.set("Accept", "application/json, text/plain, */*");

            ResponseEntity<RankingResponseDto> response = restTemplate.exchange(
                    RANKING_API_URL, HttpMethod.GET, new HttpEntity<Void>(headers), RankingResponseDto.class);

            RankingResponseDto body = response.getBody();
            if (body == null || body.getData() == null || body.getData().getEventRankings() == null) return;

            List<RankingResponseDto.RankingItem> rankings = body.getData().getEventRankings();
            if (rankings.isEmpty()) return;

            Integer currentEventId = rankings.get(0).getEventId();

            // 처음 보는 이벤트면 이름을 가져오기 위해 이벤트 마스터를 즉시 동기화
            Event event = eventRepository.findById(currentEventId).orElse(null);
            if (event == null) {
                syncEvents();
                event = eventRepository.findById(currentEventId)
                        .orElseGet(() -> eventRepository.save(new Event(currentEventId)));
            }

            LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Tokyo")).truncatedTo(ChronoUnit.MINUTES);
            if (snapshotRepository.existsByEventIdAndRecordedAt(currentEventId, now)) {
                log.info("이미 {} 시각 스냅샷이 있어 수집 생략", now);
                return;
            }
            // 이벤트 종료 후: 최종 스냅샷이 이미 저장됐으면 더 쌓지 않음 (이전 이벤트는 그대로 보존)
            if (event.getAggregateAt() != null && now.isAfter(event.getAggregateAt())) {
                LocalDateTime finalAfter = event.getAggregateAt().plusMinutes(FINAL_SNAPSHOT_DELAY_MINUTES);
                if (snapshotRepository.existsByEventIdAndRecordedAtGreaterThanEqual(currentEventId, finalAfter)) {
                    log.info("이벤트 {} 종료 및 최종 집계 저장 완료 - 수집 생략", currentEventId);
                    return;
                }
            }

            Set<Integer> targetRanks = new HashSet<>();
            for (int i = 1; i <= 10; i++) targetRanks.add(i);
            targetRanks.addAll(List.of(20, 30, 50, 100, 200, 300,
                    500, 1000, 2000, 3000, 5000, 10000, 30000, 50000, 100000));

            List<EventRankingSnapshot> snapshotList = new ArrayList<>();
            Map<Long, Player> playerMap = new HashMap<>();

            for (RankingResponseDto.RankingItem item : rankings) {
                Integer rank = item.getRank();
                if (rank == null || !targetRanks.contains(rank)) continue;

                String userName = item.getName();
                Long userId = item.getUserId() != null
                        ? item.getUserId()
                        : (long) Math.abs(String.valueOf(userName).hashCode());

                RankingResponseDto.UserCard card = item.getUserCard();

                snapshotList.add(new EventRankingSnapshot(
                        currentEventId,
                        userId,
                        userName,
                        rank,
                        item.getScoreAsLong(),
                        now,
                        card != null ? card.getCardId() : null,
                        card != null ? card.getLevel() : null,
                        card != null ? item.isAfterTrainingImage() : null
                ));

                String rawJson = null;
                try {
                    rawJson = objectMapper.writeValueAsString(item);
                } catch (Exception e) {
                    log.warn("RankingItem JSON 직렬화 실패 userId: {}", userId);
                }
                playerMap.put(userId, new Player(userId, userName, rawJson));
            }

            if (!snapshotList.isEmpty()) {
                playerRepository.saveAll(playerMap.values());
                snapshotRepository.saveAll(snapshotList);
                evictRankingCachesAfterCommit();
                log.info("랭킹 수집 완료: event {} / {}건 스냅샷, {}명 유저", currentEventId,
                        snapshotList.size(), playerMap.size());
            }

        } catch (Exception e) {
            log.error("크롤링 중 오류 발생", e);
        }
    }

    private LocalDateTime toLocal(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.of("Asia/Tokyo"));
    }
    //DB에 저장된 데이터를 비운다.
    private void evictRankingCachesAfterCommit() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evictRankingCaches();
                }
            });
        } else {
            // 서버 시작 시 호출처럼 트랜잭션 밖에서 실행된 경우
            evictRankingCaches();
        }
    }

    private void evictRankingCaches() {
        for (String name : List.of("latestRanking", "singleRankTrend")) {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) cache.clear();
        }
    }

}