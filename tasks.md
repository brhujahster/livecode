# SPP — Divisão de tarefas

Sistema de Pagamento com Porquinhos. Quatro pessoas, três serviços, um processo Micronaut (`co.inter.piggies`). Cada serviço persiste no próprio schema do PostgreSQL. Contratos (OpenAPI e JSON Schema) entram no repositório antes da implementação.

Pacotes:

| Pessoa | Pacote | Serviço |
|---|---|---|
| Dev 1 | `coordinator` | PiggiesPaymentCoordinator |
| Dev 2 | `reserve` | PiggiesReserveService |
| Dev 3 | `merchant` | PiggiesMerchantService |
| Dev 4 | `messaging` | Eventos Kafka e teste de ponta a ponta |

Ordem de corte se o tempo acabar:

1. Happy path HTTP: intenção, reserva, validação, débito e crédito.
2. Publicar `spp.payment.confirmed`.
3. Consumer Kafka aplicando o crédito de forma idempotente.
4. Liberar a reserva quando o merchant estiver inativo ou o crédito falhar.

Fora desta versão: valor fracionário, decodificação de imagem de QR, cancelamento após `CONFIRMED`, autenticação e três processos separados.

## Bloco 0 — Contratos (todos, ~30 min)

Cada pessoa escreve o contrato do que vai implementar. Os campos congelam ao fim deste bloco.

- [ ] **Dev 1** — `src/main/resources/openapi`
  - `POST /v1/payments` → `202` com `paymentId` e `status`
  - `GET /v1/payments/{id}` → intenção e status
  - Body: QR decodificado (`merchantCnpj`, `amount` inteiro) e pagador (`payerCpf`, `payerAgency`, `payerAccount`)
  - Erros em `application/problem+json`
- [ ] **Dev 2** — `src/main/resources/openapi`
  - `POST /v1/reservations`
  - `POST /v1/reservations/{id}/confirm`
  - `POST /v1/reservations/{id}/release`
- [ ] **Dev 3** — `src/main/resources/openapi`
  - `POST /v1/merchants/validate`
  - `POST /v1/credits`
- [ ] **Dev 4** — schemas de evento
  - `contracts/events/payment-confirm.schema.json` → tópico `spp.payment.confirm`
  - `contracts/events/payment-confirmed.schema.json` → tópico `spp.payment.confirmed`
  - Payload de confirmação: `paymentId`, `reservationId`, `merchantCnpj`, `amount`
  - Payload confirmado: `paymentId`, `merchantCnpj`, `amount`, `creditedAt`

`paymentId` é a chave de idempotência em todos os serviços.

## Dev 1 — PiggiesPaymentCoordinator

Ponto de entrada do app. Registra a intenção e orquestra a confirmação. A borda é assíncrona: o `POST` devolve `202` e o trabalho segue depois.

Schema `spp_coordinator`.

### Entidade

- [ ] `PaymentIntent`: `id`, `payerCpf`, `payerAgency`, `payerAccount`, `merchantCnpj`, `amount`, `status` (`PROCESSING`, `CONFIRMED`, `FAILED`), `reservationId`, `failureReason`, `createdAt`, `updatedAt`

### API

- [ ] `POST /v1/payments` persiste a intenção em `PROCESSING` e responde `202`
- [ ] `GET /v1/payments/{id}` devolve o estado atual
- [ ] Validar amount inteiro e maior que zero
- [ ] Repetir o mesmo `paymentId` devolve a intenção existente

### Orquestração

- [ ] Depois do `202`, chamar em paralelo reserva (`POST /v1/reservations`) e validação (`POST /v1/merchants/validate`)
- [ ] Com os dois ok, pedir o débito (`POST /v1/reservations/{id}/confirm`) e publicar `spp.payment.confirm`
- [ ] Marcar a intenção `CONFIRMED` ao observar o crédito concluído
- [ ] Marcar `FAILED` com `failureReason` quando reserva ou validação recusar
- [ ] Se a reserva foi criada e a validação falhou, chamar `POST /v1/reservations/{id}/release`

### Testes

- [ ] Máquina de estados com clientes HTTP mockados: sucesso, merchant inativo, saldo insuficiente
- [ ] `POST` responde `202` sem esperar o débito

## Dev 2 — PiggiesReserveService

Reserva e confirma o débito do cliente.

Schema `spp_reserve`.

### Entidades

- [ ] `Client`: `id`, `name`, `cpf` único
- [ ] `Account`: `id`, `clientId`, `agency`, `number`, `balance`, `reservedBalance`
- [ ] `Reservation`: `id`, `paymentIntentId` único, `accountId`, `amount`, `status` (`RESERVED`, `CONFIRMED`, `RELEASED`), `createdAt`

Disponível = `balance − reservedBalance`.

### API

- [ ] `POST /v1/reservations` reserva o valor se houver disponível. Sobe `reservedBalance`. Status `RESERVED`
- [ ] `POST /v1/reservations/{id}/confirm` debita: tira o valor de `balance` e de `reservedBalance`. Status `CONFIRMED`
- [ ] `POST /v1/reservations/{id}/release` devolve só `reservedBalance`. Status `RELEASED`
- [ ] Recusar amount não positivo e saldo insuficiente
- [ ] Repetir a mesma operação com o mesmo `paymentIntentId` não move saldo de novo

### Testes

- [ ] Reserva com saldo suficiente e com saldo insuficiente
- [ ] Confirmação debita uma vez
- [ ] Release após reserva restaura o disponível e não mexe em `balance`
- [ ] Integração com PostgreSQL (Testcontainers)

### Dados de apoio

- [ ] Seed do Cliente A com conta e saldo suficiente para o cenário de 100 Piggies

## Dev 3 — PiggiesMerchantService

Valida o merchant e registra o crédito.

Schema `spp_merchant`.

### Entidades

- [ ] `Merchant`: `id`, `name`, `cnpj` único, `agency`, `accountNumber`, `status` (`ACTIVE`, `INACTIVE`)
- [ ] `Receivable`: `id`, `paymentIntentId` único, `merchantId`, `amount`, `status` (`CREDITED`), `creditedAt`

### API

- [ ] `POST /v1/merchants/validate` aceita merchant `ACTIVE` e recusa `INACTIVE` ou CNPJ desconhecido
- [ ] `POST /v1/credits` grava o recebível `CREDITED` para um merchant ativo
- [ ] Repetir o crédito do mesmo `paymentIntentId` não cria outro recebível

### Testes

- [ ] Merchant ativo, inativo e inexistente
- [ ] Crédito idempotente
- [ ] Integração com PostgreSQL (Testcontainers)

### Dados de apoio

- [ ] Seed do Merchant X ativo e de um merchant inativo para o caso de falha

## Dev 4 — Eventos e jornada completa

O merchant consome e publica eventos de pagamento. O teste de ponta a ponta prova o cenário do enunciado.

### Kafka

- [ ] Producer de `spp.payment.confirm` (disparado pelo coordinator quando reserva e validação passaram)
- [ ] Consumer de `spp.payment.confirm` no merchant: credita de forma idempotente
- [ ] Producer de `spp.payment.confirmed` depois do crédito
- [ ] Coordenar a leitura de `spp.payment.confirmed` para o coordinator fechar a intenção em `CONFIRMED`

### Teste de integração

- [ ] Cliente A paga 100 Piggies ao Merchant X
- [ ] Intenção termina `CONFIRMED`
- [ ] Conta de A: `balance` reduzido em 100 e `reservedBalance` de volta ao valor anterior
- [ ] Recebível de X no valor de 100
- [ ] Mensagem em `spp.payment.confirmed` com `paymentId`, `merchantCnpj`, `amount` e `creditedAt`
- [ ] Merchant inativo: intenção `FAILED`, reserva `RELEASED`, nenhum recebível e nenhum débito

## Dependências entre as frentes

```text
Bloco 0 (contratos)
    ├─ Dev 2 implementa reserve
    ├─ Dev 3 implementa validate + crédito HTTP
    ├─ Dev 1 implementa 202/GET e orquestra com clientes mockados
    └─ Dev 4 escreve schemas e o teste (pode nascer falhando)
Dev 2 + Dev 3 prontos
    └─ Dev 1 troca o mock pelas chamadas reais
Dev 4 liga os tópicos
    └─ Jornada de 100 Piggies fecha com evento publicado
```

Enquanto reserve e merchant não existem, o Dev 1 codifica contra os OpenAPI com clientes mockados. O Dev 4 monta o teste de ponta a ponta em cima dos contratos, mesmo que ele fique vermelho até a ligação final.
