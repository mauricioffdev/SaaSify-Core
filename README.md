# SaaSify Core

SaaSify Core e uma API backend de alta performance desenvolvida para gerenciar o ciclo de vida de licencas de software e assinaturas, projetada com foco em arquitetura limpa, concorrencia moderna e padroes comerciais rigorosos.

## Arquitetura e Tecnologias

O projeto utiliza o ecossistema Java moderno e as melhores praticas de desenvolvimento de APIs RESTful:

- **Java 27**: Utilizacao de Records para objetos de dominio imutaveis.
- **Spring Boot 4.1 / Spring Framework 7**: Framework principal para construcao dos microsservicos, com API stateless e sem sessao.
- **Spring Data JPA / Hibernate 7**: Camada de persistencia e mapeamento objeto-relacional.
- **PostgreSQL**: Banco de dados relacional para armazenamento seguro de tenants e licencas.
- **Jackson 3** (`tools.jackson`): Serializacao JSON.
- **Bean Validation (Jakarta)**: Validacao rigorosa de payloads de entrada.
- **Spring Security**: Autenticacao por API Key nas rotas administrativas e assinatura HMAC no webhook.
- **Virtual Threads (Project Loom)**: Concorrencia nativa de alta performance para operacoes de I/O.

## Estrutura do Projeto

```
src/main/java/mffdev/saasify_core/
├── domain/            # Records imutaveis, regras de negocio e excecoes de dominio
│   ├── License.java
│   ├── WebhookPayload.java
│   └── exception/     # BusinessException e falhas de regra de negocio
├── service/           # Regras de negocio e orquestracao (LicenseService)
├── controller/        # Endpoints REST, DTOs de entrada/saida e handler de erros
│   └── dto/
└── infrastructure/
    ├── persistence/   # Entidade JPA, repositorio e LicenseMapper
    └── security/      # SecurityConfig, filtros de API Key e de assinatura HMAC
```

A separacao segue o fluxo: `controller` nao conversa com o banco, `service` concentra as regras, `domain` guarda as invariantes e `infrastructure` cuida de JPA e seguranca.

## Funcionalidades Principais

- **Gerenciamento de Licencas**: Criacao automatizada de chaves de acesso unicas baseadas em UUID e tempo de validade, com o id gerado pelo proprio dominio.
- **Validacao de Licencas**: A rota de consulta verifica existencia, status e vigencia antes de responder, devolvendo `404`, `410` ou `403` conforme o caso.
- **Processamento de Webhooks**: Endpoint dedicado para recebimento de eventos de pagamento de assinaturas (`SUBSCRIPTION_PAID`), autenticado por assinatura HMAC-SHA256.
- **Seguranca da API**: Rotas administrativas protegidas por API Key, sessao stateless e rotas fora do escopo bloqueadas por padrao.
- **Validacao e Tratamento de Erros**: Handler global de excecoes com respostas padronizadas em JSON (`ErrorResponse`), com codigo de erro estavel por tipo de falha.

## Variaveis de Ambiente

| Variavel | Descricao | Padrao |
|---|---|---|
| `DB_PASSWORD` | Senha do PostgreSQL | `postgres` |
| `SAASIFY_API_KEYS` | Chaves de acesso as rotas administrativas, separadas por virgula | vazio |
| `SAASIFY_WEBHOOK_SECRET` | Segredo usado no calculo do HMAC-SHA256 do webhook | vazio |
| `SAASIFY_WEBHOOK_TOLERANCE` | Janela aceitavel de replay do webhook | `5m` |
| `SHOW_SQL` | Exibe o SQL gerado pelo Hibernate | `false` |

Sem `SAASIFY_API_KEYS` a API falha de forma fechada: toda rota em `/api/v1/**` responde `401`.
Sem `SAASIFY_WEBHOOK_SECRET` o webhook responde `503`.

## Como Executar o Projeto

### Pre-requisitos
- JDK 27 instalado.
- Servidor PostgreSQL rodando localmente.

### Passo a Passo

1. Clone o repositorio:
   ```bash
   git clone https://github.com/seu-usuario/saasify-core.git
   ```

2. Crie o banco de dados (as tabelas sao criadas pelo Hibernate em `ddl-auto: update`):
   ```bash
   psql -U postgres -c "CREATE DATABASE saasify_db;"
   ```

3. Configure as variaveis de ambiente:
   ```bash
   export DB_PASSWORD=sua_senha_do_postgres
   export SAASIFY_API_KEYS=sua-chave-de-admin
   export SAASIFY_WEBHOOK_SECRET=seu-segredo-do-webhook
   ```

4. Execute a aplicacao:
   ```bash
   ./mvnw spring-boot:run
   ```

## Endpoints da API

### 1. Criar Licenca (Manual)
- **Metodo:** POST
- **Rota:** `/api/v1/licenses`
- **Header:** `X-Api-Key: <chave configurada em SAASIFY_API_KEYS>`
- **Body:**
  ```json
  {
    "tenantId": "123e4567-e89b-12d3-a456-426614174000",
    "customerEmail": "cliente@exemplo.com",
    "validityDays": 30
  }
  ```
- **Resposta:** `201 Created` com a licenca gerada

```bash
curl -X POST http://localhost:8080/api/v1/licenses \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: $SAASIFY_API_KEYS" \
  -d '{
        "tenantId": "123e4567-e89b-12d3-a456-426614174000",
        "customerEmail": "cliente@exemplo.com",
        "validityDays": 30
      }'
```

### 2. Consultar Licenca
- **Metodo:** GET
- **Rota:** `/api/v1/licenses/{licenseKey}`
- **Header:** `X-Api-Key: <chave configurada em SAASIFY_API_KEYS>`
- **Respostas:** `200` (ativa), `403` (suspensa), `404` (inexistente), `410` (expirada)

```bash
curl http://localhost:8080/api/v1/licenses/SAAS-1A2B3C4D \
  -H "X-Api-Key: $SAASIFY_API_KEYS"
```

### 3. Processar Webhook de Pagamento
- **Metodo:** POST
- **Rota:** `/api/v1/webhooks/payments`
- **Headers:**
  - `X-Webhook-Timestamp`: instante do envio em ISO-8601 UTC
  - `X-Webhook-Signature`: `sha256=<hex>` do HMAC-SHA256
- **Body:**
  ```json
  {
    "eventType": "SUBSCRIPTION_PAID",
    "tenantId": "123e4567-e89b-12d3-a456-426614174000",
    "customerEmail": "cliente@exemplo.com",
    "validityDays": 30
  }
  ```
- **Respostas:** `200` (licenca gerada), `400` (evento nao suportado ou timestamp expirado), `401` (assinatura ausente ou invalida), `413` (corpo acima de 256 KB), `503` (segredo nao configurado)

A assinatura e o HMAC-SHA256 do texto `<timestamp>.<corpo cru>` usando `SAASIFY_WEBHOOK_SECRET`:

```bash
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)
BODY='{"eventType":"SUBSCRIPTION_PAID","tenantId":"123e4567-e89b-12d3-a456-426614174000","customerEmail":"cliente@exemplo.com","validityDays":30}'
SIGNATURE=$(printf '%s.%s' "$TIMESTAMP" "$BODY" \
  | openssl dgst -sha256 -hmac "$SAASIFY_WEBHOOK_SECRET" -hex | awk '{print $2}')

curl -X POST http://localhost:8080/api/v1/webhooks/payments \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Timestamp: $TIMESTAMP" \
  -H "X-Webhook-Signature: sha256=$SIGNATURE" \
  -d "$BODY"
```

## Formato dos Erros

Todas as respostas de erro seguem o mesmo formato:

```json
{
  "timestamp": "2026-10-02T14:31:22.481Z",
  "status": 400,
  "code": "validation_error",
  "message": "Um ou mais campos sao invalidos",
  "path": "/api/v1/licenses",
  "fieldErrors": {
    "validityDays": "A validade minima deve ser de pelo menos 1 dia"
  }
}
```

| `code` | HTTP | Significado |
|---|---|---|
| `validation_error` | 400 | Campo invalido no payload (`fieldErrors` detalha cada campo) |
| `malformed_request` | 400 | Corpo JSON invalido ou incompleto |
| `webhook_timestamp_out_of_range` | 400 | Timestamp do webhook fora da janela de replay |
| `unsupported_webhook_event` | 400 | Evento diferente de `SUBSCRIPTION_PAID` |
| `unauthorized` | 401 | API Key ausente ou invalida |
| `webhook_signature_missing` | 401 | Headers de assinatura ausentes |
| `webhook_signature_invalid` | 401 | Assinatura HMAC nao confere |
| `license_suspended` | 403 | Licenca suspensa |
| `license_not_found` | 404 | Chave inexistente |
| `resource_not_found` | 404 | Rota inexistente |
| `license_expired` | 410 | Licenca vencida |
| `invalid_license_data` | 422 | Regra de negocio violada na emissao |
| `webhook_payload_too_large` | 413 | Corpo do webhook acima de 256 KB |
| `internal_error` | 500 | Falha inesperada |
| `webhook_auth_not_configured` | 503 | `SAASIFY_WEBHOOK_SECRET` ausente |

## Testes

A suite roda com H2 em memoria, sem necessidade de PostgreSQL:

```bash
./mvnw test
```

Ha testes de dominio, de service, de controller (`@WebMvcTest`) e de integracao (`@SpringBootTest`) cobrindo autenticacao, assinatura do webhook, expiracao e suspensao.

## Monitoramento

O actuator expoe `health`, `info` e `metrics`. Apenas `health` e `info` sao publicos:

```bash
curl http://localhost:8080/actuator/health
```

Licenca

Este projeto e distribuido sob a licenca MIT.

Mauricio Filadelfo Filho
