package pt.vcc.smartshopcart.feature.prices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.common.DateTimeText
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.model.PriceBasis
import pt.vcc.smartshopcart.domain.model.ReferencePrice

/**
 * Folha "Preços em Portugal" (§12, OP14), aberta no passo de confirmação do scanner. "Usar como referência"
 * só preenche o preço do rascunho; o utilizador continua a ter de o confirmar com "Adicionar".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductPricesSheet(
    barcode: String,
    onDismiss: () -> Unit,
    onUsePrice: (ReferencePrice) -> Unit,
    viewModel: ProductPricesViewModel = hiltViewModel(),
) {
    LaunchedEffect(barcode) { viewModel.load(barcode) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        ProductPricesScreen(
            state = state.takeIf { it.barcode == barcode } ?: ProductPricesUiState(barcode = barcode, refreshing = true),
            onUsePrice = onUsePrice,
            onRetry = viewModel::retry,
        )
    }
}

@Composable
fun ProductPricesScreen(
    state: ProductPricesUiState,
    onUsePrice: (ReferencePrice) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxWidth().navigationBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.prices_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.prices_subtitle), style = MaterialTheme.typography.bodySmall)
                if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        item { StatusLine(state, onRetry) }
        if (state.isEmpty && !state.refreshing && !state.neverFetched) {
            item { Text(stringResource(R.string.prices_empty), style = MaterialTheme.typography.bodyMedium) }
        }
        section(R.string.prices_section_unit, state.unitPrices, state, onUsePrice)
        section(R.string.prices_section_kg, state.kilogramPrices, state, onUsePrice)
        item {
            Text(
                stringResource(R.string.prices_source),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusLine(state: ProductPricesUiState, onRetry: () -> Unit) {
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        state.notice?.let { notice ->
            Text(
                stringResource(
                    when (notice) {
                        PricesNotice.OFFLINE -> R.string.prices_notice_offline
                        PricesNotice.FAILED -> R.string.prices_notice_failed
                        PricesNotice.RATE_LIMITED -> R.string.prices_notice_rate_limited
                    },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (state.sync?.incomplete == true && !state.isEmpty) {
            Text(stringResource(R.string.prices_incomplete), style = MaterialTheme.typography.bodySmall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                state.sync?.lastSuccessAt?.let { stringResource(R.string.prices_updated_at, DateTimeText.dateTime(it)) }.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry, enabled = !state.refreshing) { Text(stringResource(R.string.prices_refresh)) }
        }
    }
}

private fun LazyListScope.section(
    title: Int,
    prices: List<ReferencePrice>,
    state: ProductPricesUiState,
    onUsePrice: (ReferencePrice) -> Unit,
) {
    if (prices.isEmpty()) return
    item(key = "header-$title") {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
    }
    items(prices, key = { it.externalId }) { price -> PriceCard(price, state, onUsePrice) }
}

@Composable
private fun PriceCard(price: ReferencePrice, state: ProductPricesUiState, onUsePrice: (ReferencePrice) -> Unit) {
    val historical = price.isHistorical(state.today)
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (historical) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val amount = MoneyFormatter.format(price.priceCents)
            Text(
                if (price.basis == PriceBasis.KILOGRAM) stringResource(R.string.prices_per_kg, amount) else amount,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(price.storeName ?: stringResource(R.string.prices_store_unknown), price.storeCity).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(stringResource(R.string.prices_observed, DateTimeText.date(price.observedAt)), style = MaterialTheme.typography.bodySmall)
            if (historical) {
                Text(stringResource(R.string.prices_historical), style = MaterialTheme.typography.labelMedium)
            }
            if (price.isDiscounted) {
                val type = discountLabel(price.discountType)
                Text(
                    type?.let { stringResource(R.string.prices_promo_type, it) } ?: stringResource(R.string.prices_promo),
                    style = MaterialTheme.typography.labelMedium,
                )
                price.priceWithoutDiscountCents?.let {
                    Text(stringResource(R.string.prices_without_discount, MoneyFormatter.format(it)), style = MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedButton(onClick = { onUsePrice(price) }, modifier = Modifier.padding(top = 4.dp)) {
                Text(stringResource(R.string.prices_use))
            }
        }
    }
}

@Composable
private fun discountLabel(type: String?): String? {
    val res = when (type?.uppercase()) {
        "QUANTITY" -> R.string.prices_discount_quantity
        "SALE" -> R.string.prices_discount_sale
        "SEASONAL" -> R.string.prices_discount_seasonal
        "LOYALTY_PROGRAM" -> R.string.prices_discount_loyalty
        "EXPIRES_SOON" -> R.string.prices_discount_expires_soon
        "PICK_IT_YOURSELF" -> R.string.prices_discount_pick_it_yourself
        "SECOND_HAND" -> R.string.prices_discount_second_hand
        "OTHER" -> R.string.prices_discount_other
        else -> return null
    }
    return stringResource(res)
}
