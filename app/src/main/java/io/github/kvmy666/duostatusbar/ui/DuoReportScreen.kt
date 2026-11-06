package io.github.kvmy666.duostatusbar.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import io.github.kvmy666.duostatusbar.RootLogs
import io.github.kvmy666.duostatusbar.R
import io.github.kvmy666.duostatusbar.settings.DuoPrefs
import io.github.kvmy666.duostatusbar.settings.TelegramLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val REPORT_CATEGORIES = listOf("Bug", "Device Support", "Feature Request")

/**
 * In-app report form. The ticket number is created locally and copied into the Telegram log
 * caption; the report ZIP is delivered through the existing relay. The channel link is the
 * follow-up path, so no separate Telegram ticket bot is required for this flow.
 */
@Composable
fun DuoReportScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var problem by rememberSaveable { mutableStateOf("") }
    var telegramUser by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(REPORT_CATEGORIES.first()) }
    var sending by remember { mutableStateOf(false) }
    var ticket by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("") }

    if (ticket.isBlank()) {
        Column(
            modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                // The top scroll-edge glass is 72dp tall; 48dp below the status-bar inset keeps the
                // headline and first field out from under it without changing the page layout.
                .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.report_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.report_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(stringResource(R.string.report_label), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REPORT_CATEGORIES.forEach { label ->
                    FilterChip(
                        selected = category == label,
                        onClick = { category = label },
                        label = { Text(label) }
                    )
                }
            }
            OutlinedTextField(
                value = problem,
                onValueChange = { problem = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.report_describe)) },
                minLines = 5,
                enabled = !sending
            )
            OutlinedTextField(
                value = telegramUser,
                onValueChange = { telegramUser = it.take(64) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.report_telegram_username)) },
                supportingText = { Text(stringResource(R.string.report_telegram_hint)) },
                singleLine = true,
                enabled = !sending
            )
            val collectingText = stringResource(R.string.report_collecting)
            val sentText = stringResource(R.string.report_sent)
            val failedText = stringResource(R.string.report_failed)
            Button(
                onClick = {
                    if (problem.isBlank() || sending) return@Button
                    sending = true
                    status = collectingText
                    scope.launch {
                        val settings = DuoPrefs.read(context)
                        val logs = withContext(Dispatchers.IO) { RootLogs.collect(context) }
                        val report = buildFullReport(
                            context,
                            problem,
                            settings,
                            DuoPrefs.status(context),
                            DuoPrefs.statusHistory(context),
                            DuoPrefs.dump(context),
                            0L,
                            logs
                        )
                        val zip = withContext(Dispatchers.IO) {
                            writeReportZip(
                                context,
                                report,
                                logs,
                                DuoPrefs.status(context),
                                DuoPrefs.dump(context)
                            )
                        }
                        val handle = telegramUser.trim().let {
                            if (it.isBlank()) "" else if (it.startsWith("@")) it else "@$it"
                        }
                        val caption = "Ticket #PENDING · [$category]${if (handle.isBlank()) "" else " $handle"} · ${problem.take(180)}"
                        val result = if (zip != null) {
                            withContext(Dispatchers.IO) {
                                TelegramLog.sendWithTicket(context, zip, caption)
                            }
                        } else {
                            TelegramLog.SendResult(TelegramLog.Result.FAILED)
                        }
                        sending = false
                        val assigned = result.ticket
                        if (result.result == TelegramLog.Result.SENT && !assigned.isNullOrBlank()) {
                            ticket = assigned
                            status = sentText
                        } else {
                            status = failedText
                        }
                    }
                },
                enabled = problem.isNotBlank() && !sending,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (sending) stringResource(R.string.report_sending) else stringResource(R.string.report_send))
            }
            if (status.isNotBlank()) {
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(stringResource(R.string.report_ticket_created), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.report_ticket_keep), style = MaterialTheme.typography.bodyMedium)
        GlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.report_ticket_label), style = MaterialTheme.typography.labelMedium)
                Text(ticket, style = MaterialTheme.typography.headlineMedium)
                Text("$category · ${problem.take(120)}", style = MaterialTheme.typography.bodySmall)
                if (telegramUser.isNotBlank()) {
                    Text(stringResource(R.string.report_telegram_user, telegramUser), style = MaterialTheme.typography.bodySmall)
                }
                Text(status, style = MaterialTheme.typography.bodySmall)
            }
        }
        val copiedText = stringResource(R.string.report_ticket_copied)
        val shareChooserText = stringResource(R.string.report_share_chooser)
        Button(
            onClick = {
                clipboard.setText(AnnotatedString(ticket))
                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.report_copy_ticket)) }
        OutlinedButton(
            onClick = {
                val publicText = "Ticket: $ticket\nLabel: $category\nStatus: Open\n\n$problem"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, publicText)
                }
                context.startActivity(Intent.createChooser(send, shareChooserText))
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.report_share_public)) }
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TELEGRAM_CHAT_URL)))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.report_contact_developer)) }
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/duoReportsGroup")))
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.report_follow_telegram)) }
        OutlinedButton(
            onClick = {
                ticket = ""
                problem = ""
                telegramUser = ""
                status = ""
                category = REPORT_CATEGORIES.first()
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.report_create_another)) }
    }
}
