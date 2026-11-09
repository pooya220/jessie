package ir.jessi.finance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/** Persian (Jalali) month picker, dates validated using the existing JalaliDates converter. */
@Composable
fun JalaliDateInput(label: String, value: String, optional: Boolean = false, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(value.ifBlank { "انتخاب تاریخ شمسی" }, modifier = Modifier.weight(1f))
            Text("تغییر", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
        }
        if (optional && value.isNotBlank()) {
            TextButton(onClick = { onChange("") }) { Text("بدون سررسید", fontSize = 11.sp) }
        }
    }
    if (expanded) {
        JalaliCalendarPopup(
            initial = JalaliDates.parse(value) ?: Jalali.of(System.currentTimeMillis()),
            allowClear = optional,
            onDismiss = { expanded = false },
            onSelect = { date ->
                onChange(date?.let(JalaliDates::format) ?: "")
                expanded = false
            }
        )
    }
}

@Composable
private fun JalaliCalendarPopup(
    initial: Jalali.Date, allowClear: Boolean, onDismiss: () -> Unit,
    onSelect: (Jalali.Date?) -> Unit
) {
    var year by remember(initial) { mutableIntStateOf(initial.year) }
    var month by remember(initial) { mutableIntStateOf(initial.month) }
    val months = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val days = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
    val firstEpoch = JalaliDates.toEpoch(Jalali.Date(year, month, 1))
    val saturdayOffset = firstEpoch?.let {
        Calendar.getInstance().apply { timeInMillis = it }.get(Calendar.DAY_OF_WEEK) % 7
    } ?: 0
    val maxDay = (31 downTo 28).firstOrNull {
        JalaliDates.toEpoch(Jalali.Date(year, month, it)) != null
    } ?: 29
    val current = Jalali.of(System.currentTimeMillis())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("انتخاب تاریخ شمسی", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = {
                        val d = JalaliDates.addMonths(Jalali.Date(year, month, 1), 1)
                        if (d.year <= 1600) { year = d.year; month = d.month }
                    }) {
                        Icon(Icons.Outlined.ChevronRight, contentDescription = "ماه بعد")
                    }
                    Text(months[month - 1] + " " + digitsFa(year.toString()),
                        fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = {
                        val d = JalaliDates.addMonths(Jalali.Date(year, month, 1), -1)
                        if (d.year >= 1300) { year = d.year; month = d.month }
                    }) {
                        Icon(Icons.Outlined.ChevronLeft, contentDescription = "ماه قبل")
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    days.forEach { day ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(day, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val cells = saturdayOffset + maxDay
                for (week in 0 until (cells + 6) / 7) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (weekday in 0..6) {
                            val dayNumber = week * 7 + weekday - saturdayOffset + 1
                            val date = Jalali.Date(year, month, dayNumber)
                            val valid = dayNumber in 1..maxDay
                            val isToday = valid && date == current
                            val selected = valid && date == initial
                            val activeColor = MaterialTheme.colorScheme.primary
                            Box(
                                Modifier.weight(1f).height(40.dp)
                                    .then(
                                        if (valid) Modifier
                                            .background(
                                                if (selected) activeColor.copy(alpha = .19f)
                                                else if (isToday) activeColor.copy(alpha = .08f)
                                                else Color.Transparent,
                                                RoundedCornerShape(9.dp)
                                            )
                                            .clickable { onSelect(date) }
                                        else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (valid) Text(digitsFa(dayNumber.toString()),
                                    color = if (selected || isToday) activeColor
                                        else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (selected || isToday) FontWeight.Bold
                                        else FontWeight.Normal,
                                    fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelect(current) }) { Text("امروز") }
        },
        dismissButton = {
            Row {
                if (allowClear) TextButton(onClick = { onSelect(null) }) { Text("پاک‌کردن") }
                TextButton(onClick = onDismiss) { Text("انصراف") }
            }
        }
    )
}
