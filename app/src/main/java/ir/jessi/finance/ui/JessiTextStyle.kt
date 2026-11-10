package ir.jessi.finance.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import ir.jessi.finance.LocalJessiDark

/**
 * In Dark Mode, all explicitly colored text uses pure white for a unified,
 * high-contrast typography system. Decorative icons and chart fills retain their accents.
 */
@Composable
fun jessiReadableText(original: Color): Color =
    if (LocalJessiDark.current) Color.White else original
