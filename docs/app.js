// =====================================================================
//  Panel de licencias del plugin RbFernan (Supabase + GitHub Pages)
// =====================================================================
const cfg = window.SS_CONFIG || {};
const $ = (s) => document.querySelector(s);
const show = (el, yes = true) => el.classList.toggle('hidden', !yes);

function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [k, v] of Object.entries(attrs)) {
    if (v === null || v === undefined || v === false) continue;
    if (k === 'class') el.className = v;
    else if (k === 'text') el.textContent = v;
    else if (k.startsWith('on')) el.addEventListener(k.slice(2), v);
    else el.setAttribute(k, v === true ? '' : v);
  }
  for (const c of children.flat()) {
    if (c === null || c === undefined || c === false) continue;
    el.append(c instanceof Node ? c : String(c));
  }
  return el;
}
const fmt = (s) => (s ? new Date(s).toLocaleString('es', { dateStyle: 'short', timeStyle: 'short' }) : '—');
const ago = (s) => {
  if (!s) return '—';
  const m = Math.floor((Date.now() - new Date(s)) / 60000);
  if (m < 1) return 'ahora';
  if (m < 60) return `hace ${m} min`;
  if (m < 1440) return `hace ${Math.floor(m / 60)} h`;
  return `hace ${Math.floor(m / 1440)} días`;
};
const badge = (t, c) => h('span', { class: `badge ${c}`, text: t });
const statusBadge = (s) => s === 'approved' ? badge('Aprobado', 'green')
  : s === 'rejected' ? badge('Rechazado', 'red') : badge('Pendiente', 'orange');

let toastTimer;
function toast(msg, bad = false) {
  const t = $('#toast');
  t.textContent = msg;
  t.className = 'toast' + (bad ? ' bad' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => show(t, false), 3500);
}
function msgRow(tbody, cols, text, err = false) {
  tbody.replaceChildren(h('tr', {}, h('td', { colspan: cols, class: err ? 'error' : 'muted', text })));
}
async function copy(t) { try { await navigator.clipboard.writeText(t); toast('Copiado: ' + t); } catch { toast('No se pudo copiar', true); } }

let sb = null, entered = false, servers = [], licenses = [];

if (!cfg.SUPABASE_URL || !cfg.SUPABASE_ANON_KEY || !window.supabase) {
  show($('#setup'));
} else {
  sb = window.supabase.createClient(cfg.SUPABASE_URL, cfg.SUPABASE_ANON_KEY);
  sb.auth.onAuthStateChange((_e, s) => setTimeout(() => (s ? enterApp(s) : enterLogin()), 0));
  wire();
}

function enterLogin() { entered = false; show($('#app'), false); show($('#login')); }
function enterApp(session) {
  show($('#login'), false); show($('#app'));
  $('#user-email').textContent = session.user.email || '';
  if (!entered) { entered = true; openTab('lic'); }
}

function wire() {
  $('#login-form').addEventListener('submit', async (e) => {
    e.preventDefault(); $('#login-error').textContent = '';
    const { error } = await sb.auth.signInWithPassword({ email: $('#login-email').value.trim(), password: $('#login-pass').value });
    if (error) $('#login-error').textContent = 'No se pudo entrar: ' + error.message;
  });
  $('#logout').addEventListener('click', () => sb.auth.signOut());
  document.querySelectorAll('[data-tab]').forEach((b) => b.addEventListener('click', () => openTab(b.dataset.tab)));
  $('#lic-refresh').addEventListener('click', loadAll);
  $('#srv-refresh').addEventListener('click', loadAll);
  $('#srv-filter').addEventListener('change', renderServers);
  $('#lic-form').addEventListener('submit', createLicenses);
}

function openTab(name) {
  for (const t of ['lic', 'srv']) show($('#tab-' + t), t === name);
  document.querySelectorAll('[data-tab]').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  loadAll();
}

async function loadAll() {
  const [lc, sv] = await Promise.all([
    sb.from('plugin_licenses').select('*').order('created_at', { ascending: false }),
    sb.from('plugin_servers').select('*').order('last_seen', { ascending: false }),
  ]);
  if (lc.error) { msgRow($('#lic-body'), 5, 'Error: ' + lc.error.message, true); return; }
  licenses = lc.data || [];
  servers = sv.data || [];
  renderStats();
  renderLicenses();
  renderServers();
}

function serversOf(id) { return servers.filter((s) => s.license_id === id); }

function renderStats() {
  $('#st-lic').textContent = licenses.length;
  $('#st-srv').textContent = servers.length;
  $('#st-ok').textContent = servers.filter((s) => s.status === 'approved').length;
  $('#st-pend').textContent = servers.filter((s) => s.status === 'pending').length;
}

function licStatus(l) {
  if (!l.active) return badge('Desactivada', 'gray');
  if (l.expires_at && new Date(l.expires_at) < new Date()) return badge('Vencida', 'orange');
  return badge('Activa', 'green');
}

function renderLicenses() {
  const tb = $('#lic-body');
  if (!licenses.length) return msgRow(tb, 5, 'Todavía no hay licencias. Crea una arriba.');
  const rows = [];
  for (const l of licenses) {
    const srv = serversOf(l.id);
    const ok = srv.filter((s) => s.status === 'approved').length;
    const pend = srv.filter((s) => s.status === 'pending').length;
    rows.push(h('tr', { class: 'clickable', onclick: (e) => { if (!e.target.closest('button')) toggleSub(l.id); } },
      h('td', {}, h('code', { text: l.key }), ' ', h('button', { class: 'btn small', onclick: () => copy(l.key), text: 'Copiar' })),
      h('td', { text: l.note || '—' }),
      h('td', {}, `${ok} aprobado(s)`, pend ? h('span', { class: 'badge orange', style: 'margin-left:6px', text: `${pend} pendiente(s)` }) : '',
        ` / ${srv.length}${l.max_servers ? ' · máx ' + l.max_servers : ''}`),
      h('td', {}, licStatus(l)),
      h('td', { class: 'right' },
        h('button', { class: 'btn small', text: l.active ? 'Desactivar' : 'Activar', onclick: () => updLic(l.id, { active: !l.active }) }),
        h('button', { class: 'btn small danger', text: 'Borrar', onclick: () => delLic(l) }),
      ),
    ));
    rows.push(h('tr', { class: 'sub hidden', id: 'sub-' + l.id }, h('td', { colspan: 5 }, h('div', { class: 'inner', id: 'inner-' + l.id }, 'Click para ver servidores…'))));
  }
  tb.replaceChildren(...rows);
}

function toggleSub(id) {
  const row = $('#sub-' + id);
  const open = row.classList.toggle('hidden');
  if (!open) renderSubServers(id);
}

function renderSubServers(id) {
  const box = $('#inner-' + id);
  const srv = serversOf(id);
  if (!srv.length) { box.replaceChildren(h('span', { class: 'muted', text: 'Ningún servidor ha usado esta licencia todavía.' })); return; }
  const table = h('table', {},
    h('thead', {}, h('tr', {}, ...['Servidor', 'IP real', 'MC', 'Plugin', 'Puerto', 'Última vez', 'Estado', ''].map((t) => h('th', { text: t })))),
    h('tbody', {}, ...srv.map((s) => serverRow(s, true))),
  );
  box.replaceChildren(table);
}

function serverRow(s, compact) {
  const actions = h('td', { class: 'right' });
  if (s.status !== 'approved') actions.append(h('button', { class: 'btn small', text: 'Aprobar', onclick: () => updSrv(s.id, 'approved') }));
  if (s.status !== 'rejected') actions.append(h('button', { class: 'btn small danger', text: 'Rechazar', onclick: () => updSrv(s.id, 'rejected') }));
  actions.append(h('button', { class: 'btn small danger', text: 'Borrar', onclick: () => delSrv(s) }));
  if (compact) {
    return h('tr', { class: 'srv-row' },
      h('td', { text: s.server_name || '—' }),
      h('td', {}, h('span', { class: 'mono', text: s.ip || '—' })),
      h('td', { text: s.mc_version || '—' }),
      h('td', { text: s.plugin_version || '—' }),
      h('td', { text: s.port || '—' }),
      h('td', { text: ago(s.last_seen) }),
      h('td', {}, statusBadge(s.status)),
      actions,
    );
  }
  const lic = licenses.find((l) => l.id === s.license_id);
  return h('tr', { class: 'srv-row' },
    h('td', { text: s.server_name || '—' }),
    h('td', {}, h('span', { class: 'mono', text: s.ip || '—' })),
    h('td', {}, lic ? h('code', { text: lic.key }) : '—'),
    h('td', { text: (s.mc_version || '—') + ' · ' + (s.plugin_version || '') }),
    h('td', { text: ago(s.last_seen) }),
    h('td', {}, statusBadge(s.status)),
    actions,
  );
}

function renderServers() {
  const tb = $('#srv-body');
  const f = $('#srv-filter').value;
  const rows = servers.filter((s) => f === 'all' || s.status === f);
  if (!rows.length) return msgRow(tb, 7, 'Sin servidores para ese filtro.');
  tb.replaceChildren(...rows.map((s) => serverRow(s, false)));
}

async function updSrv(id, status) {
  const { error } = await sb.from('plugin_servers').update({ status }).eq('id', id);
  if (error) return toast('Error: ' + error.message, true);
  toast(status === 'approved' ? 'Servidor aprobado' : 'Servidor rechazado');
  loadAll();
}
async function delSrv(s) {
  if (!confirm('¿Borrar este servidor? Si sigue encendido, volverá a aparecer como pendiente la próxima vez.')) return;
  const { error } = await sb.from('plugin_servers').delete().eq('id', s.id);
  if (error) return toast('Error: ' + error.message, true);
  toast('Servidor borrado'); loadAll();
}
async function updLic(id, patch) {
  const { error } = await sb.from('plugin_licenses').update(patch).eq('id', id);
  if (error) return toast('Error: ' + error.message, true);
  loadAll();
}
async function delLic(l) {
  if (!confirm('¿Borrar la licencia ' + l.key + '?\nEl plugin dejará de funcionar en los servidores que la usen (se apaga solo en la próxima revisión).')) return;
  const { error } = await sb.from('plugin_licenses').delete().eq('id', l.id);
  if (error) return toast('Error: ' + error.message, true);
  toast('Licencia borrada'); loadAll();
}

function genKey() {
  const abc = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  const b = new Uint8Array(12); crypto.getRandomValues(b);
  let s = '';
  for (let i = 0; i < 12; i++) { if (i && i % 4 === 0) s += '-'; s += abc[b[i] % abc.length]; }
  return 'RB-' + s;
}

async function createLicenses(e) {
  e.preventDefault();
  const qty = Math.min(50, Math.max(1, parseInt($('#lic-qty').value || '1', 10)));
  const max = $('#lic-max').value ? Math.max(1, parseInt($('#lic-max').value, 10)) : null;
  const exp = $('#lic-exp').value ? new Date($('#lic-exp').value + 'T23:59:59').toISOString() : null;
  const rows = Array.from({ length: qty }, () => ({
    key: genKey(), note: $('#lic-note').value.trim() || null, max_servers: max, expires_at: exp, auto_approve: $('#lic-auto').checked,
  }));
  const { error } = await sb.from('plugin_licenses').insert(rows);
  if (error) return toast('Error: ' + error.message, true);
  const box = $('#lic-created');
  box.replaceChildren(h('b', { text: `${qty} licencia(s) creada(s):` }),
    ...rows.map((r) => h('div', {}, h('code', { text: r.key }), h('button', { class: 'btn small', type: 'button', text: 'Copiar', onclick: () => copy(r.key) }))));
  show(box);
  $('#lic-form').reset(); $('#lic-qty').value = 1;
  loadAll();
}
