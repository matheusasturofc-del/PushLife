# Contas do PushLife (Firebase)

As contas (e-mail e senha, @ único e backup na nuvem) usam o **Firebase**, do Google. O plano grátis
(Spark) aguenta milhares de usuários por dia sem custo.

O código já está pronto. Enquanto o arquivo `app/google-services.json` não existir, o app funciona
normalmente, só que sem a parte de contas: não aparece o campo do @, nem "Já tem uma conta?".

## Passo a passo (uns 10 minutos, pelo computador)

### 1. Criar o projeto
1. Abra https://console.firebase.google.com e entre com sua conta Google.
2. **Criar um projeto** → nome `PushLife` → pode desativar o Google Analytics → **Criar projeto**.

### 2. Registrar o app Android
1. Na página do projeto, clique no ícone do **Android** ("Adicionar app").
2. Nome do pacote: `com.pushlife.app` (exatamente assim). Apelido: `PushLife`. Clique em **Registrar app**.
3. Clique em **Baixar google-services.json**. Os outros passos dessa tela podem ser pulados (o código já
   está pronto): é só ir em **Avançar** até **Continuar no console**.

### 3. Ligar o login com e-mail
1. Menu da esquerda: **Criação** (ou *Build*) → **Authentication** → **Vamos começar**.
2. Aba **Método de login** → **E-mail/senha** → ative a **primeira** chave → **Salvar**.
3. (Opcional) Aba **Modelos** → mude o idioma para português, para o e-mail de "esqueci minha senha".

### 4. Criar o banco (onde ficam o @ e o backup)
1. Menu da esquerda: **Criação** → **Firestore Database** → **Criar banco de dados**.
2. Local: `southamerica-east1 (São Paulo)` → **modo de produção** → **Criar**.
3. Aba **Regras**: apague tudo, cole o conteúdo do arquivo [`firestore.rules`](firestore.rules) deste
   repositório e clique em **Publicar**. São essas regras que impedem dois usuários de terem o mesmo @.

### 5. Colocar o arquivo no GitHub
1. No GitHub, abra o repositório, entre na pasta **app**.
2. **Add file** → **Upload files** → arraste o `google-services.json` → **Commit changes**.
3. O GitHub gera um APK novo em ~12 minutos, já com as contas funcionando.

Esse arquivo pode ficar no repositório: ele só identifica o projeto. Quem protege os dados são as regras
do passo 4.

## Código por e-mail (para criar conta e para entrar)

Quem manda o código de 6 dígitos é um pequeno servidor (pasta `functions`), publicado pelo GitHub sozinho.
Para isso são precisos mais 3 coisas, uma vez só:

### 6. Plano Blaze no Firebase
1. No Firebase, embaixo à esquerda, clique em **Spark** → **Fazer upgrade** → **Blaze** e cadastre o cartão.
2. Para ficar tranquilo, crie um **alerta de orçamento** de R$ 5 quando ele perguntar. O uso do PushLife
   fica dentro da cota grátis (milhões de chamadas por mês), então a conta deve ficar em R$ 0.

### 7. Senha de app do Gmail (o e-mail que manda os códigos)
1. Entre em https://myaccount.google.com/security e ative a **Verificação em duas etapas** (se ainda não tiver).
2. Abra https://myaccount.google.com/apppasswords, digite `PushLife` e clique em **Criar**.
3. Copie a senha de 16 letras que aparece (ela só aparece uma vez).

### 8. Chave para o GitHub publicar o servidor
1. Abra https://console.cloud.google.com/iam-admin/serviceaccounts?project=pushlife
2. **+ Criar conta de serviço** → nome `github-deploy` → **Criar e continuar**.
3. Em **Papel**, escolha **Básico → Proprietário** → **Continuar** → **Concluir**.
4. Clique na conta criada → aba **Chaves** → **Adicionar chave** → **Criar nova chave** → **JSON** → **Criar**.
   Um arquivo `.json` é baixado.
5. No GitHub: repositório → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**.
   Crie estes 3 segredos:
   - `FIREBASE_SERVICE_ACCOUNT`: abra o arquivo `.json` no Bloco de Notas, copie **tudo** e cole.
   - `GMAIL_USER`: o Gmail que vai mandar os códigos (ex.: `seunome@gmail.com`).
   - `GMAIL_APP_PASSWORD`: a senha de 16 letras do passo 7.
6. No GitHub: aba **Actions** → **Publicar servidor (códigos por e-mail)** → **Run workflow**.
   Leva uns 5 minutos. Depois disso o app já manda os códigos.

Os segredos ficam guardados no GitHub, ninguém mais vê. Apague o arquivo `.json` do computador depois.

## Como funciona

- **Boas-vindas:** depois do nome vem o **@** (3 a 20 letras minúsculas, números, `_` ou `.`). O app
  confere no servidor enquanto você digita e só deixa continuar se ninguém estiver usando. Na tela seguinte
  a pessoa digita e-mail e senha, recebe um **código de 6 dígitos** no e-mail e, com ele, a conta é criada.
- **Já tem uma conta?** (primeira tela, embaixo do botão): e-mail e senha → código no e-mail → entra e traz
  todo o progresso salvo, indo direto para o app.
- **Código:** vale 10 minutos, dá para reenviar depois de 1 minuto (até 5 por hora) e aceita 5 tentativas.
- **Backup:** com conta, o progresso vai para a nuvem sozinho sempre que o app sai da tela (no máximo a
  cada 5 minutos).
- **Perfil → Conta:** quem já usava o app antes pode criar a conta ou entrar por ali; também dá para sair.
- **Apagar todos os dados** sai da conta antes, para não apagar o backup da nuvem.
- **Apagar uma conta** (se alguém pedir): no Firebase, apague o usuário em *Authentication* e os documentos
  `users/{uid}` e `usernames/{@}` no *Firestore*.
