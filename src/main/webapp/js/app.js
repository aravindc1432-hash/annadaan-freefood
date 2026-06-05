/**
 * AnnaDaan v2.0 — Full JS
 */
const API = (() => {
  const base = window.location.origin + window.location.pathname.replace(/\/[^/]*$/, '');
  return { EVENTS: base+'/api/events', AUTH: base+'/api/auth', ADMIN: base+'/api/admin', PHOTOS: base+'/api/photos' };
})();

// ── STATE ──────────────────────────────────────────────────────────────────
let state = {
  events: [], total: 0, page: 1, pageSize: 10, totalPages: 1,
  userLat: null, userLon: null, locationActive: false,
  currentUser: null, mapMode: false, leafletMap: null,
  dupEventId: null, checkinEventId: null,
  sessionId: localStorage.getItem('sessionId') || (() => { const id = 'sess_'+Math.random().toString(36).slice(2); localStorage.setItem('sessionId', id); return id; })()
};

// ── INIT ───────────────────────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', async () => {
  setTodayMin();
  await checkAuth();
  await loadEvents();
  bindAll();
});

// ── BIND ALL EVENTS ────────────────────────────────────────────────────────
function bindAll() {
  // Header
  document.getElementById('btnOpenPost').addEventListener('click', openCreateModal);
  document.getElementById('btnLogin').addEventListener('click', () => openAuthModal('login'));
  document.getElementById('btnRegister').addEventListener('click', () => openAuthModal('register'));
  document.getElementById('btnLogout').addEventListener('click', logout);
  // Location & view
  document.getElementById('btnLocate').addEventListener('click', getUserLocation);
  document.getElementById('btnToggleView').addEventListener('click', toggleView);
  // Search & filter
  document.getElementById('searchInput').addEventListener('input', renderList);
  document.getElementById('stateFilter').addEventListener('change', () => { state.page=1; loadEvents(); });
  // Post form
  document.getElementById('btnCloseModal').addEventListener('click', closeModal);
  document.getElementById('btnCancelModal').addEventListener('click', closeModal);
  document.getElementById('eventForm').addEventListener('submit', handleSubmit);
  document.getElementById('btnGetGPS').addEventListener('click', fillFormGPS);
  document.getElementById('isWitness').addEventListener('change', e => {
    document.getElementById('witnessFields').classList.toggle('hidden', !e.target.checked);
  });
  document.getElementById('photoInput').addEventListener('change', previewPhoto);
  // Detail modal
  document.getElementById('btnCloseDetail').addEventListener('click', () => document.getElementById('detailOverlay').classList.remove('active'));
  // Auth modal
  document.getElementById('btnCloseAuth').addEventListener('click', () => document.getElementById('authOverlay').classList.remove('active'));
  document.getElementById('authForm').addEventListener('submit', handleAuth);
  // Dup modal
  document.getElementById('btnCloseDup').addEventListener('click', () => document.getElementById('dupOverlay').classList.remove('active'));
  document.getElementById('btnCancelDup').addEventListener('click', () => document.getElementById('dupOverlay').classList.remove('active'));
  document.getElementById('btnConfirmDup').addEventListener('click', confirmDuplicate);
  // Checkin modal
  document.getElementById('btnCloseCheckin').addEventListener('click', () => document.getElementById('checkinOverlay').classList.remove('active'));
  document.getElementById('btnCancelCheckin').addEventListener('click', () => document.getElementById('checkinOverlay').classList.remove('active'));
  document.getElementById('btnConfirmCheckin').addEventListener('click', confirmCheckin);
  // Close overlays on outside click
  ['modalOverlay','detailOverlay','authOverlay','dupOverlay','checkinOverlay'].forEach(id => {
    const el = document.getElementById(id);
    el.addEventListener('click', e => { if (e.target === el) el.classList.remove('active'); });
  });
}

// ── LOAD EVENTS ────────────────────────────────────────────────────────────
async function loadEvents() {
  document.getElementById('loadingMsg').style.display = 'block';
  document.getElementById('eventsList').innerHTML = '';
  document.getElementById('emptyMsg').classList.add('hidden');
  try {
    let url = `${API.EVENTS}?page=${state.page}&pageSize=${state.pageSize}`;
    if (state.locationActive && state.userLat !== null)
      url += `&lat=${state.userLat}&lon=${state.userLon}`;
    const data = await fetchJSON(url);
    if (data.success) {
      state.events     = data.events || [];
      state.total      = data.total  || 0;
      state.totalPages = data.totalPages || 1;
      populateStateFilter();
      updateStats();
      renderList();
      if (state.mapMode) renderMap();
    }
  } catch(e) { showToast('❌ Cannot reach server. Is Tomcat running?'); }
  finally { document.getElementById('loadingMsg').style.display = 'none'; }
}

// ── RENDER LIST ────────────────────────────────────────────────────────────
function renderList() {
  const q     = document.getElementById('searchInput').value.trim().toLowerCase();
  const stVal = document.getElementById('stateFilter').value;
  const list  = document.getElementById('eventsList');
  const empty = document.getElementById('emptyMsg');

  let filtered = state.events.filter(e =>
    (!q || [e.foodDescription, e.city, e.providerName, e.address].some(f => f?.toLowerCase().includes(q))) &&
    (!stVal || e.state === stVal)
  );

  list.innerHTML = '';
  if (filtered.length === 0) { empty.classList.remove('hidden'); renderPagination(); return; }
  empty.classList.add('hidden');
  filtered.forEach(ev => list.appendChild(buildCard(ev)));
  renderPagination();
}

function buildCard(ev) {
  const card = document.createElement('div');
  card.className = 'event-card';

  const photoHtml = ev.photoPath
    ? `<img class="card-photo" src="${API.PHOTOS}/${ev.photoPath}" alt="Food photo" loading="lazy"/>`
    : `<div class="card-photo-placeholder">🍲</div>`;

  const witnessHtml = ev.addedByWitness
    ? `<div class="witness-badge">👁️ Reported by ${esc(ev.witnessName || 'someone')}</div>` : '';

  card.innerHTML = `
    ${photoHtml}
    <div class="card-top">
      <h3>${esc(ev.foodDescription)}</h3>
      <div class="card-provider">by ${esc(ev.providerName)}</div>
      ${witnessHtml}
      ${ev.distanceKm > 0 ? `<div class="card-distance">${ev.distanceKm} km</div>` : ''}
    </div>
    <div class="card-body">
      <div class="card-row"><span class="icon">📍</span><span>${esc(ev.address)}, ${esc(ev.city)}, ${esc(ev.state)}</span></div>
      <div class="card-row"><span class="icon">📅</span><span>${fmtDate(ev.eventDate)}</span></div>
      <div class="card-row"><span class="icon">⏰</span>
        <span>
          <span class="time-badge">🟢 ${fmtTime(ev.startTime)} – ${fmtTime(ev.endTime)}</span>
          ${ev.quantity > 0 ? `<span class="qty-badge">👥 ~${ev.quantity}</span>` : ''}
        </span>
      </div>
    </div>
    <div class="card-actions">
      <button class="btn-going ${ev.userIsGoing?'active':''}" data-id="${ev.id}" onclick="toggleGoing(${ev.id},this);event.stopPropagation()">
        ${ev.userIsGoing?'✅':'🙋'} Going (${ev.goingCount})
      </button>
      <button class="btn-checkin" onclick="openCheckin(${ev.id});event.stopPropagation()">📍 I'm Here</button>
      <button class="btn-whatsapp" onclick="shareWhatsApp(${ev.id});event.stopPropagation()">📲 Share</button>
      ${ev.checkinCount > 0 ? `<span class="checkin-count">✅ ${ev.checkinCount} confirmed</span>` : ''}
    </div>`;
  card.addEventListener('click', () => openDetail(ev));
  return card;
}

// ── MAP VIEW ───────────────────────────────────────────────────────────────
function toggleView() {
  state.mapMode = !state.mapMode;
  const mapC  = document.getElementById('mapContainer');
  const evSec = document.getElementById('eventsSection');
  const btn   = document.getElementById('btnToggleView');
  if (state.mapMode) {
    mapC.classList.remove('hidden');
    evSec.style.display = 'none';
    btn.textContent = '📋 List View';
    renderMap();
  } else {
    mapC.classList.add('hidden');
    evSec.style.display = '';
    btn.textContent = '🗺️ Map View';
  }
}

function renderMap() {
  if (!state.leafletMap) {
    state.leafletMap = L.map('map').setView([20.5937, 78.9629], 5); // India center
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '© OpenStreetMap contributors'
    }).addTo(state.leafletMap);
  }
  // Clear existing markers
  state.leafletMap.eachLayer(l => { if (l instanceof L.Marker) state.leafletMap.removeLayer(l); });

  const icon = L.divIcon({ html: '🍱', className: '', iconSize: [28,28], iconAnchor:[14,14] });
  state.events.forEach(ev => {
    if (!ev.latitude && !ev.longitude) return;
    L.marker([ev.latitude, ev.longitude], { icon })
      .addTo(state.leafletMap)
      .bindPopup(`<b>${esc(ev.foodDescription)}</b><br>${esc(ev.city)}<br>${fmtDate(ev.eventDate)} ${fmtTime(ev.startTime)}–${fmtTime(ev.endTime)}<br>👥 Going: ${ev.goingCount}`);
  });

  // User location marker
  if (state.userLat) {
    const uIcon = L.divIcon({ html: '📍', className: '', iconSize: [28,28], iconAnchor:[14,28] });
    L.marker([state.userLat, state.userLon], { icon: uIcon })
      .addTo(state.leafletMap).bindPopup('You are here').openPopup();
    state.leafletMap.setView([state.userLat, state.userLon], 12);
  }
}

// ── DETAIL ─────────────────────────────────────────────────────────────────
async function openDetail(ev) {
  // Refresh latest data
  try { const d = await fetchJSON(`${API.EVENTS}/${ev.id}`); if(d.success) ev = d.event; } catch(e){}
  // Get checkins
  let checkins = [];
  try { const d = await fetchJSON(`${API.EVENTS}/${ev.id}/checkins`); checkins = d.checkins||[]; } catch(e){}

  const photoHtml = ev.photoPath
    ? `<img class="detail-photo" src="${API.PHOTOS}/${ev.photoPath}" alt="Food"/>` : '';

  const checkinsHtml = checkins.length > 0
    ? `<div class="checkin-list">${checkins.map(c=>`
        <div class="checkin-item">
          <strong>✅ ${esc(c.checkerName||'Someone')}</strong> confirmed they're here
          ${c.note ? ` — "${esc(c.note)}"` : ''}
          <span style="color:#999;font-size:10px;margin-left:6px">${c.createdAt||''}</span>
        </div>`).join('')}
      </div>` : '<p style="font-size:12px;color:#999">No confirmations yet. Be the first!</p>';

  const witnessHtml = ev.addedByWitness
    ? `<div class="witness-badge" style="margin-bottom:10px">👁️ Reported by ${esc(ev.witnessName||'a community member')} — not directly by organiser</div>` : '';

  document.getElementById('detailBody').innerHTML = `
    ${photoHtml}
    ${witnessHtml}
    <div class="detail-food">${esc(ev.foodDescription)}</div>
    <div class="detail-section">
      <div class="detail-sec-label">Location</div>
      <div class="detail-row">📍 <strong>${esc(ev.address)}</strong></div>
      <div class="detail-row">🏙️ ${esc(ev.city)}, ${esc(ev.state)}</div>
      ${ev.distanceKm>0?`<div class="detail-row">🗺️ <strong>${ev.distanceKm} km from you</strong></div>`:''}
    </div>
    <div class="detail-section">
      <div class="detail-sec-label">Date & Time</div>
      <div class="detail-row">📅 <strong>${fmtDate(ev.eventDate)}</strong></div>
      <div class="detail-row">⏰ <span class="time-badge">${fmtTime(ev.startTime)} – ${fmtTime(ev.endTime)}</span>
        ${ev.quantity>0?`<span class="qty-badge" style="margin-left:8px">👥 ~${ev.quantity} people</span>`:''}
      </div>
    </div>
    <div class="detail-section">
      <div class="detail-sec-label">Organiser</div>
      <div class="detail-row">🙋 ${esc(ev.providerName)}</div>
      ${ev.contactNumber?`<div class="detail-row">📞 ${esc(ev.contactNumber)}</div>`:''}
    </div>
    ${ev.notes?`<div class="detail-section"><div class="detail-sec-label">Notes</div><div style="font-size:13px">${esc(ev.notes)}</div></div>`:''}
    <div class="detail-section">
      <div class="detail-sec-label">🟢 ${ev.goingCount} Going · ✅ ${ev.checkinCount} Confirmed On-Site</div>
      ${checkinsHtml}
    </div>
    <div class="detail-section">
      <a href="https://www.google.com/maps?q=${ev.latitude},${ev.longitude}" target="_blank" style="color:var(--saffron);font-weight:600;font-size:13px">
        🗺️ Open in Google Maps
      </a>
    </div>`;

  document.getElementById('detailOverlay').classList.add('active');

  document.getElementById('detailOverlay').querySelector('.detail-actions')?.remove();
  const acts = document.createElement('div');
  acts.className = 'detail-actions';
  acts.innerHTML = `
    <button class="btn-going ${ev.userIsGoing?'active':''}" id="detailGoing">
      ${ev.userIsGoing?'✅ I\'m Going':'🙋 I\'m Going'} (${ev.goingCount})
    </button>
    <button class="btn-checkin" id="detailCheckin">📍 I'm There Now</button>
    <button class="btn-whatsapp" onclick="shareWhatsApp(${ev.id})">📲 WhatsApp</button>
    <button class="btn-edit-sm" onclick="openEditModal(${ev.id});document.getElementById('detailOverlay').classList.remove('active')">✏️ Edit</button>
    <button class="btn-del-sm" onclick="deleteEvent(${ev.id});document.getElementById('detailOverlay').classList.remove('active')">🗑</button>
    <button class="btn-dup" onclick="openDuplicate(${ev.id});document.getElementById('detailOverlay').classList.remove('active')">📋 Repost</button>`;
  document.getElementById('detailModal').appendChild(acts);
  document.getElementById('detailGoing').addEventListener('click', () => toggleGoing(ev.id, document.getElementById('detailGoing')));
  document.getElementById('detailCheckin').addEventListener('click', () => { document.getElementById('detailOverlay').classList.remove('active'); openCheckin(ev.id); });
}

// ── GOING ──────────────────────────────────────────────────────────────────
async function toggleGoing(eventId, btn) {
  const userName = state.currentUser?.displayName || prompt('Your name (so the organiser knows):') || 'Anonymous';
  try {
    const data = await postJSON(`${API.EVENTS}/${eventId}/going`, { sessionId: state.sessionId, userName });
    if (data.success) {
      const isNow = data.action === 'added';
      btn.textContent = `${isNow?'✅':'🙋'} Going (${data.goingCount})`;
      btn.classList.toggle('active', isNow);
      // update local state
      const ev = state.events.find(e=>e.id==eventId);
      if (ev) { ev.goingCount=data.goingCount; ev.userIsGoing=isNow; }
      showToast(data.message);
    }
  } catch(e) { showToast('❌ Error'); }
}

// ── CHECK-IN ───────────────────────────────────────────────────────────────
function openCheckin(eventId) {
  state.checkinEventId = eventId;
  document.getElementById('checkinName').value = state.currentUser?.displayName || '';
  document.getElementById('checkinNote').value = '';
  document.getElementById('checkinError').classList.add('hidden');
  document.getElementById('checkinOverlay').classList.add('active');
}

async function confirmCheckin() {
  const name = document.getElementById('checkinName').value.trim() || 'Anonymous';
  const note = document.getElementById('checkinNote').value.trim();
  const errEl = document.getElementById('checkinError');

  // Get user GPS for more accurate confirmation
  let lat = state.userLat || 0, lon = state.userLon || 0;
  if (navigator.geolocation && lat === 0) {
    navigator.geolocation.getCurrentPosition(p => { lat=p.coords.latitude; lon=p.coords.longitude; }, ()=>{});
  }

  try {
    const data = await postJSON(`${API.EVENTS}/${state.checkinEventId}/checkin`,
      { sessionId: state.sessionId, checkerName: name, lat, lon, note });

    if (data.alreadyCheckedIn) {
      errEl.textContent = data.message;
      errEl.classList.remove('hidden');
      return;
    }
    if (data.success) {
      document.getElementById('checkinOverlay').classList.remove('active');
      showToast(data.message);
      const ev = state.events.find(e=>e.id==state.checkinEventId);
      if (ev) ev.checkinCount = data.checkinCount;
      renderList();
    }
  } catch(e) { showToast('❌ Error confirming location'); }
}

// ── WHATSAPP SHARE ─────────────────────────────────────────────────────────
function shareWhatsApp(eventId) {
  const ev = state.events.find(e=>e.id==eventId);
  if (!ev) return;
  const text = `🍱 *Free Food Alert!*\n📍 ${ev.foodDescription}\n📌 ${ev.address}, ${ev.city}, ${ev.state}\n📅 ${fmtDate(ev.eventDate)}\n⏰ ${fmtTime(ev.startTime)} – ${fmtTime(ev.endTime)}\n${ev.quantity>0?`👥 ~${ev.quantity} people\n`:''}🗺️ https://maps.google.com/?q=${ev.latitude},${ev.longitude}\n\n_Shared via AnnaDaan — Free Food India_`;
  window.open(`https://wa.me/?text=${encodeURIComponent(text)}`, '_blank');
}

// ── DUPLICATE ──────────────────────────────────────────────────────────────
function openDuplicate(eventId) {
  state.dupEventId = eventId;
  const tomorrow = new Date(); tomorrow.setDate(tomorrow.getDate()+1);
  document.getElementById('dupDate').value = tomorrow.toISOString().split('T')[0];
  document.getElementById('dupDate').min = new Date().toISOString().split('T')[0];
  document.getElementById('dupOverlay').classList.add('active');
}

async function confirmDuplicate() {
  const newDate = document.getElementById('dupDate').value;
  if (!newDate) { showToast('Please select a date'); return; }
  try {
    const data = await postJSON(`${API.EVENTS}/${state.dupEventId}/duplicate`, { newDate });
    if (data.success) {
      document.getElementById('dupOverlay').classList.remove('active');
      showToast('✅ ' + data.message);
      loadEvents();
    } else showToast('❌ ' + data.error);
  } catch(e) { showToast('❌ Error'); }
}

// ── CREATE / EDIT FORM ─────────────────────────────────────────────────────
function openCreateModal() {
  document.getElementById('modalTitle').textContent = '📢 Share Free Food Event';
  document.getElementById('editId').value = '';
  document.getElementById('eventForm').reset();
  document.getElementById('witnessFields').classList.add('hidden');
  document.getElementById('photoPreview').classList.add('hidden');
  document.getElementById('formError').classList.add('hidden');
  document.getElementById('btnSubmit').textContent = '🍱 Post Event';
  setTodayMin();
  document.getElementById('modalOverlay').classList.add('active');
}

async function openEditModal(id) {
  try {
    const data = await fetchJSON(`${API.EVENTS}/${id}`);
    if (!data.success) { showToast('Event not found'); return; }
    const e = data.event;
    document.getElementById('modalTitle').textContent = '✏️ Edit Event';
    document.getElementById('editId').value        = e.id;
    document.getElementById('providerName').value  = e.providerName||'';
    document.getElementById('contactNumber').value = e.contactNumber||'';
    document.getElementById('foodDescription').value = e.foodDescription||'';
    document.getElementById('address').value       = e.address||'';
    document.getElementById('city').value          = e.city||'';
    document.getElementById('state').value         = e.state||'';
    document.getElementById('latitude').value      = e.latitude||'';
    document.getElementById('longitude').value     = e.longitude||'';
    document.getElementById('eventDate').value     = e.eventDate||'';
    document.getElementById('startTime').value     = e.startTime||'';
    document.getElementById('endTime').value       = e.endTime||'';
    document.getElementById('quantity').value      = e.quantity||'';
    document.getElementById('notes').value         = e.notes||'';
    document.getElementById('isWitness').checked   = e.addedByWitness||false;
    document.getElementById('witnessName').value   = e.witnessName||'';
    document.getElementById('witnessFields').classList.toggle('hidden', !e.addedByWitness);
    if (e.photoPath) {
      document.getElementById('photoPreviewImg').src = `${API.PHOTOS}/${e.photoPath}`;
      document.getElementById('photoPreview').classList.remove('hidden');
    }
    document.getElementById('formError').classList.add('hidden');
    document.getElementById('btnSubmit').textContent = '💾 Update Event';
    document.getElementById('modalOverlay').classList.add('active');
  } catch(e) { showToast('❌ Could not load event'); }
}

async function handleSubmit(e) {
  e.preventDefault();
  const errEl = document.getElementById('formError');
  errEl.classList.add('hidden');
  const id = document.getElementById('editId').value;

  const fd = new FormData();
  fd.append('providerName',    document.getElementById('providerName').value.trim());
  fd.append('contactNumber',   document.getElementById('contactNumber').value.trim());
  fd.append('foodDescription', document.getElementById('foodDescription').value.trim());
  fd.append('address',         document.getElementById('address').value.trim());
  fd.append('city',            document.getElementById('city').value.trim());
  fd.append('state',           document.getElementById('state').value);
  fd.append('latitude',        document.getElementById('latitude').value);
  fd.append('longitude',       document.getElementById('longitude').value);
  fd.append('eventDate',       document.getElementById('eventDate').value);
  fd.append('startTime',       document.getElementById('startTime').value);
  fd.append('endTime',         document.getElementById('endTime').value);
  fd.append('quantity',        document.getElementById('quantity').value||'0');
  fd.append('notes',           document.getElementById('notes').value.trim());
  fd.append('addedByWitness',  document.getElementById('isWitness').checked);
  fd.append('witnessName',     document.getElementById('witnessName').value.trim());
  const photoFile = document.getElementById('photoInput').files[0];
  if (photoFile) fd.append('photo', photoFile);

  const btn = document.getElementById('btnSubmit');
  btn.disabled = true; btn.textContent = 'Saving…';

  try {
    const url    = id ? `${API.EVENTS}/${id}` : API.EVENTS;
    const method = id ? 'PUT' : 'POST';
    const res    = await fetch(url, { method, body: fd });
    const data   = await res.json();
    if (data.success) {
      showToast(id ? '✅ Event updated!' : '✅ Event posted! Thank you 🙏');
      closeModal();
      loadEvents();
    } else {
      errEl.textContent = '⚠️ ' + (data.error||'Unknown error');
      errEl.classList.remove('hidden');
    }
  } catch(err) {
    errEl.textContent = '⚠️ Server error. Is Tomcat running?';
    errEl.classList.remove('hidden');
  } finally {
    btn.disabled = false;
    btn.textContent = id ? '💾 Update Event' : '🍱 Post Event';
  }
}

async function deleteEvent(id) {
  if (!confirm('Delete this food event?')) return;
  try {
    const data = await fetchJSON(`${API.EVENTS}/${id}`, { method: 'DELETE' });
    if (data.success) { showToast('✅ Event deleted'); loadEvents(); }
    else showToast('❌ ' + data.error);
  } catch(e) { showToast('❌ Delete failed'); }
}

// ── AUTH ───────────────────────────────────────────────────────────────────
async function checkAuth() {
  try {
    const data = await fetchJSON(`${API.AUTH}/me`);
    if (data.loggedIn) {
      state.currentUser = { userId: data.userId, displayName: data.displayName, isAdmin: data.isAdmin };
      showUserArea();
    }
  } catch(e) {}
}

function openAuthModal(mode) {
  const isReg = mode === 'register';
  document.getElementById('authTitle').textContent = isReg ? '📝 Register' : '🔐 Login';
  document.getElementById('registerFields').classList.toggle('hidden', !isReg);
  document.getElementById('btnAuthSubmit').textContent = isReg ? 'Create Account' : 'Login';
  document.getElementById('authSwitch').innerHTML = isReg
    ? `Already have an account? <a href="#" onclick="openAuthModal('login');return false">Login</a>`
    : `New user? <a href="#" onclick="openAuthModal('register');return false">Register</a>`;
  document.getElementById('authForm').dataset.mode = mode;
  document.getElementById('authError').classList.add('hidden');
  document.getElementById('authOverlay').classList.add('active');
}

async function handleAuth(e) {
  e.preventDefault();
  const mode  = document.getElementById('authForm').dataset.mode;
  const errEl = document.getElementById('authError');
  errEl.classList.add('hidden');
  const body  = {
    username: document.getElementById('authUsername').value.trim(),
    password: document.getElementById('authPassword').value,
    ...(mode==='register' && {
      displayName: document.getElementById('authDisplayName').value.trim(),
      phone: document.getElementById('authPhone').value.trim()
    })
  };
  try {
    const data = await postJSON(`${API.AUTH}/${mode}`, body);
    if (data.success) {
      state.currentUser = { userId: data.userId, displayName: data.displayName, isAdmin: data.isAdmin };
      showUserArea();
      document.getElementById('authOverlay').classList.remove('active');
      showToast('✅ ' + data.message);
    } else {
      errEl.textContent = '⚠️ ' + data.error;
      errEl.classList.remove('hidden');
    }
  } catch(err) { errEl.textContent = '⚠️ Server error'; errEl.classList.remove('hidden'); }
}

async function logout() {
  await postJSON(`${API.AUTH}/logout`, {});
  state.currentUser = null;
  document.getElementById('userArea').classList.add('hidden');
  document.getElementById('authArea').classList.remove('hidden');
  showToast('👋 Logged out');
}

function showUserArea() {
  document.getElementById('authArea').classList.add('hidden');
  document.getElementById('userArea').classList.remove('hidden');
  document.getElementById('userGreet').textContent = `👋 ${state.currentUser.displayName}`;
  if (state.currentUser.isAdmin) {
    document.getElementById('userGreet').innerHTML += ` <a href="admin/" style="color:rgba(255,255,255,.7);font-size:11px">[Admin]</a>`;
  }
}

// ── GPS ────────────────────────────────────────────────────────────────────
function getUserLocation() {
  if (!navigator.geolocation) { showToast('Geolocation not supported'); return; }
  setLocStatus('loading', '⏳ Getting your location…');
  navigator.geolocation.getCurrentPosition(pos => {
    state.userLat = pos.coords.latitude;
    state.userLon = pos.coords.longitude;
    state.locationActive = true;
    setLocStatus('success', `📍 (${state.userLat.toFixed(4)}, ${state.userLon.toFixed(4)}) — sorted nearest first`);
    loadEvents();
  }, err => setLocStatus('error', '❌ ' + err.message), { timeout: 10000 });
}

function fillFormGPS() {
  if (!navigator.geolocation) { showToast('Not supported'); return; }
  navigator.geolocation.getCurrentPosition(p => {
    document.getElementById('latitude').value  = p.coords.latitude.toFixed(6);
    document.getElementById('longitude').value = p.coords.longitude.toFixed(6);
    showToast('📍 Coordinates filled!');
  }, () => showToast('❌ Could not get GPS'));
}

function setLocStatus(type, msg) {
  const dot = document.getElementById('locationStatus').querySelector('.dot');
  dot.className = 'dot dot-' + type;
  document.getElementById('locationText').textContent = msg;
}

// ── STATS / FILTERS ────────────────────────────────────────────────────────
function updateStats() {
  document.getElementById('statCount').textContent    = state.total;
  const cities = new Set(state.events.map(e=>e.city)).size;
  document.getElementById('statCities').textContent   = cities;
  const servings = state.events.reduce((s,e)=>s+(e.quantity||0),0);
  document.getElementById('statServings').textContent = servings > 0 ? servings.toLocaleString('en-IN') : '—';
  const going = state.events.reduce((s,e)=>s+(e.goingCount||0),0);
  document.getElementById('statGoing').textContent    = going > 0 ? going : '—';
}

function populateStateFilter() {
  const sel    = document.getElementById('stateFilter');
  const states = [...new Set(state.events.map(e=>e.state).filter(Boolean))].sort();
  while (sel.options.length > 1) sel.remove(1);
  states.forEach(s => { const o=document.createElement('option'); o.value=o.textContent=s; sel.appendChild(o); });
}

// ── PAGINATION ─────────────────────────────────────────────────────────────
function renderPagination() {
  const pg = document.getElementById('pagination');
  pg.innerHTML = '';
  if (state.totalPages <= 1) return;
  const prev = document.createElement('button');
  prev.className = 'page-btn'; prev.textContent = '← Prev';
  prev.disabled = state.page <= 1;
  prev.addEventListener('click', () => { state.page--; loadEvents(); window.scrollTo(0,0); });
  pg.appendChild(prev);
  for (let i = Math.max(1, state.page-2); i <= Math.min(state.totalPages, state.page+2); i++) {
    const b = document.createElement('button');
    b.className = 'page-btn' + (i===state.page?' active':'');
    b.textContent = i;
    b.addEventListener('click', () => { state.page=i; loadEvents(); window.scrollTo(0,0); });
    pg.appendChild(b);
  }
  const next = document.createElement('button');
  next.className = 'page-btn'; next.textContent = 'Next →';
  next.disabled = state.page >= state.totalPages;
  next.addEventListener('click', () => { state.page++; loadEvents(); window.scrollTo(0,0); });
  pg.appendChild(next);
}

// ── PHOTO PREVIEW ──────────────────────────────────────────────────────────
function previewPhoto(e) {
  const file = e.target.files[0];
  if (!file) return;
  const reader = new FileReader();
  reader.onload = ev => {
    document.getElementById('photoPreviewImg').src = ev.target.result;
    document.getElementById('photoPreview').classList.remove('hidden');
  };
  reader.readAsDataURL(file);
}

// ── HELPERS ────────────────────────────────────────────────────────────────
function closeModal() {
  document.getElementById('modalOverlay').classList.remove('active');
  document.getElementById('eventForm').reset();
}

async function fetchJSON(url, opts={}) {
  const res = await fetch(url, opts);
  return res.json();
}

async function postJSON(url, body) {
  const res = await fetch(url, { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body) });
  return res.json();
}

function setTodayMin() {
  const today = new Date().toISOString().split('T')[0];
  const el = document.getElementById('eventDate');
  if (el && !el.value) { el.value = today; el.min = today; }
  const dup = document.getElementById('dupDate');
  if (dup) dup.min = today;
}

function fmtDate(s) {
  if (!s) return '—';
  const [y,m,d] = s.split('-');
  return `${d} ${['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec'][+m-1]} ${y}`;
}

function fmtTime(s) {
  if (!s) return '—';
  const [h,m] = s.split(':');
  const hr = +h; return `${hr%12||12}:${m} ${hr>=12?'PM':'AM'}`;
}

function esc(s) {
  if (!s) return '';
  return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

let _toastTimer;
function showToast(msg) {
  const t = document.getElementById('toast');
  t.textContent = msg; t.classList.add('show');
  clearTimeout(_toastTimer);
  _toastTimer = setTimeout(() => t.classList.remove('show'), 3200);
}
