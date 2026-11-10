package ir.jessi.finance.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import ir.jessi.finance.sms.BankSmsParser

/** Canonical digits are kept in the form state; separators are display-only. */
fun normalizeNumericInput(raw: String): String =
    BankSmsParser.normalizeDigits(raw).filter { it in '0'..'9' }

fun groupThousandsForInput(raw: String): String = buildString {
    raw.forEachIndexed { index, character ->
        if (index > 0 && (raw.length - index) % 3 == 0) append(',')
        append(character)
    }
}

/** Cursor-safe grouping for Persian money-entry fields (e.g. ۱۲,۳۴۵,۶۷۸). */
object GroupedNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val original = text.text
        val formatted = groupThousandsForInput(original)
        val separatorPositions = (1 until original.length)
            .filter { (original.length - it) % 3 == 0 }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                val clamped = offset.coerceIn(0, original.length)
                return clamped + separatorPositions.count { it <= clamped }
            }
            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset.coerceIn(0, formatted.length)).count { it != ',' }
        }
        return TransformedText(AnnotatedString(digitsFa(formatted)), mapping)
    }
}
