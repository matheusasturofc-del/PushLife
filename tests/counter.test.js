import { test } from 'node:test';
import assert from 'node:assert/strict';
import { angleAt, jointAngle, RepCounter, EXERCISES } from '../js/counter.js';

// Simula o ângulo indo de `from` até `to` em `steps` quadros.
function* sweep(from, to, steps = 10) {
  for (let i = 0; i <= steps; i++) yield from + ((to - from) * i) / steps;
}

function feed(counter, angles, start = 0, frameMs = 33) {
  let t = start;
  for (const a of angles) {
    counter.update(a, t);
    t += frameMs;
  }
  return t;
}

test('angleAt calcula ângulos retos e rasos', () => {
  assert.equal(Math.round(angleAt({ x: 0, y: 1 }, { x: 0, y: 0 }, { x: 1, y: 0 })), 90);
  assert.equal(Math.round(angleAt({ x: -1, y: 0 }, { x: 0, y: 0 }, { x: 1, y: 0 })), 180);
  assert.equal(angleAt({ x: 0, y: 0 }, { x: 0, y: 0 }, { x: 1, y: 0 }), null);
});

test('jointAngle escolhe o lado mais visível e ignora pontos ocultos', () => {
  const lm = [];
  // Lado esquerdo pouco visível (ângulo reto), lado direito visível (braço esticado).
  lm[11] = { x: 0, y: 0, visibility: 0.3 };
  lm[13] = { x: 0, y: 1, visibility: 0.3 };
  lm[15] = { x: 1, y: 1, visibility: 0.3 };
  lm[12] = { x: 0, y: 0, visibility: 0.9 };
  lm[14] = { x: 0, y: 1, visibility: 0.9 };
  lm[16] = { x: 0, y: 2, visibility: 0.9 };
  assert.equal(Math.round(jointAngle(lm, EXERCISES.pushup.sides)), 180);

  lm[12].visibility = 0.1;
  assert.equal(jointAngle(lm, EXERCISES.pushup.sides), null);
  assert.equal(jointAngle(null, EXERCISES.pushup.sides), null);
});

test('conta uma repetição por ciclo completo em cima → embaixo → em cima', () => {
  const c = new RepCounter({ downAngle: 100, upAngle: 150 });
  let t = feed(c, sweep(170, 170, 5));
  for (let i = 0; i < 3; i++) {
    t = feed(c, sweep(170, 70), t);
    t = feed(c, sweep(70, 170), t);
    t = feed(c, sweep(170, 170, 5), t);
  }
  assert.equal(c.count, 3);
});

test('não conta meia repetição (sem descer o suficiente)', () => {
  const c = new RepCounter({ downAngle: 100, upAngle: 150 });
  let t = feed(c, sweep(170, 170, 5));
  t = feed(c, sweep(170, 120), t);
  feed(c, sweep(120, 170), t);
  assert.equal(c.count, 0);
});

test('não conta se começar já embaixo e só subir', () => {
  const c = new RepCounter({ downAngle: 100, upAngle: 150 });
  let t = feed(c, sweep(70, 70, 5));
  feed(c, sweep(70, 170), t);
  assert.equal(c.count, 0);
  assert.equal(c.phase, 'up');
});

test('quadros sem pose não mudam o estado', () => {
  const c = new RepCounter({ downAngle: 100, upAngle: 150 });
  let t = feed(c, sweep(170, 170, 5));
  t = feed(c, sweep(170, 70), t);
  const r = c.update(null, t);
  assert.equal(r.visible, false);
  assert.equal(c.phase, 'down');
  feed(c, sweep(70, 170), t + 33);
  assert.equal(c.count, 1);
});

test('ignora tremidas rápidas demais (minRepMs)', () => {
  const c = new RepCounter({ downAngle: 100, upAngle: 150, smoothing: 1, minRepMs: 500 });
  c.update(170, 0);
  c.update(80, 50);
  c.update(170, 100); // 1ª rep
  c.update(80, 150);
  c.update(170, 200); // rápida demais: ignorada
  c.update(80, 700);
  c.update(170, 800); // conta
  assert.equal(c.count, 2);
});

test('progress vai de 0 (em cima) a 1 (embaixo)', () => {
  const c = new RepCounter({ downAngle: 100, upAngle: 150, smoothing: 1 });
  c.update(170, 0);
  assert.equal(c.progress, 0);
  c.update(125, 10);
  assert.equal(c.progress, 0.5);
  c.update(60, 20);
  assert.equal(c.progress, 1);
});
