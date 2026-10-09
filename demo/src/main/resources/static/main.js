const DEFAULT_CARD = 'https://storage.sekai.best/sekai-jp-assets/thumbnail/chara/res001_no001_normal.webp';
const MAIN_RANKS = '100,500,1000,5000,10000,30000,50000,100000';
const REFRESH_INTERVAL_MS = 5 * 60 * 1000; // 5분마다 자동 갱신

let chartInstance = null;
let currentEventId = null;

document.addEventListener('DOMContentLoaded', async () => {
    const select = document.getElementById('eventSelect');
    if (select) {
        select.addEventListener('change', () => selectEvent(select.value));
    }

    await loadEventOptions();

    // 선택된 이벤트 데이터를 주기적으로 갱신
    setInterval(() => {
        if (currentEventId) refreshAll(currentEventId);
    }, REFRESH_INTERVAL_MS);
});

/** 이벤트 목록을 채우고 현재 진행 중(또는 최신) 이벤트를 자동 선택 */
async function loadEventOptions() {
    const select = document.getElementById('eventSelect');
    if (!select) return;

    try {
        const [eventsRes, currentRes] = await Promise.all([
            fetch('/api/events'),
            fetch('/api/events/current')
        ]);
        if (!eventsRes.ok) throw new Error(`HTTP 에러: ${eventsRes.status}`);

        const events = await eventsRes.json();
        select.innerHTML = '';

        if (!Array.isArray(events) || events.length === 0) {
            select.innerHTML = '<option value="">수집된 이벤트가 없습니다</option>';
            return;
        }

        events.forEach(event => {
            const option = document.createElement('option');
            option.value = event.eventId;
            option.textContent = `[${event.eventId}회] ${event.name}`;
            select.appendChild(option);
        });

        // /current 응답이 비어 있을 수 있음(204)
        let current = null;
        if (currentRes.ok && currentRes.status !== 204) {
            current = await currentRes.json();
        }
        const initialId = current ? current.eventId : events[0].eventId;
        select.value = String(initialId);
        selectEvent(initialId);
    } catch (err) {
        console.error('이벤트 목록 로딩 실패', err);
        select.innerHTML = '<option value="">이벤트 목록 로드 실패</option>';
    }
}

function selectEvent(eventId) {
    if (!eventId) return;
    currentEventId = eventId;

    const select = document.getElementById('eventSelect');
    const selectedText = select && select.selectedOptions[0] ? select.selectedOptions[0].textContent : `이벤트 ${eventId}`;
    const title = document.getElementById('rankingTitle');
    if (title) title.innerText = `최신 랭킹 리더보드 - ${selectedText}`;

    refreshAll(eventId);
}

function refreshAll(eventId) {
    loadLatestRankings(eventId);
    loadCutoffTrend(eventId);
}

/** 랭킹 조회 */
async function loadLatestRankings(eventId) {
    const tableBody = document.getElementById('rankingTableBody');
    if (!tableBody || !eventId) return;
    tableBody.innerHTML = '<tr><td colspan="4">데이터를 불러오는 중...</td></tr>';

    try {
        const response = await fetch(`/api/events/${eventId}/latest`);
        if (!response.ok) throw new Error(`HTTP 에러: ${response.status}`);
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

            const cardImgUrl = item.userIconUrl || DEFAULT_CARD;
            const attrType = item.attrType || '';
            const attrIconUrl = attrType
                ? `https://storage.sekai.best/sekai-jp-assets/icon/attr/${attrType}.png`
                : '';
            const rarity = item.rarity || 0;
            const isAfterTraining = item.isAfterTraining === true;
            const starClass = isAfterTraining ? 'star after-training' : 'star normal';
            const starsHtml = Array(rarity).fill(`<span class="${starClass}">★</span>`).join('');
            const cardLevel = item.cardLevel;

            tr.innerHTML = `
                <td>${actualRank}</td>
                <td>
                    <div class="card-frame">
                        <img class="card-thumb"
                             src="${escapeHtml(cardImgUrl)}"
                             alt="card profile"
                             referrerpolicy="no-referrer"
                             onerror="this.onerror=null; this.src='${DEFAULT_CARD}';" />
                        ${attrIconUrl ? `<img class="card-attr"
                             src="${escapeHtml(attrIconUrl)}"
                             alt="${escapeHtml(attrType)}"
                             onerror="this.style.display='none';" />` : ''}
                        <div class="card-stars">${starsHtml}</div>
                        ${cardLevel ? `<div class="card-level">Lv.${Number(cardLevel)}</div>` : ''}
                    </div>
                </td>
                <td>${escapeHtml(item.playerName || item.userName)}</td>
                <td>${item.score ? Number(item.score).toLocaleString() : 0}</td>
            `;

            tr.addEventListener('click', () => {
                if (actualRank !== undefined && actualRank !== null) {
                    loadSingleRankTrend(eventId, actualRank);
                }
            });

            tableBody.appendChild(tr);
        });
    } catch (err) {
        console.error('리더보드 로딩 실패', err);
        tableBody.innerHTML = '<tr><td colspan="4">데이터 로딩 실패</td></tr>';
    }
}

/** 단일 등수 커트라인 조회 */
async function loadSingleRankTrend(eventId, rankNum) {
    const chartTitle = document.getElementById('chartTitle');
    try {
        const response = await fetch(`/api/events/${eventId}/cutoffs?ranks=${rankNum}`);
        if (!response.ok) throw new Error(`컷트라인 에러: ${response.status}`);

        const trendData = (await response.json()).trendData;
        if (!Array.isArray(trendData) || trendData.length === 0) {
            destroyChart();
            return;
        }

        if (chartTitle) chartTitle.innerText = `이벤트 ${eventId} - ${rankNum}위 컷트라인 점수 추이`;

        const datasets = [{
            label: `${rankNum}위 컷 점수`,
            data: trendData.map(point => pickScore(point, rankNum)),
            borderColor: 'rgb(75, 192, 192)',
            backgroundColor: 'rgba(75, 192, 192, 0.2)',
            tension: 0.2,
            spanGaps: true,
            fill: true
        }];

        renderChart(makeLabels(trendData), datasets);
    } catch (err) {
        console.error('단일 컷트라인 로딩 에러', err);
        destroyChart();
    }
}

/** 주요 등수 커트라인 조회 */
async function loadCutoffTrend(eventId) {
    const chartTitle = document.getElementById('chartTitle');
    if (chartTitle) chartTitle.innerText = `이벤트 ${eventId} 주요 등수 컷트라인 점수 추이`;

    try {
        const response = await fetch(`/api/events/${eventId}/cutoffs?ranks=${MAIN_RANKS}`);
        if (!response.ok) throw new Error(`컷트라인 에러: ${response.status}`);

        const trendData = (await response.json()).trendData;
        if (!Array.isArray(trendData) || trendData.length === 0) {
            destroyChart();
            return;
        }

        const colors = [
            'rgb(255, 99, 132)', 'rgb(54, 162, 235)', 'rgb(255, 206, 86)', 'rgb(75, 192, 192)',
            'rgb(153, 102, 255)', 'rgb(255, 159, 64)', 'rgb(75, 207, 250)', 'rgb(201, 203, 207)'
        ];

        const datasets = MAIN_RANKS.split(',').map(Number).map((rank, index) => ({
            label: `${rank}위 컷`,
            data: trendData.map(point => pickScore(point, rank)),
            borderColor: colors[index % colors.length],
            backgroundColor: colors[index % colors.length],
            tension: 0.2,
            spanGaps: true,
            fill: false
        }));

        renderChart(makeLabels(trendData), datasets);
    } catch (err) {
        console.error('컷트라인 로딩 에러', err);
        destroyChart();
    }
}

function pickScore(point, rank) {
    if (!point.rankScores) return null;
    return point.rankScores[rank] ?? point.rankScores[String(rank)] ?? null;
}

function makeLabels(trendData) {
    return trendData.map(item =>
        new Date(item.timestamp).toLocaleString([], {
            month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
        })
    );
}

function destroyChart() {
    if (chartInstance) {
        chartInstance.destroy();
        chartInstance = null;
    }
}

function renderChart(labels, datasets) {
    const canvas = document.getElementById('trendChart');
    if (!canvas) return;

    destroyChart();

    chartInstance = new Chart(canvas.getContext('2d'), {
        type: 'line',
        data: { labels, datasets },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            interaction: { mode: 'index', intersect: false },
            scales: {
                y: {
                    title: { display: true, text: '이벤트 점수 (Event Pt)' },
                    ticks: { callback: value => Number(value).toLocaleString() }
                },
                x: {
                    title: { display: true, text: '수집 시간' }
                }
            }
        }
    });
}

function escapeHtml(text) {
    if (text === null || text === undefined) return '';
    return String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}