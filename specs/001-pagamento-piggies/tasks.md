# Tarefas 001 — Pagamento com Piggies via QR Code

- **Plano:** [plan.md](plan.md)
- **Modelo de dados:** [data-model.md](data-model.md)
- **Contratos:** [contracts/](contracts/)

Convenções:

- `[P]` pode ser feita em paralelo com as outras `[P]` da mesma fase (arquivos diferentes, sem dependência).
- `[USn]` liga a tarefa à história da spec.
- Caminhos relativos a `src/main/java/co/inter/piggies/` (código) e `src/test/java/co/inter/piggies/` (teste).
- Em cada fatia, o teste é escrito antes da implementação e começa falhando.
- DTOs e controllers são escritos à mão espelhando os contratos (D11): mesmos nomes de campo, tipos, validações e códigos de erro.

## S0 — Fundação

Objetivo: projeto sobe com os três módulos, schemas criados, dados iniciais carregados e a regra de módulos verificada.

- [x] **T001** `pom.xml`: adicionar `com.tngtech.archunit:archunit-junit5` e `com.networknt:json-schema-validator` (escopo de teste, versões mais recentes).
- [x] **T002** `application.yml`: `hibernate.hbm2ddl.create_namespaces: true`; nomes dos tópicos em configuração (`spp.topics.payment-debited`, `spp.topics.payment-confirmed`).
- [x] **T003** `support/AbstractContainersTest`: incluir `create_namespaces` nas propriedades de teste.
- [x] **T004 [P]** `reserve/infra`: entidades `Client`, `Account`, `Reservation` e repositórios, schema `spp_reserve`, constraints de [data-model.md](data-model.md).
- [x] **T005 [P]** `merchant/infra`: entidades `Merchant`, `MerchantAccount`, `Receivable` e repositórios, schema `spp_merchant`.
- [x] **T006 [P]** `coordinator/domain`: `PaymentIntent`, `Stage`, `FailureReason` e a porta `PaymentIntentStore`; `coordinator/infra/persistence`: `PaymentIntentRepository`, schema `spp_coordinator`.
- [x] **T007 [P]** Seeds idempotentes na inicialização: `reserve/infra/ReserveSeed` (Clientes A e B) e `merchant/infra/MerchantSeed` (Merchants X e Y).
- [x] **T008** Teste `architecture/ModuleBoundariesTest` (ArchUnit) com as regras do plano:
  - `reserve` e `merchant` não dependem de outro módulo;
  - só `coordinator.infra.local` importa `reserve.facade` e `merchant.facade`;
  - nenhum módulo importa `domain`, `infra` ou `web` de outro.
- [x] **T009** Teste de integração `FoundationIT`: contexto sobe, três schemas existem, seeds presentes e sem duplicar ao rodar de novo.

**Checkpoint S0:** `./mvnw test` verde. Atingido em 24/09/2026 (13 testes).

## S1 — Jornada HTTP (US1, US2) — MVP

Objetivo: `POST /v1/payments` responde `202` e o pagamento chega a `CONFIRMED` ou `FAILED`. O crédito é chamado direto pela fachada do merchant; o Kafka entra na S2.

### Reserve

- [x] **T010** `reserve/facade`: `ReserveFacade` com `reserve`, `confirm`, `release`, `findAccount`; records `ReserveCommand`, `ReservationView`, `AccountView`; resultado de negócio como tipo selado (`Reserved` | `Rejected(code)`).
- [x] **T011 [US1][US2]** Teste `reserve/ReserveServiceIT`:
  - reserva com saldo suficiente (sobe `reserved_balance`, não muda `balance`);
  - saldo insuficiente (`INSUFFICIENT_BALANCE`);
  - conta inexistente e CPF que não é dono da conta (`PAYER_ACCOUNT_NOT_FOUND`);
  - confirmar debita saldo e reservado;
  - liberar restaura o disponível sem mexer no saldo;
  - confirmar reserva liberada e liberar reserva confirmada são conflito.
- [x] **T012** `reserve/domain` e `reserve/infra`: implementação com `UPDATE` condicional (D4) e `SELECT ... FOR UPDATE` em confirmar e liberar.

### Merchant

- [x] **T013** `merchant/facade`: `MerchantFacade` com `findByCnpj` e `credit`; records `MerchantView`, `CreditCommand`, `ReceivableView`.
- [x] **T014 [US1][US2]** Teste `merchant/MerchantServiceIT`: merchant ativo, inativo e inexistente; crédito grava recebível `CREDITED` e soma ao saldo da conta do merchant.
- [x] **T015** `merchant/domain` e `merchant/infra`: implementação.

### Coordinator

- [x] **T016** `coordinator/domain`: portas `ReserveGateway` e `MerchantGateway` (validar e, só na S1, creditar), com resultados de negócio próprios do coordinator.
- [x] **T017 [P] [US1][US2]** Teste unitário `coordinator/domain/PaymentIntentTest`: transições permitidas (`ACCEPTED` → `DEBITED` → `CONFIRMED`, `ACCEPTED` → `FAILED`), proibidas (`DEBITED` → `FAILED`, sair de estado final) e mapeamento para status público.
- [x] **T018 [P] [US1][US2]** Teste unitário `coordinator/orchestration/PaymentOrchestratorTest` com gateways falsos:
  - sucesso → `CONFIRMED`;
  - saldo insuficiente → `FAILED`, sem liberar;
  - merchant inativo ou inexistente → libera a reserva e `FAILED`;
  - as duas falham → motivo da reserva;
  - exceção técnica → continua `ACCEPTED`;
  - reserva e validação rodam em paralelo (latch).
- [x] **T019** Implementar `PaymentIntent` (transições), `PaymentService` (`accept` transacional, `get`) e `PaymentOrchestrator` (D8: executor de virtual threads, dois `CompletableFuture`).
- [x] **T020** `coordinator/infra/local`: `LocalReserveGateway` e `LocalMerchantGateway` sobre as fachadas.
- [x] **T021** `coordinator/web`: records do contrato (`CreatePaymentRequest`, `QrCode`, `Payer`, `PaymentAccepted`, `Payment`) com Bean Validation, `PaymentsController` (`POST` com `Idempotency-Key` e `Location`, `GET`) e problem+json com `code`. O `POST` dispara o orquestrador depois do commit.
- [x] **T022 [US1][US2]** Teste `coordinator/web/PaymentsHttpIT`:
  - `202` com `paymentId`, `PROCESSING` e `Location`;
  - resposta não espera o processamento (gateway lento substituído no teste);
  - `400` para amount `0`, `-1` e `10.5`, CNPJ e CPF fora do formato, header ausente;
  - `404` para pagamento inexistente.
- [x] **T023 [US1][US2]** Teste ponta a ponta `journey/PaymentJourneyIT` (Awaitility):
  - A paga 100 a X: `CONFIRMED`, A com saldo 400 e reservado 0, recebível de 100 e saldo de X 100;
  - B paga 100: `FAILED` / `INSUFFICIENT_BALANCE`, saldo de B intacto;
  - A paga Y (inativo): `FAILED` / `MERCHANT_INACTIVE`, reserva `RELEASED`, disponível de A intacto;
  - CNPJ desconhecido: `FAILED` / `MERCHANT_NOT_FOUND`;
  - CPF que não é dono da conta: `FAILED` / `PAYER_ACCOUNT_NOT_FOUND`.

**Checkpoint S1:** jornada demonstrável por `curl` com os dados iniciais. Entrega mínima do desafio. Atingido em 24/09/2026: 60 testes verdes e demo por `curl` com `compose.yaml`.

## S2 — Eventos (US3)

Objetivo: crédito e fechamento passam por Kafka, com eventos validados contra o schema.

- [ ] **T030 [P]** Records dos eventos, uma cópia por módulo (sem classe compartilhada): `coordinator/infra/messaging` (`PaymentDebited`, `PaymentConfirmed`) e `merchant/infra/messaging` (idem).
- [ ] **T031 [P] [US3]** Teste `contracts/EventSchemaTest`: eventos serializados pelos dois módulos passam em `contracts/events/*.schema.json`; valor fracionário e campo extra são recusados.
- [ ] **T032** Coordinator: producer de `spp.payment.debited` (chave `paymentId`). Orquestrador passa a gravar `DEBITED` e publicar; `credit` sai do `MerchantGateway`.
- [ ] **T033** Merchant: consumer de `spp.payment.debited` → `credit` → publica `spp.payment.confirmed`; offset confirmado só depois de publicar (D6).
- [ ] **T034** Coordinator: listener de `spp.payment.confirmed` → `CONFIRMED`. Evento de pagamento já final é ignorado.
- [ ] **T035 [US3]** Atualizar `PaymentJourneyIT`: A paga 100 a X gera exatamente um `spp.payment.confirmed` com `paymentId`, CNPJ, valor e `creditedAt`; pagamentos `FAILED` não geram evento.

**Checkpoint S2:** enunciado coberto de ponta a ponta.

## S3 — Idempotência e concorrência (US4, casos de borda)

- [ ] **T040 [US4]** Teste em `PaymentsHttpIT`: mesma `Idempotency-Key` e mesmos dados devolve o mesmo `paymentId` e não cria segunda reserva; dados diferentes devolvem `409 IDEMPOTENCY_CONFLICT`.
- [ ] **T041** Implementar a comparação no `PaymentService.accept` e o `409`.
- [ ] **T042 [US4]** Teste em `ReserveServiceIT`: reservar, confirmar e liberar duas vezes movem saldo uma vez; reservar de novo com dados diferentes é conflito.
- [ ] **T043 [US4]** Teste de integração: `spp.payment.debited` duplicado gera um recebível e um crédito; `spp.payment.confirmed` duplicado não altera o coordinator.
- [ ] **T044** Teste de concorrência em `ReserveServiceIT`: 10 reservas simultâneas de 100 numa conta com 500 → exatamente 5 aceitas, reservado 500, disponível nunca negativo.
- [ ] **T045** Ajustes que os testes T042–T044 revelarem.

## S4 — Resiliência e APIs internas

- [ ] **T050** Job agendado de reprocessamento (D9): `ACCEPTED` parado há mais de 30 s refaz o fluxo; `DEBITED` republica o evento. Teste com intenções presas.
- [ ] **T051 [P]** `reserve/web`: controllers de `reserve.openapi.yaml` sobre a fachada + teste HTTP.
- [ ] **T052 [P]** `merchant/web`: controllers de `merchant.openapi.yaml` sobre a fachada + teste HTTP.
- [ ] **T053** `README.md`: como subir Postgres e Kafka, rodar a aplicação e testar a jornada com `curl`.

## Depois da S4

- Geração de código a partir dos OpenAPI e teste automático de conformidade com o contrato (D11).
- Datasource e Flyway por módulo (D1), preparando a extração.

## Dependências

```text
S0 ──► S1 (reserve ∥ merchant ∥ testes unitários do coordinator) ──► S2 ──► S3 ──► S4
```

- Dentro da S1, reserve (T010–T012) e merchant (T013–T015) são independentes. O coordinator pode começar pelos testes unitários (T017–T018) com gateways falsos, e só precisa das fachadas em T020.
- Ordem de corte se o tempo acabar: S4, depois S3.
