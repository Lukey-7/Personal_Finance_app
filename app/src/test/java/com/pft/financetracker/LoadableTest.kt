package com.pft.financetracker

import com.pft.financetracker.ui.Loadable
import com.pft.financetracker.ui.asLoadable
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** Screens show a loading state until the database has answered, so an empty list is never mistaken for "nothing yet". */
class LoadableTest {
    @Test fun startsLoadingThenReady() = runBlocking {
        assertEquals(listOf(Loadable.Loading, Loadable.Ready(emptyList<Int>())), flowOf(emptyList<Int>()).asLoadable().toList())
    }

    @Test fun anEmptyListIsReadyNotLoading() = runBlocking {
        assertEquals(Loadable.Ready(emptyList<Int>()), flowOf(emptyList<Int>()).asLoadable().toList().last())
    }
}
