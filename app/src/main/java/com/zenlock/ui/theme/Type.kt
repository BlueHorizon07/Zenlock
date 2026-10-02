@file:OptIn(ExperimentalTextApi::class)

package com.zenlock.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.zenlock.R

/**
 * Nunito, shipped as a single variable font and instanced per weight. Rounded terminals and a
 * tall x-height are what make the numbers read warm rather than clinical, which matters most on
 * the two screens people actually stare at: the daily total and the pause countdown.
 */
private val Nunito = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Light, variationSettings = weightAxis(300)),
    Font(R.font.nunito_variable, FontWeight.Normal, variationSettings = weightAxis(400)),
    Font(R.font.nunito_variable, FontWeight.Medium, variationSettings = weightAxis(500)),
    Font(R.font.nunito_variable, FontWeight.SemiBold, variationSettings = weightAxis(600)),
    Font(R.font.nunito_variable, FontWeight.Bold, variationSettings = weightAxis(700)),
)

private fun weightAxis(weight: Int) = FontVariation.Settings(FontVariation.weight(weight))

/** Text sits optically centred in its line box rather than riding high. */
private val Snug = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun cozy(
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    tracking: Double = 0.0,
) = TextStyle(
    fontFamily = Nunito,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = Snug,
)

val ZenTypography = Typography(
    // The daily total and the countdown. Light and generously tracked-in so a big number
    // feels calm instead of shouty.
    displayLarge = cozy(56, 62, FontWeight.Light, -1.5),
    displayMedium = cozy(46, 54, FontWeight.Light, -1.0),
    displaySmall = cozy(38, 46, FontWeight.Light, -0.8),

    headlineLarge = cozy(30, 38, FontWeight.SemiBold, -0.4),
    headlineMedium = cozy(25, 32, FontWeight.SemiBold, -0.3),
    headlineSmall = cozy(21, 28, FontWeight.SemiBold, -0.2),

    titleLarge = cozy(19, 26, FontWeight.SemiBold),
    titleMedium = cozy(16, 22, FontWeight.SemiBold),
    titleSmall = cozy(14, 20, FontWeight.Medium),

    bodyLarge = cozy(16, 24),
    bodyMedium = cozy(14, 21),
    bodySmall = cozy(13, 19),

    // Section headers are set in caps, so they need the tracking opened up to stay readable.
    labelLarge = cozy(15, 20, FontWeight.SemiBold),
    labelMedium = cozy(12, 16, FontWeight.SemiBold, 0.9),
    labelSmall = cozy(11, 15, FontWeight.Medium, 0.6),
)
