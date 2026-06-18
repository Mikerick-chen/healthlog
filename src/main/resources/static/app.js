// ===== 智慧健康管理平台前端 v2（繁體中文註解）=====
const $ = (id) => document.getElementById(id);
let currentUserId = localStorage.getItem('userId') || null;
let charts = {};

// ---------- 共用 ----------
const todayStr = () => new Date().toISOString().slice(0, 10);
const riskBadge = (r) => r === '低' ? 'badge-low' : r === '中' ? 'badge-mid' : r === '高' ? 'badge-high' : 'badge-gray';
const statusBadge = (s) => s === 'good' ? 'badge-good' : s === 'warn' ? 'badge-warn' : s === 'danger' ? 'badge-danger' : 'badge-gray';
const escapeHtml = (s) => (s || '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const numOrNull = (id) => $(id).value === '' ? null : parseFloat($(id).value);
const intOrNull = (id) => $(id).value === '' ? null : parseInt($(id).value, 10);

function toast(msg, type = 'info') {
    const t = $('toast'); t.textContent = msg; t.className = `toast toast-${type}`;
    clearTimeout(t._timer); t._timer = setTimeout(() => t.classList.add('hidden'), 2800);
}

// 統一 fetch（自動帶 X-User-Id）
async function api(path, options = {}) {
    const headers = Object.assign({}, options.headers || {});
    if (currentUserId) headers['X-User-Id'] = currentUserId;
    let res;
    try { res = await fetch(path, { ...options, headers }); }
    catch (e) { throw new Error('無法連線後端服務（請確認伺服器是否啟動）'); }
    if (res.status === 204) return null;
    const ct = res.headers.get('content-type') || '';
    const data = ct.includes('application/json') ? await res.json() : null;
    if (!res.ok) throw new Error((data && data.error) || `請求失敗（HTTP ${res.status}）`);
    return data;
}

// 帶 header 的檔案下載（window.location 無法帶 header，故用 blob）
async function download(path, fallbackName) {
    try {
        toast('正在產生檔案…', 'info');
        const res = await fetch(path, { headers: currentUserId ? { 'X-User-Id': currentUserId } : {} });
        if (!res.ok) throw new Error('產生失敗');
        const blob = await res.blob();
        let name = fallbackName;
        const cd = res.headers.get('content-disposition');
        if (cd && cd.includes("filename*=UTF-8''")) name = decodeURIComponent(cd.split("filename*=UTF-8''")[1]);
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a'); a.href = url; a.download = name; a.click();
        URL.revokeObjectURL(url);
    } catch (e) { toast(e.message, 'error'); }
}

// ---------- 使用者 ----------
async function initUsers() {
    let users = await api('/users');
    if (!users.length) { // 理論上有示範帳號；保險起見
        const u = await api('/users', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: '示範帳號' }) });
        users = [u];
    }
    if (!currentUserId || !users.some(u => String(u.id) === String(currentUserId))) {
        currentUserId = String(users[0].id);
        localStorage.setItem('userId', currentUserId);
    }
    renderUserSelect(users);
}
function renderUserSelect(users) {
    $('userSelect').innerHTML = users.map(u => `<option value="${u.id}" ${String(u.id) === String(currentUserId) ? 'selected' : ''}>${escapeHtml(u.name)}</option>`).join('');
    const cur = users.find(u => String(u.id) === String(currentUserId));
    $('userAvatar').textContent = cur ? cur.name.slice(0, 1) : '?';
}
$('userSelect').addEventListener('change', (e) => {
    currentUserId = e.target.value; localStorage.setItem('userId', currentUserId);
    initUsers().then(() => { switchView(currentView); toast('已切換使用者', 'success'); });
});
$('addUserBtn').addEventListener('click', async () => {
    const name = prompt('輸入新使用者名稱：');
    if (!name || !name.trim()) return;
    const u = await api('/users', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ name: name.trim() }) });
    currentUserId = String(u.id); localStorage.setItem('userId', currentUserId);
    await initUsers(); switchView('dashboard'); toast(`已切換到 ${u.name}`, 'success');
});

// ---------- 導覽 ----------
const PAGE = {
    dashboard: ['總覽儀表板', '即時健康狀態總覽'], nlp: ['語意智能日誌', '白話文一鍵轉成健康數據'],
    healthlog: ['健康日誌', '睡眠/步數/心情 → 決策樹評估風險'], body: ['身體數據', '身高/體重/BMI/喝水'],
    vitals: ['生命徵象', '血壓/心率/體溫/血糖'], diary: ['健康日記', '每日身心狀況'],
    clinic: ['智慧診療室', '症狀 → 科別建議與評估'], insights: ['洞察分析', 'ROI 與專屬基準線'],
    trends: ['趨勢分析', '單一指標精準檢視'], analysis: ['資訊增益', '決策樹門檻計算依據'], reports: ['報表匯出', 'Excel 與 PDF 報告'],
};
let currentView = 'dashboard';
function switchView(view) {
    currentView = view;
    document.querySelectorAll('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.view === view));
    document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
    $('view-' + view).classList.add('active');
    $('pageTitle').textContent = PAGE[view][0]; $('pageCrumb').textContent = PAGE[view][1];
    ({ dashboard: loadDashboard, healthlog: loadHealthLogs, body: loadBody, vitals: loadVitals,
       diary: loadDiary, clinic: initClinic, insights: loadInsights, trends: applyTrend, analysis: loadTree }[view] || (() => {}))();
}
document.querySelectorAll('.nav-item').forEach(b => b.addEventListener('click', () => switchView(b.dataset.view)));

// ==================== 總覽儀表板 ====================
async function loadDashboard() {
    try {
        const [a, coach, baseline] = await Promise.all([api('/assessment'), api('/insights/coach'), api('/insights/baseline')]);
        $('dashError').classList.add('hidden');

        $('scoreNum').textContent = a.compositeScore; $('scoreGrade').textContent = a.scoreGrade;
        $('scoreRing').style.setProperty('--val', a.compositeScore);
        const rb = $('dashRisk'); rb.textContent = a.decisionTreeRisk || '—'; rb.className = 'badge ' + riskBadge(a.decisionTreeRisk);
        $('scoreHint').textContent = a.scoreGrade === '優' ? '健康狀態良好，請繼續保持！' : '部分指標需留意，詳見下方判讀。';

        const kpi = (label, m) => m ? { label, value: m.value, sub: m.level, status: m.status } : { label, value: '—', sub: '尚無資料', status: '' };
        const kpis = [
            { label: '今日風險（決策樹）', value: a.decisionTreeRisk || '—', sub: '睡眠→步數→心情', status: a.decisionTreeRisk === '低' ? 'good' : a.decisionTreeRisk === '中' ? 'warn' : 'danger' },
            kpi('BMI', a.bmi), kpi('血壓', a.bloodPressure), kpi('血糖', a.bloodSugar),
        ];
        $('kpiGrid').innerHTML = kpis.map(k => `<div class="kpi ${k.status}"><div class="k-label">${k.label}</div><div class="k-value">${k.value}</div><div class="k-sub">${k.sub || ''}</div></div>`).join('');

        renderCoach(coach);

        const metrics = [a.bmi, a.bloodPressure, a.bloodSugar, a.heartRate, a.bodyTemp, a.water].filter(Boolean);
        $('assessGrid').innerHTML = metrics.map(m => `<div class="assess-card"><div class="a-name">${m.name}</div><div class="a-val">${m.value}</div><div><span class="dot ${m.status}"></span><b>${m.level}</b></div><div class="a-note">${m.note}</div></div>`).join('');

        $('baselineAlertBox').innerHTML = baseline.alerts.map(s => `<div class="advisory ${s.includes('超出') ? 'warn' : 'info'}">${escapeHtml(s)}</div>`).join('') || '<div class="muted-tip">無資料</div>';

        loadDashCharts();
        loadEnv();
    } catch (e) { $('dashError').classList.remove('hidden'); toast(e.message, 'error'); }
}
function renderCoach(c) {
    $('coachCard').className = 'coach-card ' + c.tone;
    $('coachEmoji').textContent = c.emoji; $('coachTitle').textContent = c.title; $('coachMsg').textContent = c.message;
    $('coachSug').innerHTML = (c.suggestions || []).map(s => '• ' + escapeHtml(s)).join('<br>');
    $('coachReason').textContent = '判斷依據：' + (c.stateReason || '');
}
async function loadDashCharts() {
    const logs = await api('/health-logs');
    const counts = { 低: 0, 中: 0, 高: 0 }; logs.forEach(l => { if (counts[l.riskLevel] !== undefined) counts[l.riskLevel]++; });
    drawChart('riskDonut', { type: 'doughnut', data: { labels: ['低', '中', '高'], datasets: [{ data: [counts.低, counts.中, counts.高], backgroundColor: ['#16a34a', '#f59e0b', '#dc2626'] }] }, options: { plugins: { legend: { position: 'bottom' } } } });

    const recent = (arr, dateKey, valKey, n = 30) => [...arr].sort((a, b) => a[dateKey].localeCompare(b[dateKey])).slice(-n);
    const body = await api('/body-metrics');
    const bw = recent(body, 'recordDate', 'weightKg');
    drawChart('weightMini', lineCfg(bw.map(d => d.recordDate.slice(5)), bw.map(d => d.weightKg), '體重', '#2563eb'));
    const vit = await api('/vital-signs');
    const vb = recent(vit, 'recordDate', 'systolic');
    drawChart('bpMini', lineCfg(vb.map(d => d.recordDate.slice(5)), vb.map(d => d.systolic), '收縮壓', '#dc2626'));
}
function lineCfg(labels, data, label, color) {
    return { type: 'line', data: { labels, datasets: [{ label, data, borderColor: color, backgroundColor: color + '22', fill: true, tension: .3, pointRadius: data.length > 20 ? 0 : 2, borderWidth: 2 }] }, options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: false } } } };
}
function drawChart(id, cfg) { if (charts[id]) charts[id].destroy(); charts[id] = new Chart($(id), cfg); }

// 環境
async function loadEnv(lat, lon) {
    try {
        $('envBox').innerHTML = '<div class="muted-tip">抓取環境資料中…</div>';
        const q = lat && lon ? `?lat=${lat}&lon=${lon}` : '';
        const e = await api('/environment' + q);
        if (!e.available) { $('envBox').innerHTML = `<div class="advisory warn">${escapeHtml(e.message || '環境資料暫時無法取得')}</div>`; return; }
        let html = `<div class="muted-tip">${escapeHtml(e.location)}｜${escapeHtml(e.weatherText || '')}</div><div class="env-grid" style="margin-top:8px">`;
        const cell = (l, v) => `<div class="env-item"><div class="ev-label">${l}</div><div class="ev-val">${v ?? '—'}</div></div>`;
        html += cell('溫度', e.temperature != null ? e.temperature + '°C' : '—');
        html += cell('氣壓', e.pressure != null ? Math.round(e.pressure) : '—');
        html += cell('濕度', e.humidity != null ? e.humidity + '%' : '—');
        html += cell('PM2.5', e.pm25 != null ? e.pm25 : '—');
        html += '</div>';
        (e.advisories || []).forEach(a => html += `<div class="advisory ${a.level}">${escapeHtml(a.message)}</div>`);
        (e.correlations || []).forEach(c => html += `<div class="correlation">${escapeHtml(c)}</div>`);
        $('envBox').innerHTML = html;
    } catch (err) { $('envBox').innerHTML = `<div class="advisory warn">${escapeHtml(err.message)}</div>`; }
}
$('refreshEnv').addEventListener('click', () => {
    if (navigator.geolocation) navigator.geolocation.getCurrentPosition(
        p => loadEnv(p.coords.latitude, p.coords.longitude), () => loadEnv());
    else loadEnv();
});
$('retryDash').addEventListener('click', loadDashboard);

// ==================== 語意日誌 ====================
let lastNlp = null;
$('nlpParseBtn').addEventListener('click', async () => {
    const text = $('nlpText').value.trim();
    if (!text) return toast('請先輸入內容', 'error');
    try {
        const r = await api('/nlp/parse', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ text }) });
        lastNlp = r; renderNlp(r);
    } catch (e) { toast(e.message, 'error'); }
});
function renderNlp(r) {
    $('nlpEmpty').classList.add('hidden'); $('nlpResult').classList.remove('hidden');
    $('nlpSummary').textContent = r.summary;
    $('nlpTags').innerHTML = r.tags.map(t => `<span class="chip ${t.status}" title="${escapeHtml(t.message)}">${escapeHtml(t.category)}・${escapeHtml(t.keyword)}</span>`).join('') || '<span class="muted-tip">無</span>';
    const ex = r.extracted; const item = (l, v) => v == null ? '' : `<div class="extract-item"><div class="ei-label">${l}</div><div class="ei-val">${v}</div></div>`;
    $('nlpExtract').innerHTML = item('咖啡因(杯)', ex.caffeineCups) + item('睡眠(hr)', ex.sleepHours) + item('步數', ex.steps) + item('喝水(ml)', ex.waterMl) + item('推估心情', ex.moodGuess) + (ex.symptoms.length ? `<div class="extract-item"><div class="ei-label">症狀</div><div class="ei-val" style="font-size:13px">${ex.symptoms.join('、')}</div></div>` : '');
    if (r.categoryChart.length) drawChart('nlpChart', { type: 'bar', data: { labels: r.categoryChart.map(c => c.category), datasets: [{ label: '命中次數', data: r.categoryChart.map(c => c.count), backgroundColor: '#2563eb' }] }, options: { plugins: { legend: { display: false } }, scales: { y: { ticks: { stepSize: 1 } } } } });
}
$('nlpSaveBtn').addEventListener('click', async () => {
    const text = $('nlpText').value.trim();
    if (!text) return toast('請先輸入內容', 'error');
    if (!lastNlp) { await $('nlpParseBtn').click(); }
    try {
        const ex = lastNlp.extracted;
        await api('/diary', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ content: text, title: '語意日誌', symptomTags: ex.symptoms.join(','), moodTag: '' }) });
        // 若抽到睡眠/步數，順手建一筆健康日誌
        if (ex.sleepHours != null || ex.steps != null) {
            await api('/health-logs', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ sleepHours: ex.sleepHours ?? 7, steps: ex.steps ?? 5000, moodScore: ex.moodGuess ?? 6 }) });
        }
        if (ex.waterMl != null) await api('/body-metrics', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ waterMl: ex.waterMl }) });
        toast('已存成日記並帶入數據', 'success'); $('nlpText').value = '';
    } catch (e) { toast(e.message, 'error'); }
});

// ==================== 健康日誌 ====================
async function loadHealthLogs() {
    try {
        const filter = $('riskFilter').value;
        let data = await api('/health-logs');
        if (filter) data = data.filter(d => d.riskLevel === filter);
        $('logEmpty').classList.toggle('hidden', data.length > 0);
        $('logBody').innerHTML = data.map(d => `<tr><td>${d.logDate}</td><td>${d.sleepHours.toFixed(1)}</td><td>${d.steps}</td><td>${d.moodScore}</td><td><span class="badge ${riskBadge(d.riskLevel)}">${d.riskLevel ?? '—'}</span></td><td><button class="btn btn-ghost btn-sm" data-act="why" data-id="${d.id}">為什麼</button> <button class="btn-danger-text" data-act="del" data-id="${d.id}">刪除</button></td></tr>`).join('');
    } catch (e) { toast(e.message, 'error'); }
}
$('logForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const payload = { logDate: $('logDate').value || null, sleepHours: parseFloat($('sleepHours').value), steps: parseInt($('steps').value, 10), moodScore: parseInt($('moodScore').value, 10) };
    try {
        const r = await api('/health-logs', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload) });
        toast('新增成功，風險：' + r.riskLevel, 'success'); $('logForm').reset(); $('logDate').value = todayStr();
        await showRisk(r.id); await loadHealthLogs();
    } catch (e) { toast(e.message, 'error'); }
});
async function showRisk(id) {
    try {
        const r = await api('/health-logs/risk?id=' + id);
        $('riskResult').classList.remove('hidden');
        const b = $('riskBadge'); b.textContent = r.riskLevel; b.className = 'badge ' + riskBadge(r.riskLevel);
        $('reasoning').textContent = `判斷依據 → 睡眠 ${r.reasoning.sleepHours} hr｜步數 ${r.reasoning.steps}｜心情 ${r.reasoning.moodScore}`;
        $('pathList').innerHTML = r.decisionPath.map(s => `<li>${escapeHtml(s)}</li>`).join('');
        $('riskResult').scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    } catch (e) { toast(e.message, 'error'); }
}
$('logBody').addEventListener('click', async (e) => {
    const btn = e.target.closest('button'); if (!btn) return;
    if (btn.dataset.act === 'why') return showRisk(btn.dataset.id);
    if (btn.dataset.act === 'del' && confirm('確定刪除？')) { try { await api('/health-logs/' + btn.dataset.id, { method: 'DELETE' }); toast('已刪除', 'success'); loadHealthLogs(); } catch (e) { toast(e.message, 'error'); } }
});
$('riskFilter').addEventListener('change', loadHealthLogs);

// ==================== 身體數據 / 生命徵象 / 日記（共用模式）====================
async function loadBody() {
    try { const data = await api('/body-metrics'); $('bodyBody').innerHTML = data.map(d => `<tr><td>${d.recordDate}</td><td>${d.heightCm ?? '—'}</td><td>${d.weightKg ?? '—'}</td><td>${d.bmi ?? '—'}</td><td>${d.waterMl ?? '—'}</td><td><button class="btn-danger-text" data-id="${d.id}">刪除</button></td></tr>`).join(''); } catch (e) { toast(e.message, 'error'); }
}
$('bodyForm').addEventListener('submit', async (e) => { e.preventDefault();
    try { await api('/body-metrics', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ recordDate: $('bodyDate').value || null, heightCm: numOrNull('heightCm'), weightKg: numOrNull('weightKg'), waterMl: intOrNull('waterMl') }) }); toast('已儲存', 'success'); $('bodyForm').reset(); $('bodyDate').value = todayStr(); loadBody(); } catch (e) { toast(e.message, 'error'); } });
$('bodyBody').addEventListener('click', async (e) => { const b = e.target.closest('button'); if (b && confirm('確定刪除？')) { try { await api('/body-metrics/' + b.dataset.id, { method: 'DELETE' }); toast('已刪除', 'success'); loadBody(); } catch (e) { toast(e.message, 'error'); } } });

async function loadVitals() {
    try { const data = await api('/vital-signs'); $('vitalBody').innerHTML = data.map(d => `<tr><td>${d.recordDate}</td><td>${d.systolic ?? '—'}/${d.diastolic ?? '—'}</td><td>${d.heartRate ?? '—'}</td><td>${d.bodyTemp ?? '—'}</td><td>${d.bloodSugar ?? '—'}</td><td>${d.measureContext ?? '—'}</td><td><button class="btn-danger-text" data-id="${d.id}">刪除</button></td></tr>`).join(''); } catch (e) { toast(e.message, 'error'); }
}
$('vitalForm').addEventListener('submit', async (e) => { e.preventDefault();
    try { await api('/vital-signs', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ recordDate: $('vitalDate').value || null, systolic: intOrNull('systolic'), diastolic: intOrNull('diastolic'), heartRate: intOrNull('heartRate'), bodyTemp: numOrNull('bodyTemp'), bloodSugar: intOrNull('bloodSugar'), measureContext: $('measureContext').value }) }); toast('已儲存', 'success'); $('vitalForm').reset(); $('vitalDate').value = todayStr(); loadVitals(); } catch (e) { toast(e.message, 'error'); } });
$('vitalBody').addEventListener('click', async (e) => { const b = e.target.closest('button'); if (b && confirm('確定刪除？')) { try { await api('/vital-signs/' + b.dataset.id, { method: 'DELETE' }); toast('已刪除', 'success'); loadVitals(); } catch (e) { toast(e.message, 'error'); } } });

async function loadDiary() {
    try {
        const data = await api('/diary'); $('diaryEmpty').classList.toggle('hidden', data.length > 0);
        $('diaryList').innerHTML = data.map(d => `<div class="diary-card"><div class="d-head"><span class="d-title">${escapeHtml(d.title || '（無標題）')}</span><span class="d-date">${d.entryDate}</span></div><div class="d-body">${escapeHtml(d.content || '')}</div><div style="margin-top:8px">${d.moodTag ? `<span class="tag">情緒：${escapeHtml(d.moodTag)}</span>` : ''}${d.symptomTags ? `<span class="tag">症狀：${escapeHtml(d.symptomTags)}</span>` : ''}<button class="btn-danger-text" style="float:right" data-id="${d.id}">刪除</button></div></div>`).join('');
    } catch (e) { toast(e.message, 'error'); }
}
$('diaryForm').addEventListener('submit', async (e) => { e.preventDefault();
    try { await api('/diary', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ entryDate: $('diaryDate').value || null, title: $('diaryTitle').value, content: $('diaryContent').value, moodTag: $('moodTag').value, symptomTags: $('symptomTags').value }) }); toast('已儲存', 'success'); $('diaryForm').reset(); $('diaryDate').value = todayStr(); loadDiary(); } catch (e) { toast(e.message, 'error'); } });
$('diaryList').addEventListener('click', async (e) => { const b = e.target.closest('button'); if (b && confirm('確定刪除？')) { try { await api('/diary/' + b.dataset.id, { method: 'DELETE' }); toast('已刪除', 'success'); loadDiary(); } catch (e) { toast(e.message, 'error'); } } });

// ==================== 智慧診療室 ====================
let selectedSymptoms = new Set();
async function initClinic() {
    if ($('symptomChips').dataset.loaded) return;
    try {
        const syms = await api('/clinic/symptoms');
        $('symptomChips').innerHTML = syms.map(s => `<span class="chip selectable info" data-sym="${s}">${s}</span>`).join('');
        $('symptomChips').dataset.loaded = '1';
        $('symptomChips').addEventListener('click', (e) => {
            const c = e.target.closest('.chip'); if (!c) return;
            const s = c.dataset.sym; if (selectedSymptoms.has(s)) { selectedSymptoms.delete(s); c.classList.remove('on'); } else { selectedSymptoms.add(s); c.classList.add('on'); }
        });
    } catch (e) { toast(e.message, 'error'); }
}
$('clinicBtn').addEventListener('click', async () => {
    const text = $('clinicText').value.trim();
    if (selectedSymptoms.size === 0 && !text) return toast('請至少選一個症狀或輸入描述', 'error');
    try {
        const r = await api('/clinic/diagnose', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ symptoms: [...selectedSymptoms], text }) });
        renderClinic(r);
    } catch (e) { toast(e.message, 'error'); }
});
function renderClinic(r) {
    $('clinicEmpty').classList.add('hidden'); const el = $('clinicResult'); el.classList.remove('hidden');
    let html = `<div class="urgency ${r.urgency}">${escapeHtml(r.urgencyLabel)}</div>`;
    if (r.recommendedSpecialties.length) html += `<div><b>建議就診科別</b><div style="margin-top:6px">${r.recommendedSpecialties.map(s => `<span class="specialty-pill">${escapeHtml(s)}</span>`).join('')}</div></div>`;
    r.assessments.forEach(a => {
        html += `<div class="clinic-result"><b>${escapeHtml(a.symptom)}</b> ${a.redFlag ? '<span class="chip danger">需留意</span>' : ''}<div class="muted-tip" style="margin:6px 0">可能病因：${a.possibleCauses.map(escapeHtml).join('、')}</div><div style="font-size:13px">建議科別：<b>${escapeHtml(a.specialty)}</b></div><div style="font-size:13px;margin-top:4px">自我照護：${escapeHtml(a.advice)}</div></div>`;
    });
    if (r.dataInsights.length) html += `<div style="margin-top:12px"><b>結合你的數據</b>${r.dataInsights.map(d => `<div class="correlation">${escapeHtml(d)}</div>`).join('')}</div>`;
    html += `<div class="muted-tip" style="margin-top:14px">${escapeHtml(r.disclaimer)}</div>`;
    el.innerHTML = html;
}

// ==================== 洞察分析（ROI + 基準線）====================
async function loadInsights() {
    try {
        const roi = await api('/insights/roi');
        $('roiHighlights').innerHTML = roi.highlights.map(h => `<div class="highlight-box">🏆 ${escapeHtml(h)}</div>`).join('');
        const row = (it) => `<div class="roi-row"><div><div style="font-weight:600">${it.name}</div><div class="muted-tip">本週 ${it.thisWeek ?? '—'} ｜ 上週 ${it.lastWeek ?? '—'} ${it.unit}</div></div><div class="roi-delta ${it.status}">${it.delta == null ? '—' : (it.delta > 0 ? '▲' : it.delta < 0 ? '▼' : '＝') + ' ' + Math.abs(it.delta) + (it.deltaPct != null ? ` (${Math.abs(it.deltaPct)}%)` : '')}</div></div>`;
        $('roiBehaviors').innerHTML = roi.behaviors.map(row).join('');
        $('roiOutcomes').innerHTML = roi.outcomes.map(row).join('');

        const bl = await api('/insights/baseline');
        $('baselineFull').innerHTML = bl.metrics.length ? bl.metrics.map(m => {
            const span = (m.high - m.low) || 1; const pct = (v) => Math.max(0, Math.min(100, (v - m.low) / span * 100));
            return `<div class="base-row"><div><b>${m.name}</b><div class="muted-tip">常態 ${m.low}~${m.high} ${m.unit}</div></div><div class="base-track"><div class="base-fill" style="left:0;right:0"></div><div class="base-marker ${m.status}" style="left:${pct(m.latest)}%"></div></div><div style="text-align:right"><b>${m.latest}</b> ${m.unit}<div class="muted-tip">z=${m.zScore}</div></div></div>`;
        }).join('') : '<div class="muted-tip">資料量不足，持續記錄後即可建立個人基準線。</div>';
    } catch (e) { toast(e.message, 'error'); }
}

// ==================== 趨勢分析（ISO 週）====================
const METRICS = {
    health: { path: '/health-logs', dateKey: 'logDate', metrics: { sleepHours: '睡眠 (hr)', steps: '步數', moodScore: '心情' } },
    body: { path: '/body-metrics', dateKey: 'recordDate', metrics: { weightKg: '體重 (kg)', bmi: 'BMI', waterMl: '喝水 (ml)' } },
    vital: { path: '/vital-signs', dateKey: 'recordDate', metrics: { systolic: '收縮壓', diastolic: '舒張壓', heartRate: '心率', bodyTemp: '體溫', bloodSugar: '血糖' } },
};
function refreshMetricOptions() { const m = METRICS[$('trendSource').value].metrics; $('trendMetric').innerHTML = Object.entries(m).map(([k, v]) => `<option value="${k}">${v}</option>`).join(''); }
$('trendSource').addEventListener('change', () => { refreshMetricOptions(); applyTrend(); });
$('trendRange').addEventListener('change', () => { const v = $('trendRange').value; $('fromWrap').classList.toggle('hidden', !(v === 'custom' || v === 'single')); $('toWrap').classList.toggle('hidden', v !== 'custom'); });
$('trendApply').addEventListener('click', applyTrend); $('trendAgg').addEventListener('change', applyTrend);
function rangeDates() {
    const v = $('trendRange').value, today = new Date();
    if (v === 'custom') return { from: $('trendFrom').value, to: $('trendTo').value };
    if (v === 'single') return { from: $('trendFrom').value, to: $('trendFrom').value };
    const from = new Date(today); from.setDate(today.getDate() - parseInt(v, 10) + 1);
    return { from: from.toISOString().slice(0, 10), to: today.toISOString().slice(0, 10) };
}
// 真 ISO-8601 週數（#8）
function isoWeek(d) {
    const date = new Date(Date.UTC(d.getFullYear(), d.getMonth(), d.getDate()));
    const day = date.getUTCDay() || 7; date.setUTCDate(date.getUTCDate() + 4 - day);
    const yearStart = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
    const week = Math.ceil(((date - yearStart) / 86400000 + 1) / 7);
    return `${date.getUTCFullYear()}-W${String(week).padStart(2, '0')}`;
}
async function applyTrend() {
    const def = METRICS[$('trendSource').value]; const metric = $('trendMetric').value || Object.keys(def.metrics)[0];
    const { from, to } = rangeDates(); if (!from || !to) return toast('請選擇日期', 'error');
    try {
        let data = await api(`${def.path}?from=${from}&to=${to}`);
        data = [...data].sort((a, b) => a[def.dateKey].localeCompare(b[def.dateKey]));
        let points = data.map(d => ({ date: d[def.dateKey], value: d[metric] })).filter(p => p.value != null);
        if ($('trendAgg').value === 'weekly') {
            const buckets = {}; points.forEach(p => { (buckets[isoWeek(new Date(p.date))] ||= []).push(p.value); });
            points = Object.entries(buckets).map(([k, arr]) => ({ date: k, value: Math.round(arr.reduce((s, v) => s + v, 0) / arr.length * 10) / 10 }));
        }
        $('trendEmpty').classList.toggle('hidden', points.length > 0);
        drawChart('trendChart', { type: 'line', data: { labels: points.map(p => p.date.length > 7 ? p.date.slice(5) : p.date), datasets: [{ label: def.metrics[metric], data: points.map(p => p.value), borderColor: '#2563eb', backgroundColor: 'rgba(37,99,235,.12)', fill: true, tension: .3, pointRadius: points.length > 45 ? 0 : 3, borderWidth: 2 }] }, options: { plugins: { legend: { display: true } }, scales: { y: { beginAtZero: false } } } });
    } catch (e) { toast(e.message, 'error'); }
}

// ==================== 資訊增益 + 決策樹圖 ====================
async function loadTree() {
    try {
        const t = await api('/health-logs/tree');
        $('treeDiagram').innerHTML = buildTreeSvg(t.t1Sleep, t.t2Steps, t.t3Mood);
    } catch (e) { $('treeDiagram').innerHTML = `<div class="muted-tip">${escapeHtml(e.message)}</div>`; }
    loadAnalysis();
}
function buildTreeSvg(t1, t2, t3) {
    // 簡潔決策樹示意：睡眠→步數→心情→風險
    return `<svg viewBox="0 0 760 300" class="tree-svg">
      <style>.n{fill:#eff6ff;stroke:#2563eb;stroke-width:1.5;rx:8}.leaf{stroke-width:1.5}.t{font:600 12px sans-serif;fill:#0f172a}.tl{font:600 11px sans-serif;fill:#fff}.e{font:11px sans-serif;fill:#64748b}.ln{stroke:#94a3b8;stroke-width:1.5}</style>
      <line class="ln" x1="120" y1="48" x2="40" y2="110"/><line class="ln" x1="120" y1="48" x2="320" y2="110"/>
      <rect class="n" x="60" y="20" width="140" height="36" rx="8"/><text class="t" x="130" y="43" text-anchor="middle">睡眠 &lt; ${t1} hr ?</text>
      <text class="e" x="55" y="86">是(不足)</text><text class="e" x="250" y="86">否(足夠)</text>
      <rect class="n" x="0" y="110" width="120" height="34" rx="8"/><text class="t" x="60" y="132" text-anchor="middle">步數 &lt; ${t2} ?</text>
      <rect class="n" x="260" y="110" width="120" height="34" rx="8"/><text class="t" x="320" y="132" text-anchor="middle">步數 &lt; ${t2} ?</text>
      <line class="ln" x1="60" y1="144" x2="30" y2="190"/><line class="ln" x1="60" y1="144" x2="150" y2="190"/>
      <line class="ln" x1="320" y1="144" x2="290" y2="190"/><line class="ln" x1="320" y1="144" x2="430" y2="190"/>
      <rect class="n" x="0" y="190" width="120" height="34" rx="8"/><text class="t" x="60" y="212" text-anchor="middle">心情 &lt; ${t3} ?</text>
      <rect class="n" x="120" y="190" width="130" height="34" rx="8"/><text class="t" x="185" y="212" text-anchor="middle">心情 &lt; ${t3} ?</text>
      <rect class="n" x="260" y="190" width="120" height="34" rx="8"/><text class="t" x="320" y="212" text-anchor="middle">心情 &lt; ${t3} ?</text>
      <rect class="leaf" x="430" y="192" width="70" height="30" rx="8" fill="#16a34a"/><text class="tl" x="465" y="212" text-anchor="middle">低風險</text>
      <line class="ln" x1="60" y1="224" x2="40" y2="262"/><line class="ln" x1="60" y1="224" x2="95" y2="262"/>
      <rect class="leaf" x="10" y="262" width="60" height="28" rx="8" fill="#dc2626"/><text class="tl" x="40" y="281" text-anchor="middle">高</text>
      <rect class="leaf" x="80" y="262" width="60" height="28" rx="8" fill="#f59e0b"/><text class="tl" x="110" y="281" text-anchor="middle">中</text>
      <rect class="leaf" x="160" y="262" width="60" height="28" rx="8" fill="#f59e0b"/><text class="tl" x="190" y="281" text-anchor="middle">中★</text>
      <rect class="leaf" x="240" y="262" width="60" height="28" rx="8" fill="#16a34a"/><text class="tl" x="270" y="281" text-anchor="middle">低</text>
      <text class="e" x="150" y="258">步數正常但心情差→中(中間情況)</text>
    </svg>`;
}
async function loadAnalysis() {
    try {
        const a = await api('/health-logs/analysis');
        const maxIG = Math.max(...a.features.map(f => f.bestInfoGain), 0.0001);
        let html = `<p>${escapeHtml(a.summary)}</p><p class="muted-tip">樣本數：${a.sampleCount}｜母節點熵 H=${a.parentEntropy}｜標籤來源：${escapeHtml(a.labelSource)}</p>`;
        a.features.forEach(f => {
            const root = f.feature === a.chosenRootFeature;
            html += `<div class="ig-bar-row"><div><b>${f.feature}</b>${root ? ' ⭐' : ''}<div class="muted-tip">門檻 ${f.bestThreshold}</div></div><div class="ig-bar-track"><div class="ig-bar-fill" style="width:${(f.bestInfoGain / maxIG * 100).toFixed(0)}%"></div></div><div style="text-align:right"><b>${f.bestInfoGain}</b></div></div>`;
        });
        $('analysisContent').innerHTML = html;
    } catch (e) { $('analysisContent').innerHTML = `<div class="muted-tip">${escapeHtml(e.message)}</div>`; }
}

// ==================== 報表 ====================
$('dlExcel').addEventListener('click', () => download('/reports/excel', 'health.xlsx'));
$('dlPdf').addEventListener('click', () => download('/reports/pdf', 'report.pdf'));
$('topExcel').addEventListener('click', () => download('/reports/excel', 'health.xlsx'));
$('topPdf').addEventListener('click', () => download('/reports/pdf', 'report.pdf'));

// ---------- 初始化 ----------
['logDate', 'bodyDate', 'vitalDate', 'diaryDate'].forEach(id => { if ($(id)) $(id).value = todayStr(); });
refreshMetricOptions();
initUsers().then(() => loadDashboard());
