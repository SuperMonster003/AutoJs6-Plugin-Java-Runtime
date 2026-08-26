package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmDiagnosticSeverity
import org.autojs.plugin.jvmsource.api.JvmSourceContract
import org.autojs.plugin.jvmsource.api.JvmSourcePackagePathPolicy
import java.io.File

internal data class SanitizedEcjDiagnostic(
    val severity: JvmDiagnosticSeverity,
    val code: String,
    val message: String,
    val sourceFileName: String?,
    val line: Int?,
    val column: Int?,
)

/**
 * Keeps the compiler's own wording so a user can actually fix the source, while removing every
 * provider-private path, signer digest, and process identity before the text crosses Protocol V1.
 * The private session path of the compilation unit is rewritten to the logical source file name.
 * zh-CN: 保留 ECJ 原始诊断文本 (用户据此修错), 但抹掉 provider 私有路径/签名/进程身份;
 * 编译单元的私有会话路径改写为逻辑源文件名.
 */
internal object EcjDiagnosticSanitizer {
    private val LINE = Regex("\\(at line ([1-9][0-9]*)\\)")
    private val DIAGNOSTIC_HEADER = Regex("(?m)^[0-9]+\\. (?:ERROR|WARNING) in ")
    private val DIAGNOSTIC_SEPARATOR = Regex("(?m)^-{10,}[\\t ]*$")
    private val ABSOLUTE_UNIX_PATH = Regex("(?:/[\\w.@$+~%-]+){2,}/?")
    private val ABSOLUTE_WINDOWS_PATH = Regex("[A-Za-z]:[\\\\/][^\\s\"'<>|]*")
    private val DIGEST_LIKE = Regex("\\b[0-9a-fA-F]{32,}\\b")
    private val PROCESS_IDENTITY = Regex("(?i)\\b(?:uid|pid)\\s*[=:]\\s*\\d+")
    private val BINDER_REFERENCE = Regex("(?i)\\bBinder@[0-9a-fA-F]+")
    private val SENSITIVE_METADATA = Regex("(?i)\\b(?:component|signer|classpath|uid|pid)\\s*[=:]")
    private val SENSITIVE_METADATA_LINE_TAIL =
        Regex("(?i)\\b(?:component|signer|classpath|uid|pid)\\s*[=:][^\\r\\n]*")
    private const val REDACTED = "<redacted>"
    private const val MAX_SOURCE_POSITION = 1_000_000

    fun sanitize(
        raw: String,
        succeeded: Boolean,
        byteLimit: Int,
        privateFiles: Collection<File>,
        sourceFile: File? = null,
        sourceFileName: String? = null,
        sourceFiles: Map<File, String> = emptyMap(),
    ): SanitizedEcjDiagnostic? = sanitizeAll(
        raw = raw,
        succeeded = succeeded,
        byteLimit = byteLimit,
        privateFiles = privateFiles,
        sourceFile = sourceFile,
        sourceFileName = sourceFileName,
        sourceFiles = sourceFiles,
    ).firstOrNull()

    /** Returns one ordered wire candidate for every ECJ ERROR/WARNING block in the batch output. */
    fun sanitizeAll(
        raw: String,
        succeeded: Boolean,
        byteLimit: Int,
        privateFiles: Collection<File>,
        sourceFile: File? = null,
        sourceFileName: String? = null,
        sourceFiles: Map<File, String> = emptyMap(),
    ): List<SanitizedEcjDiagnostic> {
        if (raw.isBlank()) return emptyList()
        return diagnosticBlocks(raw).map { block ->
            sanitizeBlock(
                raw = block,
                succeeded = succeeded,
                byteLimit = byteLimit,
                privateFiles = privateFiles,
                sourceFile = sourceFile,
                sourceFileName = sourceFileName,
                sourceFiles = sourceFiles,
            )
        }
    }

    private fun sanitizeBlock(
        raw: String,
        succeeded: Boolean,
        byteLimit: Int,
        privateFiles: Collection<File>,
        sourceFile: File?,
        sourceFileName: String?,
        sourceFiles: Map<File, String>,
    ): SanitizedEcjDiagnostic {
        val parsedLine = LINE.find(raw)?.groupValues?.get(1)?.toIntOrNull()
            ?.takeIf { it in 1..MAX_SOURCE_POSITION }
        val parsedColumn = raw.lineSequence()
            .firstOrNull { '^' in it }
            ?.indexOf('^')
            ?.takeIf { it >= 0 }
            ?.plus(1)
            ?.takeIf { it in 1..MAX_SOURCE_POSITION }
        val line = parsedLine?.takeIf { parsedColumn != null }
        val column = parsedColumn?.takeIf { parsedLine != null }
        val severity = when {
            Regex("(?m)^[0-9]+\\. ERROR in ").containsMatchIn(raw) -> JvmDiagnosticSeverity.ERROR
            Regex("(?m)^[0-9]+\\. WARNING in ").containsMatchIn(raw) -> JvmDiagnosticSeverity.WARNING
            succeeded -> JvmDiagnosticSeverity.INFO
            else -> JvmDiagnosticSeverity.ERROR
        }
        val code = when (severity) {
            JvmDiagnosticSeverity.ERROR -> "ECJ_ERROR"
            JvmDiagnosticSeverity.WARNING -> "ECJ_WARNING"
            JvmDiagnosticSeverity.INFO -> "ECJ_INFO"
        }
        val logicalSources = linkedMapOf<File, String>().apply {
            if (sourceFile != null && sourceFileName != null) put(sourceFile, sourceFileName)
            putAll(sourceFiles)
        }
        val locatedSourceName = logicalSources.entries
            .filter { (file, logicalName) ->
                isSafeSourceFileName(logicalName) && pathVariants(file).any(raw::contains)
            }
            .maxByOrNull { (file, _) -> pathVariants(file).maxOfOrNull(String::length) ?: 0 }
            ?.value
        val redacted = redact(raw, privateFiles, logicalSources)
        val message = if (SENSITIVE_METADATA.containsMatchIn(redacted)) {
            // A label surviving the line-tail pass means its value could not be bounded safely.
            fallback(severity)
        } else {
            val bounded = BoundedTextWriter(byteLimit).also { it.write(redacted) }.value()
            limitCodePoints(bounded, JvmSourceContract.MAX_DIAGNOSTIC_MESSAGE_CODE_POINTS)
                .ifBlank { fallback(severity) }
        }
        return SanitizedEcjDiagnostic(
            severity = severity,
            code = code,
            message = message,
            sourceFileName = locatedSourceName,
            line = line,
            column = column,
        )
    }

    private fun diagnosticBlocks(raw: String): List<String> {
        val headers = DIAGNOSTIC_HEADER.findAll(raw).toList()
        if (headers.isEmpty()) return listOf(raw)
        return headers.mapIndexedNotNull { index, header ->
            val end = headers.getOrNull(index + 1)?.range?.first ?: raw.length
            val candidate = raw.substring(header.range.first, end)
            val separator = DIAGNOSTIC_SEPARATOR.find(candidate)?.range?.first ?: candidate.length
            candidate.substring(0, separator).trim().takeIf(String::isNotBlank)
        }
    }

    /**
     * Longest known private path first: replacing a parent directory before its children would
     * leave a partially rewritten path that no longer matches the remaining known prefixes.
     */
    private fun redact(
        raw: String,
        privateFiles: Collection<File>,
        sourceFiles: Map<File, String>,
    ): String {
        val replacements = LinkedHashMap<String, String>()
        sourceFiles.forEach { (sourceFile, sourceFileName) ->
            val logicalName = sourceFileName.takeIf(::isSafeSourceFileName) ?: return@forEach
            pathVariants(sourceFile).forEach { replacements[it] = logicalName }
        }
        privateFiles.forEach { file ->
            pathVariants(file).forEach { path -> replacements.putIfAbsent(path, REDACTED) }
        }
        var text = raw
        replacements.entries
            .sortedByDescending { it.key.length }
            .forEach { (path, replacement) -> text = text.replace(path, replacement) }
        // Defense in depth: any remaining absolute path or identity token is provider-private
        // even when this process did not construct it.
        text = ABSOLUTE_WINDOWS_PATH.replace(text, REDACTED)
        text = ABSOLUTE_UNIX_PATH.replace(text, REDACTED)
        text = DIGEST_LIKE.replace(text, REDACTED)
        text = PROCESS_IDENTITY.replace(text, REDACTED)
        text = BINDER_REFERENCE.replace(text, REDACTED)
        // Metadata values can contain arbitrary punctuation and path lists. Redact from the label
        // through the end of that line, retaining the actionable compiler text around other lines.
        text = SENSITIVE_METADATA_LINE_TAIL.replace(text, REDACTED)
        return text.trim()
    }

    private fun pathVariants(file: File): Set<String> = buildSet {
        add(file.path)
        add(file.absolutePath)
        runCatching { file.canonicalPath }.getOrNull()?.let(::add)
    }.filter { it.length > 1 }.toSet()

    private fun isSafeSourceFileName(value: String): Boolean =
        value.isNotBlank() && value.length <= JvmSourceContract.MAX_SOURCE_FILE_NAME_BYTES &&
            (value !in setOf(".", "..") && '/' !in value && '\\' !in value && '\u0000' !in value ||
                runCatching { JvmSourcePackagePathPolicy.requireSourcePath(value) }.isSuccess)

    private fun limitCodePoints(value: String, maximum: Int): String {
        val count = value.codePointCount(0, value.length)
        if (count <= maximum) return value
        return value.substring(0, value.offsetByCodePoints(0, maximum - 1)) + "~"
    }

    private fun fallback(severity: JvmDiagnosticSeverity): String = when (severity) {
        JvmDiagnosticSeverity.ERROR -> "Java compilation error"
        JvmDiagnosticSeverity.WARNING -> "Java compiler warning"
        JvmDiagnosticSeverity.INFO -> "Java compiler diagnostic"
    }
}
