# Specs — SPP

Este projeto segue Spec-Driven Development. Nenhum código entra sem uma spec aprovada que o justifique.

## Fluxo

1. **Constituição** (`constitution.md`): princípios que valem para toda funcionalidade. Muda raramente.
2. **Especificação** (`NNN-nome/spec.md`): o quê e por quê. Histórias, cenários de aceite, requisitos. Sem tecnologia.
3. **Plano** (`NNN-nome/plan.md`, `data-model.md`, `contracts/`): como. Arquitetura, modelo de dados, OpenAPI por serviço e JSON Schema por evento.
4. **Tarefas** (`NNN-nome/tasks.md`): fatias verticais derivadas do plano, cada uma testável sozinha.
5. **Implementação**: código e testes que cumprem as tarefas. Cada cenário de aceite da spec vira pelo menos um teste.

Cada etapa só começa depois da revisão da anterior. Se a implementação revelar que a spec está errada, corrige-se a spec primeiro.

## Funcionalidades

| Id | Nome | Estado |
|---|---|---|
| 001 | [Pagamento com Piggies via QR Code](001-pagamento-piggies/spec.md) | [Plano](001-pagamento-piggies/plan.md) e [contratos](001-pagamento-piggies/contracts/) aprovados; [tarefas](001-pagamento-piggies/tasks.md) prontas |
