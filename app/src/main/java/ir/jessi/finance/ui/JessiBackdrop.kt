package ir.jessi.finance.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A locally persisted visual style, independent of financial backup data. */
val LocalJessiBackdrop = compositionLocalOf { "aurora" }

private fun palette(style: String, dark: Boolean): List<Color> = when(style) {
    "ocean" -> if (dark) listOf(Color(0xFF081E32), Color(0xFF104454), Color(0xFF12223F))
        else listOf(Color(0xFFDAEFF9), Color(0xFFC0E9EF), Color(0xFFE9F8FB))
    "sunset" -> if (dark) listOf(Color(0xFF20152D), Color(0xFF432444), Color(0xFF29253D))
        else listOf(Color(0xFFF8EBF6), Color(0xFFFBE5D7), Color(0xFFF7F3F3))
    "minimal" -> if (dark) listOf(Color(0xFF11152A), Color(0xFF171C32))
        else listOf(Color(0xFFF7F9FB), Color(0xFFEAF3F5))
    else -> if (dark) listOf(Color(0xFF0C1535), Color(0xFF251943), Color(0xFF0C3440))
        else listOf(Color(0xFFF0ECFD), Color(0xFFE5F7F8), Color(0xFFF1F6FD))
}

@Composable
fun JessiBackdrop(
    style: String,
    photoUri: String,
    dim: Float,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Decode a downsampled bitmap only when the selected URI changes.
    val image = remember(photoUri) {
        if (photoUri.isBlank()) null else runCatching {
            val uri = Uri.parse(photoUri)
            val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
            }
        }.getOrNull()
    }
    val colors = palette(style, dark)
    Box(modifier.background(Brush.verticalGradient(colors))) {
        if (style == "photo" && image != null) {
            Image(
                bitmap = image, contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else if (style != "minimal") {
            Canvas(Modifier.fillMaxSize()) {
                val span = maxOf(size.width, size.height)
                val a = Offset(size.width * .12f, size.height * .22f)
                val b = Offset(size.width * .88f, size.height * .75f)
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            if (dark) Color(0x6636D7FF) else Color(0x554EA4F0),
                            Color.Transparent
                        ), center = a, radius = span * .72f
                    ), radius = span * .72f, center = a
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(
                            if (dark) Color(0x66CE78FF) else Color(0x44CB8BDD),
                            Color.Transparent
                        ), center = b, radius = span * .66f
                    ), radius = span * .66f, center = b
                )
            }
        }
        // Adjustable scrim retains text contrast while letting the chosen backdrop shine through.
        Box(Modifier.fillMaxSize().background(
            if (dark) Color.Black.copy(alpha = dim.coerceIn(0f, .78f))
            else Color.White.copy(alpha = dim.coerceIn(0f, .78f))
        ))
    }
}

@Composable
fun JessiBackgroundSettings(
    style: String,
    photoUri: String,
    dim: Float,
    dark: Boolean,
    onStyleChange: (String) -> Unit,
    onChooseImage: () -> Unit,
    onClearImage: () -> Unit,
    onDimChange: (Float) -> Unit
) {
    val options = listOf(
        Triple("aurora", "شفق بنفش", "هاله‌های بنفش و فیروزه‌ای"),
        Triple("ocean", "اقیانوسی", "آبی آرام و عمیق"),
        Triple("sunset", "غروب", "گرم و صورتی ملایم"),
        Triple("minimal", "ساده", "پس‌زمینه خلوت")
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("پس‌زمینه و جلوه شیشه‌ای", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
        Text("تم رنگی یا عکس دلخواهت رو انتخاب کن. کارت‌ها نیمه‌شفاف‌اند و هاله پس‌زمینه از پشتشان پیدا می‌شود.",
            color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant), fontSize = 11.sp)
        options.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { (id, title, subtitle) ->
                    val chosen = style == id
                    Surface(
                        onClick = { onStyleChange(id) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .44f),
                        border = BorderStroke(
                            if (chosen) 2.dp else 1.dp,
                            if (chosen) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline.copy(alpha = .45f)
                        )
                    ) {
                        Column(Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Box(Modifier.fillMaxWidth().height(44.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Brush.horizontalGradient(palette(id, dark))))
                            Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                            Text(subtitle, fontSize = 9.sp,
                                color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant))
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = onChooseImage, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.AddPhotoAlternate, contentDescription = null,
                modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (photoUri.isBlank()) "انتخاب عکس از گوشی" else "تغییر تصویر پس‌زمینه", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
        }
        if (photoUri.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = style == "photo",
                    onClick = { onStyleChange("photo") },
                    label = { Text("تصویر شخصی", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current)) }
                )
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onClearImage) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null,
                        modifier = Modifier.size(18.dp))
                    Text("حذف تصویر", color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
                }
            }
        }
        Text("شدت پوشش شیشه‌ای پس‌زمینه", fontSize = 12.sp, color = jessiReadableText(androidx.compose.material3.LocalContentColor.current))
        Slider(value = dim, onValueChange = onDimChange, valueRange = 0f..0.78f)
        Text("تصویر انتخابی فقط در همین گوشی استفاده می‌شود و به سرور ارسال نمی‌شود.",
            color = jessiReadableText(MaterialTheme.colorScheme.onSurfaceVariant), fontSize = 10.sp)
    }
}
