import { EXERCISES, RepCounter, jointAngle } from './counter.js';
import { createStore, todayKey, repsOn, streak, lastDays, formatMinutes, formatClock } from './store.js';
import { PoseTracker } from './pose.js';

const store = createStore();
const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => [...document.querySelectorAll(sel)];

const VIEWS = ['inicio', 'treino', 'historico', 'ajustes'];
const TOUCH_DEBOUNCE_MS = 450;

// ---------- Navegação ----------

function currentView() {
  const name = location.hash.slice(1);
  return VIEWS.includes(name) ? name : 'inicio';
}

function showView() {
  const name = currentView();
  if (name !== 'treino' && workout.active) stopWorkout();
  $$('.view').forEach((v) => (v.hidden = v.dataset.view !== name));
  $$('.tabbar a').forEach((a) =>
    a.dataset.tab === name ? a.setAttribute('aria-current', 'page') : a.removeAttribute('aria-current'),
  );
  render();
  window.scrollTo(0, 0);
}

// ---------- Renderização ----------

function rateFor(exercise) {
  return exercise.minutesPerRep * store.state.settings.rate;
}

function render() {
  const { state } = store;
  const today = todayKey();
  const todayReps = repsOn(state, today);
  const days = streak(state, today);

  $('#balance').textContent = formatMinutes(state.balance);
  $('#streak').textContent = days;
  $('#rate-hint').textContent = `1 flexão = ${formatMinutes(rateFor(EXERCISES.pushup))} min · 1 agachamento = ${formatMinutes(rateFor(EXERCISES.squat))} min`;
  $('#today-reps').textContent = todayReps;
  $('#today-goal').textContent = state.settings.dailyGoal;
  $('#today-bar').style.width = `${Math.min(100, (todayReps / state.settings.dailyGoal) * 100)}%`;

  // Sessão de tempo liberado
  $('#session-box').hidden = !state.session;
  $('#spend-box').hidden = !!state.session;
  $$('#spend-chips .chip').forEach((chip) => {
    const m = chip.dataset.minutes === 'all' ? state.balance : Number(chip.dataset.minutes);
    chip.disabled = m < 1 || m > state.balance + 1e-6;
  });
  renderClock();

  // Treino
  const { exercise, mode } = state.settings;
  $$('#exercise-picker button').forEach((b) => b.setAttribute('aria-pressed', b.dataset.exercise === exercise));
  $$('#mode-picker button').forEach((b) => b.setAttribute('aria-pressed', b.dataset.mode === mode));
  $$('#exercise-picker button, #mode-picker button').forEach((b) => (b.disabled = workout.active));
  $('#tip').textContent =
    mode === 'camera'
      ? EXERCISES[exercise].tip
      : exercise === 'pushup'
        ? 'Coloque o celular no chão, embaixo do rosto, e toque a tela com o nariz no fundo de cada flexão.'
        : 'Toque na tela a cada agachamento completo.';
  $('#touchpad span').innerHTML =
    exercise === 'pushup' ? 'Toque aqui com o nariz<br />no fundo de cada flexão' : 'Toque aqui a cada<br />agachamento completo';
  $('#touchpad').hidden = mode !== 'touch' || !workout.active;
  $('#depth').hidden = mode !== 'camera';

  // Histórico
  renderHistory(state, today, days);

  // Ajustes
  $('#set-rate').value = String(state.settings.rate);
  $('#set-goal').value = state.settings.dailyGoal;
  $('#set-sound').checked = state.settings.sound;
  $('#set-voice').checked = state.settings.voice;
}

function renderHistory(state, today, days) {
  const data = lastDays(state, 14, today);
  const max = Math.max(1, ...data.map((d) => d.reps));
  const weekday = new Intl.DateTimeFormat('pt-BR', { weekday: 'narrow' });
  $('#chart').innerHTML = data
    .map((d) => {
      const [y, m, day] = d.key.split('-').map(Number);
      const label = weekday.format(new Date(y, m - 1, day));
      const h = (d.reps / max) * 100;
      return `<div class="col${d.key === today ? ' today' : ''}" title="${d.reps} reps em ${day}/${m}">
        <div class="b${d.reps ? ' on' : ''}" style="height:${h}%"></div><span class="d">${label}</span></div>`;
    })
    .join('');
  const best = Math.max(0, ...Object.keys(state.history).map((k) => repsOn(state, k)));
  $('#total-reps').textContent = state.totalReps;
  $('#total-earned').textContent = formatMinutes(state.totalEarned);
  $('#best-day').textContent = best;
  $('#streak-big').textContent = `${days} 🔥`;
}

function renderClock() {
  const { session } = store.state;
  if (!session) return;
  const left = session.endsAt - Date.now();
  if (left <= 0) {
    store.endSession();
    notify('⏰ Seu tempo de tela acabou!', 'Bora fazer mais flexões para ganhar mais tempo.');
    toast('⏰ Tempo esgotado! Hora de treinar.');
    return;
  }
  $('#session-clock').textContent = formatClock(left);
}

function toast(msg) {
  const el = $('#toast');
  el.textContent = msg;
  el.hidden = false;
  clearTimeout(toast.t);
  toast.t = setTimeout(() => (el.hidden = true), 2800);
}

// ---------- Tempo de tela ----------

$('#spend-chips').addEventListener('click', (e) => {
  const chip = e.target.closest('.chip');
  if (!chip || chip.disabled) return;
  const minutes = chip.dataset.minutes === 'all' ? store.state.balance : Number(chip.dataset.minutes);
  if (store.startSession(minutes)) {
    if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission();
    toast(`✅ ${formatMinutes(minutes)} min liberados. Aproveite!`);
  }
});

$('#end-session').addEventListener('click', () => {
  const refund = store.endSession();
  toast(`↩️ ${formatMinutes(refund)} min devolvidos ao saldo`);
});

async function notify(title, body) {
  try {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) reg.showNotification(title, { body, icon: 'icons/icon-192.png', vibrate: [200, 100, 200] });
    else new Notification(title, { body });
  } catch {
    // notificações são opcionais
  }
}

// ---------- Treino ----------

const workout = { active: false, reps: 0, earned: 0, counter: null, tracker: null, lastTap: 0, exercise: null };
let audioCtx = null;

function beep() {
  if (!store.state.settings.sound || !audioCtx) return;
  const osc = audioCtx.createOscillator();
  const gain = audioCtx.createGain();
  osc.frequency.value = 880;
  gain.gain.setValueAtTime(0.25, audioCtx.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.15);
  osc.connect(gain).connect(audioCtx.destination);
  osc.start();
  osc.stop(audioCtx.currentTime + 0.15);
}

function speak(text) {
  if (!store.state.settings.voice || !('speechSynthesis' in window)) return;
  speechSynthesis.cancel();
  const u = new SpeechSynthesisUtterance(text);
  u.lang = 'pt-BR';
  u.rate = 1.2;
  speechSynthesis.speak(u);
}

function setStatus(text) {
  $('#status').textContent = text;
}

function registerRep() {
  const ex = workout.exercise;
  workout.reps++;
  workout.earned += store.addReps(ex.id, 1, rateFor(ex));
  const repsEl = $('#reps');
  repsEl.textContent = workout.reps;
  repsEl.classList.remove('bump');
  void repsEl.offsetWidth; // reinicia a animação
  repsEl.classList.add('bump');
  $('#earned-now').textContent = formatMinutes(workout.earned);
  beep();
  navigator.vibrate?.(40);
  speak(String(workout.reps));
}

function onPose(landmarks, aspect, now) {
  const ex = workout.exercise;
  const angle = jointAngle(landmarks, ex.sides, { aspect });
  const r = workout.counter.update(angle, now);
  $('#depth-fill').style.height = `${workout.counter.progress * 100}%`;
  if (r.counted) registerRep();

  if (!landmarks) setStatus('Não estou te vendo 👀 — afaste o celular');
  else if (!r.visible) setStatus(ex.id === 'pushup' ? 'Mostre braços e ombros para a câmera' : 'Mostre as pernas inteiras');
  else if (r.phase === 'idle') setStatus(ex.id === 'pushup' ? 'Estique os braços para começar' : 'Fique em pé para começar');
  else if (r.phase === 'up') setStatus('⬇️ Desça!');
  else setStatus('⬆️ Suba!');
}

async function startWorkout() {
  const { exercise, mode } = store.state.settings;
  const ex = EXERCISES[exercise];
  audioCtx ??= window.AudioContext ? new AudioContext() : null;
  audioCtx?.resume();

  Object.assign(workout, { active: true, reps: 0, earned: 0, exercise: ex, lastTap: 0 });
  workout.counter = new RepCounter({ downAngle: ex.downAngle, upAngle: ex.upAngle });
  $('#reps').textContent = '0';
  $('#earned-now').textContent = '0';
  $('#depth-fill').style.height = '0';
  $('#start-btn').hidden = true;
  $('#stop-btn').hidden = false;
  render();

  if (mode === 'touch') {
    setStatus(exercise === 'pushup' ? 'Encoste o nariz na tela lá embaixo!' : 'Toque a cada repetição');
    return;
  }

  setStatus('Abrindo câmera e carregando a IA…');
  const tracker = new PoseTracker($('#video'), $('#overlay'));
  tracker.highlight = ex.sides.flat();
  workout.tracker = tracker;
  try {
    await tracker.start(onPose);
  } catch (err) {
    if (workout.tracker !== tracker) return;
    console.error(err);
    stopWorkout({ silent: true });
    const denied = err?.name === 'NotAllowedError';
    toast(denied ? '📷 Permissão de câmera negada. Use o modo toque.' : '⚠️ Não consegui iniciar a câmera/IA. Use o modo toque.');
    setStatus(denied ? 'Câmera bloqueada' : 'Falha ao carregar a câmera');
  }
}

function stopWorkout({ silent = false } = {}) {
  workout.tracker?.stop();
  workout.tracker = null;
  workout.active = false;
  speechSynthesis?.cancel?.();
  $('#start-btn').hidden = false;
  $('#stop-btn').hidden = true;
  setStatus('Pronto quando você estiver');
  render();

  if (silent || workout.reps === 0) return;
  const ex = workout.exercise;
  $('#summary-title').textContent = workout.reps >= 20 ? 'Monstro! 🦍' : 'Mandou bem!';
  $('#summary-text').textContent =
    `Você fez ${workout.reps} ${workout.reps === 1 ? ex.name.toLowerCase() : ex.plural} e ganhou ` +
    `${formatMinutes(workout.earned)} min de tela. Saldo: ${formatMinutes(store.state.balance)} min.`;
  $('#summary').showModal();
}

$('#start-btn').addEventListener('click', startWorkout);
$('#stop-btn').addEventListener('click', () => stopWorkout());

$('#touchpad').addEventListener('pointerdown', (e) => {
  e.preventDefault();
  const now = performance.now();
  if (!workout.active || now - workout.lastTap < TOUCH_DEBOUNCE_MS) return;
  workout.lastTap = now;
  registerRep();
});

$('#exercise-picker').addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (b && !workout.active) store.updateSettings({ exercise: b.dataset.exercise });
});

$('#mode-picker').addEventListener('click', (e) => {
  const b = e.target.closest('button');
  if (b && !workout.active) store.updateSettings({ mode: b.dataset.mode });
});

// ---------- Ajustes ----------

$('#set-rate').addEventListener('change', (e) => store.updateSettings({ rate: Number(e.target.value) }));
$('#set-goal').addEventListener('change', (e) => {
  const goal = Math.round(Number(e.target.value));
  if (goal >= 1) store.updateSettings({ dailyGoal: Math.min(goal, 1000) });
  else render();
});
$('#set-sound').addEventListener('change', (e) => store.updateSettings({ sound: e.target.checked }));
$('#set-voice').addEventListener('change', (e) => store.updateSettings({ voice: e.target.checked }));
$('#reset-btn').addEventListener('click', () => {
  if (confirm('Apagar saldo, histórico e ajustes? Isso não pode ser desfeito.')) {
    store.reset();
    toast('Dados apagados');
  }
});

let installPrompt = null;
window.addEventListener('beforeinstallprompt', (e) => {
  e.preventDefault();
  installPrompt = e;
  $('#install-btn').hidden = false;
});
$('#install-btn').addEventListener('click', async () => {
  if (!installPrompt) return;
  installPrompt.prompt();
  await installPrompt.userChoice;
  installPrompt = null;
  $('#install-btn').hidden = true;
});

// ---------- Inicialização ----------

store.subscribe(render);
window.addEventListener('hashchange', showView);
document.addEventListener('visibilitychange', () => {
  if (document.hidden && workout.active) stopWorkout();
  else render();
});
setInterval(renderClock, 1000);
showView();

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('sw.js').catch(() => {});
}
