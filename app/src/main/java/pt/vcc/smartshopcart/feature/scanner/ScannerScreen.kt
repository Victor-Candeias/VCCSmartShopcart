package pt.vcc.smartshopcart.feature.scanner

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.money.MoneyFormatter
import pt.vcc.smartshopcart.core.ui.components.AmountField
import pt.vcc.smartshopcart.core.ui.components.NameField
import pt.vcc.smartshopcart.core.ui.components.QuantityField
import pt.vcc.smartshopcart.core.ui.components.UnitSelector
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PriceKind
import pt.vcc.smartshopcart.domain.usecase.ProductLookupStatus
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onAdded: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var manualEntry by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ScannerEvent.Added -> onAdded()
                ScannerEvent.SessionNotActive -> snackbar.showSnackbar(context.getString(R.string.session_not_active))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.scanner_title))
                        state.listItemName?.let {
                            Text(stringResource(R.string.scanner_for_item, it), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when {
            state.loading -> Box(modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            state.missing -> Box(modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.session_missing)) }
            state.inactive -> Box(modifier.fillMaxSize(), Alignment.Center) { Text(stringResource(R.string.session_not_active)) }
            state.phase == ScanPhase.CONFIRM && state.draft != null -> ConfirmContent(state, viewModel, modifier)
            state.phase == ScanPhase.RESOLVING -> Column(
                modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
                Text(stringResource(R.string.scanner_resolving), Modifier.padding(top = 16.dp))
            }
            else -> ScanningContent(
                state = state,
                onCodes = viewModel::onCodesDetected,
                onPhotoStarted = viewModel::onPhotoStarted,
                onPhotoRecognized = { viewModel.onPhotoRecognized(it.barcodes, it.lines) },
                onPhotoFailed = viewModel::onPhotoFailed,
                onReview = viewModel::onReviewDraft,
                onManualEntry = { manualEntry = true },
                modifier = modifier,
            )
        }
    }

    if (manualEntry) {
        ManualCodeDialog(
            onDismiss = { manualEntry = false },
            onConfirm = {
                manualEntry = false
                viewModel.onManualCode(it)
            },
        )
    }
    state.invalidCode?.let { code ->
        AlertDialog(
            onDismissRequest = viewModel::onDismissMessage,
            title = { Text(stringResource(R.string.scanner_invalid_title)) },
            text = { Text(stringResource(R.string.scanner_invalid_text, code)) },
            confirmButton = {
                TextButton(onClick = viewModel::onDismissMessage) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun ScanningContent(
    state: ScannerUiState,
    onCodes: (List<String>) -> Unit,
    onPhotoStarted: () -> Unit,
    onPhotoRecognized: (CaptureRecognition) -> Unit,
    onPhotoFailed: () -> Unit,
    onReview: () -> Unit,
    onManualEntry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
    }
    LaunchedEffect(Unit) {
        if (!hasPermission && !asked) {
            asked = true
            launcher.launch(Manifest.permission.CAMERA)
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val recognizer = remember { PhotoRecognizer() }
    DisposableEffect(recognizer) { onDispose { recognizer.close() } }
    val scope = rememberCoroutineScope()
    var cameraError by remember { mutableStateOf(false) }
    val draft = state.draft

    Column(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            if (hasPermission) {
                if (cameraError) {
                    Text(stringResource(R.string.scanner_camera_unavailable), Modifier.padding(24.dp), textAlign = TextAlign.Center)
                } else {
                    CameraPreview(
                        imageCapture = imageCapture,
                        onCodes = onCodes,
                        onError = { cameraError = true },
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(0.8f)
                            .height(160.dp)
                            .border(BorderStroke(3.dp, Color.White), RoundedCornerShape(12.dp)),
                    )
                    if (state.recognizing) CircularProgressIndicator()
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    ) {
                        val message = when {
                            state.recognizing -> stringResource(R.string.scanner_recognizing)
                            state.multipleCodes -> stringResource(R.string.scanner_multiple)
                            state.nothingRecognized -> stringResource(R.string.scanner_nothing)
                            draft?.barcode != null ->
                                stringResource(R.string.scanner_have_product, draft.name.ifBlank { draft.barcode })
                            draft?.ocrPriceCents != null ->
                                stringResource(R.string.scanner_have_price, MoneyFormatter.format(draft.ocrPriceCents!!))
                            else -> stringResource(R.string.scanner_hint)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                message,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            if (draft != null && !state.recognizing) {
                                TextButton(onClick = onReview) { Text(stringResource(R.string.scanner_review)) }
                            }
                        }
                    }
                }
            } else {
                Column(
                    Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.scanner_permission_denied), textAlign = TextAlign.Center)
                    OutlinedButton(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                        Text(stringResource(R.string.scanner_permission_request))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onManualEntry,
                enabled = !state.recognizing,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(stringResource(R.string.scanner_manual)) }
            Button(
                onClick = {
                    onPhotoStarted()
                    imageCapture.takePicture(
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                scope.launch {
                                    try {
                                        onPhotoRecognized(recognizer.recognize(image))
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (_: Exception) {
                                        onPhotoFailed()
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) = onPhotoFailed()
                        },
                    )
                },
                enabled = hasPermission && !cameraError && !state.recognizing,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(stringResource(R.string.scanner_photo)) }
        }
    }
}

@Composable
private fun CameraPreview(
    imageCapture: ImageCapture,
    onCodes: (List<String>) -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCodes by rememberUpdatedState(onCodes)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }

    DisposableEffect(lifecycleOwner) {
        val executor = Executors.newSingleThreadExecutor()
        val analyzer = BarcodeAnalyzer { codes -> ContextCompat.getMainExecutor(context).execute { currentOnCodes(codes) } }
        val providerFuture = ProcessCameraProvider.getInstance(context)
        var provider: ProcessCameraProvider? = null
        var disposed = false
        providerFuture.addListener({
            if (disposed) return@addListener
            try {
                val cameraProvider = providerFuture.get().also { provider = it }
                val selector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    CameraSelector.DEFAULT_BACK_CAMERA
                } else {
                    CameraSelector.DEFAULT_FRONT_CAMERA
                }
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(executor, analyzer) }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, analysis, imageCapture)
            } catch (_: Exception) {
                currentOnError()
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            provider?.unbindAll()
            analyzer.close()
            executor.shutdown()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
@Composable
private fun ManualCodeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val digits = code.filter(Char::isDigit)
    val plausible = digits.length in setOf(8, 12, 13)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scanner_manual)) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(13) },
                label = { Text(stringResource(R.string.scanner_manual_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(enabled = plausible, onClick = { onConfirm(digits) }) { Text(stringResource(R.string.scanner_search)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun ConfirmContent(state: ScannerUiState, viewModel: ScannerViewModel, modifier: Modifier = Modifier) {
    val draft = state.draft ?: return
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val found = draft.productId != null
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (found) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.tertiaryContainer
                },
            ),
            modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(draft.status?.messageRes() ?: R.string.scanner_no_barcode),
                    style = MaterialTheme.typography.titleSmall,
                )
                draft.barcode?.let { Text(stringResource(R.string.item_barcode, it), style = MaterialTheme.typography.bodyMedium) }
                listOfNotNull(draft.brand, draft.quantityLabel).takeIf { it.isNotEmpty() }?.let {
                    Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        NameField(draft.name, { v -> viewModel.onDraftChange { it.copy(name = v) } }, stringResource(R.string.item_name))
        Text(stringResource(R.string.item_unit), style = MaterialTheme.typography.labelLarge)
        UnitSelector(draft.unit, { unit -> viewModel.onDraftChange { it.copy(unit = unit) } })
        if (draft.priceCandidates.isNotEmpty()) {
            PriceCandidates(draft, viewModel::onSelectCandidate)
        }
        AmountField(
            draft.price,
            { v -> viewModel.onDraftChange { it.copy(price = v) } },
            if (draft.unit.priceUnit == MeasureUnit.UN) {
                stringResource(R.string.item_price)
            } else {
                stringResource(R.string.item_price_per, draft.unit.priceUnit.symbol)
            },
            draft.price.isNotBlank() && draft.priceCents == null,
        )
        draft.suggestedPriceCents?.let { cents ->
            Text(
                draft.suggestedStoreName?.let { store ->
                    stringResource(R.string.scanner_last_price_store, MoneyFormatter.format(cents), store)
                } ?: stringResource(R.string.scanner_last_price, MoneyFormatter.format(cents)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        QuantityField(draft.quantity, { v -> viewModel.onDraftChange { it.copy(quantity = v) } }, draft.unit, draft.quantityValue == null)
        Text(
            stringResource(R.string.item_line_total, draft.totalCents?.let(MoneyFormatter::format) ?: "—"),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedButton(
            onClick = viewModel::onCaptureMore,
            enabled = !state.saving,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(if (draft.barcode == null) R.string.scanner_read_code else R.string.scanner_photo_price))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = viewModel::onScanAnother,
                enabled = !state.saving,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(stringResource(R.string.scanner_scan_another)) }
            Button(
                onClick = viewModel::onConfirm,
                enabled = draft.totalCents != null && !state.saving,
                modifier = Modifier.weight(1f).height(56.dp),
            ) { Text(stringResource(R.string.action_add)) }
        }
    }
}

private fun ProductLookupStatus.messageRes(): Int = when (this) {
    ProductLookupStatus.LOCAL -> R.string.scanner_status_local
    ProductLookupStatus.REMOTE -> R.string.scanner_status_remote
    ProductLookupStatus.NOT_FOUND -> R.string.scanner_status_not_found
    ProductLookupStatus.OFFLINE -> R.string.scanner_status_offline
    ProductLookupStatus.REMOTE_ERROR -> R.string.scanner_status_remote_error
    ProductLookupStatus.INTERNAL_CODE -> R.string.scanner_status_internal
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PriceCandidates(draft: ScanDraft, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.scanner_prices_found), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.priceCandidates.forEachIndexed { index, candidate ->
                FilterChip(
                    selected = index == draft.selectedCandidate,
                    onClick = { onSelect(index) },
                    label = { Text("${MoneyFormatter.format(candidate.cents)} · ${stringResource(candidate.kind.labelRes())}") },
                )
            }
        }
        Text(
            stringResource(if (draft.priceFromOcr) R.string.scanner_price_auto else R.string.scanner_price_choose),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

private fun PriceKind.labelRes(): Int = when (this) {
    PriceKind.SALE -> R.string.scanner_kind_sale
    PriceKind.OLD -> R.string.scanner_kind_old
    PriceKind.PER_KG -> R.string.scanner_kind_per_kg
    PriceKind.PER_L -> R.string.scanner_kind_per_l
    PriceKind.DISCOUNT -> R.string.scanner_kind_discount
    PriceKind.UNKNOWN -> R.string.scanner_kind_unknown
}
