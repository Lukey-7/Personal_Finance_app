package com.pft.financetracker.ui

import java.util.AbstractList
import java.util.RandomAccess

/*
 * The lists the screens read start as this marker, not as an empty list, so a screen can tell "the database has not
 * answered yet" (show a spinner) from "it answered: nothing" (show the empty state). Deciding it from the very list on
 * screen means the two can never disagree, and no second query is needed.
 */
private object NotLoaded : AbstractList<Any?>(), RandomAccess {
    override val size: Int get() = 0
    override fun get(index: Int): Any? = throw IndexOutOfBoundsException("not loaded yet")

    // Equal only to itself. As a list it would equal any empty list, and a StateFlow skips a value equal to the
    // current one, so an empty answer from the database would never replace the marker: an endless spinner.
    override fun equals(other: Any?): Boolean = other === this
    override fun hashCode(): Int = System.identityHashCode(this)
}

/** The starting value of a list that comes from the database. Reads as empty. */
@Suppress("UNCHECKED_CAST")
fun <T> notLoaded(): List<T> = NotLoaded as List<T>

/** False only for the [notLoaded] marker; any real answer, even an empty one, is loaded. */
fun isLoaded(list: List<*>): Boolean = list !== NotLoaded
