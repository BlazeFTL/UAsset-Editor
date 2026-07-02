package com.example.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

data class FilterColors(
    val comment: Color = Color(0xFF64748B),          // Slate grey (readable)
    val exception: Color = Color(0xFF16A34A),        // Dark emerald Green (distinct @@)
    val cosmeticSeparator: Color = Color(0xFFDC2626),// Crimson Red (##, #@#)
    val cosmeticSelector: Color = Color(0xFF2563EB), // Royal Blue
    val scriptlet: Color = Color(0xFF7C3AED),        // Violet Purple (##+js)
    val scriptletArgs: Color = Color(0xFF9333EA),    // Purple-magenta
    val htmlFilter: Color = Color(0xFFD97706),       // Amber Orange (##^)
    val domainList: Color = Color(0xFF0D9488),       // Teal
    val regex: Color = Color(0xFFDB2777),            // Deep Pink (/.../)
    val networkBase: Color = Color(0xFF1E293B),      // Slate charcoal for high contrast light mode
    val options: Color = Color(0xFFB45309),          // Brownish yellow/amber (very readable)
    val accent: Color = Color(0xFFC026D3)            // Fuchsia highlight for || and ^
)

fun highlightFilterLine(line: String, colors: FilterColors = FilterColors()): AnnotatedString {
    val builder = AnnotatedString.Builder()
    
    val trimmed = line.trim()
    if (trimmed.startsWith("!") || trimmed.startsWith("[Adblock")) {
        // Comment or header
        // Replace space after ! with non-breaking space to prevent ugly wrapping of comments in editor
        val renderedLine = if (line.contains("! ")) {
            val idx = line.indexOf("! ")
            line.substring(0, idx) + "!\u00A0" + line.substring(idx + 2)
        } else {
            line
        }
        builder.append(renderedLine)
        builder.addStyle(SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic), 0, renderedLine.length)
        return builder.toAnnotatedString()
    }
    
    // Exception rules @@
    if (trimmed.startsWith("@@")) {
        builder.append(line)
        builder.addStyle(SpanStyle(color = colors.exception, fontWeight = FontWeight.SemiBold), 0, line.length)
        // Highlight options inside if there is a $
        val dollarIdx = line.indexOf('$')
        if (dollarIdx != -1) {
            builder.addStyle(SpanStyle(color = colors.options, fontWeight = FontWeight.Normal), dollarIdx, line.length)
        }
        return builder.toAnnotatedString()
    }
    
    // Check for cosmetic filters ##, #@#, #?#
    // Format: [domains]##[selector]
    // Or HTML filters ##^
    var cosmeticSeparator = -1
    var cosmeticType = ""
    for (sep in listOf("##+js(", "##^", "##", "#@#", "#?#")) {
        val idx = line.indexOf(sep)
        if (idx != -1 && (cosmeticSeparator == -1 || idx < cosmeticSeparator)) {
            cosmeticSeparator = idx
            cosmeticType = sep
        }
    }
    
    if (cosmeticSeparator != -1) {
        // We have domains before, cosmetic separator, and selector/scriptlet after
        val domains = line.substring(0, cosmeticSeparator)
        val separator = line.substring(cosmeticSeparator, cosmeticSeparator + cosmeticType.length)
        val rest = line.substring(cosmeticSeparator + cosmeticType.length)
        
        // Append domains
        builder.append(domains)
        if (domains.isNotEmpty()) {
            builder.addStyle(SpanStyle(color = colors.domainList, fontWeight = FontWeight.Medium), 0, domains.length)
        }
        
        // Append separator
        val sepStart = builder.length
        builder.append(separator)
        builder.addStyle(SpanStyle(color = colors.cosmeticSeparator, fontWeight = FontWeight.Bold), sepStart, builder.length)
        
        // Append rest
        val restStart = builder.length
        builder.append(rest)
        
        if (cosmeticType == "##+js(") {
            // Scriptlet injection: ##+js(args)
            builder.addStyle(SpanStyle(color = colors.scriptlet, fontWeight = FontWeight.Medium), restStart, builder.length)
            val closingParenthesis = rest.lastIndexOf(')')
            if (closingParenthesis != -1) {
                builder.addStyle(SpanStyle(color = colors.scriptletArgs, fontStyle = FontStyle.Italic), restStart, restStart + closingParenthesis)
            }
        } else if (cosmeticType == "##^") {
            // HTML filter
            builder.addStyle(SpanStyle(color = colors.htmlFilter, fontWeight = FontWeight.Medium), restStart, builder.length)
        } else {
            // Standard cosmetic filter
            builder.addStyle(SpanStyle(color = colors.cosmeticSelector, fontWeight = FontWeight.Normal), restStart, builder.length)
        }
        return builder.toAnnotatedString()
    }
    
    // Regex filters /.../
    if (trimmed.startsWith("/") && trimmed.endsWith("/") && trimmed.length > 2) {
        builder.append(line)
        builder.addStyle(SpanStyle(color = colors.regex, fontWeight = FontWeight.Medium), 0, line.length)
        return builder.toAnnotatedString()
    }
    
    // Network filters: ||domain^ or plain lines
    builder.append(line)
    val dollarIdx = line.indexOf('$')
    if (dollarIdx != -1) {
        builder.addStyle(SpanStyle(color = colors.networkBase), 0, dollarIdx)
        builder.addStyle(SpanStyle(color = colors.options, fontWeight = FontWeight.Medium), dollarIdx, line.length)
    } else {
        builder.addStyle(SpanStyle(color = colors.networkBase), 0, line.length)
    }
    
    // Highlight || and ^ if present
    val doublePipeIdx = line.indexOf("||")
    if (doublePipeIdx != -1 && (dollarIdx == -1 || doublePipeIdx < dollarIdx)) {
        builder.addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold), doublePipeIdx, doublePipeIdx + 2)
    }
    val caretIdx = line.indexOf('^')
    if (caretIdx != -1 && (dollarIdx == -1 || caretIdx < dollarIdx)) {
        builder.addStyle(SpanStyle(color = colors.accent, fontWeight = FontWeight.Bold), caretIdx, caretIdx + 1)
    }
    
    return builder.toAnnotatedString()
}
