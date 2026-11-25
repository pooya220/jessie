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
import android.os.SystemClock
import ir.jessi.finance.sms.SmsReviewNotifications
import ir.jessi.finance.notifications.FinanceReminderScheduler
import ir.jessi.finance.widgets.JessiWidgetUpdater
import ir.jessi.finance.sms.SmsDiagnostics
import ir.jessi.finance.sms.BankSmsParser
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import ir.jessi.finance.security.PinSecurity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.draw.shadow
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
    primary = Color(0xFF008E5B), onPrimary = Color.White,
    secondary = Color(0xFF087D91), background = Color(0xFFF5FAF8),
    surface = Color.White, surfaceVariant = Color(0xFFEAF4F0),
    onSurface = Color(0xFF152A2D), onSurfaceVariant = Color(0xFF627980),
    error = Color(0xFFD83D67), outline = Color(0xFFDCEBE8)
)
private val darkColors = darkColorScheme(
    primary = Color(0xFFD062FF), onPrimary = Color.White,
    secondary = Color(0xFF41DDFB), onSecondary = Color.White,
    background = Color(0xFF000000), onBackground = Color.White,
    surface = Color(0xFF151027), surfaceVariant = Color(0xFF241935),
    onSurface = Color.White, onSurfaceVariant = Color.White,
    onPrimaryContainer = Color.White, onSecondaryContainer = Color.White,
    onTertiaryContainer = Color.White, onError = Color.White,
    error = Color(0xFFFF699E), outline = Color(0xFF3B4078)
)
internal val LocalJessiDark = compositionLocalOf { true }

// Consistent four-tone accents shared across every tab: jade, cyan, violet and amber.
private val financeAqua: Color @Composable get() =
    if (LocalJessiDark.current) Color(0xFF36E8C4) else Color(0xFF008F69)
private val financeBlue: Color @Composable get() =
    if (LocalJessiDark.current) Color(0xFF43C8FF) else Color(0xFF2477C8)
private val financePurple: Color @Composable get() =
    if (LocalJessiDark.current) Color(0xFFD062FF) else Color(0xFF8450C8)
private val financeAmber: Color @Composable get() =
    if (LocalJessiDark.current) Color(0xFFFFC779) else Color(0xFFB47827)



class MainActivity : FragmentActivity() {
    private val appLocked = mutableStateOf(true)
    private var backgroundedAtElapsed = 0L

    override fun onStop() {
        super.onStop()
        // Do not re-lock instantly on a quick app switch. Time is monotonic,
        // so changes to device wall clock cannot extend an unlocked session.
        if (!appLocked.value && PinSecurity(this).isEnabled()) {
            backgroundedAtElapsed = SystemClock.elapsedRealtime()
        }
    }

    override fun onStart() {
        super.onStart()
        val security = PinSecurity(this)
        if (security.isEnabled() && !appLocked.value &&
            ir.jessi.finance.security.LockTimeoutPolicy.hasExpired(
                backgroundedAtElapsed,
                SystemClock.elapsedRealtime(),
                security.backgroundTimeoutMinutes()
            )
        ) appLocked.value = true
        backgroundedAtElapsed = 0L
    }

    private fun promptBiometricUnlock(pinSecurity: PinSecurity, onSuccess: () -> Unit) {
        val authenticator = BiometricManager.Authenticators.BIOMETRIC_WEAK
        if (!pinSecurity.biometricsEnabled() ||
            BiometricManager.from(this).canAuthenticate(authenticator) != BiometricManager.BIOMETRIC_SUCCESS
        ) return
        try {
            val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        pinSecurity.clearFailures()
                        onSuccess()
                    }
                })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("ورود امن به جسی")
                .setSubtitle("با اثرانگشت وارد شو یا ورود با پین را انتخاب کن")
                .setAllowedAuthenticators(authenticator)
                .setNegativeButtonText("ورود با پین")
                .build())
        } catch (_: Exception) {
            // App PIN stays available if biometric is unavailable.
        }
    }

    private val notificationReviewId = mutableLongStateOf(-1L)
    private val reminderDestination = mutableStateOf<String?>(null)
    private val nowEpoch = mutableLongStateOf(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore editable category choices before the first Compose frame.
        FinanceCategories.initialize(this)
        notificationReviewId.longValue = intent.getLongExtra(SmsReviewNotifications.EXTRA_REVIEW_ID, -1L)
        reminderDestination.value = intent.getStringExtra(FinanceReminderScheduler.EXTRA_OPEN_TAB)
        FinanceReminderScheduler.start(this)
        JessiWidgetUpdater.request(this)
        setContent {
            val themePrefs = remember { getSharedPreferences("jessi_ui_theme", MODE_PRIVATE) }
            val pinSecurity = remember { PinSecurity(this@MainActivity) }
            var securityEnabled by remember { mutableStateOf(pinSecurity.isEnabled()) }
            var darkMode by rememberSaveable { mutableStateOf(themePrefs.getBoolean("is_dark", true)) }
            LaunchedEffect(securityEnabled, appLocked.value) {
                if (securityEnabled && appLocked.value) {
                    promptBiometricUnlock(pinSecurity) { appLocked.value = false }
                }
            }
            SideEffect {
                window.statusBarColor = if (darkMode) android.graphics.Color.BLACK
                    else android.graphics.Color.rgb(246, 250, 251)
                window.navigationBarColor = if (darkMode) android.graphics.Color.BLACK
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
                    CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides
                            (if (darkMode) Color.White else MaterialTheme.colorScheme.onSurface)
                    ) {
                    if (securityEnabled && appLocked.value) {
                        // The lock screen uses exactly the same saved backdrop as Home.
                        val backdropPrefs = getSharedPreferences(
                            "jessi_finance_settings", MODE_PRIVATE
                        )
                        val loginStyle = backdropPrefs.getString("backdrop_style", "aurora") ?: "aurora"
                        val loginPhoto = backdropPrefs.getString("backdrop_photo_uri", "") ?: ""
                        val loginDim = backdropPrefs.getFloat(
                            "backdrop_dim", if (darkMode) .18f else .12f
                        )
                        val loginDarkness = backdropPrefs.getFloat("backdrop_darkness", 0f)
                        Box(Modifier.fillMaxSize()) {
                            JessiBackdrop(
                                style = loginStyle, photoUri = loginPhoto,
                                dim = loginDim, dark = darkMode,
                                darkness = loginDarkness,
                                modifier = Modifier.fillMaxSize()
                            )
                            JessiLockScreen(
                                security = pinSecurity,
                                onUnlocked = { appLocked.value = false },
                                biometricAvailable = BiometricManager.from(this@MainActivity)
                                    .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                                    BiometricManager.BIOMETRIC_SUCCESS,
                                onTryBiometrics = {
                                    promptBiometricUnlock(pinSecurity) { appLocked.value = false }
                                }
                            )
                        }
                    } else JessiHome(
                        openReviewId = notificationReviewId.longValue,
                        openReminderTab = reminderDestination.value,
                        onReminderOpened = { reminderDestination.value = null },
                        pinSecurity = pinSecurity,
                        onSecurityChanged = {
                            securityEnabled = pinSecurity.isEnabled()
                            appLocked.value = false
                        },
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
    }

    override fun onResume() {
        super.onResume()
        nowEpoch.longValue = System.currentTimeMillis()
        FinanceReminderScheduler.checkSoon(this)
        JessiWidgetUpdater.request(this)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationReviewId.longValue = intent.getLongExtra(SmsReviewNotifications.EXTRA_REVIEW_ID, -1L)
        reminderDestination.value = intent.getStringExtra(FinanceReminderScheduler.EXTRA_OPEN_TAB)
    }
}

private enum class Tab(val label: String) {
    // Semantic Material icons replace the previous text glyphs.
    ACCOUNTS("حساب‌ها"), TXS("تراکنش‌ها"),
    HOME("خانه"), DEBTS("اقساط"), GOALS("اهداف")
}
private sealed interface Editor {
    data class AccountForm(val value: Account? = null) : Editor
    data class TxForm(val value: Transaction? = null) : Editor
    data class DebtForm(val value: Debt? = null) : Editor
    data class BudgetForm(val value: Budget? = null) : Editor
    data class GoalForm(
        val value: SavingsGoal? = null,
        val defaultCategory: String = "UNIVERSITY"
    ) : Editor
    data class GoalMoney(val goal: SavingsGoal, val operation: String) : Editor
    data class Repay(val value: Debt) : Editor
    data class DebtPaymentForm(val value: Transaction) : Editor
    data class Review(val value: SmsReview) : Editor
    data class Delete(val title: String, val action: () -> Unit) : Editor
}

@Composable
private fun JessiHome(openReviewId: Long, openReminderTab: String?,
    onReminderOpened: () -> Unit, nowEpoch: Long, darkMode: Boolean,
    onToggleTheme: () -> Unit, onReviewOpened: () -> Unit,
    pinSecurity: PinSecurity, onSecurityChanged: () -> Unit,
    vm: FinanceViewModel = viewModel()
) {
    val accounts by vm.accounts.collectAsState()
    val txs by vm.transactions.collectAsState()
    val debts by vm.debts.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val reviews by vm.reviews.collectAsState()
    val goals by vm.goals.collectAsState()
    val goalMovements by vm.goalMovements.collectAsState()
    val context = LocalContext.current
    val settings = remember(context) { context.getSharedPreferences("jessi_finance_settings", android.content.Context.MODE_PRIVATE) }
    var backdropStyle by remember { mutableStateOf(settings.getString("backdrop_style", "aurora") ?: "aurora") }
    var backdropUri by remember { mutableStateOf(settings.getString("backdrop_photo_uri", "") ?: "") }
    var backdropDim by remember {
        mutableFloatStateOf(settings.getFloat("backdrop_dim", if (darkMode) .18f else .12f))
    }
    // Independent, persistent dark-mode background black overlay (0..100%).
    // Existing glass strength and saved background choice are not changed.
    var backdropDarkness by remember {
        mutableFloatStateOf(settings.getFloat("backdrop_darkness", 0f).coerceIn(0f, 1f))
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                backdropUri = uri.toString()
                backdropStyle = "photo"
                settings.edit().putString("backdrop_photo_uri", backdropUri)
                    .putString("backdrop_style", backdropStyle).apply()
            } catch (_: Exception) {
                Toast.makeText(context, "دسترسی به تصویر پس‌زمینه ممکن نبود",
                    Toast.LENGTH_LONG).show()
            }
        }
    }
    var periodMode by remember { mutableStateOf(settings.getString("period_mode", FinancePeriodEngine.MONTHLY) ?: FinancePeriodEngine.MONTHLY) }
    var periodOffset by rememberSaveable { mutableIntStateOf(0) }
    var balanceVisible by remember {
        mutableStateOf(settings.getBoolean("show_balance", true))
    }
    // Launcher tiles are outside Jessie's biometric gate. Amounts are
    // hidden until the owner explicitly opts into widget visibility.
    var widgetBalanceVisible by remember {
        mutableStateOf(settings.getBoolean(JessiWidgetUpdater.BALANCE_VISIBLE_KEY, false))
    }
    var smsMonitoringEnabled by remember {
        mutableStateOf(settings.getBoolean("sms_monitoring_enabled", true))
    }
    var reviewNotificationsEnabled by remember {
        mutableStateOf(settings.getBoolean("sms_review_notifications_enabled", true))
    }
    var budgetRemindersEnabled by remember {
        mutableStateOf(settings.getBoolean(FinanceReminderScheduler.PREF_BUDGET_ENABLED, true))
    }
    var installmentRemindersEnabled by remember {
        mutableStateOf(settings.getBoolean(FinanceReminderScheduler.PREF_INSTALLMENT_ENABLED, true))
    }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var diagnosticRefresh by remember { mutableIntStateOf(0) }
    var dueReminderDiagnostic by remember { mutableStateOf("") }
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
    // Budget management is retained within the Accounts destination.
    var accountsShowBudgets by rememberSaveable { mutableStateOf(false) }
    // Opening a financial reminder takes the user to the relevant existing tab.
    // The original app PIN/biometric gate remains in effect.
    LaunchedEffect(openReminderTab) {
        when (openReminderTab) {
            "ACCOUNTS" -> {
                tabName = Tab.ACCOUNTS.name
                accountsShowBudgets = true
                showSettings = false
                periodOffset = 0
            }
            "BANK_ACCOUNTS" -> {
                tabName = Tab.ACCOUNTS.name
                accountsShowBudgets = false
                showSettings = false
                periodOffset = 0
            }
            "TXS" -> {
                tabName = Tab.TXS.name
                showSettings = false
                periodOffset = 0
            }
            "GOALS" -> {
                tabName = Tab.GOALS.name
                showSettings = false
                periodOffset = 0
            }
            "DEBTS" -> {
                tabName = Tab.DEBTS.name
                showSettings = false
                periodOffset = 0
            }
        }
        if (openReminderTab != null) onReminderOpened()
    }
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

    CompositionLocalProvider(LocalJessiBackdrop provides backdropStyle) {
    Box(Modifier.fillMaxSize()) {
        JessiBackdrop(backdropStyle, backdropUri, backdropDim, darkMode,
            darkness = backdropDarkness, modifier = Modifier.fillMaxSize())
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = if (darkMode) Color.White else MaterialTheme.colorScheme.onSurface,
        topBar = {
            // RTL: settings on the visual right, light/dark on the visual left.
            // Title is anchored to the true center, independent of action widths.
            Box(
                Modifier.fillMaxWidth().height(74.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "جسی", fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = jessiReadableText(MaterialTheme.colorScheme.onSurface)
                    )
                    Text(
                        "Powerd by PR",
                        color = jessiReadableText(muted),
                        fontSize = 9.sp, letterSpacing = .7.sp
                    )
                }
                val headerButtonShape = RoundedCornerShape(16.dp)
                val headerGlass = Brush.linearGradient(
                    if (darkMode) listOf(
                        Color(0xFF321A46), Color(0xFF142647), Color(0xFF3A1B42)
                    ) else listOf(
                        Color(0xFFF7EBFF), Color(0xFFEBF3FF), Color(0xFFFFF0F9)
                    )
                )
                IconButton(
                    onClick = { showSettings = !showSettings },
                    modifier = Modifier.align(Alignment.CenterStart)
                        .size(45.dp).clip(headerButtonShape)
                        .background(headerGlass)
                        .border(
                            1.dp,
                            if (darkMode) Color(0xFF9B7BCC).copy(alpha = .60f)
                            else Color(0xFFD7BFE9),
                            headerButtonShape
                        )
                ) {
                    Icon(
                        if (showSettings) Icons.Outlined.ArrowBack
                        else Icons.Outlined.Settings,
                        contentDescription = if (showSettings)
                            "بازگشت به برنامه" else "تنظیمات جسی",
                        tint = if (darkMode) Color(0xFFF2E6FF) else muted,
                        modifier = Modifier.size(23.dp)
                    )
                }
                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier.align(Alignment.CenterEnd)
                        .size(45.dp).clip(headerButtonShape)
                        .background(headerGlass)
                        .border(
                            1.dp,
                            if (darkMode) Color(0xFF8D96E8).copy(alpha = .55f)
                            else Color(0xFFC4D4F5),
                            headerButtonShape
                        )
                ) {
                    Icon(
                        if (darkMode) Icons.Outlined.DarkMode
                        else Icons.Outlined.LightMode,
                        contentDescription = if (darkMode)
                            "تغییر به حالت روشن" else "تغییر به حالت تیره",
                        tint = if (darkMode) Color(0xFFD1BCFF) else muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        },
        bottomBar = {
            // Only the five-tab dock appearance changes. Every existing destination,
            // tab state and action remains exactly as before.
            val dockShape = RoundedCornerShape(36.dp)
            val dockGlass = Brush.linearGradient(
                if (darkMode) listOf(
                    Color(0xF218203E), Color(0xF0211634), Color(0xF31C142F)
                ) else listOf(
                    Color(0xF8FFFFFF), Color(0xEDF0F3FF), Color(0xF7FFFFFF)
                )
            )
            val dockStroke = Brush.horizontalGradient(
                if (darkMode) listOf(
                    Color(0xFF669DF6).copy(alpha = .7f),
                    Color(0xFFC782ED).copy(alpha = .8f),
                    Color(0xFFF17FC2).copy(alpha = .75f)
                ) else listOf(
                    Color.White, Color(0xFFCBBEF4), Color.White
                )
            )
            Box(
                Modifier.fillMaxWidth().height(112.dp)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Box(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth().height(75.dp)
                        .shadow(12.dp, dockShape)
                        .clip(dockShape).background(dockGlass)
                        .border(1.dp, dockStroke, dockShape)
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(1.dp)
                            .background(Brush.horizontalGradient(listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = if (darkMode) .40f else .85f),
                                Color.Transparent
                            )))
                    )
                }
                Row(
                    Modifier.align(Alignment.BottomCenter)
                        .fillMaxWidth().height(94.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Tab.entries.forEach { item ->
                        val selected = tab == item && !showSettings
                        val outlineIcon: ImageVector = when (item) {
                            Tab.ACCOUNTS -> Icons.Outlined.AccountBalanceWallet
                            Tab.TXS -> Icons.Outlined.ReceiptLong
                            Tab.HOME -> Icons.Outlined.Home
                            Tab.DEBTS -> Icons.Outlined.EventNote
                            Tab.GOALS -> Icons.Outlined.Savings
                        }
                        val filledIcon: ImageVector = when (item) {
                            Tab.ACCOUNTS -> Icons.Filled.AccountBalanceWallet
                            Tab.TXS -> Icons.Filled.ReceiptLong
                            Tab.HOME -> Icons.Filled.Home
                            Tab.DEBTS -> Icons.Filled.EventNote
                            Tab.GOALS -> Icons.Filled.Savings
                        }
                        val progress by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = spring(
                                dampingRatio = .88f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "jessiDock_${item.name}"
                        )
                        val lift by animateDpAsState(
                            targetValue = if (selected) 16.dp else 0.dp,
                            animationSpec = spring(
                                dampingRatio = .88f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "jessiDockLift_${item.name}"
                        )
                        val bubbleSize by animateDpAsState(
                            targetValue = if (selected) 53.dp else 43.dp,
                            animationSpec = spring(
                                dampingRatio = .88f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "jessiDockBubble_${item.name}"
                        )
                        val normalTint = if (darkMode) Color(0xFFD1D5EC)
                            else Color(0xFF63748C)
                        val activeTint = if (darkMode) Color(0xFFD8BBFF)
                            else Color(0xFF7556BE)
                        Column(
                            // Do not clip the tab column: the selected bubble
                            // must be free to float above the glass panel without
                            // its circular rim or soft shadow being cut off.
                            Modifier.weight(1f).fillMaxHeight()
                                .clickable {
                                    tabName = item.name
                                    showSettings = false
                                }
                                .padding(bottom = 7.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                Modifier.height(53.dp).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(bubbleSize)
                                        .offset(y = -lift)
                                        .scale(1f + progress * .04f)
                                        .shadow(
                                            9.dp * progress,
                                            CircleShape, clip = false
                                        )
                                        .clip(CircleShape)
                                        .background(Brush.linearGradient(listOf(
                                            Color(0xFFB66CFF).copy(alpha = progress),
                                            Color(0xFF7361EE).copy(alpha = progress),
                                            Color(0xFF429CFD).copy(alpha = progress),
                                            Color(0xFFF083D0).copy(alpha = progress)
                                        )))
                                        .border(
                                            1.dp * progress,
                                            Color.White.copy(alpha = .55f * progress),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (selected) filledIcon else outlineIcon,
                                        contentDescription = item.label,
                                        tint = androidx.compose.ui.graphics.lerp(
                                            normalTint, Color.White, progress
                                        ),
                                        modifier = Modifier.size(
                                            if (selected) 25.dp else 23.dp
                                        )
                                    )
                                }
                            }
                            Text(
                                item.label, fontSize = 10.sp, maxLines = 1,
                                fontWeight = if (selected) FontWeight.Bold
                                    else FontWeight.Medium,
                                color = androidx.compose.ui.graphics.lerp(
                                    normalTint, activeTint, progress
                                )
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (tab != Tab.HOME && !showSettings) {
                FloatingActionButton(onClick = {
                    editor = when (tab) {
                        Tab.ACCOUNTS -> if (accountsShowBudgets) Editor.BudgetForm() else Editor.AccountForm()
                        Tab.TXS -> Editor.TxForm()
                        Tab.DEBTS -> Editor.DebtForm()
                        Tab.GOALS -> Editor.GoalForm()
                        Tab.HOME -> null
                    }
                }, containerColor = when (tab) {
                    Tab.ACCOUNTS -> financeAqua
                    Tab.TXS -> financeBlue
                    Tab.DEBTS -> financePurple
                    Tab.GOALS -> financePurple
                    else -> mint
                }) {
                    Icon(Icons.Filled.Add, contentDescription = "افزودن",
                        tint = if (darkMode) Color(0xFF14152D) else Color.White,
                        modifier = Modifier.size(26.dp))
                }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showSettings) {
                SettingsPage(
                    widgetBudgets = budgets,
                    darkMode = darkMode,
                    onToggleTheme = onToggleTheme,
                    balanceVisible = balanceVisible,
                    widgetBalanceVisible = widgetBalanceVisible,
                    onWidgetBalanceVisibilityChange = { value ->
                        widgetBalanceVisible = value
                        settings.edit().putBoolean(
                            JessiWidgetUpdater.BALANCE_VISIBLE_KEY, value
                        ).apply()
                        JessiWidgetUpdater.request(context)
                    },
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
                    onRenameCategory = { kind, old, name, callback ->
                        vm.renameCategory(kind, old, name) { ok, problem ->
                            if (ok) {
                                val saved = FinanceCategories.rename(kind, old, name)
                                callback(saved == null, saved)
                            } else callback(false, problem)
                        }
                    },
                    budgetRemindersEnabled = budgetRemindersEnabled,
                    installmentRemindersEnabled = installmentRemindersEnabled,
                    onBudgetRemindersChange = { value ->
                        budgetRemindersEnabled = value
                        settings.edit().putBoolean(
                            FinanceReminderScheduler.PREF_BUDGET_ENABLED, value
                        ).apply()
                        if (value) {
                            FinanceReminderScheduler.checkSoon(context)
                            if (!notificationsPermitted && Build.VERSION.SDK_INT >= 33)
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onInstallmentRemindersChange = { value ->
                        installmentRemindersEnabled = value
                        settings.edit().putBoolean(
                            FinanceReminderScheduler.PREF_INSTALLMENT_ENABLED, value
                        ).apply()
                        if (value) {
                            FinanceReminderScheduler.checkSoon(context)
                            if (!notificationsPermitted && Build.VERSION.SDK_INT >= 33)
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
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
                        FinanceReminderScheduler.checkSoon(context)
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
                    dueReminderDiagnostic = dueReminderDiagnostic,
                    onTestDueReminder = {
                        ioScope.launch {
                            val report = withContext(Dispatchers.IO) {
                                FinanceReminderScheduler.testInstallmentNotification(context) +
                                    "\n" + FinanceReminderScheduler.dueDiagnostics(context)
                            }
                            dueReminderDiagnostic = report
                            diagnosticRefresh++
                            Toast.makeText(
                                context,
                                report.substringBefore('\n'),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
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
                        vm.simulateSms(raw, sender) { message, candidate ->
                            diagnosticRefresh++
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            if (candidate != null) editor = Editor.Review(candidate)
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
                    backgroundStyle = backdropStyle,
                    backgroundPhotoUri = backdropUri,
                    backgroundDim = backdropDim,
                    backgroundDarkness = backdropDarkness,
                    onBackgroundStyle = { value ->
                        backdropStyle = value
                        settings.edit().putString("backdrop_style", value).apply()
                    },
                    onPickBackground = { photoPicker.launch(arrayOf("image/*")) },
                    onClearBackground = {
                        val previous = backdropUri
                        if (previous.isNotBlank()) runCatching {
                            context.contentResolver.releasePersistableUriPermission(
                                Uri.parse(previous), Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        backdropUri = ""
                        backdropStyle = "aurora"
                        settings.edit().remove("backdrop_photo_uri")
                            .putString("backdrop_style", "aurora").apply()
                    },
                    onBackgroundDim = { value ->
                        backdropDim = value
                        settings.edit().putFloat("backdrop_dim", value).apply()
                    },
                    onBackgroundDarkness = { value ->
                        backdropDarkness = value.coerceIn(0f, 1f)
                        settings.edit().putFloat("backdrop_darkness", backdropDarkness).apply()
                    },
                    pinSecurity = pinSecurity,
                    onSecurityChanged = onSecurityChanged,
                    versionName = remember(context, installedVersion) {
                        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "نامشخص"
                    }
                )
            } else {
            if (tab != Tab.GOALS) {
                PeriodSwitcher(
                    mode = periodMode, period = chosenPeriod, selectedIndex = selectedIndex,
                    count = periods.size,
                    salaryAvailable = txs.any { it.kind == "IN" && it.category == "حقوق" },
                    onModeChange = {
                        periodMode = it
                        periodOffset = 0
                        settings.edit().putString("period_mode", it).apply()
                        FinanceReminderScheduler.checkSoon(context)
                    },
                    onPrevious = { periodOffset = (selectedIndex + 1).coerceAtMost(periods.lastIndex) },
                    onNext = { periodOffset = (selectedIndex - 1).coerceAtLeast(0) }
                )
            }
            when (tab) {
                Tab.HOME -> Dashboard(
                    accounts, txs, debts, budgets, reviews, goals, goalMovements,
                    currentPeriod = chosenPeriod, periods = periods, selectedPeriodIndex = selectedIndex,
                    nowEpoch = nowEpoch,
                    smsPermitted = smsPermitted,
                    requestPermission = { permissionLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                    notificationsPermitted = notificationsPermitted,
                    requestNotificationPermission = { if (Build.VERSION.SDK_INT >= 33) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    onReview = { editor = Editor.Review(it) },
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
                Tab.ACCOUNTS -> {
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !accountsShowBudgets,
                            onClick = { accountsShowBudgets = false },
                            label = { Text("حساب‌های بانکی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
                            leadingIcon = if (!accountsShowBudgets) ({
                                Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(17.dp))
                            }) else null)
                        FilterChip(selected = accountsShowBudgets,
                            onClick = { accountsShowBudgets = true },
                            label = { Text("بودجه‌بندی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
                            leadingIcon = if (accountsShowBudgets) ({
                                Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(17.dp))
                            }) else null)
                    }
                    if (accountsShowBudgets) {
                        BudgetsPage(budgets, periodTransactions,
                            onEdit = { editor = Editor.BudgetForm(it) },
                            onDelete = { row -> editor = Editor.Delete("سقف «${row.category}» حذف شود؟") { vm.deleteBudget(row.id) } }
                        )
                    } else {
                        AccountsPage(accounts, txs,
                            onEdit = { editor = Editor.AccountForm(it) },
                            onDelete = { row -> editor = Editor.Delete("حساب «${row.title}» حذف شود؟ تنها حساب بدون تراکنش قابل حذف است.") { vm.deleteAccount(row.id) } }
                        )
                    }
                }
                Tab.TXS -> TransactionsPage(periodTransactions, accounts, debts,
                    onAssign = { ids, debtId, callback ->
                        vm.assignWithdrawalsToDebt(ids, debtId, callback)
                    },
                    onEdit = { editor = if (it.kind in listOf("DEBT_IN", "DEBT_OUT")) Editor.DebtPaymentForm(it) else Editor.TxForm(it) },
                    onDelete = { row ->
                        val debtLinked = row.kind in listOf("DEBT_IN", "DEBT_OUT")
                        editor = Editor.Delete(
                            if (debtLinked)
                                "این وصول/بازپرداخت حذف شود؟ مبلغ از پرداخت‌شده طلب یا بدهی هم برمی‌گردد."
                            else "این تراکنش حذف شود؟"
                        ) {
                            vm.deleteTransaction(row) { ok ->
                                if (ok) {
                                    Toast.makeText(context, "تراکنش حذف شد و مانده‌ها به‌روز شدند",
                                        Toast.LENGTH_LONG).show()
                                } else if (debtLinked) {
                                    editor = Editor.Delete(
                                        "ارتباط این وصول/بازپرداخت با طلب یا بدهی مشخص نیست. فقط همین تراکنش و اثر آن بر موجودی حساب حذف شود؟ سایر بدهی‌ها تغییر نمی‌کنند."
                                    ) {
                                        vm.deleteTransaction(row, allowUnresolvedDebtDeletion = true) { removed ->
                                            Toast.makeText(context,
                                                if (removed) "تراکنش حذف شد و موجودی اصلاح شد"
                                                else "این تراکنش محافظت‌شده است یا حذف آن انجام نشد",
                                                Toast.LENGTH_LONG).show()
                                        }
                                    }
                                } else {
                                    Toast.makeText(context, "این تراکنش محافظت‌شده است یا حذف آن انجام نشد",
                                        Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    }
                )
                Tab.DEBTS -> DebtsPage(debts, txs, chosenPeriod, nowEpoch,
                    onEdit = { editor = Editor.DebtForm(it) },
                    onPay = { editor = Editor.Repay(it) },
                    onEditPayment = { editor = Editor.DebtPaymentForm(it) },
                    onDelete = { row -> editor = Editor.Delete("این بدهی یا طلب حذف شود؟") { vm.deleteDebt(row.id) } }
                )
                Tab.GOALS -> SavingsGoalsScreen(
                    goals = goals, movements = goalMovements, accounts = accounts,
                    nowEpoch = nowEpoch, dark = darkMode,
                    onAdd = { editor = Editor.GoalForm() },
                    onAddFund = { editor = Editor.GoalForm(defaultCategory = "FUND") },
                    onEdit = { editor = Editor.GoalForm(it) },
                    onDeposit = { editor = Editor.GoalMoney(it, "DEPOSIT") },
                    onWithdraw = { editor = Editor.GoalMoney(it, "WITHDRAW") },
                    onArchive = { goal ->
                        vm.setGoalArchive(goal.id, !goal.archived) { ok ->
                            Toast.makeText(context, if (ok) {
                                if (goal.archived) "هدف از آرشیو بازگردانده شد" else "هدف بایگانی شد"
                            } else "برای بایگانی اول موجودی هدف را به حساب برگردان",
                                Toast.LENGTH_LONG).show()
                        }
                    },
                    onDelete = { goal ->
                        editor = Editor.Delete("هدف «${goal.title}» حذف شود؟") {
                            vm.deleteEmptyGoal(goal.id) { ok ->
                                Toast.makeText(context, if (ok) "هدف حذف شد"
                                else "هدفِ دارای سابقه مالی قابل حذف نیست؛ بایگانی‌اش کن",
                                    Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
            }
            Spacer(Modifier.height(86.dp))
        }
    }
    }
    }

    if (restorePreview != null) {
        val candidate = restorePreview!!
        AlertDialog(
            onDismissRequest = { if (!backupBusy) restorePreview = null },
            title = { Text("بازیابی اطلاعات جسی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("فایل معتبر است و این موارد را دارد:", fontWeight = FontWeight.Bold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                    Text(candidate.summary(), fontSize = 12.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                    Text("توجه: با تأیید، تمام اطلاعات مالی فعلی با محتویات این فایل جایگزین می‌شود. " +
                        "بازیابی با داده‌های فعلی ادغام نمی‌شود.",
                        color = jessiReadableText(danger), fontSize = 12.sp)
                    Text("بهتر است پیش از بازیابی، از وضعیت فعلی پشتیبان بگیری.",
                        color = jessiReadableText(muted), fontSize = 11.sp)
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
                                FinanceReminderScheduler.checkSoon(context)
                                JessiWidgetUpdater.request(context)
                                Toast.makeText(context, "بازیابی اطلاعات موفق بود", Toast.LENGTH_LONG).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "بازیابی انجام نشد؛ اطلاعات قبلی حفظ شدند: " +
                                    (e.message ?: "خطای نامشخص"), Toast.LENGTH_LONG).show()
                            } finally { backupBusy = false }
                        }
                    }
                }, enabled = !backupBusy) { Text("جایگزینی اطلاعات", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
            },
            dismissButton = {
                TextButton(onClick = { restorePreview = null }, enabled = !backupBusy) {
                    Text("انصراف", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                }
            }
        )
    }

    when (val e = editor) {
        is Editor.AccountForm -> AccountDialog(e.value, dismiss = { editor = null }) { vm.saveAccount(it); editor = null }
        is Editor.TxForm -> TransactionDialog(
            initial = e.value, accounts = accounts, debts = debts,
            dismiss = { editor = null },
            onSave = { vm.saveTransaction(it); editor = null },
            onTransfer = { a, b, amount, note ->
                vm.transfer(a,b,amount,note); editor = null
            },
            onCollect = { debtId, accountId, amount, note, existingTxId ->
                vm.collectReceivable(debtId, accountId, amount, note, existingTxId) { ok ->
                    Toast.makeText(context,
                        if (ok) "وصول ثبت شد؛ مانده طلب و حساب با هم به‌روز شدند"
                        else "وصول ثبت نشد؛ مبلغ و طلب انتخاب‌شده را بررسی کن",
                        Toast.LENGTH_LONG).show()
                    if (ok) {
                        editor = null
                        tabName = Tab.DEBTS.name
                        showSettings = false
                    }
                }
            }
        )
        is Editor.DebtForm -> DebtDialog(e.value, dismiss = { editor = null }) { vm.saveDebt(it); editor = null }
        is Editor.BudgetForm -> BudgetDialog(e.value, budgets, dismiss = { editor = null }) { vm.saveBudget(it); editor = null }
        is Editor.GoalForm -> SavingsGoalFormDialog(
            initial = e.value,
            defaultCategory = e.defaultCategory,
            onDismiss = { editor = null },
            onSave = { goal ->
                vm.saveGoal(goal) { ok ->
                    if (ok) {
                        editor = null
                        tabName = Tab.GOALS.name
                        showSettings = false
                        Toast.makeText(context, "هدف پس‌انداز ذخیره شد", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "خطا در ذخیره هدف؛ مقادیر را بررسی کن",
                            Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
        is Editor.GoalMoney -> GoalMoneyDialog(
            goal = e.goal, operation = e.operation, accounts = accounts,
            transactions = txs,
            currentGoalBalance = SavingsGoalMath.saved(goalMovements, e.goal.id),
            onDismiss = { editor = null },
            onSubmit = { amount, accountId, finished ->
                vm.moveGoalMoney(e.goal.id, accountId, amount, e.operation) { ok ->
                    finished(ok)
                    Toast.makeText(context,
                        if (ok) "جابجایی با موفقیت ثبت شد؛ هزینه یا درآمد محسوب نمی‌شود"
                        else "واریز/برداشت انجام نشد؛ مانده‌ها را بررسی کن",
                        Toast.LENGTH_LONG).show()
                    if (ok) {
                        editor = null
                        tabName = Tab.GOALS.name
                        showSettings = false
                    }
                }
            }
        )
        is Editor.Repay -> RepayDialog(e.value, accounts, txs, dismiss = { editor = null }) { amount, accountId ->
            vm.settleDebt(e.value, amount, accountId) { ok ->
                if (ok) editor = null
                Toast.makeText(context, if (ok) "پرداخت ثبت شد و موجودی به‌روز شد" else "ثبت پرداخت انجام نشد؛ مبلغ و حساب را بررسی کن", Toast.LENGTH_LONG).show()
            }
        }
        is Editor.DebtPaymentForm -> DebtPaymentEditDialog(e.value, accounts, debts, txs,
            dismiss = { editor = null },
            onSave = { accountId, amount, note, allowUnresolved ->
                vm.editDebtPayment(e.value, accountId, amount, note, allowUnresolved) { ok ->
                    if (ok) editor = null
                    Toast.makeText(context, if (ok) "پرداخت و موجودی اصلاح شد" else "ویرایش انجام نشد؛ ارتباط بدهی و مبلغ را بررسی کن", Toast.LENGTH_LONG).show()
                }
            }
        )
        is Editor.Review -> ReviewDialog(
            e.value, accounts, debts,
            dismiss = { editor = null },
            onAccept = { id, category, note, unit, receivableDebtId ->
                vm.acceptReview(e.value, id, category, note, unit, receivableDebtId) { saved ->
                    if (saved) {
                        editor = null
                        showSettings = false
                        tabName = Tab.HOME.name
                        Toast.makeText(context, "تراکنش ثبت شد و در آمار اعمال شد", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "ثبت تراکنش انجام نشد؛ مبلغ، واحد و حساب را بررسی کن", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onReject = { vm.dismissReview(e.value.id); editor = null }
        )
        is Editor.Delete -> AlertDialog(onDismissRequest = { editor = null }, title = { Text("تأیید حذف", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }, text = { Text(e.title, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }, confirmButton = { TextButton(onClick = { e.action(); editor = null }) { Text("حذف", color = jessiReadableText(danger)) } }, dismissButton = { TextButton(onClick = { editor = null }) { Text("انصراف", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) } })
        null -> Unit
    }
}


@Composable
private fun SettingsPage(
    widgetBudgets: List<Budget>,
    darkMode: Boolean, onToggleTheme: () -> Unit,
    balanceVisible: Boolean, onBalanceVisibilityChange: (Boolean) -> Unit,
    widgetBalanceVisible: Boolean,
    onWidgetBalanceVisibilityChange: (Boolean) -> Unit,
    smsMonitoringEnabled: Boolean, onSmsMonitoringChange: (Boolean) -> Unit,
    reviewNotificationsEnabled: Boolean, onReviewNotificationsChange: (Boolean) -> Unit,
    onRenameCategory: (kind: String, old: String, name: String,
        callback: (Boolean, String?) -> Unit) -> Unit,
    budgetRemindersEnabled: Boolean, installmentRemindersEnabled: Boolean,
    onBudgetRemindersChange: (Boolean) -> Unit,
    onInstallmentRemindersChange: (Boolean) -> Unit,
    periodMode: String, onPeriodChange: (String) -> Unit,
    smsPermitted: Boolean, notificationsPermitted: Boolean,
    onSmsPermission: () -> Unit, onNotificationPermission: () -> Unit,
    onSystemSettings: () -> Unit,
    diagnosticSummary: String, notificationBlocker: String?, pendingReviews: Int,
    onTestNotification: () -> Unit,
    dueReminderDiagnostic: String,
    onTestDueReminder: () -> Unit,
    onResendPending: () -> Unit,
    onNotificationSettings: () -> Unit,
    onRefreshDiagnostics: () -> Unit,
    onSimulateSms: (String, String) -> Unit,
    onExportBackup: () -> Unit, onImportBackup: () -> Unit, backupBusy: Boolean,
    backgroundStyle: String, backgroundPhotoUri: String, backgroundDim: Float,
    backgroundDarkness: Float,
    onBackgroundStyle: (String) -> Unit, onPickBackground: () -> Unit,
    onClearBackground: () -> Unit, onBackgroundDim: (Float) -> Unit,
    onBackgroundDarkness: (Float) -> Unit,
    pinSecurity: PinSecurity, onSecurityChanged: () -> Unit,
    versionName: String
) {
    var section by remember { mutableStateOf("home") }
    val titles = mapOf(
        "appearance" to "ظاهر و پس‌زمینه",
        "widgets" to "تنظیمات ویجت‌ها",
        "categories" to "دسته‌بندی‌های درآمد و هزینه",
        "security" to "قفل ورود و امنیت",
        "sms" to "پیامک و اعلان‌ها",
        "diagnostics" to "آزمایش و عیب‌یابی",
        "backup" to "پشتیبان‌گیری",
        "period" to "دوره مالی",
        "about" to "درباره جسی"
    )
    BackHandler(enabled = section != "home") { section = "home" }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (section != "home") {
                IconButton(onClick = { section = "home" }, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.ArrowForward, contentDescription = "بازگشت به تنظیمات")
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(if (section == "home") "تنظیمات" else titles[section].orEmpty(),
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Text(if (section == "home") "هر موضوع در صفحه جداگانه و مرتب"
                     else "جسی • تنظیمات", fontSize = 11.sp, color = jessiReadableText(muted))
            }
        }
        if (section == "home") {
            SettingsNavRow(Icons.Outlined.Palette, "ظاهر و پس‌زمینه",
                "تم، تصویر دلخواه، شدت هاله شیشه‌ای", financeAqua) { section = "appearance" }
            SettingsNavRow(Icons.Outlined.Widgets, "تنظیمات ویجت‌ها",
                "نمایش موجودی و انتخاب پنج بودجه برای هوم‌اسکرین",
                financeBlue) { section = "widgets" }
            SettingsNavRow(Icons.Outlined.Category, "دسته‌بندی‌های درآمد و هزینه",
                "افزودن، تغییر نام یا حذف دسته‌ها", financePurple) {
                section = "categories"
            }
            SettingsNavRow(Icons.Outlined.Lock, "قفل ورود و امنیت",
                "اثر انگشت و پین ورود", financePurple) { section = "security" }
            SettingsNavRow(Icons.Outlined.Sms, "پیامک و اعلان‌ها",
                "اعلان پیامک، هشدار بودجه و سررسید اقساط", financeBlue) { section = "sms" }
            SettingsNavRow(Icons.Outlined.BugReport, "آزمایش و عیب‌یابی",
                "اعلان آزمایشی و تست متن پیامک", financePurple) { section = "diagnostics" }
            SettingsNavRow(Icons.Outlined.SaveAlt, "پشتیبان‌گیری",
                "خروجی و بازیابی اطلاعات مالی", financeAqua) { section = "backup" }
            SettingsNavRow(Icons.Outlined.CalendarMonth, "دوره مالی",
                "انتخاب ماه شمسی یا دوره حقوق", financeAmber) { section = "period" }
            SettingsNavRow(Icons.Outlined.Info, "درباره جسی",
                "نسخه و اطلاعات نگهداری داده", financeBlue) { section = "about" }
        }
        if (section == "categories") {
            Tile(Modifier.fillMaxWidth(), accent = financePurple) {
                FinanceCategorySettings(onRename = onRenameCategory)
            }
        }
        if (section == "widgets") {
            Tile(Modifier.fillMaxWidth(), accent = financeBlue) {
                JessiWidgetSettings(
                    budgets = widgetBudgets,
                    showBalance = widgetBalanceVisible,
                    onShowBalance = onWidgetBalanceVisibilityChange
                )
            }
        }

        if (section == "appearance") {
        Tile(Modifier.fillMaxWidth(), accent = financeAqua) {
            SettingsGroupTitle(Icons.Outlined.Palette, "ظاهر و حریم خصوصی", accent = financeAqua)
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

            Tile(Modifier.fillMaxWidth(), accent = financeAqua) {
                JessiBackgroundSettings(
                    style = backgroundStyle, photoUri = backgroundPhotoUri,
                    dim = backgroundDim, dark = darkMode,
                    darkness = backgroundDarkness,
                    onStyleChange = onBackgroundStyle,
                    onChooseImage = onPickBackground,
                    onClearImage = onClearBackground,
                    onDimChange = onBackgroundDim,
                    onDarknessChange = onBackgroundDarkness
                )
            }
        }

        if (section == "sms") {
        Tile(Modifier.fillMaxWidth(), accent = financePurple) {
            SettingsGroupTitle(Icons.Outlined.NotificationsActive,
                "هشدارهای مالی", accent = financePurple)
            SettingsToggle(
                icon = Icons.Outlined.Savings,
                title = "هشدار ۹۰٪ بودجه",
                description = "برای هر دسته‌بندی، یک‌بار در هر دوره مالی",
                checked = budgetRemindersEnabled, onChange = onBudgetRemindersChange
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .35f))
            SettingsToggle(
                icon = Icons.Outlined.CalendarMonth,
                title = "یادآوری سررسید اقساط",
                description = "در روز سررسید هر قسط پرداخت‌نشده",
                checked = installmentRemindersEnabled,
                onChange = onInstallmentRemindersChange
            )
            OutlinedButton(
                onClick = onTestDueReminder,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.NotificationsActive,
                    contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("تست و بررسی یادآور اقساط")
            }
            if (dueReminderDiagnostic.isNotBlank()) {
                Text(
                    dueReminderDiagnostic,
                    fontSize = 11.sp,
                    color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant)
                )
            }
            Text(
                "اگر اندروید بررسی پس‌زمینه را عقب بیندازد، جسی سررسیدهای " +
                    "پرداخت‌نشده تا ۳ روز قبل را هم یک‌بار یادآوری می‌کند.",
                fontSize = 10.sp, color = jessiReadableText(muted)
            )
            Text(
                "جسی در پس‌زمینه بررسی می‌کند؛ ممکن است به‌خاطر مدیریت باتری اندروید، " +
                    "اعلان سررسید با کمی تأخیر برسد.",
                fontSize = 10.sp, color = jessiReadableText(muted)
            )
        }
        Tile(Modifier.fillMaxWidth(), accent = financeBlue) {
            SettingsGroupTitle(Icons.Outlined.Sms, "پیامک بانکی و اعلان‌ها", accent = financeBlue)
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
                    Text("دسترسی SMS", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                    Text(if (smsPermitted) "اجازه داده شده" else "اجازه داده نشده",
                        fontSize = 11.sp, color = jessiReadableText(if (smsPermitted) mint else danger))
                }
                if (!smsPermitted) TextButton(onClick = onSmsPermission) { Text("درخواست مجوز", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
                else Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = mint)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("دسترسی اعلان‌ها", fontWeight = FontWeight.Medium, fontSize = 13.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                    Text(if (notificationsPermitted) "اجازه داده شده" else "اجازه داده نشده",
                        fontSize = 11.sp, color = jessiReadableText(if (notificationsPermitted) mint else danger))
                }
                if (!notificationsPermitted) TextButton(onClick = onNotificationPermission) { Text("درخواست مجوز", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
                else Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = mint)
            }
            OutlinedButton(onClick = onSystemSettings, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.OpenInNew, contentDescription = null,
                    modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text("مدیریت مجوزها در تنظیمات اندروید", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
        }

        }

        if (section == "diagnostics") {
        Tile(Modifier.fillMaxWidth(), accent = financePurple) {
            SettingsGroupTitle(Icons.Outlined.BugReport, "عیب‌یابی دریافت پیامک", accent = financePurple)
            Text("وضعیت اعلان: " + (notificationBlocker ?: "مجوز و کانال اعلان فعال است"),
                fontSize = 12.sp, color = jessiReadableText(if (notificationBlocker == null) mint else danger))
            Text("وضعیت آخرین پیامک در جسی:", fontWeight = FontWeight.Medium, fontSize = 12.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            Text(diagnosticSummary, fontSize = 11.sp, color = jessiReadableText(muted), lineHeight = 17.sp)
            Text("پیامک‌های منتظر بررسی: " + digitsFa(pendingReviews.toString()),
                fontSize = 12.sp, color = jessiReadableText(muted))
            OutlinedButton(onClick = onTestNotification, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ارسال اعلان آزمایشی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
            OutlinedButton(
                onClick = onResendPending, enabled = pendingReviews > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("اعلان دوباره برای پیامک‌های منتظر", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
            TextButton(onClick = onNotificationSettings) {
                Text("تنظیمات کانال اعلان پیامک در اندروید", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
            TextButton(onClick = onRefreshDiagnostics) { Text("به‌روزرسانی وضعیت", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
            Text("این عیب‌یابی متن پیامک، شماره حساب یا مبلغ رو ذخیره نمی‌کنه.",
                color = jessiReadableText(muted), fontSize = 10.sp)
        }

        SmsParserTestSettings(onSimulateSms)

        }

        if (section == "backup") {
        Tile(Modifier.fillMaxWidth(), accent = financeAqua) {
            SettingsGroupTitle(Icons.Outlined.SaveAlt, "پشتیبان‌گیری و بازیابی اطلاعات", accent = financeAqua)
            Text(
                "از حساب‌ها، تراکنش‌ها، بدهی‌ها، اقساط، بودجه‌ها و صف پیامک‌ها یک فایل JSON بگیر.",
                fontSize = 12.sp, color = jessiReadableText(muted))
            OutlinedButton(
                onClick = onExportBackup, enabled = !backupBusy, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ذخیره فایل پشتیبان", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
            OutlinedButton(
                onClick = onImportBackup, enabled = !backupBusy, modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("انتخاب فایل و بازیابی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
            if (backupBusy) CircularProgressIndicator(modifier = Modifier.size(23.dp))
            Text(
                "فایل پشتیبان رمزگذاری نشده و اطلاعات مالی حساس دارد. در جای امن نگهداری کن. " +
                "بازیابی فقط بعد از بررسی فایل و تأیید تو انجام می‌شود.",
                fontSize = 11.sp, color = jessiReadableText(danger))
        }

        }

        if (section == "period") {
        Tile(Modifier.fillMaxWidth(), accent = financeAmber) {
            SettingsGroupTitle(Icons.Outlined.CalendarMonth, "دوره مالی", accent = financeAmber)
            Text("شروع دوره جدید و محاسبه سقف هزینه‌ها",
                fontSize = 12.sp, color = jessiReadableText(muted))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                FilterChip(
                    selected = periodMode == FinancePeriodEngine.MONTHLY,
                    onClick = { onPeriodChange(FinancePeriodEngine.MONTHLY) },
                    label = { Text("اول هر ماه شمسی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
                )
                FilterChip(
                    selected = periodMode == FinancePeriodEngine.SALARY,
                    onClick = { onPeriodChange(FinancePeriodEngine.SALARY) },
                    label = { Text("پس از تأیید حقوق", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
                )
            }
            Text("تعویض دوره هیچ تراکنش یا بدهی را پاک نمی‌کند.",
                fontSize = 11.sp, color = jessiReadableText(muted))
        }

        }

        if (section == "about") {
        Tile(Modifier.fillMaxWidth(), accent = financeBlue) {
            SettingsGroupTitle(Icons.Outlined.Info, "درباره جسی", accent = financeBlue)
            Text("نسخه " + digitsFa(versionName),
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            Text("اطلاعات حساب‌ها و تراکنش‌ها در حافظه همین گوشی نگهداری می‌شوند. متن کامل پیامک بانکی ذخیره نمی‌شود.",
                fontSize = 12.sp, color = jessiReadableText(muted))
            Text("هنگام حذف برنامه، ممکن است داده‌های ذخیره‌شده حذف شوند؛ بدون پشتیبان برنامه را پاک نکن.",
                fontSize = 11.sp, color = jessiReadableText(danger))
        }
        }
        if (section == "security") {
            Tile(Modifier.fillMaxWidth(), accent = financePurple) {
                JessiSecuritySettings(pinSecurity, onSecurityChanged)
            }
        }
        Text(
            text = "Powerd by PR",
            fontSize = 10.sp,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .72f),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp),
            textAlign = TextAlign.Center
        )
    }
}


@Composable
private fun SettingsNavRow(
    icon: ImageVector, title: String, description: String,
    accent: Color, onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .57f))
            .border(1.dp, accent.copy(alpha = .24f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        FinanceIconTile(icon, accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            Text(description, fontSize = 11.sp, color = jessiReadableText(muted))
        }
        Icon(Icons.Outlined.ChevronLeft, contentDescription = null,
            tint = accent, modifier = Modifier.size(20.dp))
    }
}


@Composable
private fun SmsParserTestSettings(onSimulateSms: (String, String) -> Unit) {
    var body by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var tested by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BankSmsParser.Result?>(null) }
    Tile(Modifier.fillMaxWidth(), accent = financeBlue) {
        SettingsGroupTitle(Icons.Outlined.Science, "آزمایش متن پیامک بانکی", accent = financeBlue)
        Text("متن پیامک رو از برنامه پیام‌ها کپی کن و اینجا بچسبون؛ بدون دریافت SMS واقعی هم تشخیص آزمایش می‌شه.",
            fontSize = 12.sp, color = jessiReadableText(muted))
        OutlinedTextField(
            value = sender,
            onValueChange = { sender = it; tested = false },
            label = { Text("فرستنده (اختیاری)", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = body,
            onValueChange = { body = it; tested = false },
            label = { Text("متن پیامک", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
            minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = { result = BankSmsParser.parse(body, sender); tested = true },
            enabled = body.isNotBlank(), modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text("تست تشخیص پیامک", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
        }
        if (tested) {
            val found = result
            if (found == null) {
                Text("این متن با قواعد فعلی، تراکنش بانکی تشخیص داده نشد.",
                    color = jessiReadableText(danger), fontSize = 12.sp)
                Text("برای اصلاح الگوریتم، همین نمونه رو به جسی بفرست.",
                    color = jessiReadableText(muted), fontSize = 11.sp)
            } else {
                Text("پیامک شناسایی شد", color = jessiReadableText(mint), fontWeight = FontWeight.Bold)
                Text("بانک: " + found.bankName.ifBlank { "نامشخص" }, fontSize = 12.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Text("نوع: " + if (found.kind == "IN") "واریز" else "برداشت",
                    fontSize = 12.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Text(
                    "مبلغ: " + if (found.amountUnit == BankSmsParser.UNKNOWN) {
                        digitsFa(NumberFormat.getIntegerInstance(Locale.US).format(found.amountRaw)) +
                            " (واحد نامشخص؛ هنگام بررسی باید انتخاب شود)"
                    } else toman(found.amountToman),
                    fontSize = 12.sp, fontWeight = FontWeight.Bold
                , color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                OutlinedButton(
                    onClick = { onSimulateSms(body, sender) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Send, contentDescription = null, modifier = Modifier.size(19.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ساخت نمونه و بازکردن فرم ثبت", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                }
                Text(
                    "نمونه به صف بررسی اضافه می‌شه؛ سپس می‌تونی با تأیید جداگانه، " +
                    "اون رو به‌عنوان «آزمایشی» در موجودی، بودجه و نمودارها هم ثبت کنی.",
                    fontSize = 11.sp, color = jessiReadableText(muted))
            }
        }
        Text("متن پیامک در فرم نگهداری دائمی نمی‌شود؛ تست مستقل از مجوز دریافت SMS است.",
            fontSize = 11.sp, color = jessiReadableText(muted))
    }
}

@Composable
private fun SettingsGroupTitle(icon: ImageVector, text: String, accent: Color = mint) {
    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Surface(shape = RoundedCornerShape(11.dp), color = accent.copy(alpha = .16f),
            border = BorderStroke(1.dp, accent.copy(alpha = .23f))) {
            Icon(icon, contentDescription = null, tint = accent,
                modifier = Modifier.padding(8.dp).size(21.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
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
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            Text(description, color = jessiReadableText(muted), fontSize = 10.sp, lineHeight = 15.sp)
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
            fontWeight = FontWeight.Bold, color = jessiReadableText(if (LocalJessiDark.current) color
                else if (income) Color(0xFF007D56) else Color(0xFFC82C56)))
        Text(if (hidden) "••••••" else toman(value), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            color = jessiReadableText(if (LocalJessiDark.current) color
                else if (income) Color(0xFF007D56) else Color(0xFFC82C56)),
            maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Text("دوره مالی", fontSize = 10.sp, color = jessiReadableText(muted))
                    Text(title, color = jessiReadableText(mint), fontWeight = FontWeight.Bold,
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
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                    Icon(Icons.Outlined.ExpandMore, contentDescription = null,
                        tint = muted, modifier = Modifier.size(19.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text("ماه شمسی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
                    onClick = { onModeChange(FinancePeriodEngine.MONTHLY); expanded = false }
                )
                DropdownMenuItem(
                    text = { Text("از هر حقوق جدید", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
                    onClick = { onModeChange(FinancePeriodEngine.SALARY); expanded = false }
                )
            }
        }
    }
    if (mode == FinancePeriodEngine.SALARY && !salaryAvailable) {
        Text("بعد از تأیید اولین واریز حقوق، دوره‌های حقوقی فعال می‌شن.",
            fontSize = 11.sp, color = jessiReadableText(muted))
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
        if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = jessiReadableText(muted))
    }
}

@Composable
private fun Tile(
    modifier: Modifier = Modifier,
    accent: Color = Color.Unspecified,
    content: @Composable ColumnScope.() -> Unit
) {
    // Compact C5 Dark Aurora: restrained glow, lighter elevation and tighter content rhythm.
    // Light mode uses a gentle Aurora variation to preserve text contrast.
    val dark = LocalJessiDark.current
    val tint = if (accent == Color.Unspecified) {
        if (dark) financePurple else financeAqua
    } else accent
    val shape = RoundedCornerShape(18.dp)
    val base = if (dark) Color(0xE5110F20) else Color(0xCEF7FBFF)
    val aurora = if (dark) Brush.linearGradient(listOf(
        Color(0xEF2A1747), Color(0xEA132B4C),
        Color(0xEB3A1744), Color(0xF1131027)
    )) else Brush.linearGradient(listOf(
        Color(0xCFF1EAFB), Color(0xD5F1FAFF), Color(0xCDECF9F6), Color(0xCFFDFBFF)
    ))
    val glow = Brush.radialGradient(listOf(
        if (dark) Color(0x543E62F4) else tint.copy(alpha = .095f),
        if (dark) Color(0x23FF6FCA) else Color.Transparent,
        Color.Transparent
    ), radius = 560f)
    Surface(
        modifier = modifier, color = base, shape = shape,
        contentColor = if (dark) Color.White else MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp,
            if (dark) tint.copy(alpha = .35f) else tint.copy(alpha = .19f)),
        shadowElevation = if (dark) 4.dp else 2.dp
    ) {
        Column(
            Modifier.background(aurora).background(glow)
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
            content = content
        )
    }
}

@Composable
private fun Tag(text: String, good: Boolean = false) {
    Surface(
        color = if (good) mint.copy(alpha = .13f) else secondary,
        shape = RoundedCornerShape(30.dp)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = jessiReadableText(if (good) mint else muted), fontSize = 10.sp)
    }
}

@Composable
private fun MoneyLine(label: String, value: Long, color: Color = Color.Unspecified, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = jessiReadableText(muted), fontSize = 12.sp)
        Text(toman(value), color = jessiReadableText(if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color), fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun isThisMonth(epoch: Long): Boolean = Jalali.of(epoch).monthKey == Jalali.of(System.currentTimeMillis()).monthKey

@Composable
private fun DashboardCompactMetric(
    label: String, value: String, icon: ImageVector, tone: Color,
    modifier: Modifier = Modifier
) {
    val dark = LocalJessiDark.current
    val shape = RoundedCornerShape(13.dp)
    Column(
        modifier.clip(shape).background(Brush.linearGradient(
            listOf(tone.copy(alpha = if (dark) .25f else .13f),
                if (dark) Color(0xFF201539) else Color.White.copy(alpha = .75f))
        )).border(1.dp, tone.copy(alpha = if (dark) .34f else .20f), shape)
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = tone,
                modifier = Modifier.size(18.dp))
            Text(label, fontSize = 10.sp, color = jessiReadableText(muted), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
    }
}

@Composable
private fun Dashboard(
    accounts: List<Account>, txs: List<Transaction>, debts: List<Debt>, budgets: List<Budget>, reviews: List<SmsReview>,
    goals: List<SavingsGoal>, goalMovements: List<GoalMovement>,
    currentPeriod: FinancePeriod, periods: List<FinancePeriod>, selectedPeriodIndex: Int, nowEpoch: Long,
    smsPermitted: Boolean, requestPermission: () -> Unit,
    notificationsPermitted: Boolean, requestNotificationPermission: () -> Unit,
    onReview: (SmsReview) -> Unit, onSelectTab: (Tab) -> Unit,
    balanceVisible: Boolean, onBalanceVisibilityChange: (Boolean) -> Unit,
    smsMonitoringEnabled: Boolean, reviewNotificationsEnabled: Boolean,
    onOpenSettings: () -> Unit
) {
    val dark = LocalJessiDark.current
    val total = accounts.sumOf { accountBalance(it.id, it.openingToman, txs) }
    val current = txs.filter { currentPeriod.contains(it.createdAt) }
    val income = current.filter { it.kind == "IN" }.sumOf { it.amountToman }
    val expense = current.filter { it.kind == "OUT" }.sumOf { it.amountToman }
    // Next Jalali month's actual payment obligations: scheduled installments and
    // ordinary dated debts, regardless of their name or note. Exclude claims and
    // undated or fully-settled records. Do not include the total lifetime balance.
    val nextMonthDue = UpcomingDebtPlanner.nextMonth(debts, nowEpoch)
    val heroGradient = if (dark)
        Brush.linearGradient(listOf(Color(0xF42B174A), Color(0xEE142B53), Color(0xED461A46)))
    else Brush.linearGradient(listOf(Color(0xE9F3ECFB), Color(0xE6EBF7FF), Color(0xE6E8F8F2)))
    val heroShape = RoundedCornerShape(19.dp)

    // Dashboard follows the reference top-to-bottom, using real account and period data.
    Column(
        Modifier.fillMaxWidth().clip(heroShape).background(heroGradient)
            .border(BorderStroke(1.dp,
                if (dark) Color(0xFFB483F7).copy(alpha = .56f)
                else Color(0xFFC7DCEC)), heroShape)
            .padding(11.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (dark) Color(0xFF59318C) else Color(0xFFD9F5E5),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null,
                    modifier = Modifier.padding(8.dp).size(20.dp), tint = financeAqua)
            }
            Spacer(Modifier.width(9.dp))
            Text("موجودی کل حساب‌ها", modifier = Modifier.weight(1f),
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
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
                fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                color = jessiReadableText(MaterialTheme.colorScheme.onSurface), maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(6.dp))
            Text("تومان", fontSize = 12.sp, color = jessiReadableText(muted))
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

    val savedForGoals = goals.sumOf { SavingsGoalMath.saved(goalMovements, it.id) }
    Tile(Modifier.fillMaxWidth(), accent = financePurple) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FinanceIconTile(Icons.Filled.Savings, financePurple)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("بانک اهداف", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Text(
                    if (balanceVisible) toman(savedForGoals) else "••••••",
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = jessiReadableText(financePurple),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { onSelectTab(Tab.GOALS) }) {
                Text("اهداف", fontSize = 11.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Icon(Icons.Outlined.ChevronLeft, contentDescription = null,
                    modifier = Modifier.size(16.dp))
            }
        }
        Text("پس‌انداز کنارگذاشته‌شده • جدا از موجودی حساب‌ها",
            color = jessiReadableText(muted), fontSize = 10.sp)
    }

    val jalaliMonthNames = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")
    val monthCaption = jalaliMonthNames[nextMonthDue.jalaliMonth.month - 1] +
        " " + digitsFa(nextMonthDue.jalaliMonth.year.toString())

    // Dashboard debt panel is intentionally scoped ONLY to the next Jalali month.
    Tile(Modifier.fillMaxWidth(), accent = financePurple) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceIconTile(Icons.Filled.EventNote, financePurple)
            Column(Modifier.weight(1f)) {
                Text("اقساط و بدهی‌های ماه آینده", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Text("سررسیدهای " + monthCaption, fontSize = 11.sp, color = jessiReadableText(muted))
            }
            TextButton(onClick = { onSelectTab(Tab.DEBTS) },
                contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("جزئیات", fontSize = 11.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Icon(Icons.Outlined.ChevronLeft, contentDescription = null,
                    modifier = Modifier.size(17.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            DashboardCompactMetric(
                label = "اقساط ماه آینده",
                value = if (balanceVisible) toman(nextMonthDue.installmentsToman) else "••••••",
                icon = Icons.Outlined.CalendarMonth,
                tone = financePurple,
                modifier = Modifier.weight(1f)
            )
            DashboardCompactMetric(
                label = "بدهی‌های سررسیددار",
                value = if (balanceVisible) toman(nextMonthDue.ordinaryDebtsToman) else "••••••",
                icon = Icons.Outlined.ReceiptLong,
                tone = financeAqua,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(financePurple.copy(alpha = if (dark) .16f else .08f))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null,
                tint = financePurple, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f)) {
                Text("جمع پرداخت‌های ماه آینده", color = jessiReadableText(muted), fontSize = 11.sp)
                Text(if (balanceVisible) toman(nextMonthDue.totalToman) else "••••••",
                    color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Tag(digitsFa(nextMonthDue.payments.size.toString()) + " سررسید",
                nextMonthDue.payments.isNotEmpty())
        }
        if (nextMonthDue.payments.isEmpty()) {
            Text("برای ماه آینده هیچ قسط یا بدهی سررسیدداری ثبت نشده ✦",
                fontSize = 11.sp, color = jessiReadableText(muted))
        } else {
            Text("سررسیدهای ماه آینده", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            nextMonthDue.payments.take(5).forEach { item ->
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        if (item.isInstallment) Icons.Outlined.CalendarToday
                            else Icons.Outlined.EventAvailable,
                        contentDescription = null,
                        tint = if (item.isInstallment) financePurple else financeAqua,
                        modifier = Modifier.size(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.debt.person, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                        Text((if (item.isInstallment) "قسط" else "بدهی") + " • " +
                            JalaliDates.format(item.dueDate), color = jessiReadableText(muted), fontSize = 10.sp)
                    }
                    Text(if (balanceVisible) toman(item.amountToman) else "••••••",
                        fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                }
            }
            if (nextMonthDue.payments.size > 5) {
                Text("و " + digitsFa((nextMonthDue.payments.size - 5).toString()) +
                    " سررسید دیگر در بخش اقساط و بدهی‌ها",
                    fontSize = 10.sp, color = jessiReadableText(muted))
            }
        }
        Text("محاسبه بر اساس تاریخ سررسید و مبلغ پرداخت‌نشده است؛ عنوان «قسط» شرط نیست.",
            fontSize = 10.sp, color = jessiReadableText(muted))
    }

    // Keep the full prior features, but below the photographed dashboard layout.
    if (reviews.isNotEmpty()) {
        Tile(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Markunread, tint = mint, contentDescription = null)
                Spacer(Modifier.width(7.dp))
                Text("پیامک‌های منتظر بررسی", modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Bold, fontSize = 14.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Tag(digitsFa(reviews.size.toString()), true)
            }
            reviews.take(5).forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (row.kind == "IN") "واریز احتمالی" else "برداشت احتمالی",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                        Text("${reviewAmountLabel(row)} • ${row.sender}", fontSize = 11.sp, color = jessiReadableText(muted))
                    }
                    TextButton(onClick = { onReview(row) }) { Text("بررسی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
                }
            }
        }
    }
    if (budgets.isNotEmpty()) {
        Tile(Modifier.fillMaxWidth()) {
            Text("بودجه‌های این دوره", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            budgets.take(4).forEach { budget ->
                val spent = current.filter { it.kind == "OUT" && it.category == budget.category }
                    .sumOf { it.amountToman }
                val fraction = (spent.toFloat() / budget.monthlyLimitToman.coerceAtLeast(1)).coerceIn(0f, 1f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(budget.category, color = jessiReadableText(muted), fontSize = 12.sp)
                    Text(digitsFa((fraction * 100).toInt().toString()) + "٪",
                        color = jessiReadableText(if (spent > budget.monthlyLimitToman) danger else mint), fontSize = 12.sp)
                }
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(),
                    color = mint, trackColor = secondary)
            }
        }
    }
    if (!smsPermitted || !notificationsPermitted || !smsMonitoringEnabled || !reviewNotificationsEnabled) {
        Tile(Modifier.fillMaxWidth()) {
            Text("دسترسی‌های پیامک بانکی", fontWeight = FontWeight.Bold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            if (!smsMonitoringEnabled || !reviewNotificationsEnabled) {
                Text("بررسی پیامک یا اعلان‌ها از تنظیمات جسی غیرفعال شده.",
                    fontSize = 12.sp, color = jessiReadableText(muted))
                TextButton(onClick = onOpenSettings) { Text("تنظیمات پیامک و اعلان", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
            }
            if (!smsPermitted) {
                Text("برای شناسایی پیامک‌های بانکی جدید، مجوز SMS را فعال کن.", fontSize = 12.sp, color = jessiReadableText(muted))
                TextButton(onClick = requestPermission) { Text("فعال‌کردن SMS", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
            }
            if (!notificationsPermitted) {
                Text("برای اعلان بررسی تراکنش، مجوز اعلان را فعال کن.", fontSize = 12.sp, color = jessiReadableText(muted))
                TextButton(onClick = requestNotificationPermission) { Text("فعال‌کردن اعلان", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
            }
        }
    }
    // Keep recent transactions at the very bottom of the dashboard.
    Tile(Modifier.fillMaxWidth(), accent = financeAqua) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.History, contentDescription = null, tint = financeAqua,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("تراکنش‌های اخیر", modifier = Modifier.weight(1f),
                fontWeight = FontWeight.Bold, fontSize = 15.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            TextButton(onClick = { onSelectTab(Tab.TXS) }, contentPadding = PaddingValues(4.dp)) {
                Text("مشاهده همه", fontSize = 11.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Icon(Icons.Outlined.ChevronLeft, contentDescription = null,
                    modifier = Modifier.size(16.dp))
            }
        }
        val recent = current.filter { it.kind != "TRANSFER_IN" }.take(4)
        if (recent.isEmpty()) {
            Text("تراکنشی در این دوره ثبت نشده.", color = jessiReadableText(muted), fontSize = 12.sp)
        } else {
            recent.forEachIndexed { i, tx ->
                if (i > 0) HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = .4f))
                DashboardRecentTransaction(tx, accounts, txs)
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
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            Text((accounts.firstOrNull { it.id == row.accountId }?.title ?: "حساب نامشخص") +
                " • " + Jalali.of(row.createdAt), color = jessiReadableText(muted), fontSize = 10.sp)
        }
        Text((if (incoming) "+" else "−") + digitsFa(
            NumberFormat.getIntegerInstance(Locale.US).format(row.amountToman)
        ), color = jessiReadableText(if (incoming) mint else danger),
            fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyNote(message: String) {
    Tile(Modifier.fillMaxWidth()) { Text(message, fontSize = 13.sp, color = jessiReadableText(muted)) }
}

@Composable
private fun FinanceSectionHero(
    title: String, description: String, icon: ImageVector,
    mainStat: String, statLabel: String, accent: Color = Color.Unspecified
) {
    val dark = LocalJessiDark.current
    val highlight = if (accent == Color.Unspecified) mint else accent
    val shape = RoundedCornerShape(15.dp)
    val gradient = if (dark) Brush.linearGradient(
        listOf(
            highlight.copy(alpha = .25f), Color(0xE32D184B),
            Color(0xDC173356), Color(0xE536173C)
        )
    ) else Brush.linearGradient(
        listOf(
            highlight.copy(alpha = .12f), Color(0xDFF4F0FD), Color(0xE5ECF8FD)
        )
    )
    Column(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (dark) Color(0xB711172E) else Color(0xCFF9FBFF))
            .background(gradient)
            .border(1.dp, highlight.copy(alpha = if (dark) .49f else .24f), shape)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(
                color = highlight.copy(alpha = if (dark) .21f else .12f),
                shape = RoundedCornerShape(11.dp)
            ) {
                Icon(icon, contentDescription = null, tint = highlight,
                    modifier = Modifier.padding(6.dp).size(18.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Text(description, color = jessiReadableText(muted), fontSize = 10.sp, lineHeight = 14.sp)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mainStat, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Text(statLabel, fontSize = 10.sp, color = jessiReadableText(muted), modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

@Composable
private fun FinanceIconTile(icon: ImageVector, tint: Color = mint) {
    val dark = LocalJessiDark.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier.size(38.dp).clip(shape)
            .background(Brush.linearGradient(listOf(
                tint.copy(alpha = if (dark) .29f else .17f),
                if (dark) Color(0xFF27234A) else Color(0xFFF7FAFF)
            )))
            .border(1.dp,
                if (dark) Color.White.copy(alpha = .20f)
                else tint.copy(alpha = .29f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FinanceActionRow(
    edit: (() -> Unit)?, delete: (() -> Unit)?, primary: (() -> Unit)? = null,
    primaryLabel: String = "ثبت پرداخت"
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        if (primary != null) {
            FilledTonalButton(onClick = primary,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = mint.copy(alpha = .15f), contentColor = mint
                )) {
                Text(primaryLabel, fontSize = 11.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
        }
        Spacer(Modifier.weight(1f))
        if (edit != null) {
            TextButton(onClick = edit) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("ویرایش", fontSize = 11.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
        }
        if (delete != null) {
            TextButton(onClick = delete) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null,
                    tint = danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text("حذف", color = jessiReadableText(danger), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun AccountsPage(
    accounts: List<Account>, txs: List<Transaction>,
    onEdit: (Account) -> Unit, onDelete: (Account) -> Unit
) {
    val total = accounts.sumOf { accountBalance(it.id, it.openingToman, txs) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("حساب‌های من", fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f))
        Text(toman(total), fontSize = 13.sp, fontWeight = FontWeight.Bold,
            color = jessiReadableText(financeAqua), maxLines = 1,
            overflow = TextOverflow.Ellipsis)
    }
    if (accounts.isEmpty()) EmptyNote("حسابی ثبت نشده. برای شروع روی + بزن.")
    accounts.forEach { acc ->
        val amount = accountBalance(acc.id, acc.openingToman, txs)
        val canDelete = txs.none { it.accountId == acc.id }
        val details = listOf(
            acc.bank, acc.last4.takeIf { it.isNotBlank() }?.let { "•••• " + digitsFa(it) }.orEmpty()
        ).filter { it.isNotBlank() }.joinToString(" • ")
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(15.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .87f),
            border = BorderStroke(1.dp, financeAqua.copy(alpha = .18f))
        ) {
            Row(
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = financeAqua.copy(alpha = .12f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null,
                        tint = financeAqua, modifier = Modifier.padding(8.dp).size(18.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(acc.title, modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                        Spacer(Modifier.width(6.dp))
                        Text(toman(amount), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = jessiReadableText(financeAqua))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(details, modifier = Modifier.weight(1f), maxLines = 1,
                            overflow = TextOverflow.Ellipsis, fontSize = 10.sp,
                            color = jessiReadableText(muted))
                        IconButton(
                            onClick = { onEdit(acc) }, modifier = Modifier.size(27.dp)
                        ) {
                            Icon(Icons.Outlined.Edit, contentDescription = "ویرایش " + acc.title,
                                modifier = Modifier.size(16.dp))
                        }
                        if (canDelete) {
                            IconButton(
                                onClick = { onDelete(acc) }, modifier = Modifier.size(27.dp)
                            ) {
                                Icon(Icons.Outlined.DeleteOutline,
                                    contentDescription = "حذف " + acc.title,
                                    tint = danger, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun TransactionsPage(
    txs: List<Transaction>, accounts: List<Account>, debts: List<Debt>,
    onAssign: (List<Long>, Long, (Boolean) -> Unit) -> Unit,
    onEdit: (Transaction) -> Unit, onDelete: (Transaction) -> Unit
) {
    var selecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showAssignment by remember { mutableStateOf(false) }
    var debtId by remember { mutableStateOf<Long?>(null) }
    var savingAssignment by remember { mutableStateOf(false) }
    var assignmentError by remember { mutableStateOf(false) }
    val selectedRows = txs.filter { it.id in selectedIds && it.kind == "OUT" && it.groupId == null }
    val selectedTotal = selectedRows.sumOf { it.amountToman }
    val availableDebts = debts.filter { it.direction == "I_OWE" && it.paymentPeriodRemaining() > 0L }
    val target = availableDebts.firstOrNull { it.id == debtId }
    BackHandler(enabled = selecting && !showAssignment) { selecting = false; selectedIds = emptySet() }
    if (showAssignment) AlertDialog(
        onDismissRequest = { if (!savingAssignment) showAssignment = false },
        title = { Text("ثبت پرداخت تجمیعی") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(digitsFa(selectedRows.size.toString()) + " برداشت • " + toman(selectedTotal))
                SelectItem("بدهی یا قسط", target, availableDebts,
                    { it.person + " • " + toman(it.paymentPeriodRemaining()) },
                    { debtId = it.id; assignmentError = false })
                target?.let {
                    if (it.installmentMonths > 0) Text(
                        "قسط بعدی: " + (Installments.nextDate(it)?.toString() ?: "") +
                            " • مانده این دوره: " + toman(it.paymentPeriodRemaining()))
                    if (selectedTotal > it.paymentPeriodRemaining())
                        Text("مجموع برداشت‌ها از مانده قابل پرداخت این دوره بیشتر است.",
                            color = MaterialTheme.colorScheme.error)
                }
                Text("برداشت‌ها با حساب و تاریخ اصلی حفظ می‌شوند و دوباره از موجودی کم نمی‌شوند.", fontSize = 11.sp)
                if (assignmentError) Text("ثبت انجام نشد؛ مبلغ یا وضعیت تراکنش‌ها تغییر کرده است.",
                    color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(enabled = !savingAssignment && target != null && selectedTotal > 0 &&
                selectedTotal <= target.paymentPeriodRemaining() && selectedRows.size == selectedIds.size,
                onClick = {
                    val id = debtId ?: return@Button
                    savingAssignment = true
                    onAssign(selectedRows.map { it.id }, id) { ok ->
                        savingAssignment = false
                        if (ok) {
                            showAssignment = false
                            selecting = false
                            selectedIds = emptySet()
                        } else assignmentError = true
                    }
                }) { Text("ثبت پرداخت") }
        },
        dismissButton = {
            TextButton(enabled = !savingAssignment, onClick = { showAssignment = false }) { Text("انصراف") }
        }
    )
    var search by rememberSaveable { mutableStateOf("") }
    var type by rememberSaveable { mutableStateOf(TransactionSearch.ALL) }
    var selectedAccountId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var accountMenu by remember { mutableStateOf(false) }
    var categoryMenu by remember { mutableStateOf(false) }
    val types = listOf(
        TransactionSearch.ALL to "همه",
        TransactionSearch.INCOME to "درآمد",
        TransactionSearch.EXPENSE to "هزینه",
        TransactionSearch.TRANSFER to "انتقال",
        TransactionSearch.DEBT to "اقساط",
        TransactionSearch.GOAL to "اهداف"
    )
    val categories = remember(txs, type) {
        txs.filter { row ->
            when (type) {
                TransactionSearch.INCOME -> row.kind == "IN"
                TransactionSearch.EXPENSE -> row.kind == "OUT"
                else -> row.kind != "TRANSFER_IN"
            }
        }.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }
    LaunchedEffect(accounts, categories) {
        if (selectedAccountId != null && accounts.none { it.id == selectedAccountId })
            selectedAccountId = null
        if (selectedCategory != null && selectedCategory !in categories)
            selectedCategory = null
    }
    val visible = remember(txs, accounts, search, type, selectedAccountId, selectedCategory) {
        TransactionSearch.filter(
            txs, accounts, search, type, selectedAccountId, selectedCategory
        )
    }
    val income = visible.filter { it.kind == "IN" }.sumOf { it.amountToman }
    val expense = visible.filter { it.kind == "OUT" }.sumOf { it.amountToman }
    var filtersExpanded by rememberSaveable { mutableStateOf(false) }
    // Compact screen toolbar replaces three large stacked hero/filter/summary cards.
    Row(
        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Outlined.ReceiptLong, contentDescription = null,
            tint = financeBlue, modifier = Modifier.size(21.dp))
        Text("تراکنش‌ها", fontSize = 15.sp, fontWeight = FontWeight.Bold,
            color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
            modifier = Modifier.weight(1f))
        Text(digitsFa(visible.size.toString()) + " تراکنش",
            fontSize = 10.sp, color = jessiReadableText(muted))
    }
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            modifier = Modifier.weight(1f).height(48.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
                Icon(Icons.Outlined.Search, contentDescription = null,
                    modifier = Modifier.size(17.dp))
            },
            trailingIcon = if (search.isNotEmpty()) ({
                IconButton(onClick = { search = "" }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Outlined.Close, contentDescription = "پاک‌کردن",
                        modifier = Modifier.size(16.dp))
                }
            }) else null,
            placeholder = { Text("جست‌وجوی تراکنش…", fontSize = 11.sp) }
        )
        FilledTonalIconButton(onClick = { filtersExpanded = !filtersExpanded },
            modifier = Modifier.size(42.dp)) {
            Icon(if (filtersExpanded) Icons.Outlined.FilterAltOff
                else Icons.Outlined.FilterAlt,
                contentDescription = if (filtersExpanded) "بستن فیلترها" else "بازکردن فیلترها",
                modifier = Modifier.size(20.dp))
        }
    }
    if (filtersExpanded) {
        Surface(
            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .85f),
            border = BorderStroke(1.dp, financeBlue.copy(alpha = .18f))
        ) {
            Column(Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    types.forEach { (code, label) ->
                        FilterChip(selected = type == code,
                            onClick = { type = code; selectedCategory = null },
                            label = { Text(label, fontSize = 10.sp) })
                    }
                }
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth().height(34.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp),
                            onClick = { accountMenu = true }
                        ) {
                            Text(accounts.firstOrNull { it.id == selectedAccountId }?.title
                                ?: "همه حساب‌ها", maxLines = 1,
                                overflow = TextOverflow.Ellipsis, fontSize = 10.sp)
                        }
                        DropdownMenu(expanded = accountMenu,
                            onDismissRequest = { accountMenu = false }) {
                            DropdownMenuItem(text = { Text("همه حساب‌ها") },
                                onClick = { selectedAccountId = null; accountMenu = false })
                            accounts.forEach { account ->
                                DropdownMenuItem(text = { Text(account.title) },
                                    onClick = {
                                        selectedAccountId = account.id
                                        accountMenu = false
                                    })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth().height(34.dp),
                            contentPadding = PaddingValues(horizontal = 7.dp),
                            onClick = { categoryMenu = true }
                        ) {
                            Text(selectedCategory ?: "همه دسته‌ها",
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp)
                        }
                        DropdownMenu(expanded = categoryMenu,
                            onDismissRequest = { categoryMenu = false }) {
                            DropdownMenuItem(text = { Text("همه دسته‌ها") },
                                onClick = { selectedCategory = null; categoryMenu = false })
                            categories.forEach { category ->
                                DropdownMenuItem(text = { Text(category) },
                                    onClick = {
                                        selectedCategory = category
                                        categoryMenu = false
                                    })
                            }
                        }
                    }
                }
                if (search.isNotBlank() || type != TransactionSearch.ALL ||
                    selectedAccountId != null || selectedCategory != null
                ) TextButton(
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    onClick = {
                        search = ""
                        type = TransactionSearch.ALL
                        selectedAccountId = null
                        selectedCategory = null
                    }
                ) { Text("پاک‌کردن فیلترها", fontSize = 10.sp) }
            }
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 3.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("واریز: " + toman(income), fontSize = 10.sp,
            color = jessiReadableText(financeAqua), maxLines = 1,
            modifier = Modifier.weight(1f))
        Text("برداشت: " + toman(expense), fontSize = 10.sp,
            color = jessiReadableText(danger), maxLines = 1,
            modifier = Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = {
            selecting = !selecting
            if (!selecting) selectedIds = emptySet()
        }) { Text(if (selecting) "لغو انتخاب" else "انتخاب برداشت‌ها") }
        if (selecting) {
            Text(toman(selectedTotal), modifier = Modifier.weight(1f), fontSize = 11.sp)
            Button(enabled = selectedRows.isNotEmpty() && availableDebts.isNotEmpty(), onClick = {
                debtId = availableDebts.firstOrNull()?.id
                assignmentError = false
                showAssignment = true
            }) { Text("ثبت پرداخت") }
        }
    }
    if (visible.isEmpty()) EmptyNote(
        if (txs.isEmpty()) "برای ثبت اولین تراکنش روی + بزن."
        else "تراکنشی با این جست‌وجو و فیلتر پیدا نشد."
    )
    visible.forEach { row ->
        val eligible = row.kind == "OUT" && row.groupId == null
        val toggle = {
            selectedIds = if (row.id in selectedIds) selectedIds - row.id else selectedIds + row.id
        }
        Row(Modifier.fillMaxWidth().then(if (eligible) Modifier.combinedClickable(
            onClick = { if (selecting) toggle() else onEdit(row) },
            onLongClick = { selecting = true; selectedIds = selectedIds + row.id }
        ) else Modifier), verticalAlignment = Alignment.CenterVertically) {
            if (selecting && eligible) Checkbox(checked = row.id in selectedIds, onCheckedChange = { toggle() })
            Box(Modifier.weight(1f)) {
                TransactionTile(
                    row, accounts, txs,
                    if (!selecting && row.kind in listOf("IN", "OUT", "DEBT_IN", "DEBT_OUT")) ({ onEdit(row) }) else null,
                    if (selecting || row.kind in listOf("GOAL_IN", "GOAL_OUT"))
                        null else ({ onDelete(row) })
                )
            }
        }
    }
}

@Composable
private fun TransactionTile(
    row: Transaction, accounts: List<Account>, txs: List<Transaction>,
    edit: (() -> Unit)?, delete: (() -> Unit)?
) {
    val credit = row.kind in listOf("IN", "DEBT_IN", "GOAL_IN")
    val transfer = row.kind == "TRANSFER_OUT" || row.kind == "GOAL_OUT" ||
        row.kind == "GOAL_IN"
    val transferTarget = if (transfer) txs.firstOrNull {
        it.groupId == row.groupId && it.kind == "TRANSFER_IN"
    }?.accountId else null
    val description = when {
        transferTarget != null -> "انتقال به " +
            (accounts.firstOrNull { it.id == transferTarget }?.title ?: "حساب دیگر")
        row.kind == "GOAL_OUT" -> "کنارگذاشتن برای هدف"
        row.kind == "GOAL_IN" -> "بازگشت پول از هدف"
        else -> row.category
    }
    val tone = when {
        row.kind in listOf("GOAL_OUT", "GOAL_IN") -> financePurple
        transfer -> financeBlue
        credit -> financeAqua
        else -> danger
    }
    val icon = when {
        row.kind in listOf("GOAL_OUT", "GOAL_IN") -> Icons.Filled.Savings
        transfer -> Icons.Outlined.SwapHoriz
        credit -> Icons.Outlined.SouthWest
        else -> Icons.Outlined.NorthEast
    }
    val type = if (row.kind in listOf("GOAL_OUT", "GOAL_IN")) "بانک اهداف"
        else if (transfer) "انتقال" else if (credit) "ورودی" else "خروجی"
    Surface(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .86f),
        border = BorderStroke(1.dp, tone.copy(alpha = .20f))
    ) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Surface(color = tone.copy(alpha = .14f),
                    shape = RoundedCornerShape(9.dp)) {
                    Icon(icon, contentDescription = null, tint = tone,
                        modifier = Modifier.padding(7.dp).size(17.dp))
                }
                Text(description, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f), fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                Text((if (credit) "+" else "−") + toman(row.amountToman),
                    color = jessiReadableText(tone), fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1)
            }
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text((accounts.firstOrNull { it.id == row.accountId }?.title ?: "حساب نامشخص") +
                    " • " + Jalali.of(row.createdAt) + " • " + type,
                    color = jessiReadableText(muted), fontSize = 10.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f))
                if (edit != null) IconButton(onClick = edit, modifier = Modifier.size(27.dp)) {
                    Icon(Icons.Outlined.Edit, contentDescription = "ویرایش تراکنش",
                        modifier = Modifier.size(16.dp))
                }
                if (delete != null) IconButton(onClick = delete, modifier = Modifier.size(27.dp)) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف تراکنش",
                        tint = danger, modifier = Modifier.size(16.dp))
                }
            }
            if (row.note.isNotBlank()) Text(row.note,
                color = jessiReadableText(muted), fontSize = 10.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (row.source == SmsReviewPolicy.TEST_SOURCE) Text(
                "آزمایشی • در آمار و مانده حساب محاسبه شده",
                color = jessiReadableText(financePurple), fontSize = 10.sp)
        }
    }
}

@Composable
private fun DebtsPage(
    debts: List<Debt>, txs: List<Transaction>, period: FinancePeriod, nowEpoch: Long,
    onEdit: (Debt) -> Unit, onPay: (Debt) -> Unit, onEditPayment: (Transaction) -> Unit, onDelete: (Debt) -> Unit
) {
    val totalOwed = debts.filter { it.direction == "I_OWE" }
        .sumOf { (it.totalToman - it.paidToman).coerceAtLeast(0) }
    FinanceSectionHero(
        "بدهی و اقساط", "پیگیری سررسیدها و پرداخت‌های باقی‌مانده",
        Icons.Filled.EventNote, toman(totalOwed), "بدهی باقی‌مانده",
        accent = financePurple
    )
    val completed = debts.filter { it.paidToman >= it.totalToman }
    val active = debts.filter { it.paidToman < it.totalToman }
    val debtIds = debts.map { it.id }.toSet()
    val payments = txs.filter { it.kind in listOf("DEBT_OUT", "DEBT_IN") && it.debtReferenceId() in debtIds }
        .sortedByDescending { it.createdAt }
    var history by rememberSaveable { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !history, onClick = { history = false }, label = { Text("فعال") })
        FilterChip(selected = history, onClick = { history = true }, label = { Text("تاریخچه بدهی و اقساط (${digitsFa(completed.size.toString())})") })
    }
    if (history && completed.isEmpty() && payments.isEmpty()) EmptyNote("هنوز سابقه پرداخت یا وصول ثبت نشده.")
    if (!history && active.isEmpty()) EmptyNote("فعلاً بدهی یا طلب فعالی ثبت نشده.")
    (if (history) completed else active).forEach { row ->
        val remaining = (row.totalToman - row.paidToman).coerceAtLeast(0)
        val mine = row.direction == "I_OWE"
        val tone = if (mine) financePurple else financeAqua
        val progress = (row.paidToman.toFloat() /
            row.totalToman.coerceAtLeast(1L)).coerceIn(0f, 1f)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .87f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, tone.copy(alpha = .20f))
        ) {
            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(color = tone.copy(alpha = .13f),
                        shape = RoundedCornerShape(9.dp)) {
                        Icon(if (row.installmentMonths > 0) Icons.Filled.CalendarMonth
                            else Icons.Filled.Handshake, contentDescription = null,
                            tint = tone, modifier = Modifier.padding(7.dp).size(17.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(row.person, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                        Text((if (row.installmentMonths > 0) "اقساطی" else "تسویه عادی") +
                            " • " + (if (mine) "بدهکارم" else "طلبکارم") +
                            (if (remaining == 0L) " • تسویه شده" else ""),
                            fontSize = 10.sp, color = jessiReadableText(muted))
                    }
                    Text(toman(remaining), fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = jessiReadableText(tone),
                        maxLines = 1)
                }
                // Keep both the original amount and the paid amount visible.
                Text("کل: " + toman(row.totalToman) + "  •  پرداخت‌شده: " +
                    toman(row.paidToman), fontSize = 10.sp,
                    color = jessiReadableText(muted), maxLines = 2)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = tone, trackColor = tone.copy(alpha = .12f)
                )
                if (row.installmentMonths > 0) {
                    val done = Installments.countPaid(row)
                    val left = Installments.remainingCount(row)
                    val next = Installments.nextDate(row)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(digitsFa(done.toString()) + " پرداخت‌شده",
                            fontSize = 10.sp, color = jessiReadableText(tone))
                        Text(digitsFa(left.toString()) + " باقی‌مانده",
                            fontSize = 10.sp, color = jessiReadableText(muted))
                    }
                    if (next != null) {
                        val dueEpoch = JalaliDates.toEpoch(next)
                        val monthsAway = JalaliDates.monthDistance(Jalali.of(nowEpoch), next)
                        val overdue = dueEpoch != null && dueEpoch < nowEpoch
                        val status = when {
                            overdue -> "سررسید گذشته"
                            monthsAway > 0 -> "تا " + digitsFa(monthsAway.toString()) + " ماه"
                            else -> "همین ماه"
                        }
                        Text("قسط بعد: " + JalaliDates.format(next) + " • " + status +
                            " • " + toman(Installments.nextAmount(row)),
                            color = jessiReadableText(if (overdue) danger else muted),
                            fontSize = 10.sp, maxLines = 2)
                    }
                    val dueHere = Installments.dueInPeriod(
                        row, period.start, period.endExclusive)
                    if (dueHere > 0L) Text("سررسید این دوره: " + toman(dueHere),
                        color = jessiReadableText(tone), fontSize = 10.sp)
                } else if (row.dueDateJalali.isNotBlank()) {
                    Text("سررسید: " + row.dueDateJalali, fontSize = 10.sp,
                        color = jessiReadableText(muted))
                }
                if (row.note.isNotBlank()) Text(row.note, fontSize = 10.sp,
                    color = jessiReadableText(muted), maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
                if (!history) Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (remaining > 0L) {
                        FilledTonalButton(
                            onClick = { onPay(row) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 1.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = tone.copy(alpha = .14f))
                        ) {
                            Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                                modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (mine) "ثبت پرداخت" else "ثبت وصول",
                                fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { onEdit(row) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "ویرایش بدهی",
                            modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = { onDelete(row) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.DeleteOutline,
                            contentDescription = "حذف بدهی", tint = danger,
                            modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
    if (history && payments.isNotEmpty()) {
        Text("سوابق پرداخت بدهی و اقساط", fontWeight = FontWeight.Bold, color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
        val batches = payments.groupBy {
            it.groupId?.takeIf { group -> group.startsWith("DEBT_REF:") && group.count { c -> c == ':' } == 2 }
                ?: "SINGLE:${it.id}"
        }
        batches.values.forEach { batch ->
            if (batch.size > 1) Text(
                "پرداخت تجمیعی • " + digitsFa(batch.size.toString()) + " برداشت • " + toman(batch.sumOf { it.amountToman }),
                fontWeight = FontWeight.Bold, fontSize = 12.sp, color = jessiReadableText(financePurple))
            batch.forEach { payment ->
            val parent = debts.firstOrNull { payment.debtReferenceId() == it.id }
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .87f)) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(parent?.person ?: payment.note, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(Jalali.of(payment.createdAt).toString(), fontSize = 10.sp, color = jessiReadableText(muted))
                    }
                    Text(toman(payment.amountToman), color = jessiReadableText(if (payment.kind == "DEBT_OUT") danger else mint), fontSize = 12.sp)
                    IconButton(onClick = { onEditPayment(payment) }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "ویرایش پرداخت قسط", modifier = Modifier.size(17.dp))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun BudgetsPage(
    budgets: List<Budget>, txs: List<Transaction>,
    onEdit: (Budget) -> Unit, onDelete: (Budget) -> Unit
) {
    val totalLimit = budgets.sumOf { it.monthlyLimitToman }
    FinanceSectionHero(
        "بودجه‌بندی", "کنترل هزینه‌ها در دوره مالی انتخاب‌شده",
        Icons.Filled.PieChart, toman(totalLimit), "سقف کل بودجه‌ها",
        accent = financeAmber
    )
    if (budgets.isEmpty()) EmptyNote("برای تعریف سقف هزینه ماهانه روی + بزن.")
    budgets.forEach { b ->
        val spent = txs.filter { it.kind == "OUT" && it.category == b.category }
            .sumOf { it.amountToman }
        val ratio = spent.toFloat() / b.monthlyLimitToman.coerceAtLeast(1L)
        val percent = (ratio * 100f).toInt()
        val tone = when {
            percent >= 100 -> danger
            percent >= 80 -> financeAmber
            else -> financeAqua
        }
        val status = when {
            percent >= 100 -> "سقف هزینه رد شده"
            percent >= 80 -> "نزدیک سقف هزینه"
            else -> "در محدوده بودجه"
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .87f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, tone.copy(alpha = .20f))
        ) {
            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Surface(color = tone.copy(alpha = .13f),
                        shape = RoundedCornerShape(9.dp)) {
                        Icon(Icons.Filled.DonutLarge, contentDescription = null,
                            tint = tone, modifier = Modifier.padding(7.dp).size(17.dp))
                    }
                    Text(b.category, modifier = Modifier.weight(1f),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(digitsFa(percent.toString()) + "٪", fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = jessiReadableText(tone))
                }
                Text("مصرف‌شده: " + toman(spent) + "  •  سقف: " +
                    toman(b.monthlyLimitToman), fontSize = 10.sp,
                    color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
                    maxLines = 2)
                LinearProgressIndicator(
                    progress = { ratio.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(4.dp)
                        .clip(RoundedCornerShape(7.dp)),
                    color = tone, trackColor = tone.copy(alpha = .12f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, fontSize = 10.sp, modifier = Modifier.weight(1f),
                        color = jessiReadableText(if (percent >= 80) tone else muted))
                    IconButton(onClick = { onEdit(b) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "ویرایش بودجه",
                            modifier = Modifier.size(17.dp))
                    }
                    IconButton(onClick = { onDelete(b) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Outlined.DeleteOutline,
                            contentDescription = "حذف بودجه", tint = danger,
                            modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TextEntry(
    label: String, value: String, onChange: (String) -> Unit,
    number: Boolean = false, singleLine: Boolean = true, grouped: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(if (number) normalizeNumericInput(it) else it) },
        label = { Text(label, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }, singleLine = singleLine,
        modifier = Modifier.fillMaxWidth(),
        visualTransformation = if (number && grouped) GroupedNumberVisualTransformation
            else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else KeyboardType.Text)
    )
}

@Composable
private fun <T> SelectItem(label: String, selected: T?, items: List<T>, display: (T) -> String, onPick: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontSize = 12.sp, color = jessiReadableText(muted))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(selected?.let(display) ?: "انتخاب کنید", modifier = Modifier.weight(1f), color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Text("⌄", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                items.forEach { item ->
                    DropdownMenuItem(text = { Text(display(item), color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }, onClick = { onPick(item); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun EntryDialog(title: String, valid: Boolean, dismiss: () -> Unit, onSave: () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = if (LocalJessiDark.current) Color(0xFF1B1B45) else Color.White,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, color = jessiReadableText(mint), fontWeight = FontWeight.ExtraBold)
                Box(Modifier.width(58.dp).height(3.dp)
                    .background(Brush.horizontalGradient(listOf(financeAqua, financePurple)),
                        RoundedCornerShape(5.dp)))
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
        },
        confirmButton = { Button(onClick = onSave, enabled = valid) { Text("ذخیره", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("انصراف", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) } }
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
        TextEntry("چهار رقم آخر کارت/حساب (اختیاری)", last4, { last4 = it }, number = true, grouped = false)
        TextEntry("موجودی اولیه به تومان", opening, { opening = it }, number = true)
        Text("موجودی فعلی = موجودی اولیه + واریزها − برداشت‌ها. برای جلوگیری از دوباره‌شماری، موجودی اولیه را در زمان شروع ثبت کن.", fontSize = 11.sp, color = jessiReadableText(muted))
    }
}

@Composable
private fun TransactionDialog(
    initial: Transaction?, accounts: List<Account>, debts: List<Debt>,
    dismiss: () -> Unit,
    onSave: (Transaction) -> Unit,
    onTransfer: (Long, Long, Long, String) -> Unit,
    onCollect: (Long, Long, Long, String, Long) -> Unit
) {
    var type by remember(initial) { mutableStateOf(initial?.kind ?: "OUT") }
    var accountId by remember(initial) { mutableStateOf(initial?.accountId ?: accounts.firstOrNull()?.id) }
    var targetAccountId by remember(initial) { mutableStateOf(accounts.firstOrNull { it.id != accountId }?.id) }
    var category by remember(initial) { mutableStateOf(initial?.category ?: "سایر") }
    var amountStr by remember(initial) { mutableStateOf(initial?.amountToman?.toString().orEmpty()) }
    var note by remember(initial) { mutableStateOf(initial?.note.orEmpty()) }
    var asCollection by remember(initial) { mutableStateOf(false) }
    var receivableId by remember(initial) { mutableStateOf<Long?>(null) }
    val receivables = remember(debts) {
        debts.filter { it.direction == "OWED_TO_ME" && it.totalToman > it.paidToman }
    }
    val selectedReceivable = receivables.firstOrNull { it.id == receivableId }
    val amount = readToman(amountStr)
    val transfer = type == "TRANSFER_OUT"
    val valid = amount != null && amount > 0 && accountId != null &&
        (!transfer || (targetAccountId != null && targetAccountId != accountId)) &&
        (!asCollection || (type == "IN" && selectedReceivable != null &&
            amount <= selectedReceivable.totalToman - selectedReceivable.paidToman))
    EntryDialog(if (initial == null) "ثبت تراکنش" else "ویرایش تراکنش", valid, dismiss, {
        if (transfer) onTransfer(accountId!!, targetAccountId!!, amount!!, note)
        else if (asCollection) onCollect(
            receivableId!!, accountId!!, amount!!, note, initial?.id ?: 0L
        )
        else onSave(Transaction(
            id = initial?.id ?: 0, accountId = accountId!!, amountToman = amount!!,
            kind = type, category = if (
                type == "IN" && category !in incomeCategories &&
                category != initial?.category
            ) "درآمد" else category,
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
                    }, label = { Text(label, fontSize = 11.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) })
                }
            }
        }
        if (accounts.isEmpty()) Text("ابتدا از بخش حساب‌ها یک حساب بساز.", color = jessiReadableText(danger), fontSize = 12.sp)
        else SelectItem("حساب", accountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title }, { accountId = it.id })
        if (transfer && accounts.isNotEmpty()) {
            SelectItem("حساب مقصد", targetAccountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title }, { targetAccountId = it.id })
            Text("انتقال بین حساب‌ها درآمد یا هزینه محسوب نمی‌شود.", fontSize = 11.sp, color = jessiReadableText(muted))
        }
        TextEntry("مبلغ به تومان", amountStr, { amountStr = it }, number = true)
        if (type == "OUT") SelectItem("دسته‌بندی", category, categories, { it }, { category = it })
        if (type == "IN") {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Checkbox(
                    checked = asCollection,
                    enabled = receivables.isNotEmpty(),
                    onCheckedChange = { yes ->
                        asCollection = yes
                        if (yes && receivableId == null)
                            receivableId = receivables.firstOrNull()?.id
                    }
                )
                Column(Modifier.weight(1f)) {
                    Text("این واریزی مربوط به وصول طلب است",
                        fontSize = 12.sp,
                        color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                    Text("وصول از مبلغ طلب کم می‌شود و دوباره به موجودی افزوده نمی‌شود.",
                        fontSize = 10.sp, color = jessiReadableText(muted))
                }
            }
            if (asCollection) {
                SelectItem("طلب مربوط", selectedReceivable, receivables,
                    { it.person + " • مانده " + toman(it.totalToman - it.paidToman) },
                    { receivableId = it.id })
                if (selectedReceivable != null) Text(
                    "مانده قابل وصول: " + toman(
                        selectedReceivable.totalToman - selectedReceivable.paidToman
                    ), fontSize = 11.sp, color = jessiReadableText(financeAqua))
            } else {
                SelectItem("نوع درآمد", category, incomeCategories, { it }, { category = it })
                if (category == "حقوق") Text(
                    "این واریز در حالت «از حقوق» دوره جدید مالی رو آغاز می‌کنه.",
                    color = jessiReadableText(mint), fontSize = 11.sp)
            }
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
            FilterChip(selected = dir == "I_OWE", onClick = { dir = "I_OWE" }, label = { Text("من بدهکارم", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) })
            FilterChip(selected = dir == "OWED_TO_ME", onClick = { dir = "OWED_TO_ME" }, label = { Text("من طلبکارم", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !installment, onClick = { installment = false }, label = { Text("بدهی عادی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) })
            FilterChip(selected = installment, onClick = { installment = true }, label = { Text("قسطی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) })
        }
        TextEntry("نام شخص، بانک یا مؤسسه", name, { name = it })
        TextEntry("مبلغ کل به تومان", amountStr, { amountStr = it }, number = true)
        if (installment) {
            TextEntry("تعداد کل اقساط (ماه)", installmentCount, { installmentCount = it }, number = true)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("اولین قسط چند ماه دیگر؟", fontSize = 12.sp, modifier = Modifier.weight(1f), color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                OutlinedButton(onClick = {
                    monthsUntilDue = (monthsUntilDue - 1).coerceAtLeast(0)
                    firstDue = JalaliDates.format(JalaliDates.addMonths(Jalali.of(System.currentTimeMillis()), monthsUntilDue))
                }, enabled = monthsUntilDue > 0) { Text("−", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
                Text(digitsFa(monthsUntilDue.toString()), color = jessiReadableText(mint))
                OutlinedButton(onClick = {
                    monthsUntilDue = (monthsUntilDue + 1).coerceAtMost(36)
                    firstDue = JalaliDates.format(JalaliDates.addMonths(Jalali.of(System.currentTimeMillis()), monthsUntilDue))
                }, enabled = monthsUntilDue < 36) { Text("+", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
            }
            JalaliDateInput("تاریخ اولین سررسید شمسی", firstDue, onChange = { firstDue = it })
            if (amount != null && months != null && months > 0) {
                Text("مبلغ تقریبی هر قسط: ${toman(amount / months)} • مانده با ثبت پرداخت کم می‌شه.",
                    fontSize = 11.sp, color = jessiReadableText(mint))
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
        Text("سقف‌ها در ابتدای هر دوره انتخابی مجدداً محاسبه می‌شوند؛ مانده حساب صفر نمی‌شود.", fontSize = 11.sp, color = jessiReadableText(muted))
    }
}

@Composable
private fun RepayDialog(debt: Debt, accounts: List<Account>, txs: List<Transaction>, dismiss: () -> Unit, onPay: (Long, Long) -> Unit) {
    var amountStr by remember(debt) { mutableStateOf(
        if (debt.installmentMonths > 0) Installments.nextAmount(debt).takeIf { it > 0L }?.toString().orEmpty() else ""
    ) }
    var accountId by remember(debt) { mutableStateOf<Long?>(if (accounts.size == 1) accounts.firstOrNull()?.id else null) }
    val amount = readToman(amountStr)
    val balance = debt.totalToman - debt.paidToman
    EntryDialog(if (debt.direction == "I_OWE") "ثبت بازپرداخت" else "ثبت وصول طلب", amount != null && amount > 0 && amount <= balance && accountId != null, dismiss, {
        onPay(amount!!, accountId!!)
    }) {
        Text("مانده: ${toman(balance)}", color = jessiReadableText(mint))
        if (debt.installmentMonths > 0) Text("مبلغ قسط بعد: ${toman(Installments.nextAmount(debt))}", color = jessiReadableText(mint), fontSize = 12.sp)
        if (accounts.isEmpty()) Text("برای ثبت پرداخت، ابتدا یک حساب بساز.", color = jessiReadableText(danger))
        else SelectItem(if (debt.direction == "I_OWE") "کسر از حساب" else "واریز به حساب", accountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title + " • " + toman(accountBalance(it.id, it.openingToman, txs)) }, { accountId = it.id })
        if (balance > 0L) OutlinedButton(onClick = { amountStr = balance.toString() }, modifier = Modifier.fillMaxWidth()) {
            Text("کل مبلغ باقی‌مانده • ${toman(balance)}")
        }
        TextEntry("مبلغ پرداخت یا دریافت (تومان)", amountStr, { amountStr = it }, number = true)
        Text("این مبلغ هم در مانده حساب اعمال می‌شود؛ اما به عنوان هزینه روزمره یا درآمد ماهانه دسته‌بندی نمی‌شود.", fontSize = 11.sp, color = jessiReadableText(muted))
    }
}

@Composable
private fun DebtPaymentEditDialog(
    payment: Transaction, accounts: List<Account>, debts: List<Debt>, txs: List<Transaction>,
    dismiss: () -> Unit, onSave: (Long, Long, String, Boolean) -> Unit
) {
    var accountId by remember(payment) { mutableStateOf<Long?>(payment.accountId) }
    var amountStr by remember(payment) { mutableStateOf(payment.amountToman.toString()) }
    var note by remember(payment) { mutableStateOf(payment.note) }
    var confirmUnlinked by remember(payment) { mutableStateOf(false) }
    val parentId = payment.debtReferenceId()
    val parent = debts.firstOrNull { it.id == parentId }
    val amount = readToman(amountStr)
    val maxAmount = parent?.let { it.totalToman - it.paidToman + payment.amountToman }
    val unresolvedChange = parent == null && amount != payment.amountToman
    val valid = amount != null && amount in 1L..100_000_000_000L &&
        accounts.any { it.id == accountId } && (maxAmount == null || amount <= maxAmount) &&
        (!unresolvedChange || confirmUnlinked)
    EntryDialog(if (payment.kind == "DEBT_OUT") "ویرایش پرداخت بدهی یا قسط" else "ویرایش وصول طلب",
        valid, dismiss, { onSave(accountId!!, amount!!, note, confirmUnlinked) }) {
        if (parent != null) {
            Text(parent.person, fontWeight = FontWeight.Bold)
            Text("حداکثر مبلغ قابل ثبت: " + toman(maxAmount!!), color = jessiReadableText(mint))
        }
        SelectItem(if (payment.kind == "DEBT_OUT") "کسر از حساب" else "واریز به حساب",
            accounts.firstOrNull { it.id == accountId }, accounts,
            { it.title + " • " + toman(accountBalance(it.id, it.openingToman, txs)) }, { accountId = it.id })
        TextEntry("مبلغ (تومان)", amountStr, { amountStr = it }, number = true)
        TextEntry("توضیح", note, { note = it })
        if (unresolvedChange) Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = confirmUnlinked, onCheckedChange = { confirmUnlinked = it })
            Text("طلب/بدهی مرتبط پیدا نشد؛ فقط تراکنش و موجودی حساب اصلاح شود.", fontSize = 11.sp)
        }
        Text("پرداخت قبلی جایگزین می‌شود؛ مبلغ دوباره کسر نمی‌شود.", fontSize = 11.sp, color = jessiReadableText(muted))
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
    row: SmsReview, accounts: List<Account>, debts: List<Debt>,
    dismiss: () -> Unit,
    onAccept: (Long, String, String, String, Long?) -> Unit,
    onReject: () -> Unit
) {
    val isSimulation = SmsReviewPolicy.isSimulation(row.fingerprint)
    var testRegistrationConsent by remember(row.id) { mutableStateOf(false) }
    var accountId by remember(row.id) { mutableStateOf(row.accountGuess ?: accounts.firstOrNull()?.id) }
    var category by remember(row.id) { mutableStateOf(if (row.kind == "IN") "درآمد" else "سایر") }
    var note by remember(row.id) { mutableStateOf("") }
    var asCollection by remember(row.id) { mutableStateOf(false) }
    var receivableId by remember(row.id) { mutableStateOf<Long?>(null) }
    val activeReceivables = debts.filter {
        it.totalToman > it.paidToman &&
            if (row.kind == "IN") it.direction == "OWED_TO_ME"
            else row.kind == "OUT" && it.direction == "I_OWE"
    }
    val selectedReceivable = activeReceivables.firstOrNull { it.id == receivableId }
    val needsUnit = row.amountUnit == "UNKNOWN"
    var selectedUnit by remember(row.id) { mutableStateOf(if (needsUnit) "" else row.amountUnit) }
    val original = row.amountRaw.takeIf { it > 0L } ?: row.amountToman
    val selectedAmount = when (selectedUnit) {
        "TOMAN" -> original
        "RIAL" -> BankSmsParser.rialToToman(original)
        else -> 0L
    }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("بررسی پیامک بانکی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSimulation) {
                    Text("نمونه آزمایشی • ثبت اختیاری", color = jessiReadableText(financePurple),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("در صورت تأیید، تراکنش آزمایشی مانند تراکنش عادی روی موجودی حساب، " +
                        "بودجه‌ها و نمودارهای درآمد و هزینه تأثیر می‌گذارد و با برچسب آزمایشی قابل شناسایی و حذف است.",
                        color = jessiReadableText(danger), fontSize = 12.sp, lineHeight = 19.sp)
                }
                Text(if (row.kind == "IN") "واریز احتمالی" else "برداشت احتمالی", color = jessiReadableText(mint))
                if (row.bankName.isNotBlank()) Text(row.bankName, fontSize = 13.sp, color = jessiReadableText(muted))
                Text(if (needsUnit && selectedUnit.isBlank())
                    reviewAmountLabel(row) else toman(selectedAmount),
                    fontWeight = FontWeight.Bold, fontSize = 20.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                Text("فرستنده: ${row.sender}", fontSize = 12.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                if (needsUnit) {
                    Text("واحد پول در پیامک مشخص نیست؛ قبل از ثبت انتخاب کن:",
                        color = jessiReadableText(danger), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = selectedUnit == "RIAL",
                            onClick = { selectedUnit = "RIAL" },
                            label = { Text("ریال", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
                        )
                        FilterChip(
                            selected = selectedUnit == "TOMAN",
                            onClick = { selectedUnit = "TOMAN" },
                            label = { Text("تومان", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
                        )
                    }
                    if (selectedUnit == "RIAL" && original % 10L != 0L) {
                        Text("مبلغ ریالی به نزدیک‌ترین تومان صحیح گرد می‌شود.",
                            fontSize = 11.sp, color = jessiReadableText(danger))
                    }
                }
                if (accounts.isEmpty()) Text("اول یک حساب بساز.", color = jessiReadableText(danger))
                else SelectItem("این تراکنش برای کدام حساب است؟",
                    accountId?.let { id -> accounts.firstOrNull { it.id == id } },
                    accounts, { it.title }, { accountId = it.id })
                if (row.kind in listOf("IN", "OUT") && activeReceivables.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = asCollection, onCheckedChange = { yes ->
                            asCollection = yes
                            if (yes && receivableId == null)
                                receivableId = activeReceivables.firstOrNull()?.id
                        })
                        Text(if (row.kind == "IN") "این واریزی، وصول طلب است" else "این برداشت، پرداخت بدهی / قسط است",
                            fontSize = 12.sp,
                            color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                    }
                    if (asCollection) {
                        SelectItem(if (row.kind == "IN") "طلب مرتبط" else "بدهی یا قسط مرتبط", selectedReceivable, activeReceivables,
                            { it.person + " • " + toman(if (row.kind == "OUT") it.paymentPeriodRemaining() else it.totalToman - it.paidToman) },
                            { receivableId = it.id })
                        if (row.kind == "OUT" && (selectedReceivable?.installmentMonths ?: 0) > 0) {
                            selectedReceivable?.let {
                                Text("قسط بعدی: " + (Installments.nextDate(it)?.toString() ?: "") +
                                    " • مانده این دوره: " + toman(it.paymentPeriodRemaining()), fontSize = 11.sp)
                                if (selectedAmount > it.paymentPeriodRemaining()) Text("مبلغ از مانده این قسط بیشتر است.",
                                    color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                        Text(if (row.kind == "IN") "موجودی حساب فقط یک‌بار افزایش پیدا می‌کند و مانده طلب کم می‌شود." else "موجودی حساب فقط یک‌بار کم می‌شود و مبلغ از مانده بدهی یا قسط کسر می‌شود.",
                            fontSize = 10.sp, color = jessiReadableText(muted))
                    }
                }
                if (!asCollection) SelectItem("برای چه دسته‌ای بود؟", category,
                    if (row.kind == "IN") incomeCategories else categories,
                    { it }, { category = it })
                if (!asCollection && category == "حقوق" && row.kind == "IN") {
                    Text("این واریز می‌تونه دوره جدید رو در حالت «از حقوق» آغاز کنه.",
                        color = jessiReadableText(mint), fontSize = 11.sp)
                }
                TextEntry("برای چی بود؟ (توضیح اختیاری)", note, { note = it }, singleLine = false)
                if (isSimulation) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = testRegistrationConsent,
                            onCheckedChange = { testRegistrationConsent = it })
                        Text("می‌پذیرم این تراکنش آزمایشی در آمار و مانده حساب اعمال شود.",
                            fontSize = 11.sp, color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                    }
                } else {
                    Text("قبل از تأیید، مبلغ و نوع تراکنش را با پیامک اصلی تطبیق بده.",
                        fontSize = 11.sp, color = jessiReadableText(muted))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    accountId?.let {
                        onAccept(it, category, note, selectedUnit,
                            if (asCollection) receivableId else null)
                    }
                },
                enabled = accountId != null && selectedAmount > 0L &&
                    (!asCollection || (selectedReceivable != null &&
                        selectedAmount <= (if (row.kind == "OUT") selectedReceivable.paymentPeriodRemaining()
                            else selectedReceivable.totalToman - selectedReceivable.paidToman))) &&
                    (!isSimulation || testRegistrationConsent)
            ) {
                Text(if (isSimulation) "ثبت آزمایشی در آمار" else "ثبت تراکنش", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReject) {
                    Text(if (isSimulation) "حذف نمونه" else "نادیده گرفتن", color = jessiReadableText(danger))
                }
                TextButton(onClick = dismiss) { Text("بعداً", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
            }
        }
    )
}
