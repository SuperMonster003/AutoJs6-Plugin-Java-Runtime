package org.autojs.plugin.jvmsource.java

import org.eclipse.jdt.core.compiler.batch.BatchCompiler
import org.autojs.plugin.jvmsource.api.JvmSourceErrorCode
import org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase
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
    ): EcjCompilationResult {
        val diagnosticWriter = BoundedTextWriter(diagnosticByteLimit)
        val printWriter = PrintWriter(diagnosticWriter, true)
        ensureActive()
        val succeeded = try {
            BatchCompiler.compile(
                arguments(sourceFile, outputDirectory),
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

    internal fun arguments(sourceFile: File, outputDirectory: File): Array<String> = arrayOf(
        "-source", "8",
        "-target", "8",
        "-proc:none",
        "-encoding", "UTF-8",
        "-g:lines,vars,source",
        "-classpath", classpath.ecjClasspath,
        "-bootclasspath", classpath.ecjBootClasspath,
        "-d", outputDirectory.absolutePath,
        sourceFile.absolutePath,
    )

    companion object {
        val CACHE_OPTIONS_IDENTITY: List<String> = listOf(
            "source=8",
            "target=8",
            "annotation-processing=disabled",
            "encoding=UTF-8",
            "debug=lines,vars,source",
            "classpath=entry-api-only",
            "bootclasspath=controlled-android-stubs",
        )
    }
}
