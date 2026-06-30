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

import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = Color(0xFF818CF8),
    secondary = Color(0xFF2DD4BF),
    tertiary = Color(0xFFA78BFA),
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onPrimary = Color(0xFF0F172A),
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = Color(0xFF4F46E5),        // Indigo
    secondary = Color(0xFF0D9488),      // Teal
    tertiary = Color(0xFF8B5CF6),       // Purple
    background = Color(0xFFF8FAFC),     // Off-white / light slate
    surface = Color(0xFFFFFFFF),        // Crisp white
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),   // Dark slate
    onSurface = Color(0xFF0F172A),      // Dark slate
    surfaceVariant = Color(0xFFF1F5F9),  // Light grey
    onSurfaceVariant = Color(0xFF475569),// Soft slate
    outline = Color(0xFFCBD5E1)         // Soft gray border
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = false, // Enforce light theme by default as requested
  dynamicColor: Boolean = false, // Keep colors cohesive and custom
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
