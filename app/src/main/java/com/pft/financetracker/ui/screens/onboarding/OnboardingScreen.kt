package com.pft.financetracker.ui.screens.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.PrimaryPill

@Composable
fun OnboardingScreen(vm: AppViewModel, onDone: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val granted = result[Manifest.permission.READ_SMS] == true
        vm.setOnboarded(true)
        if (granted) vm.scanInbox(full = true)
        onDone()
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text("Your money, on your phone only", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "FinTrack reads bank, UPI and card alert SMS to log transactions automatically. Everything stays in an encrypted database on this device.",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(20.dp))
        FinCard {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Why SMS permission?", style = MaterialTheme.typography.titleMedium)
                Bullet("Reads only alphanumeric sender IDs (like VM-HDFCBK). Personal messages from phone numbers are skipped.")
                Bullet("Stores only the parsed amount, merchant, date and last-4 account digits. Raw SMS text is not kept, except messages you choose to review.")
                Bullet("No internet is used for SMS processing. No analytics, no tracking, no cloud.")
                Bullet("You can revoke the permission any time; the app keeps working with manual entry.")
            }
        }
        Spacer(Modifier.height(24.dp))
        PrimaryPill("Allow SMS access & import", onClick = { launcher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) })
        TextButton(onClick = { vm.setOnboarded(true); onDone() }, modifier = Modifier.fillMaxWidth()) {
            Text("Skip, I will add transactions manually")
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
