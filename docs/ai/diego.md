# Prompts do Diego

Todos os prompts enviados ao assistente de IA (Cursor) na conversa do dia 24/09/2026, na ordem e no texto
original, sem correções. As escolhas feitas em perguntas de múltipla escolha aparecem como "Escolha". O resumo de
cada resposta está em [`2026-09-24-reinicio-sdd.md`](2026-09-24-reinicio-sdd.md).

## 1

````text
vamos planejar o sistema seguindo as instrução abaixo, iremos iniciar da maneira mais simples e depois vamos realizando melhorias caso necessáio. Precisaremos criar 3 serviços, para isso vamos criar 3 componentes separados um com se fosse um monolito modular, para que depois fique fácil extrair cada um para um serviço separado.

## SPP — Sistema de Pagamento com Porquinhos

No Inter trabalhamos com `contract-first`. O primeiro passo de qualquer entrega é
definir os contratos entre os microserviços usando OpenAPI ou JSON Schema — um contrato
por serviço, definido antes da implementação. Eventos publicados (ex.: Kafka) também
devem ter schema.

Os clientes do Inter não gostam de ficar olhando para uma ampulheta na tela, por
isso todo serviço de borda precisa trabalhar de forma assíncrona.

Testes unitários e de integração são obrigatórios.

Planejem as entregas. Tempo é um recurso limitado e a gestão do tempo é parte do desafio.

Trabalhem em equipe. Vocês não estão competindo entre si. Vocês vencem juntos ou perdem juntos.

Uma entrega parcial que faz alguma coisa vale mais do que um monte de código que não
realiza nada.

Vocês podem usar IA, consultar a internet e usar qualquer outra ferramenta à sua
disposição, desde que tudo seja anexado ao projeto e comitado (pesquisas, prompts,
códigos de referência, etc.).

Contudo, usem com responsabilidade:
- Vocês precisarão resolver bugs e responder questões sobre a implementação. Responder
  “Não sei, a IA que fez assim” é critério de eliminação.
- Fiquem atentos quanto a propriedade intelectual e nunca utilizem códigos ou documentos
  que vocês não estão autorizados a publicar.

Piggie é a nova stablecoin do Inter. O desafio é implementar o Sistema de Pagamento com Porquinhos (SPP).

Um pagamento envolve algumas etapas:

1. Cliente inicia um pagamento por meio de um QR Code que identifica o merchant e a quantidade de Piggies
2. Em paralelo, a intenção de pagamento é registrada, o saldo do cliente é reservado e o merchant é validado
3. Quando as três operações concluem com sucesso, o pagamento é confirmado: Piggies são debitados do cliente, creditados ao merchant e o evento é publicado para outros sistemas

Exemplo:
- Cliente A inicia pagamento de 100 Piggies ao Merchant X
- Intenção de pagamento é registrada
- Reserva de 100 Piggies é criada na conta do Cliente A
- Merchant X é validado como ativo
- Pagamento é confirmado: 100 Piggies debitados de A, 100 creditados a X

**IMPORTANTE** nessa dinâmica vamos implementar uma versão simplificada do sistema:
- Pagamentos são sempre por um valor inteiro de Piggies
- A API já vai receber o QR Code decodificado
- Não há cancelamento após confirmação

Vocês devem implementar 3 serviços:

- **PiggiesPaymentCoordinator** é o ponto de entrada do App, registra a intenção de pagamento e orquestra a confirmação de ponta a ponta.
- **PiggiesReserveService** reserva e confirma o débito do cliente. Persiste o estado da reserva em PostgreSQL.
- **PiggiesMerchantService** valida o merchant e registra o crédito. Persiste recebíveis em PostgreSQL e consome/publica eventos de pagamento via Kafka.

 Cliente(nome, cpf, Conta)
 Conta(numero, agencia, Client/Merchant, numero da conta, saldo)
 QRcode
 Merchant(nome, cnpj Conta)
````

## 2

> estamos numa branch nova, pode excluir tudo da nova branch e recomeçar novamente nessa branch, descarte o que já foi feito, e ignore o tasks.md, vamos utilizar a abordagem do SDD, como se estivéssmo iniciando do zero

## 3

> siga para a próxima etapa

## 4

> vamos seguir com o plano 11 para garnharmos tempo no desenolvimento depois podemos olhar essa questão da openapi

## 5

> sim

## 6

> sim

## 7

> o teste demorou demais acho que alguma coisa travou

## 8

> valide qual o proximo passo a seguir

Escolha: fechar a S1 com a demo por `curl`, limpar e commitar S0 e S1, depois iniciar a S2.

## 9

Escolha: commitar a S2 e iniciar a S3.

## 10

Escolha: commitar a S3 e fazer só o README (T053).

## 11

> como eu faço para testar manualmente esses serviços?

## 12

> esse comando está retornando um erro docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh
> console-producer.sh: no such file or directory

## 13

> sim

## 14

> siga para o S4

## 15

> vamos dividir o s4 de outra forma, crie somente as apis HTTP do reserve e merchant, não crie o job

## 16

> me responda como testar e em português

## 17

> adicione todo prompt que conversamos ao arquivo diego.md
