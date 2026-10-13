// Lógica pura de contagem de repetições: sem DOM, testável em Node.
// Os índices seguem os 33 pontos do MediaPipe Pose Landmarker.

export const EXERCISES = {
  pushup: {
    id: 'pushup',
    name: 'Flexão',
    plural: 'flexões',
    emoji: '💪',
    minutesPerRep: 1,
    // [ombro, cotovelo, punho] de cada lado; usamos o lado mais visível.
    sides: [[11, 13, 15], [12, 14, 16]],
    downAngle: 100,
    upAngle: 150,
    tip: 'Apoie o celular de lado para você, a uns 2 m, com o corpo inteiro na tela.',
  },
  squat: {
    id: 'squat',
    name: 'Agachamento',
    plural: 'agachamentos',
    emoji: '🦵',
    minutesPerRep: 0.5,
    // [quadril, joelho, tornozelo]
    sides: [[23, 25, 27], [24, 26, 28]],
    downAngle: 105,
    upAngle: 155,
    tip: 'Fique de lado ou de frente para o celular, com o corpo inteiro na tela.',
  },
};

// Ângulo (em graus) no vértice b formado pelos pontos a-b-c.
export function angleAt(a, b, c) {
  const abx = a.x - b.x, aby = a.y - b.y;
  const cbx = c.x - b.x, cby = c.y - b.y;
  const mag = Math.hypot(abx, aby) * Math.hypot(cbx, cby);
  if (!mag) return null;
  const cos = Math.min(1, Math.max(-1, (abx * cbx + aby * cby) / mag));
  return (Math.acos(cos) * 180) / Math.PI;
}

// Ângulo da articulação usando o lado do corpo mais visível para a câmera.
// `aspect` (largura/altura do vídeo) corrige as coordenadas normalizadas.
export function jointAngle(landmarks, sides, { minVisibility = 0.5, aspect = 1 } = {}) {
  if (!landmarks) return null;
  let best = null;
  for (const [ia, ib, ic] of sides) {
    const pts = [landmarks[ia], landmarks[ib], landmarks[ic]];
    if (pts.some((p) => !p)) continue;
    const vis = Math.min(...pts.map((p) => p.visibility ?? 1));
    if (vis < minVisibility) continue;
    if (!best || vis > best.vis) best = { vis, pts };
  }
  if (!best) return null;
  const [a, b, c] = best.pts.map((p) => ({ x: p.x * aspect, y: p.y }));
  return angleAt(a, b, c);
}

// Máquina de estados com histerese: conta uma repetição a cada
// ciclo completo "em cima → embaixo → em cima".
export class RepCounter {
  constructor({ downAngle, upAngle, smoothing = 0.5, minRepMs = 500 }) {
    this.downAngle = downAngle;
    this.upAngle = upAngle;
    this.smoothing = smoothing;
    this.minRepMs = minRepMs;
    this.reset();
  }

  reset() {
    this.count = 0;
    this.phase = 'idle'; // idle → up ⇄ down
    this.angle = null;
    this.lastRepAt = -Infinity;
  }

  // 0 = totalmente em cima, 1 = profundidade suficiente.
  get progress() {
    if (this.angle == null) return 0;
    const p = (this.upAngle - this.angle) / (this.upAngle - this.downAngle);
    return Math.min(1, Math.max(0, p));
  }

  update(rawAngle, now) {
    if (rawAngle == null || Number.isNaN(rawAngle)) {
      return { counted: false, count: this.count, phase: this.phase, angle: this.angle, visible: false };
    }
    this.angle = this.angle == null ? rawAngle : this.angle + this.smoothing * (rawAngle - this.angle);

    let counted = false;
    if (this.phase === 'idle') {
      if (this.angle >= this.upAngle) this.phase = 'up';
    } else if (this.phase === 'up') {
      if (this.angle <= this.downAngle) this.phase = 'down';
    } else if (this.angle >= this.upAngle) {
      this.phase = 'up';
      if (now - this.lastRepAt >= this.minRepMs) {
        this.count++;
        this.lastRepAt = now;
        counted = true;
      }
    }
    return { counted, count: this.count, phase: this.phase, angle: this.angle, visible: true };
  }
}
