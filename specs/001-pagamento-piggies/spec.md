# Spec 001 — Pagamento com Piggies via QR Code

- **Estado:** aprovada em 24/09/2026, com as cinco decisões do fim do documento
- **Criada em:** 24/09/2026
- **Origem:** enunciado do desafio SPP

## Contexto

Piggie é a stablecoin do Inter. O cliente paga um merchant lendo um QR Code que identifica o merchant e a quantidade de Piggies. O cliente não pode ficar esperando na tela: o app recebe a confirmação de que o pagamento foi aceito e acompanha o resultado depois.

Nesta versão:

- O valor é sempre um número inteiro de Piggies.
- O QR Code chega já decodificado.
- Não existe cancelamento depois da confirmação.

## Histórias de usuário

### US1 — Pagar um merchant ativo com saldo suficiente (P1)

Como cliente, quero pagar um merchant pelo QR Code e acompanhar o pagamento até a confirmação, sem ficar preso numa tela de espera.

**Por que P1:** é o fluxo do enunciado. Sem ele nada funciona.

**Teste independente:** Cliente A, com saldo suficiente, paga 100 Piggies ao Merchant X, que está ativo. O pagamento termina confirmado, com 100 debitados de A e 100 creditados a X.

**Cenários de aceite:**

1. **Dado** que o Cliente A tem 500 Piggies disponíveis e o Merchant X está ativo, **quando** A inicia um pagamento de 100 Piggies para X, **então** a resposta é imediata, traz um identificador de pagamento e o status `PROCESSING`.
2. **Dado** o pagamento do cenário 1, **quando** o processamento termina, **então** consultar o pagamento pelo identificador devolve `CONFIRMED`.
3. **Dado** o pagamento confirmado, **então** o saldo de A é 400, nada fica reservado na conta de A e X tem um recebível de 100 Piggies.
4. **Dado** que a resposta do cenário 1 foi devolvida, **então** ela não esperou a reserva, a validação do merchant nem a confirmação terminarem.

### US2 — Pagamento recusado sem mover dinheiro (P1)

Como cliente, quero saber por que um pagamento não foi concluído e ter certeza de que nenhum Piggie saiu da minha conta.

**Por que P1:** um pagamento que falha e mesmo assim prende ou perde saldo é pior do que não ter pagamento.

**Teste independente:** um pagamento para um merchant inativo termina `FAILED`, e o saldo disponível do cliente volta ao que era antes.

**Cenários de aceite:**

1. **Dado** que A tem 50 Piggies disponíveis, **quando** A tenta pagar 100 a X, **então** o pagamento termina `FAILED` com motivo `INSUFFICIENT_BALANCE`. O saldo de A continua 50 e X não recebe nada.
2. **Dado** que o Merchant Y está inativo, **quando** A tenta pagar 100 a Y, **então** o pagamento termina `FAILED` com motivo `MERCHANT_INACTIVE`. Se a reserva chegou a ser criada, ela é liberada; o saldo disponível de A volta ao valor anterior e nada é debitado.
3. **Dado** um CNPJ que não pertence a nenhum merchant, **quando** A tenta pagar, **então** o pagamento termina `FAILED` com motivo `MERCHANT_NOT_FOUND`, e o saldo de A não muda.
4. **Dado** que a conta do pagador não existe ou não pertence ao CPF informado, **quando** o pagamento é iniciado, **então** ele termina `FAILED` com motivo `PAYER_ACCOUNT_NOT_FOUND`.
5. **Dado** um valor zero, negativo ou fracionário, **quando** o pagamento é iniciado, **então** a solicitação é recusada na hora e nenhum pagamento é registrado.

### US3 — Outros sistemas são avisados do pagamento confirmado (P2)

Como sistema interessado (extrato, antifraude, notificação), quero receber um evento quando um pagamento é confirmado.

**Por que P2:** faz parte do enunciado, mas o pagamento já tem valor para o cliente sem ele.

**Teste independente:** depois de um pagamento confirmado, existe exatamente um evento de pagamento confirmado com os dados do pagamento.

**Cenários de aceite:**

1. **Dado** um pagamento confirmado, **então** é publicado um evento com identificador do pagamento, CNPJ do merchant, valor e momento do crédito.
2. **Dado** um pagamento que terminou `FAILED`, **então** nenhum evento de pagamento confirmado é publicado.

### US4 — Reenvio seguro da mesma solicitação (P2)

Como app, quero poder reenviar um pagamento quando a rede falha, sem risco de cobrar o cliente duas vezes.

**Por que P2:** redes móveis falham e o app vai reenviar. Sem isso, um reenvio vira cobrança dupla.

**Teste independente:** enviar o mesmo pagamento duas vezes resulta em um só pagamento, um só débito e um só crédito.

**Cenários de aceite:**

1. **Dado** um pagamento já iniciado com uma chave, **quando** o app reenvia a mesma solicitação com a mesma chave, **então** recebe o mesmo identificador e o status atual, e não ocorre nova reserva, débito ou crédito.
2. **Dado** uma chave já usada, **quando** o app a reenvia com dados diferentes (outro valor ou outro merchant), **então** a solicitação é recusada como conflito.
3. **Dado** que uma etapa interna (reserva, débito, crédito ou evento) é executada mais de uma vez para o mesmo pagamento, **então** o saldo só muda uma vez.

## Casos de borda

- **Pagamentos simultâneos da mesma conta:** dois pagamentos que, somados, passam do disponível não podem ser ambos reservados. O saldo nunca fica negativo.
- **Falha depois do débito:** se o crédito ao merchant falhar temporariamente depois que o cliente foi debitado, o sistema tenta de novo até concluir. O pagamento continua `PROCESSING` nesse meio-tempo e não volta para `FAILED`, porque não há estorno nesta versão.
- **Consulta de pagamento inexistente:** responde que o pagamento não foi encontrado.
- **Estados finais:** `CONFIRMED` e `FAILED` não mudam mais.

## Requisitos funcionais

**Entrada**

- **FR-001:** O sistema recebe o QR Code decodificado (CNPJ do merchant e valor em Piggies) e a identificação do pagador (CPF, agência e número da conta).
- **FR-002:** O sistema recusa na hora valores que não sejam inteiros maiores que zero, sem registrar pagamento.
- **FR-003:** O sistema registra o pagamento de forma durável e responde imediatamente com o identificador e o status `PROCESSING`, sem esperar as etapas seguintes.
- **FR-004:** O sistema permite consultar o status e o motivo de falha de um pagamento pelo identificador.

**Processamento**

- **FR-005:** Com o pagamento registrado, a reserva do saldo e a validação do merchant acontecem em paralelo.
- **FR-006:** A reserva só é criada se o disponível do pagador (saldo menos o já reservado) cobre o valor. A reserva aumenta o valor reservado e não altera o saldo.
- **FR-007:** Um merchant é válido quando existe e está ativo.
- **FR-008:** Quando a reserva e a validação concluem com sucesso, o sistema confirma o pagamento: debita o valor do saldo do pagador consumindo a reserva, credita o merchant registrando um recebível, marca o pagamento `CONFIRMED` e publica o evento de pagamento confirmado.
- **FR-009:** Se a reserva ou a validação falhar, o pagamento vira `FAILED` com o motivo (`INSUFFICIENT_BALANCE`, `PAYER_ACCOUNT_NOT_FOUND`, `MERCHANT_NOT_FOUND` ou `MERCHANT_INACTIVE`). Uma reserva já criada é liberada, e nada é debitado nem creditado.
- **FR-010:** Não há cancelamento nem estorno de pagamento `CONFIRMED`.

**Consistência**

- **FR-011:** Cada pagamento tem uma chave de idempotência enviada pelo app. A mesma chave com os mesmos dados devolve o pagamento existente; com dados diferentes, é recusada como conflito.
- **FR-012:** Reserva, débito, liberação, crédito e publicação do evento são idempotentes pelo identificador do pagamento.
- **FR-013:** Para todo pagamento `CONFIRMED`, o valor debitado do pagador é igual ao valor creditado ao merchant.

**Evento**

- **FR-014:** O evento de pagamento confirmado tem schema versionado e contém identificador do pagamento, CNPJ do merchant, valor e momento do crédito.

## Entidades (visão de negócio)

- **Cliente:** pessoa física que paga. Nome e CPF, que é único.
- **Merchant:** estabelecimento que recebe. Nome, CNPJ único e situação (ativo ou inativo).
- **Conta:** agência, número e saldo em Piggies. Pertence a um cliente ou a um merchant. A conta do cliente também registra quanto do saldo está reservado.
- **QR Code:** identifica o merchant (CNPJ) e o valor. Chega decodificado e não é armazenado.
- **Pagamento (intenção):** quem paga, quem recebe, valor, status (`PROCESSING`, `CONFIRMED`, `FAILED`) e motivo da falha.
- **Reserva:** valor separado na conta do cliente para um pagamento. Situação: reservada, confirmada (debitada) ou liberada.
- **Recebível:** crédito registrado para o merchant por um pagamento.
- **Evento de pagamento confirmado:** aviso publicado para outros sistemas.

## Restrições do desafio

Estas vêm do enunciado e não são decisões nossas:

- Três serviços: **PiggiesPaymentCoordinator** (borda e orquestração), **PiggiesReserveService** (reserva e débito, persistido em PostgreSQL) e **PiggiesMerchantService** (validação e crédito, recebíveis em PostgreSQL, consome e publica eventos de pagamento via Kafka).
- Contratos OpenAPI por serviço e JSON Schema por evento, antes da implementação.
- Testes unitários e de integração.

## Critérios de sucesso

- **SC-001:** A resposta ao início do pagamento não depende do tempo de reserva, validação ou confirmação.
- **SC-002:** No ambiente de teste de integração, o pagamento de 100 Piggies de A para X chega a `CONFIRMED` em poucos segundos.
- **SC-003:** Nenhum cenário de teste termina com saldo negativo, reserva presa em pagamento finalizado, ou diferença entre total debitado e total creditado.
- **SC-004:** Todos os cenários de aceite desta spec têm teste automatizado passando.

## Fora do escopo

- Valores fracionários.
- Decodificação da imagem do QR Code.
- Cancelamento ou estorno depois da confirmação.
- Autenticação e autorização.
- Cadastro de clientes, merchants e contas via API (entram como dados iniciais).
- Três processos separados em produção (a arquitetura precisa permitir, mas esta versão roda num processo).

## Decisões tomadas nesta spec

O enunciado não resolve estes pontos. Mudar qualquer um altera a spec antes do plano.

1. **Ordem das etapas:** o enunciado diz que registro, reserva e validação acontecem em paralelo. Registramos o pagamento primeiro, porque sem registro não há o que responder ao app nem como garantir idempotência. Reserva e validação rodam em paralelo depois disso.
2. **Identificação do pagador:** CPF, agência e número da conta vêm na solicitação, junto com o QR Code.
3. **Chave reenviada com dados diferentes:** conflito, em vez de devolver o pagamento antigo em silêncio.
4. **Crédito do merchant:** gera um recebível e soma o valor ao saldo da conta do merchant.
5. **Status visíveis ao app:** só `PROCESSING`, `CONFIRMED` e `FAILED`. Etapas internas não aparecem.
