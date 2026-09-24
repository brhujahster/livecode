> Log da primeira tentativa, descartada quando o projeto foi reiniciado com SDD (ver 2026-09-24-reinicio-sdd.md). Mantido porque o desafio exige que todos os prompts usados fiquem no projeto.

# Conversa com IA — Diego (Dev 1)

Registro da conversa com o agente de IA do Cursor usada no desafio SPP (Sistema de Pagamento com Porquinhos). O desafio exige que prompts e pesquisas feitos com IA sejam anexados e commitados no projeto.

Data: 24/09/2026

---

## 1. Divisão do trabalho e entidades

### Prompt

> Precisamos criar um projeto, que será desenvolvido por 4 desenvolvedores, seguindo as instruções abaixo. Sugira ideias de como realizar as divisões para todos que todos consigam desenvolver
> Também sugira quais entidades precisaremos criar para completar a tarefa.
>
> ## SPP — Sistema de Pagamento com Porquinhos
>
> No Inter trabalhamos com `contract-first`. O primeiro passo de qualquer entrega é definir os contratos entre os microserviços usando OpenAPI ou JSON Schema — um contrato por serviço, definido antes da implementação. Eventos publicados (ex.: Kafka) também devem ter schema.
>
> Os clientes do Inter não gostam de ficar olhando para uma ampulheta na tela, por isso todo serviço de borda precisa trabalhar de forma assíncrona.
>
> Testes unitários e de integração são obrigatórios.
>
> Planejem as entregas. Tempo é um recurso limitado e a gestão do tempo é parte do desafio.
>
> Trabalhem em equipe. Vocês não estão competindo entre si. Vocês vencem juntos ou perdem juntos.
>
> Uma entrega parcial que faz alguma coisa vale mais do que um monte de código que não realiza nada.
>
> Vocês podem usar IA, consultar a internet e usar qualquer outra ferramenta à sua disposição, desde que tudo seja anexado ao projeto e comitado (pesquisas, prompts, códigos de referência, etc.).
>
> Contudo, usem com responsabilidade:
> - Vocês precisarão resolver bugs e responder questões sobre a implementação. Responder "Não sei, a IA que fez assim" é critério de eliminação.
> - Fiquem atentos quanto a propriedade intelectual e nunca utilizem códigos ou documentos que vocês não estão autorizados a publicar.
>
> Piggie é a nova stablecoin do Inter. O desafio é implementar o Sistema de Pagamento com Porquinhos (SPP).
>
> Um pagamento envolve algumas etapas:
>
> 1. Cliente inicia um pagamento por meio de um QR Code que identifica o merchant e a quantidade de Piggies
> 2. Em paralelo, a intenção de pagamento é registrada, o saldo do cliente é reservado e o merchant é validado
> 3. Quando as três operações concluem com sucesso, o pagamento é confirmado: Piggies são debitados do cliente, creditados ao merchant e o evento é publicado para outros sistemas
>
> Exemplo:
> - Cliente A inicia pagamento de 100 Piggies ao Merchant X
> - Intenção de pagamento é registrada
> - Reserva de 100 Piggies é criada na conta do Cliente A
> - Merchant X é validado como ativo
> - Pagamento é confirmado: 100 Piggies debitados de A, 100 creditados a X
>
> **IMPORTANTE** nessa dinâmica vamos implementar uma versão simplificada do sistema:
> - Pagamentos são sempre por um valor inteiro de Piggies
> - A API já vai receber o QR Code decodificado
> - Não há cancelamento após confirmação
>
> Vocês devem implementar 3 serviços:
>
> - **PiggiesPaymentCoordinator** é o ponto de entrada do App, registra a intenção de pagamento e orquestra a confirmação de ponta a ponta.
> - **PiggiesReserveService** reserva e confirma o débito do cliente. Persiste o estado da reserva em PostgreSQL.
> - **PiggiesMerchantService** valida o merchant e registra o crédito. Persiste recebíveis em PostgreSQL e consome/publica eventos de pagamento via Kafka.
>
> Cliente(nome, cpf)
> Conta(numero, agencia, Client/Merchant, numero da conta, saldo)
> QRcode
> Merchant(nome, cnpj numero da conta)

### Resposta da IA (resumo)

A divisão sugerida é por pacote, com os contratos congelados nos primeiros 30 minutos. O repositório já é um Micronaut único com Postgres e Kafka no Testcontainers. Por isso a proposta foi três pacotes dentro de `co.inter.piggies`, cada um com seu schema Postgres. Os três processos separados ficam para depois do happy path verde.

| Pessoa | Pacote | Entrega |
|---|---|---|
| Dev 1 | `coordinator` | `POST /v1/payments` → 202, `GET /v1/payments/{id}`, orquestração paralela |
| Dev 2 | `reserve` | Reservar, confirmar o débito, liberar a reserva |
| Dev 3 | `merchant` | Validar merchant, recebível e crédito |
| Dev 4 | `messaging` | JSON Schema dos eventos, consumer/producer Kafka, teste de ponta a ponta |

Ordem de corte se o tempo acabar:

1. Happy path HTTP.
2. Publicar `spp.payment.confirmed`.
3. Consumer idempotente.
4. Liberar a reserva quando o merchant estiver inativo.

Entidades sugeridas (sete tabelas, cada uma com um dono):

- **Coordinator** (`spp_coordinator`): `PaymentIntent`
- **Reserve** (`spp_reserve`): `Client`, `Account` (`balance`, `reservedBalance`), `Reservation`
- **Merchant** (`spp_merchant`): `Merchant`, `Receivable`

O QR Code entra como DTO de entrada, sem tabela. `paymentId` é a chave de idempotência em todos os serviços.

Eventos:

- `spp.payment.confirm`: publicado pelo coordinator, consumido pelo merchant
- `spp.payment.confirmed`: publicado pelo merchant depois do crédito

A IA também gerou um canvas visual com essa divisão.

---

## 2. Arquivo de tarefas

### Prompt

> Crie um arquivo chamado tasks.md com as divisões de tarefas necessárias para o desenvolvimento dos serviços

### Resposta da IA (resumo)

A IA criou o `tasks.md` na raiz do projeto com:

- **Bloco 0 (contratos):** três OpenAPI e dois JSON Schema.
- **Dev 1:** coordinator, com entidade, API, orquestração e testes.
- **Dev 2:** reserve, com entidades, API, testes e seed do Cliente A.
- **Dev 3:** merchant, com entidades, API, testes e seeds de merchant ativo e inativo.
- **Dev 4:** Kafka e o teste de integração da jornada de 100 Piggies.
- O diagrama de dependências entre as frentes.

---

## 3. Contratos OpenAPI

### Prompt

> Gere os contratos levando em consideração as entidades sugeridas acima, no formato da OpenAPI

### Resposta da IA (resumo)

A IA criou três contratos OpenAPI 3.0.3 em `contracts/openapi/`:

| Arquivo | Serviço | Operações |
|---|---|---|
| `payments.openapi.yaml` | PiggiesPaymentCoordinator | `POST /v1/payments` → 202, `GET /v1/payments/{id}` |
| `reservations.openapi.yaml` | PiggiesReserveService | `POST /v1/reservations`, `/confirm`, `/release` |
| `merchants.openapi.yaml` | PiggiesMerchantService | `POST /v1/merchants/validate`, `POST /v1/credits` |

Decisões dos contratos:

- Erros em `application/problem+json`.
- O header `Idempotency-Key` vira o `id` da `PaymentIntent`.
- Repetir uma operação devolve o recurso já existente sem mover saldo.
- Transição inválida responde 409. Saldo insuficiente ou merchant inativo responde 422.
- Os eventos `PaymentConfirmEvent` e `PaymentConfirmedEvent` entram como schemas nos contratos de quem publica ou consome.

---

## 4. Implementação das tasks do Dev 1

### Prompt

> vamos desenvover as tasks do dev 1 nesse momento, gere as entidades e tudo que precisa para que o serviço rode da meneira correta, também crie os testes com uma cobertura de 100% para as regras de negócio geradas

### Resposta da IA (resumo)

O PiggiesPaymentCoordinator foi implementado usando os modelos e clientes gerados a partir do OpenAPI que já estavam no projeto.

Estrutura criada em `src/main/java/co/inter/piggies/coordinator/`:

- `domain/`: `PaymentIntentEntity` (entidade JPA em `spp_coordinator.payment_intent`), repositório, `PaymentIntentStore`, `FailureReasons`, `InvalidPaymentException`, `DuplicatePayment`
- `persistence/`: `JpaPaymentIntentStore`
- `service/`: `PaymentApplicationService` (aceite idempotente e consulta)
- `orchestration/`: `PaymentOrchestrator`, `AsyncPaymentOrchestration` e os gateways de reserve, merchant e publisher
- `client/`: adaptadores sobre os clientes HTTP gerados (`ReservationsApi`, `MerchantsApi`) e o mapeamento dos erros 400/404/409/422
- `messaging/`: producer de `spp.payment.confirm` e listener de `spp.payment.confirmed`
- `api/`: `InvalidPaymentExceptionHandler` e `WholePiggieAmountFilter`, que recusa amount fracionário

O controller `PiggiesPaymentCoordinator` implementa `PaymentsApi`.

Fluxo:

1. `POST /v1/payments` grava a intenção em `PROCESSING` e responde 202.
2. Em segundo plano, a reserva e a validação do merchant rodam em paralelo.
3. Com as duas ok, o coordinator confirma o débito e publica `spp.payment.confirm`.
4. A intenção vira `CONFIRMED` ao receber `spp.payment.confirmed` com CNPJ e valor iguais.
5. Se a reserva ou o merchant falhar, a intenção vira `FAILED` com `failureReason`. Uma reserva já criada é liberada.

Regras cobertas pelos testes:

- Mesmo `Idempotency-Key` devolve a intenção existente, sem reservar de novo.
- Amount inteiro e maior que zero: `0` e `10.5` respondem 400.
- Merchant inativo libera a reserva e marca `FAILED`.
- Saldo insuficiente marca `FAILED` sem liberar.
- O `POST` responde 202 antes da reserva terminar.
- Pagamento desconhecido no `GET` responde 404.
- Evento de crédito divergente marca `FAILED`.
- Falha ao publicar depois do débito marca `FAILED` sem liberar a reserva.

Testes criados:

- `PaymentIntentEntityTest`, `DuplicatePaymentTest`, `DownstreamErrorsTest`, `GatewayAdapterTest`
- `PaymentOrchestratorTest`, `PaymentApplicationServiceTest`, `AsyncPaymentFlowTest`
- `InvalidPaymentExceptionHandlerTest`, `WholePiggieAmountFilterTest`
- `PaymentCoordinatorHttpTest`: integração com Postgres e Kafka via Testcontainers; reserve, merchant e publisher trocados por dublês no ambiente `coordinator-it`

Ajustes de configuração:

- `application.yml`: URLs dos serviços `reserve` e `merchant`
- `application.yml`: criação do schema `spp_coordinator` na conexão
- `hbm2ddl.create_namespaces`

Problemas encontrados durante o desenvolvimento:

- O Maven rodava com Java 8. A build passou com o JDK 25 do `mise`.
- Faltava `@Controller` na implementação de `PaymentsApi` e as rotas davam 404.
- O schema `spp_coordinator` não existia no Postgres.
- O filtro de amount fracionário precisou de `@Nullable` no retorno.

A suíte completa (`./mvnw test`) passou. As tasks do Dev 1 foram marcadas no `tasks.md`.

Pendência: enquanto o Dev 2 e o Dev 3 não subirem os endpoints de reserve e merchant, uma chamada real falha a intenção.

---

## 5. Commit

### Prompts

> realize o commit para o servidor

> antes de realizar o commit gere um arquivo diego.md com toda nossa conversa depois realize o commit

### Resposta da IA

A IA gerou este arquivo e fez o commit das mudanças do Dev 1 junto com ele.
