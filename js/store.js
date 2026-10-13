// Estado do app (saldo, histórico, sessão de tela, ajustes) salvo no localStorage.

const KEY = 'pushtrainer:v1';
const EPSILON = 1e-6;

const pad = (n) => String(n).padStart(2, '0');

export function todayKey(date = new Date()) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function addDays(key, n) {
  const [y, m, d] = key.split('-').map(Number);
  return todayKey(new Date(y, m - 1, d + n));
}

export function defaultState() {
  return {
    balance: 0, // minutos de tela disponíveis
    totalReps: 0,
    totalEarned: 0,
    history: {}, // { 'AAAA-MM-DD': { reps: { pushup: 10 }, earned: 10 } }
    session: null, // { startedAt, endsAt, minutes }
    settings: {
      rate: 1, // multiplicador de minutos por repetição
      dailyGoal: 50,
      exercise: 'pushup',
      mode: 'camera',
      sound: true,
      voice: true,
    },
  };
}

function safeStorage() {
  try {
    return globalThis.localStorage ?? null;
  } catch {
    return null;
  }
}

export function createStore(storage = safeStorage()) {
  const listeners = new Set();
  let state = load();

  function load() {
    try {
      const raw = storage?.getItem(KEY);
      if (raw) {
        const saved = JSON.parse(raw);
        const base = defaultState();
        return { ...base, ...saved, settings: { ...base.settings, ...saved.settings } };
      }
    } catch {
      // dados corrompidos ou storage bloqueado: começa do zero
    }
    return defaultState();
  }

  function save() {
    try {
      storage?.setItem(KEY, JSON.stringify(state));
    } catch {
      // modo privado / storage cheio: segue só em memória
    }
    listeners.forEach((fn) => fn(state));
  }

  return {
    get state() {
      return state;
    },

    subscribe(fn) {
      listeners.add(fn);
      return () => listeners.delete(fn);
    },

    addReps(exerciseId, reps, minutesEach, date = new Date()) {
      const day = (state.history[todayKey(date)] ??= { reps: {}, earned: 0 });
      const earned = reps * minutesEach;
      day.reps[exerciseId] = (day.reps[exerciseId] ?? 0) + reps;
      day.earned += earned;
      state.balance += earned;
      state.totalReps += reps;
      state.totalEarned += earned;
      save();
      return earned;
    },

    startSession(minutes, now = Date.now()) {
      if (state.session || minutes <= 0 || minutes > state.balance + EPSILON) return false;
      state.balance = Math.max(0, state.balance - minutes);
      state.session = { startedAt: now, endsAt: now + minutes * 60_000, minutes };
      save();
      return true;
    },

    // Encerra a sessão e devolve ao saldo os minutos não usados.
    endSession(now = Date.now()) {
      if (!state.session) return 0;
      const refund = Math.max(0, state.session.endsAt - now) / 60_000;
      state.balance += refund;
      state.session = null;
      save();
      return refund;
    },

    updateSettings(patch) {
      Object.assign(state.settings, patch);
      save();
    },

    reset() {
      state = defaultState();
      save();
    },
  };
}

export function repsOn(state, key) {
  const day = state.history[key];
  return day ? Object.values(day.reps).reduce((a, b) => a + b, 0) : 0;
}

// Dias seguidos com pelo menos uma repetição (hoje ainda pode estar em aberto).
export function streak(state, today = todayKey()) {
  let key = repsOn(state, today) > 0 ? today : addDays(today, -1);
  let n = 0;
  while (repsOn(state, key) > 0) {
    n++;
    key = addDays(key, -1);
  }
  return n;
}

export function lastDays(state, n, today = todayKey()) {
  return Array.from({ length: n }, (_, i) => {
    const key = addDays(today, i - n + 1);
    return { key, reps: repsOn(state, key), earned: state.history[key]?.earned ?? 0 };
  });
}

const minutesFmt = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 });

export function formatMinutes(m) {
  return minutesFmt.format(Math.floor(m * 10 + EPSILON) / 10);
}

export function formatClock(ms) {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  return h ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
}
