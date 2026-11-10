package ir.jessi.finance.ui

import androidx.biometric.BiometricManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.jessi.finance.security.PinSecurity

@Composable
private fun PinInput(
    title: String, value: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { digit -> digit in '0'..'9' }.take(6)) },
        label = { Text(title) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
    )
}

@Composable
fun JessiLockScreen(
    security: PinSecurity,
    onUnlocked: () -> Unit,
    biometricAvailable: Boolean,
    onTryBiometrics: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Box(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .90f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .30f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(38.dp))
                Text("ورود امن به جسی", fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
                Text("ابتدا اثرانگشت را تأیید کن، یا با پین وارد شو",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp)
                PinInput("پین ورود (۴ تا ۶ رقم)", pin, { pin = it; error = null })
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                Button(
                    onClick = {
                        val result = security.verify(pin)
                        if (result == null) { pin = ""; onUnlocked() }
                        else { error = result; pin = "" }
                    },
                    enabled = pin.length in 4..6,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("ورود با پین") }
                if (biometricAvailable && security.biometricsEnabled()) {
                    OutlinedButton(
                        onClick = onTryBiometrics, modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.Fingerprint, contentDescription = null,
                            modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("تلاش دوباره با اثرانگشت")
                    }
                }
                Text("برای امنیت اطلاعات مالی، بعد از خروج از برنامه دوباره قفل فعال می‌شود.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp)
            }
        }
    }
}

@Composable
fun JessiSecuritySettings(
    security: PinSecurity,
    onChanged: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(security.isEnabled()) }
    var biometricEnabled by remember { mutableStateOf(security.biometricsEnabled()) }
    val biometricAvailable = remember {
        BiometricManager.from(context).canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }
    var action by remember { mutableStateOf<String?>(null) }
    var oldPin by remember(action) { mutableStateOf("") }
    var newPin by remember(action) { mutableStateOf("") }
    var confirmation by remember(action) { mutableStateOf("") }
    var error by remember(action) { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("قفل ورود به برنامه", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("وقتی فعال باشد، بعد از بازکردن جسی ابتدا تأیید بیومتریک و در صورت نیاز پین درخواست می‌شود.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        if (!enabled) {
            Button(onClick = { action = "enable" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("فعال‌کردن قفل با پین")
            }
        } else {
            Text("قفل ورود فعال است", color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ورود با اثرانگشت", fontSize = 13.sp)
                    Text(
                        if (biometricAvailable) "اول اثرانگشت، در صورت انصراف یا خطا پین"
                        else "اثر انگشت در تنظیمات گوشی فعال نیست؛ پین همیشه کار می‌کند",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp
                    )
                }
                Switch(
                    checked = biometricEnabled,
                    onCheckedChange = {
                        biometricEnabled = it
                        security.setBiometricsEnabled(it)
                    }
                )
            }
            OutlinedButton(onClick = { action = "change" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.LockReset, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("تغییر پین")
            }
            TextButton(onClick = { action = "disable" }) {
                Text("غیرفعال‌کردن قفل (با پین فعلی)")
            }
        }
        Text("پین رمزگذاریِ یک‌طرفه و با نمک تصادفی ذخیره می‌شود. پین در فایل پشتیبان مالی ذخیره نمی‌شود.",
            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    if (action != null) {
        AlertDialog(
            onDismissRequest = { action = null },
            title = { Text(when (action) {
                "enable" -> "ساخت پین ورود"
                "change" -> "تغییر پین ورود"
                else -> "غیرفعال‌کردن قفل"
            }) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (action != "enable") PinInput("پین فعلی", oldPin, { oldPin = it; error = null })
                    if (action != "disable") {
                        PinInput("پین جدید", newPin, { newPin = it; error = null })
                        PinInput("تکرار پین جدید", confirmation,
                            { confirmation = it; error = null })
                    }
                    if (error != null) Text(error!!,
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val problem = when {
                            action != "disable" && !security.isValidPin(newPin) ->
                                "پین باید بین ۴ تا ۶ رقم باشد"
                            action != "disable" && newPin != confirmation ->
                                "تکرار پین با پین جدید مطابقت ندارد"
                            action == "enable" -> {
                                security.enable(newPin)
                                null
                            }
                            action == "change" -> security.change(oldPin, newPin)
                            else -> security.disable(oldPin)
                        }
                        if (problem != null) error = problem
                        else {
                            enabled = security.isEnabled()
                            biometricEnabled = security.biometricsEnabled()
                            action = null
                            onChanged()
                        }
                    }
                ) { Text("تأیید") }
            },
            dismissButton = {
                TextButton(onClick = { action = null }) { Text("انصراف") }
            }
        )
    }
}
