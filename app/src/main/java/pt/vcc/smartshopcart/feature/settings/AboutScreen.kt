package pt.vcc.smartshopcart.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.BuildConfig
import pt.vcc.smartshopcart.R

private const val OPEN_FOOD_FACTS_URL = "https://world.openfoodfacts.org"
private const val OPEN_PRICES_URL = "https://prices.openfoodfacts.org"

/** D24: versão, atribuição Open Food Facts e Open Prices (ODbL) e licenças. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_about)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium)

            Section(R.string.about_off_title, R.string.about_off_text)
            OutlinedButton(onClick = { uriHandler.openUri(OPEN_FOOD_FACTS_URL) }) {
                Text(stringResource(R.string.about_off_open))
            }

            Section(R.string.about_prices_title, R.string.about_prices_text)
            OutlinedButton(onClick = { uriHandler.openUri(OPEN_PRICES_URL) }) {
                Text(stringResource(R.string.about_prices_open))
            }

            Section(R.string.about_licenses_title, R.string.about_licenses_text)
        }
    }
}

@Composable
private fun Section(title: Int, text: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}
