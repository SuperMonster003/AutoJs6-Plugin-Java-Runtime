package org.autojs.plugin.jvmsource.java

import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry
import org.autojs.plugin.jvmsource.api.JvmAppApi
import org.autojs.plugin.jvmsource.api.JvmCancellation
import org.autojs.plugin.jvmsource.api.JvmClipboardApi
import org.autojs.plugin.jvmsource.api.JvmConsoleApi
import org.autojs.plugin.jvmsource.api.JvmDexRuntimeProfile
import org.autojs.plugin.jvmsource.api.JvmScriptContext
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.java.worker.WorkerDexLoader
import org.autojs.plugin.jvmsource.java.worker.WorkerDexLoadStage
import org.autojs.plugin.jvmsource.java.worker.WorkerEntryFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileOutputStream
import java.util.UUID

/**
 * Provider-internal Android runtime smoke only. Canonical host-to-provider Binder evidence lives in
 * the host application's instrumentation suite so this test cannot weaken production caller checks.
 */
@RunWith(AndroidJUnit4::class)
class JavaProviderPipelineInstrumentedTest {
    @Test
    fun ecjD8DexValidationAndEntryInvocationRunOnAndroid() {
        runPipeline(PipelineEntryLayout(simpleName = "Main", packageName = null))
    }

    @Test
    fun nonMainSimpleAndPackageLayoutsRunThroughEcjD8AndArt() {
        listOf(
            PipelineEntryLayout(simpleName = "ScriptEntry", packageName = null),
            PipelineEntryLayout(simpleName = "ScriptEntry", packageName = "com.example.scripts"),
            PipelineEntryLayout(simpleName = "\$Entry9", packageName = "_root.\$generated.p9"),
        ).forEach(::runPipeline)
    }

    private fun runPipeline(layout: PipelineEntryLayout) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val environment = JavaProviderEnvironment.get(context)
        val source = layout.source()
        val normalizedSource = JavaSourcePolicy.decodeAndValidate(
            bytes = source.toByteArray(Charsets.UTF_8),
            sourceFileName = layout.sourceFileName,
            entryClassName = layout.entryClassName,
        )
        PrivateSessionWorkspace.create(context, layout.sourceFileName).use { workspace ->
            FileOutputStream(workspace.sourceFile).use { output ->
                output.write(normalizedSource.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }

            val ecj = EcjJavaCompiler(environment.compilerClasspath).compile(
                sourceFile = workspace.sourceFile,
                outputDirectory = workspace.classesDirectory,
                diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
                ensureActive = {},
            )
            assertTrue(
                "ECJ must compile ${layout.entryClassName}: ${ecj.diagnostics}",
                ecj.succeeded,
            )

            val classes = UserClassJarWriter.write(
                workspace.classesDirectory,
                workspace.programJar,
                layout.entryClassName,
            )
            assertEquals(
                layout.entryClassName,
                setOf("L${layout.entryClassName.replace('.', '/')};"),
                classes.dexDescriptors,
            )
            val dexFiles = D8JavaCompiler(environment.d8RuntimeLibraries).compile(
                programJar = workspace.programJar,
                outputDirectory = workspace.d8OutputDirectory,
                minApi = JvmSourceContract.MIN_ANDROID_API,
                ensureActive = {},
            )
            val dexIdentity = ProviderDexSetIdentity.fromFiles(dexFiles)
            dexFiles.forEach { dexFile ->
                assertTrue("Generated ${dexFile.name} must be frozen before dynamic loading", dexFile.setReadOnly())
            }

            val descriptors = dexFiles.map { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }
            val loader = WorkerDexLoader(context)
            val validatedDex = loader.validateStructure(
                descriptors = descriptors,
                expectedIdentity = dexIdentity,
                expectedClassDescriptors = classes.dexDescriptors,
                requestMinApi = JvmSourceContract.MIN_ANDROID_API,
                ensureActive = {},
            )
            assertEquals(WorkerDexLoadStage.STRUCTURE_VALIDATED, validatedDex.stage)
            loader.createClassLoader(
                validated = validatedDex,
                generation = 1L,
                requestId = UUID.randomUUID().toString(),
                parent = AutoJsJvmEntry::class.java.classLoader!!,
            ).use { loaded ->
                assertEquals(WorkerDexLoadStage.ART_CLASS_LOADER_CREATED, loaded.stage)
                assertEquals(loaded.validatedArtifacts.loaderKind, loaded.actualLoaderKind)
                assertEquals(
                    DexRuntimePolicy.loaderKind(android.os.Build.VERSION.SDK_INT),
                    loaded.validatedArtifacts.loaderKind,
                )
                assertEquals(
                    JvmDexRuntimeProfile.loaderKindForApi(android.os.Build.VERSION.SDK_INT),
                    loaded.actualLoaderKind.apiKind,
                )
                assertTrue(
                    loaded.validatedArtifacts.version in
                        JvmDexRuntimeProfile.admittedVersions(android.os.Build.VERSION.SDK_INT),
                )
                val loadedEntry = WorkerEntryFactory.loadFromArt(
                    loaded.classLoader,
                    layout.entryClassName,
                )
                assertEquals(WorkerDexLoadStage.ART_ENTRY_CLASS_LOADED, loadedEntry.stage)
                val entry = WorkerEntryFactory.instantiate(loadedEntry)
                assertEquals(layout.entryClassName, entry.run(NoHostCallsContext))
            }
        }
    }

    private object NoHostCallsContext : JvmScriptContext {
        private val app = object : JvmAppApi {
            override fun launch(packageName: String): Boolean =
                error("The internal smoke source must not issue host calls")
        }
        private val cancellation = object : JvmCancellation {
            override fun isCancellationRequested(): Boolean = false

            override fun throwIfCancellationRequested() = Unit
        }
        private val clipboard = object : JvmClipboardApi {
            override fun getText(): String = error("The internal smoke source must not read clipboard")

            override fun setText(text: String) = error("The internal smoke source must not write clipboard")
        }

        override fun app(): JvmAppApi = app

        override fun args(): Map<String, Any?> = emptyMap()

        override fun clipboard(): JvmClipboardApi = clipboard

        override fun console(): JvmConsoleApi = error("The internal smoke source must not use console")

        override fun cancellation(): JvmCancellation = cancellation

        override fun sleep(millis: Long) = error("The internal smoke source must not sleep")

        override fun toast(message: String) = error("The internal smoke source must not show toast")
    }

    private data class PipelineEntryLayout(
        val simpleName: String,
        val packageName: String?,
    ) {
        val sourceFileName: String = "$simpleName.java"
        val entryClassName: String = packageName?.let { "$it.$simpleName" } ?: simpleName

        fun source(): String = buildString {
            packageName?.let { appendLine("package $it;") }
            appendLine(
                "public final class $simpleName implements " +
                    "org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {",
            )
            appendLine("    @Override")
            appendLine(
                "    public Object run(" +
                    "org.autojs.plugin.jvmsource.api.JvmScriptContext context) {",
            )
            appendLine("        return \"$entryClassName\";")
            appendLine("    }")
            append('}')
        }
    }
}
