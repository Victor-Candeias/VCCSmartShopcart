package pt.vcc.smartshopcart.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import pt.vcc.smartshopcart.domain.model.MeasureUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnitSelector(selected: MeasureUnit, onSelect: (MeasureUnit) -> Unit, modifier: Modifier = Modifier) {
    val units = MeasureUnit.entries
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        units.forEachIndexed { index, unit ->
            SegmentedButton(
                selected = selected == unit,
                onClick = { onSelect(unit) },
                shape = SegmentedButtonDefaults.itemShape(index, units.size),
            ) { Text(unit.symbol) }
        }
    }
}
