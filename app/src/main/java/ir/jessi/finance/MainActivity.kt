package ir.jessi.finance

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import ir.jessi.finance.sms.SmsReviewNotifications
import ir.jessi.finance.sms.SmsDiagnostics
import ir.jessi.finance.sms.BankSmsParser
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import java.util.Locale
import java.text.NumberFormat
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
    HOME("خانه", "⌂"), DEBTS("اقساط", "⇆"),
    BUDGETS("بودجه", "◉"), TXS("تراکنش‌ها", "≋"), ACCOUNTS("حساب‌ها", "▣")
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
    var balanceVisible by remember {
        mutableStateOf(settings.getBoolean("show_balance", true))
    }
    var smsMonitoringEnabled by remember {
        mutableStateOf(settings.getBoolean("sms_monitoring_enabled", true))
    }
    var reviewNotificationsEnabled by remember {
        mutableStateOf(settings.getBoolean("sms_review_notifications_enabled", true))
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var diagnosticRefresh by remember { mutableIntStateOf(0) }
    val currentDiagnostic = remember(nowEpoch, showSettings, diagnosticRefresh) {
        SmsDiagnostics.summary(context)
    }
    val notificationBlocker = remember(nowEpoch, showSettings, diagnosticRefresh) {
        SmsReviewNotifications.blockedReason(context)
    }

    val periods = remember(txs, periodMode, nowEpoch) { FinancePeriodEngine.periods(txs, periodMode, nowEpoch) }
    val selectedIndex = periodOffset.coerceIn(0, (periods.size - 1).coerceAtLeast(0))
    val chosenPeriod = periods[selectedIndex]
    val periodTransactions = remember(txs, chosenPeriod) { txs.filter { chosenPeriod.contains(it.createdAt) } }
    var smsPermitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { smsPermitted = it }
    var notificationsPermitted by remember { mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificationsPermitted = it }
    val permissionPrefs = remember(context) {
        context.getSharedPreferences("jessi_permissions_onboarding", android.content.Context.MODE_PRIVATE)
    }
    val installedVersion = remember(context) {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }
    val firstOpenPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        smsPermitted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        notificationsPermitted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(nowEpoch) {
        smsPermitted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECEIVE_SMS
        ) == PackageManager.PERMISSION_GRANTED
        notificationsPermitted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
    }
    LaunchedEffect(installedVersion) {
        if (permissionPrefs.getLong("asked_version", -1L) != installedVersion) {
            permissionPrefs.edit().putLong("asked_version", installedVersion).apply()
            val missing = buildList {
                if (!smsPermitted) add(Manifest.permission.RECEIVE_SMS)
                if (Build.VERSION.SDK_INT >= 33 && !notificationsPermitted) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (missing.isNotEmpty()) firstOpenPermissionLauncher.launch(missing.toTypedArray())
        }
    }
    var tabName by rememberSaveable { mutableStateOf(Tab.HOME.name) }
    val tab = Tab.valueOf(tabName)
    var editor by remember { mutableStateOf<Editor?>(null) }
    val ioScope = rememberCoroutineScope()
    var backupBusy by remember { mutableStateOf(false) }
    var restorePreview by remember { mutableStateOf<FinanceBackup.Snapshot?>(null) }
    val backupExportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            backupBusy = true
            ioScope.launch {
                try {
                    val output = withContext(Dispatchers.IO) { FinanceBackup.export(context, uri) }
                    Toast.makeText(context, "پشتیبان جسی ذخیره شد. " +
                        output.accounts.size + " حساب و " + output.transactions.size + " تراکنش",
                        Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "خطای پشتیبان‌گیری: " +
                        (e.message ?: "فایل قابل ذخیره نیست"), Toast.LENGTH_LONG).show()
                } finally { backupBusy = false }
            }
        }
    }
    val backupImportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backupBusy = true
            ioScope.launch {
                try {
                    restorePreview = withContext(Dispatchers.IO) {
                        FinanceBackup.inspect(context, uri)
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطای خواندن فایل: " +
                        (e.message ?: "فایل معتبر نیست"), Toast.LENGTH_LONG).show()
                } finally { backupBusy = false }
            }
        }
    }

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
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(40.dp).background(
                        if (darkMode) Color(0xFF2E1C5E) else Color(0xFFDAF6E7),
                        RoundedCornerShape(14.dp)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Eco, contentDescription = "نشان جسی",
                        tint = if (darkMode) Color(0xFFC46EFF) else Color(0xFF0CB86C),
                        modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("جسی", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("مدیریت مالی شخصی", color = muted, fontSize = 10.sp)
                }
                IconButton(
                    onClick = { showSettings = !showSettings },
                    modifier = Modifier.size(43.dp)
                ) {
                    Icon(
                        if (showSettings) Icons.Outlined.ArrowBack else Icons.Outlined.Settings,
                        contentDescription = if (showSettings) "بازگشت به برنامه" else "تنظیمات جسی",
                        tint = muted, modifier = Modifier.size(23.dp)
                    )
                }
                // One accessible toggle: tap anywhere to flip between light and dark.
                Surface(
                    onClick = onToggleTheme,
                    modifier = Modifier.width(80.dp).height(40.dp),
                    color = secondary,
                    shape = RoundedCornerShape(50.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .55f))
                ) {
                    Box(Modifier.fillMaxSize().padding(3.dp)) {
                        Icon(Icons.Outlined.LightMode, contentDescription = null,
                            tint = muted, modifier = Modifier.align(Alignment.CenterStart)
                                .padding(start = 8.dp).size(18.dp))
                        Icon(Icons.Outlined.DarkMode, contentDescription = null,
                            tint = muted, modifier = Modifier.align(Alignment.CenterEnd)
                                .padding(end = 8.dp).size(18.dp))
                        Box(
                            Modifier.align(if (darkMode) Alignment.CenterEnd else Alignment.CenterStart)
                                .size(34.dp).background(
                                    if (darkMode) Color(0xFF8143E5) else Color(0xFF00A960),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (darkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                                contentDescription = if (darkMode) "دارک مود؛ تغییر به لایت" else "لایت مود؛ تغییر به دارک",
                                tint = Color.White, modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                containerColor = surface,
                tonalElevation = 0.dp,
                modifier = Modifier.height(75.dp)
            ) {
                Tab.entries.forEach { item ->
                    val navigationIcon: ImageVector = when (item) {
                        Tab.HOME -> Icons.Outlined.Home
                        Tab.DEBTS -> Icons.Outlined.CreditCard
                        Tab.BUDGETS -> Icons.Outlined.PieChartOutline
                        Tab.TXS -> Icons.Outlined.ReceiptLong
                        Tab.ACCOUNTS -> Icons.Outlined.AccountBalanceWallet
                    }
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tabName = item.name; showSettings = false },
                        icon = { Icon(navigationIcon, contentDescription = item.label, modifier = Modifier.size(22.dp)) },
                        label = { Text(item.label, fontSize = 10.sp, maxLines = 1) },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = mint, selectedTextColor = mint,
                            indicatorColor = secondary, unselectedIconColor = muted, unselectedTextColor = muted
                        )
                    )
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.HOME && !showSettings) {
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
            if (showSettings) {
                SettingsPage(
                    darkMode = darkMode,
                    onToggleTheme = onToggleTheme,
                    balanceVisible = balanceVisible,
                    onBalanceVisibilityChange = { value ->
                        balanceVisible = value
                        settings.edit().putBoolean("show_balance", value).apply()
                    },
                    smsMonitoringEnabled = smsMonitoringEnabled,
                    onSmsMonitoringChange = { value ->
                        smsMonitoringEnabled = value
                        settings.edit().putBoolean("sms_monitoring_enabled", value).apply()
                        if (value && !smsPermitted) {
                            permissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
                        }
                    },
                    reviewNotificationsEnabled = reviewNotificationsEnabled,
                    onReviewNotificationsChange = { value ->
                        reviewNotificationsEnabled = value
                        settings.edit().putBoolean("sms_review_notifications_enabled", value).apply()
                        if (value && !notificationsPermitted && Build.VERSION.SDK_INT >= 33) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    periodMode = periodMode,
                    onPeriodChange = { value ->
                        periodMode = value
                        periodOffset = 0
                        settings.edit().putString("period_mode", value).apply()
                    },
                    smsPermitted = smsPermitted,
                    notificationsPermitted = notificationsPermitted,
                    onSmsPermission = { permissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                    onNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    },
                    onSystemSettings = {
                        context.startActivity(Intent(
                            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:${context.packageName}")
                        ))
                    },
                    diagnosticSummary = currentDiagnostic,
                    notificationBlocker = notificationBlocker,
                    pendingReviews = reviews.size,
                    onTestNotification = {
                        val posted = SmsReviewNotifications.showTest(context)
                        diagnosticRefresh++
                        Toast.makeText(context,
                            if (posted) "اعلان تست ارسال شد؛ پنل اعلان‌ها را بررسی کن"
                            else "اعلان مسدود است؛ دسترسی و کانال اعلان را بررسی کن",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onResendPending = {
                        var sent = 0
                        reviews.take(10).forEach {
                            if (SmsReviewNotifications.show(
                                    context, it.id, it.kind,
                                    isTest = it.fingerprint.startsWith("JESSI_TEST_")
                                )) sent++
                        }
                        diagnosticRefresh++
                        Toast.makeText(context,
                            "اعلان برای " + sent + " تراکنش در انتظار ارسال شد",
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    onNotificationSettings = {
                        SmsReviewNotifications.ensureChannel(context)
                        val i = Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                            putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                            putExtra(AndroidSettings.EXTRA_CHANNEL_ID, SmsReviewNotifications.CHANNEL_ID)
                        }
                        context.startActivity(i)
                    },
                    onRefreshDiagnostics = { diagnosticRefresh++ },
                    onSimulateSms = { raw, sender ->
                        vm.simulateSms(raw, sender) { message ->
                            diagnosticRefresh++
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    onExportBackup = {
                        if (!backupBusy) {
                            val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
                                .format(Date())
                            backupExportPicker.launch("Jessi-Backup-$stamp.json")
                        }
                    },
                    onImportBackup = {
                        if (!backupBusy) backupImportPicker.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    },
                    backupBusy = backupBusy,
                    versionName = remember(context, installedVersion) {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "نامشخص"
                    }
                )
            } else {
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
                    onSelectTab = { tabName = it.name; showSettings = false },
                    balanceVisible = balanceVisible,
                    onBalanceVisibilityChange = { value ->
                        balanceVisible = value
                        settings.edit().putBoolean("show_balance", value).apply()
                    },
                    smsMonitoringEnabled = smsMonitoringEnabled,
                    reviewNotificationsEnabled = reviewNotificationsEnabled,
                    onOpenSettings = { showSettings = true }
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
            }
            Spacer(Modifier.height(86.dp))
        }
    }

    if (restorePreview != null) {
        val candidate = restorePreview!!
        AlertDialog(
            onDismissRequest = { if (!backupBusy) restorePreview = null },
            title = { Text("بازیابی اطلاعات جسی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("فایل معتبر است و این موارد را دارد:", fontWeight = FontWeight.Bold)
                    Text(candidate.summary(), fontSize = 12.sp)
                    Text("توجه: با تأیید، تمام اطلاعات مالی فعلی با محتویات این فایل جایگزین می‌شود. " +
                        "بازیابی با داده‌های فعلی ادغام نمی‌شود.",
                        color = danger, fontSize = 12.sp)
                    Text("بهتر است پیش از بازیابی، از وضعیت فعلی پشتیبان بگیری.",
                        color = muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (!backupBusy) {
                        backupBusy = true
                        ioScope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    FinanceBackup.restore(context, candidate)
                                }
                                restorePreview = null
                                periodOffset = 0
                                Toast.makeText(context, "بازیابی اطلاعات موفق بود", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "بازیابی انجام نشد؛ اطلاعات قبلی حفظ شدند: " +
                                    (e.message ?: "خطای نامشخص"), Toast.LENGTH_LONG).show()
                            } finally { backupBusy = false }
                        }
                    }
                }, enabled = !backupBusy) { Text("جایگزینی اطلاعات") }
            },
            dismissButton = {
                TextButton(onClick = { restorePreview = null }, enabled = !backupBusy) {
                    Text("انصراف")
                }
            }
        )
    }

    when (val e = editor) {
        is Editor.AccountForm -> AccountDialog(e.value, dismiss = { editor = null }) { vm.saveAccount(it); editor = null }
        is Editor.TxForm -> TransactionDialog(e.value, accounts, dismiss = { editor = null }, onSave = { vm.saveTransaction(it); editor = null }, onTransfer = { a, b, amount, note -> vm.transfer(a,b,amount,note); editor = null })
        is Editor.DebtForm -> DebtDialog(e.value, dismiss = { editor = null }) { vm.saveDebt(it); editor = null }
        is Editor.BudgetForm -> BudgetDialog(e.value, budgets, dismiss = { editor = null }) { vm.saveBudget(it); editor = null }
        is Editor.Repay -> RepayDialog(e.value, accounts, dismiss = { editor = null }) { amount, accountId -> vm.settleDebt(e.value, amount, accountId); editor = null }
        is Editor.Review -> ReviewDialog(e.value, accounts, dismiss = { editor = null }, onAccept = { id, category, note, unit -> vm.acceptReview(e.value, id, category, note, unit); editor = null }, onReject = { vm.dismissReview(e.value.id); editor = null })
        is Editor.Delete -> AlertDialog(onDismissRequest = { editor = null }, title = { Text("تأیید حذف") }, text = { Text(e.title) }, confirmButton = { TextButton(onClick = { e.action(); editor = null }) { Text("حذف", color = danger) } }, dismissButton = { TextButton(onClick = { editor = null }) { Text("انصراف") } })
        null -> Unit
    }
}


@Composable
private fun SettingsPage(
    darkMode: Boolean, onToggleTheme: () -> Unit,
    balanceVisible: Boolean, onBalanceVisibilityChange: (Boolean) -> Unit,
    smsMonitoringEnabled: Boolean, onSmsMonitoringChange: (Boolean) -> Unit,
    reviewNotificationsEnabled: Boolean, onReviewNotificationsChange: (Boolean) -> Unit,
    periodMode: String, onPeriodChange: (String) -> Unit,
    smsPermitted: Boolean, notificationsPermitted: Boolean,
    onSmsPermission: () -> Unit, onNotificationPermission: () -> Unit,
    onSystemSettings: () -> Unit,
    diagnosticSummary: String, notificationBlocker: String?, pendingReviews: Int,
    onTestNotification: () -> Unit,
    onResendPending: () -> Unit,
    onNotificationSettings: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onSimulateSms: (String, String) -> Unit,
    onExportBackup: () -> Unit, onImportBackup: () -> Unit, backupBusy: Boolean,
    versionName: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Column(Modifier.padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("تنظیمات", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            Text("شخصی‌سازی جسی و کنترل دسترسی‌ها", fontSize = 12.sp, color = muted)
        }
        Tile(Modifier.fillMaxWidth()) {
            SettingsGroupTitle(Icons.Outlined.Palette, "ظاهر و حریم خصوصی")
            SettingsToggle(
                icon = if (darkMode) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                title = "حالت تاریک",
                description = "دارک شب‌آبی و بنفش / لایت مینیمال",
                checked = darkMode,
                onChange = { if (it != darkMode) onToggleTheme() }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
            SettingsToggle(
                icon = Icons.Outlined.VisibilityOff,
                title = "نمایش موجودی در داشبورد",
                description = "موجودی کل، درآمد و هزینه را پنهان یا نمایش بده",
                checked = balanceVisible, onChange = onBalanceVisibilityChange
            )
        }

        Tile(Modifier.fillMaxWidth()) {
            SettingsGroupTitle(Icons.Outlined.Sms, "پیامک بانکی و اعلان‌ها")
            SettingsToggle(
                icon = Icons.Outlined.Markunread,
                title = "بررسی پیامک‌های بانکی",
                description = "فقط پیامک جدید را برای تأیید در صف بگذار؛ ثبت خودکار انجام نمی‌شود",
                checked = smsMonitoringEnabled, onChange = onSmsMonitoringChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
            SettingsToggle(
                icon = Icons.Outlined.NotificationsActive,
                title = "اعلان پیامک جدید",
                description = "نمایش اعلان ثبت یا نادیده‌گرفتن؛ پیامک‌های بدون اعلان در صف می‌مانند",
                checked = reviewNotificationsEnabled, onChange = onReviewNotificationsChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("دسترسی SMS", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(if (smsPermitted) "اجازه داده شده" else "اجازه داده نشده",
                        fontSize = 11.sp, color = if (smsPermitted) mint else danger)
                }
                if (!smsPermitted) TextButton(onClick = onSmsPermission) { Text("درخواست مجوز") }
                else Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = mint)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("دسترسی اعلان‌ها", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(if (notificationsPermitted) "اجازه داده شده" else "اجازه داده نشده",
                        fontSize = 11.sp, color = if (notificationsPermitted) mint else danger)
                }
                if (!notificationsPermitted) TextButton(onClick = onNotificationPermission) { Text("درخواست مجوز") }
                else Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = mint)
            }
            OutlinedButton(onClick = onSystemSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("مدیریت مجوزها در تنظیمات اندروید")
            }
        }

        Tile(Modifier.fillMaxWidth()) {
            SettingsGroupTitle(Icons.Outlined.BugReport, "عیب‌یابی دریافت پیامک")
            Text("وضعیت اعلان: " + (notificationBlocker ?: "مجوز و کانال اعلان فعال است"),
                fontSize = 12.sp, color = if (notificationBlocker == null) mint else danger)
            Text("وضعیت آخرین پیامک در جسی:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
            Text(diagnosticSummary, fontSize = 11.sp, color = muted, lineHeight = 17.sp)
            Text("پیامک‌های منتظر بررسی: " + digitsFa(pendingReviews.toString()),
                fontSize = 12.sp, color = muted)
            OutlinedButton(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ارسال اعلان آزمایشی")
            }
            OutlinedButton(
                onClick = onResendPending, enabled = pendingReviews > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("اعلان دوباره برای پیامک‌های منتظر")
            }
            TextButton(onClick = onNotificationSettings) {
                Text("تنظیمات کانال اعلان پیامک در اندروید")
            }
            TextButton(onClick = onRefreshDiagnostics) { Text("به‌روزرسانی وضعیت") }
            Text("این عیب‌یابی متن پیامک، شماره حساب یا مبلغ رو ذخیره نمی‌کنه.",
                color = muted, fontSize = 10.sp)
        }

        SmsParserTestSettings(onSimulateSms)

        Tile(Modifier.fillMaxWidth()) {
            SettingsGroupTitle(Icons.Outlined.SaveAlt, "پشتیبان‌گیری و بازیابی اطلاعات")
            Text(
                "از حساب‌ها، تراکنش‌ها، بدهی‌ها، اقساط، بودجه‌ها و صف پیامک‌ها یک فایل JSON بگیر.",
                fontSize = 12.sp, color = muted
            )
            OutlinedButton(
                onClick = onExportBackup, enabled = !backupBusy, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ذخیره فایل پشتیبان")
            }
            OutlinedButton(
                onClick = onImportBackup, enabled = !backupBusy, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("انتخاب فایل و بازیابی")
            }
            if (backupBusy) CircularProgressIndicator(modifier = Modifier.size(23.dp))
            Text(
                "فایل پشتیبان رمزگذاری نشده و اطلاعات مالی حساس دارد. در جای امن نگهداری کن. " +
                "بازیابی فقط بعد از بررسی فایل و تأیید تو انجام می‌شود.",
                fontSize = 11.sp, color = danger
            )
        }

        Tile(Modifier.fillMaxWidth()) {
            SettingsGroupTitle(Icons.Outlined.CalendarMonth, "دوره مالی")
            Text("شروع دوره جدید و محاسبه سقف هزینه‌ها",
                fontSize = 12.sp, color = muted)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FilterChip(
                    selected = periodMode == FinancePeriodEngine.MONTHLY,
                    onClick = { onPeriodChange(FinancePeriodEngine.MONTHLY) },
                    label = { Text("اول هر ماه شمسی") }
                )
                FilterChip(
                    selected = periodMode == FinancePeriodEngine.SALARY,
                    onClick = { onPeriodChange(FinancePeriodEngine.SALARY) },
                    label = { Text("پس از تأیید حقوق") }
                )
            }
            Text("تعویض دوره هیچ تراکنش یا بدهی را پاک نمی‌کند.",
                fontSize = 11.sp, color = muted)
        }

        Tile(Modifier.fillMaxWidth()) {
            SettingsGroupTitle(Icons.Outlined.Info, "درباره جسی")
            Text("نسخه " + digitsFa(versionName),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text("اطلاعات حساب‌ها و تراکنش‌ها در حافظه همین گوشی نگهداری می‌شوند. متن کامل پیامک بانکی ذخیره نمی‌شود.",
                fontSize = 12.sp, color = muted)
            Text("هنگام حذف برنامه، ممکن است داده‌های ذخیره‌شده حذف شوند؛ بدون پشتیبان برنامه را پاک نکن.",
                fontSize = 11.sp, color = danger)
        }
    }
}


@Composable
private fun SmsParserTestSettings(onSimulateSms: (String, String) -> Unit) {
    var body by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var tested by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BankSmsParser.Result?>(null) }
    Tile(Modifier.fillMaxWidth()) {
        SettingsGroupTitle(Icons.Outlined.Science, "آزمایش متن پیامک بانکی")
        Text("متن پیامک رو از برنامه پیام‌ها کپی کن و اینجا بچسبون؛ بدون دریافت SMS واقعی هم تشخیص آزمایش می‌شه.",
            fontSize = 12.sp, color = muted)
        OutlinedTextField(
            value = sender,
            onValueChange = { sender = it; tested = false },
            label = { Text("فرستنده (اختیاری)") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it; tested = false },
            label = { Text("متن پیامک") },
            minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { result = BankSmsParser.parse(body, sender); tested = true },
            enabled = body.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("تست تشخیص پیامک")
        }
        if (tested) {
            val found = result
            if (found == null) {
                Text("این متن با قواعد فعلی، تراکنش بانکی تشخیص داده نشد.",
                    color = danger, fontSize = 12.sp)
                Text("برای اصلاح الگوریتم، همین نمونه رو به جسی بفرست.",
                    color = muted, fontSize = 11.sp)
            } else {
                Text("پیامک شناسایی شد", color = mint, fontWeight = FontWeight.Bold)
                Text("بانک: " + found.bankName.ifBlank { "نامشخص" }, fontSize = 12.sp)
                Text("نوع: " + if (found.kind == "IN") "واریز" else "برداشت",
                    fontSize = 12.sp)
                Text(
                    "مبلغ: " + if (found.amountUnit == BankSmsParser.UNKNOWN) {
                        digitsFa(NumberFormat.getIntegerInstance(Locale.US).format(found.amountRaw)) +
                            " (واحد نامشخص؛ هنگام بررسی باید انتخاب شود)"
                    } else toman(found.amountToman),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
                OutlinedButton(
                    onClick = { onSimulateSms(body, sender) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("شبیه‌سازی صف بررسی و اعلان")
                }
                Text("این آزمایش وارد صف می‌شه و اعلان آزمایشی می‌فرسته، اما قابل ثبت به‌عنوان تراکنش واقعی نیست.",
                    fontSize = 11.sp, color = muted)
            }
        }
        Text("متن پیامک در فرم نگهداری دائمی نمی‌شود؛ تست مستقل از مجوز دریافت SMS است.",
            fontSize = 11.sp, color = muted)
    }
}

@Composable
private fun SettingsGroupTitle(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = RoundedCornerShape(11.dp), color = mint.copy(alpha = .13f)) {
            Icon(icon, contentDescription = null, tint = mint,
                modifier = Modifier.padding(8.dp).size(21.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector, title: String, description: String,
    checked: Boolean, onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = muted, modifier = Modifier.size(22.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(description, color = muted, fontSize = 10.sp, lineHeight = 15.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange,
            modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun FinanceSummaryChip(title: String, value: Long, income: Boolean, modifier: Modifier = Modifier, hidden: Boolean = false) {
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
        Text(if (hidden) "••••••" else toman(value), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            color = if (LocalJessiDark.current) color
                else if (income) Color(0xFF007D56) else Color(0xFFC82C56),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DashboardAction(
    icon: ImageVector, title: String, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    val dark = LocalJessiDark.current
    Surface(
        onClick = onClick, modifier = modifier.height(71.dp),
        color = if (dark) Color(0xFF201B4C) else Color(0xFFF1F8F7),
        shape = RoundedCornerShape(15.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .46f))
    ) {
        Column(
            Modifier.padding(vertical = 9.dp, horizontal = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = title, tint = mint, modifier = Modifier.size(23.dp))
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
    val dark = LocalJessiDark.current
    val panel = if (dark) Color(0xFF252252) else Color(0xFFEBF5F3)
    val jalaliDate = Jalali.of(period.start)
    val monthNames = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val title = if (mode == FinancePeriodEngine.MONTHLY)
        monthNames.getOrElse(jalaliDate.month - 1) { "ماه" } + " " + digitsFa(jalaliDate.year.toString())
    else period.label
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(.61f).height(51.dp),
            color = panel, shape = RoundedCornerShape(15.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, enabled = selectedIndex < count - 1,
                    modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "دوره قبل",
                        tint = if (selectedIndex < count-1) mint else muted, modifier = Modifier.size(21.dp))
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("دوره مالی", fontSize = 10.sp, color = muted)
                    Text(title, color = mint, fontWeight = FontWeight.Bold,
                        fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onNext, enabled = selectedIndex > 0,
                    modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Outlined.ChevronLeft, contentDescription = "دوره بعد",
                        tint = if (selectedIndex > 0) mint else muted, modifier = Modifier.size(21.dp))
                }
            }
        }
        Box(Modifier.weight(.39f)) {
            Surface(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth().height(51.dp),
                shape = RoundedCornerShape(15.dp),
                color = panel,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .4f))
            ) {
                Row(
                    Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(if (mode == FinancePeriodEngine.SALARY) "از حقوق" else "ماه شمسی",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null,
                        tint = muted, modifier = Modifier.size(19.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("ماه شمسی") },
                    onClick = { onModeChange(FinancePeriodEngine.MONTHLY); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("از هر حقوق جدید") },
                    onClick = { onModeChange(FinancePeriodEngine.SALARY); expanded = false }
                )
            }
        }
    }
    if (mode == FinancePeriodEngine.SALARY && !salaryAvailable) {
        Text("بعد از تأیید اولین واریز حقوق، دوره‌های حقوقی فعال می‌شن.",
            fontSize = 11.sp, color = muted)
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
    onReview: (SmsReview) -> Unit, onNewTx: () -> Unit, onSelectTab: (Tab) -> Unit,
    balanceVisible: Boolean, onBalanceVisibilityChange: (Boolean) -> Unit,
    smsMonitoringEnabled: Boolean, reviewNotificationsEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val dark = LocalJessiDark.current
    val total = accounts.sumOf { accountBalance(it.id, it.openingToman, txs) }
    val current = txs.filter { currentPeriod.contains(it.createdAt) }
    val income = current.filter { it.kind == "IN" }.sumOf { it.amountToman }
    val expense = current.filter { it.kind == "OUT" }.sumOf { it.amountToman }
    val heroGradient = if (dark)
        Brush.linearGradient(listOf(Color(0xFF123E6A), Color(0xFF28205E), Color(0xFF461D75)))
    else Brush.linearGradient(listOf(Color(0xFFEAF8F2), Color(0xFFF4FBFF), Color(0xFFF9F7FF)))
    val heroShape = RoundedCornerShape(22.dp)

    // Dashboard follows the reference top-to-bottom, using real account and period data.
    Column(
        Modifier.fillMaxWidth().clip(heroShape).background(heroGradient)
            .border(BorderStroke(1.dp,
                if (dark) Color(0xFF4B58C4).copy(alpha = .7f)
                else Color(0xFFDDEEE8)), heroShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (dark) Color(0xFF59318C) else Color(0xFFD9F5E5),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(22.dp), tint = mint)
            }
            Spacer(Modifier.width(9.dp))
            Text("موجودی کل حساب‌ها", modifier = Modifier.weight(1f),
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
            IconButton(onClick = { onBalanceVisibilityChange(!balanceVisible) }, modifier = Modifier.size(36.dp)) {
                Icon(if (balanceVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                    contentDescription = if (balanceVisible) "پنهان کردن موجودی" else "نمایش موجودی",
                    tint = muted, modifier = Modifier.size(21.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(if (balanceVisible) digitsFa(NumberFormat.getIntegerInstance(Locale.US).format(total))
                    else "••••••••",
                fontSize = 29.sp, fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Text("تومان", fontSize = 12.sp, color = muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            FinanceSummaryChip("درآمد", income, income = true,
                modifier = Modifier.weight(1f), hidden = !balanceVisible)
            FinanceSummaryChip("هزینه", expense, income = false,
                modifier = Modifier.weight(1f), hidden = !balanceVisible)
        }
    }

    Tile(Modifier.fillMaxWidth()) {
        SpendingCategoryChart(current)
    }
    Tile(Modifier.fillMaxWidth()) {
        FinancialTrendChart(periods, selectedPeriodIndex, txs)
    }

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        DashboardAction(Icons.Outlined.AddCircleOutline, "تراکنش جدید", Modifier.weight(1f), onNewTx)
        DashboardAction(Icons.Outlined.EventNote, "اقساط و بدهی", Modifier.weight(1f)) { onSelectTab(Tab.DEBTS) }
        DashboardAction(Icons.Outlined.AccountBalanceWallet, "حساب‌ها", Modifier.weight(1f)) { onSelectTab(Tab.ACCOUNTS) }
        DashboardAction(Icons.Outlined.BarChart, "گزارش‌ها", Modifier.weight(1f)) { onSelectTab(Tab.BUDGETS) }
    }

    val nextDebt = debts.asSequence()
        .filter { it.direction == "I_OWE" && it.installmentMonths > 0 &&
            Installments.remainingCount(it) > 0 }
        .mapNotNull { debt ->
            Installments.nextDate(debt)?.let { date ->
                JalaliDates.toEpoch(date)?.let { epoch -> Triple(debt, date, epoch) }
            }
        }.minByOrNull { it.third }

    Tile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Layers, contentDescription = null, tint = mint,
                modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(7.dp))
            Text("اقساط و بدهی", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 15.sp)
            TextButton(onClick = { onSelectTab(Tab.DEBTS) }, contentPadding = PaddingValues(4.dp)) {
                Text("مشاهده", fontSize = 11.sp)
                Icon(Icons.Outlined.ChevronLeft, contentDescription = null, modifier = Modifier.size(17.dp))
            }
        }
        if (nextDebt == null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.EventAvailable, contentDescription = null, tint = mint)
                Text("هنوز قسط فعالی نداری؛ از بخش اقساط اضافه کن.",
                    color = muted, fontSize = 12.sp)
            }
        } else {
            val (debt, date, _) = nextDebt
            Row(
                Modifier.fillMaxWidth().background(secondary.copy(alpha = .75f), RoundedCornerShape(15.dp))
                    .padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(color = mint.copy(alpha = .13f), shape = RoundedCornerShape(12.dp)) {
                    Icon(Icons.Outlined.CreditCard, contentDescription = null, tint = mint,
                        modifier = Modifier.padding(9.dp).size(23.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(debt.person, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("قسط ماهانه: " + toman(Installments.nextAmount(debt)), fontSize = 11.sp, color = muted)
                    Text("سررسید بعدی: " + JalaliDates.format(date), fontSize = 11.sp, color = muted)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(digitsFa(Installments.remainingCount(debt).toString()),
                        color = mint, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("قسط باقی", color = muted, fontSize = 10.sp)
                }
            }
        }
    }

    Tile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = muted,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("تراکنش‌های اخیر", modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold, fontSize = 15.sp)
            TextButton(onClick = { onSelectTab(Tab.TXS) }, contentPadding = PaddingValues(4.dp)) {
                Text("مشاهده همه", fontSize = 11.sp)
                Icon(Icons.Outlined.ChevronLeft, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        val recent = current.filter { it.kind != "TRANSFER_IN" }.take(4)
        if (recent.isEmpty()) {
            Text("تراکنشی در این دوره ثبت نشده.", color = muted, fontSize = 12.sp)
        } else {
            recent.forEachIndexed { i, tx ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                DashboardRecentTransaction(tx, accounts, txs)
            }
        }
    }

    // Keep the full prior features, but below the photographed dashboard layout.
    if (reviews.isNotEmpty()) {
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Markunread, tint = mint, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("پیامک‌های منتظر بررسی", modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Tag(digitsFa(reviews.size.toString()), true)
            }
            reviews.take(5).forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (row.kind == "IN") "واریز احتمالی" else "برداشت احتمالی",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${reviewAmountLabel(row)} • ${row.sender}", fontSize = 11.sp, color = muted)
                    }
                    TextButton(onClick = { onReview(row) }) { Text("بررسی") }
                }
            }
        }
    }
    if (debts.any { it.totalToman > it.paidToman }) {
        Tile(Modifier.fillMaxWidth()) {
            Text("خلاصه بدهی و طلب", fontWeight = FontWeight.Bold)
            Row {
                MoneyLine("بدهی باقی‌مانده", debts.filter { it.direction == "I_OWE" }
                    .sumOf { it.totalToman - it.paidToman }, danger, Modifier.weight(1f))
                MoneyLine("طلب باقی‌مانده", debts.filter { it.direction == "OWED_TO_ME" }
                    .sumOf { it.totalToman - it.paidToman }, mint, Modifier.weight(1f))
            }
        }
    }
    Tile(Modifier.fillMaxWidth()) { InstallmentForecastChart(debts, nowEpoch) }
    if (budgets.isNotEmpty()) {
        Tile(Modifier.fillMaxWidth()) {
            Text("بودجه‌های این دوره", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            budgets.take(4).forEach { budget ->
                val spent = current.filter { it.kind == "OUT" && it.category == budget.category }
                    .sumOf { it.amountToman }
                val fraction = (spent.toFloat() / budget.monthlyLimitToman.coerceAtLeast(1)).coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(budget.category, color = muted, fontSize = 12.sp)
                    Text(digitsFa((fraction * 100).toInt().toString()) + "٪",
                        color = if (spent > budget.monthlyLimitToman) danger else mint, fontSize = 12.sp)
                }
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(),
                    color = mint, trackColor = secondary)
            }
        }
    }
    if (!smsPermitted || !notificationsPermitted || !smsMonitoringEnabled || !reviewNotificationsEnabled) {
        Tile(Modifier.fillMaxWidth()) {
            Text("دسترسی‌های پیامک بانکی", fontWeight = FontWeight.Bold)
            if (!smsMonitoringEnabled || !reviewNotificationsEnabled) {
                Text("بررسی پیامک یا اعلان‌ها از تنظیمات جسی غیرفعال شده.",
                    fontSize = 12.sp, color = muted)
                TextButton(onClick = onOpenSettings) { Text("تنظیمات پیامک و اعلان") }
            }
            if (!smsPermitted) {
                Text("برای شناسایی پیامک‌های بانکی جدید، مجوز SMS را فعال کن.", fontSize = 12.sp, color = muted)
                TextButton(onClick = requestPermission) { Text("فعال‌کردن SMS") }
            }
            if (!notificationsPermitted) {
                Text("برای اعلان بررسی تراکنش، مجوز اعلان را فعال کن.", fontSize = 12.sp, color = muted)
                TextButton(onClick = requestNotificationPermission) { Text("فعال‌کردن اعلان") }
            }
        }
    }
}

@Composable
private fun DashboardRecentTransaction(
    row: Transaction, accounts: List<Account>, allTransactions: List<Transaction>
) {
    val incoming = row.kind == "IN" || row.kind == "DEBT_IN"
    val transferTarget = if (row.kind == "TRANSFER_OUT") allTransactions
        .firstOrNull { it.groupId == row.groupId && it.kind == "TRANSFER_IN" }?.accountId else null
    val label = if (transferTarget != null)
        "انتقال به " + (accounts.firstOrNull { it.id == transferTarget }?.title ?: "حساب دیگر")
    else if (row.note.isNotBlank()) row.note else row.category
    val icon = when {
        incoming -> Icons.Outlined.AccountBalance
        row.category == "خوراک" || row.category == "خرید" -> Icons.Outlined.ShoppingCart
        row.category == "قبوض" -> Icons.Outlined.Bolt
        else -> Icons.Outlined.ReceiptLong
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            shape = RoundedCornerShape(11.dp),
            color = if (incoming) mint.copy(alpha = .14f) else secondary
        ) {
            Icon(icon, contentDescription = null, tint = if (incoming) mint else muted,
                modifier = Modifier.padding(9.dp).size(21.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text((accounts.firstOrNull { it.id == row.accountId }?.title ?: "حساب نامشخص") +
                " • " + Jalali.of(row.createdAt), color = muted, fontSize = 10.sp)
        }
        Text((if (incoming) "+" else "−") + digitsFa(
            NumberFormat.getIntegerInstance(Locale.US).format(row.amountToman)
        ), color = if (incoming) mint else danger,
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
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
            JalaliDateInput("تاریخ اولین سررسید شمسی", firstDue, onChange = { firstDue = it })
            if (amount != null && months != null && months > 0) {
                Text("مبلغ تقریبی هر قسط: ${toman(amount / months)} • مانده با ثبت پرداخت کم می‌شه.",
                    fontSize = 11.sp, color = mint)
            }
        } else {
            JalaliDateInput("تاریخ سررسید شمسی", due, optional = true, onChange = { due = it })
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

private fun reviewAmountLabel(row: SmsReview): String {
    val raw = row.amountRaw.takeIf { it > 0L } ?: row.amountToman
    return if (row.amountUnit == "UNKNOWN")
        digitsFa(NumberFormat.getIntegerInstance(Locale.US).format(raw)) + " (واحد نامشخص)"
    else toman(row.amountToman)
}

@Composable
private fun ReviewDialog(
    row: SmsReview, accounts: List<Account>, dismiss: () -> Unit,
    onAccept: (Long, String, String, String) -> Unit, onReject: () -> Unit
) {
    val isSimulation = row.fingerprint.startsWith("JESSI_TEST_")
    var accountId by remember(row.id) { mutableStateOf(row.accountGuess ?: accounts.firstOrNull()?.id) }
    var category by remember(row.id) { mutableStateOf(if (row.kind == "IN") "درآمد" else "سایر") }
    var note by remember(row.id) { mutableStateOf("") }
    val needsUnit = row.amountUnit == "UNKNOWN"
    var selectedUnit by remember(row.id) { mutableStateOf(if (needsUnit) "" else row.amountUnit) }
    val original = row.amountRaw.takeIf { it > 0L } ?: row.amountToman
    val selectedAmount = when (selectedUnit) {
        "TOMAN" -> original
        "RIAL" -> if (original % 10L == 0L) original / 10L else 0L
        else -> 0L
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("بررسی پیامک بانکی") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isSimulation) Text("این یک آزمایش است و در حساب‌های واقعی ثبت نمی‌شود.",
                    color = danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(if (row.kind == "IN") "واریز احتمالی" else "برداشت احتمالی", color = mint)
                if (row.bankName.isNotBlank()) Text(row.bankName, fontSize = 13.sp, color = muted)
                Text(if (needsUnit && selectedUnit.isBlank())
                    reviewAmountLabel(row) else toman(selectedAmount),
                    fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("فرستنده: ${row.sender}", fontSize = 12.sp)
                if (needsUnit) {
                    Text("واحد پول در پیامک مشخص نیست؛ قبل از ثبت انتخاب کن:",
                        color = danger, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = selectedUnit == "RIAL",
                            onClick = { selectedUnit = "RIAL" },
                            label = { Text("ریال") }
                        )
                        FilterChip(
                            selected = selectedUnit == "TOMAN",
                            onClick = { selectedUnit = "TOMAN" },
                            label = { Text("تومان") }
                        )
                    }
                    if (selectedUnit == "RIAL" && original % 10L != 0L) {
                        Text("مبلغ ریالی به تومانِ صحیح قابل تبدیل نیست؛ این مورد را دستی بررسی کن.",
                            fontSize = 11.sp, color = danger)
                    }
                }
                if (accounts.isEmpty()) Text("اول یک حساب بساز.", color = danger)
                else SelectItem("این تراکنش برای کدام حساب است؟",
                    accountId?.let { id -> accounts.firstOrNull { it.id == id } },
                    accounts, { it.title }, { accountId = it.id })
                SelectItem("برای چه دسته‌ای بود؟", category,
                    if (row.kind == "IN") incomeCategories else categories,
                    { it }, { category = it })
                if (category == "حقوق" && row.kind == "IN") {
                    Text("این واریز می‌تونه دوره جدید رو در حالت «از حقوق» آغاز کنه.",
                        color = mint, fontSize = 11.sp)
                }
                TextEntry("برای چی بود؟ (توضیح اختیاری)", note, { note = it }, singleLine = false)
                Text("قبل از تأیید، مبلغ و نوع تراکنش را با پیامک اصلی تطبیق بده.",
                    fontSize = 11.sp, color = muted)
            }
        },
        confirmButton = {
            if (isSimulation) {
                Button(onClick = onReject) { Text("پایان آزمایش و حذف از صف") }
            } else {
                Button(
                    onClick = { accountId?.let { onAccept(it, category, note, selectedUnit) } },
                    enabled = accountId != null && selectedAmount > 0L
                ) { Text("ثبت تراکنش") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReject) { Text("نادیده گرفتن", color = danger) }
                TextButton(onClick = dismiss) { Text("بعداً") }
            }
        }
    )
}
