package org.autojs.plugin.jvmsource.java

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.Adler32
import javax.tools.ToolProvider

internal object CacheTestArtifacts {
    fun java8MainClass(root: File, packageName: String? = null): ByteArray {
        val source = root.resolve("Main.java").apply {
            writeText(
                """
                ${packageName?.let { "package $it;" }.orEmpty()}
                import org.autojs.plugin.jvmsource.api.AutoJsJvmEntry;
                import org.autojs.plugin.jvmsource.api.JvmScriptContext;
                public final class Main implements AutoJsJvmEntry {
                    public Main() {}
                    public Object run(JvmScriptContext context) { return Boolean.TRUE; }
                }
                """.trimIndent(),
            )
        }
        val output = root.resolve("test-classes").apply { check(mkdir()) }
        val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) { "Unit tests require a JDK compiler" }
        val exit = compiler.run(
            null,
            null,
            null,
            "-source",
            "8",
            "-target",
            "8",
            "-proc:none",
            "-classpath",
            System.getProperty("java.class.path"),
            "-d",
            output.absolutePath,
            source.absolutePath,
        )
        check(exit == 0) { "Unable to create the Java 8 cache test artifact" }
        val packagePath = packageName?.replace('.', File.separatorChar).orEmpty()
        return output.resolve(packagePath).resolve("Main.class").readBytes()
    }

    fun minimalDex(descriptor: String = "LMain;"): ByteArray {
        require(descriptor.length < 128 && descriptor.startsWith('L') && descriptor.endsWith(';'))
        val stringOffset = 152
        val stringEnd = stringOffset + 1 + descriptor.length + 1
        val mapOffset = (stringEnd + 3) and -4
        val bytes = ByteArray(mapOffset + 4 + 6 * 12)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("dex\n035\u0000".toByteArray(Charsets.US_ASCII))
        buffer.putInt(32, bytes.size)
        buffer.putInt(36, 0x70)
        buffer.putInt(40, 0x12345678)
        buffer.putInt(52, mapOffset)
        buffer.putInt(56, 1)
        buffer.putInt(60, 112)
        buffer.putInt(64, 1)
        buffer.putInt(68, 116)
        buffer.putInt(96, 1)
        buffer.putInt(100, 120)
        buffer.putInt(104, bytes.size - stringOffset)
        buffer.putInt(108, 152)
        buffer.putInt(112, 152)
        buffer.putInt(116, 0)
        buffer.putInt(120, 0)
        buffer.putInt(124, 1)
        buffer.putInt(128, -1)
        buffer.putInt(136, -1)
        bytes[stringOffset] = descriptor.length.toByte()
        descriptor.toByteArray(Charsets.US_ASCII).copyInto(bytes, stringOffset + 1)
        bytes[stringEnd - 1] = 0
        buffer.putInt(mapOffset, 6)
        var cursor = mapOffset + 4
        fun map(type: Int, size: Int, offset: Int) {
            buffer.putShort(cursor, type.toShort())
            buffer.putShort(cursor + 2, 0)
            buffer.putInt(cursor + 4, size)
            buffer.putInt(cursor + 8, offset)
            cursor += 12
        }
        map(0x0000, 1, 0)
        map(0x0001, 1, 112)
        map(0x0002, 1, 116)
        map(0x0006, 1, 120)
        map(0x2002, 1, stringOffset)
        map(0x1000, 1, mapOffset)
        val signature = MessageDigest.getInstance("SHA-1").digest(bytes.copyOfRange(32, bytes.size))
        signature.copyInto(bytes, 12)
        val checksum = Adler32().apply { update(bytes, 12, bytes.size - 12) }.value
        buffer.putInt(8, checksum.toInt())
        return bytes
    }
}
