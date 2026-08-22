package org.autojs.plugin.jvmsource.java

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.util.UUID

internal class PrivateSessionWorkspace private constructor(
    private val root: File,
    sourceFileName: String,
) : Closeable {
    val sourceFile = File(root, sourceFileName).also { source ->
        require(source.name == sourceFileName && source.parentFile == root) {
            "Java source file escaped its private workspace"
        }
    }
    val classesDirectory = File(root, "classes")
    val programJar = File(root, "program.jar")
    val d8OutputDirectory = File(root, "d8-output")
    val cacheHitDirectory = File(root, "cache-hit")

    init {
        if (!classesDirectory.mkdir() || !d8OutputDirectory.mkdir() || !cacheHitDirectory.mkdir()) {
            close()
            throw IOException("Failed to create the private Java provider workspace")
        }
    }

    override fun close() {
        if (!closeAndVerifyRemoved()) throw IOException("Java provider workspace cleanup was not verified")
    }

    internal fun closeAndVerifyRemoved(): Boolean {
        val lexicalParent = root.absoluteFile.parentFile
            ?: throw IOException("Java provider workspace has no private parent")
        val canonicalParent = lexicalParent.canonicalFile
        if (canonicalParent.path != lexicalParent.path || !canonicalParent.isDirectory) {
            throw IOException("Java provider workspace parent is no longer an ordinary directory")
        }
        deleteTreeWithoutFollowingLinks(root, root.absoluteFile)
        val remaining = canonicalParent.list()
            ?: throw IOException("Unable to verify Java provider workspace cleanup")
        return remaining.none { it == root.name }
    }

    companion object {
        private val SESSION_DIRECTORY = Regex(
            "session-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",
        )

        fun create(context: Context, sourceFileName: String): PrivateSessionWorkspace = createUnder(
            File(AndroidPrivateDirectoryAnchor.cache(context), "jvm-source-java-sessions"),
            sourceFileName,
        )

        fun clearStale(context: Context) {
            clearStaleUnder(File(AndroidPrivateDirectoryAnchor.cache(context), "jvm-source-java-sessions"))
        }

        internal fun clearStaleUnder(baseDirectory: File) {
            if (!baseDirectory.exists()) return
            val lexicalBase = baseDirectory.absoluteFile
            val canonicalBase = baseDirectory.canonicalFile
            if (lexicalBase.path != canonicalBase.path) {
                throw IOException("Java provider session root must not be a symbolic link")
            }
            if (!canonicalBase.isDirectory) throw IOException("Java provider session root is not a directory")
            val candidates = canonicalBase.listFiles()
                ?: throw IOException("Unable to enumerate the Java provider session root")
            candidates.forEach { candidate ->
                if (!SESSION_DIRECTORY.matches(candidate.name)) return@forEach
                val lexicalCandidate = File(canonicalBase, candidate.name).absoluteFile
                val canonicalCandidate = runCatching { candidate.canonicalFile }.getOrNull()
                    ?: return@forEach
                // Only an ordinary, exact session child is eligible. A candidate symlink is left untouched.
                if (candidate.absoluteFile.path != lexicalCandidate.path ||
                    canonicalCandidate.path != lexicalCandidate.path || !candidate.isDirectory
                ) return@forEach
                deleteTreeWithoutFollowingLinks(candidate, lexicalCandidate)
            }
        }

        internal fun createUnder(
            baseDirectory: File,
            sourceFileName: String = "Main.java",
        ): PrivateSessionWorkspace {
            if (!baseDirectory.exists() && !baseDirectory.mkdirs()) {
                throw IOException("Failed to create the Java provider session root")
            }
            val lexicalBase = baseDirectory.absoluteFile
            val canonicalBase = baseDirectory.canonicalFile
            if (lexicalBase.path != canonicalBase.path || !canonicalBase.isDirectory) {
                throw IOException("Java provider session root must be an ordinary directory")
            }
            repeat(8) {
                val candidate = File(canonicalBase, "session-${UUID.randomUUID()}")
                if (candidate.mkdir()) {
                    val canonicalCandidate = candidate.canonicalFile
                    val prefix = canonicalBase.path + File.separator
                    if (!canonicalCandidate.path.startsWith(prefix)) {
                        candidate.delete()
                        throw IOException("Java provider workspace escaped its private root")
                    }
                    return PrivateSessionWorkspace(canonicalCandidate, sourceFileName)
                }
            }
            throw IOException("Failed to allocate a Java provider workspace")
        }

        private fun deleteTreeWithoutFollowingLinks(node: File, expectedLexicalPath: File) {
            val lexical = node.absoluteFile
            require(lexical.path == expectedLexicalPath.absoluteFile.path) {
                "Java provider cleanup escaped its lexical root"
            }
            val canonical = runCatching { node.canonicalFile }.getOrNull()
            if (canonical == null || canonical.path != lexical.path) {
                deleteListedEntry(node, "Unable to remove a Java provider workspace link")
                return
            }
            if (node.isDirectory) {
                val children = node.listFiles()
                    ?: throw IOException("Unable to enumerate a Java provider workspace")
                children.forEach { child ->
                    deleteTreeWithoutFollowingLinks(child, File(lexical, child.name))
                }
            }
            deleteListedEntry(node, "Unable to remove a Java provider workspace entry")
        }

        private fun deleteListedEntry(node: File, failureMessage: String) {
            val listed = node.parentFile?.list()?.any { it == node.name } == true
            if (listed && !node.delete()) {
                throw IOException(failureMessage)
            }
        }
    }
}
