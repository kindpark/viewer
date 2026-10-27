[qwmvkdid.duckdns.org](https://qwmvkdid.duckdns.org/)

# 📊 게임 실시간 랭킹 & 커트라인 추이 서비스 (sekai-viewer)

> sekai.best의 JSON API와 GitHub에 공개된 마스터 데이터를 5분마다 수집해 DB에 저장하고, 이벤트별 상위 랭킹과 컷트라인 점수 추이를 보여주는 웹 서비스입니다. 카드 이미지는 서버를 거치지 않고 방문자의 브라우저가 외부 CDN에서 직접 불러오며, 조회 결과는 서버 메모리 캐시와 HTTP 캐시로 재사용해 DB 조회와 서버 부담을 줄이도록 설계했습니다.

---

## 💡 개발 배경

3학년 여름방학 당시 개인 NAS를 통해 실시간 랭킹 사이트를 제작해 배포했으나, 10명정도의 동시 이용자 발생 시 **서버 부하 문제**와 **동적 프로필 이미지 로딩 실패** 등의 한계를 경험했습니다. 

이번 프로젝트는 기존 프로젝트의 문제점을 개선하여 **안정적인 데이터 수집, 서버 트래픽 최적화, UI/UX 개선**을 목표로 재설계 및 개발했습니다.

---

## 📱 주요 화면 및 기능

### 1. 실시간 랭킹 & 유저 점수 추이 (데스크톱 / 모바일)

5분 간격으로 수집되는 실시간 랭킹 정보를 확인하고, 각 유저를 클릭하여 점수 변화 그래프를 관찰할 수 있습니다.

| 데스크톱 뷰 | 모바일/태블릿 뷰 |
| :---: | :---: |
| <img width="885" alt="Desktop View" src="https://github.com/user-attachments/assets/a3118558-ebae-4bba-8ea3-6f1684934932" /> | <img width="698" alt="Mobile View" src="https://github.com/user-attachments/assets/a2a45d10-abf1-47e8-ac90-8663fc39c8c0" /> |

---

### 2. 지난 이벤트 랭킹 조회

프로젝트 실행 이후 기록된 이벤트들의 랭킹 및 커트라인 데이터를 언제든지 조회할 수 있습니다.

<img width="918" alt="Past Event Analysis" src="https://github.com/user-attachments/assets/9f3d29b6-60b3-4019-a704-927c48283417" />

---

### 3. 주요 커트라인 필터링

상단 범례의 등수 칸을 클릭하여 원하는 주요 커트라인(10위, 100위 등)만 선택적으로 그래프에 표시할 수 있습니다.

<img width="432" height="539" alt="image" src="https://github.com/user-attachments/assets/a5938084-d2a2-49bb-8330-1c0b4b6c576f" />


---

## 🏗 시스템 아키텍처 & 플로우

### 1. 데이터 수집 및 백그라운드 스케줄링

매일 새벽 4시 마스터 데이터 동기화 및 10분 주기 스냅샷 수집을 진행합니다.

<img width="1076" alt="Data Flow Chart" src="https://github.com/user-attachments/assets/ddaca5aa-7f5f-44bc-acd4-ca5572770610" />

### 2. 사용자 요청 처리 흐름

클라이언트 요청 시 서버 내부 DB 및 CDN 주소를 조합하여 랭킹 및 리더보드 데이터를 반환합니다.

<img width="1126" alt="User Request Flow" src="https://github.com/user-attachments/assets/81b93014-fed2-4555-a8d0-b8c71e994509" />

### 3. 시스템 아키텍쳐 다이어그램

<img width="1060" height="822" alt="image" src="https://github.com/user-attachments/assets/a31d29da-cd2b-4c19-a711-57faf82058f9" />


---

## 🗄 데이터베이스 구조 (Data Schema)

* **`event`**: 이벤트 ID, 이벤트명, 진행 기간
* **`card_master`**: 카드 이미지 CDN URL, 속성, 레어리티 정보
* **`event_ranking_snapshot`**: 수집 시각별 순위, 점수, 리더 카드 ID 및 각성 여부
* **`player`**: 유저 원본 JSON 데이터 (디버깅/기록용)

---

## 🚀 성과 및 최적화 포인트

1. **외부 API 트래픽 최소화**:
   - 스케줄러가 5분마다 1회만 외부 API (`api.sekai.best`)를 호출하여 DB에 캐싱합니다.
   - 사용자 방문자 수에 비례하여 외부 API에 가해지는 요청 부하가 증가하지 않습니다.

2. **서버 대역폭 부하 절감**:
   - 카드 이미지를 백엔드 서버에서 직접 리턴하지 않고, `card_master` 테이블의 **외부 CDN URL**을 브라우저에 전달합니다.
   - 서버의 네트워크 대역폭 소비를 대폭 줄여 안정적인 응답 속도를 유지합니다.

---

## ⚠️ 한계점 & 향후 개선 과제

* **이벤트 종료 타이밍의 스냅샷 누락 가능성**:
  - 서버 다운으로 인해 이벤트 종료 시점에 랭킹을 수집하지 못하고 다음 이벤트가 시작되면, 라이브 API 특성상 이전 이벤트의 최종 스냅샷 저장이 불가능합니다. (마지막 수집 시점까지의 데이터만 유실 없이 보존됨)
