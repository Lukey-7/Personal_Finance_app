package com.pft.financetracker.ui.model

import com.pft.financetracker.data.local.ImportBatchEntity
import com.pft.financetracker.ui.StatementUiState
import kotlinx.coroutines.CancellationException

/** Shown when saving a statement fails; the import is one change, so nothing was saved. */
const val IMPORT_FAILED = "Couldn't save this statement. Nothing was added."

/**
 * Saving a previewed statement, as the import screen sees it: [save] (the ledger's all-or-nothing import) either
 * gives the batch, or fails and the screen says nothing was added. Leaving the screen (cancellation) is not a failure.
 */
suspend fun saveStatement(save: suspend () -> ImportBatchEntity): StatementUiState = try {
    StatementUiState.Saved(save())
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    StatementUiState.Error(IMPORT_FAILED)
}
