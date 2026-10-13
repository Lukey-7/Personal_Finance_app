package com.pft.financetracker.ui.model

/**
 * Where Ask's answers come from, in the words the Tools tile shows. [online] is "an AI provider is set up and Ask
 * uses it"; [assistantName] is that provider's name for itself ("ChatGPT", "Claude", "your local model").
 */
fun askSource(online: Boolean, assistantName: String): String =
    if (online) "Answered by $assistantName" else "Answered on this phone"
