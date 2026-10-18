package pt.vcc.smartshopcart.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.domain.usecase.BudgetLevel
import pt.vcc.smartshopcart.domain.usecase.BudgetStatus

/** Total e orçamento; o estado é comunicado por texto além da cor. */
@Composable
fun BudgetBar(status: BudgetStatus, modifier: Modifier = Modifier) {
    val color = when (status.level) {
        BudgetLevel.EXCEEDED -> MaterialTheme.colorScheme.error
        BudgetLevel.WARNING -> Color(0xFFB26A00)
        else -> MaterialTheme.colorScheme.primary
    }
    Column(modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.session_total), style = MaterialTheme.typography.titleMedium)
            Text(
                MoneyFormatter.format(status.totalCents),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (status.budgetCents != null && status.remainingCents != null) {
            LinearProgressIndicator(
                progress = { status.usedFraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = color,
            )
            val text = when (status.level) {
                BudgetLevel.EXCEEDED -> stringResource(
                    R.string.budget_exceeded,
                    MoneyFormatter.format(-status.remainingCents),
                    MoneyFormatter.format(status.budgetCents),
                )
                BudgetLevel.WARNING -> stringResource(
                    R.string.budget_warning,
                    MoneyFormatter.format(status.remainingCents),
                    MoneyFormatter.format(status.budgetCents),
                )
                else -> stringResource(
                    R.string.budget_remaining,
                    MoneyFormatter.format(status.remainingCents),
                    MoneyFormatter.format(status.budgetCents),
                )
            }
            Text(text, color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
