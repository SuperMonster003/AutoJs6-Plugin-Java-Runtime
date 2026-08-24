package org.autojs.plugin.jvmsource.java.worker

import java.io.File
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrivateDexArtifactPublisherTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun publishesCanonicalDexSetOnlyAfterEveryReadOnlyWriteCompletes() {
        val root = temporaryFolder.newFolder("success")
        val events = mutableListOf<String>()

        val names = PrivateDexArtifactPublisher.publishSet(
            root,
            listOf(NamedDexBytes("classes.dex", byteArrayOf(1)), NamedDexBytes("classes2.dex", byteArrayOf(2))),
            targetFactory = { file -> RecordingTarget(file, events) },
        ) { files ->
            events += "publish"
            files.map(File::getName)
        }

        assertEquals(listOf("classes.dex", "classes2.dex"), names)
        assertEquals(
            listOf(
                "classes.dex:create",
                "classes.dex:open",
                "classes.dex:readonly",
                "classes.dex:write",
                "classes.dex:sync",
                "classes.dex:close",
                "classes2.dex:create",
                "classes2.dex:open",
                "classes2.dex:readonly",
                "classes2.dex:write",
                "classes2.dex:sync",
                "classes2.dex:close",
                "publish",
            ),
            events,
        )
    }

    @Test
    fun secondDexFailureSuppressesPublicationAndRemovesTheWholeRequestTree() {
        val root = temporaryFolder.newFolder("failure")
        val events = mutableListOf<String>()
        var published = false

        assertThrows(IOException::class.java) {
            PrivateDexArtifactPublisher.publishSet(
                root,
                listOf(
                    NamedDexBytes("classes.dex", byteArrayOf(1)),
                    NamedDexBytes("classes2.dex", byteArrayOf(2)),
                ),
                targetFactory = { file -> RecordingTarget(file, events, failWrite = file.name == "classes2.dex") },
            ) {
                published = true
            }
        }

        assertFalse(published)
        assertFalse(root.exists())
        assertTrue(events.contains("classes.dex:write"))
        assertTrue(events.contains("classes2.dex:write"))
    }

    private class RecordingTarget(
        private val file: File,
        private val events: MutableList<String>,
        private val failWrite: Boolean = false,
    ) : ReadOnlyDexWritePolicy.Target {
        override fun createEmpty() {
            events += "${file.name}:create"
            check(file.createNewFile())
        }

        override fun openForWrite(): ReadOnlyDexWritePolicy.OpenOutput {
            events += "${file.name}:open"
            return object : ReadOnlyDexWritePolicy.OpenOutput {
                override fun writeContent(bytes: ByteArray) {
                    events += "${file.name}:write"
                    if (failWrite) throw IOException("controlled second DEX failure")
                }

                override fun sync() {
                    events += "${file.name}:sync"
                }

                override fun close() {
                    events += "${file.name}:close"
                }
            }
        }

        override fun markReadOnly() {
            events += "${file.name}:readonly"
        }
    }
}
