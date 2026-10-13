package com.example.demo.service; // 기존 파일의 package 줄에 맞추세요

import com.example.demo.dto.CutoffTrendResponseDto;
import com.example.demo.dto.SnapshotDto;
import com.example.demo.entity.CardMaster;
import com.example.demo.entity.Event;
import com.example.demo.entity.EventRankingSnapshot;
import com.example.demo.repository.CardMasterRepository;
import com.example.demo.repository.EventRankingSnapshotRepository;
import com.example.demo.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RankingService {

    // 기본 대체 썸네일 (CDN 직접 사용)
    private static final String DEFAULT_ICON_URL =
            "https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/res001_no001_normal.webp";

    private final EventRankingSnapshotRepository snapshotRepository;
    private final CardMasterRepository cardMasterRepository;
    private final EventRepository eventRepository;

    // 최신 리더보드: 스냅샷에 저장된 cardId로 카드 마스터를 매핑
    //캐시 히트일때도 DB 커넥션이 열릴수 있어서, 캐시랑 부딪힐수 있는 트렌젝션 삭제하고 캐시 추가

    @Cacheable(value="lastestRanking", key="#eventId", sync=true)
    public List<SnapshotDto> getLatestRanking(Integer eventId) {
        List<EventRankingSnapshot> snapshots = snapshotRepository.findLatestRankingByEventId(eventId);
        if (snapshots.isEmpty()) {
            return List.of();
        }

        Set<Long> cardIds = snapshots.stream()
                .map(EventRankingSnapshot::getCardId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, CardMaster> cardMap = cardMasterRepository.findAllById(cardIds).stream()
                .collect(Collectors.toMap(CardMaster::getId, c -> c, (a, b) -> a));

        log.debug("리더보드 조회 event={} 카드 {}종 / 마스터 매핑 {}종", eventId, cardIds.size(), cardMap.size());

        return snapshots.stream().map(s -> {
            String name = (s.getUserName() != null && !s.getUserName().isBlank()) ? s.getUserName() : "Unknown";
            String iconUrl = DEFAULT_ICON_URL;
            String attrType = null;
            Integer rarity = null;
            boolean afterTraining = false;

            CardMaster card = s.getCardId() != null ? cardMap.get(s.getCardId()) : null;
            if (card != null) {
                rarity = card.getRarity();
                attrType = card.getAttrType();
                // 3성 미만 카드는 특훈 후 이미지가 없으므로 normal로 대체
                boolean canAfter = rarity == null || rarity >= 3;
                afterTraining = Boolean.TRUE.equals(s.getAfterTraining()) && canAfter;
                iconUrl = afterTraining ? card.getAfterTrainingImageUrl() : card.getNormalImageUrl();
            } else if (s.getCardId() != null) {
                log.warn("카드 마스터에 없는 cardId={} (user {})", s.getCardId(), s.getUserId());
            }

            return new SnapshotDto(
                    s.getUserId(),
                    name,
                    s.getRankNum(),
                    s.getScore(),
                    s.getRecordedAt(),
                    iconUrl,
                    attrType,
                    rarity,
                    afterTraining,
                    s.getCardLevel()
            );
        }).collect(Collectors.toList());
    }

    /** 데이터가 수집된 이벤트 목록 (최신순) - 드롭다운용 */
    @Transactional(readOnly = true)
    public List<Event> getEventsWithData() {
        List<Integer> ids = snapshotRepository.findCollectedEventIds();
        if (ids.isEmpty()) return List.of();
        return eventRepository.findAllById(ids).stream()
                .sorted(Comparator.comparing(Event::getEventId, Comparator.reverseOrder()))
                .collect(Collectors.toList());
    }

    /** 현재 진행 중인 이벤트, 없으면 데이터가 있는 가장 최근 이벤트 */
    @Transactional(readOnly = true)
    public Optional<Event> getCurrentEvent() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> withData = getEventsWithData();
        return withData.stream()
                .filter(e -> e.getStartAt() != null && e.getAggregateAt() != null
                        && !now.isBefore(e.getStartAt()) && !now.isAfter(e.getAggregateAt()))
                .findFirst()
                .or(() -> withData.stream().findFirst());
    }

    @Cacheable(value = "singleRankTrend",
            key = "#eventId + '_' + #targetRanks.toString()",
            sync = true)
    public CutoffTrendResponseDto getCutoffTrends(Integer eventId, List<Integer> targetRanks) {
        if (targetRanks == null || targetRanks.isEmpty()) {
            return new CutoffTrendResponseDto(eventId, Collections.emptyList());
        }
/*
        List<EventRankingSnapshot> snapshots =
                snapshotRepository.findCutoffTrendsByEventIdAndRanks(eventId, targetRanks);

        if (snapshots.isEmpty()) {
            return new CutoffTrendResponseDto(eventId, Collections.emptyList());
        }
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
*/
        //시점 목록만 조회한다. 일단 15개로 샘플링
        List<LocalDateTime> allTimes = snapshotRepository.findDistinctRecordedAtByEventId(eventId);
        if(allTimes.isEmpty()){
            return new CutoffTrendResponseDto(eventId, Collections.emptyList());
        }
        List<LocalDateTime> sampledTimes = sampleTimes(allTimes, 15);

        //요청 등수 조회(전에 쓴 메소드 대비 행이 1/100수준으로 절약됨
        List<EventRankingSnapshot> snapshots = snapshotRepository.findByEventIdAndRankNumInAndRecordedAtIn(eventId, targetRanks, sampledTimes);

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
        List<CutoffTrendResponseDto.RankTrendPoint> trendData = sampledTimes.stream()
                .map(time->new CutoffTrendResponseDto.RankTrendPoint(
                        time, groupedByTime.getOrDefault(time, Map.of())
                )).collect(Collectors.toList());
        return new CutoffTrendResponseDto(eventId, trendData);
    }

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
            sampled.add(latestTime);
        }
        return sampled;
    }
}