package com.pft.financetracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
 * Just enough Markdown for model answers: `**bold**` spans, headings, `- ` / `* ` bullets, numbered lists and blank
 * lines between paragraphs. Language models write in Markdown whatever the prompt asks, so the marks have to be
 * rendered rather than shown raw. Anything else is left as plain text.
 */
private val boldRx = Regex("""\*\*(.+?)\*\*|__(.+?)__""")
private val headingRx = Regex("""^#{1,6}\s+(.*)$""")
private val numberedRx = Regex("""^(\d{1,2})[.)]\s+(.*)$""")

fun markdownInline(text: String): AnnotatedString = buildAnnotatedString {
    val plain = text.replace("`", "")
    var cursor = 0
    for (m in boldRx.findAll(plain)) {
        append(plain.substring(cursor, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1].ifEmpty { m.groupValues[2] }) }
        cursor = m.range.last + 1
    }
    if (cursor < plain.length) append(plain.substring(cursor))
}

/** One line of a model's answer, classified for display. */
internal sealed class MdLine {
    data class Heading(val text: String) : MdLine()
    data class Bullet(val text: String) : MdLine()
    data class Numbered(val number: String, val text: String) : MdLine()
    data class Para(val text: String) : MdLine()
}

internal fun markdownLines(text: String): List<MdLine> = text.replace("\r\n", "\n").split('\n').mapNotNull { raw ->
    val line = raw.trim()
    when {
        line.isEmpty() || line.all { it == '-' || it == '*' || it == '_' } -> null
        headingRx.matches(line) -> MdLine.Heading(headingRx.find(line)!!.groupValues[1].trim('*', ' '))
        line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ") -> MdLine.Bullet(line.substring(2).trim())
        numberedRx.matches(line) -> numberedRx.find(line)!!.groupValues.let { MdLine.Numbered(it[1], it[2]) }
        else -> MdLine.Para(line)
    }
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val body = MaterialTheme.typography.bodyLarge
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (l in markdownLines(text)) when (l) {
            is MdLine.Heading -> Text(markdownInline(l.text), style = MaterialTheme.typography.titleSmall)
            is MdLine.Bullet -> Row {
                Text("•", style = body, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(markdownInline(l.text), style = body)
            }
            is MdLine.Numbered -> Row {
                Text("${l.number}.", Modifier.widthIn(min = 18.dp), style = body, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(markdownInline(l.text), style = body)
            }
            is MdLine.Para -> Text(markdownInline(l.text), style = body)
        }
    }
}
