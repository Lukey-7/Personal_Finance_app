package com.pft.financetracker

import com.pft.financetracker.data.local.KeyRecovery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A broken key store must never cost the owner their data. */
class KeyRecoveryTest {
    @Test fun theKeyStoreIsRebuiltOnlyWhenThereIsNoDatabaseToLose() {
        assertTrue(KeyRecovery.mayRebuild(databaseExists = false))
        assertFalse(KeyRecovery.mayRebuild(databaseExists = true))
    }

    @Test fun anUnreadableDatabaseIsKeptUnderADatedName() {
        val name = KeyRecovery.keptName(0L)
        assertTrue(name, name.startsWith("fintrack-unreadable-") && name.endsWith(".db"))
        assertFalse(name == "fintrack.db")
    }
}
