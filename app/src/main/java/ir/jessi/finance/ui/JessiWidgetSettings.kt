package ir.jessi.finance.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jessi.finance.data.Budget
import ir.jessi.finance.widgets.JessiWidgetPrefs

/**
 * Independent widget configuration. Selection uses durable budget IDs (not
 * editable labels), so renamed budgets remain selected without data changes.
 */
@Composable
fun JessiWidgetSettings(
    budgets: List<Budget>,
    showBalance: Boolean,
    onShowBalance: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var selected by remember(budgets) {
        mutableStateOf(JessiWidgetPrefs.selectedBudgets(context, budgets))
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Widgets, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("ویجت‌های هوم‌اسکرین", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
        }
        Text(
            "ویجت‌ها را از بخش Widgets لانچر گوشی اضافه کن؛ اقساط و اهداف " +
                "مستقیماً روی هوم‌اسکرین قابل پیمایش هستند.",
            fontSize = 11.sp,
            color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("نمایش پیش‌فرض موجودی در ویجت",
                    fontSize = 13.sp,
                    color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                Text("برای تغییر سریع در هر ویجت، دکمه چشم همان ویجت را لمس کن.",
                    fontSize = 10.sp,
                    color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant))
            }
            Switch(checked = showBalance, onCheckedChange = onShowBalance)
        }
        Text(
            "توجه: با روشن‌کردن چشم، مبلغ روی صفحه اصلی گوشی " +
                "حتی بدون بازکردن قفل جسی دیده می‌شود.",
            fontSize = 10.sp,
            color = jessiReadableText(MaterialTheme.colorScheme.error)
        )
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("بودجه‌های داخل ویجت", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.weight(1f))
            Text(selected.size.toString() + " / ۵",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
        Text(
            "حداکثر پنج بودجه را انتخاب کن. هر پنج مورد در همان " +
                "سایز کوچک ویجت بودجه نمایش داده می‌شوند.",
            fontSize = 11.sp,
            color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant)
        )
        if (budgets.isEmpty()) {
            Text("هنوز بودجه‌ای ثبت نشده. اول از بخش بودجه‌بندی سقف ایجاد کن.",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        budgets.forEach { budget ->
            val checked = budget.id in selected
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Checkbox(
                    checked = checked,
                    enabled = checked || selected.size < 5,
                    onCheckedChange = { enabled ->
                        val next = if (enabled) {
                            (selected + budget.id).distinct().take(5)
                        } else selected - budget.id
                        selected = next
                        JessiWidgetPrefs.saveBudgets(context, next)
                    }
                )
                Text(budget.category, modifier = Modifier.weight(1f),
                    fontSize = 13.sp,
                    color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
            }
        }
        Text(
            "ویجت موجودی با فلش‌های خودش جمع کل و حساب‌ها را ورق می‌زند. " +
                "تیک‌های اقساط فقط فهرست کارهای پرداخت‌نشده‌اند؛ " +
                "ثبت پرداخت از داخل جسی انجام می‌شود.",
            fontSize = 10.sp,
            color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant)
        )
    }
}
