package ir.jessi.finance.sms

import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import ir.jessi.finance.data.Account
import ir.jessi.finance.data.FinanceDatabase
import ir.jessi.finance.data.SmsReview
import ir.jessi.finance.data.SmsReviewPolicy
import ir.jessi.finance.security.PinSecurity
import ir.jessi.finance.ui.categories
import ir.jessi.finance.ui.incomeCategories
import ir.jessi.finance.ui.toman
import ir.jessi.finance.ui.digitsFa
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/**
 * A small notification-only window. It doesn't create/open MainActivity or show
 * banking data until the existing Jessie lock has been satisfied.
 */
class SmsReviewPopupActivity : FragmentActivity() {
    private var authenticated by mutableStateOf(false)
    private var biometricAttempted = false
    private lateinit var security: PinSecurity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val reviewId = intent.getLongExtra(SmsReviewNotifications.EXTRA_REVIEW_ID, -1L)
        if (reviewId <= 0L) { finish(); return }
        security = PinSecurity(this)
        authenticated = !security.isEnabled()
        val dark = getSharedPreferences("jessi_ui_theme", MODE_PRIVATE)
            .getBoolean("is_dark", true)

        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(
                    colorScheme = if (dark) darkColorScheme(
                        primary = Color(0xFFD062FF),
                        background = Color(0xFF090E29),
                        surface = Color(0xFF191B3D),
                        onSurface = Color.White,
                        onSurfaceVariant = Color(0xFFF0EFFF)
                    ) else lightColorScheme(
                        primary = Color(0xFF008E5B),
                        surface = Color.White,
                        onSurface = Color(0xFF152A2D)
                    )
                ) {
                    Box(Modifier.fillMaxWidth().padding(12.dp),
                        contentAlignment = Alignment.Center) {
                        Surface(
                            modifier = Modifier.widthIn(max = 450.dp).fillMaxWidth(),
                            shape = RoundedCornerShape(23.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = .24f)),
                            shadowElevation = 14.dp
                        ) {
                            if (!authenticated) {
                                PopupUnlock(
                                    security = security,
                                    onUnlocked = { authenticated = true },
                                    onBiometric = { requestBiometric() },
                                    biometricAvailable = canUseBiometric()
                                )
                            } else {
                                ReviewPopupContent(reviewId) { finish() }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT)
        window.setGravity(Gravity.CENTER)
    }

    override fun onResume() {
        super.onResume()
        if (::security.isInitialized && !authenticated && !biometricAttempted) {
            biometricAttempted = true
            window.decorView.post { if (!isFinishing && !authenticated) requestBiometric() }
        }
    }

    private fun canUseBiometric() = security.biometricsEnabled() &&
        BiometricManager.from(this).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS

    private fun requestBiometric() {
        if (!canUseBiometric()) return
        try {
            val prompt = BiometricPrompt(this,
                androidx.core.content.ContextCompat.getMainExecutor(this),
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        super.onAuthenticationSucceeded(result)
                        security.clearFailures()
                        authenticated = true
                    }
                })
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle("تأیید ورود به جسی")
                .setSubtitle("بررسی پیامک بانکی")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                .setNegativeButtonText("ورود با پین")
                .build())
        } catch (_: Exception) {
            // The same PIN verification remains available in the popup.
        }
    }
}

@Composable
private fun PopupUnlock(
    security: PinSecurity,
    onUnlocked: () -> Unit,
    onBiometric: () -> Unit,
    biometricAvailable: Boolean
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp))
        Text("جسی • ورود امن", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = pin,
            onValueChange = { value ->
                pin = value.filter { it in '0'..'9' }.take(6)
                error = null
            },
            placeholder = { Text("پین ورود") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error,
            fontSize = 11.sp)
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                enabled = pin.length in 4..6,
                onClick = {
                    val problem = security.verify(pin)
                    if (problem == null) {
                        pin = ""
                        onUnlocked()
                    } else {
                        error = problem
                        pin = ""
                    }
                }
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null,
                    modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(5.dp))
                Text("ورود")
            }
            if (biometricAvailable) {
                OutlinedIconButton(onClick = onBiometric) {
                    Icon(Icons.Outlined.Fingerprint, contentDescription = "اثر انگشت")
                }
            }
        }
    }
}

@Composable
private fun PopupChoice(
    label: String, selected: String, options: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("$label: $selected", modifier = Modifier.weight(1f),
                maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            Icon(Icons.Outlined.ExpandMore, contentDescription = "انتخاب",
                modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, title) ->
                DropdownMenuItem(
                    text = { Text(title, maxLines = 1, fontSize = 13.sp) },
                    onClick = { onSelected(value); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun ReviewPopupContent(reviewId: Long, dismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember(reviewId) { mutableStateOf(true) }
    var loadError by remember(reviewId) { mutableStateOf(false) }
    var row by remember(reviewId) { mutableStateOf<SmsReview?>(null) }
    var accounts by remember(reviewId) { mutableStateOf<List<Account>>(emptyList()) }
    LaunchedEffect(reviewId) {
        try {
            val data = withContext(Dispatchers.IO) {
                val dao = FinanceDatabase.get(context).dao()
                dao.findReview(reviewId) to dao.getAccounts()
            }
            row = data.first
            accounts = data.second
        } catch (_: Exception) {
            loadError = true
        } finally {
            loading = false
        }
    }

    Column(
        Modifier.padding(17.dp).heightIn(max = 580.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("بررسی تراکنش", fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = dismiss, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Outlined.Close, contentDescription = "بستن",
                    modifier = Modifier.size(20.dp))
            }
        }

        when {
            loading -> CircularProgressIndicator(modifier = Modifier.size(26.dp))
            loadError -> {
                Text("خطا در خواندن تراکنش. دوباره تلاش کن.",
                    color = MaterialTheme.colorScheme.error)
                TextButton(onClick = dismiss) { Text("بستن") }
            }
            row == null -> {
                Text("این پیامک قبلاً ثبت یا نادیده گرفته شده است.", fontSize = 12.sp)
                TextButton(onClick = dismiss) { Text("بستن") }
            }
            else -> PopupReviewForm(row!!, accounts, dismiss)
        }
    }
}

@Composable
private fun PopupReviewForm(
    row: SmsReview, accounts: List<Account>, dismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val isSimulation = SmsReviewPolicy.isSimulation(row.fingerprint)
    val isIncome = row.kind == "IN"
    val rawAmount = row.amountRaw.takeIf { it > 0L } ?: row.amountToman
    val needUnit = row.amountUnit == "UNKNOWN"
    var unit by remember(row.id) { mutableStateOf(if (needUnit) "" else row.amountUnit) }
    var accountId by remember(row.id) {
        mutableStateOf(row.accountGuess?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull()?.id)
    }
    var category by remember(row.id) {
        mutableStateOf(if (isIncome) "درآمد" else "سایر")
    }
    var note by remember(row.id) { mutableStateOf("") }
    var testConsent by remember(row.id) { mutableStateOf(false) }
    var saving by remember(row.id) { mutableStateOf(false) }
    var error by remember(row.id) { mutableStateOf<String?>(null) }

    val amount = when (unit) {
        "TOMAN" -> rawAmount
        "RIAL" -> if (rawAmount % 10L == 0L) rawAmount / 10L else 0L
        else -> 0L
    }
    val accent = if (isIncome) Color(0xFF00B992) else Color(0xFFFFAA55)

    Surface(
        color = accent.copy(alpha = .10f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(if (isIncome) "واریز" else "برداشت",
                color = accent, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(if (needUnit && unit.isBlank())
                digitsFa(NumberFormat.getIntegerInstance(Locale.US).format(rawAmount)) +
                    " • واحد نامشخص"
                else toman(amount),
                fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text((row.bankName.ifBlank { row.sender }),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
    }

    if (isSimulation) {
        Text("نمونه آزمایشی؛ ثبت آن موجودی و آمار را تغییر می‌دهد.",
            color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
    }
    if (needUnit) {
        Text("واحد پیامک مشخص نیست:", fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = unit == "RIAL", onClick = { unit = "RIAL" },
                label = { Text("ریال") })
            FilterChip(selected = unit == "TOMAN", onClick = { unit = "TOMAN" },
                label = { Text("تومان") })
        }
    }
    if (accounts.isEmpty()) {
        Text("برای ثبت، ابتدا در جسی یک حساب بساز.",
            color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
    } else {
        PopupChoice(
            "حساب",
            accounts.firstOrNull { it.id == accountId }?.title ?: "انتخاب",
            accounts.map {
                it.id.toString() to
                    listOf(it.title, it.bank,
                        it.last4.takeIf(String::isNotBlank)?.let { last -> "•••• $last" } ?: "")
                        .filter(String::isNotBlank).joinToString(" • ")
            },
            onSelected = { accountId = it.toLongOrNull() }
        )
    }
    PopupChoice(
        "دسته‌بندی", category,
        (if (isIncome) incomeCategories else categories).map { it to it },
        onSelected = { category = it }
    )
    OutlinedTextField(
        value = note, onValueChange = { note = it.take(250) },
        label = { Text("توضیح (اختیاری)", fontSize = 12.sp) },
        modifier = Modifier.fillMaxWidth(), singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )

    if (isSimulation) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = testConsent, onCheckedChange = { testConsent = it })
            Text("ثبت تراکنش آزمایشی را تأیید می‌کنم.", fontSize = 11.sp)
        }
    } else {
        Text("پیش از ثبت، مبلغ و حساب را با پیامک اصلی تطبیق بده.",
            fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (error != null) {
        Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
    }

    Row(verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(
            onClick = {
                if (!saving) {
                    saving = true
                    scope.launch {
                        val ok = try {
                            withContext(Dispatchers.IO) {
                                FinanceDatabase.get(context).dao().ignoreReview(row.id)
                            }
                        } catch (_: Exception) { false }
                        if (ok) {
                            SmsReviewNotifications.cancel(context, row.id)
                            dismiss()
                        } else {
                            error = "نادیده گرفتن انجام نشد."
                            saving = false
                        }
                    }
                }
            },
            enabled = !saving
        ) { Text("نادیده گرفتن", fontSize = 11.sp) }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                val id = accountId ?: return@Button
                saving = true
                scope.launch {
                    val saved = try {
                        withContext(Dispatchers.IO) {
                            FinanceDatabase.get(context).dao().approveReview(
                                row.id, id, category, note, unit
                            )
                        }
                    } catch (_: Exception) { false }
                    if (saved) {
                        SmsReviewNotifications.cancel(context, row.id)
                        dismiss()
                    } else {
                        error = "ثبت انجام نشد؛ مبلغ و حساب را بررسی کن."
                        saving = false
                    }
                }
            },
            enabled = !saving && accountId != null && amount > 0L &&
                (!isSimulation || testConsent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(if (saving) "..." else "ثبت تراکنش", fontSize = 12.sp)
        }
    }
}
