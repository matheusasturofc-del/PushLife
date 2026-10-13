# PushLife

App Android que transforma exercício em tempo de tela: faça flexões (e outros exercícios) para liberar
Instagram, TikTok e outros apps bloqueados. Inspirado no PushScroll, gratuito e em português, inglês e espanhol.

## Instalar no celular (versão de teste)

1. No celular, abra: https://github.com/matheusasturofc-del/PushTrainer/releases/latest/download/PushLife.apk
2. Permita instalar apps do navegador quando o Android pedir ("fontes desconhecidas").
3. Cada versão nova instala por cima da anterior, sem apagar seus dados.

## Plano

| Fase | O que entra | Status |
|---|---|---|
| 1 | Base do app: projeto, tema, 3 idiomas, APK automático | pronto |
| 2 | Contagem de flexões pela câmera (celular no chão) e saldo de tempo | pronto |
| 3 | Bloqueio de apps, emergência com dívida de flexões | pronto |
| 4 | Mais exercícios, instruções e aviso de postura | pronto |
| 5 | Sequência, metas, níveis, conquistas, gráficos, notificações | pronto |
| 6 | Bloqueio de sites adultos | pronto |
| 7 | Perfil, backup em arquivo (Google Drive) e preparação para a Play Store | pronto (login com e-mail depende do Firebase) |

Extra: boas-vindas completas na primeira abertura (23 telas: perguntas, resultado, projeção, como funciona,
permissões, escolha dos apps, plano pronto e primeiro treino).

## Tecnologia

- Kotlin + Jetpack Compose (Material 3), Android 8.0 ou mais novo
- IA da câmera: ML Kit, detecção de rosto (flexão com o celular no chão) e de pose (agachamento, abdominal,
  polichinelo, prancha, barra fixa, burpee); roda no aparelho e nenhuma imagem sai do celular
- Caminhada: sensor de passos do celular
- Bloqueio: permissão "Acesso aos dados de uso" para saber o app aberto, um serviço em primeiro plano que
  desconta o saldo a cada segundo e "Sobrepor a outros apps" para mostrar a tela de bloqueio
- Filtro de sites adultos: VPN local que só recebe as consultas de DNS; lista própria + Cloudflare for Families
- O GitHub Actions compila, testa e publica o APK a cada mudança (`.github/workflows/android.yml`)

## Abrir no Android Studio (opcional)

`File > Open` e escolha a pasta do projeto. Para rodar no celular, ative a Depuração USB
(Configurações > Sobre o telefone > toque 7 vezes em "Número da versão" > Opções do desenvolvedor).

Publicação na Play Store: veja [`play-store/README.md`](play-store/README.md). Privacidade: [`PRIVACY.md`](PRIVACY.md).

A pasta `keystore/` tem só a chave de **desenvolvimento** (senha pública `android`). A chave de publicação na
Play Store vai ficar fora do repositório.
