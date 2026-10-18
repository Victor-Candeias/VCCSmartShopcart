# CestoSmart — Novo modo: Despesas Rápidas

**Documento de especificação funcional e técnica**  
**Idioma:** Português (Portugal)  
**Objetivo:** acrescentar um terceiro modo à aplicação Android para registar consumos e pequenas despesas com seleção de produtos predefinidos e introdução rápida do preço.

## 1. Contexto e objetivo

A CestoSmart dispõe de dois modos previstos:

1. **Calculadora Rápida** — registar preços durante as compras e calcular o total.
2. **Lista Planeada** — preparar uma lista antes de ir às compras, assinalar artigos e registar os preços reais.

Pretende-se acrescentar um terceiro modo, **Despesas Rápidas**, para situações como ir a um café e consumir **um café e uma bola de Berlim**, selecionando ambos de um catálogo, introduzindo ou confirmando os preços e guardando a despesa.

Este modo não depende de código de barras, Open Food Facts ou Open Prices. Deve funcionar offline.

## 2. Experiência de utilização

### 2.1 Ecrã inicial

Mostrar três opções:

| Modo | Função |
|---|---|
| Calculadora Rápida | Introdução rápida de preços e soma |
| Lista Planeada | Planeamento e execução de compras |
| **Despesas Rápidas** | Seleção de produtos/serviços habituais e registo de despesas |

### 2.2 Fluxo principal

1. Abrir **Despesas Rápidas**.
2. Selecionar uma categoria, por exemplo **Café e Pastelaria**.
3. Escolher produtos do catálogo: **Café** e **Bola de Berlim**.
4. A aplicação sugere um preço habitual, se existir; o utilizador pode alterá-lo.
5. Ajustar quantidades.
6. Indicar opcionalmente o estabelecimento (por exemplo, Café Central).
7. Confirmar e guardar a despesa.
8. Consultar ou repetir a despesa no histórico.

### 2.3 Exemplo de registo

**Estabelecimento:** Café Central  
**Categoria:** Café e Pastelaria

| Produto | Quantidade | Preço unitário | Subtotal |
|---|---:|---:|---:|
| Café | 1 | 0,85 € | 0,85 € |
| Bola de Berlim | 1 | 1,50 € | 1,50 € |
| **Total** | | | **2,35 €** |

*Os preços são meramente ilustrativos.*

### 2.4 Tratamento de casos especiais

- Produto não existente: criar um produto personalizado sem sair do fluxo.
- Produto sem preço habitual: solicitar o preço.
- Produto com preço habitual: preencher automaticamente, permitindo correção.
- Vários exemplares: ajustar quantidade e recalcular o subtotal.
- Estabelecimento desconhecido: permitir guardar sem estabelecimento.
- Erro no preço: permitir edição antes e depois de guardar.
- Despesa interrompida: preservar um rascunho local enquanto a edição estiver em curso.

## 3. Catálogo predefinido

O catálogo deve ser **editável** e independente das listas de compras. Sugestões iniciais:

| Categoria | Exemplos |
|---|---|
| Café e Pastelaria | Café, descafeinado, galão, meia de leite, pastel de nata, bola de Berlim, sandes, água |
| Restauração | Sopa, prato do dia, menu, sobremesa, bebida |
| Transportes | Estacionamento, portagem, bilhete, táxi |
| Outras despesas | Produtos e serviços definidos pelo utilizador |

Cada produto tem nome, categoria, ícone opcional, preço habitual opcional, ordem de apresentação e indicador de favorito. O utilizador pode criar, editar, ocultar e reordenar produtos.

O catálogo inicial pode ser carregado a partir de um **seed JSON local** na primeira execução ou numa migração controlada. Não devem ser recriados itens personalizados nem sobrescritas alterações do utilizador.

## 4. Ecrãs propostos

### 4.1 DespesasHomeScreen

- Categorias e favoritos.
- Ação **Nova despesa**.
- Acesso a **Histórico** e **Gerir catálogo**.

### 4.2 ExpenseEditorScreen

- Campo de estabelecimento opcional.
- Data/hora predefinidas para o momento atual, editáveis.
- Produtos escolhidos, quantidade, preço unitário e subtotal.
- Pesquisa e botão **Adicionar produto**.
- Total sempre visível.
- Botões **Guardar** e **Cancelar**.

### 4.3 ExpenseCatalogScreen

- Listagem por categoria.
- Pesquisa, favoritos e ordenação.
- Criação e edição de produtos.

### 4.4 ExpenseHistoryScreen

- Despesas por data, estabelecimento e categoria.
- Total por registo.
- Ações **Ver**, **Editar**, **Eliminar** e **Repetir despesa**.

## 5. Modelo de dados

### 5.1 Entidades

| Entidade | Campos essenciais | Responsabilidade |
|---|---|---|
| `ExpenseCategory` | `id`, `name`, `icon`, `sortOrder`, `isActive` | Organização do catálogo |
| `ExpenseProduct` | `id`, `categoryId`, `name`, `defaultPriceCents?`, `isFavorite`, `isActive`, `sortOrder` | Produto/serviço reutilizável |
| `Expense` | `id`, `establishmentId?`, `occurredAt`, `totalCents`, `notes?`, `createdAt`, `updatedAt` | Cabeçalho de despesa |
| `ExpenseItem` | `id`, `expenseId`, `productId?`, `nameSnapshot`, `quantity`, `unitPriceCents`, `totalCents` | Itens efetivamente consumidos |
| `Establishment` | `id`, `name`, `type?`, `isFavorite` | Local de consumo opcional |

### 5.2 Regras de integridade

- Identificadores locais podem ser UUID.
- Guardar valores monetários em **cêntimos (`Long`)**, nunca `Double`.
- Quantidades podem ser inteiras no MVP; caso sejam necessárias frações, usar representação decimal exata.
- `ExpenseItem.nameSnapshot` e `unitPriceCents` preservam os valores históricos, mesmo que o catálogo seja alterado.
- `Expense.totalCents` é calculado a partir dos itens e atualizado na mesma transação Room.
- Produtos do catálogo devem poder ser desativados sem eliminar despesas antigas.
- Não permitir preços negativos nem quantidades nulas/negativas.
- Datas e horas devem ser armazenadas de forma consistente, preferencialmente como instantes UTC, apresentadas no fuso local.

### 5.3 Exemplo de modelo Kotlin

```kotlin
data class ExpenseDraftItem(
    val productId: String?,
    val name: String,
    val quantity: Int,
    val unitPriceCents: Long
) {
    val totalCents: Long
        get() = Math.multiplyExact(quantity.toLong(), unitPriceCents)
}

data class ExpenseDraft(
    val establishmentId: String?,
    val items: List<ExpenseDraftItem>,
    val notes: String? = null
) {
    val totalCents: Long
        get() = items.fold(0L) { total, item ->
            Math.addExact(total, item.totalCents)
        }
}
```

A validação de quantidades e preços deve ser realizada nos casos de utilização antes de persistir.

## 6. Arquitetura Android

Reutilizar a arquitetura já prevista para a CestoSmart:

- **Kotlin + Jetpack Compose / Material 3** para interface.
- **MVVM + StateFlow** para gestão de estado.
- **Room** para persistência offline.
- **Hilt** para injeção de dependências.
- **Coroutines** para operações assíncronas.
- **Navigation Compose** para navegação.

Estrutura sugerida:

```text
pt.vcc.cestosmart/
├── domain/
│   ├── model/expense/
│   ├── repository/ExpenseRepository.kt
│   └── usecase/expense/
├── data/
│   ├── local/entity/expense/
│   ├── local/dao/ExpenseDao.kt
│   ├── repository/ExpenseRepositoryImpl.kt
│   └── seed/expense_catalog.json
└── feature/expenses/
    ├── home/
    ├── editor/
    ├── catalog/
    └── history/
```

### 6.1 Contrato do repositório

```kotlin
interface ExpenseRepository {
    fun observeCategories(): Flow<List<ExpenseCategory>>
    fun observeProducts(categoryId: String?): Flow<List<ExpenseProduct>>
    fun observeExpenses(): Flow<List<Expense>>
    suspend fun saveExpense(draft: ExpenseDraft): String
    suspend fun getExpense(id: String): Expense?
    suspend fun deleteExpense(id: String)
}
```

A implementação de `saveExpense` deve guardar cabeçalho e itens numa única transação `RoomDatabase.withTransaction`.

### 6.2 Casos de utilização

- `GetExpenseCatalogUseCase`
- `CreateExpenseUseCase`
- `UpdateExpenseUseCase`
- `DeleteExpenseUseCase`
- `RepeatExpenseUseCase`
- `SuggestProductPriceUseCase`
- `CalculateExpenseTotalUseCase`

## 7. Sugestão inteligente de preços

Ao selecionar um produto, sugerir um preço seguindo esta ordem:

1. Último preço pago **no mesmo estabelecimento** para o mesmo produto.
2. Último preço pago pelo utilizador para esse produto, independentemente do estabelecimento.
3. Preço habitual configurado no catálogo.
4. Campo vazio para introdução manual.

A sugestão **nunca deve ser considerada preço atual garantido**. O utilizador confirma o preço antes de guardar.

Para permitir a regra por estabelecimento, consultar `ExpenseItem` associado a `Expense`, ordenando pela data da despesa. Não é necessário integrar APIs externas.

## 8. Integração com os modos existentes

```text
CestoSmart
├── Calculadora Rápida
│   └── ShoppingSession
├── Lista Planeada
│   └── ShoppingList + ShoppingSession
└── Despesas Rápidas
    ├── ExpenseCategory
    ├── ExpenseProduct
    ├── Expense
    ├── ExpenseItem
    └── Establishment
```

Reutilizar componentes de teclado monetário, formatação EUR, cálculo de subtotais, histórico e design visual. **Não misturar** o catálogo de despesas com os produtos identificados por EAN/GTIN, pois representam conceitos diferentes.

## 9. MVP: incluído e adiado

### Incluído na primeira versão

- Terceiro modo na página inicial.
- Catálogo predefinido por categorias, com produtos personalizáveis.
- Seleção rápida de vários produtos.
- Preço sugerido e editável.
- Quantidade, subtotal e total automático.
- Estabelecimento opcional.
- Guardar, consultar, editar, eliminar e repetir despesas.
- Funcionamento offline.

### Adiado para versões futuras

- Estatísticas e gráficos mensais.
- Orçamentos por categoria.
- Exportação CSV/PDF.
- Despesas recorrentes e lembretes.
- Partilha familiar/sincronização.
- OCR de talões para despesas.
- Comparação de preços entre estabelecimentos.

## 10. Plano de implementação

| Etapa | Trabalho | Critério de conclusão |
|---|---|---|
| 1 | Entidades, DAO e migração Room | Catálogo e despesas persistem corretamente |
| 2 | Seed inicial e gestão do catálogo | Produtos predefinidos aparecem sem duplicação |
| 3 | Editor de despesa | Seleção, quantidades e total funcionam |
| 4 | Histórico e repetição | Registos podem ser consultados, editados e repetidos |
| 5 | Sugestões de preços | Prioridade por estabelecimento e último consumo correta |
| 6 | Testes e polimento | Fluxo offline estável e acessível |

## 11. Testes e critérios de aceitação

- [ ] Registar **1 café + 1 bola de Berlim** com dois preços diferentes.
- [ ] Calcular corretamente **0,85 € + 1,50 € = 2,35 €**.
- [ ] Alterar quantidade e atualizar o total imediatamente.
- [ ] Criar um produto personalizado sem perder o rascunho.
- [ ] Guardar sem estabelecimento.
- [ ] Editar preço habitual do catálogo sem alterar despesas anteriores.
- [ ] Repetir despesa e permitir corrigir preços sugeridos.
- [ ] Funcionar sem Internet.
- [ ] Recuperar o histórico após reiniciar a aplicação.
- [ ] Evitar duplicados na importação do catálogo predefinido.
- [ ] Confirmar que valores inválidos não são guardados.
- [ ] Confirmar acessibilidade de controlos e formatação `pt-PT` em euros.

## 12. Recomendação final

**Despesas Rápidas** deve ser um terceiro modo independente, orientado para a rapidez de registo de consumos do quotidiano. Um catálogo personalizável, a sugestão do último preço e o histórico local são suficientes para uma primeira versão útil, sem dependência de serviços externos.

A solução reutiliza a infraestrutura Kotlin/Compose/Room já planeada e permite evoluir posteriormente para gestão de despesas mais completa.
