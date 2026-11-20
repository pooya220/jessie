package ir.jessi.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LockOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Edits only the category catalog unless the user confirms a rename.
 * In that case MainActivity/Room migrate the associated labels in one transaction.
 */
@Composable
fun FinanceCategorySettings(
    onRename: (kind: String, old: String, name: String,
        callback: (Boolean, String?) -> Unit) -> Unit
) {
    var kind by rememberSaveable { mutableStateOf(FinanceCategories.EXPENSE) }
    var input by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val labels = if (kind == FinanceCategories.INCOME)
        FinanceCategories.income else FinanceCategories.expense
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "مدیریت دسته‌بندی‌ها",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = jessiReadableText(MaterialTheme.colorScheme.onSurface)
        )
        Text(
            "دسته‌بندی‌های درآمد و هزینه را جداگانه بساز، ویرایش یا حذف کن.",
            fontSize = 11.sp,
            color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = kind == FinanceCategories.EXPENSE,
                onClick = {
                    kind = FinanceCategories.EXPENSE
                    message = null
                },
                label = { Text("هزینه‌ها") }
            )
            FilterChip(
                selected = kind == FinanceCategories.INCOME,
                onClick = {
                    kind = FinanceCategories.INCOME
                    message = null
                },
                label = { Text("درآمدها") }
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("نام دسته جدید") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )
            Button(
                onClick = {
                    message = FinanceCategories.add(kind, input)
                    if (message == null) input = ""
                },
                modifier = Modifier.padding(top = 7.dp),
                enabled = !busy
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(3.dp))
                Text("افزودن")
            }
        }
        message?.let {
            Text(it, fontSize = 11.sp,
                color = jessiReadableText(MaterialTheme.colorScheme.error))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .25f))
        labels.forEach { label ->
            val system = FinanceCategories.isProtected(kind, label)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    label,
                    modifier = Modifier.weight(1f).padding(top = 10.dp),
                    color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
                    fontSize = 13.sp
                )
                if (system) {
                    Icon(
                        Icons.Outlined.LockOutline,
                        contentDescription = "دسته سیستمی، برای حفظ محاسبات ثابت است",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(19.dp).padding(top = 2.dp)
                    )
                } else {
                    IconButton(onClick = {
                        editing = label
                        editText = label
                        message = null
                    }, modifier = Modifier.size(39.dp), enabled = !busy) {
                        Icon(Icons.Outlined.Edit,
                            contentDescription = "تغییر نام $label",
                            modifier = Modifier.size(19.dp))
                    }
                    IconButton(onClick = {
                        deleting = label
                        message = null
                    }, modifier = Modifier.size(39.dp), enabled = !busy) {
                        Icon(Icons.Outlined.DeleteOutline,
                            contentDescription = "حذف $label",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
        Text(
            "با تغییر نام دسته، سوابق تراکنش‌های مرتبط و سقف بودجه همان دسته نیز " +
                "به‌روزرسانی می‌شوند. حذف دسته فقط آن را از فهرست انتخاب حذف می‌کند؛ " +
                "تراکنش‌ها و بودجه‌های قبلی پاک نمی‌شوند. دسته‌های سیستمی " +
                "برای حفظ ثبت درآمد و دوره حقوق ثابت می‌مانند.",
            fontSize = 10.sp,
            color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant),
            lineHeight = 17.sp
        )
    }

    editing?.let { old ->
        AlertDialog(
            onDismissRequest = { if (!busy) editing = null },
            title = { Text("ویرایش دسته «$old»") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        label = { Text("نام جدید") },
                        singleLine = true
                    )
                    Text(
                        "برچسب این دسته در تراکنش‌های قبلی و بودجه مرتبط هم تغییر می‌کند.",
                        fontSize = 11.sp
                    )
                    message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy && editText.trim() != old,
                    onClick = {
                        val check = FinanceCategories.validation(kind, editText, old)
                        if (check != null) {
                            message = check
                        } else {
                            busy = true
                            onRename(kind, old, editText.trim()) { ok, problem ->
                                busy = false
                                if (ok) {
                                    editing = null
                                    message = null
                                } else message = problem ?: "ویرایش دسته‌بندی ممکن نشد"
                            }
                        }
                    }
                ) { Text(if (busy) "در حال ثبت" else "ذخیره تغییر") }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }, enabled = !busy) {
                    Text("انصراف")
                }
            }
        )
    }
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف دسته «$target»؟") },
            text = {
                Text(
                    "فقط از فهرست دسته‌های قابل انتخاب حذف می‌شود. " +
                        "هیچ تراکنش، مبلغ یا بودجه قبلی پاک نخواهد شد."
                )
            },
            confirmButton = {
                Button(onClick = {
                    message = FinanceCategories.delete(kind, target)
                    deleting = null
                }) { Text("حذف از فهرست") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("انصراف") }
            }
        )
    }
}
