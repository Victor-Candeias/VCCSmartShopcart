package pt.vcc.smartshopcart.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.money.QuantityParser
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import java.math.BigDecimal

@Composable
fun sessionItemTitle(item: ShoppingSessionItem): String = item.name ?: item.barcode ?: stringResource(R.string.item_unnamed)

fun sessionItemDetail(item: ShoppingSessionItem): String {
    val quantity = if (item.unit == MeasureUnit.UN && item.quantity.compareTo(BigDecimal.ONE) == 0) {
        null
    } else {
        "${QuantityParser.format(item.quantity)} ${item.unit.symbol} × "
    }
    val discount = if (item.discountCents > 0) " − ${MoneyFormatter.format(item.discountCents)}" else ""
    return (quantity ?: "") + MoneyFormatter.formatPerUnit(item.unitPriceCents, item.unit) + discount
}

@Composable
fun SessionItemRow(
    item: ShoppingSessionItem,
    onClick: (() -> Unit)?,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClickLabel = onClickLabel, onClick = onClick) else it }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(sessionItemTitle(item), style = MaterialTheme.typography.bodyLarge)
            Text(sessionItemDetail(item), style = MaterialTheme.typography.bodySmall)
        }
        Text(MoneyFormatter.format(item.totalCents), style = MaterialTheme.typography.titleMedium)
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_remove_item))
            }
        }
    }
}
