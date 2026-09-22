# Deploy do dora na AWS (infra de baixo custo, EC2 única)

Este guia cobre o deploy do dora em uma única instância EC2, rodando o app,
Postgres+pgvector e Redis via Docker Compose, com HTTPS automático via Caddy.
É a opção mais barata: sem RDS, ElastiCache ou ALB. Em produção o chat usa a
API da OpenAI (`gpt-4o-mini`) em vez de um LLM local — não é preciso rodar
Ollama na instância (isso só é usado em desenvolvimento local).

Custo aproximado (região `us-east-1`, sob demanda): uma `t4g.medium` (2 vCPU /
4 GiB) fica em torno de US$ 24/mês, mais ~US$ 5/mês para os 60 GiB de EBS
(gp3, root + data) e centavos para o Elastic IP enquanto associado à
instância em execução. Considere uma Reserved/Savings Plan ou Spot depois de
validar a carga.

## Pré-requisitos

- Conta AWS com permissão para criar EC2, IAM roles, EIP e Security Groups
- [AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html) configurado (`aws configure`)
- [Session Manager plugin do AWS CLI](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) instalado (necessário para `aws ssm start-session`)
- [Terraform](https://developer.hashicorp.com/terraform/install) >= 1.5
- Um domínio (ou subdomínio) que você controla, para apontar ao Elastic IP e emitir o certificado HTTPS — **opcional**: se não tiver um, veja "Não quero um domínio próprio" no passo 2

## 1. Provisionar a infraestrutura com Terraform

```bash
cd infra/terraform
terraform init
terraform plan
terraform apply
```

Isso cria:
- 1 instância EC2 `t4g.medium` (Amazon Linux 2023, ARM/Graviton)
- Security Group com apenas as portas 80 e 443 abertas (sem porta 22 — acesso administrativo via SSM)
- IAM role com a policy `AmazonSSMManagedInstanceCore` (acesso via Session Manager)
- Volume EBS extra (40 GiB) para dados do Postgres/Redis
- Elastic IP associado à instância

Ao final, anote os outputs:

```bash
terraform output
# instance_id, public_ip, public_dns, sslip_domain, ssm_connect_command, security_group_id
```

Para customizar (tipo de instância, região, tamanho de disco), copie
`terraform.tfvars.example` para `terraform.tfvars` e ajuste antes do `apply`.

## 2. Apontar o DNS

Crie um registro `A` no seu provedor de DNS apontando o domínio/subdomínio
escolhido (ex.: `dora.example.com`) para o `public_ip` retornado pelo
Terraform. Aguarde a propagação antes do passo 4 (o Caddy precisa resolver o
domínio para emitir o certificado Let's Encrypt).

### Não quero um domínio próprio — pode ser uma URL gerada?

Sim. O Caddy só precisa de um **hostname público que resolva para o IP da
instância** — não precisa ser um domínio que você registrou. O ACME
(Let's Encrypt) valida apenas que você controla o servidor respondendo
naquele host, não a "posse" do domínio. Duas opções prontas, sem nenhum
passo de DNS manual, usando outputs do próprio Terraform:

```bash
terraform output sslip_domain
# ex: 52-91-12-34.sslip.io  (serviço público gratuito, resolve pro IP embutido no nome)

terraform output public_dns
# ex: ec2-52-91-12-34.compute-1.amazonaws.com  (hostname que a própria AWS já atribui ao IP)
```

Use qualquer um dos dois como `DOMAIN` no `.env` (passo 4) — o restante do
guia não muda. Pule este passo 2 (não precisa criar registro `A` em lugar
nenhum) e vá direto para o passo 3.

## 3. Conectar na instância via SSM Session Manager

Não há chave SSH nem porta 22 aberta — a conexão é feita via SSM:

```bash
aws ssm start-session --target <instance_id> --region <aws_region>
```

(o comando exato está no output `ssm_connect_command` do Terraform)

## 4. Clonar o repositório e configurar os secrets

Dentro da sessão SSM (já como `ec2-user` após `sudo su - ec2-user` ou usando
`sudo -u ec2-user -i`):

```bash
cd /opt/dora
git clone <url-do-repositorio-dora> .
cp .env.example .env
nano .env   # preencha DOMAIN, ACME_EMAIL, POSTGRES_PASSWORD, WHATSAPP_*, OPENAI_API_KEY
```

Variáveis obrigatórias em `.env`:
- `DOMAIN` — o domínio apontado no passo 2 (ex.: `dora.example.com`)
- `ACME_EMAIL` — e-mail usado pelo Caddy no registro do Let's Encrypt
- `POSTGRES_PASSWORD` — senha forte para o banco
- `OPENAI_API_KEY` — usada como modelo de chat em produção (`gpt-4o-mini`); gere em
  [platform.openai.com](https://platform.openai.com/api-keys)

Variáveis opcionais (deixe em branco para desabilitar a feature):
- `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_VERIFY_TOKEN`, `WHATSAPP_APP_SECRET`

## 5. Build e subida do stack

```bash
sudo usermod -aG docker ec2-user   # se ainda não estiver no grupo docker (relogar depois)
newgrp docker

./mvnw package -DskipTests
docker compose -p dora up -d --build
```

O `-p dora` fixa o nome do projeto Compose — importante para que o deploy
automático via CI (passo 8) reutilize os mesmos containers/volumes, mesmo
rodando a partir de um diretório de checkout diferente.

Acompanhe os logs até o app subir (a ingestão de documentos/scraping no
startup pode levar 1-2 minutos):

```bash
docker compose -p dora logs -f dora
```

## 6. Validar

```bash
curl -I https://dora.example.com/
```

Deve responder `200 OK` com certificado válido (emitido automaticamente pelo
Caddy). Teste também a interface web abrindo o domínio no navegador.

## 7. Configurar o webhook do WhatsApp (opcional)

Se for usar a integração com WhatsApp, configure no painel do Meta for
Developers o webhook apontando para:

```
https://dora.example.com/webhook/whatsapp
```

Usando o `WHATSAPP_VERIFY_TOKEN` definido no `.env`.

## 8. CI/CD com GitHub Actions (opcional)

Depois do primeiro deploy manual (passos 1-6 acima), você pode automatizar
deploys seguintes: **push na branch `main` -> rebuild e restart automático do
container `dora` na EC2**.

A abordagem usada é um **self-hosted runner do GitHub Actions rodando na
própria instância EC2** — o workflow executa localmente na máquina, com as
mesmas permissões do deploy manual. Não é preciso guardar credenciais AWS
como secret no GitHub.

### 8.1 Registrar o runner na instância (passo único)

1. No GitHub: **Settings** do repositório -> **Actions** -> **Runners** ->
   **New self-hosted runner** -> escolha Linux/ARM64 -> copie o token de
   registro (ele expira em ~1h, use logo).
2. Conecte na instância via SSM (passo 3) e, como `ec2-user`:

```bash
mkdir -p /opt/actions-runner && cd /opt/actions-runner
curl -o actions-runner.tar.gz -L <URL do runner Linux ARM64, copiada no passo 1>
tar xzf actions-runner.tar.gz
./config.sh --url https://github.com/<org>/<repo> --token <TOKEN> --labels dora-prod --unattended
sudo ./svc.sh install
sudo ./svc.sh start
```

O `--labels dora-prod` precisa bater com o `runs-on` do workflow (veja 8.2).
Instalar como serviço (`svc.sh install`) garante que o runner sobreviva a
reboots da instância.

### 8.2 Workflow

Já existe em [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml):
dispara em todo push na `main`, faz `./mvnw package -DskipTests` e
`docker compose -p dora --env-file /opt/dora/.env up -d --build dora`,
reutilizando o `.env` já configurado manualmente em `/opt/dora/.env` (passo
4) — o `.env` nunca entra no repositório nem em secrets do GitHub. O caminho
fixo é necessário porque o `actions/checkout` limpa arquivos não versionados
no diretório de trabalho do runner a cada execução (o `.env`, sendo
`.gitignore`d, seria apagado se estivesse dentro do checkout). O `-p dora`
garante que o CI atualiza os mesmos containers/volumes do deploy manual,
mesmo rodando de um diretório diferente (o runner faz checkout em seu
próprio `_work/`, não em `/opt/dora`).

### 8.3 Nota de segurança

Um self-hosted runner executa o código de qualquer push na branch que ele
observa, com as credenciais da própria máquina. Mantenha *branch protection*
na `main` (exigindo PR revisado antes do merge) para não expor a instância a
código não confiável de pushes diretos.

## Operação do dia a dia

| Ação | Comando |
|------|---------|
| Ver logs | `docker compose -p dora logs -f [servico]` |
| Reiniciar um serviço | `docker compose -p dora restart dora` |
| Atualizar o app (deploy manual, sem esperar o CI) | `git pull && ./mvnw package -DskipTests && docker compose -p dora up -d --build dora` |
| Parar tudo | `docker compose -p dora down` |
| Destruir a infra AWS | `cd infra/terraform && terraform destroy` |

## Exportar a tabela `message` para CSV

A tabela `message` (histórico de conversas) fica no container `postgres`,
sem porta publicada no host — só é acessível pela rede interna do Compose.
Não há SSH/scp na instância (só SSM), então o jeito mais direto de tirar um
`.csv` já no seu computador é abrir um túnel via SSM até o container e
rodar o `psql` localmente.

```bash
# 1. Dentro da sessão SSM (aws ssm start-session --target <instance_id> ...),
#    descobrir o IP do container postgres:
cd /opt/dora
docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' \
  $(docker compose -p dora ps -q postgres)
# ex: 172.20.0.3
```

```bash
# 2. No seu computador (outro terminal), abrir o túnel TCP até o container:
aws ssm start-session \
  --target <instance_id> --region <aws_region> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<ip_do_container>"],"portNumber":["5432"],"localPortNumber":["5432"]}'
```

```bash
# 3. Ainda no seu computador, com psql instalado localmente (brew install libpq
#    ou postgresql), exportar via localhost:5432. A senha é o POSTGRES_PASSWORD
#    do /opt/dora/.env na instância:
PGPASSWORD='<POSTGRES_PASSWORD>' psql -h localhost -p 5432 -U dora -d dora \
  -c "\copy (SELECT * FROM message ORDER BY chat_id, sequence) TO 'message.csv' WITH CSV HEADER"
```

O arquivo `message.csv` é gravado diretamente na máquina local, sem precisar
copiar/colar saída de terminal.

Alternativa rápida (sem túnel, só pra espiar poucas linhas): dentro da
sessão SSM, `docker compose -p dora exec -T postgres psql -U dora -d dora -c
"\copy (SELECT * FROM message ORDER BY chat_id, sequence) TO STDOUT WITH CSV
HEADER" > /tmp/message.csv` e depois `cat /tmp/message.csv` para copiar a
saída manualmente — só vale para volumes pequenos de dados.

## Ferramenta de consulta processual TJRS (Playwright)

A tool `DoraTools#lookupTjrsProcess` (ver `TjrsProcessScraper`) usa
[Playwright](https://playwright.dev/java/) para abrir um Chromium headless real,
porque a consulta processual do TJRS (`consulta.tjrs.jus.br/consulta-processual`)
é uma SPA Angular sem API pública documentada — um `HttpClient` simples (como o
usado pelo `WebScraper` do RAG) não é suficiente.

O `Dockerfile.jvm` usa a imagem oficial
`mcr.microsoft.com/playwright/java:v1.62.0-noble`, que já inclui Chromium e as
dependências de SO, e instala o Temurin JDK 25 por cima (o app continua
compilado com `maven.compiler.release=25`). O Chromium entra na imagem no
`docker compose ... --build`; **não** é preciso instalar Playwright no host
da EC2.

Pontos de operação em produção:

- **Arquitetura**: a EC2 é ARM (`t4g.medium` / Graviton). A tag precisa ter
  `linux/arm64`. Se o pull falhar por falta dessa plataforma, o build na instância
  não sobe.
- **Memória/CPU**: um Chromium headless consome bem mais RAM que o resto do app;
  o Compose reserva `shm_size: 1gb` para o `/dev/shm` do Chromium. Revalidar se a
  `t4g.medium` (4 GiB) comporta chats concorrentes com navegações simultâneas.
- **Rede de saída**: liberar egress para `consulta.tjrs.jus.br` (e `tjrs.jus.br`)
  no Security Group/NACLs.
- **Configuração**: propriedades `tjrs.scrape.*` em `application.properties`
  (URL base, timeout, headless, rótulos de campos do formulário, marcadores de
  "não encontrado") — ajustar sem redeploy de código se o TJRS mudar o layout.

## Fora do escopo deste guia (próximos passos sugeridos)

- **Backups**: snapshots automáticos do volume EBS de dados (`aws_ebs_volume.data`) ou `pg_dump` agendado
- **Alta disponibilidade**: essa arquitetura é uma única instância — sem redundância. Para HA seria necessário migrar para RDS + ElastiCache + múltiplas instâncias/ECS, com custo bem maior
