# Constituição do SPP

Princípios que toda spec, plano e implementação do Sistema de Pagamento com Porquinhos seguem. Um plano que viole um princípio precisa justificar a exceção por escrito no próprio plano.

## I. Contract-first

Todo serviço tem um contrato OpenAPI e todo evento publicado tem um JSON Schema. O contrato é escrito e revisado antes da implementação. O código é gerado a partir do contrato ou testado contra ele; o contrato é a fonte de verdade.

## II. Borda assíncrona

O cliente nunca espera o processamento terminar. O serviço de borda registra a solicitação, responde `202 Accepted` com um identificador e processa depois. O resultado é consultado pelo identificador.

## III. Monólito modular, pronto para separar

Os três serviços (`coordinator`, `reserve`, `merchant`) vivem no mesmo processo nesta versão, mas como módulos isolados:

- Cada módulo é dono dos seus dados, num schema próprio do PostgreSQL. Não há chave estrangeira nem consulta entre schemas.
- Um módulo não importa entidades, repositórios ou classes internas de outro. A comunicação passa só pela API pública do módulo, que espelha o contrato dele.
- Extrair um módulo para um processo separado deve exigir apenas trocar o adaptador de chamada local por um cliente HTTP ou Kafka.

## IV. Idempotência

Toda operação que move saldo ou cria registro é idempotente pela chave do pagamento. Repetir uma chamada ou reprocessar um evento nunca debita, credita ou reserva duas vezes.

## V. Testes obrigatórios

- Regras de negócio têm testes unitários.
- Persistência, mensageria e a jornada completa têm testes de integração com Testcontainers (PostgreSQL e Kafka).
- Todo cenário de aceite de uma spec tem pelo menos um teste automatizado que o prova.

## VI. Entregas incrementais e simples

Entrega-se em fatias verticais: cada fatia funciona de ponta a ponta e pode ser demonstrada sozinha. Uma entrega parcial que funciona vale mais que uma completa que não roda. Não se cria abstração antes de haver o segundo uso.

## VII. Rastreabilidade de IA

Prompts, pesquisas e referências usados com IA são registrados em `docs/ai/` e commitados. Todo código gerado precisa ser entendido e defensável por quem o commitou.

## Governança

Esta constituição prevalece sobre specs e planos. Mudá-la exige registrar o motivo e revisar as specs afetadas.
