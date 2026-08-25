package org.autojs.plugin.jvmsource.java

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.IJvmHostBridge
import org.autojs.plugin.jvmsource.api.IJvmHostBridgeCallback
import org.autojs.plugin.jvmsource.api.JvmAppApi
import org.autojs.plugin.jvmsource.api.JvmCancellation
import org.autojs.plugin.jvmsource.api.JvmClipboardApi
import org.autojs.plugin.jvmsource.api.JvmConsoleApi
import org.autojs.plugin.jvmsource.api.JvmProtocolVersion
import org.autojs.plugin.jvmsource.api.JvmRequestId
import org.autojs.plugin.jvmsource.api.JvmScriptContext
import org.autojs.plugin.jvmsource.api.JvmSha256
import org.autojs.plugin.jvmsource.api.JvmSourceCodec
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourceLanguage
import org.autojs.plugin.jvmsource.api.JvmSourceRequest
import org.autojs.plugin.jvmsource.api.JvmSourceResult
import org.autojs.plugin.jvmsource.api.JvmSourceValidation
import org.autojs.plugin.jvmsource.java.worker.IJavaExecutionCallback
import org.autojs.plugin.jvmsource.java.worker.IJavaExecutionWorker
import org.autojs.plugin.jvmsource.java.worker.JavaExecutionWorkerService
import org.autojs.plugin.jvmsource.java.worker.WorkerDexLoader
import org.autojs.plugin.jvmsource.java.worker.WorkerEntryFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class JavaMultiDexPipelineInstrumentedTest {
    @Test
    fun overMethodReferenceLimitCompilesLoadsAndExecutesAcrossTheR4DexSet() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        val environment = JavaProviderEnvironment.get(context)
        val source = sourceOverDexMethodLimit()
        val sourceBytes = source.toByteArray(Charsets.UTF_8)
        assertTrue(sourceBytes.size.toLong() <= JvmSourceContract.MAX_SOURCE_BYTES)

        PrivateSessionWorkspace.create(context, "Main.java").use { workspace ->
            FileOutputStream(workspace.sourceFile).use { output ->
                output.write(sourceBytes)
                output.fd.sync()
            }
            val ecj = EcjJavaCompiler(environment.compilerClasspath).compile(
                workspace.sourceFile,
                workspace.classesDirectory,
                JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
                ensureActive = {},
            )
            assertTrue("ECJ failed for the generated multidex fixture: ${ecj.diagnostics}", ecj.succeeded)
            val classes = UserClassJarWriter.write(workspace.classesDirectory, workspace.programJar)
            assertEquals(HELPER_CLASS_COUNT + 1, classes.classFileCount)

            val dexFiles = D8JavaCompiler(environment.d8RuntimeLibraries).compile(
                workspace.programJar,
                workspace.d8OutputDirectory,
                JvmSourceContract.MIN_ANDROID_API,
                ensureActive = {},
            )
            assertTrue(
                "The generated fixture must cross the single-DEX method-reference limit: ${dexFiles.map { it.name }}",
                dexFiles.size in 2..JavaDexOutputPolicy.MAX_DEX_FILES,
            )
            assertEquals(JavaDexOutputPolicy.expectedDexNames(dexFiles.size), dexFiles.map { it.name })
            val dexIdentity = ProviderDexSetIdentity.fromFiles(dexFiles)
            dexFiles.forEach { assertTrue("Unable to freeze ${it.name}", it.setReadOnly()) }

            val descriptors = dexFiles.map { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
            val loader = WorkerDexLoader(context)
            val validated = try {
                loader.validateStructure(
                    descriptors = descriptors,
                    expectedIdentity = dexIdentity,
                    expectedClassDescriptors = classes.dexDescriptors,
                    requestMinApi = JvmSourceContract.MIN_ANDROID_API,
                    ensureActive = {},
                )
            } finally {
                descriptors.forEach { runCatching { it.close() } }
            }
            assertEquals(classes.dexDescriptors, validated.artifacts.classDescriptors.intersect(classes.dexDescriptors))

            loader.createClassLoader(
                validated,
                generation = 1L,
                requestId = UUID.randomUUID().toString(),
                parent = AutoJsJvmEntry::class.java.classLoader!!,
            ).use { loaded ->
                val entry = WorkerEntryFactory.instantiate(
                    WorkerEntryFactory.loadFromArt(loaded.classLoader),
                )
                assertEquals(EXPECTED_RESULT, entry.run(NoHostCallsContext))
                assertEquals(DexRuntimePolicy.loaderKind(Build.VERSION.SDK_INT), loaded.actualLoaderKind)
                Log.i(
                    TAG,
                    "R4_MULTIDEX_EVIDENCE api=${Build.VERSION.SDK_INT} " +
                        "loader=${loaded.actualLoaderKind} dexFiles=${dexFiles.size} " +
                        "dexBytes=${dexIdentity.sizeBytes} classes=${classes.classFileCount} direct=true",
                )
            }

            val workerResult = executeThroughDisposableWorker(
                context = context,
                sourceBytes = sourceBytes,
                programJar = workspace.programJar,
                dexFiles = dexFiles,
                dexIdentity = dexIdentity,
                expectedClassDescriptors = classes.dexDescriptors,
            )
            assertEquals(EXPECTED_RESULT.toString(), workerResult.resultJson)
            assertEquals(dexIdentity.sizeBytes, workerResult.dexArtifactSizeBytes)
            assertEquals(dexIdentity.sha256, workerResult.dexArtifactSha256)
            assertEquals(DexRuntimePolicy.loaderKind(Build.VERSION.SDK_INT).apiKind, workerResult.loaderKind)
            assertTrue(workerResult.workerPid > 0 && workerResult.workerPid != Process.myPid())
            assertEquals(context.packageName + ":worker", workerResult.workerProcessName)
            Log.i(
                TAG,
                "R4_MULTIDEX_BINDER_EVIDENCE api=${Build.VERSION.SDK_INT} " +
                    "loader=${workerResult.loaderKind} dexFiles=${dexFiles.size} " +
                    "dexBytes=${dexIdentity.sizeBytes} classes=${classes.classFileCount} " +
                    "workerIsolated=true",
            )
        }
    }

    private fun executeThroughDisposableWorker(
        context: Context,
        sourceBytes: ByteArray,
        programJar: File,
        dexFiles: List<File>,
        dexIdentity: ProviderDexSetIdentity,
        expectedClassDescriptors: Set<String>,
    ): JvmSourceResult {
        val request = JvmSourceRequest(
            requestId = JvmRequestId.fromUuid(UUID.randomUUID()),
            protocolVersion = JvmProtocolVersion(
                JvmSourceContract.PROTOCOL_MAJOR,
                JvmSourceContract.PROTOCOL_MINOR,
            ),
            language = JvmSourceLanguage.JAVA,
            sourceFileName = "Main.java",
            sourceSizeBytes = sourceBytes.size.toLong(),
            sourceSha256 = JvmSha256.digest(sourceBytes),
            expectedToolchainFingerprint = JvmSha256.digest("r4-multidex-device-test".toByteArray()),
            entryClassName = "Main",
            minApi = JvmSourceContract.MIN_ANDROID_API,
            timeoutMillis = JvmSourceContract.DEFAULT_TIMEOUT_MILLIS,
            maxStdoutBytes = JvmSourceContract.MAX_STDOUT_BYTES,
            maxStderrBytes = JvmSourceContract.MAX_STDERR_BYTES,
            diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
            allowedHostCalls = emptyList(),
            grantedCapabilities = emptyList(),
        ).also(JvmSourceValidation::validateRequest)
        val classIdentity = ProviderDigests.file(programJar, JvmSourceContract.MAX_CLASS_ARTIFACT_BYTES)
        val generation = 2L
        val connectionLatch = CountDownLatch(1)
        val terminalLatch = CountDownLatch(1)
        val observationLatch = CountDownLatch(1)
        val remoteReference = AtomicReference<IJavaExecutionWorker?>()
        val connectionFailure = AtomicReference<String?>()
        val callbackFailure = AtomicReference<String?>()
        val ready = AtomicReference<Pair<Int, Long>?>()
        val encodedResult = AtomicReference<ByteArray?>()
        val observation = AtomicReference<JavaProviderObservation?>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) {
                    connectionFailure.compareAndSet(null, "Worker returned a null Binder")
                } else {
                    remoteReference.set(IJavaExecutionWorker.Stub.asInterface(service))
                }
                connectionLatch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) = Unit

            override fun onBindingDied(name: ComponentName?) {
                connectionFailure.compareAndSet(null, "Worker binding died before admission")
                connectionLatch.countDown()
            }

            override fun onNullBinding(name: ComponentName?) {
                connectionFailure.compareAndSet(null, "Worker returned a null binding")
                connectionLatch.countDown()
            }
        }
        val callback = object : IJavaExecutionCallback.Stub() {
            override fun onReady(pid: Int, callbackGeneration: Long) {
                if (!ready.compareAndSet(null, pid to callbackGeneration)) {
                    callbackFailure.compareAndSet(null, "Worker readiness was delivered more than once")
                }
            }

            override fun onCompleted(callbackGeneration: Long, result: ByteArray?) {
                if (callbackGeneration != generation || result == null) {
                    callbackFailure.compareAndSet(null, "Worker completion framing was invalid")
                } else {
                    encodedResult.set(result.copyOf())
                }
                terminalLatch.countDown()
            }

            override fun onFailed(callbackGeneration: Long, error: ByteArray?) {
                val detail = runCatching {
                    require(callbackGeneration == generation && error != null)
                    JvmSourceCodec.decodeError(error).toString()
                }.getOrElse { failure -> "undecodable worker error: ${failure.javaClass.simpleName}" }
                callbackFailure.compareAndSet(null, "Worker failed: $detail")
                terminalLatch.countDown()
            }

            override fun onCancelled(callbackGeneration: Long, cancellation: ByteArray?) {
                val detail = runCatching {
                    require(callbackGeneration == generation && cancellation != null)
                    JvmSourceCodec.decodeCancellation(cancellation).toString()
                }.getOrElse { failure -> "undecodable worker cancellation: ${failure.javaClass.simpleName}" }
                callbackFailure.compareAndSet(null, "Worker cancelled: $detail")
                terminalLatch.countDown()
            }

            override fun onObservation(callbackGeneration: Long, encodedObservation: ByteArray?) {
                runCatching {
                    require(callbackGeneration == generation && encodedObservation != null)
                    JavaProviderObservationCodec.decode(encodedObservation)
                }.onSuccess(observation::set).onFailure { failure ->
                    callbackFailure.compareAndSet(
                        null,
                        "Worker observation was invalid: ${failure.javaClass.simpleName}",
                    )
                }
                observationLatch.countDown()
            }

            override fun onRuntimeDiagnostic(callbackGeneration: Long, line: Int) {
                callbackFailure.compareAndSet(null, "Unexpected runtime diagnostic at line $line")
            }
        }
        val hostBridge = object : IJvmHostBridge.Stub() {
            override fun dispatch(request: ByteArray?, callback: IJvmHostBridgeCallback?) {
                error("Multidex fixture must not dispatch a host call")
            }

            override fun destroy(reason: ByteArray?) = Unit
        }

        val intent = Intent(context, JavaExecutionWorkerService::class.java)
        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        assertTrue("Unable to bind the disposable Java worker", bound)
        val dexDescriptors = mutableListOf<ParcelFileDescriptor>()
        val stdoutPipe = ParcelFileDescriptor.createPipe()
        val stderrPipe = ParcelFileDescriptor.createPipe()
        try {
            assertTrue("Timed out binding the disposable Java worker", connectionLatch.await(10, TimeUnit.SECONDS))
            assertNull("Worker binding failed: ${connectionFailure.get()}", connectionFailure.get())
            val remote = checkNotNull(remoteReference.get()) { "Worker Binder was not delivered" }
            dexFiles.forEach { file ->
                dexDescriptors += ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            try {
                remote.execute(
                    JvmSourceCodec.encodeRequest(request),
                    generation,
                    0L,
                    classIdentity.sizeBytes,
                    classIdentity.sha256.toByteArray(),
                    dexIdentity.files.map(ProviderFileIdentity::name).toTypedArray(),
                    dexIdentity.files.map(ProviderFileIdentity::sizeBytes).toLongArray(),
                    dexIdentity.flattenedSha256(),
                    expectedClassDescriptors.toTypedArray(),
                    dexDescriptors.toTypedArray(),
                    stdoutPipe[1],
                    stderrPipe[1],
                    hostBridge,
                    callback,
                )
            } finally {
                dexDescriptors.forEach { runCatching { it.close() } }
                runCatching { stdoutPipe[1].close() }
                runCatching { stderrPipe[1].close() }
            }
            assertEquals(generation, ready.get()?.second)
            assertTrue("Worker readiness did not identify an isolated process", (ready.get()?.first ?: -1) > 0)
            assertTrue("Timed out waiting for worker completion", terminalLatch.await(30, TimeUnit.SECONDS))
            assertTrue("Timed out waiting for worker observation", observationLatch.await(10, TimeUnit.SECONDS))
            assertNull("Worker callback failed: ${callbackFailure.get()}", callbackFailure.get())
            val result = JvmSourceCodec.decodeResult(checkNotNull(encodedResult.get()))
            JvmSourceValidation.validateResultAgainstRuntime(result, request, Build.VERSION.SDK_INT)
            assertEquals(classIdentity.sizeBytes, result.classArtifactSizeBytes)
            assertEquals(classIdentity.sha256, result.classArtifactSha256)
            assertEquals(generation, result.workerGeneration)
            assertTrue(checkNotNull(observation.get()).loadDurationMillis != null)
            return result
        } finally {
            runCatching { stdoutPipe[0].close() }
            runCatching { stdoutPipe[1].close() }
            runCatching { stderrPipe[0].close() }
            runCatching { stderrPipe[1].close() }
            if (bound) runCatching { context.unbindService(connection) }
        }
    }

    private fun sourceOverDexMethodLimit(): String = buildString(3_000_000) {
        appendLine("public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {")
        appendLine("  public Main() {}")
        appendLine("  public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) {")
        appendLine("    return Integer.valueOf(Helper127.method511());")
        appendLine("  }")
        appendLine("}")
        repeat(HELPER_CLASS_COUNT) { classIndex ->
            appendLine("final class Helper${classIndex.toString().padStart(3, '0')} {")
            repeat(METHODS_PER_HELPER) { methodIndex ->
                append("  static int method")
                append(methodIndex.toString().padStart(3, '0'))
                append("() { return ")
                append(classIndex * METHODS_PER_HELPER + methodIndex)
                appendLine("; }")
            }
            appendLine("}")
        }
    }

    private object NoHostCallsContext : JvmScriptContext {
        private val app = object : JvmAppApi {
            override fun launch(packageName: String): Boolean = error("Multidex fixture must not call the host")
        }
        private val cancellation = object : JvmCancellation {
            override fun isCancellationRequested(): Boolean = false
            override fun throwIfCancellationRequested() = Unit
        }
        private val clipboard = object : JvmClipboardApi {
            override fun getText(): String = error("Multidex fixture must not read clipboard")
            override fun setText(text: String) = error("Multidex fixture must not write clipboard")
        }

        override fun app(): JvmAppApi = app
        override fun clipboard(): JvmClipboardApi = clipboard
        override fun console(): JvmConsoleApi = error("Multidex fixture must not use console")
        override fun cancellation(): JvmCancellation = cancellation
        override fun sleep(millis: Long) = error("Multidex fixture must not sleep")
        override fun toast(message: String) = error("Multidex fixture must not show toast")
    }

    private companion object {
        const val HELPER_CLASS_COUNT = 128
        const val METHODS_PER_HELPER = 512
        const val EXPECTED_RESULT = 65_535
        const val TAG = "JvmSourceR4MultiDex"
    }
}
