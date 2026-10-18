# Integração Open Prices no MVP Android — CestoSmart

> Especificação técnica de implementação: Kotlin, Retrofit, Room, Hilt, Coroutines, Jetpack Compose e tratamento de preços portugueses.
>
> **Nota:** Os exemplos são uma base de implementação. Confirmar os nomes e formatos exatos dos campos na documentação OpenAPI antes de compilar e publicar: https://prices.openfoodfacts.org/api/docs.

## 1. Objetivo e fluxo

O Open Food Facts identifica produtos pelo EAN/GTIN; o Open Prices fornece observações de preços. O valor do carrinho é sempre o preço confirmado pelo utilizador, nunca automaticamente um preço comunitário.

```text
Scanner ML Kit (EAN)
  -> Room: produto conhecido?
  -> Open Food Facts: identificar produto (se necessário)
  -> Room: preços em cache
  -> Open Prices: atualizar preços
  -> validar país PT, moeda EUR, data, unidade, promoções
  -> apresentar preço, loja, local e data
  -> confirmar preço manualmente ou por OCR
  -> adicionar ao carrinho
```

A aplicação continua a funcionar offline. A falta de preços externos não bloqueia a compra.

## 2. Endpoints e requisitos

Base URL: `https://prices.openfoodfacts.org/api/v1/`

```http
GET /prices?product_code=3017620422003&currency=EUR&type=PRODUCT&page=1&size=100
GET /locations/{id}
```

A leitura pública não exige API Key. Para escrita/contribuições, consultar os requisitos de autenticação e provas de preço da documentação oficial. Identificar a aplicação com um `User-Agent` e contacto real; respeitar os limites e respostas HTTP 429.

Teste:

```bash
curl -L -H 'Accept: application/json' \
  -H 'User-Agent: CestoSmart/1.0 (contacto@exemplo.pt)' \
  'https://prices.openfoodfacts.org/api/v1/prices?product_code=3017620422003'
```

Não inferir Portugal apenas por `currency=EUR`.

## 3. Estrutura do projeto

```text
pt.vcc.cestosmart/
  data/
    remote/openprices/
      OpenPricesApi.kt
      OpenPricesDtos.kt
      OpenPricesMapper.kt
    local/entity/ExternalPriceEntity.kt
    local/entity/ExternalPriceSyncEntity.kt
    local/dao/ExternalPriceDao.kt
    repository/OpenPricesRepositoryImpl.kt
  domain/
    model/ProductPrice.kt
    repository/PriceRepository.kt
  di/OpenPricesModule.kt
  feature/prices/
    ProductPricesViewModel.kt
    ProductPricesScreen.kt
```

## 4. Gradle e permissões

Dependências exemplificativas; harmonizar versões com o catálogo Gradle existente:

```kotlin
dependencies {
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
}
```

Em `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.INTERNET" />
```

O projeto já deverá incluir Room, Hilt, Compose, Coroutines e Navigation Compose.

## 5. DTOs Retrofit/Gson

A resposta de listagem é paginada. Os DTOs abaixo são deliberadamente tolerantes a valores ausentes. **Validar os campos concretos contra o schema OpenAPI atual**, sobretudo os detalhes de `location` e `price_per`.

```kotlin
import com.google.gson.annotations.SerializedName

data class OpenPricesResponseDto(
    val items: List<OpenPriceDto> = emptyList(),
    val page: Int = 1,
    val pages: Int = 1,
    val total: Int = 0
)

data class OpenPriceDto(
    val id: Long,
    @SerializedName("product_code") val productCode: String?,
    @SerializedName("location_id") val locationId: Long?,
    val price: Double?,
    val currency: String?,
    val date: String?,
    @SerializedName("price_per") val pricePer: String?,
    @SerializedName("price_is_discounted") val isDiscounted: Boolean?,
    @SerializedName("price_without_discount") val priceWithoutDiscount: Double?,
    @SerializedName("discount_type") val discountType: String?,
    val type: String?,
    val location: OpenPriceLocationDto?
)

data class OpenPriceLocationDto(
    val id: Long?,
    @SerializedName("osm_name") val osmName: String?,
    @SerializedName("osm_brand") val osmBrand: String?,
    @SerializedName("osm_display_name") val displayName: String?,
    @SerializedName("osm_address_city") val city: String?,
    @SerializedName("osm_address_country_code") val countryCode: String?,
    @SerializedName("osm_address_country") val country: String?
)
```

Usar `Double` apenas para desserializar; no domínio e na persistência, guardar dinheiro como `Long` em cêntimos. Para máxima fidelidade decimal, pode optar-se por desserialização customizada diretamente para `BigDecimal`.

## 6. Serviço Retrofit

```kotlin
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface OpenPricesApi {
    @GET("prices")
    suspend fun getPrices(
        @Query("product_code") barcode: String,
        @Query("currency") currency: String = "EUR",
        @Query("type") type: String = "PRODUCT",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = 100
    ): OpenPricesResponseDto

    @GET("locations/{id}")
    suspend fun getLocation(
        @Path("id") id: Long
    ): OpenPriceLocationDto
}
```

**Atenção:** o DTO de `/locations/{id}` deve ser conferido com a resposta real. Se o endpoint devolver um wrapper ou campos adicionais, ajustar o contrato antes da integração.

### Hilt / OkHttp

```kotlin
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object OpenPricesModule {
    @Provides
    @Singleton
    fun provideOpenPricesApi(): OpenPricesApi {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "CestoSmart/1.0 (contacto@teudominio.pt)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("https://prices.openfoodfacts.org/api/v1/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenPricesApi::class.java)
    }
}
```

Evitar logging de dados sensíveis; em produção, manter logs HTTP desativados ou limitados.

## 7. Modelo de domínio

```kotlin
import java.time.LocalDate

data class ProductPrice(
    val externalId: Long,
    val barcode: String,
    val priceCents: Long,
    val currency: String = "EUR",
    val storeName: String?,
    val storeCity: String?,
    val storeCountryCode: String,
    val locationId: Long?,
    val observedAt: LocalDate,
    val isDiscounted: Boolean,
    val discountType: String?,
    val pricePer: String?,
    val source: PriceSource = PriceSource.OPEN_PRICES
)

enum class PriceSource { MANUAL, OCR, OPEN_PRICES }
```

`LocalDate` está disponível no Android a partir da API 26, correspondente ao `minSdk` previsto.

## 8. Normalização e validação de preços portugueses

| Regra | Tratamento |
|---|---|
| Moeda | Aceitar apenas EUR |
| País | Aceitar apenas `PT` validado a partir da localização |
| País desconhecido | Não classificar como preço português |
| Valor nulo, zero, negativo, infinito | Ignorar |
| Data futura ou inválida | Ignorar |
| Data antiga | Mostrar como observação histórica |
| Preço promocional | Identificar e mostrar condições, quando conhecidas |
| Preço por kg/litro | Não comparar diretamente com preço por embalagem |
| Loja desconhecida | Não inventar supermercado |
| EAN com zeros iniciais | Guardar sempre como String |

```kotlin
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

fun Double.toCents(): Long =
    BigDecimal.valueOf(this)
        .movePointRight(2)
        .setScale(0, RoundingMode.HALF_UP)
        .longValueExact()

fun OpenPriceDto.toDomain(barcode: String): ProductPrice? {
    if (currency != "EUR" || type != "PRODUCT") return null
    val loc = location ?: return null
    if (loc.countryCode?.uppercase() != "PT") return null
    val amount = price?.takeIf { it.isFinite() && it > 0.0 } ?: return null
    val observed = try {
        LocalDate.parse(date ?: return null)
    } catch (_: DateTimeParseException) {
        return null
    }
    if (observed.isAfter(LocalDate.now())) return null

    return ProductPrice(
        externalId = id,
        barcode = barcode,
        priceCents = amount.toCents(),
        storeName = loc.osmBrand ?: loc.osmName ?: loc.displayName,
        storeCity = loc.city,
        storeCountryCode = "PT",
        locationId = locationId,
        observedAt = observed,
        isDiscounted = isDiscounted == true,
        discountType = discountType,
        pricePer = pricePer
    )
}
```

Se a API devolver só `location_id`, consultar `/locations/{id}` antes de chamar `toDomain()`. Recomenda-se guardar em cache as localizações para evitar chamadas repetidas. Para `price_per`, mapear os valores documentados para um enum interno de unidades e impedir comparações incompatíveis.

## 9. Room — tabela de preços externos

Manter dados comunitários separados do histórico local de preços confirmados pelo utilizador.

```kotlin
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "external_prices",
    indices = [Index("barcode"), Index("observedAt")]
)
data class ExternalPriceEntity(
    @PrimaryKey val externalId: Long,
    val barcode: String,
    val priceCents: Long,
    val currency: String,
    val storeName: String?,
    val storeCity: String?,
    val storeCountryCode: String,
    val locationId: Long?,
    val observedAt: String,
    val isDiscounted: Boolean,
    val discountType: String?,
    val pricePer: String?,
    val fetchedAt: Long
)
```

### DAO

```kotlin
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ExternalPriceDao {
    @Query("SELECT * FROM external_prices WHERE barcode = :barcode ORDER BY observedAt DESC")
    fun observePrices(barcode: String): Flow<List<ExternalPriceEntity>>

    @Query("SELECT * FROM external_prices WHERE barcode = :barcode ORDER BY observedAt DESC")
    suspend fun getPrices(barcode: String): List<ExternalPriceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(prices: List<ExternalPriceEntity>)

    @Query("SELECT MAX(fetchedAt) FROM external_prices WHERE barcode = :barcode")
    suspend fun getLastFetch(barcode: String): Long?
}
```

### Mappers Room

```kotlin
fun ProductPrice.toEntity(fetchedAt: Long) = ExternalPriceEntity(
    externalId = externalId,
    barcode = barcode,
    priceCents = priceCents,
    currency = currency,
    storeName = storeName,
    storeCity = storeCity,
    storeCountryCode = storeCountryCode,
    locationId = locationId,
    observedAt = observedAt.toString(),
    isDiscounted = isDiscounted,
    discountType = discountType,
    pricePer = pricePer,
    fetchedAt = fetchedAt
)

fun ExternalPriceEntity.toDomain() = ProductPrice(
    externalId = externalId,
    barcode = barcode,
    priceCents = priceCents,
    currency = currency,
    storeName = storeName,
    storeCity = storeCity,
    storeCountryCode = storeCountryCode,
    locationId = locationId,
    observedAt = LocalDate.parse(observedAt),
    isDiscounted = isDiscounted,
    discountType = discountType,
    pricePer = pricePer
)
```

### Cache de pesquisas vazias

Não usar apenas `MAX(fetchedAt)` da tabela de preços: quando uma pesquisa devolve zero resultados, não fica qualquer marca de consulta. Criar uma tabela própria:

```kotlin
@Entity(tableName = "external_price_sync")
data class ExternalPriceSyncEntity(
    @PrimaryKey val barcode: String,
    val lastAttemptAt: Long,
    val lastSuccessAt: Long?,
    val status: String
)
```

Adicionar a entidade à `RoomDatabase`, criar DAO para ler/escrever o estado e criar uma migração Room explícita. A transação de atualização deve gravar preços e estado de sincronização de forma consistente.

## 10. Repository

### Interface

```kotlin
interface PriceRepository {
    fun observePrices(barcode: String): Flow<List<ProductPrice>>
    suspend fun refreshPrices(barcode: String): Result<Int>
}
```

### Implementação simplificada

```kotlin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OpenPricesRepositoryImpl(
    private val api: OpenPricesApi,
    private val dao: ExternalPriceDao
) : PriceRepository {

    override fun observePrices(barcode: String): Flow<List<ProductPrice>> =
        dao.observePrices(barcode).map { rows -> rows.map { it.toDomain() } }

    override suspend fun refreshPrices(barcode: String): Result<Int> {
        return try {
            val collected = mutableListOf<ProductPrice>()
            var page = 1
            do {
                val response = api.getPrices(barcode = barcode, page = page)
                collected += response.items.mapNotNull { it.toDomain(barcode) }
                val hasNext = page < response.pages && page < 3
                page++
            } while (hasNext)

            val now = System.currentTimeMillis()
            dao.upsertAll(collected.map { it.toEntity(now) })
            Result.success(collected.size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

**Pontos a completar para produção:** resolver localizações ausentes; injetar a implementação com Hilt; gravar o estado de sincronização mesmo sem resultados; sinalizar paginação parcial; proteger contra atualizações concorrentes do mesmo EAN; considerar remoção/expiração de registos desatualizados; aplicar a política de cache antes de consultar a rede.

### Política de cache

| Situação | Comportamento |
|---|---|
| Primeira consulta | Procurar na API |
| Última sincronização há menos de 24 h | Usar Room |
| Cache mais antiga | Mostrar cache e atualizar em segundo plano |
| Sem Internet | Usar cache, se existir |
| Sem preços encontrados | Guardar resultado vazio durante 24 h |
| HTTP 429 | Respeitar `Retry-After`, se presente |
| Erro de rede | Preservar cache e informar discretamente |

Os prazos de 24 horas são decisões de produto, não garantias de atualização da API.

## 11. ViewModel

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProductPricesUiState(
    val prices: List<ProductPrice> = emptyList(),
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ProductPricesViewModel @Inject constructor(
    private val repository: PriceRepository
) : ViewModel() {
    private val barcode = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProductPricesUiState> =
        barcode.flatMapLatest { code ->
            if (code == null) flowOf(emptyList())
            else repository.observePrices(code)
        }.combine(refreshing) { prices, loading ->
            ProductPricesUiState(prices = prices, isRefreshing = loading)
        }.combine(error) { state, message ->
            state.copy(errorMessage = message)
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            ProductPricesUiState()
        )

    fun load(barcodeValue: String) {
        barcode.value = barcodeValue
        viewModelScope.launch {
            refreshing.value = true
            error.value = null
            try {
                repository.refreshPrices(barcodeValue).onFailure {
                    error.value = "Não foi possível atualizar os preços."
                }
            } finally {
                refreshing.value = false
            }
        }
    }
}
```

No projeto final, a atualização deve ser feita por um caso de utilização que consulta primeiro a política de cache. Evitar múltiplas chamadas quando o mesmo código de barras é reconhecido repetidamente pelo scanner.

## 12. Ecrã de preços — Jetpack Compose

Mostrar preços por supermercado, cidade, data, unidade e origem. Nunca apresentar uma observação antiga como promoção atual.

```kotlin
@Composable
fun ProductPricesScreen(
    state: ProductPricesUiState,
    onConfirmPrice: (ProductPrice) -> Unit,
    onEnterPriceManually: () -> Unit
) {
    Column {
        Text("Preços conhecidos em Portugal")
        if (state.isRefreshing) LinearProgressIndicator()
        state.errorMessage?.let { Text(it) }

        if (state.prices.isEmpty() && !state.isRefreshing) {
            Text("Sem preços disponíveis para este produto.")
        }

        LazyColumn {
            items(state.prices, key = { it.externalId }) { price ->
                Column {
                    Text(price.storeName ?: "Estabelecimento não identificado")
                    Text("${price.priceCents / 100.0} € · ${price.observedAt}")
                    price.storeCity?.let { Text(it) }
                    if (price.isDiscounted) Text("Preço promocional registado")
                    Button(onClick = { onConfirmPrice(price) }) {
                        Text("Usar como referência")
                    }
                }
            }
        }

        Button(onClick = onEnterPriceManually) {
            Text("Introduzir / fotografar preço atual")
        }
    }
}
```

Imports Compose omitidos. **Para apresentação em produção**, formatar moeda com `NumberFormat` e `Locale("pt", "PT")` em vez de interpolar `priceCents / 100.0`; garantir que confirmar um preço externo não o marca como preço observado na loja atual sem validação do utilizador.

## 13. Testes e critérios de aceitação

| Cenário | Resultado esperado |
|---|---|
| EAN com preços portugueses | Mostrar observações PT/EUR |
| Apenas preços de Espanha | Não apresentar como preços de Portugal |
| EUR sem país | Não assumir PT |
| Sem preços | Estado vazio e cache de consulta |
| Preço com data antiga | Etiqueta de preço histórico |
| Promoção condicionada | Não prometer desconto automático |
| Preço por kg vs. unidade | Não comparar diretamente |
| API indisponível | Continuar com Room |
| Mesmo preço importado duas vezes | Sem duplicados |
| EAN com zeros à esquerda | Preservar como String |
| Paginação limitada | Avisar que resultados podem estar incompletos |
| Scanner repete o EAN | Evitar chamadas duplicadas |
| HTTP 429 | Aplicar recuo/Retry-After |

Testar mappers, validação de país/moeda, conversão monetária, paginação, cache, cancelamento de coroutines, migrações Room e estados de interface.

## 14. Checklist de implementação

- [ ] Confirmar o contrato OpenAPI atual e exemplos reais de JSON.
- [ ] Adicionar Retrofit, Gson e permissão de Internet.
- [ ] Criar DTOs e serviço `OpenPricesApi`.
- [ ] Configurar OkHttp, `User-Agent` e Hilt.
- [ ] Criar modelo `ProductPrice` e mappers.
- [ ] Validar `PT`, `EUR`, datas, unidades e promoções.
- [ ] Resolver localizações quando apenas `location_id` está disponível.
- [ ] Criar `ExternalPriceEntity`, `ExternalPriceSyncEntity` e DAOs.
- [ ] Criar migração Room e testes de migração.
- [ ] Implementar `PriceRepository` e paginação.
- [ ] Implementar cache e sincronização de pesquisas vazias.
- [ ] Integrar com o scanner e com Open Food Facts.
- [ ] Criar ecrã Compose de preços históricos.
- [ ] Garantir confirmação explícita do preço de compra.
- [ ] Testar com 20–50 EAN reais vendidos em Portugal.
- [ ] Verificar atribuição e obrigações da licença ODbL.

## 15. Referências

- Open Prices API: https://prices.openfoodfacts.org/api/docs
- Open Prices: https://prices.openfoodfacts.org/
- Open Food Facts: https://world.openfoodfacts.org/
- Android Room: https://developer.android.com/training/data-storage/room
- Retrofit: https://square.github.io/retrofit/
- ML Kit Barcode Scanning: https://developers.google.com/ml-kit/vision/barcode-scanning/android

---

**Decisão do MVP:** Open Prices é uma fonte opcional de preços de referência. O preço efetivo do carrinho é sempre confirmado pelo utilizador (manual ou OCR). A arquitetura deve funcionar offline, mostrar a data e a origem de cada observação e nunca pressupor que um preço histórico continua válido.
