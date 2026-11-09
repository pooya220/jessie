package ir.jessi.finance.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.jessi.finance.ui.JalaliDates
import ir.jessi.finance.ui.Jalali
import java.util.Calendar

/**
 * Pure Persian (Jalali) month/day chooser. Dates are checked with the existing
 * JalaliDates.toEpoch round trip, including 29/30 Esfand and leap years.
 */
@Composable
fun JalaliDateField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    optional: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null,
                modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text(if (value.isBlank()) "انتخاب تاریخ از تقویم شمسی" else value,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
            Text("⌄")
        }
    }
    if (open) {
        JalaliCalendarDialog(
            initial = JalaliDates.parse(value) ?: Jalali.of(System.currentTimeMillis()),
            selected = JalaliDates.parse(value),
            optional = optional,
            onSelect = { chosen -> onChange(chosen); open = false },
            onDismiss = { open = false }
        )
    }
}

private val monthNames = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)
private val weekdays = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

@Composable
private fun JalaliCalendarDialog(
    initial: Jalali.Date,
    selected: Jalali.Date?,
    optional: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var monthIndex by remember(initial) {
        mutableIntStateOf(initial.year * 12 + initial.month - 1)
    }
    val year = Math.floorDiv(monthIndex, 12)
    val month = Math.floorMod(monthIndex, 12) + 1
    val start = JalaliDates.toEpoch(Jalali.Date(year, month, 1))
    val offset = start?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_WEEK) % 7
    } ?: 0
    val count = (31 downTo 28).firstOrNull {
        JalaliDates.toEpoch(Jalali.Date(year, month, it)) != null
    } ?: 0
    val today = Jalali.of(System.currentTimeMillis())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ شمسی", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { monthIndex-- }, enabled = monthIndex > 1300 * 12
                    ) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "ماه قبل")
                    }
                    Text(monthNames[month - 1] + " " + digitsFa(year.toString()),
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { monthIndex++ }, enabled = monthIndex < (1600 * 12 + 11)
                    ) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "ماه بعد")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    weekdays.forEach { title ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(title, style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                repeat(6) { week ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { weekday ->
                            val day = week * 7 + weekday - offset + 1
                            val exists = day in 1..count
                            val current = exists && selected?.year == year &&
                                selected.month == month && selected.day == day
                            val isToday = exists && today.year == year &&
                                today.month == month && today.day == day
                            Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                                if (exists) {
                                    Surface(
                                        onClick = {
                                            onSelect(JalaliDates.format(Jalali.Date(year, month, day)))
                                        },
                                        color = if (current) MaterialTheme.colorScheme.primary
                                            else if (isToday) MaterialTheme.colorScheme.primaryContainer
                                            else MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(50.dp)
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                digitsFa(day.toString()),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (current) MaterialTheme.colorScheme.onPrimary
                                                    else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
        dismissButton = if (optional) {
            { TextButton(onClick = { onSelect("") }) { Text("بدون تاریخ") } }
        } else null
    )
}
