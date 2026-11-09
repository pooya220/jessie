package ir.jessi.finance.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.jessi.finance.data.FinanceBackup
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.platform.LocalContext

/** Uses Android's native document picker: no broad storage permission required. */
@Composable
fun FinanceBackupControls() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<FinanceBackup.Preview?>(null) }
    var confirmed by remember(selected) { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }

    fun showError(error: Throwable) {
        status = error.message ?: "عملیات فایل انجام نشد"
    }

    val exportFile = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    val summary = FinanceBackup.exportTo(context, uri)
                    status = "فایل پشتیبان ساخته شد:\n" + summary
                } catch (e: Exception) { showError(e) }
                finally { busy = false }
            }
        }
    }
    val importFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && !busy) {
            busy = true
            scope.launch {
                try {
                    selected = FinanceBackup.load(context, uri)
                    status = "فایل بررسی شد و آماده تأیید است."
                } catch (e: Exception) { showError(e) }
                finally { busy = false }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("از حساب‌ها، تراکنش‌ها، اقساط، بودجه‌ها، صف پیامک و تنظیمات یک فایل پشتیبان بگیر یا از فایل قبلی بازیابی کن.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = {
                val filename = "Jessi-backup-" +
                    SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date()) + ".json"
                exportFile.launch(filename)
            },
            modifier = Modifier.fillMaxWidth(), enabled = !busy
        ) {
            Icon(Icons.Outlined.FileDownload, contentDescription = null,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("خروجی و ذخیره فایل پشتیبان")
        }
        OutlinedButton(
            onClick = {
                importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            },
            modifier = Modifier.fillMaxWidth(), enabled = !busy
        ) {
            Icon(Icons.Outlined.FileUpload, contentDescription = null,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("فراخوانی اطلاعات از فایل")
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (status.isNotBlank()) {
            Text(status, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("فایل JSON رمزگذاری نشده و شامل اطلاعات مالی است؛ آن را فقط در جای امن نگهداری کن.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error)
    }
    val preview = selected
    if (preview != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) selected = null },
            title = { Text("تأیید بازیابی اطلاعات") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("اطلاعات فایل انتخابی:", fontWeight = FontWeight.Bold)
                    Text(preview.summary())
                    Text("هشدار: بازیابی، تمام اطلاعات مالی فعلی و صف پیامک‌های فعلی را با داده‌های این فایل جایگزین می‌کند. اول از وضعیت فعلی خروجی بگیر.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                    Row {
                        Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                        Text("از اطلاعات فعلی پشتیبان دارم و جایگزینی را تأیید می‌کنم.",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (busy || !confirmed) return@Button
                    busy = true
                    scope.launch {
                        try {
                            FinanceBackup.restore(context, preview)
                            selected = null
                            Toast.makeText(context, "اطلاعات با موفقیت بازیابی شد",
                                Toast.LENGTH_LONG).show()
                            (context as? Activity)?.recreate()
                        } catch (e: Exception) { showError(e) }
                        finally { busy = false }
                    }
                }, enabled = confirmed && !busy) { Text("بازیابی و جایگزینی") }
            },
            dismissButton = {
                TextButton(onClick = { selected = null }, enabled = !busy) { Text("انصراف") }
            }
        )
    }
}
