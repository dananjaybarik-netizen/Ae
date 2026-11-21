package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.AiStudioDatabase
import com.example.data.repository.PluginInstallResult
import com.example.data.repository.PluginRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PluginSecurityRobolectricTest {

    @Test
    fun `aifx export creates verifiable package and imports cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AiStudioDatabase.getInstance(context)
        val repo = PluginRepository(context, db.pluginDao())

        val exportedFile = repo.exportSampleAifxPackage()
        assertTrue(exportedFile.exists())
        assertTrue(exportedFile.name.endsWith(".aifx"))

        val result = repo.importAifxPackage(Uri.fromFile(exportedFile))
        assertTrue(result is PluginInstallResult.Success)
        val success = result as PluginInstallResult.Success
        assertTrue(success.plugin.isSignatureVerified)
    }

    @Test
    fun `aifx importer rejects native executable so or dex payloads`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = AiStudioDatabase.getInstance(context)
        val repo = PluginRepository(context, db.pluginDao())

        val maliciousZip = File(context.cacheDir, "malicious.aifx")
        ZipOutputStream(FileOutputStream(maliciousZip)).use { zos ->
            zos.putNextEntry(ZipEntry("libuntrusted.so"))
            zos.write(byteArrayOf(0x7F, 0x45, 0x4C, 0x46))
            zos.closeEntry()
        }

        val result = repo.importAifxPackage(Uri.fromFile(maliciousZip))
        assertTrue(result is PluginInstallResult.Failure)
        val failure = result as PluginInstallResult.Failure
        assertTrue(failure.reason.contains("Security block"))
    }
}
