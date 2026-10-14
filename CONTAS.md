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

## Como funciona

- **Boas-vindas:** depois do nome vem o **@** (3 a 20 letras minúsculas, números, `_` ou `.`). O app
  confere no servidor enquanto você digita e só deixa continuar se ninguém estiver usando. Na tela seguinte
  a pessoa cria a conta com e-mail e senha.
- **Já tem uma conta?** (primeira tela, embaixo do botão): entra com e-mail e senha e traz todo o progresso
  salvo, indo direto para o app.
- **Backup:** com conta, o progresso vai para a nuvem sozinho sempre que o app sai da tela (no máximo a
  cada 5 minutos).
- **Perfil → Conta:** quem já usava o app antes pode criar a conta ou entrar por ali; também dá para sair.
- **Apagar todos os dados** sai da conta antes, para não apagar o backup da nuvem.
- **Apagar uma conta** (se alguém pedir): no Firebase, apague o usuário em *Authentication* e os documentos
  `users/{uid}` e `usernames/{@}` no *Firestore*.
