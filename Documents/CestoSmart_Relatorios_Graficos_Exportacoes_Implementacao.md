# CestoSmart — Implementação de Relatórios, Gráficos e Exportações

**Versão:** 1.0 · **Data:** 2026-10-19 · **Plataforma:** Android nativo (Kotlin, Jetpack Compose, Room, Hilt)

## 1. Objetivo e âmbito

Criar um quarto módulo **Relatórios** que analise dados persistidos nos três modos existentes:

- **Calculadora Rápida:** sessões concluídas e respetivos itens.
- **Lista Planeada:** compras efetivamente concluídas; as listas ainda não executadas são planeamento, não despesa.
- **Despesas Rápidas:** despesas guardadas, com estabelecimento, categoria e itens.

Permitir filtros, indicadores, gráficos e exportações **CSV, JSON, PDF** no MVP. Preparar arquitetura para **XLSX, HTML e TXT** numa fase seguinte. Tudo deve funcionar **offline**, sem backend, e com preços em cêntimos (`Long`).

### Regras de negócio inegociáveis

1. Contabilizar apenas sessões `COMPLETED` e despesas confirmadas; não somar listas planeadas ou sessões canceladas.
2. Contar uma transação uma única vez, mesmo que tenha vários itens; agregar totais de itens por transação.
3. Guardar preços e nomes históricos nos itens (*snapshots*), para alterações posteriores ao catálogo não modificarem relatórios antigos.
4. Filtrar datas segundo o fuso horário local do utilizador (`ZoneId.systemDefault()`); intervalo `[início, fim)` com limite superior exclusivo.
5. Não misturar valores de orçamento, preços sugeridos, preços Open Prices e despesa efetivamente paga.
6. Valores monetários internos em `Long` (cêntimos); quantidades em `BigDecimal`, serializadas como texto.
7. Indicar claramente se os dados estão incompletos (por exemplo, sessões sem itens ou migração parcial).

## 2. Funcionalidades e ecrãs

| Ecrã | Função | MVP |
|---|---|---|
| `ReportsHomeScreen` | KPI: total, transações, média, variação temporal | Sim |
| `ReportsFiltersSheet` | Datas, modo, categoria, estabelecimento, produto | Sim |
| `ExpensesByCategoryScreen` | Barras e distribuição por categoria | Sim |
| `ExpensesTimelineScreen` | Evolução diária/semanal/mensal | Sim |
| `StoresReportScreen` | Totais por estabelecimento | Sim |
| `ProductsReportScreen` | Produtos frequentes e despesa acumulada | Sim |
| `BudgetReportScreen` | Orçamento vs. realizado por sessão/lista | Sim |
| `PriceHistoryScreen` | Histórico de preços próprios, separado dos externos | Fase 2 |
| `ExportReportSheet` | Formato, granularidade, nome e destino | Sim |

Filtros rápidos: **Hoje, 7 dias, Este mês, Mês anterior, Este ano, Personalizado**. Permitir alternar entre visão global e um modo específico.

### Gráficos recomendados

- **Linha:** evolução diária/semanal/mensal do total gasto.
- **Barras horizontais:** top 10 categorias, produtos e estabelecimentos.
- **Donut:** proporção de despesas por categoria (com legenda e percentagens; evitar demasiadas fatias).
- **Barras agrupadas:** orçamento vs. realizado.

**Biblioteca:** Vico (Compose) ou outra biblioteca de gráficos compatível com a versão de Compose do projeto. Validar a API e as versões atuais antes de adicionar a dependência. Para o MVP, gráficos simples também podem ser implementados em `Canvas` Compose. Não acoplar o modelo de relatório à biblioteca gráfica.

## 3. Estrutura do projeto

```text
pt.vcc.cestosmart/
  feature/reports/
    ReportsHomeScreen.kt
    ReportsViewModel.kt
    ReportsFiltersSheet.kt
    ExportReportSheet.kt
    charts/ExpensesLineChart.kt
    charts/CategoryBarChart.kt
  domain/reports/
    ReportFilter.kt
    ReportRow.kt
    ReportSummary.kt
    ReportData.kt
    ReportsRepository.kt
    GenerateReportUseCase.kt
  data/reports/
    ReportsRepositoryImpl.kt
    ReportsDao.kt
    ReportRowMapper.kt
    export/
      ReportExporter.kt
      CsvReportExporter.kt
      JsonReportExporter.kt
      PdfReportExporter.kt
      ExportCoordinator.kt
      ExportFormat.kt
  di/ReportsModule.kt
```

## 4. Modelo de domínio

```kotlin
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

enum class ReportMode { CALCULATOR, PLANNED_LIST, EXPENSE }

data class ReportFilter(
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val modes: Set<ReportMode> = ReportMode.entries.toSet(),
    val categoryIds: Set<String> = emptySet(),
    val establishmentIds: Set<String> = emptySet(),
    val productIds: Set<String> = emptySet()
)

data class ReportRow(
    val transactionId: String,
    val itemId: String,
    val occurredAt: Instant,
    val mode: ReportMode,
    val establishmentId: String?,
    val establishmentName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val productId: String?,
    val productName: String,
    val quantity: BigDecimal,
    val unitPriceCents: Long,
    val discountCents: Long,
    val totalCents: Long
)

data class ReportSummary(
    val totalCents: Long,
    val transactionCount: Int,
    val averageTransactionCents: Long,
    val itemCount: Int
)

data class ReportData(
    val schemaVersion: Int = 1,
    val filter: ReportFilter,
    val rows: List<ReportRow>,
    val summary: ReportSummary,
    val generatedAt: Instant
)
```

**Nota:** `totalCents` é o total final do item, após descontos. O valor de uma transação é a soma dos seus itens. Se houver ajustes ao nível da transação (cupões globais, arredondamentos), representá-los como linhas de ajuste ou distribuí-los de forma determinística para não perder reconciliação.

## 5. Consultas Room: unificar três fontes

É preferível manter as tabelas existentes e normalizar as linhas numa camada de leitura. Os nomes das tabelas/colunas abaixo são **exemplificativos**; adaptar ao esquema Room real.

```kotlin
// Projeção normalizada, usada pelo ReportsRepository.
data class ReportRowDb(
    val transactionId: String,
    val itemId: String,
    val occurredAtMillis: Long,
    val mode: String,
    val establishmentId: String?,
    val establishmentName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val productId: String?,
    val productName: String,
    val quantityText: String,
    val unitPriceCents: Long,
    val discountCents: Long,
    val totalCents: Long
)
```

Exemplo de consulta para despesas rápidas:

```kotlin
@Dao
interface ReportsDao {
    @Query("""
        SELECT
            e.id AS transactionId,
            i.id AS itemId,
            e.dateMillis AS occurredAtMillis,
            'EXPENSE' AS mode,
            e.establishmentId AS establishmentId,
            est.name AS establishmentName,
            p.categoryId AS categoryId,
            c.name AS categoryName,
            i.productId AS productId,
            i.nameSnapshot AS productName,
            i.quantityText AS quantityText,
            i.unitPriceCents AS unitPriceCents,
            0 AS discountCents,
            i.totalCents AS totalCents
        FROM expenses e
        JOIN expense_items i ON i.expenseId = e.id
        LEFT JOIN establishments est ON est.id = e.establishmentId
        LEFT JOIN expense_products p ON p.id = i.productId
        LEFT JOIN expense_categories c ON c.id = p.categoryId
        WHERE e.dateMillis >= :fromMillis AND e.dateMillis < :untilMillis
        ORDER BY e.dateMillis DESC, i.id
    """)
    suspend fun getExpenseRows(fromMillis: Long, untilMillis: Long): List<ReportRowDb>
}
```

**Importante:** se uma categoria ou produto for apagado, os `LEFT JOIN` não devem fazer desaparecer o item. Idealmente `categoryName` também é guardado como snapshot em `ExpenseItem`. Criar consultas equivalentes para `ShoppingSession` e `ShoppingSessionItem`, com filtro `status = 'COMPLETED'` e mapeamento `CALCULATOR`/`PLANNED_LIST`.

### Intervalo de datas

```kotlin
fun ReportFilter.toEpochRange(zone: ZoneId): Pair<Long, Long> {
    require(!endDateInclusive.isBefore(startDate))
    val from = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val until = endDateInclusive.plusDays(1)
        .atStartOfDay(zone).toInstant().toEpochMilli()
    return from to until
}
```

### Repositório

```kotlin
interface ReportsRepository {
    suspend fun loadRows(filter: ReportFilter): List<ReportRow>
}

class ReportsRepositoryImpl @Inject constructor(
    private val dao: ReportsDao
) : ReportsRepository {
    override suspend fun loadRows(filter: ReportFilter): List<ReportRow> =
        withContext(Dispatchers.IO) {
            val (from, until) = filter.toEpochRange(ZoneId.systemDefault())
            val rows = buildList {
                if (ReportMode.EXPENSE in filter.modes)
                    addAll(dao.getExpenseRows(from, until).map { it.toDomain() })
                // Acrescentar getCompletedShoppingRows(...) com os modos restantes.
            }
            rows.filter { row ->
                (filter.categoryIds.isEmpty() || row.categoryId in filter.categoryIds) &&
                (filter.establishmentIds.isEmpty() || row.establishmentId in filter.establishmentIds) &&
                (filter.productIds.isEmpty() || row.productId in filter.productIds)
            }
        }
}
```

Para históricos volumosos, migrar os filtros e agregações para SQL em vez de carregar todos os itens em memória. Índices recomendados: datas/estado das transações, FK dos itens, estabelecimento e categoria.

## 6. Agregação e KPIs

```kotlin
class GenerateReportUseCase @Inject constructor(
    private val repository: ReportsRepository,
    private val clock: Clock
) {
    suspend operator fun invoke(filter: ReportFilter): ReportData {
        val rows = repository.loadRows(filter)
        val total = rows.sumOf { it.totalCents }
        val transactions = rows.map { it.transactionId to it.mode }.distinct().size
        val average = if (transactions == 0) 0L else total / transactions
        return ReportData(
            filter = filter,
            rows = rows,
            summary = ReportSummary(total, transactions, average, rows.size),
            generatedAt = clock.instant()
        )
    }
}
```

**Cuidado com IDs:** usar `(mode, transactionId)` evita colisões entre tabelas diferentes. O indicador `itemCount` representa linhas; se quiseres unidades físicas, somar `quantity` separadamente.

### Séries para gráficos

```kotlin
data class ChartPoint(val label: String, val amountCents: Long)

fun ReportData.byCategory(): List<ChartPoint> = rows
    .groupBy { it.categoryName ?: "Sem categoria" }
    .map { (name, items) -> ChartPoint(name, items.sumOf { it.totalCents }) }
    .sortedByDescending { it.amountCents }

fun ReportData.byDay(zone: ZoneId): List<ChartPoint> = rows
    .groupBy { it.occurredAt.atZone(zone).toLocalDate() }
    .toSortedMap()
    .map { (date, items) -> ChartPoint(date.toString(), items.sumOf { it.totalCents }) }
```

Para uma linha temporal contínua, preencher dias sem movimentos com zero. Comparações de períodos devem usar janelas equivalentes e assinalar explicitamente quando o período atual ainda está incompleto.

## 7. ViewModel e Compose

```kotlin
sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data class Ready(val report: ReportData) : ReportsUiState
    data class Error(val message: String) : ReportsUiState
}

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val generateReport: GenerateReportUseCase
) : ViewModel() {
    private val _state = MutableStateFlow<ReportsUiState>(ReportsUiState.Loading)
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    fun refresh(filter: ReportFilter) {
        viewModelScope.launch {
            _state.value = ReportsUiState.Loading
            try {
                _state.value = ReportsUiState.Ready(generateReport(filter))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (ex: Exception) {
                _state.value = ReportsUiState.Error("Não foi possível gerar o relatório.")
            }
        }
    }
}
```

No `ReportsHomeScreen`, apresentar cartões KPI e gráficos alimentados pelo mesmo `ReportData`. Usar `collectAsStateWithLifecycle()` e `remember`/`derivedStateOf` para agregações de apresentação. Evitar gerar ficheiros ou consultas pesadas na thread principal.

## 8. Exportadores: contrato extensível

```kotlin
enum class ExportFormat(val extension: String, val mimeType: String) {
    CSV("csv", "text/csv"),
    JSON("json", "application/json"),
    PDF("pdf", "application/pdf"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    HTML("html", "text/html"),
    TXT("txt", "text/plain")
}

interface ReportExporter {
    val format: ExportFormat
    suspend fun write(report: ReportData, output: OutputStream)
}
```

A UI escolhe o formato, mas não conhece a implementação concreta. Um `ExportCoordinator` seleciona o exportador através de um mapa `ExportFormat -> ReportExporter`, gere `Dispatchers.IO`, erros e cancelamento.

## 9. CSV — implementação

Para Portugal, usar `;` como delimitador, decimal `,`, UTF-8 com BOM e cabeçalhos claros. Nunca escrever strings sem *escaping*.

```kotlin
class CsvReportExporter @Inject constructor() : ReportExporter {
    override val format = ExportFormat.CSV

    override suspend fun write(report: ReportData, output: OutputStream) =
        withContext(Dispatchers.IO) {
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(output, Charsets.UTF_8).buffered().use { w ->
                w.appendLine("Data;Modo;Estabelecimento;Categoria;Produto;Quantidade;Preço unitário;Total")
                report.rows.forEach { r ->
                    val cells = listOf(
                        r.occurredAt.toString(), r.mode.name,
                        r.establishmentName.orEmpty(), r.categoryName.orEmpty(),
                        r.productName, r.quantity.toPlainString().replace('.', ','),
                        centsPt(r.unitPriceCents), centsPt(r.totalCents)
                    )
                    w.appendLine(cells.joinToString(";") { csvCell(it) })
                }
            }
        }
}

fun centsPt(cents: Long): String =
    BigDecimal.valueOf(cents, 2).setScale(2).toPlainString().replace('.', ',')

fun csvCell(raw: String): String {
    // Evita interpretação como fórmula por Excel/Sheets.
    val safe = if (raw.trimStart().firstOrNull() in listOf('=', '+', '-', '@'))
        "'" + raw else raw
    return "\"" + safe.replace("\"", "\"\"") + "\""
}
```

**Observação:** o escape acima é uma política conservadora para texto não fiável. Para preservar números negativos legítimos, tratar colunas numéricas de forma tipada em vez de aplicar a mesma regra a todas as células. A data no CSV deve ser formatada no fuso escolhido pelo utilizador (o exemplo usa ISO UTC como ponto de partida).

## 10. JSON — exportação versionada

O JSON serve para interoperabilidade e, no futuro, restauro. Usar `kotlinx.serialization` ou Moshi/Gson já adotado no projeto, com DTOs próprios de exportação (não serializar entidades Room diretamente).

```json
{
  "schemaVersion": 1,
  "application": "CestoSmart",
  "generatedAt": "2026-10-19T00:00:00Z",
  "currency": "EUR",
  "summary": { "totalCents": 235, "transactionCount": 1 },
  "items": [
    { "transactionId": "abc", "name": "Café", "quantity": "1", "unitPriceCents": 85, "totalCents": 85 },
    { "transactionId": "abc", "name": "Bola de Berlim", "quantity": "1", "unitPriceCents": 150, "totalCents": 150 }
  ]
}
```

Exportar `quantity` como `String`, preços como inteiros e datas em ISO-8601. Evitar incluir dados pessoais desnecessários. Se vier a existir **importação**, validar versão, duplicados e integridade antes de inserir na base de dados.

## 11. PDF — geração no dispositivo

Para um PDF simples sem dependências adicionais, utilizar `android.graphics.pdf.PdfDocument`:

```kotlin
class PdfReportExporter @Inject constructor() : ReportExporter {
    override val format = ExportFormat.PDF

    override suspend fun write(report: ReportData, output: OutputStream) =
        withContext(Dispatchers.Default) {
            val document = PdfDocument()
            try {
                val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
                val page = document.startPage(pageInfo)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 16f }
                page.canvas.drawText("CestoSmart — Relatório", 40f, 60f, paint)
                paint.textSize = 12f
                page.canvas.drawText("Total: ${centsPt(report.summary.totalCents)} EUR", 40f, 90f, paint)
                document.finishPage(page)
                // Versão final: paginar tabelas, repetir cabeçalhos, desenhar gráficos,
                // calcular quebras de linha e acrescentar numeração de páginas.
                withContext(Dispatchers.IO) { document.writeTo(output) }
            } finally {
                document.close()
            }
        }
}
```

O código é um **esqueleto funcional de uma página**, não um gerador completo de tabelas. Implementar um `PdfLayoutEngine` com margens, fontes, cabeçalho, rodapé, quebra de página e gráficos vetoriais. Para gráficos, desenhar as mesmas séries do dashboard em `Canvas` PDF, sem capturas de ecrã. Validar páginas com 0, 1, 50 e 1000 linhas e caracteres portugueses.

## 12. XLSX, HTML e TXT — fase seguinte

- **XLSX:** criar folhas `Resumo`, `Movimentos`, `Categorias`, `Estabelecimentos`; aplicar formatos de data/moeda, filtros e congelamento de cabeçalhos. Avaliar uma biblioteca OOXML realmente compatível com Android ou um escritor ZIP/XML mínimo (Office Open XML). Não assumir que Apache POI funciona sem problemas em qualquer versão de Android.
- **HTML:** template HTML autónomo com CSS embebido e tabelas; escapar sempre nomes introduzidos pelo utilizador para evitar injeção de HTML.
- **TXT:** resumo legível para partilha, sem tabelas complexas.

Cada exportador deve receber o **mesmo `ReportData`**, garantindo valores idênticos em todos os formatos.

## 13. Guardar e partilhar: Storage Access Framework

Utilizar `ActivityResultContracts.CreateDocument` para o utilizador escolher destino. Não é necessária permissão de armazenamento geral.

```kotlin
val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("text/csv")
) { uri: Uri? ->
    if (uri != null) viewModel.exportTo(uri, ExportFormat.CSV)
}

Button(onClick = { launcher.launch("CestoSmart_Relatorio.csv") }) {
    Text("Exportar CSV")
}
```

Para vários formatos, criar um launcher por MIME type ou usar `ActivityResultContracts.StartActivityForResult` com `Intent.ACTION_CREATE_DOCUMENT` e `type` dinâmico. No coordenador:

```kotlin
suspend fun writeToUri(
    context: Context,
    uri: Uri,
    exporter: ReportExporter,
    report: ReportData
) = withContext(Dispatchers.IO) {
    val stream = context.contentResolver.openOutputStream(uri, "w")
        ?: error("Não foi possível abrir o destino")
    stream.use { exporter.write(report, it) }
}
```

Para partilhar por outras aplicações: criar o ficheiro na cache interna, expor via `FileProvider`, enviar `ACTION_SEND` com `FLAG_GRANT_READ_URI_PERMISSION`, limpar ficheiros temporários e nunca usar `file://`.

## 14. Desempenho, segurança e qualidade

- **Índices Room:** transações por data/estado; itens por `transactionId`; estabelecimento/categoria por ID.
- **Memória:** para exportações muito grandes, usar paginação e *streaming* em vez de criar `List<ReportRow>` gigantes.
- **Consistência:** gerar um snapshot transacional da consulta ou manter dados do relatório imutáveis durante a exportação.
- **Privacidade:** não enviar relatórios para servidores externos; permitir apagar exportações temporárias.
- **Falhas:** cancelar corretamente coroutines, mostrar erro e evitar apresentar ficheiro incompleto como sucesso.
- **Acessibilidade:** gráficos com descrição textual e tabela alternativa; contraste, tamanhos de letra e formatação monetária pt-PT.
- **Internacionalização:** formatação visual pt-PT; exportação estruturada com ISO-8601, EUR e valores exatos.

## 15. Testes automatizados

| Cenário | Validação |
|---|---|
| Duas linhas na mesma despesa | `transactionCount = 1` |
| Lista planeada não concluída | Não entra no total gasto |
| Sessão cancelada | Não entra no total |
| Sessão de compras + despesa | Totais agregados sem duplicação |
| Filtro por categoria | Só linhas dessa categoria |
| Intervalo em mudança de hora | Limites locais corretos |
| Produto apagado do catálogo | Snapshot histórico permanece |
| CSV com `;`, aspas e nova linha | Escaping correto |
| CSV com nome `=SUM(...)` | Não executa fórmula |
| JSON exportado | `schemaVersion`, preços inteiros e quantidades string |
| PDF de 1000 linhas | Paginação e cabeçalhos sem corte |
| Relatório vazio | Total zero e exportação válida |
| Erro de escrita | Mensagem clara e sem falso sucesso |
| Comparação entre formatos | Mesmo total e número de transações |

## 16. Plano de implementação (sprints)

### Sprint 1 — Dados e agregações
- [ ] Inventariar entidades Room atuais dos três modos.
- [ ] Definir snapshots de nome/categoria/estabelecimento onde faltarem.
- [ ] Criar `ReportFilter`, `ReportRow`, `ReportData`.
- [ ] Criar `ReportsDao`, `ReportsRepository` e testes de totais.

### Sprint 2 — Dashboard e gráficos
- [ ] Criar ecrã `Relatórios` na navegação.
- [ ] Implementar filtros e KPIs.
- [ ] Implementar gráficos por dia e por categoria.
- [ ] Criar estados vazio, erro e carregamento.

### Sprint 3 — CSV e JSON
- [ ] Criar contrato `ReportExporter` e `ExportCoordinator`.
- [ ] Implementar CSV seguro e JSON versionado.
- [ ] Implementar `CreateDocument` e partilha.
- [ ] Testar Excel português e ficheiros com acentos.

### Sprint 4 — PDF e robustez
- [ ] Criar PDF multipágina com tabelas e gráficos.
- [ ] Adicionar testes de desempenho e exportações grandes.
- [ ] Rever privacidade, acessibilidade e mensagens de erro.

### Fase 2
- [ ] XLSX, HTML e TXT.
- [ ] Histórico de preços e comparação de períodos.
- [ ] Relatórios recorrentes com WorkManager (com consentimento do utilizador).
- [ ] Importação/restauro de JSON versionado.

## 17. Critérios de aceitação do MVP

1. O utilizador consegue consultar um resumo de gastos para um intervalo de datas.
2. O total do dashboard coincide com a soma das transações concluídas na base de dados.
3. Os gráficos atualizam quando se alteram os filtros.
4. O utilizador consegue exportar o relatório filtrado em CSV, JSON e PDF.
5. Os três ficheiros apresentam totais coerentes e não dependem de Internet.
6. A exportação pode ser guardada numa pasta escolhida ou partilhada com outra aplicação.
7. O sistema suporta relatórios vazios, falhas de escrita e dados com caracteres portugueses.

## 18. Decisão técnica final

**MVP:** Room + `GenerateReportUseCase` + Compose (gráficos) + CSV/JSON/PDF + Storage Access Framework.

**Princípio central:** os três modos produzem um modelo normalizado de movimentos reais; relatórios e exportações consomem esse modelo, evitando lógica duplicada e diferenças de totais entre ecrãs e ficheiros.

---

**Nota de integração:** exemplos de nomes de tabelas e classes devem ser adaptados ao esquema atual do projeto. Os excertos ilustram as responsabilidades e os contratos principais; não constituem um projeto Android integral compilável sem os restantes imports, bindings Hilt, migrations e implementação dos mappers.
