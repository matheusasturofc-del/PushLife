package com.pushlife.app.ui

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.pushlife.app.R
import com.pushlife.app.data.CrashLog

// o app fechou sozinho da última vez: pergunta se pode mandar o relatório
@Composable
fun CrashReportDialog() {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.pending(context)) }
    val text = report ?: return
    AlertDialog(
        onDismissRequest = {
            CrashLog.clear(context)
            report = null
        },
        title = { Text(stringResource(R.string.crash_title)) },
        text = { Text(stringResource(R.string.crash_body)) },
        confirmButton = {
            TextButton(onClick = {
                sendReport(context, context.getString(R.string.crash_subject), text)
                CrashLog.clear(context)
                report = null
            }) { Text(stringResource(R.string.crash_send)) }
        },
        dismissButton = {
            TextButton(onClick = {
                CrashLog.clear(context)
                report = null
            }) { Text(stringResource(R.string.crash_skip)) }
        },
    )
}

// "Relatar um problema" no Perfil: e-mail com a versão e o aparelho já preenchidos
fun sendProblemReport(context: Context) =
    sendReport(context, context.getString(R.string.problem_subject), context.getString(R.string.problem_body_hint) + "\n\n\n---\n" + CrashLog.header(context))

private fun sendReport(context: Context, subject: String, body: String) {
    runCatching { context.startActivity(CrashLog.emailIntent(subject, body)) }
}
