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

            //API 응답 구조(rankNum 또는 rank)에서 정확한 등수 값 안전하게 추출
            const actualRank = item.rankNum !== undefined ? item.rankNum : item.rank;

            //DOM dataset 속성에 확실하게 주입
            tr.setAttribute('data-rank', actualRank);

            const defaultCard = 'https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/res001_no011_after_training.webp';
            const cardImgUrl = item.userIconUrl || defaultCard;
            // 2. 속성(Attribute) 아이콘 경로 (기본값: cool)
                        const attrType = item.attrType || 'cool';
                        const attrIconUrl = `https://storage.sekai.best/sekai-jp-assets/icon/attr/${attrType}.png`;

                        // 3. 레어리티 별(Star) HTML 생성 (각성 완료 시 보라색 별, 미각성 시 노란색 별)
                        const rarity = item.rarity || 4;
                        const isAfterTraining = item.isAfterTraining === true;
                        const starClass = isAfterTraining ? 'star after-training' : 'star normal';
                        const starsHtml = Array(rarity).fill(`<span class="${starClass}">★</span>`).join('');

                        // 4. 카드 레벨
                        const cardLevel = item.cardLevel || 1;
// 5. 오버레이 레이어가 합성된 innerHTML 구성
            tr.innerHTML = `
                <td>${actualRank}</td>
                <td>
                    <div class="card-frame">
                        <!-- Layer 1: 메인 썸네일 -->
                        <img class="card-thumb"
                             src="${cardImgUrl}"
                             alt="card profile"
                             referrerpolicy="no-referrer"
                             onerror="this.onerror=null; this.src='${defaultCard}';" />

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
                <td>${escapeHtml(item.playerName)}</td>
                <td>${item.score ? Number(item.score).toLocaleString() : 0}</td>
            `;

            // [수정 3] 클릭 시 e.currentTarget.getAttribute('data-rank')로 확실하게 가져옴
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