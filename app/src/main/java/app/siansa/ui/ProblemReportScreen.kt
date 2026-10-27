package app.siansa.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import app.siansa.log.AppLog
import app.siansa.log.Report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/** Where a report is: being written, on its way, sent, or failed (issue #414). */
private sealed interface Sending {
    data object Idle : Sending
    data object Busy : Sending
    data class Sent(val result: Report.Result) : Sending
    data class Failed(val why: String) : Sending
}

/** Report a problem to the beta team: two short answers and the whole log, kept private on the Siansa server. */
@Composable
fun ProblemReportScreen(nav: NavController) {
    val graph = LocalGraph.current
    val context = LocalContext.current
    val settings by graph.settings.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var happened by rememberSaveable { mutableStateOf("") }
    var expected by rememberSaveable { mutableStateOf("") }
    var repeated by rememberSaveable { mutableStateOf(false) }
    var sending by remember { mutableStateOf<Sending>(Sending.Idle) }
    val logBytes = AppLog.sizeBytes()
    val problem = Report.problem(happened, expected, settings.logging, logBytes, repeated)

    fun send() {
        sending = Sending.Busy
        scope.launch {
            sending = withContext(Dispatchers.IO) {
                runCatching {
                    val archive = AppLog.exportArchive(File(context.cacheDir, "logs/siansa-report.zip"))
                    val request = Request.Builder()
                        .url(Report.URL)
                        .header("X-Report", Report.header(happened, expected, AppLog.about, settings.sourceKind.name))
                        .post(archive.asRequestBody("application/zip".toMediaType()))
                        .build()
                    val client = graph.http.newBuilder().callTimeout(5, TimeUnit.MINUTES).writeTimeout(2, TimeUnit.MINUTES).build()
                    client.newCall(request).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        archive.delete()
                        if (response.isSuccessful) {
                            val result = Report.parse(body)
                            if (result == null) Sending.Failed("The server's answer wasn't understood.") else Sending.Sent(result)
                        } else {
                            AppLog.w("Report", "Report refused: HTTP ${response.code}")
                            Sending.Failed(Report.failure(response.code))
                        }
                    }
                }.getOrElse { e ->
                    AppLog.w("Report", "Report not sent", e)
                    Sending.Failed("The report couldn't be sent. Check your connection and try again.")
                }
            }
            (sending as? Sending.Sent)?.let { AppLog.i("Report", "Report sent (id ${it.result.id}, issue ${it.result.issue})") }
        }
    }

    Screen(title = "Report a problem", nav = nav) { padding ->
        Column(
            // Ends above the keyboard, so the box being typed in scrolls into view (beta testers #10).
            Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize().aboveKeyboard()
                .verticalScroll(rememberScrollState())
                .padding(Space.Page),
            verticalArrangement = Arrangement.spacedBy(Space.Medium),
        ) {
            val state = sending
            if (state is Sending.Sent) {
                Text("Thank you", style = MaterialTheme.typography.titleLarge)
                Hint(
                    "Your report is in (id ${state.result.id}). " +
                        if (state.result.issue != null) "It's issue #${state.result.issue} in the beta testers repo, where you can follow it."
                        else "It will be looked at soon.",
                )
                state.result.url?.let { url ->
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Open the issue") }
                }
                OutlinedButton(onClick = { nav.popBackStack() }) { Text("Done") }
            } else {
                ReportBody(
                    happened = happened,
                    expected = expected,
                    onHappened = { happened = it.take(Report.MAX_TEXT) },
                    onExpected = { expected = it.take(Report.MAX_TEXT) },
                    logging = settings.logging,
                    onLogging = { on ->
                        graph.settings.setLogging(on)
                        AppLog.setEnabled(on)
                    },
                    logSize = formatBytes(logBytes),
                    repeated = repeated,
                    onRepeated = { repeated = it },
                    busy = state is Sending.Busy,
                    error = (state as? Sending.Failed)?.why,
                    ready = problem == null,
                    onSend = ::send,
                )
                // The log comes first: what's missing there shows straight away; the words once some are typed.
                problem?.let { if (!settings.logging || logBytes <= 0 || happened.isNotEmpty() || expected.isNotEmpty()) Hint(it) }
            }
        }
    }
}

/** The report form itself, without the page around it (so spacing can be checked on its own). */
@Composable
internal fun ReportBody(
    happened: String,
    expected: String,
    onHappened: (String) -> Unit,
    onExpected: (String) -> Unit,
    logging: Boolean,
    onLogging: (Boolean) -> Unit,
    logSize: String,
    repeated: Boolean,
    onRepeated: (Boolean) -> Unit,
    busy: Boolean,
    error: String?,
    ready: Boolean,
    onSend: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.Medium)) {
        // Before anything else (#567): a report always carries the log, and one that shows the problem a few times.
        Text(
            "Before you send: turn on debug logs, then make the problem happen a few times. The log goes with every " +
                "report, so we can see exactly what went wrong.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.testTag("report-steps"),
        )
        SwitchRow("Debug logs", logging, onLogging)
        Hint(
            if (logging) "The log is $logSize and goes with your report."
            else "Debug logs are off, so nothing is being recorded. Turn them on and repeat the problem.",
        )
        Hint(
            "Tell us what went wrong. Your words are posted as a short note in the public beta testers repo. " +
                "The whole log stays private: it's kept on the Siansa server and in a private GitHub repository, and may be " +
                "read with AI tools to find the problem. It records every request in full, including your server's address " +
                "and what you played. Passwords, tokens and API keys are removed.",
        )
        OutlinedTextField(
            happened, onHappened,
            label = { Text("What happened?") },
            minLines = 3, maxLines = 6,
            supportingText = { Text("${happened.length} / ${Report.MAX_TEXT}") },
            modifier = Modifier.fillMaxWidth().testTag("report-happened"),
        )
        OutlinedTextField(
            expected, onExpected,
            label = { Text("What did you expect to happen?") },
            minLines = 2, maxLines = 5,
            supportingText = { Text("${expected.length} / ${Report.MAX_TEXT}") },
            modifier = Modifier.fillMaxWidth().testTag("report-expected"),
        )
        Row(
            Modifier.fillMaxWidth().toggleable(value = repeated, enabled = logging, role = Role.Checkbox, onValueChange = onRepeated).testTag("report-repeated"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = repeated, onCheckedChange = null, enabled = logging)
            Spacer(Modifier.width(Space.Small))
            Text("I made the problem happen a few times with debug logs on", style = MaterialTheme.typography.bodyMedium)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onSend, enabled = ready && !busy, modifier = Modifier.testTag("report-send")) { Text("Send report") }
            if (busy) {
                Spacer(Modifier.width(Space.Medium))
                CircularProgressIndicator(Modifier.size(Space.Section))
                Spacer(Modifier.width(Space.Small))
                Text("Sending…", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
