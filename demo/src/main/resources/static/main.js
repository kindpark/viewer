let chartInstance = null;

document.addEventListener('DOMContentLoaded', () => {
    const loadBtn = document.getElementById('loadBtn');
    loadBtn.addEventListener('click', loadLatestRankings);

    loadLatestRankings();
    loadCutoffTrend();
});

async function loadLatestRankings() {
    const eventId = document.getElementById('eventIdInput').value;
    const tableBody = document.getElementById('rankingTableBody');
    tableBody.innerHTML = '<tr><td colspan="3">데이터를 불러오는 중...</td></tr>';

    try {
        const response = await fetch(`/api/events/${eventId}/latest`);
        //if(!response.ok) throw new Error('리더보드 로드 실패');
        const data = await response.json();

        tableBody.innerHTML = '';
        if (!Array.isArray(data) || data.length===0) {
            tableBody.innerHTML = '<tr><td colspan="3">수집된 데이터가 없습니다.</td></tr>';
            return;
        }

        data.forEach(item => {
            const tr = document.createElement('tr');
            tr.style.cursor = 'pointer';

            const defaultCard = 'https://sekai.best/assets/rarity_star_afterTraining-CUlLhfpl.png';
                const cardImgUrl = (item.userIconUrl && !item.userIconUrl.includes('null')) ? item.userIconUrl : defaultCard;
            // 실제 sekai.best 사이트의 카드 프레임 SVG 구조 복원
                const profileSvgHtml = `
                    <div class="card-thumb-wrapper" style="position: relative; width: 50px; height: 50px; display: inline-block; vertical-align: middle;">
                        <svg viewBox="0 0 156 156" style="width: 100%; height: 100%; border-radius: 8px; overflow: hidden;">
                            <!-- 1. 배경 유저 카드 일러스트 -->
                            <image href="${cardImgUrl}" x="0" y="0" width="156" height="156" preserveAspectRatio="xMidYMid slice"
                                   onerror="this.setAttribute('href', '${defaultCard}');" />

                            <!-- 2. 하단 블랙 오버레이 바 -->
                            <rect x="0" y="119" width="156" height="35" fill="black" fill-opacity="0.6"></rect>

                            <!-- 3. 카드 레어리티 테두리 프레임 -->
                            <image href="https://sekai.best/assets/cardFrame_S_4-DkwuVAqt.png" x="0" y="0" width="156" height="156"></image>

                            <!-- 4. 속성 아이콘 (예: Cool) -->
                            <image href="https://sekai.best/assets/icon_attribute_cool-Cm_EFAKA.png" x="2" y="2" width="32" height="32"></image>

                            <!-- 5. 레어리티 별 아이콘 (4성 예시) -->
                            <image href="https://sekai.best/assets/rarity_star_afterTraining-CUlLhfpl.png" x="6" y="122" width="24" height="24"></image>
                            <image href="https://sekai.best/assets/rarity_star_afterTraining-CUlLhfpl.png" x="30" y="122" width="24" height="24"></image>
                            <image href="https://sekai.best/assets/rarity_star_afterTraining-CUlLhfpl.png" x="54" y="122" width="24" height="24"></image>
                            <image href="https://sekai.best/assets/rarity_star_afterTraining-CUlLhfpl.png" x="78" y="122" width="24" height="24"></image>
                        </svg>
                    </div>
                `;
            const defaultIcon = 'https://sekai.best/assets/rarity_star_afterTraining-CUlLhfpl.png';
            const hasValidIcon = item.userIconUrl && item.userIconUrl.trim() !== '' && !item.userIconUrl.endsWith('null');
            const iconSrc = item.userIconUrl || defaultIcon;
            tr.innerHTML = `
                <td>${item.rankNum}</td>
                <td>${profileSvgHtml}</td>
                <td>${escapeHtml(item.playerName)}</td>
                <td>${item.score ? item.score.toLocaleString() : 0}</td>
            `;
            tr.addEventListener('click', ()=>{
            loadSingleRankTrend(eventId, item.rankNum);
            })
            tableBody.appendChild(tr);
        });
    } catch (err) {
        console.error(err);
        tableBody.innerHTML = '<tr><td colspan="3">데이터 로딩 실패</td></tr>';
    }
}
//단일 등수 커트라인 조회
async function loadSingleRankTrend(eventId, rankNum) {
    const chartTitle = document.getElementById('chartTitle');
    chartTitle.innerText = `이벤트 ${eventId} - ${rankNum}위 컷트라인 점수 추이`;

    try {
        // 단 1개의 등수만 백엔드로 요청
        const response = await fetch(`/api/events/${eventId}/cutoffs?ranks=${rankNum}`);
        if (!response.ok) throw new Error(`컷트라인 에러: ${response.status}`);

        const result = await response.json();
        const trendData = result.trendData;

        if (!Array.isArray(trendData) || trendData.length === 0) {
            if (chartInstance) chartInstance.destroy();
            return;
        }

        const labels = trendData.map(item =>
            new Date(item.timestamp).toLocaleTimeString([], { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
        );

        // 단일 선 데이터셋 생성
        const datasets = [{
            label: `${rankNum}위 컷 점수`,
            data: trendData.map(point => point.rankScores[rankNum] || null),
            borderColor: 'rgb(75, 192, 192)',
            backgroundColor: 'rgba(75, 192, 192, 0.2)',
            tension: 0.2,
            fill: true
        }];

        renderChart(labels, datasets);

    } catch (err) {
        console.error("단일 컷트라인 로딩 에러", err);
    }
}
//주요 등수 커트라인 조회
async function loadCutoffTrend() {
    const eventId = document.getElementById('eventIdInput').value;
    const chartTitle = document.getElementById('chartTitle');
    chartTitle.innerText = `이벤트 ${eventId} 주요 등수 컷트라인 점수 추이`;

    // 조회할 컷트라인 등수 설정
    const targetRanks = "100,500,1000,5000,10000,30000,50000,100000";

    try {
        const response = await fetch(`/api/events/${eventId}/cutoffs?ranks=${targetRanks}`);
        if (!response.ok) throw new Error(`컷트라인 에러: ${response.status}`);

        const result = await response.json(); // { eventId: 219, trendData: [...] }
        const trendData = result.trendData;

        if (!Array.isArray(trendData) || trendData.length === 0) {
            if (chartInstance) chartInstance.destroy();
            return;
        }

        // X축 시간 라벨 생성
        const labels = trendData.map(item =>
            new Date(item.timestamp).toLocaleTimeString([], { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
        );

        // API에서 지정한 등수 목록 가져오기 (예: [100, 500, 1000, 5000, 10000])
        const ranksList = targetRanks.split(',').map(Number);

        // 등수별 선 색상지정
        const colors = [
            'rgb(255, 99, 132)',  // 100위 (빨강)
            'rgb(54, 162, 235)',  // 500위 (파랑)
            'rgb(255, 206, 86)',  // 1000위 (노랑)
            'rgb(75, 192, 192)',  // 5000위 (청록)
            'rgb(153, 102, 255)'  // 10000위 (보라)
        ];

        // Chart.js용 datasets 구성
        const datasets = ranksList.map((rank, index) => {
            return {
                label: `${rank}위 컷`,
                data: trendData.map(point => point.rankScores[rank] || null),
                borderColor: colors[index % colors.length],
                backgroundColor: colors[index % colors.length],
                tension: 0.2,
                fill: false
            };
        });

        renderChart(labels, datasets);

    } catch (err) {
        console.error("컷트라인 로딩 에러", err);
    }
}

// 3. Chart.js 랜더링 (점수 추이용)
function renderChart(labels, datasets) {
    const ctx = document.getElementById('trendChart').getContext('2d');

    if (chartInstance) {
        chartInstance.destroy();
    }

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
                    reverse: false, // 점수 그래프이므로 높을수록 위로 감!
                    title: { display: true, text: '이벤트 점수 (Event Pt)' },
                    ticks: {
                        callback: function(value) {
                            return value.toLocaleString(); // 숫자에 쉼표 표시
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