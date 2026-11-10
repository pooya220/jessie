package ir.jessi.finance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jessi.finance.data.*
import java.util.Locale

private val jadeLight = Color(0xFF008F69)
private val blueLight = Color(0xFF2477C8)
private val violetLight = Color(0xFF8450C8)
private val amberLight = Color(0xFFB47827)

private fun goalTone(category: String, dark: Boolean): Color = when (category) {
    "UNIVERSITY" -> if (dark) Color(0xFFD062FF) else violetLight
    "PURCHASE" -> if (dark) Color(0xFF43C8FF) else blueLight
    "EMERGENCY" -> if (dark) Color(0xFFFFC779) else amberLight
    "TRAVEL" -> if (dark) Color(0xFF36E8C4) else jadeLight
    else -> if (dark) Color(0xFF36E8C4) else jadeLight
}

private fun goalCategoryText(type: String) = when (type) {
    "UNIVERSITY" -> "تحصیل و دانشگاه"
    "PURCHASE" -> "خرید"
    "TRAVEL" -> "سفر"
    "EMERGENCY" -> "صندوق اضطراری"
    else -> "پس‌انداز"
}
private fun goalIcon(type: String): ImageVector = when (type) {
    "UNIVERSITY" -> Icons.Filled.School
    "PURCHASE" -> Icons.Filled.ShoppingBag
    "TRAVEL" -> Icons.Filled.FlightTakeoff
    "EMERGENCY" -> Icons.Filled.Shield
    else -> Icons.Filled.Savings
}

@Composable
fun SavingsGoalsScreen(
    goals: List<SavingsGoal>,
    movements: List<GoalMovement>,
    accounts: List<Account>,
    nowEpoch: Long,
    dark: Boolean,
    onAdd: () -> Unit,
    onEdit: (SavingsGoal) -> Unit,
    onDeposit: (SavingsGoal) -> Unit,
    onWithdraw: (SavingsGoal) -> Unit,
    onArchive: (SavingsGoal) -> Unit,
    onDelete: (SavingsGoal) -> Unit
) {
    val totalSaved = goals.sumOf { SavingsGoalMath.saved(movements, it.id) }
    val active = goals.filterNot { it.archived }
    val monthlyTotal = active.sumOf { it.monthlyToman }
    val current = Jalali.of(nowEpoch)
    val start = JalaliDates.toEpoch(Jalali.Date(current.year, current.month, 1))
        ?: nowEpoch
    val next = JalaliDates.toEpoch(
        JalaliDates.addMonths(Jalali.Date(current.year, current.month, 1), 1)
    ) ?: (nowEpoch + 32L * 24 * 60 * 60 * 1000L)
    val monthlySaved = active.sumOf { SavingsGoalMath.monthlyProgress(movements, it.id, start, next) }
    val purple = if (dark) Color(0xFFD062FF) else violetLight
    val aqua = if (dark) Color(0xFF36E8C4) else jadeLight
    val textMuted = MaterialTheme.colorScheme.onSurfaceVariant
    var showArchived by rememberSaveable { mutableStateOf(false) }

    val shape = RoundedCornerShape(22.dp)
    val heroGradient = if (dark) Brush.linearGradient(
        listOf(Color(0xFF30245C), Color(0xFF16334F), Color(0xFF19413D))
    ) else Brush.linearGradient(
        listOf(Color(0xFFF1ECFC), Color(0xFFEDF8FF), Color(0xFFEAF8F2))
    )
    Column(
        Modifier.fillMaxWidth().clip(shape).background(heroGradient)
            .border(1.dp, purple.copy(alpha = if (dark) .50f else .18f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Surface(color = purple.copy(alpha = .16f), shape = RoundedCornerShape(13.dp)) {
                Icon(Icons.Filled.Savings, contentDescription = null, tint = purple,
                    modifier = Modifier.padding(11.dp).size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text("بانک اهداف", fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                Text("پس‌انداز برای دانشگاه، خرید و هر چیزی که برات مهمه",
                    fontSize = 11.sp, color = textMuted)
            }
        }
        Text(toman(totalSaved), fontWeight = FontWeight.ExtraBold, fontSize = 25.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("جمع پول کنارگذاشته‌شده • جدا از موجودی حساب‌ها",
            color = textMuted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            GoalMetricTile(
                label = "برنامه ماهانه", value = toman(monthlyTotal),
                icon = Icons.Outlined.CalendarMonth, tone = purple,
                modifier = Modifier.weight(1f)
            )
            GoalMetricTile(
                label = "کنارگذاشته این ماه", value = toman(monthlySaved),
                icon = Icons.Outlined.TrendingUp, tone = aqua,
                modifier = Modifier.weight(1f)
            )
        }
        Button(onClick = onAdd, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = purple)) {
            Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("ساخت هدف جدید", fontWeight = FontWeight.Bold)
        }
    }

    if (goals.isEmpty()) {
        GoalSurface(tone = aqua, dark = dark) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Flag, contentDescription = null, tint = aqua,
                    modifier = Modifier.size(30.dp))
                Column {
                    Text("اولین هدف پس‌اندازت رو بساز", fontWeight = FontWeight.Bold)
                    Text("مثلاً «دانشگاه» با ماهی ۳ میلیون تومان یا «خرید لپ‌تاپ» با مبلغ نهایی مشخص.",
                        color = textMuted, fontSize = 12.sp)
                }
            }
        }
    }
    if (goals.any { it.archived }) {
        FilterChip(selected = showArchived, onClick = { showArchived = !showArchived },
            label = { Text("نمایش اهداف بایگانی‌شده") },
            leadingIcon = {
                Icon(Icons.Outlined.Archive, contentDescription = null,
                    modifier = Modifier.size(18.dp))
            })
    }
    goals.filter { !it.archived || showArchived }.forEach { goal ->
        val tone = goalTone(goal.category, dark)
        val saved = SavingsGoalMath.saved(movements, goal.id)
        val monthSaved = SavingsGoalMath.monthlyProgress(movements, goal.id, start, next)
        val planned = goal.monthlyToman
        val hasTarget = goal.targetToman > 0L
        val limit = if (hasTarget) goal.targetToman else planned
        val progress = SavingsGoalMath.fraction(if (hasTarget) saved else monthSaved, limit)
        val goalEntries = movements.filter { it.goalId == goal.id }
        var expandedHistory by rememberSaveable(goal.id) { mutableStateOf(false) }
        GoalSurface(tone, dark) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(color = tone.copy(alpha = if (dark) .24f else .11f),
                    shape = RoundedCornerShape(14.dp)) {
                    Icon(goalIcon(goal.category), contentDescription = null, tint = tone,
                        modifier = Modifier.padding(11.dp).size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(goal.title, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(goalCategoryText(goal.category),
                        fontSize = 11.sp, color = textMuted)
                }
                if (goal.archived) {
                    Text("آرشیو", color = tone, fontSize = 11.sp)
                } else if (hasTarget && saved >= goal.targetToman) {
                    Text("تکمیل!", color = tone, fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold)
                } else {
                    Text(digitsFa((progress * 100).toInt().toString()) + "٪",
                        color = tone, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("پس‌انداز فعلی", color = textMuted, fontSize = 11.sp)
                    Text(toman(saved), fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp, color = tone,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (hasTarget) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text("هدف نهایی", color = textMuted, fontSize = 11.sp)
                        Text(toman(goal.targetToman), fontSize = 12.sp,
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(9.dp)
                    .clip(RoundedCornerShape(10.dp)),
                color = tone, trackColor = tone.copy(alpha = if (dark) .18f else .12f)
            )
            if (planned > 0L) {
                val progressColor = if (monthSaved >= planned) aqua else tone
                Row(Modifier.fillMaxWidth()
                    .background(progressColor.copy(alpha = .09f), RoundedCornerShape(12.dp))
                    .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("این ماه: " + toman(monthSaved), fontSize = 11.sp,
                        color = progressColor, fontWeight = FontWeight.Medium)
                    Text("برنامه: " + toman(planned), fontSize = 11.sp,
                        color = textMuted)
                }
            }
            val forecast = SavingsGoalMath.monthsToGoal(saved, goal.targetToman, planned)
            if (forecast != null && forecast > 0L) {
                Text("با همین برنامه: حدود " + digitsFa(forecast.toString()) + " ماه تا هدف",
                    color = textMuted, fontSize = 11.sp)
            }
            if (goal.deadlineJalali.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Event, contentDescription = null, tint = tone,
                        modifier = Modifier.size(16.dp))
                    Text("تاریخ هدف: " + goal.deadlineJalali, fontSize = 11.sp,
                        color = textMuted)
                }
            }
            if (goal.note.isNotBlank()) Text(goal.note, fontSize = 11.sp, color = textMuted)
            if (!goal.archived) {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(onClick = { onDeposit(goal) }, modifier = Modifier.weight(1f),
                        enabled = accounts.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = tone)) {
                        Icon(Icons.Outlined.Add, contentDescription = null,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("واریز")
                    }
                    OutlinedButton(onClick = { onWithdraw(goal) },
                        enabled = saved > 0L && accounts.isNotEmpty(),
                        modifier = Modifier.weight(1f)) { Text("برداشت") }
                }
            }
            if (goalEntries.isNotEmpty()) {
                HorizontalDivider(color = tone.copy(alpha = .22f))
                TextButton(onClick = { expandedHistory = !expandedHistory }) {
                    Icon(Icons.Outlined.ReceiptLong, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (expandedHistory) "بستن ریز واریزها" else
                        "تاریخچه " + digitsFa(goalEntries.size.toString()) + " جابه‌جایی")
                }
                if (expandedHistory) {
                    goalEntries.sortedWith(compareByDescending<GoalMovement> { it.createdAt }
                        .thenByDescending { it.id }).take(20).forEach { movement ->
                        val deposit = movement.operation == "DEPOSIT"
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (deposit) Icons.Outlined.ArrowDownward
                                else Icons.Outlined.ArrowUpward,
                                contentDescription = null,
                                tint = if (deposit) aqua else tone,
                                modifier = Modifier.size(17.dp)
                            )
                            Column(Modifier.weight(1f)) {
                                Text(if (deposit) "واریز به هدف" else "برداشت از هدف",
                                    fontSize = 11.sp)
                                Text(
                                    (accounts.firstOrNull { it.id == movement.accountId }?.title
                                        ?: "حساب") + " • " + Jalali.of(movement.createdAt),
                                    color = textMuted, fontSize = 10.sp
                                )
                            }
                            Text((if (deposit) "+" else "−") + toman(movement.amountToman),
                                color = if (deposit) aqua else tone,
                                fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                    if (goalEntries.size > 20) {
                        Text("۲۰ جابه‌جایی آخر نمایش داده می‌شود.",
                            color = textMuted, fontSize = 10.sp)
                    }
                }
            }
            HorizontalDivider(color = tone.copy(alpha = .18f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onEdit(goal) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp)); Text("ویرایش")
                }
                Spacer(Modifier.weight(1f))
                if (goal.archived) {
                    TextButton(onClick = { onArchive(goal) }) { Text("بازگردانی از آرشیو") }
                } else if (saved == 0L && goalEntries.isNotEmpty()) {
                    TextButton(onClick = { onArchive(goal) }) { Text("بایگانی") }
                }
                if (goalEntries.isEmpty()) {
                    TextButton(onClick = { onDelete(goal) }) {
                        Text("حذف", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if (active.isNotEmpty()) {
        Text(
            "پول هدف از موجودی حساب انتخابی کم یا به آن بازگردانده می‌شود، " +
                "ولی درآمد و هزینه روزمره محسوب نمی‌شود. واریز ماهانه برنامه‌ریزی است؛ " +
                "برداشت خودکار از بانک انجام نمی‌شود.",
            fontSize = 11.sp, lineHeight = 18.sp, color = textMuted
        )
    }
}

@Composable
private fun GoalMetricTile(
    label: String, value: String, icon: ImageVector, tone: Color,
    modifier: Modifier = Modifier
) {
    val dark = MaterialTheme.colorScheme.background.red < .3f
    Surface(
        modifier = modifier,
        color = if (dark) Color(0xFF172442) else Color(0xFFF4FAFF),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, tone.copy(alpha = if (dark) .44f else .22f))
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(
                tone.copy(alpha = if (dark) .25f else .09f),
                Color.Transparent
            ))).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(17.dp))
                Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, fontWeight = FontWeight.ExtraBold, color = tone,
                fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GoalSurface(
    tone: Color, dark: Boolean, content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val base = if (dark) Color(0xFF12182F) else Color(0xFFF8FBFF)
    val gradient = if (dark) Brush.linearGradient(listOf(
        tone.copy(alpha = .28f),
        Color(0xFF1F254D), Color(0xFF143543), Color(0xFF14192E)
    )) else Brush.linearGradient(listOf(
        tone.copy(alpha = .12f),
        Color(0xFFF4F1FC), Color(0xFFEDF9FC), Color(0xFFFAFCFF)
    ))
    Surface(
        color = base, modifier = Modifier.fillMaxWidth(),
        shape = shape, shadowElevation = if (dark) 8.dp else 5.dp,
        border = BorderStroke(1.dp, tone.copy(alpha = if (dark) .47f else .23f))
    ) {
        Column(Modifier.background(gradient).padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun SavingsGoalFormDialog(
    initial: SavingsGoal?,
    onDismiss: () -> Unit,
    onSave: (SavingsGoal) -> Unit
) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var kind by remember(initial) { mutableStateOf(initial?.category ?: "UNIVERSITY") }
    var target by remember(initial) {
        mutableStateOf(initial?.targetToman?.takeIf { it > 0L }?.toString().orEmpty())
    }
    var monthly by remember(initial) {
        mutableStateOf(initial?.monthlyToman?.takeIf { it > 0L }?.toString().orEmpty())
    }
    var deadline by remember(initial) { mutableStateOf(initial?.deadlineJalali.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    val targetAmount = if (target.isBlank()) 0L else readToman(target)
    val monthlyAmount = if (monthly.isBlank()) 0L else readToman(monthly)
    val valid = title.isNotBlank() && title.length <= 120 &&
        targetAmount != null && monthlyAmount != null &&
        targetAmount in 0L..100_000_000_000L &&
        monthlyAmount in 0L..100_000_000_000L &&
        (targetAmount > 0L || monthlyAmount > 0L) &&
        (deadline.isBlank() || JalaliDates.parse(deadline) != null)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        title = { Text(if (initial == null) "هدف جدید" else "ویرایش هدف",
            fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(Modifier.heightIn(max = 485.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(11.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(120) },
                    label = { Text("عنوان هدف؛ مثلاً دانشگاه") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("نوع هدف", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "UNIVERSITY" to "دانشگاه",
                        "PURCHASE" to "خرید",
                        "TRAVEL" to "سفر",
                        "EMERGENCY" to "اضطراری",
                        "OTHER" to "پس‌انداز"
                    ).forEach { (code, label) ->
                        FilterChip(selected = kind == code, onClick = { kind = code },
                            label = { Text(label, fontSize = 11.sp) })
                    }
                }
                OutlinedTextField(
                    value = monthly, onValueChange = { monthly = normalizeNumericInput(it) },
                    label = { Text("برنامه پس‌انداز ماهانه (تومان)") },
                    placeholder = { Text("مثلاً ۳۰۰۰۰۰۰") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = GroupedNumberVisualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = target, onValueChange = { target = normalizeNumericInput(it) },
                    label = { Text("مبلغ نهایی هدف (تومان؛ اختیاری)") },
                    placeholder = { Text("مثلاً ۳۰۰۰۰۰۰۰") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = GroupedNumberVisualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text("برای پس‌انداز ماهانه دائمی، مبلغ نهایی رو خالی بذار.",
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                JalaliDateInput("تاریخ رسیدن به هدف (اختیاری)",
                    deadline, optional = true, onChange = { deadline = it })
                OutlinedTextField(value = note, onValueChange = { note = it },
                    label = { Text("توضیحات (اختیاری)") },
                    minLines = 2, maxLines = 3, modifier = Modifier.fillMaxWidth())
                Text("ساخت هدف پولی منتقل نمی‌کند؛ برای کنارگذاشتن پول از دکمه واریز استفاده کن.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (valid) onSave(SavingsGoal(
                    id = initial?.id ?: 0L,
                    title = title.trim(), category = kind,
                    targetToman = targetAmount ?: 0L,
                    monthlyToman = monthlyAmount ?: 0L,
                    deadlineJalali = deadline.trim(), note = note.trim(),
                    createdAt = initial?.createdAt ?: System.currentTimeMillis(),
                    archived = initial?.archived ?: false
                ))
            }, enabled = valid) { Text("ذخیره هدف") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
fun GoalMoneyDialog(
    goal: SavingsGoal,
    operation: String,
    accounts: List<Account>,
    transactions: List<Transaction>,
    currentGoalBalance: Long,
    onDismiss: () -> Unit,
    onSubmit: (Long, Long, (Boolean) -> Unit) -> Unit
) {
    val deposit = operation == "DEPOSIT"
    var amountText by remember(goal.id, operation) {
        mutableStateOf(if (deposit && goal.monthlyToman > 0L)
            goal.monthlyToman.toString() else "")
    }
    var accountId by remember(goal.id, operation) {
        mutableStateOf(accounts.firstOrNull()?.id)
    }
    var expanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val amount = readToman(amountText)
    val account = accounts.firstOrNull { it.id == accountId }
    val bankBalance = if (account == null) 0L else
        accountBalance(account.id, account.openingToman, transactions)
    val maxAvailable = if (deposit) bankBalance else currentGoalBalance
    val valid = accountId != null && amount != null &&
        amount in 1L..100_000_000_000L && amount <= maxAvailable

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(24.dp),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (deposit) "واریز به هدف" else "برداشت از هدف",
                    fontWeight = FontWeight.ExtraBold)
                Text(goal.title, fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true },
                        modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                        Text(account?.title ?: "انتخاب حساب بانکی", modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ExpandMore, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        accounts.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.title) },
                                onClick = { accountId = item.id; expanded = false }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = amountText, onValueChange = { amountText = normalizeNumericInput(it) },
                    label = { Text("مبلغ (تومان)") },
                    singleLine = true, enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = GroupedNumberVisualTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(
                    if (deposit) "موجودی حساب: " + toman(bankBalance)
                    else "موجودی هدف: " + toman(currentGoalBalance),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp
                )
                Text(
                    if (deposit) "این انتقال از موجودی حساب کم و به بانک اهداف اضافه می‌شود؛ هزینه نیست."
                    else "این مبلغ از هدف کم و به حساب انتخابی اضافه می‌شود؛ درآمد نیست.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp,
                    lineHeight = 17.sp
                )
                if (!valid && (amount ?: 0L) > maxAvailable) {
                    Text("مبلغ بیش از موجودی قابل برداشت است.",
                        color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                enabled = valid && !busy,
                onClick = {
                    val selectedAccount = accountId ?: return@Button
                    val selectedAmount = amount ?: return@Button
                    busy = true
                    onSubmit(selectedAmount, selectedAccount) { success ->
                        if (!success) busy = false
                    }
                }
            ) { Text(if (deposit) "تأیید واریز" else "تأیید برداشت") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("انصراف") }
        }
    )
}
