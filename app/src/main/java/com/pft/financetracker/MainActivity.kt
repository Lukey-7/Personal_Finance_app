package com.pft.financetracker

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pft.financetracker.ui.nav.AppNav
import com.pft.financetracker.ui.theme.FinTrackTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Financial data: block screenshots / screen recording and hide content in the recents switcher.
        // Debug builds leave it off so the UI can be captured for docs and QA; release builds always set it.
        if (!BuildConfig.DEBUG) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        enableEdgeToEdge()
        setContent {
            FinTrackTheme {
                AppNav()
            }
        }
    }
}
