// Service worker: deixa o app abrir offline e guarda a IA (modelo + wasm) após o 1º uso.
const VERSION = 'v1';
const APP_CACHE = `pushtrainer-app-${VERSION}`;
const CDN_CACHE = 'pushtrainer-cdn';
const APP_SHELL = [
  './',
  'index.html',
  'css/styles.css',
  'js/app.js',
  'js/counter.js',
  'js/store.js',
  'js/pose.js',
  'manifest.webmanifest',
  'icons/icon.svg',
  'icons/icon-192.png',
  'icons/icon-512.png',
];
const CDN_HOSTS = ['cdn.jsdelivr.net', 'storage.googleapis.com'];

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(APP_CACHE).then((c) => c.addAll(APP_SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => Promise.all(keys.filter((k) => k.startsWith('pushtrainer-app-') && k !== APP_CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim()),
  );
});

self.addEventListener('fetch', (event) => {
  const { request } = event;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);

  // Arquivos do app: rede primeiro (pega atualizações), cache se offline.
  if (url.origin === self.location.origin) {
    event.respondWith(
      fetch(request)
        .then((res) => {
          if (res.ok) caches.open(APP_CACHE).then((c) => c.put(request, res.clone()));
          return res;
        })
        .catch(() => caches.match(request, { ignoreSearch: true })),
    );
    return;
  }

  // IA no CDN: URLs com versão fixa, então cache primeiro.
  if (CDN_HOSTS.includes(url.hostname)) {
    event.respondWith(
      caches.match(request).then(
        (hit) =>
          hit ||
          fetch(request).then((res) => {
            if (res.ok) caches.open(CDN_CACHE).then((c) => c.put(request, res.clone()));
            return res;
          }),
      ),
    );
  }
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  event.waitUntil(
    self.clients.matchAll({ type: 'window' }).then((list) => (list[0] ? list[0].focus() : self.clients.openWindow('./'))),
  );
});
