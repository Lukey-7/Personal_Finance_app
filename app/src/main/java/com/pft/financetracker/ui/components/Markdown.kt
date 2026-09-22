package com.pft.financetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/**
 * Just enough Markdown for the model's monthly summary: `**bold**` spans, `- ` / `* ` bullets and blank
 * lines between paragraphs. Language models write in Markdown whatever the prompt asks, so the asterisks
 * have to be rendered rather than shown raw. Anything else is left as plain text.
 */
private val boldRx = Regex("""\*\*(.+?)\*\*""")

fun markdownInline(text: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    for (m in boldRx.findAll(text)) {
        append(text.substring(cursor, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
        cursor = m.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val lines = text.replace("\r\n", "\n").split('\n')
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            val bullet = line.removePrefix("- ").removePrefix("* ").removePrefix("• ")
            if (bullet !== line) {
                Row {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(markdownInline(bullet), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(markdownInline(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
