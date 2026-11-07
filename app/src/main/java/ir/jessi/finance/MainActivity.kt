package ir.jessi.finance

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import ir.jessi.finance.sms.SmsReviewNotifications
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.jessi.finance.data.*
import ir.jessi.finance.ui.*


private val background: Color @Composable get() = MaterialTheme.colorScheme.background
private val surface: Color @Composable get() = MaterialTheme.colorScheme.surface
private val secondary: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
private val mint: Color @Composable get() = MaterialTheme.colorScheme.primary
private val muted: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
private val danger: Color @Composable get() = MaterialTheme.colorScheme.error

private val lightColors = lightColorScheme(
    primary = Color(0xFF008A62), onPrimary = Color.White,
    secondary = Color(0xFF087D91), background = Color(0xFFF6FAFB),
    surface = Color.White, surfaceVariant = Color(0xFFECF3F5),
    onSurface = Color(0xFF17272E), onSurfaceVariant = Color(0xFF617582),
    error = Color(0xFFDC4261), outline = Color(0xFFDCE8EC)
)
private val darkColors = darkColorScheme(
    primary = Color(0xFFBA8AFF), onPrimary = Color(0xFF16102F),
    secondary = Color(0xFF40C9FF), background = Color(0xFF080F26),
    surface = Color(0xFF131B3C), surfaceVariant = Color(0xFF202952),
    onSurface = Color(0xFFF5F4FF), onSurfaceVariant = Color(0xFFADB9D7),
    error = Color(0xFFFF749E), outline = Color(0xFF37446B)
)
private val LocalJessiDark = compositionLocalOf { true }


class MainActivity : ComponentActivity() {
    private val notificationReviewId = mutableLongStateOf(-1L)
    private val nowEpoch = mutableLongStateOf(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationReviewId.longValue = intent.getLongExtra(SmsReviewNotifications.EXTRA_REVIEW_ID, -1L)
        setContent {
            val themePrefs = remember { getSharedPreferences("jessi_ui_theme", MODE_PRIVATE) }
            var darkMode by rememberSaveable { mutableStateOf(themePrefs.getBoolean("is_dark", true)) }
            SideEffect {
                window.statusBarColor = if (darkMode) android.graphics.Color.rgb(8, 15, 38)
                    else android.graphics.Color.rgb(246, 250, 251)
                window.navigationBarColor = if (darkMode) android.graphics.Color.rgb(8, 15, 38)
                    else android.graphics.Color.rgb(246, 250, 251)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !darkMode
                    isAppearanceLightNavigationBars = !darkMode
                }
            }
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalJessiDark provides darkMode
            ) {
                MaterialTheme(colorScheme = if (darkMode) darkColors else lightColors) {
                    JessiHome(
                        openReviewId = notificationReviewId.longValue,
                        nowEpoch = nowEpoch.longValue,
                        darkMode = darkMode,
                        onToggleTheme = {
                            darkMode = !darkMode
                            themePrefs.edit().putBoolean("is_dark", darkMode).apply()
                        },
                        onReviewOpened = { notificationReviewId.longValue = -1L }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nowEpoch.longValue = System.currentTimeMillis()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationReviewId.longValue = intent.getLongExtra(SmsReviewNotifications.EXTRA_REVIEW_ID, -1L)
    }
}

private enum class Tab(val label: String, val icon: String) {
    HOME("داشبورد", "⌂"), DEBTS("بدهی و قرض", "⇆"),
    BUDGETS("سقف پرداخت", "◉"), TXS("تراکنش‌ها", "≋"), ACCOUNTS("حساب‌ها", "▣")
}
private sealed interface Editor {
    data class AccountForm(val value: Account? = null) : Editor
    data class TxForm(val value: Transaction? = null) : Editor
    data class DebtForm(val value: Debt? = null) : Editor
    data class BudgetForm(val value: Budget? = null) : Editor
    data class Repay(val value: Debt) : Editor
    data class Review(val value: SmsReview) : Editor
    data class Delete(val title: String, val action: () -> Unit) : Editor
}

@Composable
private fun JessiHome(openReviewId: Long, nowEpoch: Long, darkMode: Boolean, onToggleTheme: () -> Unit, onReviewOpened: () -> Unit, vm: FinanceViewModel = viewModel()) {
    val accounts by vm.accounts.collectAsState()
    val txs by vm.transactions.collectAsState()
    val debts by vm.debts.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val reviews by vm.reviews.collectAsState()
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("jessi_finance_settings", android.content.Context.MODE_PRIVATE) }
    var periodMode by remember { mutableStateOf(settings.getString("period_mode", FinancePeriodEngine.MONTHLY) ?: FinancePeriodEngine.MONTHLY) }
    var periodOffset by rememberSaveable { mutableIntStateOf(0) }
    val periods = remember(txs, periodMode, nowEpoch) { FinancePeriodEngine.periods(txs, periodMode, nowEpoch) }
    val selectedIndex = periodOffset.coerceIn(0, (periods.size - 1).coerceAtLeast(0))
    val chosenPeriod = periods[selectedIndex]
    val periodTransactions = remember(txs, chosenPeriod) { txs.filter { chosenPeriod.contains(it.createdAt) } }
    var smsPermitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsPermitted = it }
    var notificationsPermitted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsPermitted = it }
    var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    val tab = Tab.valueOf(tabName)
    var editor by remember { mutableStateOf<Editor?>(null) }

    LaunchedEffect(openReviewId, reviews) {
        if (openReviewId > 0L) {
            reviews.firstOrNull { it.id == openReviewId }?.let {
                editor = Editor.Review(it)
                onReviewOpened()
            }
        }
    }

    Scaffold(
        containerColor = background,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(15.dp),
                    color = if (darkMode) Color(0xFF332158) else Color(0xFFE2F8EC)
                ) {
                    Text(if (darkMode) "◈" else "❀",
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                        fontSize = 25.sp, color = mint, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("جسی", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                    Text("مدیریت مالی شخصی", color = muted, fontSize = 11.sp)
                }
                Surface(
                    onClick = onToggleTheme,
                    shape = RoundedCornerShape(30.dp),
                    color = secondary,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(if (darkMode) "☾" else "☀", color = mint, fontSize = 21.sp)
                        Text(if (darkMode) "دارک" else "لایت",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = surface, tonalElevation = 0.dp) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tabName = item.name },
                        icon = { Text(item.icon, fontSize = 18.sp) },
                        label = { Text(item.label, fontSize = 9.sp, maxLines = 1) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = mint, selectedTextColor = mint,
                            indicatorColor = secondary, unselectedIconColor = muted,
                            unselectedTextColor = muted
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.HOME) {
                FloatingActionButton(onClick = {
                    editor = when (tab) {
                        Tab.ACCOUNTS -> Editor.AccountForm()
                        Tab.TXS -> Editor.TxForm()
                        Tab.DEBTS -> Editor.DebtForm()
                        Tab.BUDGETS -> Editor.BudgetForm()
                        Tab.HOME -> null
                    }
                }, containerColor = mint) {
                    Text("+", fontSize = 29.sp, color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PeriodSwitcher(
                mode = periodMode, period = chosenPeriod, selectedIndex = selectedIndex,
                count = periods.size,
                salaryAvailable = txs.any { it.kind == "IN" && it.category == "حقوق" },
                onModeChange = {
                    periodMode = it
                    periodOffset = 0
                    settings.edit().putString("period_mode", it).apply()
                },
                onPrevious = { periodOffset = (selectedIndex + 1).coerceAtMost(periods.lastIndex) },
                onNext = { periodOffset = (selectedIndex - 1).coerceAtLeast(0) }
            )
            when (tab) {
                Tab.HOME -> Dashboard(
                    accounts, txs, debts, budgets, reviews,
                    currentPeriod = chosenPeriod, periods = periods, selectedPeriodIndex = selectedIndex,
                    nowEpoch = nowEpoch,
                    smsPermitted = smsPermitted,
                    requestPermission = { permissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                    notificationsPermitted = notificationsPermitted,
                    requestNotificationPermission = { if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onReview = { editor = Editor.Review(it) },
                    onNewTx = { editor = Editor.TxForm() },
                    onSelectTab = { tabName = it.name }
                )
                Tab.ACCOUNTS -> AccountsPage(accounts, txs,
                    onEdit = { editor = Editor.AccountForm(it) },
                    onDelete = { row -> editor = Editor.Delete("حساب «${row.title}» حذف شود؟ تنها حساب بدون تراکنش قابل حذف است.") { vm.deleteAccount(row.id) } }
                )
                Tab.TXS -> TransactionsPage(periodTransactions, accounts,
                    onEdit = { editor = Editor.TxForm(it) },
                    onDelete = { row -> editor = Editor.Delete("این تراکنش حذف شود؟") { vm.deleteTransaction(row) } }
                )
                Tab.DEBTS -> DebtsPage(debts, chosenPeriod, nowEpoch,
                    onEdit = { editor = Editor.DebtForm(it) },
                    onPay = { editor = Editor.Repay(it) },
                    onDelete = { row -> editor = Editor.Delete("این بدهی یا طلب حذف شود؟") { vm.deleteDebt(row.id) } }
                )
                Tab.BUDGETS -> BudgetsPage(budgets, periodTransactions,
                    onEdit = { editor = Editor.BudgetForm(it) },
                    onDelete = { row -> editor = Editor.Delete("سقف «${row.category}» حذف شود؟") { vm.deleteBudget(row.id) } }
                )
            }
            Spacer(Modifier.height(86.dp))
        }
    }

    when (val e = editor) {
        is Editor.AccountForm -> AccountDialog(e.value, dismiss = { editor = null }) { vm.saveAccount(it); editor = null }
        is Editor.TxForm -> TransactionDialog(e.value, accounts, dismiss = { editor = null }, onSave = { vm.saveTransaction(it); editor = null }, onTransfer = { a, b, amount, note -> vm.transfer(a,b,amount,note); editor = null })
        is Editor.DebtForm -> DebtDialog(e.value, dismiss = { editor = null }) { vm.saveDebt(it); editor = null }
        is Editor.BudgetForm -> BudgetDialog(e.value, budgets, dismiss = { editor = null }) { vm.saveBudget(it); editor = null }
        is Editor.Repay -> RepayDialog(e.value, accounts, dismiss = { editor = null }) { amount, accountId -> vm.settleDebt(e.value, amount, accountId); editor = null }
        is Editor.Review -> ReviewDialog(e.value, accounts, dismiss = { editor = null }, onAccept = { id, category, note -> vm.acceptReview(e.value, id, category, note); editor = null }, onReject = { vm.dismissReview(e.value.id); editor = null })
        is Editor.Delete -> AlertDialog(onDismissRequest = { editor = null }, title = { Text("تأیید حذف") }, text = { Text(e.title) }, confirmButton = { TextButton(onClick = { e.action(); editor = null }) { Text("حذف", color = danger) } }, dismissButton = { TextButton(onClick = { editor = null }) { Text("انصراف") } })
        null -> Unit
    }
}

@Composable
private fun FinanceSummaryChip(title: String, value: Long, income: Boolean, modifier: Modifier = Modifier) {
    val color = if (income) Color(0xFF00C68E) else Color(0xFFFF689C)
    val chipColor = if (LocalJessiDark.current) color.copy(alpha = .14f)
        else if (income) Color(0xFFDDF8EA) else Color(0xFFFFE7EE)
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(chipColor).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text((if (income) "↗ " else "↘ ") + title, fontSize = 11.sp,
            fontWeight = FontWeight.Bold, color = if (LocalJessiDark.current) color
                else if (income) Color(0xFF007D56) else Color(0xFFC82C56))
        Text(toman(value), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            color = if (LocalJessiDark.current) color
                else if (income) Color(0xFF007D56) else Color(0xFFC82C56),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DashboardAction(iconText: String, title: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier,
        color = surface, shape = RoundedCornerShape(17.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(iconText, fontSize = 23.sp, color = mint, fontWeight = FontWeight.Bold)
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun PeriodSwitcher(
    mode: String, period: FinancePeriod, selectedIndex: Int, count: Int,
    salaryAvailable: Boolean, onModeChange: (String) -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit
) {
    Tile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("دوره مالی", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            FilterChip(selected = mode == FinancePeriodEngine.MONTHLY,
                onClick = { onModeChange(FinancePeriodEngine.MONTHLY) }, label = { Text("ماه شمسی", fontSize = 11.sp) })
            FilterChip(selected = mode == FinancePeriodEngine.SALARY,
                onClick = { onModeChange(FinancePeriodEngine.SALARY) }, label = { Text("از حقوق", fontSize = 11.sp) })
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onPrevious, enabled = selectedIndex < count - 1) { Text("› قبلی") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(period.label, color = mint, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(if (selectedIndex == 0) "دوره جاری" else "دوره گذشته",
                    fontSize = 10.sp, color = muted)
            }
            TextButton(onClick = onNext, enabled = selectedIndex > 0) { Text("بعدی ‹") }
        }
        if (mode == FinancePeriodEngine.SALARY && !salaryAvailable) {
            Text("هنوز واریز تأییدشده‌ای با دسته «حقوق» نیست؛ فعلاً دوره‌ها ماهانه نمایش داده می‌شن.",
                color = muted, fontSize = 11.sp)
        } else if (mode == FinancePeriodEngine.SALARY) {
            Text("با تأیید هر واریز به دسته «حقوق»، دوره جدید به‌صورت خودکار شروع می‌شه.",
                color = muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = muted)
    }
}

@Composable
private fun Tile(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier, color = surface, shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = if (LocalJessiDark.current) .45f else .55f)),
        shadowElevation = if (LocalJessiDark.current) 0.dp else 2.dp
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
    }
}

@Composable
private fun Tag(text: String, good: Boolean = false) {
    Surface(
        color = if (good) mint.copy(alpha = .13f) else secondary,
        shape = RoundedCornerShape(30.dp)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            color = if (good) mint else muted, fontSize = 11.sp)
    }
}

@Composable
private fun MoneyLine(label: String, value: Long, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = muted, fontSize = 12.sp)
        Text(toman(value), color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun isThisMonth(epoch: Long): Boolean = Jalali.of(epoch).monthKey == Jalali.of(System.currentTimeMillis()).monthKey

@Composable
private fun Dashboard(
    accounts: List<Account>, txs: List<Transaction>, debts: List<Debt>, budgets: List<Budget>, reviews: List<SmsReview>,
    currentPeriod: FinancePeriod, periods: List<FinancePeriod>, selectedPeriodIndex: Int, nowEpoch: Long,
    smsPermitted: Boolean, requestPermission: () -> Unit,
    notificationsPermitted: Boolean, requestNotificationPermission: () -> Unit,
    onReview: (SmsReview) -> Unit, onNewTx: () -> Unit, onSelectTab: (Tab) -> Unit
) {
    val total = accounts.sumOf { accountBalance(it.id, it.openingToman, txs) }
    val current = txs.filter { currentPeriod.contains(it.createdAt) }
    val income = current.filter { it.kind == "IN" }.sumOf { it.amountToman }
    val expense = current.filter { it.kind == "OUT" }.sumOf { it.amountToman }
    // Visual hierarchy matches the selected design: balance hero, colorful spending
    // donut, six-period trend, quick actions, installments, recent activity.
    val darkMode = LocalJessiDark.current
    val heroGradient = if (darkMode)
        Brush.linearGradient(listOf(Color(0xFF164078), Color(0xFF28215C), Color(0xFF4E2377)))
    else Brush.linearGradient(listOf(Color(0xFFE8F8F2), Color(0xFFEDF8FF), Color(0xFFF7F4FF)))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(heroGradient)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .35f)), RoundedCornerShape(24.dp))
            .padding(19.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("موجودی کل حساب‌ها", color = muted, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("▣", color = mint, fontSize = 22.sp)
        }
        Text(toman(total), fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface)
        Text("بر اساس حساب‌ها و تراکنش‌های ثبت‌شده", color = muted, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceSummaryChip(
                title = "درآمد دوره", value = income, income = true,
                modifier = Modifier.weight(1f)
            )
            FinanceSummaryChip(
                title = "هزینه دوره", value = expense, income = false,
                modifier = Modifier.weight(1f)
            )
        }
    }
    Tile(Modifier.fillMaxWidth()) { SpendingCategoryChart(current) }
    Tile(Modifier.fillMaxWidth()) { FinancialTrendChart(periods, selectedPeriodIndex, txs) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DashboardAction("＋", "تراکنش", Modifier.weight(1f), onNewTx)
        DashboardAction("▦", "اقساط", Modifier.weight(1f)) { onSelectTab(Tab.DEBTS) }
        DashboardAction("▣", "حساب‌ها", Modifier.weight(1f)) { onSelectTab(Tab.ACCOUNTS) }
        DashboardAction("◫", "بودجه", Modifier.weight(1f)) { onSelectTab(Tab.BUDGETS) }
    }
    val upcomingDebt = debts.filter { it.direction == "I_OWE" && it.installmentMonths > 0 }
        .mapNotNull { debt ->
            Installments.nextDate(debt)?.let { date ->
                JalaliDates.toEpoch(date)?.let { due -> Triple(debt, date, due) }
            }
        }.minByOrNull { it.third }
    if (upcomingDebt != null) {
        val (debt, date, _) = upcomingDebt
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("قسط بعدی", fontWeight = FontWeight.Bold)
                    Text(debt.person, color = muted, fontSize = 12.sp)
                }
                Tag("باقی‌مانده " + digitsFa(Installments.remainingCount(debt).toString()) + " قسط", true)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(toman(Installments.nextAmount(debt)), fontSize = 17.sp, color = mint, fontWeight = FontWeight.Bold)
                    Text("سررسید " + JalaliDates.format(date), color = muted, fontSize = 12.sp)
                }
                TextButton(onClick = { onSelectTab(Tab.DEBTS) }) { Text("مدیریت") }
            }
        }
    }
    Tile(Modifier.fillMaxWidth()) { InstallmentForecastChart(debts, nowEpoch) }
    val installmentDue = debts.sumOf { Installments.dueInPeriod(it, currentPeriod.start, currentPeriod.endExclusive) }
    if (installmentDue > 0L) {
        Tile(Modifier.fillMaxWidth()) {
            Text("اقساط سررسیدشده در این دوره", fontWeight = FontWeight.Bold)
            MoneyLine("مبلغ برنامه‌ریزی‌شده", installmentDue, danger)
            TextButton(onClick = { onSelectTab(Tab.DEBTS) }) { Text("مدیریت اقساط") }
        }
    }
    if (!smsPermitted) {
        Tile(Modifier.fillMaxWidth()) {
            Text("دریافت پیامک‌های جدید غیرفعال است", fontWeight = FontWeight.Bold)
            Text("برای بررسی پیامک‌های بانکی جدید، مجوز SMS لازم است. ممکن است نصب دستی APK در اندروید این مجوز را محدود کند.", color = muted, fontSize = 12.sp)
            Button(onClick = requestPermission) { Text("درخواست مجوز دریافت SMS") }
        }
    } else {
        Tile(Modifier.fillMaxWidth()) {
            Text("✓ دریافت پیامک‌های جدید فعال است", color = mint, fontWeight = FontWeight.Bold)
            Text("فقط پیامک‌های تازه بررسی می‌شوند؛ تا تأیید نکنی تراکنش ثبت نمی‌شود. کدهای تأیید و متن اصلی پیام ذخیره نمی‌شوند.", fontSize = 12.sp, color = muted)
        }
    }
    if (!notificationsPermitted) {
        Tile(Modifier.fillMaxWidth()) {
            Text("اعلان‌های پیامک بانکی غیرفعال است", fontWeight = FontWeight.Bold)
            Text("برای نمایش اعلان ثبت یا نادیده گرفتن پیامک‌های جدید، اجازه اعلان‌ها را فعال کن. بدون این مجوز پیامک‌ها همچنان داخل داشبورد منتظر بررسی می‌مانند.", fontSize = 12.sp, color = muted)
            Button(onClick = requestNotificationPermission) { Text("فعال‌کردن اعلان‌ها") }
        }
    }
    if (reviews.isNotEmpty()) {
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("پیامک‌های منتظر بررسی", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Tag(digitsFa(reviews.size.toString()), true)
            }
            reviews.take(5).forEach { row ->
                HorizontalDivider(color = secondary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (row.kind == "IN") "واریز احتمالی" else "برداشت احتمالی")
                        Text("${toman(row.amountToman)} • ${row.sender}", fontSize = 12.sp, color = muted)
                    }
                    TextButton(onClick = { onReview(row) }) { Text("بررسی") }
                }
            }
        }
    }
    if (debts.any { it.totalToman > it.paidToman }) {
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("بدهی و طلب‌های باز", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = { onSelectTab(Tab.DEBTS) }) { Text("مشاهده") }
            }
            MoneyLine("مجموع بدهی باقی‌مانده", debts.filter { it.direction == "I_OWE" }.sumOf { it.totalToman - it.paidToman }, danger)
            MoneyLine("مجموع طلب باقی‌مانده", debts.filter { it.direction == "OWED_TO_ME" }.sumOf { it.totalToman - it.paidToman }, mint)
        }
    }
    if (budgets.isNotEmpty()) {
        Tile(Modifier.fillMaxWidth()) {
            Text("سقف هزینه‌های دوره", fontWeight = FontWeight.Bold)
            budgets.take(4).forEach { budget ->
                val spent = current.filter { it.kind == "OUT" && it.category == budget.category }.sumOf { it.amountToman }
                val fraction = (spent.toFloat() / budget.monthlyLimitToman.coerceAtLeast(1)).coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(budget.category, fontSize = 12.sp)
                    Text("${digitsFa((fraction * 100).toInt().toString())}٪", fontSize = 12.sp, color = if (spent >= budget.monthlyLimitToman) danger else mint)
                }
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = if (spent >= budget.monthlyLimitToman) danger else mint, trackColor = secondary)
            }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionTitle("تراکنش‌های اخیر")
        Spacer(Modifier.weight(1f))
        TextButton(onClick = { onSelectTab(Tab.TXS) }) { Text("مشاهده همه") }
    }
    if (current.isEmpty()) EmptyNote("هنوز تراکنشی در این دوره ثبت نشده.")
    else current.filter { it.kind != "TRANSFER_IN" }.take(5).forEach { TransactionTile(it, accounts, txs, null, null) }
}

@Composable
private fun EmptyNote(message: String) {
    Tile(Modifier.fillMaxWidth()) { Text(message, fontSize = 13.sp, color = muted) }
}

@Composable
private fun AccountsPage(accounts: List<Account>, txs: List<Transaction>, onEdit: (Account) -> Unit, onDelete: (Account) -> Unit) {
    SectionTitle("حساب‌های من", "افزودن حساب از دکمه +")
    if (accounts.isEmpty()) EmptyNote("حسابی ثبت نشده. برای شروع روی + بزن و موجودی اولیه را وارد کن.")
    accounts.forEach { acc ->
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(acc.title, fontWeight = FontWeight.Bold)
                    Text(listOf(acc.bank, if (acc.last4.isNotBlank()) "•••• ${acc.last4}" else "").filter { it.isNotBlank() }.joinToString(" • "), color = muted, fontSize = 12.sp)
                }
                Tag("حساب بانکی")
            }
            Text(toman(accountBalance(acc.id, acc.openingToman, txs)), color = mint, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = { onEdit(acc) }) { Text("ویرایش") }
                TextButton(onClick = { onDelete(acc) }, enabled = txs.none { it.accountId == acc.id }) { Text("حذف", color = if (txs.none { it.accountId == acc.id }) danger else muted) }
            }
        }
    }
}

@Composable
private fun TransactionsPage(txs: List<Transaction>, accounts: List<Account>, onEdit: (Transaction) -> Unit, onDelete: (Transaction) -> Unit) {
    SectionTitle("تراکنش‌ها", "ثبت دستی، پیامک بانکی و انتقال بین حساب‌ها")
    if (txs.isEmpty()) EmptyNote("برای ثبت اولین تراکنش روی + بزن.")
    txs.filter { it.kind != "TRANSFER_IN" }.forEach { row ->
        TransactionTile(row, accounts, txs, if (row.kind in listOf("IN", "OUT")) ({ onEdit(row) }) else null, if (row.kind in listOf("DEBT_IN", "DEBT_OUT")) null else ({ onDelete(row) }))
    }
}

@Composable
private fun TransactionTile(row: Transaction, accounts: List<Account>, txs: List<Transaction>, edit: (() -> Unit)?, delete: (() -> Unit)?) {
    val credit = row.kind in listOf("IN", "DEBT_IN")
    val transferTarget = if (row.kind == "TRANSFER_OUT") txs.firstOrNull { it.groupId == row.groupId && it.kind == "TRANSFER_IN" }?.accountId else null
    val description = if (transferTarget != null) "انتقال به ${accounts.firstOrNull { it.id == transferTarget }?.title.orEmpty()}" else row.category
    Tile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(description, fontWeight = FontWeight.Medium)
                Text("${accounts.firstOrNull { it.id == row.accountId }?.title ?: "حساب نامشخص"} • ${Jalali.of(row.createdAt)}", color = muted, fontSize = 11.sp)
            }
            Text((if (credit) "+" else "−") + toman(row.amountToman), color = if (credit) mint else danger, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        if (row.note.isNotBlank()) Text(row.note, fontSize = 11.sp, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (edit != null || delete != null) Row {
            if (edit != null) TextButton(onClick = edit) { Text("ویرایش") }
            if (delete != null) TextButton(onClick = delete) { Text("حذف", color = danger) }
        }
    }
}

@Composable
private fun DebtsPage(debts: List<Debt>, period: FinancePeriod, nowEpoch: Long, onEdit: (Debt) -> Unit, onPay: (Debt) -> Unit, onDelete: (Debt) -> Unit) {
    SectionTitle("بدهی و اقساط", "مانده‌ها بین دوره‌ها باقی می‌مونن؛ سررسیدها بر اساس دوره انتخابی مشخص می‌شن")
    if (debts.isEmpty()) EmptyNote("فعلاً بدهی یا طلبی ثبت نشده.")
    debts.forEach { row ->
        val remaining = (row.totalToman - row.paidToman).coerceAtLeast(0)
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.person, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Tag(if (row.direction == "I_OWE") "بدهکارم" else "طلبکارم", row.direction == "OWED_TO_ME")
            }
            MoneyLine("مانده", remaining, if (row.direction == "I_OWE") danger else mint)
            Text("کل: ${toman(row.totalToman)} • تسویه‌شده: ${toman(row.paidToman)}", fontSize = 12.sp, color = muted)
            if (row.installmentMonths > 0) {
                val done = Installments.countPaid(row)
                val left = Installments.remainingCount(row)
                val next = Installments.nextDate(row)
                Text("اقساط: ${digitsFa(done.toString())} پرداخت‌شده از ${digitsFa(row.installmentMonths.toString())} • ${digitsFa(left.toString())} قسط باقی‌مانده",
                    fontSize = 12.sp, color = mint)
                if (next != null) {
                    val dueEpoch = JalaliDates.toEpoch(next)
                    val monthsAway = JalaliDates.monthDistance(Jalali.of(nowEpoch), next)
                    val status = when {
                        dueEpoch != null && dueEpoch < nowEpoch -> "سررسید گذشته"
                        monthsAway > 0 -> "تا ${digitsFa(monthsAway.toString())} ماه دیگر"
                        else -> "همین ماه"
                    }
                    Text("قسط بعد: ${JalaliDates.format(next)} • $status", color = if (dueEpoch != null && dueEpoch < nowEpoch) danger else muted, fontSize = 12.sp)
                    Text("مبلغ لازم برای تکمیل قسط بعد: ${toman(Installments.nextAmount(row))}", fontSize = 12.sp, color = muted)
                }
                LinearProgressIndicator(progress = { (row.paidToman.toFloat() / row.totalToman.coerceAtLeast(1)).coerceIn(0f,1f) },
                    modifier = Modifier.fillMaxWidth(), color = mint, trackColor = secondary)
                val dueHere = Installments.dueInPeriod(row, period.start, period.endExclusive)
                if (dueHere > 0) Tag("سررسید این دوره • ${toman(dueHere)}")
            } else if (row.dueDateJalali.isNotBlank()) {
                Text("سررسید: ${row.dueDateJalali}", fontSize = 12.sp, color = muted)
            }
            if (row.note.isNotBlank()) Text(row.note, fontSize = 12.sp, color = muted)
            Row {
                if (remaining > 0) TextButton(onClick = { onPay(row) }) { Text(if (row.direction == "I_OWE") "ثبت پرداخت" else "ثبت وصول") }
                TextButton(onClick = { onEdit(row) }) { Text("ویرایش") }
                TextButton(onClick = { onDelete(row) }) { Text("حذف", color = danger) }
            }
        }
    }
}

@Composable
private fun BudgetsPage(budgets: List<Budget>, txs: List<Transaction>, onEdit: (Budget) -> Unit, onDelete: (Budget) -> Unit) {
    SectionTitle("سقف پرداخت", "بودجه دوره انتخابی؛ صرفاً هشدار، نه محدودیت بانکی")
    if (budgets.isEmpty()) EmptyNote("برای تعریف سقف هزینه ماهانه روی + بزن.")
    budgets.forEach { b ->
        val spent = txs.filter { it.kind == "OUT" && it.category == b.category }.sumOf { it.amountToman }
        val percent = 100f * spent / b.monthlyLimitToman.coerceAtLeast(1)
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.category, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Tag(if (percent >= 100) "سقف رد شده" else if (percent >= 80) "نزدیک سقف" else "عادی", percent < 80)
            }
            Text("${toman(spent)} از ${toman(b.monthlyLimitToman)}", fontSize = 13.sp, color = muted)
            LinearProgressIndicator(progress = { (percent / 100).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (percent >= 100) danger else mint, trackColor = secondary)
            Row {
                TextButton(onClick = { onEdit(b) }) { Text("ویرایش") }
                TextButton(onClick = { onDelete(b) }) { Text("حذف", color = danger) }
            }
        }
    }
}

@Composable
private fun TextEntry(label: String, value: String, onChange: (String) -> Unit, number: Boolean = false, singleLine: Boolean = true) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) }, singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text)
    )
}

@Composable
private fun <T> SelectItem(label: String, selected: T?, items: List<T>, display: (T) -> String, onPick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = muted)
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let(display) ?: "انتخاب کنید", modifier = Modifier.weight(1f))
                Text("⌄")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(display(item)) }, onClick = { onPick(item); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun EntryDialog(title: String, valid: Boolean, dismiss: () -> Unit, onSave: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
        },
        confirmButton = { Button(onClick = onSave, enabled = valid) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("انصراف") } }
    )
}

@Composable
private fun AccountDialog(initial: Account?, dismiss: () -> Unit, save: (Account) -> Unit) {
    var title by remember(initial) { mutableStateOf(initial?.title.orEmpty()) }
    var bank by remember(initial) { mutableStateOf(initial?.bank.orEmpty()) }
    var last4 by remember(initial) { mutableStateOf(initial?.last4.orEmpty()) }
    var opening by remember(initial) { mutableStateOf(initial?.openingToman?.toString() ?: "0") }
    val amount = readToman(opening)
    val normalizedLast4 = ir.jessi.finance.sms.BankSmsParser.normalizeDigits(last4).filter { it.isDigit() }
    EntryDialog("${if (initial == null) "افزودن" else "ویرایش"} حساب", title.isNotBlank() && amount != null && (normalizedLast4.isEmpty() || normalizedLast4.length == 4), dismiss, {
        save(Account(id = initial?.id ?: 0, title = title.trim(), bank = bank.trim(), last4 = normalizedLast4, openingToman = amount ?: 0))
    }) {
        TextEntry("نام حساب (مثلاً سینا)", title, { title = it })
        TextEntry("نام بانک", bank, { bank = it })
        TextEntry("چهار رقم آخر کارت/حساب (اختیاری)", last4, { last4 = it }, number = true)
        TextEntry("موجودی اولیه به تومان", opening, { opening = it }, number = true)
        Text("موجودی فعلی = موجودی اولیه + واریزها − برداشت‌ها. برای جلوگیری از دوباره‌شماری، موجودی اولیه را در زمان شروع ثبت کن.", fontSize = 11.sp, color = muted)
    }
}

@Composable
private fun TransactionDialog(
    initial: Transaction?, accounts: List<Account>, dismiss: () -> Unit,
    onSave: (Transaction) -> Unit, onTransfer: (Long, Long, Long, String) -> Unit
) {
    var type by remember(initial) { mutableStateOf(initial?.kind ?: "OUT") }
    var accountId by remember(initial) { mutableStateOf(initial?.accountId ?: accounts.firstOrNull()?.id) }
    var targetAccountId by remember(initial) { mutableStateOf(accounts.firstOrNull { it.id != accountId }?.id) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "سایر") }
    var amountStr by remember(initial) { mutableStateOf(initial?.amountToman?.toString().orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    val amount = readToman(amountStr)
    val transfer = type == "TRANSFER_OUT"
    val valid = amount != null && amount > 0 && accountId != null && (!transfer || (targetAccountId != null && targetAccountId != accountId))
    EntryDialog(if (initial == null) "ثبت تراکنش" else "ویرایش تراکنش", valid, dismiss, {
        if (transfer) onTransfer(accountId!!, targetAccountId!!, amount!!, note)
        else onSave(Transaction(
            id = initial?.id ?: 0, accountId = accountId!!, amountToman = amount!!,
            kind = type, category = if (type == "IN" && category !in incomeCategories) "درآمد" else category,
            note = note, createdAt = initial?.createdAt ?: System.currentTimeMillis(),
            source = initial?.source ?: "MANUAL", groupId = initial?.groupId,
            dedupKey = initial?.dedupKey
        ))
    }) {
        if (initial == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("OUT" to "برداشت", "IN" to "واریز", "TRANSFER_OUT" to "انتقال").forEach { (code, label) ->
                    FilterChip(selected = type == code, onClick = {
                        type = code
                        if (type == "IN" && category !in incomeCategories) category = "درآمد"
                    }, label = { Text(label, fontSize = 11.sp) })
                }
            }
        }
        if (accounts.isEmpty()) Text("ابتدا از بخش حساب‌ها یک حساب بساز.", color = danger, fontSize = 12.sp)
        else SelectItem("حساب", accountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title }, { accountId = it.id })
        if (transfer && accounts.isNotEmpty()) {
            SelectItem("حساب مقصد", targetAccountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title }, { targetAccountId = it.id })
            Text("انتقال بین حساب‌ها درآمد یا هزینه محسوب نمی‌شود.", fontSize = 11.sp, color = muted)
        }
        TextEntry("مبلغ به تومان", amountStr, { amountStr = it }, number = true)
        if (type == "OUT") SelectItem("دسته‌بندی", category, categories, { it }, { category = it })
        if (type == "IN") {
            SelectItem("نوع درآمد", category, incomeCategories, { it }, { category = it })
            if (category == "حقوق") Text("این واریز در حالت «از حقوق» دوره جدید مالی رو آغاز می‌کنه.", color = mint, fontSize = 11.sp)
        }
        TextEntry("توضیح (اختیاری)", note, { note = it })
    }
}

@Composable
private fun DebtDialog(initial: Debt?, dismiss: () -> Unit, save: (Debt) -> Unit) {
    var name by remember(initial) { mutableStateOf(initial?.person.orEmpty()) }
    var dir by remember(initial) { mutableStateOf(initial?.direction ?: "I_OWE") }
    var amountStr by remember(initial) { mutableStateOf(initial?.totalToman?.toString().orEmpty()) }
    var due by remember(initial) { mutableStateOf(initial?.dueDateJalali.orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var installment by remember(initial) { mutableStateOf((initial?.installmentMonths ?: 0) > 0) }
    var installmentCount by remember(initial) { mutableStateOf((initial?.installmentMonths?.takeIf { it > 0 } ?: 6).toString()) }
    var monthsUntilDue by remember(initial) { mutableIntStateOf(1) }
    var firstDue by remember(initial) {
        mutableStateOf(initial?.firstInstallmentJalali?.takeIf { it.isNotBlank() } ?:
            JalaliDates.format(JalaliDates.addMonths(Jalali.of(System.currentTimeMillis()), 1)))
    }
    val amount = readToman(amountStr)
    val months = ir.jessi.finance.sms.BankSmsParser.normalizeDigits(installmentCount).toIntOrNull()
    val valid = name.isNotBlank() && amount != null && amount > 0 &&
        amount >= (initial?.paidToman ?: 0) &&
        (if (installment) months != null && months in 1..120 && JalaliDates.parse(firstDue) != null
         else due.isBlank() || JalaliDates.parse(due) != null)
    EntryDialog(
        if (initial == null) "افزودن بدهی، طلب یا قسط" else "ویرایش بدهی یا قسط",
        valid, dismiss, {
        save(Debt(id = initial?.id ?: 0, person = name.trim(), direction = dir,
            totalToman = amount!!, paidToman = initial?.paidToman ?: 0,
            dueDateJalali = if (installment) "" else due.trim(),
            note = note.trim(),
            installmentMonths = if (installment) months!! else 0,
            firstInstallmentJalali = if (installment) JalaliDates.format(JalaliDates.parse(firstDue)!!) else ""))
    }) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = dir == "I_OWE", onClick = { dir = "I_OWE" }, label = { Text("من بدهکارم") })
            FilterChip(selected = dir == "OWED_TO_ME", onClick = { dir = "OWED_TO_ME" }, label = { Text("من طلبکارم") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !installment, onClick = { installment = false }, label = { Text("بدهی عادی") })
            FilterChip(selected = installment, onClick = { installment = true }, label = { Text("قسطی") })
        }
        TextEntry("نام شخص، بانک یا مؤسسه", name, { name = it })
        TextEntry("مبلغ کل به تومان", amountStr, { amountStr = it }, number = true)
        if (installment) {
            TextEntry("تعداد کل اقساط (ماه)", installmentCount, { installmentCount = it }, number = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("اولین قسط چند ماه دیگر؟", fontSize = 12.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {
                    monthsUntilDue = (monthsUntilDue - 1).coerceAtLeast(0)
                    firstDue = JalaliDates.format(JalaliDates.addMonths(Jalali.of(System.currentTimeMillis()), monthsUntilDue))
                }, enabled = monthsUntilDue > 0) { Text("−") }
                Text(digitsFa(monthsUntilDue.toString()), color = mint)
                OutlinedButton(onClick = {
                    monthsUntilDue = (monthsUntilDue + 1).coerceAtMost(36)
                    firstDue = JalaliDates.format(JalaliDates.addMonths(Jalali.of(System.currentTimeMillis()), monthsUntilDue))
                }, enabled = monthsUntilDue < 36) { Text("+") }
            }
            TextEntry("تاریخ اولین سررسید شمسی", firstDue, { firstDue = it })
            if (amount != null && months != null && months > 0) {
                Text("مبلغ تقریبی هر قسط: ${toman(amount / months)} • مانده با ثبت پرداخت کم می‌شه.",
                    fontSize = 11.sp, color = mint)
            }
        } else {
            TextEntry("تاریخ سررسید شمسی (اختیاری؛ مثلاً ۱۴۰۵/۰۹/۱۵)", due, { due = it })
        }
        TextEntry("توضیح", note, { note = it })
    }
}

@Composable
private fun BudgetDialog(initial: Budget?, existing: List<Budget>, dismiss: () -> Unit, save: (Budget) -> Unit) {
    var category by remember(initial) { mutableStateOf(initial?.category ?: categories.firstOrNull { cat -> existing.none { it.category == cat } } ?: "سایر") }
    var amountStr by remember(initial) { mutableStateOf(initial?.monthlyLimitToman?.toString().orEmpty()) }
    val amount = readToman(amountStr)
    EntryDialog(if (initial == null) "سقف پرداخت جدید" else "ویرایش سقف پرداخت", amount != null && amount > 0 && existing.none { it.category == category && it.id != initial?.id }, dismiss, {
        save(Budget(id = initial?.id ?: 0, category = category, monthlyLimitToman = amount!!))
    }) {
        SelectItem("دسته‌بندی هزینه", category, categories, { it }, { category = it })
        TextEntry("سقف ماهانه (تومان)", amountStr, { amountStr = it }, number = true)
        Text("سقف‌ها در ابتدای هر دوره انتخابی مجدداً محاسبه می‌شوند؛ مانده حساب صفر نمی‌شود.", fontSize = 11.sp, color = muted)
    }
}

@Composable
private fun RepayDialog(debt: Debt, accounts: List<Account>, dismiss: () -> Unit, onPay: (Long, Long) -> Unit) {
    var amountStr by remember(debt) { mutableStateOf(
        if (debt.installmentMonths > 0) Installments.nextAmount(debt).takeIf { it > 0L }?.toString().orEmpty() else ""
    ) }
    var accountId by remember(debt) { mutableStateOf(accounts.firstOrNull()?.id) }
    val amount = readToman(amountStr)
    val balance = debt.totalToman - debt.paidToman
    EntryDialog(if (debt.direction == "I_OWE") "ثبت بازپرداخت" else "ثبت وصول طلب", amount != null && amount > 0 && amount <= balance && accountId != null, dismiss, {
        onPay(amount!!, accountId!!)
    }) {
        Text("مانده: ${toman(balance)}", color = mint)
        if (debt.installmentMonths > 0) Text("مبلغ قسط بعد: ${toman(Installments.nextAmount(debt))}", color = mint, fontSize = 12.sp)
        if (accounts.isEmpty()) Text("برای ثبت پرداخت، ابتدا یک حساب بساز.", color = danger)
        else SelectItem("حساب مرتبط", accountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title }, { accountId = it.id })
        TextEntry("مبلغ پرداخت یا دریافت (تومان)", amountStr, { amountStr = it }, number = true)
        Text("این مبلغ هم در مانده حساب اعمال می‌شود؛ اما به عنوان هزینه روزمره یا درآمد ماهانه دسته‌بندی نمی‌شود.", fontSize = 11.sp, color = muted)
    }
}

@Composable
private fun ReviewDialog(row: SmsReview, accounts: List<Account>, dismiss: () -> Unit, onAccept: (Long, String, String) -> Unit, onReject: () -> Unit) {
    var accountId by remember(row.id) { mutableStateOf(row.accountGuess ?: accounts.firstOrNull()?.id) }
    var category by remember(row.id) { mutableStateOf(if (row.kind == "IN") "درآمد" else "سایر") }
    var note by remember(row.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("بررسی پیامک بانکی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(if (row.kind == "IN") "واریز احتمالی" else "برداشت احتمالی", color = mint)
                Text(toman(row.amountToman), fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("فرستنده: ${row.sender}", fontSize = 12.sp)
                if (accounts.isEmpty()) Text("اول یک حساب بساز.", color = danger)
                else SelectItem("این تراکنش برای کدام حساب است؟", accountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title }, { accountId = it.id })
                SelectItem("برای چه دسته‌ای بود؟", category, if (row.kind == "IN") incomeCategories else categories, { it }, { category = it })
                if (category == "حقوق" && row.kind == "IN") Text("تأیید این واریز می‌تونه دوره جدید رو در حالت «از حقوق» آغاز کنه.", color = mint, fontSize = 11.sp)
                TextEntry("برای چی بود؟ (توضیح اختیاری)", note, { note = it }, singleLine = false)
                Text("قبل از تأیید، مبلغ و نوع تراکنش را با پیامک اصلی تطبیق بده.", fontSize = 11.sp, color = muted)
            }
        },
        confirmButton = { Button(onClick = { accountId?.let { onAccept(it, category, note) } }, enabled = accountId != null) { Text("ثبت تراکنش") } },
        dismissButton = {
            Row {
                TextButton(onClick = onReject) { Text("نادیده گرفتن", color = danger) }
                TextButton(onClick = dismiss) { Text("بعداً") }
            }
        }
    )
}
