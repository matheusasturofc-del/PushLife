// PushLife — interações do site
(() => {
  const $ = (s) => document.querySelector(s);
  const t = (k, v) => window.I18N.t(k, v);
  const locale = () => ({ pt: 'pt-BR', en: 'en-US', es: 'es-ES' })[window.I18N.lang];

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

  // ---------- título do topo, palavra por palavra ----------
  function renderHero() {
    const h1 = $('#heroTitle');
    const words = [...t('hero_l1').split(' '), '\n', ...t('hero_l2').split(' ')];
    let i = 0;
    const span = (w, hot) => `<span class="word${hot ? ' hot' : ''}" style="animation-delay:${(0.05 + 0.12 * i++).toFixed(2)}s">${w}</span>`;
    h1.innerHTML = words.map((w) => (w === '\n' ? '<br>' : span(w))).join(' ') + ' ' + span(t('hero_hot'), true);
    h1.setAttribute('aria-label', `${t('hero_l1')} ${t('hero_l2')} ${t('hero_hot')}`);
  }
  renderHero();

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
  let msgKey = null;
  const horizontal = () => matchMedia('(max-width: 760px)').matches;

  function setDepth(d) {
    depth = Math.min(1, Math.max(0, d));
    draw(depth);
    handle.textContent = horizontal() ? '↔' : '↕';
    $('#dragHint').textContent = t(horizontal() ? 'hint_h' : 'hint_v');
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
    if ([5, 10, 20].includes(reps)) confetti(stage);
    if (reps === 1) msgKey = 'msg1';
    if (reps === 5) msgKey = 'msg5';
    if (reps === 20) msgKey = 'msg20';
    $('#dragMsg').textContent = msgKey ? t(msgKey) : '';
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
    { key: 'app_short', emo: '🎬', bg: '#111', locked: true },
    { key: 'app_social', emo: '📸', bg: 'linear-gradient(135deg,#f58529,#dd2a7b,#8134af)', locked: true },
    { key: 'app_videos', emo: '▶️', bg: '#e62117', locked: true },
    { key: 'app_game', emo: '🎮', bg: '#3b5bdb', locked: true },
    { key: 'app_msg', emo: '💬', bg: '#25a244', locked: false },
    { key: 'app_phone', emo: '📞', bg: '#2f9e44', locked: false },
    { key: null, emo: '', bg: 'url(assets/icon.svg) center/cover', locked: false, self: true },
    { key: 'app_photos', emo: '🖼️', bg: '#f08c00', locked: false },
    { key: 'app_news', emo: '📰', bg: '#495057', locked: true },
  ];
  const appName = (a) => (a.self ? 'PushLife' : t(a.key));
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
  function renderBlocker() {
    if (!current) return;
    $('#blockText').textContent = t('blocker_text', { app: appName(current) });
    $('#useBalance').textContent = balance > 0 ? t('use_balance', { n: balance }) : t('no_balance');
    $('#useBalance').disabled = balance <= 0;
  }
  function renderApps() {
    home.innerHTML = '';
    APPS.forEach((a) => {
      const b = document.createElement('button');
      b.className = 'app';
      b.innerHTML = `<span class="ico" style="background:${a.bg}">${a.emo}</span>${a.locked ? '<span class="lock">🔒</span>' : ''}<span>${appName(a)}</span>`;
      b.addEventListener('click', () => {
        if (a.self) return showToast(t('toast_self'));
        if (!a.locked) return showToast(t('toast_open', { app: appName(a) }));
        current = a;
        renderBlocker();
        blocker.classList.add('open');
      });
      home.appendChild(b);
    });
  }
  renderApps();
  $('#closeBlock').addEventListener('click', () => blocker.classList.remove('open'));
  $('#useBalance').addEventListener('click', () => {
    if (balance <= 0) return;
    const used = balance;
    setBalance(0);
    blocker.classList.remove('open');
    showToast(t('toast_unlocked', { app: appName(current), n: used }));
  });
  $('#emergency').addEventListener('click', () => {
    blocker.classList.remove('open');
    showToast(t('toast_emergency'));
  });
  $('#goTrain').addEventListener('click', () => blocker.classList.remove('open'));

  // ---------- cartas dos exercícios ----------
  const KEYS = ['pushup', 'squat', 'lunge', 'situp', 'jack', 'plank', 'wallsit', 'pullup', 'burpee', 'walk'];
  const cards = $('#cards');
  const finePointer = matchMedia('(hover: hover) and (pointer: fine)').matches;
  function renderCards() {
    const shown = cards.dataset.shown === '1';
    cards.querySelectorAll('svg').forEach((svg) => window.Figures.unmount(svg));
    cards.innerHTML = '';
    t('ex').forEach(([name, val, tip], i) => {
      const c = document.createElement('button');
      c.className = 'card';
      c.setAttribute('data-reveal', 'zoom');
      c.setAttribute('aria-label', t('flip_label', { name }));
      c.innerHTML = `<div class="inner">
        <div class="face front"><div class="fig"><svg aria-hidden="true"></svg></div>
          <div class="meta"><h3>${name}</h3><div class="row"><span class="val-chip">${val}</span><small>${t('flip_hint')}</small></div></div></div>
        <div class="face back"><span class="val">${val}</span><p>${tip}</p><small>${t('card_back')}</small></div></div>`;
      c.addEventListener('click', () => c.classList.toggle('flipped'));
      // inclina seguindo o mouse (só no computador)
      if (finePointer) {
        c.addEventListener('pointermove', (e) => {
          c.style.transitionDelay = '0s';
          const r = c.getBoundingClientRect();
          c.style.setProperty('--rx', `${((e.clientY - r.top) / r.height - 0.5) * -14}deg`);
          c.style.setProperty('--ry', `${((e.clientX - r.left) / r.width - 0.5) * 16}deg`);
          c.style.setProperty('--gx', `${((e.clientX - r.left) / r.width) * 100}%`);
          c.style.setProperty('--gy', `${((e.clientY - r.top) / r.height) * 100}%`);
        });
        c.addEventListener('pointerleave', () => { c.style.setProperty('--rx', '0deg'); c.style.setProperty('--ry', '0deg'); });
      }
      c.style.transitionDelay = `${(i % 5) * 0.06}s`;
      cards.appendChild(c);
      window.Figures.mount(c.querySelector('svg'), KEYS[i]);
      if (shown) c.classList.add('shown');
      else io.observe(c);
    });
  }
  renderCards();
  // depois que as cartas apareceram uma vez, ao trocar de idioma já vêm visíveis
  new IntersectionObserver((e, o) => { if (e[0].isIntersecting) { cards.dataset.shown = '1'; o.disconnect(); } }).observe(cards);

  // ---------- vídeo do topo no idioma escolhido ----------
  const video = $('#heroVideo');
  function renderVideo() {
    const src = `assets/video/promo-${window.I18N.lang}.mp4`;
    if (video.getAttribute('src') === src) return;
    video.style.opacity = 0;
    setTimeout(() => {
      video.setAttribute('src', src);
      video.play().catch(() => {});
      video.style.opacity = 1;
    }, video.getAttribute('src') ? 250 : 0);
  }
  renderVideo();

  // ---------- faixa correndo ----------
  function renderMarquee() {
    const items = t('marquee').map((x) => `<span>${x}</span><i>✦</i>`).join('');
    $('#marquee').innerHTML = items + items; // duas vezes para emendar sem pulo
  }
  renderMarquee();

  // ---------- números que contam ao aparecer ----------
  const counters = new IntersectionObserver((entries) => {
    entries.forEach((e) => {
      if (!e.isIntersecting) return;
      counters.unobserve(e.target);
      const end = Number(e.target.dataset.count);
      const t0 = performance.now();
      const step = (now) => {
        const k = Math.min(1, (now - t0) / 1200);
        e.target.textContent = Math.round(end * (1 - Math.pow(1 - k, 3)));
        if (k < 1) requestAnimationFrame(step);
        else e.target.classList.add('done');
      };
      requestAnimationFrame(step);
    });
  }, { threshold: 0.6 });
  document.querySelectorAll('[data-count]').forEach((el) => counters.observe(el));

  // ---------- confete ----------
  function confetti(host) {
    const colors = ['#ff6a00', '#ffc300', '#ff9a1f', '#1a1208', '#ffffff'];
    for (let i = 0; i < 28; i++) {
      const p = document.createElement('i');
      p.className = 'confetti';
      p.style.background = colors[i % colors.length];
      p.style.left = '50%';
      p.style.top = '45%';
      host.appendChild(p);
      const a = Math.random() * Math.PI * 2;
      const d = 90 + Math.random() * 160;
      p.animate([
        { transform: 'translate(-50%,-50%) rotate(0deg)', opacity: 1 },
        { transform: `translate(${Math.cos(a) * d}px, ${Math.sin(a) * d + 60}px) rotate(${Math.random() * 720}deg)`, opacity: 0 },
      ], { duration: 900 + Math.random() * 500, easing: 'cubic-bezier(.2,.8,.3,1)' }).onfinish = () => p.remove();
    }
  }

  // ---------- calculadora ----------
  const perDay = $('#perDay');
  const fmt = (n) => n.toLocaleString(locale());
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
    $('#filterLabel').textContent = t('filter_turning');
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
    $('#filterLabel').textContent = t('filter_kept');
    sw.focus();
  }
  $('#chKeep').addEventListener('click', keep);
  modal.addEventListener('click', (e) => { if (e.target === modal) keep(); });
  addEventListener('keydown', (e) => { if (e.key === 'Escape' && modal.classList.contains('open')) keep(); });

  // ---------- trocou de idioma: refaz o que é montado pelo script ----------
  document.addEventListener('langchange', () => {
    renderHero();
    renderApps();
    renderBlocker();
    renderCards();
    renderVideo();
    renderMarquee();
    calc();
    setDepth(depth);
    $('#dragMsg').textContent = msgKey ? t(msgKey) : '';
  });
})();
