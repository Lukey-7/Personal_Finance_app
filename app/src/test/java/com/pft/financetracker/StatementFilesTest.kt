package com.pft.financetracker

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.importer.StatementFiles
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Opening picked files: password-protected PDFs, and clean errors for broken or hostile files. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class StatementFilesTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val files = StatementFiles(ctx)
    private var n = 0

    @Before fun setUp() { PDFBoxResourceLoader.init(ctx) }

    private fun uri(bytes: ByteArray, name: String): Uri {
        val u = Uri.parse("content://test/${++n}/$name")
        shadowOf(ctx.contentResolver).registerInputStreamSupplier(u) { bytes.inputStream() }
        return u
    }

    private fun pdf(user: String, owner: String = "owner-secret"): ByteArray {
        val out = ByteArrayOutputStream()
        PDDocument().use { d ->
            d.addPage(com.tom_roush.pdfbox.pdmodel.PDPage())
            d.protect(StandardProtectionPolicy(owner, user, AccessPermission().apply { setCanPrint(false) }).apply { encryptionKeyLength = 128 })
            d.save(out)
        }
        return out.toByteArray()
    }

    @Test fun passwordPrompts() = runBlocking {
        val locked = pdf(user = "01011990")
        assertEquals(StatementFiles.Read.NeedsPassword("a.pdf", wrong = false), files.read(uri(locked, "a.pdf")))
        assertEquals(StatementFiles.Read.NeedsPassword("a.pdf", wrong = true), files.read(uri(locked, "a.pdf"), "12345678"))
        assertEquals("an empty password is no password", StatementFiles.Read.NeedsPassword("a.pdf", wrong = false), files.read(uri(locked, "a.pdf"), ""))
        assertTrue(files.read(uri(locked, "a.pdf"), "01011990") !is StatementFiles.Read.NeedsPassword)
    }

    @Test fun anOwnerOnlyPdfOpensWithoutAsking() = runBlocking {
        assertTrue(files.read(uri(pdf(user = ""), "b.pdf")) !is StatementFiles.Read.NeedsPassword)
    }

    @Test fun errorsNeverEchoTheFileOrThePassword() = runBlocking {
        val broken = "%PDF-1.7\nSECRET-CONTENT-123 garbage".toByteArray()
        val r = files.read(uri(broken, "c.pdf"), "MyPassw0rd")
        assertTrue(r.toString(), r is StatementFiles.Read.Error || r is StatementFiles.Read.NeedsPassword)
        assertFalse(r.toString().contains("SECRET-CONTENT")); assertFalse(r.toString().contains("MyPassw0rd"))
    }

    @Test fun aZipBombBecomesAFriendlyError() = runBlocking {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val mb = ByteArray(1 shl 20) { ' '.code.toByte() }
            repeat(80) { z.write(mb) }
            z.closeEntry()
        }
        val r = files.read(uri(out.toByteArray(), "bomb.xlsx"))
        assertTrue(r.toString(), r is StatementFiles.Read.Error && (r as StatementFiles.Read.Error).message.contains("too large", true))
    }

    @Test fun anEmptyFileIsAFriendlyError() = runBlocking {
        assertTrue(files.read(uri(ByteArray(0), "empty.csv")) is StatementFiles.Read.Error)
    }
}
