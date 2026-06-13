package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = BentoBgDark,
    surface = BentoSurfaceDark,
    primaryContainer = BentoPrimaryContainerDark,
    onPrimaryContainer = BentoOnPrimaryContainerDark,
    secondaryContainer = BentoSecondaryContainerDark,
    tertiaryContainer = BentoTertiaryContainerDark,
    onTertiaryContainer = BentoOnTertiaryContainerDark,
    outline = BentoBorderDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = BentoBgLight,
    surface = BentoSurfaceLight,
    primaryContainer = BentoPrimaryContainerLight,
    onPrimaryContainer = BentoOnPrimaryContainerLight,
    secondaryContainer = BentoSecondaryContainerLight,
    tertiaryContainer = BentoTertiaryContainerLight,
    onTertiaryContainer = BentoOnTertiaryContainerLight,
    outline = BentoBorderLight
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color by default to preserve custom Bento Theme design guidelines
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
