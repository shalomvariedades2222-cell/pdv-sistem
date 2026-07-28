/* ==========================================================================
   SLMsys — Service Worker (modo offline do APP em si)
   ==========================================================================
   O que isso faz: guarda uma cópia do próprio site (este index.html, o
   manifest, o ícone e as bibliotecas externas) no cache do navegador na
   primeira vez que o app é aberto com internet. Depois disso, sempre que o
   navegador tentar abrir o site e não conseguir falar com a internet, ele
   serve essa cópia guardada em vez de mostrar erro de "sem conexão".

   O que isso NÃO faz: isso não guarda os dados da loja (produtos, vendas,
   clientes...). Esses dados continuam sendo tratados pelo próprio
   index.html, que já salva tudo no localStorage do aparelho (cache local
   de emergência) — essa parte já funciona independente deste arquivo.

   Toda vez que você alterar o index.html e publicar de novo, troque o
   número da versão abaixo (CACHE_NAME) — isso avisa o navegador que existe
   uma versão nova e faz ele baixar tudo de novo, senão ele continuaria
   usando a cópia antiga guardada no cache pra sempre.
========================================================================== */
const CACHE_NAME = 'slmsys-shell-v1';

// Arquivos do próprio site (mesma origem) que formam o "esqueleto" do app.
const ARQUIVOS_DO_APP = [
  './',
  './index.html',
  './manifest.json',
  './ice.png'
];

// Bibliotecas externas usadas pelo app (código de barras, QR code, PDF,
// impressão da etiqueta). Guardamos elas também pra tudo isso continuar
// funcionando offline (gerar etiqueta, recibo em PDF, etc.).
const ARQUIVOS_EXTERNOS = [
  'https://cdnjs.cloudflare.com/ajax/libs/JsBarcode/3.11.6/JsBarcode.all.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/qrious/4.0.2/qrious.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js',
  'https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js'
];

self.addEventListener('install', function(event){
  event.waitUntil(
    caches.open(CACHE_NAME).then(function(cache){
      // Cada arquivo é adicionado separadamente: se algum falhar (ex: um
      // CDN externo fora do ar bem na hora da instalação), os outros ainda
      // são guardados, em vez de o cache inteiro falhar por causa de um só.
      return Promise.all(
        ARQUIVOS_DO_APP.concat(ARQUIVOS_EXTERNOS).map(function(url){
          return cache.add(url).catch(function(e){
            console.warn('Service worker: não deu pra guardar em cache:', url, e);
          });
        })
      );
    }).then(function(){
      return self.skipWaiting(); // ativa a versão nova assim que instalar, sem esperar recarregar
    })
  );
});

self.addEventListener('activate', function(event){
  event.waitUntil(
    caches.keys().then(function(nomes){
      return Promise.all(
        nomes.filter(function(nome){ return nome !== CACHE_NAME; })
             .map(function(nome){ return caches.delete(nome); }) // limpa versões antigas do cache
      );
    }).then(function(){ return self.clients.claim(); })
  );
});

self.addEventListener('fetch', function(event){
  const req = event.request;
  const url = new URL(req.url);

  // NUNCA intercepta os pedidos para o servidor da loja (Google Apps
  // Script/Drive): esses precisam sempre tentar ir pela internet de
  // verdade. Se interceptássemos, o app poderia achar que salvou/carregou
  // dados quando na real só recebeu uma resposta antiga guardada em cache.
  if(url.hostname.indexOf('script.google.com') !== -1) return;
  if(req.method !== 'GET') return;

  event.respondWith(
    caches.match(req).then(function(respostaEmCache){
      // Cache-first para o esqueleto do app: se já temos guardado, usa na
      // hora (mais rápido e funciona sem internet). Em paralelo, tenta
      // atualizar o cache com uma versão mais nova pra próxima vez.
      const buscaNaRede = fetch(req).then(function(respostaDaRede){
        if(respostaDaRede && respostaDaRede.ok){
          const copia = respostaDaRede.clone();
          caches.open(CACHE_NAME).then(function(cache){ cache.put(req, copia); });
        }
        return respostaDaRede;
      }).catch(function(){
        return respostaEmCache; // sem internet: cai pro que já está guardado
      });
      return respostaEmCache || buscaNaRede;
    })
  );
});
