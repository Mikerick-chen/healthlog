// ===== 智慧健康管理平台前端（繁體中文註解）=====

// 後端 API 一律用相對路徑（同源），本機與 Zeabur 部署皆適用，不寫死 host。
const API = '';

let trendChart = null;

// ---------- 工具 ----------
function $(id) { return document.getElementById(id); }
function todayStr() { return new Date().toISOString().slice(0, 10); }
function statusToBadge(s) { return s === 'good' ? 'badge-good' : s === 'warn' ? 'badge-warn' : s === 'danger' ? 'badge-danger' : 'badge-gray'; }
function riskToBadge(r) { return r === '低' ? 'badge-low' : r === '中' ? 'badge-mid' : r === '高' ? 'badge-high' : 'badge-gray'; }

function toast(msg, type = 'info') {
    const t = $('toast');
    t.textContent = msg;
    t.className = `toast toast-${type}`;
    clearTimeout(t._timer);
    t._timer = setTimeout(() => t.classList.add('hidden'), 2800);
}

// 統一 fetch：非 2xx 解析 {error}，網路失敗丟出可讀訊息
async function api(path, options = {}) {
    let res;
    try {
        res = await fetch(API + path, options);
    } catch (e) {
        throw new Error('無法連線後端服務（請確認伺服器是否啟動）');
    }
    if (res.status === 204) return null;
    let data = null;
    const ct = res.headers.get('content-type') || '';
    if (ct.includes('application/json')) data = await res.json();
    if (!res.ok) throw new Error((data && data.error) || `請求失敗（HTTP ${res.status}）`);
    return data;
}

// ---------- 導覽 ----------
const PAGE_META = {
    dashboard: ['總覽儀表板', '即時健康狀態總覽'],
    healthlog: ['健康日誌', '記錄睡眠/步數/心情並由決策樹評估風險'],
    body: ['身體數據', '身高 / 體重 / BMI / 喝水量'],
    vitals: ['生命徵象', '血壓 / 心率 / 體溫 / 血糖'],
    diary: ['健康日記', '記錄每日身心狀況'],
    trends: ['趨勢分析', '單一指標的精準趨勢檢視'],
    analysis: ['資訊增益', '決策樹門檻的計算依據'],
    reports: ['報表匯出', 'Excel 數據檔與 PDF 診斷報告書'],
};

function switchView(view) {
    document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.view === view));
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    $('view-' + view).classList.add('active');
    $('pageTitle').textContent = PAGE_META[view][0];
    $('pageCrumb').textContent = PAGE_META[view][1];

    // 進入視圖時載入對應資料
    if (view === 'dashboard') loadDashboard();
    if (view === 'healthlog') loadHealthLogs();
    if (view === 'body') loadBody();
    if (view === 'vitals') loadVitals();
    if (view === 'diary') loadDiary();
    if (view === 'trends') applyTrend();
}
document.querySelectorAll('.nav-item').forEach(b => b.addEventListener('click', () => switchView(b.dataset.view)));

// ==================== 總覽儀表板 ====================
async function loadDashboard() {
    try {
        const a = await api('/assessment');
        $('dashError').classList.add('hidden');

        // 綜合分數環
        $('scoreNum').textContent = a.compositeScore;
        $('scoreGrade').textContent = a.scoreGrade;
        $('scoreRing').style.setProperty('--val', a.compositeScore);
        const riskBadge = $('dashRisk');
        riskBadge.textContent = a.decisionTreeRisk || '—';
        riskBadge.className = 'badge ' + riskToBadge(a.decisionTreeRisk);
        $('scoreHint').textContent = a.scoreGrade === '優' ? '健康狀態良好，請繼續保持！' : '部分指標需留意，詳見下方判讀。';

        // KPI 卡（風險 / BMI / 血壓 / 血糖）
        const kpis = [
            { label: '今日風險（決策樹）', value: a.decisionTreeRisk || '—', sub: '睡眠→步數→心情', status: a.decisionTreeRisk === '低' ? 'good' : a.decisionTreeRisk === '中' ? 'warn' : 'danger' },
            metricKpi('BMI', a.bmi),
            metricKpi('血壓', a.bloodPressure),
            metricKpi('血糖', a.bloodSugar),
        ];
        $('kpiGrid').innerHTML = kpis.map(k => `
            <div class="kpi ${k.status}">
                <div class="k-label">${k.label}</div>
                <div class="k-value">${k.value}</div>
                <div class="k-sub">${k.sub || ''}</div>
            </div>`).join('');

        // 建議
        $('adviceList').innerHTML = a.recommendations.map(r => `<li>• ${r}</li>`).join('');

        // 各項判讀
        const metrics = [a.bmi, a.bloodPressure, a.bloodSugar, a.heartRate, a.bodyTemp, a.water].filter(Boolean);
        $('assessGrid').innerHTML = metrics.map(m => `
            <div class="assess-card">
                <div class="a-name">${m.name}</div>
                <div class="a-val">${m.value}</div>
                <div><span class="dot ${m.status}"></span><b>${m.level}</b></div>
                <div class="a-note">${m.note}</div>
            </div>`).join('');
    } catch (e) {
        $('dashError').classList.remove('hidden');
        toast(e.message, 'error');
    }
}
function metricKpi(label, m) {
    if (!m) return { label, value: '—', sub: '尚無資料', status: '' };
    return { label, value: m.value, sub: m.level, status: m.status };
}
$('retryDash').addEventListener('click', loadDashboard);

// ==================== 健康日誌 ====================
async function loadHealthLogs() {
    try {
        const filter = $('riskFilter').value;
        let data = await api('/health-logs');
        if (filter) data = data.filter(d => d.riskLevel === filter);
        const body = $('logBody');
        $('logEmpty').classList.toggle('hidden', data.length > 0);
        body.innerHTML = data.map(d => `
            <tr>
                <td>${d.logDate}</td><td>${d.sleepHours.toFixed(1)}</td><td>${d.steps}</td><td>${d.moodScore}</td>
                <td><span class="badge ${riskToBadge(d.riskLevel)}">${d.riskLevel ?? '—'}</span></td>
                <td>
                    <button class="btn-ghost btn btn-sm" data-act="why" data-id="${d.id}">為什麼</button>
                    <button class="btn-danger-text" data-act="del" data-id="${d.id}">刪除</button>
                </td>
            </tr>`).join('');
    } catch (e) { toast(e.message, 'error'); }
}

$('logForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
        logDate: $('logDate').value || null,
        sleepHours: parseFloat($('sleepHours').value),
        steps: parseInt($('steps').value, 10),
        moodScore: parseInt($('moodScore').value, 10),
    };
    try {
        const r = await api('/health-logs', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        toast('新增成功，風險：' + r.riskLevel, 'success');
        $('logForm').reset();
        $('logDate').value = todayStr();
        await showRisk(r.id);
        await loadHealthLogs();
    } catch (e) { toast(e.message, 'error'); }
});

async function showRisk(id) {
    try {
        const r = await api('/health-logs/risk?id=' + id);
        $('riskResult').classList.remove('hidden');
        const b = $('riskBadge'); b.textContent = r.riskLevel; b.className = 'badge ' + riskToBadge(r.riskLevel);
        const rs = r.reasoning;
        $('reasoning').textContent = `判斷依據 → 睡眠 ${rs.sleepHours} hr｜步數 ${rs.steps}｜心情 ${rs.moodScore}`;
        $('pathList').innerHTML = r.decisionPath.map(s => `<li>${s}</li>`).join('');
        $('riskResult').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } catch (e) { toast(e.message, 'error'); }
}

$('logBody').addEventListener('click', async (e) => {
    const btn = e.target.closest('button'); if (!btn) return;
    const id = btn.dataset.id;
    if (btn.dataset.act === 'why') return showRisk(id);
    if (btn.dataset.act === 'del') {
        if (!confirm('確定刪除這筆紀錄？')) return;
        try { await api('/health-logs/' + id, { method: 'DELETE' }); toast('已刪除', 'success'); loadHealthLogs(); }
        catch (e) { toast(e.message, 'error'); }
    }
});
$('riskFilter').addEventListener('change', loadHealthLogs);

// ==================== 身體數據 ====================
async function loadBody() {
    try {
        const data = await api('/body-metrics');
        $('bodyBody').innerHTML = data.map(d => `
            <tr>
                <td>${d.recordDate}</td><td>${d.heightCm ?? '—'}</td><td>${d.weightKg ?? '—'}</td>
                <td>${d.bmi ?? '—'}</td><td>${d.waterMl ?? '—'}</td>
                <td><button class="btn-danger-text" data-id="${d.id}">刪除</button></td>
            </tr>`).join('');
    } catch (e) { toast(e.message, 'error'); }
}
$('bodyForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
        recordDate: $('bodyDate').value || null,
        heightCm: numOrNull('heightCm'), weightKg: numOrNull('weightKg'), waterMl: intOrNull('waterMl'),
    };
    try { await api('/body-metrics', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        toast('身體數據已儲存', 'success'); $('bodyForm').reset(); $('bodyDate').value = todayStr(); loadBody();
    } catch (e) { toast(e.message, 'error'); }
});
$('bodyBody').addEventListener('click', async (e) => {
    const btn = e.target.closest('button'); if (!btn) return;
    if (!confirm('確定刪除？')) return;
    try { await api('/body-metrics/' + btn.dataset.id, { method: 'DELETE' }); toast('已刪除', 'success'); loadBody(); }
    catch (e) { toast(e.message, 'error'); }
});

// ==================== 生命徵象 ====================
async function loadVitals() {
    try {
        const data = await api('/vital-signs');
        $('vitalBody').innerHTML = data.map(d => `
            <tr>
                <td>${d.recordDate}</td>
                <td>${d.systolic ?? '—'}/${d.diastolic ?? '—'}</td>
                <td>${d.heartRate ?? '—'}</td><td>${d.bodyTemp ?? '—'}</td>
                <td>${d.bloodSugar ?? '—'}</td><td>${d.measureContext ?? '—'}</td>
                <td><button class="btn-danger-text" data-id="${d.id}">刪除</button></td>
            </tr>`).join('');
    } catch (e) { toast(e.message, 'error'); }
}
$('vitalForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
        recordDate: $('vitalDate').value || null,
        systolic: intOrNull('systolic'), diastolic: intOrNull('diastolic'), heartRate: intOrNull('heartRate'),
        bodyTemp: numOrNull('bodyTemp'), bloodSugar: intOrNull('bloodSugar'), measureContext: $('measureContext').value,
    };
    try { await api('/vital-signs', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        toast('生命徵象已儲存', 'success'); $('vitalForm').reset(); $('vitalDate').value = todayStr(); loadVitals();
    } catch (e) { toast(e.message, 'error'); }
});
$('vitalBody').addEventListener('click', async (e) => {
    const btn = e.target.closest('button'); if (!btn) return;
    if (!confirm('確定刪除？')) return;
    try { await api('/vital-signs/' + btn.dataset.id, { method: 'DELETE' }); toast('已刪除', 'success'); loadVitals(); }
    catch (e) { toast(e.message, 'error'); }
});

// ==================== 健康日記 ====================
async function loadDiary() {
    try {
        const data = await api('/diary');
        $('diaryEmpty').classList.toggle('hidden', data.length > 0);
        $('diaryList').innerHTML = data.map(d => `
            <div class="diary-card">
                <div class="d-head">
                    <div><span class="d-title">${escapeHtml(d.title || '（無標題）')}</span></div>
                    <div class="d-date">${d.entryDate}</div>
                </div>
                <div class="d-body">${escapeHtml(d.content || '')}</div>
                <div style="margin-top:8px">
                    ${d.moodTag ? `<span class="tag">情緒：${escapeHtml(d.moodTag)}</span>` : ''}
                    ${d.symptomTags ? `<span class="tag">症狀：${escapeHtml(d.symptomTags)}</span>` : ''}
                    <button class="btn-danger-text" style="float:right" data-id="${d.id}">刪除</button>
                </div>
            </div>`).join('');
    } catch (e) { toast(e.message, 'error'); }
}
$('diaryForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = {
        entryDate: $('diaryDate').value || null, title: $('diaryTitle').value,
        content: $('diaryContent').value, moodTag: $('moodTag').value, symptomTags: $('symptomTags').value,
    };
    try { await api('/diary', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        toast('日記已儲存', 'success'); $('diaryForm').reset(); $('diaryDate').value = todayStr(); loadDiary();
    } catch (e) { toast(e.message, 'error'); }
});
$('diaryList').addEventListener('click', async (e) => {
    const btn = e.target.closest('button'); if (!btn) return;
    if (!confirm('確定刪除這篇日記？')) return;
    try { await api('/diary/' + btn.dataset.id, { method: 'DELETE' }); toast('已刪除', 'success'); loadDiary(); }
    catch (e) { toast(e.message, 'error'); }
});

// ==================== 趨勢分析（單一指標精準檢視）====================
const METRIC_DEFS = {
    health: { path: '/health-logs', dateKey: 'logDate', metrics: { sleepHours: '睡眠時數 (hr)', steps: '步數', moodScore: '心情分數' } },
    body: { path: '/body-metrics', dateKey: 'recordDate', metrics: { weightKg: '體重 (kg)', bmi: 'BMI', waterMl: '喝水量 (ml)' } },
    vital: { path: '/vital-signs', dateKey: 'recordDate', metrics: { systolic: '收縮壓', diastolic: '舒張壓', heartRate: '心率', bodyTemp: '體溫 (℃)', bloodSugar: '血糖 (mg/dL)' } },
};
function refreshMetricOptions() {
    const src = $('trendSource').value;
    const metrics = METRIC_DEFS[src].metrics;
    $('trendMetric').innerHTML = Object.entries(metrics).map(([k, v]) => `<option value="${k}">${v}</option>`).join('');
}
$('trendSource').addEventListener('change', () => { refreshMetricOptions(); applyTrend(); });
$('trendRange').addEventListener('change', () => {
    const v = $('trendRange').value;
    $('fromWrap').classList.toggle('hidden', !(v === 'custom' || v === 'single'));
    $('toWrap').classList.toggle('hidden', v !== 'custom');
});
$('trendApply').addEventListener('click', applyTrend);
$('trendAgg').addEventListener('change', applyTrend);

function rangeDates() {
    const v = $('trendRange').value;
    const today = new Date();
    if (v === 'custom') return { from: $('trendFrom').value, to: $('trendTo').value };
    if (v === 'single') return { from: $('trendFrom').value, to: $('trendFrom').value };
    const days = parseInt(v, 10);
    const from = new Date(today); from.setDate(today.getDate() - days + 1);
    return { from: from.toISOString().slice(0, 10), to: today.toISOString().slice(0, 10) };
}

async function applyTrend() {
    const src = $('trendSource').value;
    const def = METRIC_DEFS[src];
    const metric = $('trendMetric').value || Object.keys(def.metrics)[0];
    const { from, to } = rangeDates();
    if (!from || !to) { toast('請選擇日期', 'error'); return; }
    try {
        let data = await api(`${def.path}?from=${from}&to=${to}`);
        // 依日期排序（後端 body/vital 已 asc；health 區間查詢亦 asc）
        data = [...data].sort((a, b) => a[def.dateKey].localeCompare(b[def.dateKey]));
        let points = data.map(d => ({ date: d[def.dateKey], value: d[metric] }))
            .filter(p => p.value !== null && p.value !== undefined);

        if ($('trendAgg').value === 'weekly') points = weeklyAvg(points);

        $('trendEmpty').classList.toggle('hidden', points.length > 0);
        renderTrend(points, def.metrics[metric]);
    } catch (e) { toast(e.message, 'error'); }
}

// 週平均彙總：以 ISO 週為單位
function weeklyAvg(points) {
    const buckets = {};
    for (const p of points) {
        const d = new Date(p.date);
        const onejan = new Date(d.getFullYear(), 0, 1);
        const week = Math.ceil((((d - onejan) / 86400000) + onejan.getDay() + 1) / 7);
        const key = `${d.getFullYear()}-W${String(week).padStart(2, '0')}`;
        (buckets[key] ||= []).push(p.value);
    }
    return Object.entries(buckets).map(([k, arr]) => ({ date: k, value: Math.round(arr.reduce((s, v) => s + v, 0) / arr.length * 10) / 10 }));
}

function renderTrend(points, label) {
    if (trendChart) trendChart.destroy();
    trendChart = new Chart($('trendChart'), {
        type: 'line',
        data: {
            labels: points.map(p => p.date.length > 7 ? p.date.slice(5) : p.date),
            datasets: [{
                label, data: points.map(p => p.value),
                borderColor: '#2563eb', backgroundColor: 'rgba(37,99,235,.12)',
                fill: true, tension: .3, pointRadius: points.length > 45 ? 0 : 3, borderWidth: 2,
            }]
        },
        options: { responsive: true, plugins: { legend: { display: true } }, scales: { y: { beginAtZero: false } } }
    });
}

// ==================== 資訊增益 ====================
$('loadAnalysis').addEventListener('click', loadAnalysis);
async function loadAnalysis() {
    try {
        const a = await api('/health-logs/analysis');
        const maxIG = Math.max(...a.features.map(f => f.bestInfoGain), 0.0001);
        let html = `<p>${a.summary}</p>
            <p style="color:var(--muted);font-size:13px">樣本數：${a.sampleCount}｜母節點熵 H=${a.parentEntropy}｜標籤來源：${a.labelSource}</p>`;
        for (const f of a.features) {
            const root = f.feature === a.chosenRootFeature;
            html += `<div class="assess-card" style="margin-bottom:10px;${root ? 'border-color:var(--brand);background:var(--brand-light)' : ''}">
                <b>${f.feature}</b> ${root ? '⭐ 第一層分支' : ''}
                <div style="font-size:13px">最佳門檻：<b>${f.bestThreshold}</b>｜資訊增益：<b>${f.bestInfoGain}</b></div>
                <div style="height:8px;background:var(--brand);border-radius:4px;margin-top:6px;width:${(f.bestInfoGain / maxIG * 100).toFixed(0)}%"></div>
            </div>`;
        }
        $('analysisContent').innerHTML = html;
    } catch (e) { toast(e.message, 'error'); }
}

// ==================== 報表匯出 ====================
function download(path) { window.location.href = API + path; }
$('dlExcel').addEventListener('click', () => download('/reports/excel'));
$('dlPdf').addEventListener('click', () => { toast('正在產生 PDF 報告…', 'info'); download('/reports/pdf'); });
$('topExcel').addEventListener('click', () => download('/reports/excel'));
$('topPdf').addEventListener('click', () => { toast('正在產生 PDF 報告…', 'info'); download('/reports/pdf'); });

// ---------- 小工具 ----------
function numOrNull(id) { const v = $(id).value; return v === '' ? null : parseFloat(v); }
function intOrNull(id) { const v = $(id).value; return v === '' ? null : parseInt(v, 10); }
function escapeHtml(s) { return (s || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }

// ---------- 初始化 ----------
['logDate', 'bodyDate', 'vitalDate', 'diaryDate'].forEach(id => { if ($(id)) $(id).value = todayStr(); });
refreshMetricOptions();
loadDashboard();
