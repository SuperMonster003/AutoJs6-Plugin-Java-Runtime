package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JavaDexOutputPolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun oneThroughFourContiguousDexFilesAreAdmittedInCanonicalOrder() {
        val dex = temporaryFolder.newFile("classes.dex")
        temporaryFolder.newFile("notes.txt")

        assertEquals(listOf(dex), JavaDexOutputPolicy.requireDexFiles(temporaryFolder.root.listFiles()!!.asList()))

        val multiple = temporaryFolder.newFolder("multiple")
        val classes3 = multiple.resolve("classes3.dex").apply { createNewFile() }
        val classes = multiple.resolve("classes.dex").apply { createNewFile() }
        val classes2 = multiple.resolve("classes2.dex").apply { createNewFile() }
        assertEquals(
            listOf(classes, classes2, classes3),
            JavaDexOutputPolicy.requireDexFiles(listOf(classes3, classes, classes2)),
        )
    }

    @Test
    fun missingRenamedGappedAndExcessOutputsAreStableRejections() {
        val empty = temporaryFolder.newFolder("empty")
        assertDexingFailure(empty.listFiles()!!.asList())

        val renamed = temporaryFolder.newFolder("renamed")
        renamed.resolve("classes2.dex").createNewFile()
        assertDexingFailure(renamed.listFiles()!!.asList())

        val gapped = temporaryFolder.newFolder("gapped")
        gapped.resolve("classes.dex").createNewFile()
        gapped.resolve("classes3.dex").createNewFile()
        assertDexingFailure(gapped.listFiles()!!.asList())

        val nonCanonical = temporaryFolder.newFolder("non-canonical")
        nonCanonical.resolve("classes.dex").createNewFile()
        nonCanonical.resolve("classes01.dex").createNewFile()
        assertDexingFailure(nonCanonical.listFiles()!!.asList())

        val excess = temporaryFolder.newFolder("excess")
        (1..5).forEach { index ->
            excess.resolve(if (index == 1) "classes.dex" else "classes$index.dex").createNewFile()
        }
        assertDexingFailure(excess.listFiles()!!.asList())

        val nonFile = temporaryFolder.newFolder("non-file")
        nonFile.resolve("classes.dex").mkdir()
        assertDexingFailure(nonFile.listFiles()!!.asList())
    }

    private fun assertDexingFailure(files: Collection<java.io.File>) {
        val failure = assertThrows(JavaProviderFailure::class.java) {
            JavaDexOutputPolicy.requireDexFiles(files)
        }
        assertEquals(JvmSourceErrorCode.DEXING_FAILED, failure.code)
    }
}
