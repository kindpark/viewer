const DEFAULT_CARD = 'https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/res001_no011_after_training.webp';

let chartInstance = null;

document.addEventListener('DOMContentLoaded', () => {
    const loadBtn = document.getElementById('loadBtn');
    if (loadBtn) {
        loadBtn.addEventListener('click', loadLatestRankings);
    }

    loadLatestRankings();
    loadCutoffTrend();
});

async function loadLatestRankings() {
    const eventIdInput = document.getElementById('eventIdInput');
    const eventId = eventIdInput ? eventIdInput.value : '';
    const tableBody = document.getElementById('rankingTableBody');
    tableBody.innerHTML = '<tr><td colspan="4">데이터를 불러오는 중...</td></tr>';

    try {
        const response = await fetch(`/api/events/${eventId}/latest`);
        const data = await response.json();

        tableBody.innerHTML = '';
        if (!Array.isArray(data) || data.length === 0) {
            tableBody.innerHTML = '<tr><td colspan="4">수집된 데이터가 없습니다.</td></tr>';
            return;
        }

        data.forEach(item => {
            const tr = document.createElement('tr');
            tr.style.cursor = 'pointer';

            const actualRank = item.rankNum !== undefined ? item.rankNum : item.rank;
            tr.setAttribute('data-rank', actualRank);

            // 초기 1차 렌더링용 데이터 설정
            const cardImgUrl = item.userIconUrl || DEFAULT_CARD;
            const attrType = item.attrType || 'cool';
            const attrIconUrl = `https://storage.sekai.best/sekai-jp-assets/icon/attr/${attrType}.png`;
            const rarity = item.rarity || 4;
            const isAfterTraining = item.isAfterTraining === true;
            const starClass = isAfterTraining ? 'star after-training' : 'star normal';
            const starsHtml = Array(rarity).fill(`<span class="${starClass}">★</span>`).join('');
            const cardLevel = item.cardLevel || 1;

            // DOM 요소 고유 구분을 위해 userId 식별자 등록
            const rowClassId = item.userId ? `user-row-${item.userId}` : '';

            tr.innerHTML = `
                <td>${actualRank}</td>
                <td>
                    <div class="card-frame ${rowClassId}">
                        <!-- Layer 1: 메인 썸네일 -->
                        <img class="card-thumb"
                             src="${cardImgUrl}"
                             alt="card profile"
                             referrerpolicy="no-referrer"
                             onerror="this.onerror=null; this.src='${DEFAULT_CARD}';" />

                        <!-- Layer 2: 좌상단 속성 아이콘 -->
                        <img class="card-attr"
                             src="${attrIconUrl}"
                             alt="${attrType}"
                             onerror="this.style.display='none';" />

                        <!-- Layer 3: 좌하단 레어리티 별 -->
                        <div class="card-stars">${starsHtml}</div>

                        <!-- Layer 4: 우하단 카드 레벨 -->
                        <div class="card-level">Lv.${cardLevel}</div>
                    </div>
                </td>
                <td>${escapeHtml(item.playerName || item.userName)}</td>
                <td>${item.score ? Number(item.score).toLocaleString() : 0}</td>
            `;

            // 단일 등수 클릭 이벤트
            tr.addEventListener('click', (e) => {
                const targetTr = e.currentTarget;
                const clickedRank = targetTr.getAttribute('data-rank');

                if (clickedRank && clickedRank !== 'undefined') {
                    loadSingleRankTrend(eventId, clickedRank);
                }
            });

            tableBody.appendChild(tr);
        });
    } catch (err) {
        console.error("리더보드 로딩 실패", err);
        tableBody.innerHTML = '<tr><td colspan="4">데이터 로딩 실패</td></tr>';
    }
}
/**
 * 외부 프로필 API를 비동기 조회의해 카드 썸네일 및 오버레이 에셋을 실시간 업데이트
 */
async function fetchUserProfileAndOverlays(userId, cardFrameElement) {
    if (!cardFrameElement) return;

    try {
        // sekai.best 유저 프로필 조회 API 엔드포인트
        const res = await fetch(`https://api.sekai.best/user/${userId}/profile`);
        if (!res.ok) return;

        const data = await res.json();

        // 1. 착용 대표 카드 데이터 추출 (유저 착용 카드 정보)
        const userCard = data.userCard || data.userDeck?.leaderCard || null;
        if (!userCard) return;

        const assetbundleName = userCard.assetbundleName || userCard.card?.assetbundleName;
        const defaultImage = userCard.defaultImage || (userCard.specialTrainingStatus === 'done' ? 'after_training' : 'normal');

        // 2. 카드 썸네일 URL 조합 (sekai.best CDN)
        if (assetbundleName) {
            const thumbUrl = `https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/${assetbundleName}_${defaultImage}.webp`;
            const thumbImg = cardFrameElement.querySelector('.card-thumb');
            if (thumbImg) thumbImg.src = thumbUrl;
        }

        // 3. 카드 속성(Attribute) 갱신
        const attr = userCard.attr || userCard.card?.attr;
        if (attr) {
            const attrImg = cardFrameElement.querySelector('.card-attr');
            if (attrImg) {
                attrImg.src = `https://storage.sekai.best/sekai-jp-assets/icon/attr/${attr}.png`;
                attrImg.style.display = 'block';
            }
        }

        // 4. 레어리티 및 각성 상태별 별(Star) 갱신
        const rarity = userCard.rarity || userCard.card?.rarity || 4;
        const isAfterTraining = userCard.specialTrainingStatus === 'done' || defaultImage === 'after_training';
        const starClass = isAfterTraining ? 'star after-training' : 'star normal';
        const starsContainer = cardFrameElement.querySelector('.card-stars');
        if (starsContainer) {
            starsContainer.innerHTML = Array(rarity).fill(`<span class="${starClass}">★</span>`).join('');
        }

        // 5. 카드 레벨 갱신
        const level = userCard.level || userCard.cardLevel;
        if (level) {
            const levelDiv = cardFrameElement.querySelector('.card-level');
            if (levelDiv) levelDiv.innerText = `Lv.${level}`;
        }

    } catch (err) {
        // CORS, 404, 네트워크 차단 시 기본 정적 카드 오버레이 상태 유지
    }
}
// 단일 등수 커트라인 조회
async function loadSingleRankTrend(eventId, rankNum) {
    const chartTitle = document.getElementById('chartTitle');
    try {
        const response = await fetch(`/api/events/${eventId}/cutoffs?ranks=${rankNum}`);
        if (!response.ok) throw new Error(`컷트라인 에러: ${response.status}`);

        const result = await response.json();
        const trendData = result.trendData;

        if (!Array.isArray(trendData) || trendData.length === 0) {
            destroyChart();
            return;
        }

        const labels = trendData.map(item =>
            new Date(item.timestamp).toLocaleTimeString([], { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
        );
        const latestPoint = trendData[trendData.length - 1];
        const latestScore = latestPoint.rankScores ? (latestPoint.rankScores[rankNum] ?? latestPoint.rankScores[String(rankNum)]) : null;
        const latestTimeStr = new Date(latestPoint.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
        if (chartTitle) chartTitle.innerText = `이벤트 ${eventId} - ${rankNum}위 컷트라인 점수 추이`;

        const datasets = [{
            label: `${rankNum}위 컷 점수`,
            data: trendData.map(point => {
                if (!point.rankScores) return null;
                return point.rankScores[rankNum] ?? point.rankScores[String(rankNum)] ?? null;
            }),
            borderColor: 'rgb(75, 192, 192)',
            backgroundColor: 'rgba(75, 192, 192, 0.2)',
            tension: 0.2,
            spanGaps: true,
            fill: true
        }];

        renderChart(labels, datasets);

    } catch (err) {
        console.error("단일 컷트라인 로딩 에러", err);
        destroyChart();
    }
}

// 주요 등수 커트라인 조회
async function loadCutoffTrend() {
    const eventIdInput = document.getElementById('eventIdInput');
    const eventId = eventIdInput ? eventIdInput.value : '';
    const chartTitle = document.getElementById('chartTitle');
    if (chartTitle) chartTitle.innerText = `이벤트 ${eventId} 주요 등수 컷트라인 점수 추이`;

    const targetRanks = "100,500,1000,5000,10000,30000,50000,100000";

    try {
        const response = await fetch(`/api/events/${eventId}/cutoffs?ranks=${targetRanks}`);
        if (!response.ok) throw new Error(`컷트라인 에러: ${response.status}`);

        const result = await response.json();
        const trendData = result.trendData;

        if (!Array.isArray(trendData) || trendData.length === 0) {
            destroyChart();
            return;
        }

        const labels = trendData.map(item =>
            new Date(item.timestamp).toLocaleTimeString([], { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
        );

        const ranksList = targetRanks.split(',').map(Number);
        const colors = [
            'rgb(255, 99, 132)',
            'rgb(54, 162, 235)',
            'rgb(255, 206, 86)',
            'rgb(75, 192, 192)',
            'rgb(153, 102, 255)',
            'rgb(255, 159, 64)',
            'rgb(75, 207, 250)',
            'rgb(201, 203, 207)'
        ];

        const datasets = ranksList.map((rank, index) => ({
            label: `${rank}위 컷`,
            data: trendData.map(point => {
                if (!point.rankScores) return null;
                return point.rankScores[rank] ?? point.rankScores[String(rank)] ?? null;
            }),
            borderColor: colors[index % colors.length],
            backgroundColor: colors[index % colors.length],
            tension: 0.2,
            spanGaps: true,
            fill: false
        }));

        renderChart(labels, datasets);

    } catch (err) {
        console.error("컷트라인 로딩 에러", err);
        destroyChart();
    }
}

// 차트 객체 파괴 함수
function destroyChart() {
    if (chartInstance) {
        chartInstance.destroy();
        chartInstance = null;
    }
}

// Chart.js 렌더링
function renderChart(labels, datasets) {
    const canvas = document.getElementById('trendChart');
    if (!canvas) return;

    const ctx = canvas.getContext('2d');

    destroyChart();

    chartInstance = new Chart(ctx, {
        type: 'line',
        data: {
            labels: labels,
            datasets: datasets
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: {
                mode: 'index',
                intersect: false,
            },
            scales: {
                y: {
                    reverse: false,
                    title: { display: true, text: '이벤트 점수 (Event Pt)' },
                    ticks: {
                        callback: function(value) {
                            return Number(value).toLocaleString();
                        }
                    }
                },
                x: {
                    title: { display: true, text: '수집 시간' }
                }
            }
        }
    });
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/&/g, "&amp;")
               .replace(/</g, "&lt;")
               .replace(/>/g, "&gt;")
               .replace(/"/g, "&quot;")
               .replace(/'/g, "&#039;");
}