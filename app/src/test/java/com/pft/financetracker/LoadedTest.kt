package com.pft.financetracker

import com.pft.financetracker.ui.isLoaded
import com.pft.financetracker.ui.notLoaded
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The lists screens read start as a marker, so "loaded" is decided by the very value on screen, never a second query. */
class LoadedTest {
    @Test fun theStartingMarkerIsNotLoaded() = assertFalse(isLoaded(notLoaded<Int>()))
    @Test fun anEmptyAnswerFromTheDatabaseIsLoaded() = assertTrue(isLoaded(ArrayList<Int>()))
    @Test fun aKotlinEmptyListIsLoaded() = assertTrue(isLoaded(emptyList<Int>()))
}
