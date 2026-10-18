# MVP — Aplicação Android de Compras Inteligentes

**Documento de implementação** · Versão 1.0 · 17/10/2026  
**Plataforma:** Android · **Idioma:** Português (Portugal) · **Moeda:** EUR

## 1. Objetivo e âmbito

Desenvolver uma aplicação Android nativa, offline-first, com dois modos de utilização:

1. **Calculadora rápida:** registar preços manualmente ou por fotografia, sem lista prévia; somar imediatamente e manter as linhas editáveis.
2. **Lista planeada:** criar uma lista de produtos, iniciar uma sessão de compras, identificar produtos e registar os preços efetivos.

O MVP inclui scanner inteligente com leitura de **código de barras (EAN-8/EAN-13)** e **preço por OCR**, na mesma fotografia ou em capturas sucessivas; identificação opcional do produto através da **Open Food Facts API**; histórico local; orçamento e persistência das sessões.

### 1.1 Incluído no MVP

- Criar, editar, duplicar e arquivar listas planeadas.
- Iniciar, retomar, concluir e cancelar sessões de compras.
- Introdução rápida de preço, quantidade, desconto simples e remoção/desfazer.
- Captura de código de barras em tempo real e fotografia para OCR do preço.
- Suportar uma imagem com ambos os dados ou capturas separadas, em qualquer ordem.
- Confirmação obrigatória do preço detetado antes de o adicionar.
- Consulta Open Food Facts por código de barras e cache local.
- Produtos desconhecidos e preços sem produto identificado.
- Produtos unitários e ao peso, com preço por unidade de medida.
- Orçamento por sessão, total atualizado e alertas visuais.
- Histórico de compras e de preços registados por supermercado.
- Funcionamento offline para funções locais; consulta remota apenas quando disponível.

### 1.2 Fora do MVP

Open Prices e outras APIs de preços; comparação automática de preços entre supermercados; leitura de talões completos; contas de utilizador; sincronização na nuvem; partilha familiar; stock de lojas; promoções complexas (3 por 2, cupões globais); reconhecimento automático do produto pela imagem sem código de barras.

## 2. Stack tecnológica

| Área | Tecnologia | Observações |
|---|---|---|
| Linguagem | Kotlin | Android nativo |
| UI | Jetpack Compose + Material 3 | Interface declarativa |
| Arquitetura | Clean Architecture + MVVM | Separação UI / domínio / dados |
| DI | Hilt | Injeção de dependências |
| Persistência | Room / SQLite | Fonte de verdade local |
| Preferências | DataStore | Moeda, alertas, tema |
| Assíncrono | Coroutines + Flow | Estado reativo |
| Navegação | Navigation Compose | Rotas tipadas quando compatíveis |
| Câmara | CameraX | Preview, análise e captura |
| Barcode | ML Kit Barcode Scanning | EAN-8, EAN-13; outros opcionais |
| OCR | ML Kit Text Recognition (Latin) | Texto de etiquetas |
| HTTP | Retrofit + OkHttp + kotlinx.serialization ou Moshi | Cliente Open Food Facts |
| Testes | JUnit, MockK, Room tests, Compose UI tests | Testes de cálculo e fluxo |
| Android mínimo proposto | API 26 | Validar compatibilidade de dependências |

Fixar versões estáveis e compatíveis no `gradle/libs.versions.toml`, sem versões dinâmicas.

## 3. Arquitetura

```text
Jetpack Compose Screens
       |
ViewModels + StateFlow (UI state)
       |
Use Cases (domínio)
       |
Repository Interfaces
       |
+--------------------+----------------------+------------------+
| Room / DataStore   | OpenFoodFactsClient  | CameraX / ML Kit |
+--------------------+----------------------+------------------+
```

Princípios:

- **Room é a fonte de verdade** para listas, sessões, produtos em cache e preços registados.
- A UI não chama diretamente Retrofit, DAOs ou ML Kit.
- Casos de utilização são testáveis sem Android quando possível.
- Integrações externas falham de forma não bloqueante: o utilizador continua a comprar offline.
- Preparar `PriceProvider` como interface para uma versão futura, **sem integrar Open Prices no MVP**.

### 3.1 Estrutura de packages

```text
pt.vcc.shopping/
  ShoppingApplication.kt
  MainActivity.kt
  core/
    database/{ShoppingDatabase.kt,dao/,entity/,migration/}
    datastore/
    di/
    navigation/
    money/
    camera/
    ui/
  domain/
    model/
    repository/
    usecase/
  data/
    repository/
    mapper/
    remote/openfoodfacts/
    scanner/
  feature/
    home/
    calculator/
    shoppinglist/
    session/
    scanner/
    product/
    history/
    settings/
```

Começar com **um módulo `app`** e packages por responsabilidade. Extrair módulos Gradle apenas se o crescimento justificar.

## 4. Ecrãs e navegação

| Rota | Ecrã | Responsabilidade |
|---|---|---|
| `home` | HomeScreen | Entrar na calculadora ou listas |
| `calculator/{sessionId}` | CalculatorScreen | Teclado numérico, total, linhas, scanner |
| `lists` | ShoppingListsScreen | Listas guardadas |
| `list/edit/{listId?}` | ShoppingListEditorScreen | Nome, orçamento, produtos, quantidades |
| `session/{sessionId}` | ShoppingSessionScreen | Carrinho e progresso da lista |
| `scanner/{sessionId}` | SmartScannerScreen | Barcode + OCR, capturas sequenciais |
| `capture/confirm/{draftId}` | PriceConfirmationScreen | Escolher/corrigir produto e preço |
| `product/edit/{itemId}` | ProductEditorScreen | Nome, preço, unidade, desconto |
| `history` | HistoryScreen | Sessões concluídas |
| `history/{sessionId}` | PurchaseDetailScreen | Detalhe de compra |
| `settings` | SettingsScreen | Tema, alertas, privacidade |

Navegação inferior: **Início, Listas, Histórico, Definições**. Scanner e confirmação abrem sobre a sessão ativa. Estado transitório do scanner deve sobreviver à rotação e, quando relevante, à recriação do processo.

### 4.1 Calculadora rápida

1. Iniciar sessão `CALCULATOR` sem lista.
2. Mostrar total e teclado grande; introdução monetária sem ambiguidades (vírgula decimal).
3. `Adicionar` cria `ShoppingSessionItem` com nome opcional.
4. Lista mostra preço, quantidade e total; edição e remoção imediatas.
5. Câmara abre scanner inteligente; após confirmação, acrescentar item.
6. Finalizar grava `finalTotalCents` e estado `COMPLETED`.

### 4.2 Lista planeada

1. Criar lista e itens (`PENDING` implícito enquanto não comprados).
2. Iniciar sessão `PLANNED` ligada à lista.
3. Selecionar item e introduzir preço ou abrir scanner.
4. Ao confirmar, criar item de sessão associado ao item planeado.
5. Permitir produtos extra não planeados e itens ignorados.
6. Mostrar total, orçamento disponível e progresso.
7. Finalizar sem alterar o histórico de outras sessões.

## 5. Scanner inteligente — requisitos detalhados

### 5.1 Duas estratégias de captura

**A — Fotografia única:** enviar o mesmo `InputImage` para ML Kit Barcode Scanner e Text Recognition. Reunir candidatos a código e preço e apresentar confirmação.

**B — Capturas sucessivas:** ler primeiro barcode e depois preço, ou preço e depois barcode. Manter `ProductCaptureDraft` até completar ou cancelar.

**Comportamento obrigatório:**

| Resultado | Ação |
|---|---|
| Barcode + preço | Consultar catálogo; confirmar ambos |
| Apenas barcode | Consultar produto; pedir fotografia ou introdução do preço |
| Apenas preço | Guardar candidato; pedir barcode ou nome manual |
| Vários preços | Mostrar candidatos, destacando sugestão; nunca escolher silenciosamente |
| Vários barcodes | Pedir escolha ou novo enquadramento |
| Nenhum | Repetir fotografia ou inserir manualmente |
| Produto não encontrado na API | Permitir nome manual e guardar código |
| Sem Internet | Consultar Room; permitir continuar sem dados remotos |

### 5.2 Pipeline

```text
CameraX Preview / ImageCapture
          |
          +--> BarcodeRecognizer --> barcodeCandidates[]
          |
          +--> TextRecognizer ----> textBlocks[]
                                       |
                                 PriceCandidateParser
                                       |
                           priceCandidates[] + contexto
          |
CaptureDraftReducer (acumula capturas)
          |
ProductResolver (Room -> Open Food Facts, se necessário)
          |
PriceConfirmationScreen
          |
AddSessionItemUseCase -> Room -> UI atualizada
```

Para leitura em tempo real, limitar frequência de análise, fechar `ImageProxy` em todos os caminhos e evitar consultas repetidas à API. Usar `ImageCapture` para OCR mais fiável quando necessário.

### 5.3 Parser de preços

- Normalizar `1,49 €`, `€ 1,49` e `1.49` com regras específicas para contexto português.
- Identificar e classificar preço de venda, preço antigo, preço/kg, preço/L, percentagens e descontos.
- Utilizar posição relativa do texto e palavras próximas (`€/kg`, `antes`, `promoção`) para ordenar candidatos.
- Não assumir que o maior, menor ou primeiro número é o preço correto.
- Exigir confirmação; se não houver confiança, abrir edição manual.
- Guardar `rawText`, candidatos e preço confirmado, sem persistir fotografias por defeito.

### 5.4 Estado de captura

```kotlin
data class ProductCaptureDraft(
    val id: String,
    val sessionId: String,
    val barcode: String? = null,
    val productId: String? = null,
    val productName: String? = null,
    val priceCandidatesCents: List<Long> = emptyList(),
    val confirmedPriceCents: Long? = null,
    val quantity: String = "1",
    val unit: String = "UN",
    val barcodeCaptured: Boolean = false,
    val priceCaptured: Boolean = false
)
```

Draft é estado temporário; persistir no `SavedStateHandle`/Room se a recuperação após encerramento for necessária. Nunca adicionar automaticamente ao carrinho sem confirmação do utilizador.

## 6. Integração Open Food Facts

### 6.1 Responsabilidade

A API recebe o **código de barras**, não a fotografia. A app lê o código com ML Kit e usa-o para consultar nome, marca, quantidade, imagem e categorias. **A API de produtos não fornece preços de supermercado.**

Endpoint de referência:

```http
GET https://world.openfoodfacts.org/api/v3/product/{barcode}
```

Exemplo:

```bash
curl -L \
  -H 'User-Agent: VCCShopping/1.0 (contacto-real@dominio.pt)' \
  'https://world.openfoodfacts.org/api/v3/product/3017620422003'
```

O contacto no exemplo é ilustrativo; substituir antes de distribuir. Confirmar os campos e os limites atuais na documentação oficial: https://openfoodfacts.github.io/openfoodfacts-server/api/ . Respeitar a licença ODbL e atribuição dos dados.

### 6.2 Cliente e política de cache

```kotlin
interface ProductCatalogRepository {
    suspend fun findByBarcode(barcode: String): Product?
    suspend fun refreshFromOpenFoodFacts(barcode: String): Product?
}
```

1. Validar formato do barcode; preservar zeros à esquerda (usar `String`).
2. Consultar Room primeiro.
3. Se não existir e houver rede, consultar Open Food Facts.
4. Mapear apenas campos conhecidos; tolerar campos ausentes e registos incompletos.
5. Guardar produto e origem `OPEN_FOOD_FACTS` no Room.
6. Em 404, timeout, limite de pedidos ou resposta inválida, continuar com produto manual.
7. Usar `User-Agent` identificativo, timeout, retry moderado e cache para reduzir chamadas.
8. Não enviar fotografias, listas de compras ou histórico pessoal para Open Food Facts.

O mesmo código de barras identifica normalmente a mesma referência comercial, mas packs, tamanhos, marcas próprias e etiquetas internas podem diferir. Não usar barcode como chave primária do banco local.

## 7. Modelo de dados Room

### 7.1 Entidades

| Entidade | Campos principais | Relações |
|---|---|---|
| `ShoppingList` | `id UUID`, `name`, `budgetCents?`, `createdAt`, `updatedAt`, `archived` | 1:N `ShoppingListItem` |
| `ShoppingListItem` | `id`, `shoppingListId`, `productId?`, `name`, `plannedQuantity TEXT`, `unit`, `estimatedPriceCents?`, `sortOrder` | N:1 lista |
| `ShoppingSession` | `id`, `shoppingListId?`, `mode`, `status`, `storeId?`, `budgetCents?`, `startedAt`, `completedAt?`, `finalTotalCents?` | 1:N `ShoppingSessionItem` |
| `ShoppingSessionItem` | `id`, `sessionId`, `shoppingListItemId?`, `productId?`, `name?`, `quantity TEXT`, `unit`, `unitPriceCents`, `discountCents`, `totalCents`, `priceSource`, `addedAt` | N:1 sessão |
| `Product` | `id`, `barcode?`, `name`, `brand?`, `quantityLabel?`, `category?`, `imageUrl?`, `source`, `updatedAt` | Referenciado por itens |
| `Store` | `id`, `name`, `createdAt` | 1:N sessões e histórico |
| `ProductPriceHistory` | `id`, `productId?`, `barcode?`, `storeId?`, `priceCents`, `unit`, `quantity TEXT`, `source`, `observedAt`, `sessionItemId?` | Histórico local |
| `PriceCapture` | `id`, `sessionItemId?`, `rawText`, `detectedPriceCents?`, `confirmedPriceCents?`, `status`, `capturedAt` | Auditoria OCR |

**Índices e constraints:** `Product.barcode` único quando não nulo (conforme política de catálogo); índices em `ShoppingListItem.shoppingListId`, `ShoppingSessionItem.sessionId`, `ProductPriceHistory(productId,storeId,observedAt)`, `ShoppingSession(status,startedAt)`; chaves estrangeiras e transações nas operações compostas.

### 7.2 Estados

```text
SessionMode: CALCULATOR | PLANNED
SessionStatus: ACTIVE | COMPLETED | CANCELLED
PriceSource: MANUAL | OCR | HISTORY
ProductSource: MANUAL | OPEN_FOOD_FACTS
CaptureStatus: PENDING | CONFIRMED | REJECTED
Unit: UN | KG | G | L | ML
```

O estado `PENDING/ADDED/SKIPPED` de um item planeado pode ser derivado da sessão, com registo explícito de `SKIPPED` se necessário para distinguir decisão de ausência de registo.

### 7.3 Regras monetárias

- Guardar moeda em cêntimos (`Long`); nunca usar `Float` ou `Double` para dinheiro.
- Quantidades decimais com `BigDecimal` no domínio e `String` em Room.
- Calcular `lineSubtotal = unitPrice * quantity`, converter para cêntimos com regra `HALF_UP` explícita e subtrair descontos da linha.
- `total = soma(line.totalCents)`; `remainingBudget = budgetCents - total`.
- Recalcular após adicionar, editar, remover ou alterar quantidades.
- Distinguir preço unitário de preço por kg/L; não somar automaticamente valores de referência da etiqueta.
- Em produtos ao peso, registar quantidade efetiva e unidade coerente com o preço.

Exemplo: `1,79 €/kg × 0,750 kg = 1,3425 € -> 1,34 €`.

## 8. Repositórios e casos de utilização

```kotlin
interface ShoppingListRepository {
    fun observeLists(): Flow<List<ShoppingList>>
    suspend fun saveList(list: ShoppingList)
}

interface ShoppingSessionRepository {
    fun observeSession(id: String): Flow<ShoppingSessionWithItems>
    suspend fun addItem(item: ShoppingSessionItem)
    suspend fun completeSession(id: String)
}

interface PriceProvider {
    suspend fun getPrices(barcode: String): List<ProductPrice>
}
```

`PriceProvider` é uma extensão futura; **não** fazer chamadas ao Open Prices no MVP.

Casos de utilização prioritários:

```text
CreateShoppingListUseCase
AddShoppingListItemUseCase
DuplicateShoppingListUseCase
StartShoppingSessionUseCase
ResumeShoppingSessionUseCase
AddSessionItemUseCase
UpdateSessionItemUseCase
RemoveSessionItemUseCase
CalculateSessionTotalUseCase
CalculateRemainingBudgetUseCase
CompleteShoppingSessionUseCase
RecognizeBarcodeUseCase
RecognizePriceUseCase
ResolveProductUseCase
ConfirmCaptureUseCase
GetShoppingHistoryUseCase
GetProductPriceHistoryUseCase
```

**Consistência:** `ConfirmCaptureUseCase` deve adicionar o item e registar a captura numa transação. Ao concluir sessão, guardar total consolidado e criar histórico de preços apenas para registos válidos e identificáveis, sem duplicar entradas em reaberturas.

## 9. UI state e tratamento de erros

Cada ViewModel expõe `StateFlow<ScreenUiState>` e recebe eventos de UI. Exemplo:

```kotlin
data class ScannerUiState(
    val draft: ProductCaptureDraft? = null,
    val isRecognizing: Boolean = false,
    val isResolvingProduct: Boolean = false,
    val errorMessage: String? = null,
    val canConfirm: Boolean = false
)
```

Tratamento obrigatório: permissão da câmara recusada; ausência de câmara; imagem desfocada; barcode inválido; vários preços; produto ausente na API; falha de rede; duplicação acidental de captura; sessão concluída; armazenamento indisponível. Usar mensagens em português de Portugal e alternativas manuais.

## 10. Segurança, privacidade e acessibilidade

- Pedir permissão de câmara apenas quando o utilizador abre o scanner.
- Não pedir localização no MVP; supermercado é selecionado manualmente.
- Não guardar fotografias por defeito; se houver retenção, informar e permitir eliminar.
- Armazenar dados privados em armazenamento interno da aplicação.
- Utilizar HTTPS e `User-Agent` próprio para a API.
- Sem analytics invasivos ou transmissão do histórico de compras a terceiros.
- Suportar TalkBack, tamanhos de texto ampliados, contraste e botões grandes.
- Indicar claramente quando um preço foi reconhecido automaticamente e quando foi confirmado.

## 11. Testes e critérios de aceitação

### 11.1 Testes unitários

- `1,79 €/kg × 0,750 kg` resulta em `1,34 €`.
- Quantidades, descontos e arredondamento não produzem diferenças de cêntimos inesperadas.
- OCR diferencia `1,49 €` de `1,49 €/kg` quando ambos aparecem.
- `ProductResolver` retorna cache sem Internet e não bloqueia compra em erro HTTP.
- Remover item atualiza total e orçamento imediatamente.

### 11.2 Testes de integração

- Criar lista, iniciar sessão, registar itens e concluir preserva dados após reiniciar app.
- Captura em duas etapas, barcode→preço e preço→barcode, produz um único item.
- Captura com ambos na mesma fotografia não duplica produto.
- Produto desconhecido pode ser registado manualmente.
- Migrações Room preservam listas e histórico.

### 11.3 Critérios de aceitação do MVP

1. Utilizador consegue adicionar 10 preços manualmente sem ligação à Internet.
2. Total corresponde à soma de todas as linhas, incluindo quantidades.
3. Utilizador consegue criar uma lista e iniciar sessão associada.
4. Scanner suporta código e preço na mesma imagem quando legíveis.
5. Scanner suporta duas capturas sucessivas em qualquer ordem.
6. Preço OCR é sempre confirmado antes de entrar no carrinho.
7. Open Food Facts preenche dados disponíveis pelo barcode sem impedir registo manual em falha.
8. Sessão ativa é retomada após fechar e reabrir a aplicação.
9. Sessão concluída aparece no histórico com total e itens.
10. Orçamento mostra valor restante e aviso quando ultrapassado.

## 12. Plano de implementação

| Sprint | Entregáveis | Saída verificável |
|---|---|---|
| 1 — Fundação | Projeto, Compose, Hilt, Room, entidades, DAOs, navegação | App inicia e persiste dados |
| 2 — Calculadora | Sessões, teclado, linhas, totais, orçamento | Compras rápidas offline |
| 3 — Listas | CRUD listas, itens planeados, sessão ligada à lista | Lista completa funcional |
| 4 — Barcode e catálogo | CameraX, ML Kit Barcode, Open Food Facts, cache | Identificação de produtos |
| 5 — OCR e captura combinada | ML Kit Text, parser, draft, confirmação, capturas sequenciais | Scanner inteligente funcional |
| 6 — Histórico e estabilização | Histórico, testes, acessibilidade, erros, APK assinado | MVP pronto para testes internos |

**Ordem recomendada:** validar primeiro o fluxo completo sem câmara; integrar barcode; só depois adicionar OCR e a combinação dos resultados. O scanner é o maior risco técnico, sobretudo etiquetas com múltiplos preços.

## 13. Tarefas técnicas iniciais

- [ ] Criar projeto Android Kotlin + Compose, package `pt.vcc.shopping`.
- [ ] Configurar catálogo de versões Gradle, Hilt, Room, Navigation e DataStore.
- [ ] Implementar entidades, DAOs, migrações e testes Room.
- [ ] Implementar domínio monetário com `BigDecimal` e testes.
- [ ] Implementar sessões e calculadora offline.
- [ ] Implementar listas planeadas e associação de itens.
- [ ] Integrar CameraX com leitura EAN-8/EAN-13.
- [ ] Implementar cliente Open Food Facts e cache local.
- [ ] Integrar OCR e parser de preços portugueses.
- [ ] Implementar `ProductCaptureDraft` e confirmação.
- [ ] Registar histórico local de preços por loja.
- [ ] Testar com etiquetas reais de vários supermercados.
- [ ] Gerar APK assinado e executar testes de aceitação.

## 14. Referências técnicas

- Open Food Facts API: https://openfoodfacts.github.io/openfoodfacts-server/api/
- Open Food Facts API v3: https://world.openfoodfacts.org/api/v3/product/3017620422003
- ML Kit Barcode Scanning: https://developers.google.com/ml-kit/vision/barcode-scanning/android
- ML Kit Text Recognition: https://developers.google.com/ml-kit/vision/text-recognition/v2/android
- CameraX: https://developer.android.com/media/camera/camerax
- Room: https://developer.android.com/training/data-storage/room
- Jetpack Compose: https://developer.android.com/compose
- Hilt: https://developer.android.com/training/dependency-injection/hilt-android

---

**Decisão de arquitetura:** o MVP integra **Open Food Facts para identificação de produtos**, mas **não depende de uma API de preços**. Todos os preços reais são registados localmente por introdução manual ou OCR, com estrutura preparada para futuros fornecedores de preços.
