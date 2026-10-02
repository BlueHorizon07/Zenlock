package com.zenlock.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * A palette is described by seven decisions; everything else (outlines, muted text, dividers)
 * is derived by mixing ink into the background. That keeps contrast consistent across every
 * theme instead of relying on a hand-picked grey per palette that drifts out of step.
 */
private data class Tones(
    val accent: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val background: Color,
    val card: Color,
    val ink: Color,
)

private fun Tones.scheme(dark: Boolean): ColorScheme {
    val muted = lerp(ink, background, 0.32f)
    val outline = lerp(ink, background, 0.52f)
    val hairline = lerp(ink, background, 0.82f)
    val error = if (dark) Color(0xFFF2B8B5) else Color(0xFFB3261E)
    val onError = if (dark) Color(0xFF601410) else Color(0xFFFFFFFF)

    val base = if (dark) {
        darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accentContainer,
            onSecondaryContainer = onAccentContainer,
            background = background,
            onBackground = ink,
            surface = background,
            onSurface = ink,
            surfaceVariant = card,
            onSurfaceVariant = muted,
            outline = outline,
            outlineVariant = hairline,
            error = error,
            onError = onError,
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = accentContainer,
            onPrimaryContainer = onAccentContainer,
            secondary = accent,
            onSecondary = onAccent,
            secondaryContainer = accentContainer,
            onSecondaryContainer = onAccentContainer,
            background = background,
            onBackground = ink,
            surface = background,
            onSurface = ink,
            surfaceVariant = card,
            onSurfaceVariant = muted,
            outline = outline,
            outlineVariant = hairline,
            error = error,
            onError = onError,
        )
    }
    return base
}

/** The palettes offered in Screen > Appearance. */
enum class ZenPalette(val label: String) {
    Sage("Sage"),
    Clay("Clay"),
    Honey("Honey"),
    Dusk("Dusk"),
    Ink("Ink"),
    ;

    private val tones: Pair<Tones, Tones>
        get() = when (this) {
            Sage -> Tones(
                accent = Color(0xFF3F6F5C),
                onAccent = Color(0xFFFFFFFF),
                accentContainer = Color(0xFFD3E8DD),
                onAccentContainer = Color(0xFF14352A),
                background = Color(0xFFFAF9F5),
                card = Color(0xFFECF1EC),
                ink = Color(0xFF1A211E),
            ) to Tones(
                accent = Color(0xFF8FD6B8),
                onAccent = Color(0xFF06291D),
                accentContainer = Color(0xFF21453A),
                onAccentContainer = Color(0xFFC6EEDC),
                background = Color(0xFF0E1412),
                card = Color(0xFF19221F),
                ink = Color(0xFFE3E8E4),
            )

            Clay -> Tones(
                accent = Color(0xFFB0553B),
                onAccent = Color(0xFFFFFFFF),
                accentContainer = Color(0xFFF7DED2),
                onAccentContainer = Color(0xFF45180C),
                background = Color(0xFFFDF8F4),
                card = Color(0xFFF3E9E2),
                ink = Color(0xFF241A15),
            ) to Tones(
                accent = Color(0xFFF0A187),
                onAccent = Color(0xFF3A1206),
                accentContainer = Color(0xFF5A2617),
                onAccentContainer = Color(0xFFFFD9CB),
                background = Color(0xFF141010),
                card = Color(0xFF221A17),
                ink = Color(0xFFECE3DE),
            )

            Honey -> Tones(
                accent = Color(0xFF8A6320),
                onAccent = Color(0xFFFFFFFF),
                accentContainer = Color(0xFFF6E3BE),
                onAccentContainer = Color(0xFF3A2705),
                background = Color(0xFFFDFAF3),
                card = Color(0xFFF2EBDC),
                ink = Color(0xFF241F16),
            ) to Tones(
                accent = Color(0xFFEBC077),
                onAccent = Color(0xFF3B2A05),
                accentContainer = Color(0xFF4C380F),
                onAccentContainer = Color(0xFFFFE2B0),
                background = Color(0xFF13110C),
                card = Color(0xFF201C15),
                ink = Color(0xFFEBE5D9),
            )

            Dusk -> Tones(
                accent = Color(0xFF5B5A9D),
                onAccent = Color(0xFFFFFFFF),
                accentContainer = Color(0xFFE0DFF6),
                onAccentContainer = Color(0xFF1F1E4A),
                background = Color(0xFFFAF9FD),
                card = Color(0xFFEDECF6),
                ink = Color(0xFF1C1B26),
            ) to Tones(
                accent = Color(0xFFB4B2F0),
                onAccent = Color(0xFF22214F),
                accentContainer = Color(0xFF33326B),
                onAccentContainer = Color(0xFFE2E1FF),
                background = Color(0xFF100F16),
                card = Color(0xFF1C1B25),
                ink = Color(0xFFE5E4EC),
            )

            Ink -> Tones(
                accent = Color(0xFF1C1C1C),
                onAccent = Color(0xFFFFFFFF),
                accentContainer = Color(0xFFE4E4E4),
                onAccentContainer = Color(0xFF141414),
                background = Color(0xFFFBFBFB),
                card = Color(0xFFF0F0F0),
                ink = Color(0xFF121212),
            ) to Tones(
                accent = Color(0xFFEDEDED),
                onAccent = Color(0xFF101010),
                accentContainer = Color(0xFF2A2A2A),
                onAccentContainer = Color(0xFFEDEDED),
                background = Color(0xFF0B0B0B),
                card = Color(0xFF181818),
                ink = Color(0xFFF2F2F2),
            )
        }

    fun colorScheme(dark: Boolean): ColorScheme =
        if (dark) tones.second.scheme(dark = true) else tones.first.scheme(dark = false)

    /** The two dots shown in the theme picker, so a palette is judged by its own colours. */
    fun swatch(dark: Boolean): Pair<Color, Color> {
        val t = if (dark) tones.second else tones.first
        return t.accent to t.accentContainer
    }

    companion object {
        fun from(name: String?): ZenPalette =
            entries.firstOrNull { it.name == name } ?: Sage
    }
}

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
    ;

    companion object {
        fun from(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: System
    }
}
