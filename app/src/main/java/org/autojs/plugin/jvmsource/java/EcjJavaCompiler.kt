package org.autojs.plugin.jvmsource.java

import org.eclipse.jdt.core.compiler.batch.BatchCompiler
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import java.io.File
import java.io.PrintWriter

internal data class EcjCompilationResult(
    val succeeded: Boolean,
    val diagnostics: String,
)

internal class EcjJavaCompiler(private val classpath: CompilerClasspath) {
    fun compile(
        sourceFile: File,
        outputDirectory: File,
        diagnosticByteLimit: Int,
        ensureActive: () -> Unit,
    ): EcjCompilationResult = compile(
        sourceFiles = listOf(sourceFile),
        outputDirectory = outputDirectory,
        diagnosticByteLimit = diagnosticByteLimit,
        ensureActive = ensureActive,
    )

    fun compile(
        sourceFiles: List<File>,
        outputDirectory: File,
        diagnosticByteLimit: Int,
        ensureActive: () -> Unit,
    ): EcjCompilationResult {
        require(sourceFiles.isNotEmpty() && sourceFiles.size <= JvmSourceContract.MAX_SOURCE_FILES)
        val diagnosticWriter = BoundedTextWriter(diagnosticByteLimit)
        val printWriter = PrintWriter(diagnosticWriter, true)
        ensureActive()
        val succeeded = try {
            BatchCompiler.compile(
                arguments(sourceFiles, outputDirectory),
                printWriter,
                printWriter,
                null,
            )
        } catch (error: Throwable) {
            throw JavaProviderFailure(
                JvmSourceErrorCode.COMPILATION_FAILED,
                JvmSourceFailurePhase.COMPILATION,
                error.message ?: "ECJ failed to compile Java source",
                error,
            )
        } finally {
            printWriter.flush()
        }
        ensureActive()
        return EcjCompilationResult(succeeded, diagnosticWriter.value())
    }

    internal fun arguments(sourceFile: File, outputDirectory: File): Array<String> =
        arguments(listOf(sourceFile), outputDirectory)

    internal fun arguments(sourceFiles: List<File>, outputDirectory: File): Array<String> = arrayOf(
        "-source", "8",
        "-target", "8",
        "-proc:none",
        "-encoding", "UTF-8",
        "-g:lines,vars,source",
        "-classpath", classpath.ecjClasspath,
        "-bootclasspath", classpath.ecjBootClasspath,
        "-d", outputDirectory.absolutePath,
        *sourceFiles.map(File::getAbsolutePath).toTypedArray(),
    )

    companion object {
        val CACHE_OPTIONS_IDENTITY: List<String> = listOf(
            "source=8",
            "target=8",
            "annotation-processing=disabled",
            "encoding=UTF-8",
            "debug=lines,vars,source",
            "source-input=canonical-ordered-file-set-v1",
            "classpath=entry-api-only",
            "bootclasspath=controlled-core-library-stubs-plus-api-24-android",
            "core-library-visible=java-time-api-26-and-supported-stream-2.1.5",
        )
    }
}
