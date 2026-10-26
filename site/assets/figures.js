// bonequinho laranja do app fazendo cada exercício (mesmas poses do ExerciseAnimation.kt)
(() => {
  const P = (x, y) => [x, y];
  // fig(cabeça, pescoço, quadril, cotovelo, mão, joelho, pé) — membros de trás iguais aos da frente, se não disser
  const fig = (head, neck, hip, elbow, hand, knee, foot, b = {}) => ({
    head, neck, hip, elbowF: elbow, handF: hand, elbowB: b.elbowB || elbow, handB: b.handB || hand,
    kneeF: knee, footF: foot, kneeB: b.kneeB || knee, footB: b.footB || foot,
  });
  const full = (head, neck, hip, elbowF, handF, elbowB, handB, kneeF, footF, kneeB, footB) =>
    ({ head, neck, hip, elbowF, handF, elbowB, handB, kneeF, footF, kneeB, footB });

  const STAND = fig(P(100, 26), P(100, 46), P(100, 105), P(100, 76), P(102, 104), P(102, 142), P(100, 180));
  const crouch = fig(P(118, 96), P(108, 110), P(84, 138), P(122, 142), P(128, 180), P(116, 142), P(100, 180));
  const plankB = fig(P(150, 102), P(134, 112), P(80, 146), P(134, 146), P(134, 180), P(52, 163), P(22, 180));
  const walkA = fig(P(100, 26), P(100, 46), P(100, 105), P(92, 74), P(86, 100), P(112, 141), P(124, 180),
    { elbowB: P(108, 74), handB: P(116, 100), kneeB: P(92, 143), footB: P(78, 180) });
  const walkB = { ...walkA, elbowF: walkA.elbowB, handF: walkA.handB, elbowB: walkA.elbowF, handB: walkA.handF,
    kneeF: walkA.kneeB, footF: walkA.footB, kneeB: walkA.kneeF, footB: walkA.footF };

  const ANIMS = {
    pushup: { ms: 700, facing: -1, frames: [
      fig(P(44, 98), P(60, 110), P(127, 147), P(60, 144), P(60, 180), P(155, 163), P(184, 180)),
      fig(P(43, 148), P(60, 158), P(127, 170), P(86, 166), P(60, 180), P(155, 175), P(184, 180))] },
    squat: { ms: 800, facing: 1, frames: [
      fig(P(100, 26), P(100, 46), P(100, 105), P(124, 52), P(148, 54), P(103, 142), P(100, 180)),
      fig(P(95, 60), P(88, 78), P(72, 132), P(112, 80), P(138, 80), P(112, 134), P(100, 180))] },
    lunge: { ms: 850, facing: 1, frames: [
      fig(P(100, 26), P(100, 46), P(100, 105), P(86, 74), P(98, 100), P(118, 141), P(136, 180), { kneeB: P(84, 142), footB: P(66, 180) }),
      fig(P(100, 54), P(100, 74), P(100, 132), P(86, 102), P(98, 128), P(138, 134), P(138, 180), { kneeB: P(84, 172), footB: P(56, 180) })] },
    situp: { ms: 800, facing: 1, frames: [
      fig(P(30, 168), P(48, 172), P(100, 176), P(56, 160), P(62, 168), P(132, 142), P(164, 180)),
      fig(P(92, 104), P(88, 124), P(100, 176), P(102, 136), P(104, 126), P(132, 142), P(164, 180))] },
    jack: { ms: 450, facing: 0, frames: [
      full(P(100, 28), P(100, 48), P(100, 105), P(90, 78), P(86, 106), P(110, 78), P(114, 106), P(96, 142), P(94, 180), P(104, 142), P(106, 180)),
      full(P(100, 22), P(100, 42), P(100, 100), P(76, 32), P(70, 10), P(124, 32), P(130, 10), P(86, 138), P(70, 180), P(114, 138), P(130, 180))] },
    plank: { ms: 1500, facing: -1, frames: [
      fig(P(42, 140), P(58, 150), P(125, 156), P(58, 178), P(88, 178), P(155, 166), P(184, 180)),
      fig(P(42, 138), P(58, 148), P(125, 153), P(58, 178), P(88, 178), P(155, 165), P(184, 180))] },
    wallsit: { ms: 1500, facing: 1, prop: 'wall', frames: [
      fig(P(64, 52), P(60, 70), P(62, 126), P(72, 100), P(92, 118), P(112, 126), P(112, 180)),
      fig(P(64, 54), P(60, 72), P(62, 128), P(72, 102), P(92, 120), P(112, 127), P(112, 180))] },
    pullup: { ms: 900, facing: 0, prop: 'bar', frames: [
      full(P(100, 48), P(100, 66), P(100, 120), P(84, 42), P(80, 20), P(116, 42), P(120, 20), P(96, 150), P(96, 176), P(104, 150), P(104, 176)),
      full(P(100, 14), P(100, 36), P(100, 92), P(70, 40), P(80, 20), P(130, 40), P(120, 20), P(96, 122), P(96, 150), P(104, 122), P(104, 150))] },
    burpee: { ms: 550, facing: 1, frames: [STAND, crouch, plankB, crouch] },
    walk: { ms: 450, facing: 1, frames: [walkA, walkB] },
  };

  const NS = 'http://www.w3.org/2000/svg';
  const ORANGE = '#ff6a00';
  const BACK = '#b84a00';
  const ease = (t) => t * t * (3 - 2 * t); // suave no começo e no fim
  const mix = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];

  function make(svg, tag, attrs) {
    const e = document.createElementNS(NS, tag);
    for (const k in attrs) e.setAttribute(k, attrs[k]);
    svg.appendChild(e);
    return e;
  }

  // monta o desenho uma vez; depois só mexe nas coordenadas
  function create(svg, key) {
    const anim = ANIMS[key];
    svg.setAttribute('viewBox', '0 0 200 200');
    const ground = getComputedStyle(document.documentElement).getPropertyValue('--line').trim() || '#ead9cb';
    make(svg, 'line', { x1: 10, y1: 182, x2: 190, y2: 182, stroke: ground, 'stroke-width': 3, 'stroke-linecap': 'round' });
    if (anim.prop === 'wall') make(svg, 'line', { x1: 48, y1: 20, x2: 48, y2: 182, stroke: ground, 'stroke-width': 6, 'stroke-linecap': 'round' });
    if (anim.prop === 'bar') make(svg, 'line', { x1: 55, y1: 20, x2: 145, y2: 20, stroke: ground, 'stroke-width': 6, 'stroke-linecap': 'round' });
    const bone = (color, w) => make(svg, 'line', { stroke: color, 'stroke-width': w, 'stroke-linecap': 'round' });
    const parts = {
      armB1: bone(BACK, 11), armB2: bone(BACK, 10), legB1: bone(BACK, 12), legB2: bone(BACK, 11),
      torso: bone(ORANGE, 17), neckL: bone(ORANGE, 8),
      head: make(svg, 'circle', { r: 14, fill: ORANGE }),
      eye1: make(svg, 'circle', { r: anim.facing === 0 ? 2.6 : 2.8, fill: '#fff' }),
      eye2: anim.facing === 0 ? make(svg, 'circle', { r: 2.6, fill: '#fff' }) : null,
      dot: make(svg, 'circle', { r: 3.5, fill: '#ffc300' }),
      legF1: bone(ORANGE, 12), legF2: bone(ORANGE, 11), armF1: bone(ORANGE, 11), armF2: bone(ORANGE, 10),
    };
    const set = (l, a, b) => { l.setAttribute('x1', a[0]); l.setAttribute('y1', a[1]); l.setAttribute('x2', b[0]); l.setAttribute('y2', b[1]); };
    const at = (c, p) => { c.setAttribute('cx', p[0]); c.setAttribute('cy', p[1]); };

    function draw(timeMs) {
      const n = anim.frames.length;
      const p = (timeMs / anim.ms) % n;
      const i = Math.floor(p);
      const t = ease(p - i);
      const A = anim.frames[i];
      const B = anim.frames[(i + 1) % n];
      const f = {};
      for (const k in A) f[k] = mix(A[k], B[k], t);
      set(parts.armB1, f.neck, f.elbowB); set(parts.armB2, f.elbowB, f.handB);
      set(parts.legB1, f.hip, f.kneeB); set(parts.legB2, f.kneeB, f.footB);
      set(parts.torso, f.neck, f.hip); set(parts.neckL, f.neck, f.head);
      at(parts.head, f.head);
      if (anim.facing === 0) { at(parts.eye1, [f.head[0] - 5, f.head[1] - 2]); at(parts.eye2, [f.head[0] + 5, f.head[1] - 2]); }
      else at(parts.eye1, [f.head[0] + 6 * anim.facing, f.head[1] - 2]);
      at(parts.dot, mix(f.neck, f.hip, 0.25));
      set(parts.legF1, f.hip, f.kneeF); set(parts.legF2, f.kneeF, f.footF);
      set(parts.armF1, f.neck, f.elbowF); set(parts.armF2, f.elbowF, f.handF);
    }
    draw(0);
    return draw;
  }

  // anima só o que está aparecendo na tela
  const live = new Set();
  const seen = new IntersectionObserver((entries) => {
    entries.forEach((e) => (e.isIntersecting ? live.add(e.target.__draw) : live.delete(e.target.__draw)));
  });
  const still = matchMedia('(prefers-reduced-motion: reduce)').matches;
  function loop(t) {
    live.forEach((draw) => draw(t));
    requestAnimationFrame(loop);
  }
  if (!still) requestAnimationFrame(loop);

  window.Figures = {
    keys: Object.keys(ANIMS),
    mount(svg, key) {
      svg.__draw = create(svg, key);
      seen.observe(svg);
    },
    unmount(svg) {
      live.delete(svg.__draw);
      seen.unobserve(svg);
    },
  };
})();
