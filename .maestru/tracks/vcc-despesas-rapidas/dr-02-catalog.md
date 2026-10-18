---
maestru: "0.4"
type: work-item
id: dr-02-catalog
title: "Catálogo de despesas: seed JSON sem duplicados e ecrã de gestão"
created: 2026-10-18
owner: developer
priority: high
status: done
completed: 2026-10-18
specs:
  - dr-02-catalog-spec
track: vcc-despesas-rapidas
blocked-by:
  - dr-01-room-model
---

# dr-02-catalog: Catálogo de despesas: seed JSON sem duplicados e ecrã de gestão

Fonte: §3, §4.3 e §10 (etapa 2).

## Âmbito

- `assets/expense_catalog.json` com as categorias Café e Pastelaria, Restauração, Transportes e Outras despesas e produtos sugeridos, com IDs estáveis.
- `ExpenseCatalogSeeder`: importação na primeira execução, `INSERT OR IGNORE` por ID estável e versão do seed em DataStore — nunca duplica nem sobrescreve alterações do utilizador.
- Casos de utilização do catálogo: `GetExpenseCatalogUseCase`, guardar produto (criar/editar), favorito, ocultar/mostrar e reordenar.
- `ExpenseCatalogScreen`: listagem por categoria, pesquisa, favoritos, ocultos, ordenação (subir/descer) e diálogo de criação/edição.

## Saída verificável

Produtos predefinidos aparecem sem duplicação após reimportação; testes do seeder e dos casos de utilização a passar.
