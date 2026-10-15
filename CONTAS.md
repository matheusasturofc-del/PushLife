# Contas do PushLife (Supabase)

As contas usam o **Supabase**, que é grátis e não pede cartão. Elas incluem e-mail e senha, um código de
6 dígitos no e-mail para criar a conta e para entrar, um @ que não se repete e o backup na nuvem.

O código do app já está pronto. Enquanto o arquivo `supabase.properties` não existir na raiz do
repositório, o app funciona normalmente, só que sem a parte de contas.

## Passo a passo (uns 15 minutos, pelo computador)

### 1. Criar o projeto
1. Abra https://supabase.com e clique em **Start your project**. Dá para entrar com a conta do GitHub.
2. **New project**:
   - **Name:** `PushLife`
   - **Database Password:** clique em *Generate* e guarde a senha
   - **Region:** *South America (São Paulo)*
3. Clique em **Create new project** e espere uns 2 minutos.

### 2. Criar as tabelas
1. No menu da esquerda, abra **SQL Editor** e clique em **New query**.
2. Cole todo o conteúdo do arquivo [`supabase/schema.sql`](supabase/schema.sql).
3. Clique em **Run**. Deve aparecer "Success".

### 3. Ligar o código por e-mail
1. **Authentication** → **Sign In / Providers** → **Email**.
2. Deixe **Enable Email provider** e **Confirm email** ligados.
3. Em **Email OTP Length**, coloque `6`.
4. Clique em **Save**.

### 4. Senha de app do Gmail (o e-mail que manda os códigos)
1. Entre em https://myaccount.google.com/security e ative a **Verificação em duas etapas**, se ainda não
   tiver. É grátis.
2. Abra https://myaccount.google.com/apppasswords, digite `PushLife` e clique em **Criar**.
3. Copie a senha de 16 letras que aparece. Ela só aparece uma vez.

### 5. Fazer o Supabase mandar os e-mails pelo seu Gmail
Sem isso o Supabase só manda e-mail para você mesmo.
1. Abra **Authentication** → **Emails** → aba **SMTP Settings** e ligue **Enable Custom SMTP**.
2. Preencha:
   - **Sender email:** o seu Gmail
   - **Sender name:** `PushLife`
   - **Host:** `smtp.gmail.com`
   - **Port:** `465`
   - **Username:** o seu Gmail
   - **Password:** a senha de 16 letras do passo 4
3. Clique em **Save**.

### 6. E-mails com o código
Em **Authentication** → **Emails** → aba **Templates**, troque estes três modelos. Para cada um, cole o
assunto e cole o arquivo inteiro no campo do corpo (*Message body*), depois clique em **Save**:

| Modelo | Assunto | Corpo |
|---|---|---|
| **Confirm signup** | `Seu código do PushLife: {{ .Token }}` | [`supabase/email-confirmar-cadastro.html`](supabase/email-confirmar-cadastro.html) |
| **Magic Link** | `Seu código do PushLife: {{ .Token }}` | [`supabase/email-entrar.html`](supabase/email-entrar.html) |
| **Reset Password** | `Código para sua senha nova: {{ .Token }}` | [`supabase/email-nova-senha.html`](supabase/email-nova-senha.html) |

### 7. Ligar no app
1. Abra **Project Settings** (a engrenagem) → **API Keys** (ou **API**).
2. Copie o **Project URL**. Ele fica em **Project Settings → Data API**, ou no topo da página.
3. Copie a chave **anon / public** (ou **publishable**). Essa chave é pública, feita para ir dentro do
   app. **Nunca** copie a *service_role* nem a *secret*.
4. Mande as duas para o Claude, ou crie você mesmo o arquivo `supabase.properties` na raiz do repositório:
   ```
   url=https://SEU-PROJETO.supabase.co
   key=SUA-CHAVE-ANON
   ```
5. O GitHub gera um APK novo em ~12 minutos, já com as contas funcionando.

## Entrar com Google, Discord e Twitch

Os botões já estão no app. Cada provedor precisa ser ligado no Supabase uma vez. Em todos, o
**endereço de retorno** (callback) é:

```
https://xklucpmiodcxwsulfaei.supabase.co/auth/v1/callback
```

### A. Antes de tudo (uma vez)
1. **Supabase → Authentication → URL Configuration → Redirect URLs → Add URL:** `pushlife://auth-callback`.
   É assim que o navegador devolve a pessoa para o app.
2. **Supabase → SQL Editor:** rode de novo o `supabase/schema.sql` inteiro. A versão nova deixa o app
   criar o perfil (o @) de quem entra pelo Google, Discord ou Twitch.

### B. Google (console.cloud.google.com)
1. Abra https://console.cloud.google.com, crie um projeto `PushLife` e selecione-o.
2. **APIs e serviços → Tela de consentimento OAuth** (ou "Google Auth Platform → Branding"):
   - Tipo **Externo**.
   - Nome do app: `PushLife`.
   - E-mail de suporte: o seu.
   - Salve.
3. **Público-alvo:** clique em **Publicar app**, para qualquer pessoa conseguir entrar, e não só quem
   você adicionar como testador.
4. **Credenciais (ou "Clientes") → Criar credenciais → ID do cliente OAuth:**
   - Tipo **Aplicativo da Web**.
   - Em **URIs de redirecionamento autorizados**, cole o endereço de retorno acima.
   - Clique em **Criar**.
5. Copie o **ID do cliente** e a **Chave secreta do cliente**.
6. **Supabase → Authentication → Sign In / Providers → Google:**
   - Ligue a chave.
   - Cole o **Client ID** e o **Client Secret**.
   - Clique em **Save**.

### C. Discord (discord.com/developers)
1. Abra https://discord.com/developers/applications e clique em **New Application** → nome `PushLife`.
2. **OAuth2:**
   - Em **Redirects**, clique em **Add Redirect**, cole o endereço de retorno e salve.
   - Copie o **Client ID**.
   - Clique em **Reset Secret** e copie o **Client Secret**.
3. **Supabase → Sign In / Providers → Discord:** ligue a chave, cole o Client ID e o Client Secret e salve.

### D. Twitch (dev.twitch.tv)
1. A Twitch exige a **verificação em duas etapas** ligada na sua conta Twitch.
2. Abra https://dev.twitch.tv/console/apps e clique em **Register Your Application**:
   - **Nome:** `PushLife` (se já existir, use `PushLife App`).
   - **OAuth Redirect URLs:** o endereço de retorno.
   - **Categoria:** Application Integration.
   - **Client Type:** Confidential.
3. Abra o app criado, copie o **Client ID** e clique em **New Secret** para pegar o **Client Secret**.
4. **Supabase → Sign In / Providers → Twitch:** ligue a chave, cole o Client ID e o Client Secret e salve.

Na primeira vez que alguém entra por um desses, o app pede o nome e o **@**, que continua único.

## Convidado

Na primeira tela há o botão **Entrar como convidado**:
- O nome vira `guest` + números.
- Não dá para trocar o nome nem a foto (a cor do avatar).
- Não tem backup, nem em arquivo nem na nuvem.

Para salvar o progresso, o convidado cria uma conta em **Perfil → Conta**. A partir daí tudo fica
liberado.

## Como funciona

- **Boas-vindas:** depois do nome vem o **@** (3 a 20 letras minúsculas, números, `_` ou `.`). O app
  confere enquanto você digita e só deixa continuar se o @ estiver livre. Na tela seguinte a pessoa
  digita e-mail e senha e recebe um **código de 6 dígitos**. Com o código a conta é criada.
- **O @ nunca se repete:** o banco tem uma regra de "único" e cria o perfil junto com a conta. Se duas
  pessoas pegarem o mesmo @ ao mesmo tempo, só uma consegue.
- **Já tem uma conta?:** fica na primeira tela, embaixo do botão. A pessoa digita e-mail e senha, recebe
  o código no e-mail e entra. Todo o progresso salvo volta e ela vai direto para o app.
- **Esqueci minha senha:** a pessoa recebe um código no e-mail, escolhe a senha nova e já entra.
- **Backup:** com conta, o progresso vai para a nuvem sozinho sempre que o app sai da tela (no máximo a
  cada 5 minutos).
- **Perfil → Conta:** quem já usava o app antes pode criar a conta ou entrar por ali. Também dá para sair.
- **Apagar todos os dados:** sai da conta antes, para não apagar o backup da nuvem.
- **Apagar a conta (Perfil → Conta → Apagar conta):** chega um código no e-mail da conta. Com o código
  certo o app apaga para sempre a conta, o @, o backup na nuvem e os dados do celular, e volta para as
  boas-vindas. Usa a função `delete_my_account`, que está no fim do `supabase/schema.sql`.
- **Apagar a conta de alguém pelo painel:** no Supabase, abra **Authentication** → **Users** → apague a
  pessoa. O perfil e o backup somem juntos.
- **Projeto pausado:** no plano grátis, se ninguém usar o app por 1 semana, o Supabase pausa o projeto.
  Para voltar, é só entrar no site e clicar em **Restore**.
