package org.autojs.plugin.jvmsource.java.worker

import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import dalvik.system.DexClassLoader
import dalvik.system.InMemoryDexClassLoader
import org.autojs.plugin.jvmsource.api.JvmCancellationException
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.java.AndroidPrivateDirectoryAnchor
import org.autojs.plugin.jvmsource.java.DexArtifactPayload
import org.autojs.plugin.jvmsource.java.DexArtifactSetValidator
import org.autojs.plugin.jvmsource.java.JavaDexOutputPolicy
import org.autojs.plugin.jvmsource.java.JavaProviderFailure
import org.autojs.plugin.jvmsource.java.ProviderDexSetIdentity
import org.autojs.plugin.jvmsource.java.ValidatedDexArtifactSet
import org.autojs.plugin.jvmsource.java.WorkerDexLoaderKind
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

internal class WorkerDexLoader(private val context: Context) {
    /** Gate 1: consume, bind, and structurally validate the DEX without constructing a ClassLoader. */
    fun validateStructure(
        descriptors: List<ParcelFileDescriptor>,
        expectedIdentity: ProviderDexSetIdentity,
        expectedClassDescriptors: Set<String>,
        requestMinApi: Int,
        ensureActive: () -> Unit,
    ): StructurallyValidatedWorkerDexSet {
        if (descriptors.size != expectedIdentity.files.size) {
            throw invalidArtifact("DEX descriptor count differs from its metadata")
        }
        val payloads = descriptors.zip(expectedIdentity.files).map { (descriptor, identity) ->
            DexArtifactPayload(identity, readDex(descriptor, identity.sizeBytes, ensureActive))
        }
        val artifacts = DexArtifactSetValidator.validate(
            payloads = payloads,
            expectedIdentity = expectedIdentity,
            requestMinApi = requestMinApi,
            deviceApi = Build.VERSION.SDK_INT,
            expectedClassDescriptors = expectedClassDescriptors,
        )
        ensureActive()
        return StructurallyValidatedWorkerDexSet(payloads, artifacts)
    }

    /** Gate 2a: construct the exact ClassLoader selected by the validated runtime policy. */
    fun createClassLoader(
        validated: StructurallyValidatedWorkerDexSet,
        generation: Long,
        requestId: String,
        parent: ClassLoader,
    ): LoadedDex {
        return try {
            when (validated.artifacts.loaderKind) {
                WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        when {
                            validated.payloads.size == 1 -> loadSingleInMemory(validated, parent)
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 ->
                                loadMultipleInMemory(validated, parent)
                            else -> throw invalidArtifact(
                                "Android API 26 cannot load a shared multi-DEX in-memory namespace",
                            )
                        }
                    } else {
                        throw invalidArtifact("In-memory DEX loading is unavailable on this Android version")
                    }
                }
                WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER ->
                    loadFromPrivateFile(validated, generation, requestId, parent)
            }
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: JvmCancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.CLASS_LOADING_FAILED,
                JvmSourceFailurePhase.WORKER_START,
                "ART ClassLoader creation failed after DEX structure validation",
                error,
            )
        }
    }

    private fun readDex(
        descriptor: ParcelFileDescriptor,
        expectedSizeBytes: Long,
        ensureActive: () -> Unit,
    ): ByteArray {
        if (expectedSizeBytes !in 1L..JvmSourceContract.MAX_DEX_ARTIFACT_BYTES ||
            expectedSizeBytes > Int.MAX_VALUE
        ) {
            throw invalidArtifact("DEX size metadata is outside the worker limit")
        }
        val expected = expectedSizeBytes.toInt()
        val bytes = ByteArray(expected)
        var offset = 0
        try {
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                while (offset < expected) {
                    ensureActive()
                    val read = input.read(bytes, offset, expected - offset)
                    if (read < 0) break
                    if (read > 0) offset += read
                }
                require(offset == expected) { "DEX stream ended before its declared size" }
                require(input.read() < 0) { "DEX stream exceeds its declared size" }
                descriptor.checkError()
            }
        } catch (error: JavaProviderFailure) {
            throw error
        } catch (error: JvmCancellationException) {
            throw error
        } catch (error: InterruptedException) {
            throw error
        } catch (error: Throwable) {
            throw invalidArtifact("Unable to read the compiler DEX stream", error)
        }
        return bytes
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun loadSingleInMemory(validated: StructurallyValidatedWorkerDexSet, parent: ClassLoader): LoadedDex {
        // ART requires a non-direct buffer to expose its backing array while constructing the
        // in-memory DEX. The validated bytes remain private to this isolated worker.
        val retainedBytes = validated.payloads.single().bytes
        return LoadedDex(
            classLoader = InMemoryDexClassLoader(ByteBuffer.wrap(retainedBytes), parent),
            validatedArtifacts = validated.artifacts,
            actualLoaderKind = WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
            retainedBytes = listOf(retainedBytes),
        )
    }

    @RequiresApi(Build.VERSION_CODES.O_MR1)
    private fun loadMultipleInMemory(validated: StructurallyValidatedWorkerDexSet, parent: ClassLoader): LoadedDex {
        val retainedBytes = validated.payloads.map(DexArtifactPayload::bytes)
        return LoadedDex(
            classLoader = InMemoryDexClassLoader(retainedBytes.map(ByteBuffer::wrap).toTypedArray(), parent),
            validatedArtifacts = validated.artifacts,
            actualLoaderKind = WorkerDexLoaderKind.IN_MEMORY_DEX_CLASS_LOADER,
            retainedBytes = retainedBytes,
        )
    }

    private fun loadFromPrivateFile(
        validated: StructurallyValidatedWorkerDexSet,
        generation: Long,
        requestId: String,
        parent: ClassLoader,
    ): LoadedDex {
        require(REQUEST_ID.matches(requestId)) { "Worker request ID is invalid" }
        val privateCodeCache = AndroidPrivateDirectoryAnchor.codeCache(context)
        val lexicalBase = File(privateCodeCache, "jvm-source-worker").absoluteFile
        if (!lexicalBase.exists() && !lexicalBase.mkdirs()) throw IOException("Unable to create worker code cache")
        val base = lexicalBase.canonicalFile
        require(base.path == lexicalBase.path && base.isDirectory) {
            "Worker code cache must be an ordinary private directory"
        }
        val root = File(base, "request-$requestId-generation-$generation").canonicalFile
        val expectedPrefix = base.path + File.separator
        require(root.path.startsWith(expectedPrefix) && root.mkdir()) { "Unable to allocate worker code cache" }
        val artifacts = validated.payloads.map { NamedDexBytes(it.identity.name, it.bytes) }
        return PrivateDexArtifactPublisher.publishSet(root, artifacts) { dexFiles ->
            val optimized = File(root, "optimized")
            if (!optimized.mkdir()) throw IOException("Unable to create worker optimized cache")
            LoadedDex(
                DexClassLoader(
                    dexFiles.joinToString(File.pathSeparator, transform = File::getAbsolutePath),
                    optimized.absolutePath,
                    null,
                    parent,
                ),
                validatedArtifacts = validated.artifacts,
                actualLoaderKind = WorkerDexLoaderKind.PRIVATE_DEX_CLASS_LOADER,
                cleanupRoot = root,
            )
        }
    }

    private fun invalidArtifact(message: String, cause: Throwable? = null) = JavaProviderFailure(
        JvmSourceErrorCode.ARTIFACT_INVALID,
        JvmSourceFailurePhase.WORKER_START,
        message,
        cause,
    )

    companion object {
        private val REQUEST_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
        private val STALE_DIRECTORY = Regex("request-${REQUEST_ID.pattern}-generation-[1-9][0-9]*")

        fun clearStalePrivateDex(context: Context) {
            val privateCodeCache = AndroidPrivateDirectoryAnchor.codeCache(context)
            val lexicalBase = File(privateCodeCache, "jvm-source-worker").absoluteFile
            if (!lexicalBase.exists()) return
            val base = lexicalBase.canonicalFile
            if (base.path != lexicalBase.path || !base.isDirectory) {
                throw IOException("Worker code cache must be an ordinary private directory")
            }
            val candidates = base.listFiles()
                ?: throw IOException("Unable to enumerate worker code cache")
            candidates.forEach { candidate ->
                if (!STALE_DIRECTORY.matches(candidate.name)) return@forEach
                val lexicalCandidate = File(base, candidate.name).absoluteFile
                val canonical = candidate.canonicalFile
                if (candidate.absoluteFile.path != lexicalCandidate.path ||
                    canonical.path != lexicalCandidate.path || !candidate.isDirectory
                ) return@forEach
                deletePrivateTreeWithoutFollowingLinks(candidate)
            }
        }
    }
}

/**
 * The single production publication boundary for private disk DEX. A file is publishable only
 * after the read-only write returns; every failure removes the entire request root without
 * following links before the exception escapes.
 */
internal data class NamedDexBytes(val name: String, val bytes: ByteArray)

internal object PrivateDexArtifactPublisher {
    fun <T> publish(
        root: File,
        bytes: ByteArray,
        targetFactory: (File) -> ReadOnlyDexWritePolicy.Target = ::FileReadOnlyDexWriteTarget,
        publish: (File) -> T,
    ): T {
        return publishSet(
            root = root,
            artifacts = listOf(NamedDexBytes("classes.dex", bytes)),
            targetFactory = targetFactory,
        ) { files -> publish(files.single()) }
    }

    fun <T> publishSet(
        root: File,
        artifacts: List<NamedDexBytes>,
        targetFactory: (File) -> ReadOnlyDexWritePolicy.Target = ::FileReadOnlyDexWriteTarget,
        publish: (List<File>) -> T,
    ): T {
        val lexicalRoot = root.absoluteFile
        require(root.canonicalFile.path == lexicalRoot.path && root.isDirectory) {
            "Private DEX publication root must be an ordinary directory"
        }
        return try {
            JavaDexOutputPolicy.requireCanonicalDexNames(artifacts.map(NamedDexBytes::name))
            val dexFiles = artifacts.map { File(lexicalRoot, it.name) }
            artifacts.zip(dexFiles).forEach { (artifact, dex) ->
                ReadOnlyDexWritePolicy.write(artifact.bytes, targetFactory(dex))
            }
            publish(dexFiles)
        } catch (error: Throwable) {
            runCatching { deletePrivateTreeWithoutFollowingLinks(lexicalRoot) }
                .onFailure(error::addSuppressed)
            throw error
        }
    }
}

internal enum class WorkerDexLoadStage {
    STRUCTURE_VALIDATED,
    ART_CLASS_LOADER_CREATED,
    ART_ENTRY_CLASS_LOADED,
}

internal class StructurallyValidatedWorkerDexSet internal constructor(
    internal val payloads: List<DexArtifactPayload>,
    val artifacts: ValidatedDexArtifactSet,
) {
    val stage: WorkerDexLoadStage = WorkerDexLoadStage.STRUCTURE_VALIDATED
}

internal class LoadedDex(
    val classLoader: ClassLoader,
    val validatedArtifacts: ValidatedDexArtifactSet,
    val actualLoaderKind: WorkerDexLoaderKind,
    @Suppress("unused") private val retainedBytes: List<ByteArray> = emptyList(),
    private val cleanupRoot: File? = null,
) : Closeable {
    val stage: WorkerDexLoadStage = WorkerDexLoadStage.ART_CLASS_LOADER_CREATED

    init {
        require(actualLoaderKind == validatedArtifacts.loaderKind) {
            "Actual ART ClassLoader branch differs from the validated DEX runtime policy"
        }
    }

    override fun close() {
        if (!closeAndVerifyTemporaryStorageReleased()) {
            throw IOException("Worker code-cache cleanup was not verified")
        }
    }

    internal fun closeAndVerifyTemporaryStorageReleased(): Boolean {
        val root = cleanupRoot ?: return true
        val lexicalParent = root.absoluteFile.parentFile
            ?: throw IOException("Worker code-cache request has no private parent")
        val canonicalParent = lexicalParent.canonicalFile
        if (canonicalParent.path != lexicalParent.path || !canonicalParent.isDirectory) {
            throw IOException("Worker code-cache parent is no longer an ordinary directory")
        }
        deletePrivateTreeWithoutFollowingLinks(root)
        val remaining = canonicalParent.list()
            ?: throw IOException("Unable to verify worker code-cache cleanup")
        return remaining.none { it == root.name }
    }
}

private fun deletePrivateTreeWithoutFollowingLinks(root: File) {
    fun delete(node: File, expectedLexicalPath: File) {
        val lexical = node.absoluteFile
        require(lexical.path == expectedLexicalPath.absoluteFile.path) {
            "Worker code-cache cleanup escaped its lexical root"
        }
        val canonical = runCatching { node.canonicalFile }.getOrNull()
        if (canonical == null || canonical.path != lexical.path) {
            deleteListedWorkerEntry(node, "Unable to remove a worker code-cache link")
            return
        }
        if (node.isDirectory) {
            val children = node.listFiles()
                ?: throw IOException("Unable to enumerate worker code cache")
            children.forEach { child -> delete(child, File(lexical, child.name)) }
        }
        deleteListedWorkerEntry(node, "Unable to remove a worker code-cache entry")
    }

    delete(root, root.absoluteFile)
}

private fun deleteListedWorkerEntry(node: File, failureMessage: String) {
    val listed = node.parentFile?.list()?.any { it == node.name } == true
    if (listed && !node.delete()) throw IOException(failureMessage)
}
