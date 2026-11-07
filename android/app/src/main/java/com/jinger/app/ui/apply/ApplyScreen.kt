package com.jinger.app.ui.apply

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jinger.app.data.AppContainer
import com.jinger.app.data.api.FieldPlanDto
import com.jinger.app.ui.theme.Sun

private const val JS_COLLECT = "JSON.stringify(window.__jinger ? window.__jinger.collectFields() : [])"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplyScreen(container: AppContainer, jobId: String, onBack: () -> Unit) {
    val vm: ApplyViewModel = viewModel(key = "apply-$jobId") {
        ApplyViewModel(jobId, container.jobs, container.profile, container.api)
    }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val autofillJs = remember { context.assets.open("autofill.js").bufferedReader().readText() }
    val webView = remember { mutableWebView() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.job?.company ?: "Apply", style = MaterialTheme.typography.titleMedium)
                        state.job?.let { Text(it.title, style = MaterialTheme.typography.bodySmall, maxLines = 1) }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (state.needsUser.isNotEmpty() && state.stage == ApplyStage.REVIEW) {
                        TextButton(onClick = { vm.setSheet(true) }) { Text("${state.needsUser.size} to review") }
                    }
                },
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp)) {
                StatusLine(state)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) { Text("Later") }
                    Button(
                        onClick = { vm.markSubmitted(onBack) },
                        enabled = state.stage != ApplyStage.LOADING_PAGE,
                        modifier = Modifier.weight(1f),
                    ) { Text("I submitted it") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val url = state.job?.applyUrl
            if (url == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webView.value = this
                            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = true
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onSubmitted() { post { vm.markSubmitted(onBack) } }
                            }, "JingerBridge")
                            val cv = container.profile.cvFile
                            webChromeClient = CvFileChooser(
                                if (cv.exists()) FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", cv) else null,
                            )
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = false
                                override fun onPageFinished(view: WebView, loadedUrl: String) {
                                    vm.onPageLoaded()
                                    view.evaluateJavascript(autofillJs, null)
                                    // Give React forms a moment to render before scanning.
                                    view.postDelayed({ scanAndFill(view, vm) }, 1500)
                                }
                            }
                            loadUrl(url)
                        }
                    },
                )
            }
            if (state.stage == ApplyStage.PLANNING || state.stage == ApplyStage.SCANNING) {
                LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    if (state.sheetOpen) {
        ModalBottomSheet(onDismissRequest = { vm.setSheet(false) }) {
            NeedsUserSheet(
                items = state.needsUser,
                llmUsed = state.llmUsed,
                onEdit = vm::updateDraft,
                onGoTo = { sel ->
                    webView.value?.evaluateJavascript("window.__jinger && window.__jinger.scrollTo(${jsString(sel)})", null)
                    vm.setSheet(false)
                },
                onAccept = { item ->
                    webView.value?.evaluateJavascript(
                        "window.__jinger && window.__jinger.fillField(${jsString(item.selector)}, ${jsString(item.value)})", null,
                    )
                    vm.resolveField(item.selector)
                },
            )
        }
    }
}

private fun scanAndFill(view: WebView, vm: ApplyViewModel) {
    view.evaluateJavascript(JS_COLLECT) { raw ->
        // evaluateJavascript returns a JSON string literal; unwrap it.
        val json = raw.takeIf { it != "null" }?.let { com.google.gson.Gson().fromJson(it, String::class.java) } ?: "[]"
        vm.onFieldsCollected(json) { plan ->
            val needsUser = vm.selectorsJson(vm.state.value.needsUser.map { it.selector })
            view.evaluateJavascript("window.__jinger.fillFields(${vm.toJson(plan)})") {
                view.evaluateJavascript("window.__jinger.highlight($needsUser)", null)
                vm.onFilled()
            }
        }
    }
}

@Composable
private fun StatusLine(state: ApplyUiState) {
    val text = when (state.stage) {
        ApplyStage.LOADING_PAGE -> "Loading application page…"
        ApplyStage.SCANNING -> "Scanning form fields…"
        ApplyStage.PLANNING -> "Drafting answers from your CV…"
        ApplyStage.FILLING -> "Filling the form…"
        ApplyStage.REVIEW -> state.error ?: buildString {
            append("Auto-filled ${state.autoFilled} field(s). ")
            if (state.needsUser.isNotEmpty()) append("${state.needsUser.size} highlighted in yellow need you. ")
            append("Review everything, then tap Submit on the page.")
        }
        ApplyStage.NO_FORM -> "No form found on this page yet — tap the site's Apply button, or fill it in manually."
        ApplyStage.SUBMITTED -> "Marked as applied."
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun NeedsUserSheet(
    items: List<FieldPlanDto>,
    llmUsed: Boolean,
    onEdit: (String, String) -> Unit,
    onGoTo: (String) -> Unit,
    onAccept: (FieldPlanDto) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Text("Needs your input", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            if (llmUsed) "Drafts are grounded in your CV and the job description. Edit freely — nothing is submitted until you tap Submit on the page."
            else "No LLM key configured, so there are no drafted answers. Fill these in on the page yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (items.isEmpty()) Text("All done — nothing left to review.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.selector }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Sun.copy(alpha = 0.15f)), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.label.ifBlank { item.selector }, fontWeight = FontWeight.SemiBold)
                        if (item.reason.isNotBlank()) {
                            Text(item.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(6.dp))
                        OutlinedTextField(
                            value = item.value,
                            onValueChange = { onEdit(item.selector, it) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Your answer") },
                            minLines = 1,
                            maxLines = 6,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { onGoTo(item.selector) }) { Text("Show on page") }
                            Spacer(Modifier.width(4.dp))
                            Button(onClick = { onAccept(item) }, enabled = item.value.isNotBlank()) { Text("Use this") }
                        }
                    }
                }
            }
        }
    }
}

/** Hands the stored CV to the page's <input type=file> so the user never has to browse for it. */
private class CvFileChooser(private val cvUri: Uri?) : WebChromeClient() {
    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean {
        if (cvUri == null) return false
        filePathCallback.onReceiveValue(arrayOf(cvUri))
        return true
    }
}

private fun jsString(s: String): String = com.google.gson.Gson().toJson(s)

@SuppressLint("SetJavaScriptEnabled")
private fun mutableWebView() = androidx.compose.runtime.mutableStateOf<WebView?>(null)
