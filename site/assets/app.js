// PushLife — interações do site
(() => {
  const $ = (s) => document.querySelector(s);

  // ---------- barra de progresso + menu escuro ao rolar ----------
  const bar = $('.progress');
  const nav = $('.nav');
  const rail = $('#rail');
  const steps = $('#steps');
  function onScroll() {
    const max = document.documentElement.scrollHeight - innerHeight;
    bar.style.transform = `scaleX(${max > 0 ? scrollY / max : 0})`;
    nav.classList.toggle('solid', scrollY > 40);
    // a linha dos passos enche conforme você rola
    if (steps) {
      const r = steps.getBoundingClientRect();
      const t = (innerHeight * 0.75 - r.top) / r.height;
      rail.style.transform = `scaleY(${Math.min(1, Math.max(0, t))})`;
    }
  }
  addEventListener('scroll', onScroll, { passive: true });
  onScroll();

  // ---------- coisas aparecendo ao rolar ----------
  const io = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (e.isIntersecting) {
        e.target.classList.add('shown');
        io.unobserve(e.target);
      }
    });
  }, { threshold: 0.15 });
  document.querySelectorAll('[data-reveal]').forEach((el, i) => {
    el.style.transitionDelay = `${(i % 3) * 0.08}s`;
    io.observe(el);
  });

  // ---------- brilho seguindo o mouse no topo ----------
  const hero = $('.hero');
  const glow = $('.hero .glow');
  hero.addEventListener('pointermove', (e) => {
    const r = hero.getBoundingClientRect();
    glow.style.left = `${e.clientX - r.left}px`;
    glow.style.top = `${e.clientY - r.top}px`;
  });

  // ---------- saldo (compartilhado entre as brincadeiras) ----------
  let balance = 0;
  let reps = 0;
  function setBalance(v) {
    balance = Math.max(0, v);
    const txt = `${balance} min`;
    $('#balance').textContent = txt;
    $('#balance2').textContent = txt;
  }

  // ---------- arraste para fazer flexão ----------
  // poses do bonequinho do app (de perfil, olhando para a esquerda)
  const UP = { head: [44, 98], neck: [60, 110], hip: [127, 147], elbow: [60, 144], hand: [60, 180], knee: [155, 163], foot: [184, 180] };
  const DOWN = { head: [43, 148], neck: [60, 158], hip: [127, 170], elbow: [86, 166], hand: [60, 180], knee: [155, 175], foot: [184, 180] };
  const fig = $('#figure');
  const lerp = (a, b, t) => [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
  const line = (a, b, color, w) => `<line x1="${a[0]}" y1="${a[1]}" x2="${b[0]}" y2="${b[1]}" stroke="${color}" stroke-width="${w}" stroke-linecap="round"/>`;
  function draw(t) {
    const p = {};
    for (const k in UP) p[k] = lerp(UP[k], DOWN[k], t);
    const back = '#b84a00';
    const o = '#ff6a00';
    const off = (pt, dx, dy) => [pt[0] + dx, pt[1] + dy];
    fig.innerHTML =
      line([10, 182], [190, 182], '#d8c4b4', 3) +
      line(off(p.neck, 4, 0), off(p.elbow, 6, 0), back, 11) + line(off(p.elbow, 6, 0), off(p.hand, 6, 0), back, 10) +
      line(p.hip, off(p.knee, 0, 2), back, 12) + line(off(p.knee, 0, 2), off(p.foot, -4, 0), back, 11) +
      line(p.neck, p.hip, o, 17) + line(p.neck, p.head, o, 8) +
      `<circle cx="${p.head[0]}" cy="${p.head[1]}" r="14" fill="${o}"/>` +
      `<circle cx="${p.head[0] - 6}" cy="${p.head[1] - 2}" r="2.8" fill="#fff"/>` +
      `<circle cx="${p.neck[0] + (p.hip[0] - p.neck[0]) * 0.25}" cy="${p.neck[1] + (p.hip[1] - p.neck[1]) * 0.25}" r="3.5" fill="#ffc300"/>` +
      line(p.hip, p.knee, o, 12) + line(p.knee, p.foot, o, 11) +
      line(p.neck, p.elbow, o, 11) + line(p.elbow, p.hand, o, 10);
  }

  const slider = $('#slider');
  const handle = $('#handle');
  const fill = $('#fill');
  const stage = $('#stage');
  let depth = 0;
  let down = false;
  const horizontal = () => matchMedia('(max-width: 760px)').matches;

  function setDepth(d) {
    depth = Math.min(1, Math.max(0, d));
    draw(depth);
    handle.textContent = horizontal() ? '↔' : '↕';
    $('#dragHint').textContent = horizontal() ? 'Arraste → e ←' : 'Arraste ↓ e ↑';
    if (horizontal()) {
      handle.style.top = '50%';
      handle.style.left = `calc(36px + (100% - 72px) * ${depth})`;
      fill.style.width = `calc(36px + (100% - 72px) * ${depth})`;
      fill.style.height = '';
    } else {
      handle.style.left = '50%';
      handle.style.top = `calc(36px + (100% - 72px) * ${depth})`;
      fill.style.height = `calc(36px + (100% - 72px) * ${depth})`;
      fill.style.width = '';
    }
    slider.setAttribute('aria-valuenow', Math.round(depth * 100));
    // mesma ideia do app: precisa descer bem e voltar em cima para contar
    if (!down && depth >= 0.85) down = true;
    if (down && depth <= 0.15) {
      down = false;
      countRep();
    }
  }

  function countRep() {
    reps += 1;
    $('#reps').textContent = reps;
    setBalance(balance + 1);
    const pop = document.createElement('span');
    pop.className = 'pop';
    pop.textContent = '+1 min';
    pop.style.left = `${30 + Math.random() * 40}%`;
    pop.style.top = '40%';
    stage.appendChild(pop);
    setTimeout(() => pop.remove(), 1000);
    const msg = $('#dragMsg');
    if (reps === 1) msg.textContent = 'Isso! Mais algumas…';
    if (reps === 5) msg.textContent = 'Boa! 5 minutos de saldo. Lá embaixo dá para usar para liberar um app. 👇';
    if (reps === 20) msg.textContent = 'Uau, 20! Imagina isso de verdade, com a câmera contando. 💪';
  }

  function fromPointer(e) {
    const r = slider.getBoundingClientRect();
    const d = horizontal() ? (e.clientX - r.left - 36) / (r.width - 72) : (e.clientY - r.top - 36) / (r.height - 72);
    setDepth(d);
  }
  slider.addEventListener('pointerdown', (e) => {
    slider.setPointerCapture(e.pointerId);
    slider.classList.add('dragging');
    fromPointer(e);
  });
  slider.addEventListener('pointermove', (e) => {
    if (slider.classList.contains('dragging')) fromPointer(e);
  });
  const release = () => slider.classList.remove('dragging');
  slider.addEventListener('pointerup', release);
  slider.addEventListener('pointercancel', release);
  slider.addEventListener('keydown', (e) => {
    const step = { ArrowDown: 0.1, ArrowRight: 0.1, ArrowUp: -0.1, ArrowLeft: -0.1, End: 1, Home: -1 }[e.key];
    if (step === undefined) return;
    e.preventDefault();
    setDepth(e.key === 'End' ? 1 : e.key === 'Home' ? 0 : depth + step);
  });
  addEventListener('resize', () => setDepth(depth));
  setDepth(0);

  // ---------- celular com apps bloqueados ----------
  const APPS = [
    { name: 'Vídeos curtos', emo: '🎬', bg: '#111', locked: true },
    { name: 'Rede social', emo: '📸', bg: 'linear-gradient(135deg,#f58529,#dd2a7b,#8134af)', locked: true },
    { name: 'Vídeos', emo: '▶️', bg: '#e62117', locked: true },
    { name: 'Jogo', emo: '🎮', bg: '#3b5bdb', locked: true },
    { name: 'Mensagens', emo: '💬', bg: '#25a244', locked: false },
    { name: 'Telefone', emo: '📞', bg: '#2f9e44', locked: false },
    { name: 'PushLife', emo: '', bg: 'url(assets/icon.svg) center/cover', locked: false, self: true },
    { name: 'Fotos', emo: '🖼️', bg: '#f08c00', locked: false },
    { name: 'Notícias', emo: '📰', bg: '#495057', locked: true },
  ];
  const home = $('#home');
  const blocker = $('#blocker');
  const toast = $('#toast');
  let current = null;
  let toastTimer;
  function showToast(text) {
    toast.textContent = text;
    toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.remove('show'), 2200);
  }
  APPS.forEach((a) => {
    const b = document.createElement('button');
    b.className = 'app';
    b.innerHTML = `<span class="ico" style="background:${a.bg}">${a.emo}</span>${a.locked ? '<span class="lock">🔒</span>' : ''}<span>${a.name}</span>`;
    b.addEventListener('click', () => {
      if (a.self) return showToast('Esse é o PushLife. Hora de treinar! 💪');
      if (!a.locked) return showToast(`${a.name} não está bloqueado. Abriu normal.`);
      current = a;
      $('#blockText').textContent = `Seu saldo acabou para usar ${a.name}. Faça flexões para liberar.`;
      $('#useBalance').textContent = balance > 0 ? `Usar meu saldo (${balance} min)` : 'Sem saldo ainda';
      $('#useBalance').disabled = balance <= 0;
      blocker.classList.add('open');
    });
    home.appendChild(b);
  });
  $('#closeBlock').addEventListener('click', () => blocker.classList.remove('open'));
  $('#useBalance').addEventListener('click', () => {
    if (balance <= 0) return;
    const used = balance;
    setBalance(0);
    blocker.classList.remove('open');
    showToast(`${current.name} liberado por ${used} min. ⏱️`);
  });
  $('#emergency').addEventListener('click', () => {
    blocker.classList.remove('open');
    showToast('Emergência: 5 min. Você fica devendo 10 flexões. 😅');
  });
  $('#goTrain').addEventListener('click', () => blocker.classList.remove('open'));

  // ---------- cartas dos exercícios ----------
  const EX = [
    ['💪', 'Flexão', '1 flexão = 1 min', 'Celular de lado, corpo inteiro aparecendo. Conta pelo cotovelo.'],
    ['🏋️', 'Agachamento', '1 = 30 s', 'Conta pelo joelho, com o tronco em pé.'],
    ['🦵', 'Afundo', '1 = 30 s', 'De lado. O joelho da frente desce até uns 90°.'],
    ['🔥', 'Abdominal', '1 = 30 s', 'Deitado, joelhos dobrados. Conta pelo quadril.'],
    ['⭐', 'Polichinelo', '1 = 15 s', 'De frente. Mãos para cima e pés afastados.'],
    ['🧱', 'Prancha', '1 s = 3 s', 'Corpo reto e joelhos esticados. Conta o tempo parado.'],
    ['🪑', 'Agachamento isométrico', '1 s = 3 s', 'Costas na parede, joelhos dobrados. Segura!'],
    ['🧗', 'Barra fixa', '1 = 2 min', 'Mãos acima dos ombros. Sobe até o queixo passar.'],
    ['⚡', 'Burpee', '1 = 1 min 30 s', 'Em pé, chão, em pé. O mais puxado de todos.'],
    ['👟', 'Caminhada', '100 passos = 1 min', 'Usa o sensor de passos. Só andar.'],
  ];
  const cards = $('#cards');
  EX.forEach(([emo, name, val, tip], i) => {
    const c = document.createElement('button');
    c.className = 'card';
    c.setAttribute('data-reveal', 'zoom');
    c.setAttribute('aria-label', `${name}: toque para ver quanto vale`);
    c.innerHTML = `<div class="inner"><div class="face front"><span class="emo">${emo}</span><div><h3>${name}</h3><small>Toque para virar ↻</small></div></div>` +
      `<div class="face back"><span class="val">${val}</span><p>${tip}</p></div></div>`;
    c.addEventListener('click', () => c.classList.toggle('flipped'));
    c.style.transitionDelay = `${(i % 5) * 0.06}s`;
    cards.appendChild(c);
    io.observe(c);
  });

  // ---------- calculadora ----------
  const perDay = $('#perDay');
  const fmt = (n) => n.toLocaleString('pt-BR');
  const hm = (min) => {
    const h = Math.floor(min / 60);
    const m = min % 60;
    return h && m ? `${h} h ${m} min` : h ? `${h} h` : `${m} min`;
  };
  function calc() {
    const n = Number(perDay.value);
    $('#perDayOut').textContent = n;
    const earned = Math.min(n, 120); // o saldo guarda até 2 h
    const total = 60 + earned;
    $('#outDay').textContent = hm(total);
    $('#outMonth').textContent = fmt(n * 30);
    $('#outYear').textContent = fmt(n * 365);
    $('#barFree').style.width = `${(60 / 180) * 100}%`;
    $('#barEarned').style.width = `${(earned / 180) * 100}%`;
  }
  perDay.addEventListener('input', calc);
  calc();

  // ---------- filtro: tente desligar ----------
  const sw = $('#filterSwitch');
  const modal = $('#challenge');
  let timer;
  sw.addEventListener('click', () => {
    sw.setAttribute('aria-checked', 'false');
    $('#filterLabel').textContent = 'Desligando…';
    modal.classList.add('open');
    let s = 180;
    $('#chTimer').textContent = '3:00';
    clearInterval(timer);
    timer = setInterval(() => {
      s -= 1;
      $('#chTimer').textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
      if (s <= 0) clearInterval(timer);
    }, 1000);
    $('#chKeep').focus();
  });
  function keep() {
    clearInterval(timer);
    modal.classList.remove('open');
    sw.setAttribute('aria-checked', 'true');
    $('#filterLabel').textContent = 'Continua ligado 💪';
    sw.focus();
  }
  $('#chKeep').addEventListener('click', keep);
  modal.addEventListener('click', (e) => { if (e.target === modal) keep(); });
  addEventListener('keydown', (e) => { if (e.key === 'Escape' && modal.classList.contains('open')) keep(); });
})();
