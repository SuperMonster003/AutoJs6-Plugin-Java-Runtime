package org.autojs.plugin.jvmsource.java

internal data class JavaEntryLayoutTestCase(
    val simpleName: String,
    val packageName: String?,
) {
    val sourceFileName: String = "$simpleName.java"
    val entryClassName: String = packageName?.let { "$it.$simpleName" } ?: simpleName
    val internalName: String = entryClassName.replace('.', '/')
    val dexDescriptor: String = "L$internalName;"

    fun source(): String = buildString {
        packageName?.let { appendLine("package $it;") }
        appendLine("import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;")
        appendLine("import org.autojs.plugin.jvmsource.api.JvmScriptContext;")
        appendLine("public final class $simpleName implements AutoJsJvmEntry {")
        appendLine("    public $simpleName() {}")
        appendLine("    public Object run(JvmScriptContext context) { return Boolean.TRUE; }")
        append('}')
    }
}

/** Cartesian coverage of representative non-default identifier shapes admitted by R1. */
internal val JAVA_NON_MAIN_ENTRY_LAYOUT_CASES: List<JavaEntryLayoutTestCase> =
    listOf("ScriptEntry", "_Entry9", "\$Entry").flatMap { simpleName ->
        listOf<String?>(null, "com.example.scripts", "_root.\$generated.p9").map { packageName ->
            JavaEntryLayoutTestCase(simpleName, packageName)
        }
    }
