# Publicar o PushLife na Play Store

Guia passo a passo. O que é marcado com **você** só pode ser feito na sua conta.

## 1. Conta de desenvolvedor (você)

1. Acesse https://play.google.com/console e crie a conta de desenvolvedor (US$ 25, uma vez só).
2. Contas pessoais novas precisam de um **teste fechado com pelo menos 12 testadores por 14 dias** antes de
   publicar para todo mundo. Chame amigos e família com celular Android.

## 2. Chave de publicação (você, uma vez)

No computador com o Android Studio instalado (o `keytool` vem junto), rode num terminal:

```
keytool -genkeypair -v -keystore pushlife-upload.jks -alias pushlife -keyalg RSA -keysize 2048 -validity 10000
```

Guarde o arquivo `pushlife-upload.jks` e as senhas num lugar seguro. **Nunca** coloque esse arquivo no GitHub.

Depois, no GitHub: repositório → Settings → Secrets and variables → Actions → New repository secret, e crie:

| Segredo | Valor |
|---|---|
| `PUSHLIFE_KEYSTORE_BASE64` | o arquivo .jks em base64 (no Windows: `certutil -encode pushlife-upload.jks saida.txt` e cole o conteúdo sem as linhas BEGIN/END) |
| `PUSHLIFE_KEYSTORE_PASSWORD` | senha do arquivo |
| `PUSHLIFE_KEY_ALIAS` | `pushlife` |
| `PUSHLIFE_KEY_PASSWORD` | senha da chave |

A partir daí, cada push gera também o **PushLife-playstore.aab** (aba Actions → a execução → Artifacts).
É esse arquivo que vai para a Play Console. Use a "Assinatura de apps do Google Play" (recomendado pelo Google).

## 3. Ficha da loja

- Textos prontos: `listing-pt.md`, `listing-en.md`, `listing-es.md`.
- Política de privacidade: use o link do arquivo `PRIVACY.md` deste repositório.
- Imagens: ícone 512×512, imagem de destaque 1024×500 e pelo menos 2 capturas de tela do celular.

## 4. Declarações que a Play Console vai pedir

| Item | Resposta |
|---|---|
| Segurança dos dados | Nenhum dado coletado nem compartilhado. Tudo fica no aparelho. Câmera processada no aparelho. |
| Serviço em primeiro plano (uso especial) | Monitora qual app está em primeiro plano para bloquear os apps escolhidos pelo usuário quando o tempo ganho com exercício acaba. Grave um vídeo curto mostrando o bloqueio. |
| VPN (VpnService) | Filtro de conteúdo adulto por DNS, ligado pelo usuário. Só as consultas de DNS passam pela VPN. |
| Acesso aos dados de uso | Necessário para saber o app em uso e mostrar o tempo de uso de cada app. |
| Classificação de conteúdo | Responda o questionário (app de saúde e fitness, sem conteúdo adulto). |
| Público-alvo | 13 anos ou mais. |

## 5. Login e backup automático na nuvem

Feito com o Supabase (grátis, sem cartão). O passo a passo está em [CONTAS.md](../CONTAS.md).
No formulário "Segurança dos dados" da Play Store, informe: e-mail, nome de usuário e dados do app
(progresso) são coletados para a conta e o backup, criptografados em trânsito, e a pessoa pode pedir para
apagar a conta.
