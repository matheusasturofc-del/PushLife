# PushTrainer (rascunho)

Primeira versão de um app no estilo PushScroll: faça flexões/agachamentos para ganhar minutos de tela.
Ainda **não está definido**: plataforma, funcionalidades e visual vão ser decididos antes de continuar.

O que existe hoje (PWA, roda no navegador do celular):
- Contagem de repetições pela câmera com IA (MediaPipe Pose, roda no aparelho) ou pelo "modo toque" (encostar o nariz na tela)
- Saldo de minutos de tela, liberar tempo com cronômetro, meta diária, sequência de dias, histórico
- Funciona offline e pode ser instalado na tela inicial

Limitação: um app web **não consegue bloquear outros apps** (Instagram, TikTok...). Para isso é preciso um app nativo
(iOS: API Screen Time/Family Controls; Android: Accessibility/UsageStats).

## Abrir no celular

Depois de ativar o GitHub Pages (Settings → Pages → Source: **GitHub Actions**), o app fica em
https://matheusasturofc-del.github.io/PushTrainer/ e é atualizado a cada push.
A câmera só funciona em HTTPS, por isso o Pages (ou `localhost`).

## Rodar no computador
```
npm start   # abre em http://localhost:8080
npm test    # testes da lógica de contagem e do saldo
```
