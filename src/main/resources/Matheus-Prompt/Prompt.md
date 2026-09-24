Preciso que crie as configurações de produtor e consumidor do kafka envolvendo  o cenário descrito abaixo
Eventos e jornada completa
O merchant consome e publica eventos de pagamento. O teste de ponta a ponta prova o cenário do enunciado.

Kafka
Producer de spp.payment.confirm (disparado pelo coordinator quando reserva e validação passaram)
Consumer de spp.payment.confirm no merchant: credita de forma idempotente
Producer de spp.payment.confirmed depois do crédito
Coordenar a leitura de spp.payment.confirmed para o coordinator fechar a intenção em CONFIRMED
Teste de integração
Cliente A paga 100 Piggies ao Merchant X
Intenção termina CONFIRMED
Conta de A: balance reduzido em 100 e reservedBalance de volta ao valor anterior
Recebível de X no valor de 100
Mensagem em spp.payment.confirmed com paymentId, merchantCnpj, amount e creditedAt
Merchant inativo: intenção FAILED, reserva RELEASED, nenhum recebível e nenhum débito 