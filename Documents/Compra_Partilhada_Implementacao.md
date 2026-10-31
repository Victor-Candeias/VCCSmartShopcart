# Compra partilhada — estado da implementacao

Branch: `feature/calculadora-compra-partilhada`.

## Implementado

- Entidades Room `shopping_participants` e `shopping_item_assignments`.
- Migracao de base de dados 4 -> 5, sem alterar linhas de compras existentes.
- Repositorio transacional: participantes, atribuicao e validacao de pertença a sessao.
- Calculadora Rapida: adicionar clientes, atribuir linhas e calcular totais individuais.
- Historico de compra: subtotais por cliente.
- CSV e resumo textual: `SharedPurchaseExport` (ainda nao ligado ao menu geral).
- Testes unitarios da logica de distribuicao.

## Integracao ainda pendente

1. Scanner: escolher participante antes da confirmacao e associar a linha criada de forma atomica.
2. Exportacao: adicionar o filtro e a coluna cliente ao pipeline `ReportsRepository`, CSV/JSON/PDF e aos ecras.
3. UI: melhorar a disposicao para sessoes com muitos itens, adicionar remover/editar participante e manter selecao.
4. Room: gerar e versionar o schema 5 com KSP e executar testes de migracao 4 -> 5.
5. Compilar com JDK 17 / Android SDK 37 e executar testes no emulador ou dispositivo.
6. Rever comportamento de sessao finalizada com linhas por atribuir e testes de regressao.

## Regra de integridade

Total global = soma dos totais individuais + valor por atribuir.

Nao finalizar uma compra partilhada com linhas por atribuir. Compras sem participantes continuam com o fluxo anterior.

## Nota de arquitetura

A tabela de atribuicoes separada evita reescrever os itens existentes e preserva as migracoes anteriores. A associacao deve ser validada na mesma transacao para impedir atribuicoes entre sessoes distintas.
