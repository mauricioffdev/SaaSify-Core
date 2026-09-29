# SaaSify Core

SaaSify Core e uma API backend de alta performance desenvolvida para gerenciar o ciclo de vida de licencas de software e assinaturas, projetada com foco em arquitetura limpa, concorrencia moderna e padroes comerciais rigorosos.

## Arquitetura e Tecnologias

O projeto utiliza o ecossistema Java moderno e as melhores praticas de desenvolvimento de APIs RESTful:

- **Java 27**: Utilizacao de Records para objetos de dominio imutaveis.
- **Spring Boot 3+**: Framework principal para construcao dos microsservicos.
- **Spring Data JPA / Hibernate**: Camada de persistencia e mapeamento objeto-relacional.
- **PostgreSQL**: Banco de dados relacional para armazenamento seguro de tenants e licencas.
- **Bean Validation (Jakarta)**: Validacao rigorosa de payloads de entrada.
- **Virtual Threads (Project Loom)**: Concorrencia nativa de alta performance para operacoes de I/O.

## Funcionalidades Principais

- **Gerenciamento de Licencas**: Criacao automatizada de chaves de acesso unicas baseadas em UUID e tempo de validade.
- **Processamento de Webhooks**: Endpoint dedicado para recebimento de eventos de pagamento de assinaturas (`SUBSCRIPTION_PAID`).
- **Validacao e Tratamento de Erros**: Handler global de excecoes customizado para respostas padronizadas em formato JSON (HTTP 400, 404, etc.).
- **Consulta de Status**: Rotas para verificacao da autenticidade e vigencia de licencas ativas.

## Como Executar o Projeto

### Pre-requisitos
- JDK 27 instalado.
- Servidor PostgreSQL rodando localmente.

### Passo a Passo

1. Clone o repositorio:
   ```bash
   git clone [https://github.com/seu-usuario/saasify-core.git](https://github.com/seu-usuario/saasify-core.git)  

Configure a variavel de ambiente do banco de dados (opcional, caso utilize senha personalizada):

Bash
export DB_PASSWORD=sua_senha_do_postgres  

Execute a aplicacao atraves do Maven ou da sua IDE favorita:

Bash
./mvnw spring-boot:run  

## Endpoints da API

### 1. Criar Licenca (Manual)
- **Metodo:** POST
- **Rota:** `/api/v1/licenses`
- **Parametros (Query Params):** `tenantId`, `customerEmail`, `validityDays`

### 2. Consultar Licenca
- **Metodo:** GET
- **Rota:** `/api/v1/licenses/{licenseKey}`

### 3. Processar Webhook de Pagamento
- **Metodo:** POST
- **Rota:** `/api/v1/webhooks/payments`

## Payload Exemplo:

```json
{
  "eventType": "SUBSCRIPTION_PAID",
  "tenantId": "123e4567-e89b-12d3-a456-426614174000",
  "customerEmail": "cliente@exemplo.com",
  "validityDays": 30
}
````

Licenca   

Este projeto e distribuido sob a licenca MIT.  

Mauricio Filadelfo Filho

