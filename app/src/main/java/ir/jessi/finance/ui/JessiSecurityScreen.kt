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
        label = { Text(title, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
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
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 370.dp),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = .76f),
            border = androidx.compose.foundation.BorderStroke(
                1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .25f)
            )
        ) {
            Column(
                Modifier.padding(horizontal = 17.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = .12f),
                    shape = RoundedCornerShape(17.dp)
                ) {
                    Icon(
                        Icons.Outlined.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(11.dp).size(27.dp)
                    )
                }
                Text("جسی", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp,
                    color = jessiReadableText(MaterialTheme.colorScheme.onSurface))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { value ->
                        pin = value.filter { it in '0'..'9' }.take(6)
                        error = null
                    },
                    singleLine = true,
                    placeholder = { Text("پین ورود", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Security, contentDescription = null,
                            modifier = Modifier.size(20.dp))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error,
                        fontSize = 11.sp)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Button(
                        onClick = {
                            val result = security.verify(pin)
                            if (result == null) { pin = ""; onUnlocked() }
                            else { error = result; pin = "" }
                        },
                        enabled = pin.length in 4..6,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.Lock, contentDescription = null,
                            modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("ورود")
                    }
                    if (biometricAvailable && security.biometricsEnabled()) {
                        OutlinedIconButton(
                            onClick = onTryBiometrics,
                            modifier = Modifier.size(43.dp)
                        ) {
                            Icon(Icons.Outlined.Fingerprint,
                                contentDescription = "ورود با اثرانگشت",
                                modifier = Modifier.size(23.dp))
                        }
                    }
                }
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
        Text("قفل ورود به برنامه", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
        Text("وقتی فعال باشد، بعد از بازکردن جسی ابتدا تأیید بیومتریک و در صورت نیاز پین درخواست می‌شود.",
            color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant), fontSize = 12.sp)
        if (!enabled) {
            Button(onClick = { action = "enable" }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Security, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(8.dp))
                Text("فعال‌کردن قفل با پین", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
        } else {
            Text("قفل ورود فعال است", color = jessiReadableText(MaterialTheme.colorScheme.primary), fontSize = 13.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Fingerprint, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ورود با اثرانگشت", fontSize = 13.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                    Text(
                        if (biometricAvailable) "اول اثرانگشت، در صورت انصراف یا خطا پین"
                        else "اثر انگشت در تنظیمات گوشی فعال نیست؛ پین همیشه کار می‌کند",
                        color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant), fontSize = 10.sp
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
                Text("تغییر پین", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
            TextButton(onClick = { action = "disable" }) {
                Text("غیرفعال‌کردن قفل (با پین فعلی)", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
            }
        }
        Text("پین رمزگذاریِ یک‌طرفه و با نمک تصادفی ذخیره می‌شود. پین در فایل پشتیبان مالی ذخیره نمی‌شود.",
            fontSize = 11.sp, color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant))
    }

    if (action != null) {
        AlertDialog(
            onDismissRequest = { action = null },
            title = { Text(when (action) {
                "enable" -> "ساخت پین ورود"
                "change" -> "تغییر پین ورود"
                else -> "غیرفعال‌کردن قفل"
            }, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (action != "enable") PinInput("پین فعلی", oldPin, { oldPin = it; error = null })
                    if (action != "disable") {
                        PinInput("پین جدید", newPin, { newPin = it; error = null })
                        PinInput("تکرار پین جدید", confirmation,
                            { confirmation = it; error = null })
                    }
                    if (error != null) Text(error!!,
                        fontSize = 12.sp, color = jessiReadableText(MaterialTheme.colorScheme.error))
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
                ) { Text("تأیید", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
            },
            dismissButton = {
                TextButton(onClick = { action = null }) { Text("انصراف", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
            }
        )
    }
}
