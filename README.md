# SPP — Sistema de Pagamento com Porquinhos

Pagamento de um QR Code com Piggies. O app envia o pedido, a API responde `202 Accepted` na hora e o pagamento
segue em segundo plano: reserva o saldo do cliente, valida o merchant, debita, credita o merchant e publica a
confirmação para outros sistemas.

O projeto é um monolito modular com três módulos que podem virar serviços separados:

| Módulo | Papel | Dados |
|---|---|---|
| `coordinator` (PiggiesPaymentCoordinator) | Borda HTTP assíncrona e orquestração | schema `spp_coordinator` |
| `reserve` (PiggiesReserveService) | Reserva, débito e liberação do saldo do cliente | schema `spp_reserve` |
| `merchant` (PiggiesMerchantService) | Validação do merchant e crédito (recebível) | schema `spp_merchant` |

A especificação, o plano, os contratos (OpenAPI e JSON Schema) e as tarefas estão em [`specs/`](specs/README.md).
Os prompts de IA usados estão em [`docs/ai/`](docs/ai/).

## Requisitos

- Java 25
- Docker (Testcontainers nos testes; Postgres e Kafka pelo `compose.yaml` para rodar localmente)

## Testes

```bash
./mvnw test
```

Sobe Postgres 16 e Kafka com Testcontainers. Inclui testes unitários, de integração, de contrato dos eventos,
de fronteira entre módulos (ArchUnit) e de ponta a ponta com HTTP, banco e Kafka reais.

## Rodar localmente

```bash
docker compose up -d --wait
./mvnw package -DskipTests
java -jar target/livecode-0.1.jar
```

A aplicação sobe em `http://localhost:8080`, cria os schemas e carrega os dados iniciais:

| Quem | Documento | Agência / conta | Saldo | Situação |
|---|---|---|---|---|
| Cliente A | CPF `12345678901` | `0001` / `000123` | 500 | |
| Cliente B | CPF `98765432100` | `0001` / `000456` | 50 | |
| Merchant X | CNPJ `12345678000199` | `0001` / `900001` | 0 | ativo |
| Merchant Y | CNPJ `98765432000155` | `0001` / `900002` | 0 | inativo |

## Jornada com curl

A `Idempotency-Key` é gerada pelo app e vira o id do pagamento. Repetir o pedido com a mesma chave e os mesmos
dados não cobra de novo.

```bash
KEY=$(cat /proc/sys/kernel/random/uuid)   # ou uuidgen

curl -i -X POST localhost:8080/v1/payments \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $KEY" \
  -d '{"qrCode":{"merchantCnpj":"12345678000199","amount":100},
       "payer":{"cpf":"12345678901","agency":"0001","accountNumber":"000123"}}'
# HTTP/1.1 202 Accepted
# location: /v1/payments/<KEY>
# {"paymentId":"<KEY>","status":"PROCESSING"}

curl localhost:8080/v1/payments/$KEY
# {"paymentId":"<KEY>","status":"CONFIRMED", ...}
```

Outros cenários com os dados iniciais:

| Pedido | Resultado |
|---|---|
| Cliente B paga 100 a X | `FAILED` / `INSUFFICIENT_BALANCE` |
| Cliente A paga Y | `FAILED` / `MERCHANT_INACTIVE`, reserva liberada |
| CNPJ desconhecido | `FAILED` / `MERCHANT_NOT_FOUND` |
| CPF que não é dono da conta | `FAILED` / `PAYER_ACCOUNT_NOT_FOUND` |
| Mesma chave com outros dados | `409` / `IDEMPOTENCY_CONFLICT` |
| `amount` `0`, negativo, fracionário ou texto | `400` / `INVALID_REQUEST` |

Erros seguem `application/problem+json` (RFC 9457) com a extensão `code`.

### Kafka

A imagem `apache/kafka-native` do compose traz só o broker, sem os scripts de linha de comando. Os scripts rodam
a partir da imagem completa, num container descartável que compartilha a rede do broker (ele anuncia
`localhost:9092`):

```bash
alias kcli='docker run --rm -i --network container:live-code-kafka-1 apache/kafka:4.3.1'
```

Ler a confirmação publicada para outros sistemas (e, trocando o tópico, o `spp.payment.debited`):

```bash
kcli /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 \
  --topic spp.payment.confirmed --from-beginning --property print.key=true --timeout-ms 5000
```

Ver se os consumidores da aplicação processaram tudo (`LAG` 0):

```bash
kcli /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group spp-merchant
kcli /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group spp-coordinator
```

Testar o merchant sozinho, publicando um débito à mão (use UUIDs novos em `paymentId` e `eventId`). O merchant
credita X e publica a confirmação; repetir a mesma mensagem não credita de novo:

```bash
kcli /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 \
  --topic spp.payment.debited --property parse.key=true --property key.separator='|' <<'EOF'
3fa85f64-5717-4562-b3fc-2c963f66afa6|{"eventId":"0b7f7a0e-2a51-4a44-8d1f-6a3a8e7c1d10","eventVersion":1,"paymentId":"3fa85f64-5717-4562-b3fc-2c963f66afa6","merchantCnpj":"12345678000199","amount":100,"debitedAt":"2026-09-24T22:00:00Z"}
EOF
```

### Banco

```bash
docker compose exec postgres psql -U postgres
```

```sql
select number, balance, reserved_balance from spp_reserve.account order by number;
select payment_id, amount, status from spp_reserve.reservation;
select number, balance from spp_merchant.merchant_account order by number;
select payment_id, amount, status, credited_at from spp_merchant.receivable;
select id, stage, failure_reason from spp_coordinator.payment_intent;
```

Os saldos se acumulam entre execuções (`hbm2ddl.auto: update`). Para voltar aos dados iniciais:
`docker compose down -v`.

## Como funciona

```text
POST /v1/payments ──► grava a intenção (ACCEPTED) ──► 202
                          │
                          ▼ segundo plano
          reserva o saldo  ∥  valida o merchant
                          │
          falhou? ──► libera a reserva ──► FAILED
                          │
          debita ──► DEBITED ──► publica spp.payment.debited
                                         │
                                         ▼ merchant
                     credita (recebível + saldo) ──► publica spp.payment.confirmed
                                                              │
                                                              ▼ coordinator
                                                          CONFIRMED
```

- A API mostra `PROCESSING` enquanto o pagamento está `ACCEPTED` ou `DEBITED`.
- Tudo é idempotente pelo id do pagamento: pedido HTTP, reserva, crédito e eventos repetidos não duplicam efeito.
- A reserva usa `UPDATE` condicional (`saldo - reservado >= valor`); reservas simultâneas nunca excedem o saldo.
- Os consumidores Kafka só confirmam o offset depois de processar e tentam de novo em caso de erro.
- Os módulos só se falam pelas fachadas (`reserve.facade`, `merchant.facade`), e só pelos adaptadores em
  `coordinator.infra.local`. O ArchUnit garante essas regras.

## Estado e limitações

Fatias S0 a S3 entregues (fundação, jornada HTTP, eventos, idempotência e concorrência), mais as APIs HTTP
internas da S4; ver [`tasks.md`](specs/001-pagamento-piggies/tasks.md).

As APIs internas seguem `reserve.openapi.yaml` e `merchant.openapi.yaml`:

- `PUT /v1/reservations/{paymentId}`, `GET /v1/reservations/{paymentId}`, `POST .../confirm`, `POST .../release`
  e `GET /v1/accounts/{agency}/{accountNumber}`;
- `GET /v1/merchants/{cnpj}` e `GET /v1/receivables/{paymentId}`.

Elas existem para quando os módulos virarem serviços separados. Hoje o coordinator continua chamando `reserve` e
`merchant` em processo, pelas fachadas. Servem também para inspecionar o estado na mão:

```bash
curl -s localhost:8080/v1/accounts/0001/000123
curl -s localhost:8080/v1/merchants/12345678000199
curl -s localhost:8080/v1/receivables/$KEY   # o paymentId é a Idempotency-Key da jornada acima
```

Ainda não implementado:

- **Reprocessamento de pagamentos presos (T050).** Uma falha técnica (banco ou Kafka fora, retries esgotados) deixa
  o pagamento em `ACCEPTED` ou `DEBITED`, e hoje nada o retoma automaticamente.
