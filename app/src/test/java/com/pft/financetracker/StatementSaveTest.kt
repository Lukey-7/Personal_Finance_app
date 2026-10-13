package com.pft.financetracker

import com.pft.financetracker.data.local.ImportBatchEntity
import com.pft.financetracker.ui.StatementUiState
import com.pft.financetracker.ui.model.IMPORT_FAILED
import com.pft.financetracker.ui.model.saveStatement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

/** The import screen after "Save": the batch when it saved, a plain message when it didn't, never a crash. */
class StatementSaveTest {
    private val batch = ImportBatchEntity(id = 7, fileName = "oct.csv", format = "CSV", rowsFound = 2, added = 2, duplicates = 0, needsReview = 0,
        balanceMismatches = 0, firstDate = 0L, lastDate = 0L)

    @Test fun aSavedStatementShowsItsBatch() = runBlocking {
        assertEquals(StatementUiState.Saved(batch), saveStatement { batch })
    }

    @Test fun aFailedSaveSaysNothingWasAdded() = runBlocking {
        assertEquals(StatementUiState.Error(IMPORT_FAILED), saveStatement { throw java.io.IOException("disk full") })
        assertEquals(StatementUiState.Error(IMPORT_FAILED), saveStatement { throw IllegalStateException("constraint") })
    }

    @Test fun leavingTheScreenIsNotAFailure() = runBlocking {
        try {
            saveStatement { throw CancellationException("screen closed") }
            fail("cancellation must pass through")
        } catch (_: CancellationException) {
        }
    }
}
