package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CompilerArgumentsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun ecjUsesFixedTokensWithoutEnvironmentClasspathOrStringQuoting() {
        val root = temporaryFolder.newFolder("path with spaces")
        val androidJar = root.resolve("android.jar")
        val entryApiJar = root.resolve("entry api --flag.jar")
        val identity = JvmSha256.digest(byteArrayOf(1))
        val classpath = CompilerClasspath(
            androidJar,
            entryApiJar,
            identities = listOf(
                ProviderFileIdentity("android.jar", 1L, identity),
                ProviderFileIdentity("entry-api.jar", 1L, identity),
            ),
            fingerprint = identity,
        )
        val source = root.resolve("Main.java")
        val output = root.resolve("classes --output")

        assertArrayEquals(
            arrayOf(
                "-source", "8",
                "-target", "8",
                "-proc:none",
                "-encoding", "UTF-8",
                "-g:lines,vars,source",
                "-classpath", entryApiJar.absolutePath,
                "-bootclasspath", androidJar.absolutePath,
                "-d", output.absolutePath,
                source.absolutePath,
            ),
            EcjJavaCompiler(classpath).arguments(source, output),
        )
    }

    @Test
    fun d8UsesOnlyControlledLibrariesAndKeepsEveryPathAsOneArgument() {
        val root = temporaryFolder.newFolder("d8 path with spaces")
        val androidJar = root.resolve("android --lib.jar")
        val entryApiJar = root.resolve("entry api.jar")
        val fingerprint = JvmSha256.digest(byteArrayOf(2))
        val libraries = D8RuntimeLibraries(
            files = listOf(androidJar, entryApiJar),
            identities = emptyList(),
            fingerprint = fingerprint,
        )
        val program = root.resolve("program --min-api.jar")
        val output = root.resolve("d8 output")

        val arguments = D8JavaCompiler(libraries).arguments(program, output, 24)

        assertEquals(program.absolutePath, arguments.last())
        assertTrue(arguments.contains(androidJar.absolutePath))
        assertTrue(arguments.contains(entryApiJar.absolutePath))
        assertEquals(2, arguments.count { it == "--lib" })
        assertEquals(1, arguments.count { it == "--min-api" })
        assertEquals("24", arguments[arguments.indexOf("--min-api") + 1])
        assertTrue("dex-output-profile=r4-contiguous" in D8JavaCompiler.optionsIdentity(24))
        assertTrue("max-dex-files=4" in D8JavaCompiler.optionsIdentity(24))
    }

    @Test
    fun compilerClasspathRevalidatesItsPinnedRuntimeIdentity() {
        val root = temporaryFolder.newFolder("pinned classpath")
        val androidJar = root.resolve("android.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val entryApiJar = root.resolve("entry-api.jar").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val identities = listOf(ProviderDigests.file(androidJar), ProviderDigests.file(entryApiJar))
        val classpath = CompilerClasspath(
            androidJar,
            entryApiJar,
            identities,
            ProviderDigests.combine(CompilerClasspath.COMPILER_CLASSPATH_DOMAIN, identities),
        )

        classpath.verifyInstalled()
        entryApiJar.writeBytes(byteArrayOf(9))

        assertThrows(JavaProviderFailure::class.java) { classpath.verifyInstalled() }
    }
}
