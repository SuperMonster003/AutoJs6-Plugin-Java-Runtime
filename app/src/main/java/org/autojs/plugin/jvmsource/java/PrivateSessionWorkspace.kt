package org.autojs.plugin.jvmsource.java

import android.content.Context
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import org.autojs.plugin.jvmsource.api.JvmSourcePackagePathPolicy
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
    val sourceDirectory = File(root, "sources")
    val classesDirectory = File(root, "classes")
    val programJar = File(root, "program.jar")
    val d8OutputDirectory = File(root, "d8-output")
    val cacheHitDirectory = File(root, "cache-hit")

    init {
        if (!sourceDirectory.mkdir() || !classesDirectory.mkdir() ||
            !d8OutputDirectory.mkdir() || !cacheHitDirectory.mkdir()
        ) {
            close()
            throw IOException("Failed to create the private Java provider workspace")
        }
    }

    fun reservePackageSource(sourcePath: String): File {
        JvmSourcePackagePathPolicy.requireSourcePath(sourcePath)
        var parent = sourceDirectory
        sourcePath.split('/').dropLast(1).forEach { segment ->
            val child = File(parent, segment)
            if (!child.exists() && !child.mkdir()) {
                throw IOException("Failed to create a private Java source package directory")
            }
            if (isWindowsSymbolicLink(child)) {
                throw IOException("Java source package directory escaped its private workspace")
            }
            val canonicalChild = child.canonicalFile
            if (canonicalChild.parentFile != parent || !canonicalChild.isDirectory) {
                throw IOException("Java source package directory escaped its private workspace")
            }
            parent = canonicalChild
        }
        val target = File(parent, sourcePath.substringAfterLast('/'))
        if (target.parentFile != parent || target.exists() || !target.createNewFile()) {
            throw IOException("Failed to reserve a private Java source package file")
        }
        val canonicalTarget = target.canonicalFile
        if (isWindowsSymbolicLink(target) ||
            canonicalTarget != target.absoluteFile || canonicalTarget.parentFile != parent
        ) {
            target.delete()
            throw IOException("Java source package file escaped its private workspace")
        }
        return canonicalTarget
    }

    override fun close() {
        if (!closeAndVerifyRemoved()) throw IOException("Java provider workspace cleanup was not verified")
    }

    internal fun closeAndVerifyRemoved(): Boolean {
        val lexicalParent = root.absoluteFile.parentFile
            ?: throw IOException("Java provider workspace has no private parent")
        val canonicalParent = lexicalParent.canonicalFile
        if (isWindowsSymbolicLink(lexicalParent) ||
            canonicalParent.path != lexicalParent.path || !canonicalParent.isDirectory
        ) {
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
            if (isWindowsSymbolicLink(baseDirectory) || lexicalBase.path != canonicalBase.path) {
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
                if (isWindowsSymbolicLink(candidate) ||
                    candidate.absoluteFile.path != lexicalCandidate.path ||
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
            if (isWindowsSymbolicLink(baseDirectory) ||
                lexicalBase.path != canonicalBase.path || !canonicalBase.isDirectory
            ) {
                throw IOException("Java provider session root must be an ordinary directory")
            }
            repeat(8) {
                val candidate = File(canonicalBase, "session-${UUID.randomUUID()}")
                if (candidate.mkdir()) {
                    val canonicalCandidate = candidate.canonicalFile
                    val prefix = canonicalBase.path + File.separator
                    if (isWindowsSymbolicLink(candidate) || !canonicalCandidate.path.startsWith(prefix)) {
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
            if (isWindowsSymbolicLink(node)) {
                deleteListedEntry(node, "Unable to remove a Java provider workspace link")
                return
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
            if (!listed) return
            val deleted = if (isWindowsSymbolicLink(node)) {
                runCatching { Files.deleteIfExists(node.toPath()) }.getOrDefault(false)
            } else {
                node.delete()
            }
            if (!deleted) {
                throw IOException(failureMessage)
            }
        }

        /** Android's canonicalFile resolves links; Windows host tests require an explicit probe. */
        private fun isWindowsSymbolicLink(file: File): Boolean =
            File.separatorChar == '\\' && Files.isSymbolicLink(file.toPath())
    }
}
