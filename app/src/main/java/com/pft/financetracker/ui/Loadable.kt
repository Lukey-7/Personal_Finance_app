package com.pft.financetracker.ui

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** A value that may not have arrived yet, so "no rows" is never confused with "not loaded". */
sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>
    data class Ready<T>(val value: T) : Loadable<T>
}

fun <T> Flow<T>.asLoadable(): Flow<Loadable<T>> = map<T, Loadable<T>> { Loadable.Ready(it) }.onStart { emit(Loadable.Loading) }
