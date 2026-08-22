package org.autojs.plugin.jvmsource.java

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class JavaSourcePolicyTest {
    @Test
    fun acceptsDefaultPackageAndIgnoresDeclarationWordsInCommentsAndLiterals() {
        val source = """
            /* package hidden; */
            public final class Main {
                String first = "import hidden.Type;";
                char second = 'p'; // import hidden.Other;
            }
        """.trimIndent()

        assertEquals(source, JavaSourcePolicy.decodeAndValidate(source.toByteArray()))
    }

    @Test
    fun acceptsPackageAndImportsWhenTheyMatchTheRequestedEntry() {
        val source = """
            package com.example.scripts;
            import java.util.List;
            import static java.util.Collections.emptyList;
            public final class Main { List<?> value = emptyList(); }
        """.trimIndent()

        assertEquals(
            source,
            JavaSourcePolicy.decodeAndValidate(
                source.toByteArray(),
                sourceFileName = "Main.java",
                entryClassName = "com.example.scripts.Main",
            ),
        )
    }

    @Test
    fun stripsOnlyAnInitialUtf8Bom() {
        val source = "\uFEFFpublic final class Main {}"

        assertEquals(
            "public final class Main {}",
            JavaSourcePolicy.decodeAndValidate(source.toByteArray()),
        )
    }

    @Test
    fun rejectsUnicodeEscapeNulMalformedUtf8AndRequestLayoutMismatch() {
        listOf(
            "\\u0069mport java.util.List; public final class Main {}".toByteArray(),
            "public final class Main { String text = \"\\u0069\"; }".toByteArray(),
            "public final class Main {}\u0000".toByteArray(),
            byteArrayOf(0xc3.toByte(), 0x28),
        ).forEach { source ->
            assertThrows(JavaProviderFailure::class.java) {
                JavaSourcePolicy.decodeAndValidate(source)
            }
        }
        assertThrows(JavaProviderFailure::class.java) {
            JavaSourcePolicy.decodeAndValidate(
                "package actual; public final class Main {}".toByteArray(),
                sourceFileName = "Main.java",
                entryClassName = "claimed.Main",
            )
        }
    }

    @Test
    fun innerLambdaAndDefaultOrStaticInterfaceSyntaxRemainSourceCandidatesOnly() {
        val candidate = """
            public final class Main implements org.autojs.plugin.jvmsource.api.AutoJsJvmEntry {
                interface Helper {
                    default int value() { return 1; }
                    static int other() { return 2; }
                }
                static final class Inner implements Helper {}
                public Object run(org.autojs.plugin.jvmsource.api.JvmScriptContext context) {
                    java.util.function.IntSupplier supplier = () -> new Inner().value() + Helper.other();
                    return supplier.getAsInt();
                }
            }
        """.trimIndent()

        // This is lexical admission only. ECJ -> D8 -> ART coverage remains device evidence.
        assertEquals(candidate, JavaSourcePolicy.decodeAndValidate(candidate.toByteArray()))
    }
}
