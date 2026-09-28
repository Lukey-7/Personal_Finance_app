package com.pft.financetracker

import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.prefs.SettingsRepository
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** "Clear all data" leaves nothing behind, including cached AI answers (kept outside the encrypted database). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SettingsClearTest {
    @Test fun clearAllAlsoForgetsCachedAiAnswers() {
        val settings = SettingsRepository(ApplicationProvider.getApplicationContext<android.app.Application>())
        settings.aiAnswerCache.put("{request}", "{answer}")
        settings.clearAll()
        assertNull(settings.aiAnswerCache.get("{request}"))
    }
}
