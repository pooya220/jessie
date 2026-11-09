package ir.jessi.finance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.jessi.finance.sms.BankSmsParser

/** Test-only sandbox: never receives, sends, stores or imports any transaction. */
@Composable
fun BankSmsTester() {
    var sender by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var tested by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BankSmsParser.Result?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("یک پیامک بانکی را اینجا بچسبان تا الگوریتم واقعی جسی بررسی‌اش کند.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = sender, onValueChange = { sender = it; tested = false },
            label = { Text("نام یا شماره فرستنده (اختیاری)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = message, onValueChange = { message = it; tested = false },
            label = { Text("متن پیامک برای تست") },
            minLines = 4, maxLines = 7, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                message = "بانک سينا\nبرداشت از حساب\nمبلغ 1,000,000 ريال\n" +
                    "مانده 2,819,181 ريال\nزمان : 1405/8/18 ; 13:20:08"
                sender = "SINA"
                tested = false
            }) { Text("نمونه سینا") }
            OutlinedButton(onClick = {
                message = "900,000-\n1405/7/11-2:14\nمانده:8,988,388"
                sender = "300359900000"
                tested = false
            }) { Text("نمونه بدون واحد") }
        }
        Button(onClick = {
            result = BankSmsParser.parse(message, sender)
            tested = true
        }, enabled = message.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Science, contentDescription = null,
                modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("آزمایش تشخیص پیامک")
        }
        if (tested) {
            val parsed = result
            if (parsed == null) {
                Text("شناسایی نشد؛ پیامک با قوانین فعلی جسی تطبیق ندارد.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("شناسایی شد: " + if (parsed.kind == "IN") "واریز" else "برداشت",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold)
                Text("بانک: " + parsed.bankName.ifBlank { "نام بانک شناسایی نشد" })
                if (parsed.amountUnit == BankSmsParser.UNKNOWN) {
                    Text("مبلغ خام: " + digitsFa(parsed.amountRaw.toString()) +
                        " (ریال یا تومان نامشخص؛ هنگام ثبت باید انتخاب شود)")
                } else {
                    Text("مبلغ: " + toman(parsed.amountToman))
                    Text("واحد پیامک: " +
                        if (parsed.amountUnit == BankSmsParser.RIAL) "ریال" else "تومان")
                }
            }
        }
        Text("حالت آزمایشی: هیچ تراکنشی ذخیره نمی‌شود، موجودی تغییر نمی‌کند و پیامک یا اعلانی ارسال نمی‌شود.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
