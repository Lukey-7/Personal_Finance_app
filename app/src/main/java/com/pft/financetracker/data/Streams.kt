package com.pft.financetracker.data

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The whole stream, or null when it is longer than [max] bytes. A picked file is read this way so a huge one is
 * refused after [max] bytes instead of being loaded whole.
 */
fun InputStream.readAtMost(max: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = read(buf)
        if (n < 0) return out.toByteArray()
        if (out.size() + n > max) return null
        out.write(buf, 0, n)
    }
}
