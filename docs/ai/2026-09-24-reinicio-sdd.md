# Reinício do projeto com SDD

- **Data:** 24/09/2026
- **Autor:** Diego
- **Ferramenta:** agente de IA do Cursor

## Prompt 1 — Planejamento

> vamos planejar o sistema seguindo as instrução abaixo, iremos iniciar da maneira mais simples e depois vamos realizando melhorias caso necessário. Precisaremos criar 3 serviços, para isso vamos criar 3 componentes separados como se fosse um monolito modular, para que depois fique fácil extrair cada um para um serviço separado.
>
> (enunciado completo do desafio SPP, com as entidades sugeridas: Cliente(nome, cpf, Conta), Conta(numero, agencia, Client/Merchant, saldo), QRcode, Merchant(nome, cnpj, Conta))

### Resposta (resumo)

A IA propôs um único processo Micronaut com três pacotes (`coordinator`, `reserve`, `merchant`), cada um com seu schema no PostgreSQL e sem importar entidades dos outros. O coordinator falaria com os outros dois por interfaces, que depois viram HTTP ou Kafka. O QR Code entra só como dado da requisição. Primeira entrega: o fluxo feliz síncrono entre os módulos atrás de uma borda que responde 202; Kafka numa fatia seguinte.

## Prompt 2 — Recomeçar do zero com SDD

> estamos numa branch nova, pode excluir tudo da nova branch e recomeçar novamente nessa branch, descarte o que já foi feito, e ignore o tasks.md, vamos utilizar a abordagem do SDD, como se estivéssemos iniciando do zero

### Resposta (resumo)

- A branch `teste/sozinho` voltou ao esqueleto do commit inicial (`f93cd45`): projeto Micronaut, configuração e testes de container. Contratos, código, `tasks.md` e `diego.md` foram removidos da branch; continuam no histórico e nas branches `main` e `feature/diego`.
- Criados `specs/README.md` (fluxo do SDD), `specs/constitution.md` (sete princípios) e `specs/001-pagamento-piggies/spec.md` (histórias, cenários de aceite, requisitos, entidades e decisões a confirmar).
- Próxima etapa, depois da revisão da spec: `plan.md`, `data-model.md` e `contracts/`.

## Prompt 3 — Plano e contratos

> siga para a próxima etapa

### Resposta (resumo)

- Spec marcada como aprovada, com as cinco decisões.
- `plan.md`: arquitetura em três módulos (`facade`, `web`, `domain`, `infra`), regra de dependência verificada por ArchUnit, fluxo com reserva e validação em paralelo, débito síncrono e crédito por Kafka. Treze decisões com alternativas, tabela de falhas, estratégia de testes por cenário e cinco fatias de entrega (S0 a S4).
- `data-model.md`: tabelas dos schemas `spp_coordinator`, `spp_reserve` e `spp_merchant`, máquina de estados da intenção, operações da reserva e dados iniciais.
- `contracts/`: `coordinator.openapi.yaml`, `reserve.openapi.yaml`, `merchant.openapi.yaml`, `events/payment-debited.v1.schema.json` e `events/payment-confirmed.v1.schema.json`.
- Validação: os três OpenAPI passaram no Redocly CLI (`redocly/cli lint`, via Docker). A primeira rodada acusou `nullable` sem `type` em `failureReason` e ausência de `security`, corrigidos com enum inline e `security: []` (autenticação fora do escopo). Os exemplos dos eventos passaram no Ajv (draft 2020-12), que rejeitou valor fracionário e campo extra.

## Prompt 4 — Plano B da D11 e tarefas

> vamos seguir com o plano 11 para ganharmos tempo no desenvolvimento depois podemos olhar essa questão da openapi

### Resposta (resumo)

- D11 passou a ser: DTOs e controllers escritos à mão espelhando os contratos; geração de código a partir do OpenAPI e teste de conformidade ficam para depois da S4. Plano marcado como aprovado.
- Criado `specs/001-pagamento-piggies/tasks.md` com as tarefas T001–T053 divididas nas fatias S0 a S4, cada uma ligada às histórias da spec, com testes antes da implementação e checkpoints por fatia.

## Prompt 5 — Implementar a S0

> sim

### Resposta (resumo)

Tarefas T001–T009 implementadas e `./mvnw clean test` verde, com 13 testes.

- `pom.xml`: ArchUnit 1.5.0 e json-schema-validator 3.0.7 (teste); Surefire passa a incluir `*IT.java`.
- Entidades JPA com schema próprio por módulo e constraints `CHECK` via `@Table(check = @CheckConstraint(...))` do JPA 3.2 (Hibernate 7.4):
  - `reserve/infra`: `Client`, `Account`, `Reservation`;
  - `merchant/infra`: `Merchant`, `MerchantAccount`, `Receivable`;
  - `coordinator/domain`: `PaymentIntent`, `Stage`, `FailureReason`.
- Seeds idempotentes na inicialização (`ReserveSeed`, `MerchantSeed`), desligáveis por `spp.seed.enabled=false`.
- `ModuleBoundariesTest` (ArchUnit) com cinco regras. Conferido que pega violação: uma classe temporária em `reserve` importando `merchant` fez o teste falhar.
- `FoundationIT`: schemas criados, seeds presentes e sem duplicar, e o banco recusa saldo negativo e reservado maior que o saldo.

Ajustes durante a implementação:

- `PaymentIntentRepository` ficou em `coordinator.domain`, e não em `infra`, para o domínio não depender de `infra`. Plano e tarefas atualizados.
- O Surefire não rodava classes `*IT`; incluídas na configuração.
- O `DataSource` do Micronaut exige transação ativa; o `FoundationIT` usa conexão JDBC direta com o container para consultas de catálogo.
- `logback-test.xml` silencia os avisos inofensivos de `drop schema` do `create-drop`.

## Prompt 6 — Implementar a S1

> sim

### Resposta (resumo)

Tarefas T010–T023 implementadas.

- `reserve` e `merchant`: fachadas públicas (`ReserveFacade`, `MerchantFacade`) com records de entrada e saída. O resultado de negócio da reserva é um tipo selado (`Reserved` | `Rejected`).
- A reserva usa um `UPDATE` condicional (`balance - reserved_balance >= amount`), que evita corrida sem lock explícito. Confirmar e liberar usam `PESSIMISTIC_WRITE`.
- O crédito no merchant é idempotente por `paymentId`.
- `coordinator`:
  - portas `PaymentIntentStore`, `ReserveGateway` e `MerchantGateway`;
  - `PaymentOrchestrator` roda reserva e validação em paralelo (dois `CompletableFuture`) e, na S1, credita direto pela fachada;
  - adaptadores em `infra/local`;
  - controller com `Idempotency-Key`, `202` e `Location`, e erros em `application/problem+json` com `code`.

Ajustes durante a implementação:

- `PaymentIntentRepository` voltou para `coordinator.infra.persistence`, atrás da porta `PaymentIntentStore`. Estender `JpaRepository` a partir de uma interface do domínio dava conflito de assinatura no `save` genérico. Plano e tarefas atualizados.
- O Micronaut Serde convertia `10.5` para `10` e `"100"` para `100`. Criado `WholeNumberDeserializer` para aceitar só inteiros JSON.
- O `Problem` do Zalando serializava `status` como texto e colocava `code` dentro de `parameters`. Substituído por um record `ApiProblem` e um handler próprio.

## Prompt 7 — Suíte de testes travada

> o teste demorou demais acho que alguma coisa travou

### Investigação

- `jstack` do JVM do Surefire: a thread `main` estava parada no `DROP` das tabelas do `create-drop`, feito ao fechar o contexto do `PaymentsHttpIT`.
- `pg_stat_activity`: o `DROP` esperava uma sessão `idle in transaction` cuja última consulta tinha sido em `spp_merchant.merchant`.
- `jstack` não mostra virtual threads. Com `jcmd <pid> Thread.dump_to_file` apareceu uma thread do orquestrador no meio de `MerchantService.credit`. Ela pediu o `SessionFactory` ao contexto; como ele já tinha sido destruído, o Micronaut criou outro, que também tentou apagar as tabelas e ficou esperando o lock da própria transação da thread. Deadlock.

### Causa

O `PaymentsHttpIT` cria pagamentos e não espera o processamento terminar. O orquestrador usava o executor compartilhado do Micronaut e não esperava os pagamentos em andamento no desligamento. Isso também vale em produção: um pagamento pode ser cortado no meio quando a aplicação para.

### Correção

- `PaymentOrchestrator` tem executor próprio de virtual threads (`payment-N`). Ao receber o `ShutdownEvent`, para de aceitar tarefas e espera até 10 segundos as que estão em andamento. O que não terminar fica no estágio atual para ser retomado depois (S4).
- Usa `ShutdownEvent` e não `@PreDestroy`: o Micronaut publica o evento antes de destruir qualquer bean, enquanto o `SessionFactory`, criado sob demanda depois do orquestrador, era destruído antes dele. Com `@PreDestroy` a suíte passava, mas os logs ainda mostravam threads usando o banco já fechado.
- Surefire com `forkedProcessTimeoutInSeconds=300`, para que um travamento futuro derrube o build em vez de ficar parado.

Resultado: `./mvnw clean test` verde, com 60 testes em cerca de 16 segundos e sem erros de banco no log.

## Prompt 8 — Próximo passo e fechamento da S1

> valide qual o proximo passo a seguir

Escolhido: fechar a S1 com a demo por `curl`, limpar e commitar S0 e S1, depois iniciar a S2.

### Resposta (resumo)

- `compose.yaml` com `postgres:16` e `apache/kafka-native:4.3.1`, as mesmas imagens dos testes.
- Demo com a aplicação rodando (`java -jar target/livecode-0.1.jar`):
  - A paga 100 a X: `202` com `Location`, depois `CONFIRMED`. A fica com saldo 400 e reservado 0, X com saldo 100, recebível de 100 `CREDITED`.
  - B paga 100 a X: `FAILED` / `INSUFFICIENT_BALANCE`, saldo de B intacto (50).
  - A paga 30 a Y (inativo): `FAILED` / `MERCHANT_INACTIVE`, saldo de A intacto.
  - `GET` de pagamento inexistente: `404` com `code` `PAYMENT_NOT_FOUND`.
- A demo mostrou que os `400` gerados pelo framework (amount `10.5`, header ausente, Bean Validation) saíam sem `title` e sem `code`, fora do `Problem` do contrato. Os testes só conferiam o status.
  - Criado `ApiProblemErrorResponseProcessor`, que substitui o `ProblemErrorResponseProcessor` do problem-json. `400` sai com `code` `INVALID_REQUEST`; outros status usam o nome do status como `code`. Em `5xx` o `detail` é omitido para não expor detalhes internos.
  - `PaymentsHttpIT` passa a exigir `application/problem+json`, `code`, `status` numérico, `title` e `detail` nos `400`.
- `./mvnw clean test` verde, com 60 testes.

### S2 — Eventos (T030–T035)

- Records dos eventos, uma cópia por módulo, sem classe compartilhada: `coordinator/infra/messaging` e `merchant/infra/messaging` têm cada um seu `PaymentDebited` e `PaymentConfirmed`.
- `EventSchemaTest`, com `json-schema-validator` 3.0.7:
  - o que cada módulo publica passa no JSON Schema do contrato;
  - cada consumidor lê o exemplo do contrato com a própria cópia do record;
  - o schema recusa valor fracionário, campo extra e `eventVersion` diferente de 1.
- Coordinator:
  - nova porta `PaymentEventPublisher` no domínio. O orquestrador grava `DEBITED` e só então publica `spp.payment.debited`, com chave `paymentId`.
  - `MerchantGateway` perdeu o `credit`.
  - `PaymentConfirmedListener` chama `PaymentService.confirmCredited`, que ignora confirmação repetida, de pagamento `FAILED` ou de pagamento desconhecido, para o consumidor não ficar preso na mesma mensagem.
- Merchant: `PaymentDebitedListener` credita pela fachada e publica `spp.payment.confirmed`.
- Entrega nos dois listeners:
  - offset `SYNC_PER_RECORD`, confirmado só depois de o método terminar;
  - erro tenta de novo com backoff exponencial (5 vezes), em vez de pular a mensagem;
  - producers síncronos com `acks=all`.

  Se as tentativas se esgotarem, o pagamento fica `DEBITED` e o job da S4 republica.
- Tópicos criados na inicialização por beans `NewTopic`, cada módulo o tópico que publica, com partições e réplicas no padrão do broker.
- Testes:
  - `AbstractContainersTest` passou para `hbm2ddl.auto=create`: com listeners Kafka em todos os contextos, o `DROP` do `create-drop` ao fechar o contexto voltaria a arriscar o travamento do Prompt 7;
  - os tópicos ganham sufixo aleatório por contexto, para eventos que sobram de uma classe de teste não serem consumidos pela seguinte;
  - `PaymentJourneyIT` confere que A pagando X gera exatamente um `spp.payment.confirmed` com `paymentId`, CNPJ, valor e `creditedAt`, e que pagamentos `FAILED` não geram evento.
- `./mvnw clean test` verde, com 69 testes em cerca de 23 segundos.

## Prompt 9 — Commitar a S2 e seguir para a S3

Escolhido: commitar a S2 e iniciar a S3 (idempotência e concorrência).

### Resposta (resumo)

S2 commitada. Tarefas T040–T045 implementadas, com os testes escritos antes das correções:

- `PaymentsIdempotencyIT`, com Client A pagando X por HTTP:
  - mesma chave e mesmos dados devolve o mesmo pagamento e cobra uma vez;
  - mesma chave com outros dados devolve `409` com `code` `IDEMPOTENCY_CONFLICT`;
  - 10 `POST` simultâneos com a mesma chave criam um pagamento só.
- `ReserveServiceIT`:
  - reservar, confirmar e liberar duas vezes movem saldo uma vez;
  - reservar de novo com outros dados é conflito;
  - 10 reservas simultâneas de 100 numa conta com 500 aceitam exatamente 5;
  - 10 reservas simultâneas com o mesmo `paymentId` reservam uma vez.
- `DuplicateEventsIT`, que publica mensagens cruas no Kafka e espera o grupo de consumo processar tudo:
  - `PaymentDebited` repetido credita o merchant uma vez, e cada entrega republica a confirmação;
  - `PaymentConfirmed` repetido não altera um pagamento `CONFIRMED`, nem um `FAILED`.

Problemas que os testes revelaram e correções:

- **`POST` simultâneo com a mesma chave:** as duas transações não achavam o pagamento, as duas inseriam, e uma estourava a chave primária (`500`).
  - A intenção passou a ser gravada com `INSERT ... ON CONFLICT (id) DO NOTHING`, e a porta `PaymentIntentStore` ganhou `insertIfAbsent`. O Postgres faz a segunda transação esperar a primeira e devolve 0 linhas; ela então lê o pagamento gravado e compara os dados.
- **Reserva simultânea com o mesmo `paymentId`:** as duas faziam o `UPDATE` condicional e uma estourava a chave primária. O saldo ficava certo, porque a transação que falhava era desfeita, mas quem chamava recebia erro técnico.
  - `ReserveService.reserve` agora trava a conta (`PESSIMISTIC_WRITE`) antes de procurar a reserva existente. Pedidos para a mesma conta entram em fila, e o segundo encontra a reserva do primeiro.
- **`creditedAt` inconsistente:** a primeira confirmação usava o instante em memória (nanossegundos), e a republicada lia do Postgres (microssegundos). O mesmo recebível aparecia com dois valores.
  - Instantes das entidades truncados para microssegundos.
- A mesma chave com dados diferentes voltava `202`. `PaymentService.accept` agora compara os dados e lança `IdempotencyConflictException`, que o `IdempotencyConflictExceptionHandler` traduz em `409`.

`./mvnw clean test` verde, com 83 testes.
