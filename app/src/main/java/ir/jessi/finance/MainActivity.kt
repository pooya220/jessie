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
    background = Color(0xFF090E29), onBackground = Color.White,
    surface = Color(0xFF17183E), surfaceVariant = Color(0xFF242553),
    onSurface = Color.White, onSurfaceVariant = Color.White,
    onPrimaryContainer = Color.White, onSecondaryContainer = Color.White,
    onTertiaryContainer = Color.White, onError = Color.White,
    error = Color(0xFFFF699E), outline = Color(0xFF41407C)
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

    override fun onStop() {
        super.onStop()
        if (PinSecurity(this).isEnabled()) appLocked.value = true
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
    private val nowEpoch = mutableLongStateOf(System.currentTimeMillis())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationReviewId.longValue = intent.getLongExtra(SmsReviewNotifications.EXTRA_REVIEW_ID, -1L)
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
                    if (securityEnabled && appLocked.value) {
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
                    } else JessiHome(
                        openReviewId = notificationReviewId.longValue,
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
    data class GoalForm(val value: SavingsGoal? = null) : Editor
    data class GoalMoney(val goal: SavingsGoal, val operation: String) : Editor
    data class Repay(val value: Debt) : Editor
    data class Review(val value: SmsReview) : Editor
    data class Delete(val title: String, val action: () -> Unit) : Editor
}

@Composable
private fun JessiHome(openReviewId: Long, nowEpoch: Long, darkMode: Boolean,
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
    // Budget management is retained within the Accounts destination.
    var accountsShowBudgets by rememberSaveable { mutableStateOf(false) }
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
            modifier = Modifier.fillMaxSize())
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(if (darkMode)
                            listOf(Color(0xFF5A378F), Color(0xFF164A69))
                            else listOf(Color(0xFFDCF4FC), Color(0xFFE9E3FF))))
                        .border(1.dp, if (darkMode) Color.White.copy(alpha = .23f)
                            else Color(0xFFCEDCF2), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "نشان جسی",
                        tint = if (darkMode) Color(0xFFBFE7FF) else Color(0xFF008F69),
                        modifier = Modifier.size(25.dp))
                }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text("جسی", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                    Text("مدیریت مالی شخصی", color = jessiReadableText(muted), fontSize = 10.sp)
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
            // N2: floating liquid-glass dock. The selected destination has a
            // soft inner highlight, frosted tint and a distinct active marker.
            val glassShape = RoundedCornerShape(27.dp)
            val dockGlass = if (darkMode) {
                Brush.linearGradient(listOf(
                    Color(0xE62F365E), Color(0xE11C2342), Color(0xEA24365B)
                ))
            } else {
                Brush.linearGradient(listOf(
                    Color(0xF7FFFFFF), Color(0xE9E9F4FF), Color(0xF5FFFFFF)
                ))
            }
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
                    .height(81.dp).shadow(13.dp, glassShape)
                    .clip(glassShape).background(dockGlass)
                    .border(1.dp, if (darkMode) Color(0x7AFFFFFF)
                        else Color.White, glassShape)
            ) {
                // A subtle reflected top edge evokes liquid glass without
                // blurring the icons or reducing touch accessibility.
                Box(Modifier.fillMaxWidth().height(2.dp)
                    .background(Brush.horizontalGradient(listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = if (darkMode) .45f else .95f),
                        Color.Transparent
                    ))))
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxSize()
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
                        val accent = when (item) {
                            Tab.ACCOUNTS -> financeAqua
                            Tab.TXS -> financeBlue
                            Tab.HOME -> financeAqua
                            Tab.DEBTS -> financePurple
                            Tab.GOALS -> financeAmber
                        }
                        val iconShape = RoundedCornerShape(if (item == Tab.HOME) 16.dp else 14.dp)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { tabName = item.name; showSettings = false },
                            icon = {
                                Box(
                                    Modifier.size(if (item == Tab.HOME) 42.dp else 38.dp)
                                        .clip(iconShape)
                                        .background(if (selected) {
                                            Brush.linearGradient(listOf(
                                                accent.copy(alpha = if (darkMode) .33f else .19f),
                                                Color.White.copy(alpha = if (darkMode) .17f else .84f)
                                            ))
                                        } else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
                                        .border(1.dp, if (selected) {
                                            if (darkMode) Color.White.copy(alpha = .38f)
                                            else accent.copy(alpha = .33f)
                                        } else Color.Transparent, iconShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        if (selected) filledIcon else outlineIcon,
                                        contentDescription = item.label,
                                        modifier = Modifier.size(if (item == Tab.HOME) 26.dp else 23.dp)
                                    )
                                }
                            },
                            label = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(item.label, fontSize = 10.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                        maxLines = 1)
                                    if (selected) {
                                        Box(Modifier.width(14.dp).height(3.dp)
                                            .background(accent, RoundedCornerShape(2.dp)))
                                    }
                                }
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = accent, selectedTextColor = accent,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = if (darkMode) Color(0xFFB7C0D9) else Color(0xFF61758A),
                                unselectedTextColor = if (darkMode) Color(0xFFB7C0D9) else Color(0xFF61758A)
                            )
                        )
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
                            label = { Text("حساب‌های بانکی") },
                            leadingIcon = if (!accountsShowBudgets) ({
                                Icon(Icons.Outlined.Check, contentDescription = null, Modifier.size(17.dp))
                            }) else null)
                        FilterChip(selected = accountsShowBudgets,
                            onClick = { accountsShowBudgets = true },
                            label = { Text("بودجه‌بندی") },
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
                Tab.TXS -> TransactionsPage(periodTransactions, accounts,
                    onEdit = { editor = Editor.TxForm(it) },
                    onDelete = { row -> editor = Editor.Delete("این تراکنش حذف شود؟") { vm.deleteTransaction(row) } }
                )
                Tab.DEBTS -> DebtsPage(debts, chosenPeriod, nowEpoch,
                    onEdit = { editor = Editor.DebtForm(it) },
                    onPay = { editor = Editor.Repay(it) },
                    onDelete = { row -> editor = Editor.Delete("این بدهی یا طلب حذف شود؟") { vm.deleteDebt(row.id) } }
                )
                Tab.GOALS -> SavingsGoalsScreen(
                    goals = goals, movements = goalMovements, accounts = accounts,
                    nowEpoch = nowEpoch, dark = darkMode,
                    onAdd = { editor = Editor.GoalForm() },
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
            title = { Text("بازیابی اطلاعات جسی") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("فایل معتبر است و این موارد را دارد:", fontWeight = FontWeight.Bold)
                    Text(candidate.summary(), fontSize = 12.sp)
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
        is Editor.GoalForm -> SavingsGoalFormDialog(
            initial = e.value,
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
        is Editor.Repay -> RepayDialog(e.value, accounts, dismiss = { editor = null }) { amount, accountId -> vm.settleDebt(e.value, amount, accountId); editor = null }
        is Editor.Review -> ReviewDialog(
            e.value, accounts,
            dismiss = { editor = null },
            onAccept = { id, category, note, unit ->
                vm.acceptReview(e.value, id, category, note, unit) { saved ->
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
        is Editor.Delete -> AlertDialog(onDismissRequest = { editor = null }, title = { Text("تأیید حذف") }, text = { Text(e.title) }, confirmButton = { TextButton(onClick = { e.action(); editor = null }) { Text("حذف", color = jessiReadableText(danger)) } }, dismissButton = { TextButton(onClick = { editor = null }) { Text("انصراف") } })
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
    backgroundStyle: String, backgroundPhotoUri: String, backgroundDim: Float,
    onBackgroundStyle: (String) -> Unit, onPickBackground: () -> Unit,
    onClearBackground: () -> Unit, onBackgroundDim: (Float) -> Unit,
    pinSecurity: PinSecurity, onSecurityChanged: () -> Unit,
    versionName: String
) {
    var section by remember { mutableStateOf("home") }
    val titles = mapOf(
        "appearance" to "ظاهر و پس‌زمینه",
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
                    fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (section == "home") "هر موضوع در صفحه جداگانه و مرتب"
                     else "جسی • تنظیمات", fontSize = 11.sp, color = jessiReadableText(muted))
            }
        }
        if (section == "home") {
            SettingsNavRow(Icons.Outlined.Palette, "ظاهر و پس‌زمینه",
                "تم، تصویر دلخواه، شدت هاله شیشه‌ای", financeAqua) { section = "appearance" }
            SettingsNavRow(Icons.Outlined.Lock, "قفل ورود و امنیت",
                "اثر انگشت و پین ورود", financePurple) { section = "security" }
            SettingsNavRow(Icons.Outlined.Sms, "پیامک و اعلان‌ها",
                "مجوزها و تنظیمات بررسی پیامک", financeBlue) { section = "sms" }
            SettingsNavRow(Icons.Outlined.BugReport, "آزمایش و عیب‌یابی",
                "اعلان آزمایشی و تست متن پیامک", financePurple) { section = "diagnostics" }
            SettingsNavRow(Icons.Outlined.SaveAlt, "پشتیبان‌گیری",
                "خروجی و بازیابی اطلاعات مالی", financeAqua) { section = "backup" }
            SettingsNavRow(Icons.Outlined.CalendarMonth, "دوره مالی",
                "انتخاب ماه شمسی یا دوره حقوق", financeAmber) { section = "period" }
            SettingsNavRow(Icons.Outlined.Info, "درباره جسی",
                "نسخه و اطلاعات نگهداری داده", financeBlue) { section = "about" }
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
                    onStyleChange = onBackgroundStyle,
                    onChooseImage = onPickBackground,
                    onClearImage = onClearBackground,
                    onDimChange = onBackgroundDim
                )
            }
        }

        if (section == "sms") {
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
                    Text("دسترسی SMS", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(if (smsPermitted) "اجازه داده شده" else "اجازه داده نشده",
                        fontSize = 11.sp, color = jessiReadableText(if (smsPermitted) mint else danger))
                }
                if (!smsPermitted) TextButton(onClick = onSmsPermission) { Text("درخواست مجوز") }
                else Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = mint)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("دسترسی اعلان‌ها", fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(if (notificationsPermitted) "اجازه داده شده" else "اجازه داده نشده",
                        fontSize = 11.sp, color = jessiReadableText(if (notificationsPermitted) mint else danger))
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

        }

        if (section == "diagnostics") {
        Tile(Modifier.fillMaxWidth(), accent = financePurple) {
            SettingsGroupTitle(Icons.Outlined.BugReport, "عیب‌یابی دریافت پیامک", accent = financePurple)
            Text("وضعیت اعلان: " + (notificationBlocker ?: "مجوز و کانال اعلان فعال است"),
                fontSize = 12.sp, color = jessiReadableText(if (notificationBlocker == null) mint else danger))
            Text("وضعیت آخرین پیامک در جسی:", fontWeight = FontWeight.Medium, fontSize = 12.sp)
            Text(diagnosticSummary, fontSize = 11.sp, color = jessiReadableText(muted), lineHeight = 17.sp)
            Text("پیامک‌های منتظر بررسی: " + digitsFa(pendingReviews.toString()),
                fontSize = 12.sp, color = jessiReadableText(muted))
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
                    label = { Text("اول هر ماه شمسی") }
                )
                FilterChip(
                    selected = periodMode == FinancePeriodEngine.SALARY,
                    onClick = { onPeriodChange(FinancePeriodEngine.SALARY) },
                    label = { Text("پس از تأیید حقوق") }
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
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
                    color = jessiReadableText(danger), fontSize = 12.sp)
                Text("برای اصلاح الگوریتم، همین نمونه رو به جسی بفرست.",
                    color = jessiReadableText(muted), fontSize = 11.sp)
            } else {
                Text("پیامک شناسایی شد", color = jessiReadableText(mint), fontWeight = FontWeight.Bold)
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
                    Text("ساخت نمونه و بازکردن فرم ثبت")
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
            fontSize = 11.sp, color = jessiReadableText(muted))
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
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
    val base = if (dark) Color(0xAC12182F) else Color(0xCEF7FBFF)
    val aurora = if (dark) Brush.linearGradient(listOf(
        Color(0xC230255A), Color(0xC0142B47), Color(0xBD173244), Color(0xBC151930)
    )) else Brush.linearGradient(listOf(
        Color(0xCFF1EAFB), Color(0xD5F1FAFF), Color(0xCDECF9F6), Color(0xCFFDFBFF)
    ))
    val glow = Brush.radialGradient(listOf(
        tint.copy(alpha = if (dark) .23f else .095f),
        Color.Transparent
    ), radius = 540f)
    Surface(
        modifier = modifier, color = base, shape = shape,
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
            listOf(tone.copy(alpha = if (dark) .23f else .13f),
                if (dark) Color(0xFF18243A) else Color.White.copy(alpha = .75f))
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
        Brush.linearGradient(listOf(Color(0xE630235C), Color(0xDD172B4D), Color(0xDD17443F)))
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
                Text("بانک اهداف", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    if (balanceVisible) toman(savedForGoals) else "••••••",
                    fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = jessiReadableText(financePurple),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = { onSelectTab(Tab.GOALS) }) {
                Text("اهداف", fontSize = 11.sp)
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
                Text("اقساط و بدهی‌های ماه آینده", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Text("سررسیدهای " + monthCaption, fontSize = 11.sp, color = jessiReadableText(muted))
            }
            TextButton(onClick = { onSelectTab(Tab.DEBTS) },
                contentPadding = PaddingValues(horizontal = 4.dp)) {
                Text("جزئیات", fontSize = 11.sp)
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
            Text("سررسیدهای ماه آینده", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((if (item.isInstallment) "قسط" else "بدهی") + " • " +
                            JalaliDates.format(item.dueDate), color = jessiReadableText(muted), fontSize = 10.sp)
                    }
                    Text(if (balanceVisible) toman(item.amountToman) else "••••••",
                        fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Tag(digitsFa(reviews.size.toString()), true)
            }
            reviews.take(5).forEach { row ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (row.kind == "IN") "واریز احتمالی" else "برداشت احتمالی",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${reviewAmountLabel(row)} • ${row.sender}", fontSize = 11.sp, color = jessiReadableText(muted))
                    }
                    TextButton(onClick = { onReview(row) }) { Text("بررسی") }
                }
            }
        }
    }
    if (budgets.isNotEmpty()) {
        Tile(Modifier.fillMaxWidth()) {
            Text("بودجه‌های این دوره", fontWeight = FontWeight.Bold, fontSize = 14.sp)
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
            Text("دسترسی‌های پیامک بانکی", fontWeight = FontWeight.Bold)
            if (!smsMonitoringEnabled || !reviewNotificationsEnabled) {
                Text("بررسی پیامک یا اعلان‌ها از تنظیمات جسی غیرفعال شده.",
                    fontSize = 12.sp, color = jessiReadableText(muted))
                TextButton(onClick = onOpenSettings) { Text("تنظیمات پیامک و اعلان") }
            }
            if (!smsPermitted) {
                Text("برای شناسایی پیامک‌های بانکی جدید، مجوز SMS را فعال کن.", fontSize = 12.sp, color = jessiReadableText(muted))
                TextButton(onClick = requestPermission) { Text("فعال‌کردن SMS") }
            }
            if (!notificationsPermitted) {
                Text("برای اعلان بررسی تراکنش، مجوز اعلان را فعال کن.", fontSize = 12.sp, color = jessiReadableText(muted))
                TextButton(onClick = requestNotificationPermission) { Text("فعال‌کردن اعلان") }
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
                fontWeight = FontWeight.Bold, fontSize = 15.sp)
            TextButton(onClick = { onSelectTab(Tab.TXS) }, contentPadding = PaddingValues(4.dp)) {
                Text("مشاهده همه", fontSize = 11.sp)
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
                maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    val shape = RoundedCornerShape(18.dp)
    val gradient = if (dark) Brush.linearGradient(
        listOf(
            highlight.copy(alpha = .30f), Color(0xD2302057),
            Color(0xCF14364B), Color(0xD5111A34)
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(
                color = highlight.copy(alpha = if (dark) .21f else .12f),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(icon, contentDescription = null, tint = highlight,
                    modifier = Modifier.padding(8.dp).size(21.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Text(description, color = jessiReadableText(muted), fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(mainStat, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp,
                color = jessiReadableText(MaterialTheme.colorScheme.onSurface),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            Text(statLabel, fontSize = 11.sp, color = jessiReadableText(muted), modifier = Modifier.padding(bottom = 3.dp))
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
                if (dark) Color(0xFF343057) else Color(0xFFF7FAFF)
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
                Text(primaryLabel, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        if (edit != null) {
            TextButton(onClick = edit) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("ویرایش", fontSize = 11.sp)
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
private fun AccountsPage(accounts: List<Account>, txs: List<Transaction>, onEdit: (Account) -> Unit, onDelete: (Account) -> Unit) {
    val balance = accounts.sumOf { accountBalance(it.id, it.openingToman, txs) }
    FinanceSectionHero(
        "حساب‌های من", "نمای یکجا از حساب‌ها و موجودی ثبت‌شده",
        Icons.Filled.AccountBalanceWallet,
        toman(balance), digitsFa(accounts.size.toString()) + " حساب", accent = financeAqua
    )
    if (accounts.isEmpty()) EmptyNote("حسابی ثبت نشده. برای شروع روی + بزن و موجودی اولیه را وارد کن.")
    accounts.forEach { acc ->
        val amount = accountBalance(acc.id, acc.openingToman, txs)
        val canDelete = txs.none { it.accountId == acc.id }
        Tile(Modifier.fillMaxWidth(), accent = financeAqua) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinanceIconTile(Icons.Filled.AccountBalanceWallet, financeAqua)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(acc.title, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(listOf(acc.bank, if (acc.last4.isNotBlank())
                        "•••• " + digitsFa(acc.last4) else "").filter { it.isNotBlank() }
                        .joinToString(" • "), color = jessiReadableText(muted), fontSize = 11.sp)
                }
                Surface(color = financeAqua.copy(alpha = .12f), shape = RoundedCornerShape(30.dp)) {
                    Text("حساب", Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        color = jessiReadableText(financeAqua), fontSize = 11.sp)
                }
            }
            Row(
                Modifier.fillMaxWidth().background(
                    Brush.horizontalGradient(listOf(financeAqua.copy(alpha = .12f),
                        financeBlue.copy(alpha = .04f))),
                    RoundedCornerShape(11.dp)
                ).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("موجودی", color = jessiReadableText(muted), fontSize = 11.sp)
                Text(toman(amount), color = jessiReadableText(financeAqua), fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            FinanceActionRow(
                edit = { onEdit(acc) },
                delete = if (canDelete) ({ onDelete(acc) }) else null
            )
        }
    }
}

@Composable
private fun TransactionsPage(txs: List<Transaction>, accounts: List<Account>, onEdit: (Transaction) -> Unit, onDelete: (Transaction) -> Unit) {
    val visible = txs.filter { it.kind != "TRANSFER_IN" }
    val income = txs.filter { it.kind == "IN" }.sumOf { it.amountToman }
    val expense = txs.filter { it.kind == "OUT" }.sumOf { it.amountToman }
    FinanceSectionHero(
        "تراکنش‌ها", "گردش مالی دوره انتخاب‌شده",
        Icons.Filled.ReceiptLong,
        digitsFa(visible.size.toString()), "تراکنش", accent = financeBlue
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        FinanceSummaryChip("درآمد", income, income = true, Modifier.weight(1f))
        FinanceSummaryChip("هزینه", expense, income = false, Modifier.weight(1f))
    }
    if (visible.isEmpty()) EmptyNote("برای ثبت اولین تراکنش روی + بزن.")
    visible.forEach { row ->
        TransactionTile(
            row, accounts, txs,
            if (row.kind in listOf("IN", "OUT")) ({ onEdit(row) }) else null,
            if (row.kind in listOf("DEBT_IN", "DEBT_OUT", "GOAL_IN", "GOAL_OUT"))
                null else ({ onDelete(row) })
        )
    }
}

@Composable
private fun TransactionTile(
    row: Transaction, accounts: List<Account>, txs: List<Transaction>,
    edit: (() -> Unit)?, delete: (() -> Unit)?
) {
    val credit = row.kind in listOf("IN", "DEBT_IN", "GOAL_IN")
    val transfer = row.kind == "TRANSFER_OUT" || row.kind == "GOAL_OUT" || row.kind == "GOAL_IN"
    val transferTarget = if (transfer) txs.firstOrNull {
        it.groupId == row.groupId && it.kind == "TRANSFER_IN"
    }?.accountId else null
    val description = if (transferTarget != null)
        "انتقال به " + (accounts.firstOrNull { it.id == transferTarget }?.title ?: "حساب دیگر")
    else if (row.kind == "GOAL_OUT") "کنارگذاشتن برای هدف"
    else if (row.kind == "GOAL_IN") "بازگشت پول از هدف"
    else row.category
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
    Tile(Modifier.fillMaxWidth(), accent = tone) {
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FinanceIconTile(icon, tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(description, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text((accounts.firstOrNull { it.id == row.accountId }?.title ?: "حساب نامشخص")
                    + " • " + Jalali.of(row.createdAt),
                    color = jessiReadableText(muted), fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text((if (credit) "+" else "−") + toman(row.amountToman),
                    color = jessiReadableText(tone), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1)
                Text(if (row.kind in listOf("GOAL_OUT", "GOAL_IN")) "بانک اهداف"
                    else if (transfer) "انتقال" else if (credit) "ورودی" else "خروجی",
                    color = jessiReadableText(muted), fontSize = 10.sp)
            }
        }
        if (row.source == SmsReviewPolicy.TEST_SOURCE) {
            Surface(
                color = financePurple.copy(alpha = .13f),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("آزمایشی • در آمار و مانده حساب محاسبه شده",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = jessiReadableText(financePurple), fontSize = 10.sp)
            }
        }
        if (row.note.isNotBlank()) {
            Text(row.note, color = jessiReadableText(muted), fontSize = 11.sp,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (edit != null || delete != null) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .27f))
            FinanceActionRow(edit, delete)
        }
    }
}

@Composable
private fun DebtsPage(
    debts: List<Debt>, period: FinancePeriod, nowEpoch: Long,
    onEdit: (Debt) -> Unit, onPay: (Debt) -> Unit, onDelete: (Debt) -> Unit
) {
    val totalOwed = debts.filter { it.direction == "I_OWE" }
        .sumOf { (it.totalToman - it.paidToman).coerceAtLeast(0) }
    FinanceSectionHero(
        "بدهی و اقساط", "پیگیری سررسیدها و پرداخت‌های باقی‌مانده",
        Icons.Filled.EventNote, toman(totalOwed), "بدهی باقی‌مانده", accent = financePurple
    )
    if (debts.isEmpty()) EmptyNote("فعلاً بدهی یا طلبی ثبت نشده.")
    debts.forEach { row ->
        val remaining = (row.totalToman - row.paidToman).coerceAtLeast(0)
        val mine = row.direction == "I_OWE"
        val tone = if (mine) financePurple else financeAqua
        Tile(Modifier.fillMaxWidth(), accent = tone) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinanceIconTile(if (row.installmentMonths > 0) Icons.Filled.CalendarMonth
                    else Icons.Filled.Handshake, tone)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(row.person, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (row.installmentMonths > 0) "بازپرداخت اقساطی" else "تسویه عادی",
                        color = jessiReadableText(muted), fontSize = 11.sp)
                }
                Tag(if (mine) "بدهکارم" else "طلبکارم", !mine)
            }
            Row(Modifier.fillMaxWidth().background(
                tone.copy(alpha = .085f), RoundedCornerShape(11.dp)).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("مانده قابل تسویه", fontSize = 11.sp, color = jessiReadableText(muted))
                    Text(toman(remaining), color = jessiReadableText(if (mine) MaterialTheme.colorScheme.onSurface else mint),
                        fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
                if (remaining == 0L) Tag("تسویه شده", true)
            }
            Text("کل: " + toman(row.totalToman) + " • پرداخت‌شده: " +
                toman(row.paidToman), color = jessiReadableText(muted), fontSize = 11.sp)
            val progress = (row.paidToman.toFloat() /
                row.totalToman.coerceAtLeast(1L)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp)
                    .clip(RoundedCornerShape(7.dp)),
                color = tone, trackColor = tone.copy(alpha = .12f)
            )
            if (row.installmentMonths > 0) {
                val done = Installments.countPaid(row)
                val left = Installments.remainingCount(row)
                val next = Installments.nextDate(row)
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(digitsFa(done.toString()) + " پرداخت‌شده", fontSize = 11.sp, color = jessiReadableText(mint))
                    Text("•", color = jessiReadableText(muted))
                    Text(digitsFa(left.toString()) + " باقی‌مانده", fontSize = 11.sp, color = jessiReadableText(muted))
                }
                if (next != null) {
                    val dueEpoch = JalaliDates.toEpoch(next)
                    val monthsAway = JalaliDates.monthDistance(Jalali.of(nowEpoch), next)
                    val overdue = dueEpoch != null && dueEpoch < nowEpoch
                    val status = when {
                        overdue -> "سررسید گذشته"
                        monthsAway > 0 -> "تا " + digitsFa(monthsAway.toString()) + " ماه دیگر"
                        else -> "همین ماه"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Outlined.Event, contentDescription = null,
                            tint = if (overdue) danger else tone, modifier = Modifier.size(17.dp))
                        Text("قسط بعد: " + JalaliDates.format(next) + " • " + status,
                            color = jessiReadableText(if (overdue) danger else muted), fontSize = 11.sp)
                    }
                    Text("مبلغ قسط بعدی: " + toman(Installments.nextAmount(row)),
                        fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                val dueHere = Installments.dueInPeriod(row, period.start, period.endExclusive)
                if (dueHere > 0L) Tag("سررسید این دوره • " + toman(dueHere))
            } else if (row.dueDateJalali.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.Event, contentDescription = null,
                        tint = tone, modifier = Modifier.size(16.dp))
                    Text("سررسید: " + row.dueDateJalali, fontSize = 11.sp, color = jessiReadableText(muted))
                }
            }
            if (row.note.isNotBlank()) Text(row.note, fontSize = 11.sp, color = jessiReadableText(muted))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .27f))
            FinanceActionRow(
                edit = { onEdit(row) },
                delete = { onDelete(row) },
                primary = if (remaining > 0L) ({ onPay(row) }) else null,
                primaryLabel = if (mine) "ثبت پرداخت" else "ثبت وصول"
            )
        }
    }
}

@Composable
private fun BudgetsPage(
    budgets: List<Budget>, txs: List<Transaction>, onEdit: (Budget) -> Unit,
    onDelete: (Budget) -> Unit
) {
    val totalLimit = budgets.sumOf { it.monthlyLimitToman }
    FinanceSectionHero(
        "بودجه‌بندی", "کنترل هزینه‌ها در دوره مالی انتخاب‌شده",
        Icons.Filled.PieChart, 
        toman(totalLimit), "سقف کل بودجه‌ها", accent = financeAmber
    )
    if (budgets.isEmpty()) EmptyNote("برای تعریف سقف هزینه ماهانه روی + بزن.")
    budgets.forEach { b ->
        val spent = txs.filter { it.kind == "OUT" && it.category == b.category }
            .sumOf { it.amountToman }
        val ratio = spent.toFloat() / b.monthlyLimitToman.coerceAtLeast(1L)
        val percent = (ratio * 100f).toInt()
        val usedColor = if (percent >= 100) danger
            else if (percent >= 80) financeAmber else financeAqua
        Tile(Modifier.fillMaxWidth(), accent = usedColor) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FinanceIconTile(Icons.Filled.DonutLarge, usedColor)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(b.category, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("سقف هزینه این دوره", color = jessiReadableText(muted), fontSize = 11.sp)
                }
                Surface(
                    color = usedColor.copy(alpha = .13f),
                    shape = RoundedCornerShape(40.dp)
                ) {
                    Text(digitsFa(percent.toString()) + "٪",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontWeight = FontWeight.ExtraBold, color = jessiReadableText(usedColor), fontSize = 12.sp)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("مصرف‌شده", color = jessiReadableText(muted), fontSize = 11.sp)
                    Text(toman(spent), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("سقف تعیین‌شده", color = jessiReadableText(muted), fontSize = 11.sp)
                    Text(toman(b.monthlyLimitToman), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
            LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp)
                    .clip(RoundedCornerShape(7.dp)),
                color = usedColor, trackColor = usedColor.copy(alpha = .12f))
            Text(
                if (percent >= 100) "سقف هزینه این دسته رد شده"
                else if (percent >= 80) "نزدیک سقف هزینه هستی"
                else "در محدوده بودجه",
                fontSize = 11.sp, color = jessiReadableText(if (percent >= 80) usedColor else muted))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .27f))
            FinanceActionRow(edit = { onEdit(b) }, delete = { onDelete(b) })
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
        label = { Text(label) }, singleLine = singleLine,
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
        TextEntry("چهار رقم آخر کارت/حساب (اختیاری)", last4, { last4 = it }, number = true, grouped = false)
        TextEntry("موجودی اولیه به تومان", opening, { opening = it }, number = true)
        Text("موجودی فعلی = موجودی اولیه + واریزها − برداشت‌ها. برای جلوگیری از دوباره‌شماری، موجودی اولیه را در زمان شروع ثبت کن.", fontSize = 11.sp, color = jessiReadableText(muted))
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
        if (accounts.isEmpty()) Text("ابتدا از بخش حساب‌ها یک حساب بساز.", color = jessiReadableText(danger), fontSize = 12.sp)
        else SelectItem("حساب", accountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title }, { accountId = it.id })
        if (transfer && accounts.isNotEmpty()) {
            SelectItem("حساب مقصد", targetAccountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title }, { targetAccountId = it.id })
            Text("انتقال بین حساب‌ها درآمد یا هزینه محسوب نمی‌شود.", fontSize = 11.sp, color = jessiReadableText(muted))
        }
        TextEntry("مبلغ به تومان", amountStr, { amountStr = it }, number = true)
        if (type == "OUT") SelectItem("دسته‌بندی", category, categories, { it }, { category = it })
        if (type == "IN") {
            SelectItem("نوع درآمد", category, incomeCategories, { it }, { category = it })
            if (category == "حقوق") Text("این واریز در حالت «از حقوق» دوره جدید مالی رو آغاز می‌کنه.", color = jessiReadableText(mint), fontSize = 11.sp)
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
                Text(digitsFa(monthsUntilDue.toString()), color = jessiReadableText(mint))
                OutlinedButton(onClick = {
                    monthsUntilDue = (monthsUntilDue + 1).coerceAtMost(36)
                    firstDue = JalaliDates.format(JalaliDates.addMonths(Jalali.of(System.currentTimeMillis()), monthsUntilDue))
                }, enabled = monthsUntilDue < 36) { Text("+") }
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
        Text("مانده: ${toman(balance)}", color = jessiReadableText(mint))
        if (debt.installmentMonths > 0) Text("مبلغ قسط بعد: ${toman(Installments.nextAmount(debt))}", color = jessiReadableText(mint), fontSize = 12.sp)
        if (accounts.isEmpty()) Text("برای ثبت پرداخت، ابتدا یک حساب بساز.", color = jessiReadableText(danger))
        else SelectItem("حساب مرتبط", accountId?.let { id -> accounts.firstOrNull { it.id == id } }, accounts, { it.title }, { accountId = it.id })
        TextEntry("مبلغ پرداخت یا دریافت (تومان)", amountStr, { amountStr = it }, number = true)
        Text("این مبلغ هم در مانده حساب اعمال می‌شود؛ اما به عنوان هزینه روزمره یا درآمد ماهانه دسته‌بندی نمی‌شود.", fontSize = 11.sp, color = jessiReadableText(muted))
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
    val isSimulation = SmsReviewPolicy.isSimulation(row.fingerprint)
    var testRegistrationConsent by remember(row.id) { mutableStateOf(false) }
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
                    fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("فرستنده: ${row.sender}", fontSize = 12.sp)
                if (needsUnit) {
                    Text("واحد پول در پیامک مشخص نیست؛ قبل از ثبت انتخاب کن:",
                        color = jessiReadableText(danger), fontSize = 12.sp)
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
                            fontSize = 11.sp, color = jessiReadableText(danger))
                    }
                }
                if (accounts.isEmpty()) Text("اول یک حساب بساز.", color = jessiReadableText(danger))
                else SelectItem("این تراکنش برای کدام حساب است؟",
                    accountId?.let { id -> accounts.firstOrNull { it.id == id } },
                    accounts, { it.title }, { accountId = it.id })
                SelectItem("برای چه دسته‌ای بود؟", category,
                    if (row.kind == "IN") incomeCategories else categories,
                    { it }, { category = it })
                if (category == "حقوق" && row.kind == "IN") {
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
                onClick = { accountId?.let { onAccept(it, category, note, selectedUnit) } },
                enabled = accountId != null && selectedAmount > 0L &&
                    (!isSimulation || testRegistrationConsent)
            ) {
                Text(if (isSimulation) "ثبت آزمایشی در آمار" else "ثبت تراکنش")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReject) {
                    Text(if (isSimulation) "حذف نمونه" else "نادیده گرفتن", color = jessiReadableText(danger))
                }
                TextButton(onClick = dismiss) { Text("بعداً") }
            }
        }
    )
}
