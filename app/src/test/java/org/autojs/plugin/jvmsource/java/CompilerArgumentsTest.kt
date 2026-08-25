package org.autojs.plugin.jvmsource.java

import org.autojs.plugin.jvmsource.api.JvmSha256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CompilerArgumentsTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun ecjUsesFixedTokensWithoutEnvironmentClasspathOrStringQuoting() {
        val root = temporaryFolder.newFolder("path with spaces")
        val androidJar = root.resolve("android.jar")
        val entryApiJar = root.resolve("entry api --flag.jar")
        val coreLibraryStubsJar = root.resolve("core library stubs.jar")
        val d8JavaApiStubsJar = root.resolve("d8 java stubs.jar")
        val desugarConfiguration = root.resolve("desugar.json")
        val identity = JvmSha256.digest(byteArrayOf(1))
        val classpath = CompilerClasspath(
            androidJar = androidJar,
            entryApiJar = entryApiJar,
            coreLibraryStubsJar = coreLibraryStubsJar,
            d8JavaApiStubsJar = d8JavaApiStubsJar,
            desugaredLibraryConfiguration = desugarConfiguration,
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
                "-bootclasspath",
                coreLibraryStubsJar.absolutePath + File.pathSeparator + androidJar.absolutePath,
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
        val d8JavaApiStubsJar = root.resolve("d8 java api --lib.jar")
        val entryApiJar = root.resolve("entry api.jar")
        val desugarConfiguration = root.resolve("desugar config --lib.json")
        val fingerprint = JvmSha256.digest(byteArrayOf(2))
        val libraries = D8RuntimeLibraries(
            files = listOf(androidJar, d8JavaApiStubsJar, entryApiJar),
            desugaredLibraryConfiguration = desugarConfiguration,
            identities = emptyList(),
            fingerprint = fingerprint,
        )
        val program = root.resolve("program --min-api.jar")
        val output = root.resolve("d8 output")

        val arguments = D8JavaCompiler(libraries).arguments(program, output, 24)

        assertEquals(program.absolutePath, arguments.last())
        assertTrue(arguments.contains(androidJar.absolutePath))
        assertTrue(arguments.contains(d8JavaApiStubsJar.absolutePath))
        assertTrue(arguments.contains(entryApiJar.absolutePath))
        assertEquals(
            desugarConfiguration.absolutePath,
            arguments[arguments.indexOf("--desugared-lib") + 1],
        )
        assertEquals(3, arguments.count { it == "--lib" })
        assertEquals(1, arguments.count { it == "--desugared-lib" })
        assertEquals(1, arguments.count { it == "--min-api" })
        assertEquals("24", arguments[arguments.indexOf("--min-api") + 1])
        assertTrue("dex-output-profile=r4-contiguous" in D8JavaCompiler.optionsIdentity(24))
        assertTrue("max-dex-files=4" in D8JavaCompiler.optionsIdentity(24))
        assertTrue(
            "core-library-desugaring=desugar_jdk_libs_configuration_nio-2.1.5" in
                D8JavaCompiler.optionsIdentity(24),
        )
    }

    @Test
    fun compilerClasspathRevalidatesItsPinnedRuntimeIdentity() {
        val root = temporaryFolder.newFolder("pinned classpath")
        val androidJar = root.resolve("android.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val entryApiJar = root.resolve("entry-api.jar").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        val coreLibraryStubsJar =
            root.resolve("core-library-stubs.jar").apply { writeBytes(byteArrayOf(7)) }
        val d8JavaApiStubsJar =
            root.resolve("d8-java-api30-stubs.jar").apply { writeBytes(byteArrayOf(8)) }
        val desugarConfiguration =
            root.resolve("desugar.json").apply { writeBytes(byteArrayOf(9)) }
        val installedFiles = listOf(
            androidJar,
            entryApiJar,
            coreLibraryStubsJar,
            d8JavaApiStubsJar,
            desugarConfiguration,
        )
        val identities = installedFiles.map(ProviderDigests::file)
        val classpath = CompilerClasspath(
            androidJar = androidJar,
            entryApiJar = entryApiJar,
            coreLibraryStubsJar = coreLibraryStubsJar,
            d8JavaApiStubsJar = d8JavaApiStubsJar,
            desugaredLibraryConfiguration = desugarConfiguration,
            identities = identities,
            fingerprint = ProviderDigests.combine(
                CompilerClasspath.COMPILER_CLASSPATH_DOMAIN,
                identities,
            ),
        )

        classpath.verifyInstalled()
        val d8Libraries = D8RuntimeLibraries.controlled(classpath)
        assertEquals(
            listOf(androidJar, d8JavaApiStubsJar, entryApiJar),
            d8Libraries.files,
        )
        assertEquals(desugarConfiguration, d8Libraries.desugaredLibraryConfiguration)
        assertEquals(classpath.fingerprint, d8Libraries.fingerprint)
        desugarConfiguration.writeBytes(byteArrayOf(10))

        assertThrows(JavaProviderFailure::class.java) { classpath.verifyInstalled() }
    }
}
