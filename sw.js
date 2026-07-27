/* Service Worker do SLMsys.
   Estratégia "network-first": sempre tenta buscar a versão mais nova
   na internet primeiro (importante porque o app sincroniza dados na
   nuvem); só usa a cópia salva no aparelho se estiver sem internet.
   Isso é só o que o navegador exige para permitir "Instalar app" —
   não afeta a sincronização normal dos dados da loja. */
const CACHE_NAME = 'slmsys-cache-v1';
const ARQUIVOS_BASE = ['./index.html', './manifest.json', './ic.png'];

self.addEventListener('install', (event) => {
  self.skipWaiting();
  event.waitUntil(
    caches.open(CACHE_NAME).then((cache) => cache.addAll(ARQUIVOS_BASE)).catch(() => {})
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys().then((nomes) =>
      Promise.all(nomes.filter((n) => n !== CACHE_NAME).map((n) => caches.delete(n)))
    )
  );
  self.clients.claim();
});

self.addEventListener('fetch', (event) => {
  if (event.request.method !== 'GET') return;
  event.respondWith(
    fetch(event.request)
      .then((resp) => {
        const copia = resp.clone();
        caches.open(CACHE_NAME).then((cache) => cache.put(event.request, copia)).catch(() => {});
        return resp;
      })
      .catch(() => caches.match(event.request))
  );
});
