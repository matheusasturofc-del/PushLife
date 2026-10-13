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
| 2 | Contagem de flexões pela câmera (celular no chão) e saldo de tempo | próxima |
| 3 | Bloqueio de apps, emergência com dívida de flexões | |
| 4 | Mais exercícios, instruções e aviso de postura | |
| 5 | Sequência, metas, níveis, conquistas, gráficos, notificações | |
| 6 | Bloqueio de sites adultos | |
| 7 | Perfil, conta e backup na nuvem; preparação para a Play Store | |

## Tecnologia

- Kotlin + Jetpack Compose (Material 3), Android 8.0 ou mais novo
- IA da câmera: ML Kit (roda no aparelho; nenhuma imagem sai do celular)
- O GitHub Actions compila, testa e publica o APK a cada mudança (`.github/workflows/android.yml`)

## Abrir no Android Studio (opcional)

`File > Open` e escolha a pasta do projeto. Para rodar no celular, ative a Depuração USB
(Configurações > Sobre o telefone > toque 7 vezes em "Número da versão" > Opções do desenvolvedor).

A pasta `keystore/` tem só a chave de **desenvolvimento** (senha pública `android`). A chave de publicação na
Play Store vai ficar fora do repositório.
