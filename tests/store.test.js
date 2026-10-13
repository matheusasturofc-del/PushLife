import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createStore, streak, lastDays, addDays, todayKey, formatMinutes, formatClock } from '../js/store.js';

function memoryStorage() {
  const data = new Map();
  return { getItem: (k) => data.get(k) ?? null, setItem: (k, v) => data.set(k, String(v)) };
}

test('repetições viram saldo e histórico', () => {
  const store = createStore(memoryStorage());
  const day = new Date(2026, 9, 13);
  store.addReps('pushup', 10, 1, day);
  store.addReps('squat', 4, 0.5, day);
  const { state } = store;
  assert.equal(state.balance, 12);
  assert.equal(state.totalReps, 14);
  assert.deepEqual(state.history['2026-10-13'], { reps: { pushup: 10, squat: 4 }, earned: 12 });
});

test('estado persiste entre aberturas do app', () => {
  const storage = memoryStorage();
  createStore(storage).addReps('pushup', 5, 1);
  const reopened = createStore(storage);
  assert.equal(reopened.state.balance, 5);
  assert.equal(reopened.state.settings.dailyGoal, 50);
});

test('sessão de tela gasta saldo e devolve o que sobrar', () => {
  const store = createStore(memoryStorage());
  store.addReps('pushup', 10, 1);
  assert.equal(store.startSession(15, 0), false, 'sem saldo suficiente');
  assert.equal(store.startSession(10, 0), true);
  assert.equal(store.state.balance, 0);
  assert.equal(store.startSession(1, 0), false, 'já existe sessão ativa');
  const refund = store.endSession(4 * 60_000);
  assert.equal(refund, 6);
  assert.equal(store.state.balance, 6);
  assert.equal(store.state.session, null);
});

test('sequência de dias conta até ontem se hoje ainda não treinou', () => {
  const store = createStore(memoryStorage());
  const today = '2026-10-13';
  for (const n of [-1, -2, -3, -5]) {
    const [y, m, d] = addDays(today, n).split('-').map(Number);
    store.addReps('pushup', 1, 1, new Date(y, m - 1, d));
  }
  assert.equal(streak(store.state, today), 3);
  store.addReps('pushup', 1, 1, new Date(2026, 9, 13));
  assert.equal(streak(store.state, today), 4);
});

test('lastDays devolve n dias terminando hoje', () => {
  const store = createStore(memoryStorage());
  store.addReps('pushup', 7, 1, new Date(2026, 9, 13));
  const days = lastDays(store.state, 3, '2026-10-13');
  assert.deepEqual(days.map((d) => d.key), ['2026-10-11', '2026-10-12', '2026-10-13']);
  assert.equal(days[2].reps, 7);
});

test('datas atravessam meses corretamente', () => {
  assert.equal(addDays('2026-03-01', -1), '2026-02-28');
  assert.equal(todayKey(new Date(2026, 0, 5)), '2026-01-05');
});

test('formatação de minutos e relógio', () => {
  assert.equal(formatMinutes(12.5), '12,5');
  assert.equal(formatMinutes(0.3 + 0.6), '0,9');
  assert.equal(formatClock(65_000), '01:05');
  assert.equal(formatClock(3_725_000), '1:02:05');
});
