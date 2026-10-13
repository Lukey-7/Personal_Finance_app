package com.pft.financetracker.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.ai.HttpJson
import com.pft.financetracker.domain.ai.ProviderKind
import com.pft.financetracker.ui.AiTestState
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.AdaptiveRow
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.LearnMore
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Settings › AI: which provider answers (OpenAI, Claude, Gemini, Bedrock, Groq, OpenRouter, Mistral, DeepSeek, a local
 * server or any OpenAI-compatible one), its model, its address where that can change, and its key. Each provider keeps
 * its own key, so switching back needs no retyping. "Test connection" sends one tiny request.
 */
@Composable
internal fun AiProviderGroup(vm: AppViewModel) {
    val config by vm.aiConfig.collectAsState()
    val keyBuiltIn by vm.apiKeyBuiltIn.collectAsState()
    val test by vm.aiTest.collectAsState()
    val kind = config.kind
    var picking by remember { mutableStateOf(false) }
    val pickerState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Drafts of the fields; they start again from the saved values when the provider changes.
    var model by remember(kind) { mutableStateOf(config.model) }
    var url by remember(kind) { mutableStateOf(config.baseUrl) }
    var region by remember(kind) { mutableStateOf(config.region) }
    var key by remember(kind) { mutableStateOf("") }
    val haptics = rememberHaptics()
    val keySaved = config.apiKey != null
    val urlOk = !kind.editableUrl || HttpJson.allowed(url)
    val changed = model.trim() != config.model || url.trim() != config.baseUrl || region.trim() != config.region || key.isNotBlank()

    SettingsGroup("AI provider (optional)") {
        LearnMore(
            "Pick who answers Ask, checks unclear splits and writes the month summary. Nothing is sent until one is set up.",
            "AI providers",
            "FinTrack works without AI. With a provider set up, Ask, split intelligence and the monthly summary send what " +
                "their settings below say, to that provider only, with your own key. Keys are encrypted with Android " +
                "Keystore and only ever go to the provider they belong to.\n\nOpen models such as Llama, Qwen, Gemma and " +
                "gpt-oss run through Groq, OpenRouter, AWS Bedrock or DeepSeek, or on a server on this phone (Ollama or " +
                "llama.cpp in Termux), which keeps everything on the phone. \"Other\" takes any OpenAI-compatible https address.",
        )
        ActionRow(kind.label, Icons.Outlined.Hub, { picking = true }, subtitle = "Provider · tap to change")
        OutlinedTextField(
            model, { model = it }, label = { Text("Model") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = kind.defaultModel.takeIf { it.isNotBlank() }?.let { { Text("Suggested: $it") } },
        )
        if (kind.editableUrl) OutlinedTextField(
            url, { url = it }, label = { Text("Server address") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            isError = url.isNotBlank() && !urlOk,
            supportingText = { Text("https, or http://localhost for a server on this phone") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        if (kind == ProviderKind.BEDROCK) OutlinedTextField(
            region, { region = it }, label = { Text("AWS region") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("For example ap-south-1 (Mumbai) or us-east-1") },
        )
        if (keySaved) Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.sm))
            Note(
                if (kind == ProviderKind.OPENAI && keyBuiltIn) "Using the key built into this app, encrypted with Android Keystore"
                else "Key saved, encrypted with Android Keystore",
            )
        } else if (kind.needsKey) Note("No key yet. It stays encrypted on this phone.")
        KeyField(key, "${if (keySaved) "New key" else "Key"} (${kind.keyHint})") { key = it }
        AdaptiveRow(count = 2, minItemWidth = 140.dp) { m ->
            PrimaryButton("Save", {
                haptics.confirm()
                vm.saveAiSettings(model, url, region, key.takeIf { it.isNotBlank() })
                key = ""
            }, m, enabled = changed && urlOk && (key.isBlank() || key.trim().length >= 8))
            SecondaryButton("Test connection", { vm.testAiConnection() }, m, enabled = test !is AiTestState.Running && !changed)
        }
        when (val t = test) {
            AiTestState.Running -> BusyLine("Asking ${kind.label}…")
            is AiTestState.Ok -> Note("Connected. ${kind.label} replied “${t.reply}”.")
            is AiTestState.Failed -> Text("Couldn't connect. ${t.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            AiTestState.Idle -> {}
        }
        if (keySaved) ActionRow("Remove ${kind.label} key", Icons.Outlined.KeyOff, { vm.setApiKey(null); vm.clearAi() })
    }

    if (picking) FinSheet({ picking = false }, title = "AI provider", state = pickerState) {
        Column {
            ProviderKind.entries.forEach { k ->
                Row(
                    Modifier.fillMaxWidth().selectable(selected = k == kind, role = Role.RadioButton, onClick = {
                        vm.setAiProvider(k)
                        // Slide the sheet away before it leaves the screen, so its window closes the usual way.
                        scope.launch { pickerState.hide() }.invokeOnCompletion { picking = false }
                    })
                        .padding(vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = k == kind, onClick = null)
                    Spacer(Modifier.width(Space.sm))
                    Column(Modifier.weight(1f)) {
                        Text(k.label, style = MaterialTheme.typography.bodyLarge)
                        Text(blurb(k), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One line under each provider in the picker. */
private fun blurb(k: ProviderKind) = when (k) {
    ProviderKind.OPENAI -> "ChatGPT models"
    ProviderKind.ANTHROPIC -> "Claude models"
    ProviderKind.GEMINI -> "Gemini models, with a Google AI Studio key"
    ProviderKind.BEDROCK -> "Llama, Claude, Mistral, Nova and more on AWS, with a Bedrock API key"
    ProviderKind.GROQ -> "Fast open models: Llama, Qwen, gpt-oss"
    ProviderKind.OPENROUTER -> "Hundreds of open and closed models with one key"
    ProviderKind.MISTRAL -> "Mistral's own models"
    ProviderKind.DEEPSEEK -> "DeepSeek's own models"
    ProviderKind.LOCAL -> "A server on this phone: nothing leaves it"
    ProviderKind.CUSTOM -> "Any OpenAI-compatible https address"
}

@Composable
internal fun KeyField(value: String, label: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}
