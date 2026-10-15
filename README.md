# PushLife

App Android que transforma exercício em tempo de tela: faça flexões (e outros exercícios) para liberar
Instagram, TikTok e outros apps bloqueados. Inspirado no PushScroll, gratuito e em português, inglês e espanhol.

## Instalar no celular (versão de teste)

1. No celular, abra: https://github.com/matheusasturofc-del/PushLife/releases/latest/download/PushLife.apk
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
| 7 | Perfil, backup em arquivo (Google Drive) e preparação para a Play Store | pronto (contas: veja CONTAS.md) |

Extra: boas-vindas completas na primeira abertura (23 telas: perguntas, resultado, projeção, como funciona,
permissões, escolha dos apps, plano pronto e primeiro treino).

## Contas (e-mail, @ único e backup na nuvem)

Usam o Supabase (grátis, sem cartão), com código de 6 dígitos no e-mail para criar conta e entrar. O passo a
passo para ligar está em [CONTAS.md](CONTAS.md); sem ele o app funciona sem contas.

## Atualização obrigatória (Uptodown)

O app lê o arquivo [`update.json`](update.json) toda vez que é aberto. Se a versão instalada for mais velha
que `minVersionCode`, ele mostra só a tela "Nova atualização disponível", e o botão **Atualizar** abre o
link `url` (a página do PushLife na Uptodown). Sem internet, vale a última resposta que o app guardou.

Depois de publicar uma versão nova na Uptodown:

1. Veja o **versionCode** dela: está no título da release no GitHub ("versionCode 1xx") e no app em
   Perfil > Sobre, entre parênteses.
2. No GitHub, abra `update.json`, clique no lápis e troque `minVersionCode` por esse número.
3. Se quiser, escreva as novidades em `notes` (pt, en, es). Salve com "Commit changes".

Em até ~5 minutos quem tiver uma versão menor fica travado até atualizar. Use `0` para não travar ninguém.
Mudar só o `update.json` não gera APK novo.

## Tecnologia

- Kotlin + Jetpack Compose (Material 3), Android 8.0 ou mais novo
- IA da câmera: MediaPipe Pose Landmarker do Google (modelo "heavy", o mais preciso, para flexão, prancha,
  abdominal, agachamento, afundo e agachamento isométrico; "full" para os rápidos). Ele também estima a
  profundidade, então os ângulos do corpo são medidos em 3D. Os modelos (Apache 2.0) são baixados pelo
  Gradle na compilação. Roda no aparelho e nenhuma imagem sai do celular
- Flexão: classificador de poses do exemplo oficial do Google ML Kit (k-vizinhos com centenas de fotos
  reais de flexão), adaptado para Kotlin. Licença Apache 2.0 em `licenses/mlkit-samples-LICENSE.txt`
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
