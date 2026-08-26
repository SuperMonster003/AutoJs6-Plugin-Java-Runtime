import groovy.json.JsonSlurper
import com.android.build.gradle.internal.tasks.L8DexDesugarLibTask
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

plugins {
    id("org.autojs.build.versions")
    id("org.autojs.build.jvm-convention")
    id("com.android.application")
}

val ecjVersion = libs.versions.ecj.get()
val d8Version = libs.versions.r8.get()
val globalApplicationId = "io.github.supermonster003.autojs6.plugin.java.runtime"
val providerNamespace = "org.autojs.plugin.jvmsource.java"
val compilerStubApi = 24
val coreLibraryTimeStubApi = 26
val coreLibraryStreamStubApi = 34
val d8JavaStubApi = 30
val protocolLockFile = rootProject.file("protocol/protocol-artifacts.lock.json")
val expectedProtocolModules = linkedMapOf(
    "common-plugin-api.aar" to ":plugin-api:common-plugin-api",
    "protocol-wire-api.aar" to ":plugin-api:protocol-wire-api",
    "jvm-source-api.aar" to ":plugin-api:jvm-source-api",
)
val protocolArtifacts = expectedProtocolModules.keys.map { rootProject.file("protocol/$it") }
val jvmSourceApiAar = rootProject.file("protocol/jvm-source-api.aar")
val expectedPinnedDependencies = linkedMapOf(
    "AndroidX annotations" to (libs.androidx.annotation to "androidx.annotation:annotation:1.9.1"),
    "Kotlin standard library" to (libs.kotlin.stdlib to "org.jetbrains.kotlin:kotlin-stdlib:2.3.21"),
    "ECJ" to (libs.ecj to "org.eclipse.jdt:ecj:3.26.0"),
    "D8/R8" to (libs.r8 to "com.android.tools:r8:8.13.17"),
    "core library desugaring" to
        (libs.desugar.jdk.libs.nio to "com.android.tools:desugar_jdk_libs_nio:2.1.5"),
    "core library desugaring configuration" to
        (libs.desugar.jdk.libs.configuration.nio to
            "com.android.tools:desugar_jdk_libs_configuration_nio:2.1.5"),
)
val generatedCompilerClasspathAssets = layout.buildDirectory.dir("generated/assets/compilerClasspath")
val userCodeDesugaringConfiguration = configurations.create("userCodeDesugaringConfiguration") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}
val entryApiClassEntries = linkedSetOf(
    "org/autojs/plugin/jvmsource/api/AutoJsJvmEntry.class",
    "org/autojs/plugin/jvmsource/api/JvmScriptContext.class",
    "org/autojs/plugin/jvmsource/api/JvmScriptContext\$DefaultImpls.class",
    "org/autojs/plugin/jvmsource/api/JvmAppApi.class",
    "org/autojs/plugin/jvmsource/api/JvmClipboardApi.class",
    "org/autojs/plugin/jvmsource/api/JvmConsoleApi.class",
    "org/autojs/plugin/jvmsource/api/JvmCancellation.class",
    "org/autojs/plugin/jvmsource/api/JvmCancellationException.class",
)

data class HostAlignedSigningMaterial(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun requiredSigningValue(properties: Properties, key: String): String =
    properties.getProperty(key)?.trim()?.takeIf(String::isNotEmpty)
        ?: throw GradleException("sign.properties is missing '$key'")

fun loadHostAlignedSigningMaterial(): HostAlignedSigningMaterial {
    val propertiesFile = rootProject.file("sign.properties")
    if (!propertiesFile.isFile) {
        throw GradleException("Host-aligned signing requires ${propertiesFile.absolutePath}")
    }
    val properties = Properties().apply { propertiesFile.inputStream().use(::load) }
    val storePath = requiredSigningValue(properties, "storeFile")
    val storeFile = project.file(storePath).canonicalFile
    if (!storeFile.isFile) {
        throw GradleException("Host-aligned signing store is missing: $storeFile")
    }
    return HostAlignedSigningMaterial(
        storeFile = storeFile,
        storePassword = requiredSigningValue(properties, "storePassword"),
        keyAlias = requiredSigningValue(properties, "keyAlias"),
        keyPassword = requiredSigningValue(properties, "keyPassword"),
    )
}

val hostAlignedSigning = loadHostAlignedSigningMaterial()

android {
    namespace = providerNamespace
    compileSdk = versions.sdkVersionCompile

    defaultConfig {
        applicationId = globalApplicationId
        minSdk = versions.sdkVersionMin
        targetSdk = versions.sdkVersionTarget
        versionCode = versions.appVersionCode
        versionName = versions.appVersionName
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "HOST_PACKAGE_NAME", "\"org.autojs.autojs6\"")
        buildConfigField(
            "long",
            "MIN_HOST_VERSION_CODE",
            "${versions["REQUIRED_HOST_VERSION_CODE"]}L",
        )
        buildConfigField("String", "ECJ_VERSION", "\"$ecjVersion\"")
        buildConfigField("String", "D8_VERSION", "\"$d8Version\"")
        // Literal schema-v2 release metadata is intentionally kept parser-friendly for
        // AutoJs6-Official-Plugins-Index. Keep the host version in sync with version.properties.
        resValue("string", "plugin_id", "ecj-java")
        resValue("string", "plugin_engine", "jvm-source")
        resValue("string", "plugin_variant", "java-ecj-d8")
        resValue("string", "plugin_requires_host_version", "5281")
        resValue(
            "string",
            "plugin_runtime_component",
            "io.github.supermonster003.autojs6.plugin.java.runtime/org.autojs.plugin.jvmsource.java.service.JavaSourceCompilerService"
        )
        resValue("string", "plugin_protocol_api_min", "1.6")
        resValue("string", "plugin_protocol_api_max", "1.6")
        resValue("string", "plugin_backend", "ecj-d8")
        resValue("string", "plugin_task", "jvm-source")
    }

    buildFeatures {
        aidl = true
        buildConfig = true
        resValues = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    signingConfigs {
        create("hostAligned") {
            storeFile = hostAlignedSigning.storeFile
            storePassword = hostAlignedSigning.storePassword
            keyAlias = hostAlignedSigning.keyAlias
            keyPassword = hostAlignedSigning.keyPassword
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("hostAligned")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("hostAligned")
        }
    }

    sourceSets.named("main") {
        assets.directories.add(generatedCompilerClasspathAssets.get().asFile.absolutePath)
    }
    sourceSets.named("androidTest") {
        assets.directories.add(rootProject.file("samples").absolutePath)
    }

    packaging {
        resources.pickFirsts += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.*",
            "META-INF/NOTICE",
            "META-INF/NOTICE.*",
        )
        resources.excludes += setOf(
            "META-INF/*.SF",
            "META-INF/*.DSA",
            "META-INF/*.RSA",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        abortOnError = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as com.android.build.api.variant.HasUnitTestBuilder).enableUnitTest = true
    }
}

dependencies {
    implementation(files(protocolArtifacts))
    implementation(libs.androidx.annotation)
    implementation(libs.kotlin.stdlib)
    implementation(libs.ecj)
    implementation(libs.r8)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    add(userCodeDesugaringConfiguration.name, libs.desugar.jdk.libs.configuration.nio)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read > 0) digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
}

val verifyPinnedInputs = tasks.register("verifyPinnedInputs") {
    group = "verification"
    description = "Verifies frozen protocol AARs and literal toolchain dependency pins."
    inputs.file(protocolLockFile)
    inputs.files(protocolArtifacts)

    doLast {
        check(versions["REQUIRED_HOST_VERSION_CODE"] == "5281") {
            "plugin_requires_host_version must stay aligned with REQUIRED_HOST_VERSION_CODE"
        }
        expectedPinnedDependencies.forEach { (label, pin) ->
            val dependency = pin.first.get()
            val coordinate = with(dependency) {
                "${module.group}:${module.name}:${versionConstraint.requiredVersion}"
            }
            check(coordinate == pin.second) {
                "$label dependency must stay pinned to ${pin.second}, but was $coordinate"
            }
        }
        check(protocolLockFile.isFile) { "Missing protocol lock: $protocolLockFile" }
        val lock = JsonSlurper().parse(protocolLockFile) as? Map<*, *>
            ?: error("Protocol lock root must be a JSON object")
        check((lock["schemaVersion"] as? Number)?.toInt() == 1) {
            "Unsupported protocol lock schema"
        }
        val rows = lock["artifacts"] as? List<*>
            ?: error("Protocol lock artifacts must be an array")
        val lockedArtifacts = rows.associate { rawRow ->
            val row = rawRow as? Map<*, *> ?: error("Protocol artifact row must be an object")
            val fileName = row["file"] as? String ?: error("Protocol artifact file is missing")
            val sourceModule = row["sourceModule"] as? String
                ?: error("Protocol artifact sourceModule is missing")
            val sha256 = row["sha256"] as? String ?: error("Protocol artifact sha256 is missing")
            check(sha256.matches(Regex("^[0-9a-f]{64}$"))) {
                "Protocol artifact digest is not lowercase SHA-256: $fileName"
            }
            fileName to (sourceModule to sha256)
        }
        check(lockedArtifacts.keys == expectedProtocolModules.keys) {
            "Protocol lock file set differs: ${lockedArtifacts.keys}"
        }
        expectedProtocolModules.forEach { (fileName, sourceModule) ->
            val artifact = rootProject.file("protocol/$fileName")
            check(artifact.isFile) { "Pinned protocol artifact is missing: $artifact" }
            check(!Files.isSymbolicLink(artifact.toPath())) {
                "Pinned protocol artifact must not be a symlink: $artifact"
            }
            val locked = checkNotNull(lockedArtifacts[fileName])
            check(locked.first == sourceModule) {
                "Protocol source module differs for $fileName: ${locked.first}"
            }
            check(artifact.sha256() == locked.second) {
                "Pinned protocol artifact digest differs: $fileName"
            }
        }
    }
}

val androidSdkDirectory = androidComponents.sdkComponents.sdkDirectory.get().asFile
val sdkAndroidJar = File(androidSdkDirectory, "platforms/android-$compilerStubApi/android.jar")
val sdkCoreLibraryTimeJar =
    File(androidSdkDirectory, "platforms/android-$coreLibraryTimeStubApi/android.jar")
val sdkCoreLibraryStreamJar =
    File(androidSdkDirectory, "platforms/android-$coreLibraryStreamStubApi/android.jar")
val sdkD8JavaJar = File(androidSdkDirectory, "platforms/android-$d8JavaStubApi/android.jar")

val prepareJvmSourceCompilerClasspath = tasks.register("prepareJvmSourceCompilerClasspath") {
    group = "build"
    description = "Packages controlled compiler, desugaring, and D8 library assets."
    dependsOn(verifyPinnedInputs)
    inputs.file(sdkAndroidJar)
    inputs.file(sdkCoreLibraryTimeJar)
    inputs.file(sdkCoreLibraryStreamJar)
    inputs.file(sdkD8JavaJar)
    inputs.file(jvmSourceApiAar)
    inputs.files(userCodeDesugaringConfiguration)
    outputs.dir(generatedCompilerClasspathAssets)

    doLast {
        check(sdkAndroidJar.isFile) {
            "Missing required API $compilerStubApi android.jar: $sdkAndroidJar"
        }
        check(sdkCoreLibraryTimeJar.isFile) {
            "Missing required API $coreLibraryTimeStubApi android.jar: $sdkCoreLibraryTimeJar"
        }
        check(sdkCoreLibraryStreamJar.isFile) {
            "Missing required API $coreLibraryStreamStubApi android.jar: $sdkCoreLibraryStreamJar"
        }
        check(sdkD8JavaJar.isFile) {
            "Missing required API $d8JavaStubApi android.jar: $sdkD8JavaJar"
        }
        val desugarConfigurationJar = userCodeDesugaringConfiguration.singleFile

        val classesJarBytes = ZipFile(jvmSourceApiAar).use { aar ->
            val entry = checkNotNull(aar.getEntry("classes.jar")) {
                "jvm-source-api AAR has no classes.jar"
            }
            aar.getInputStream(entry).use { it.readBytes() }
        }
        val selectedClasses = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(classesJarBytes)).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                if (!entry.isDirectory && entry.name in entryApiClassEntries) {
                    check(selectedClasses.put(entry.name, input.readBytes()) == null) {
                        "Duplicate entry ABI class: ${entry.name}"
                    }
                }
                input.closeEntry()
            }
        }
        check(selectedClasses.keys == entryApiClassEntries) {
            "jvm-source entry ABI classes differ: ${selectedClasses.keys}"
        }

        val coreLibraryStubEntries = linkedMapOf<String, ByteArray>()
        ZipFile(sdkCoreLibraryTimeJar).use { source ->
            source.entries().asSequence()
                .filter { entry ->
                    !entry.isDirectory &&
                        entry.name.startsWith("java/time/") &&
                        entry.name.endsWith(".class")
                }
                .sortedBy { it.name }
                .forEach { entry ->
                    coreLibraryStubEntries[entry.name] =
                        source.getInputStream(entry).use { it.readBytes() }
                }
        }
        val unsupportedStreamMethods = setOf(
            "ofNullable",
            "takeWhile",
            "dropWhile",
            "mapMulti",
            "mapMultiToInt",
            "mapMultiToLong",
            "mapMultiToDouble",
        )
        fun controlledStreamStub(bytes: ByteArray): ByteArray {
            val removed = linkedSetOf<String>()
            var hasToList = false
            var hasThreeArgumentIterate = false
            val writer = ClassWriter(0)
            val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {
                override fun visitMethod(
                    access: Int,
                    name: String,
                    descriptor: String,
                    signature: String?,
                    exceptions: Array<out String>?,
                ): MethodVisitor? {
                    if (name in unsupportedStreamMethods) {
                        check(removed.add(name)) { "Duplicate Stream method selected for removal: $name" }
                        return null
                    }
                    if (name == "toList" && descriptor == "()Ljava/util/List;") {
                        hasToList = true
                    }
                    if (
                        name == "iterate" && descriptor ==
                        "(Ljava/lang/Object;Ljava/util/function/Predicate;" +
                        "Ljava/util/function/UnaryOperator;)Ljava/util/stream/Stream;"
                    ) {
                        hasThreeArgumentIterate = true
                    }
                    return super.visitMethod(access, name, descriptor, signature, exceptions)
                }
            }
            ClassReader(bytes).accept(visitor, 0)
            check(removed == unsupportedStreamMethods) {
                "API $coreLibraryStreamStubApi Stream additions differ: removed=$removed"
            }
            check(hasToList && hasThreeArgumentIterate) {
                "API $coreLibraryStreamStubApi Stream is missing a supported enhanced method"
            }
            return writer.toByteArray()
        }
        ZipFile(sdkCoreLibraryStreamJar).use { source ->
            val name = "java/util/stream/Stream.class"
            val entry = checkNotNull(source.getEntry(name)) {
                "API $coreLibraryStreamStubApi has no Stream stub"
            }
            check(coreLibraryStubEntries.put(
                name,
                controlledStreamStub(source.getInputStream(entry).use { it.readBytes() }),
            ) == null) {
                "Duplicate controlled core-library stub: $name"
            }
        }
        check("java/time/LocalDate.class" in coreLibraryStubEntries)
        check("java/util/stream/Stream.class" in coreLibraryStubEntries)
        val streamStub = checkNotNull(coreLibraryStubEntries["java/util/stream/Stream.class"])
        fun ByteArray.containsAscii(value: String): Boolean =
            toString(Charsets.ISO_8859_1).contains(value)
        check(streamStub.containsAscii("toList") && streamStub.containsAscii("iterate")) {
            "Pinned desugar Stream stub is missing the supported enhanced methods"
        }
        check(
            !streamStub.containsAscii("ofNullable") &&
                !streamStub.containsAscii("takeWhile") &&
                !streamStub.containsAscii("dropWhile") &&
                !streamStub.containsAscii("mapMulti"),
        ) {
            "Controlled Stream stub unexpectedly exposes an unsupported method"
        }
        check(!streamStub.containsAscii("java/lang/invoke/MethodHandles")) {
            "Controlled Stream stub unexpectedly requires MethodHandles"
        }

        val d8JavaStubEntries = linkedMapOf<String, ByteArray>()
        ZipFile(sdkD8JavaJar).use { source ->
            source.entries().asSequence()
                .filter { entry ->
                    !entry.isDirectory &&
                        entry.name.startsWith("java/") &&
                        entry.name.endsWith(".class")
                }
                .sortedBy { it.name }
                .forEach { entry ->
                    d8JavaStubEntries[entry.name] = source.getInputStream(entry).use { it.readBytes() }
                }
        }
        check("java/lang/Object.class" in d8JavaStubEntries)
        check("java/nio/file/Files.class" in d8JavaStubEntries)

        val desugarConfigurationBytes = ZipFile(desugarConfigurationJar).use { source ->
            val entry = checkNotNull(source.getEntry("META-INF/desugar/d8/desugar.json")) {
                "Pinned desugar configuration has no D8 JSON resource"
            }
            source.getInputStream(entry).use { it.readBytes() }
        }
        val desugarConfiguration = JsonSlurper().parse(desugarConfigurationBytes) as? Map<*, *>
            ?: error("Pinned desugar configuration root must be a JSON object")
        check(
            desugarConfiguration["identifier"] ==
                "com.tools.android:desugar_jdk_libs_configuration_nio:2.1.5",
        ) {
            "Pinned desugar configuration identifier differs: ${desugarConfiguration["identifier"]}"
        }
        check((desugarConfiguration["required_compilation_api_level"] as? Number)?.toInt() == 30) {
            "Pinned desugar configuration no longer targets compilation API 30"
        }

        val assetRoot = generatedCompilerClasspathAssets.get().asFile
        if (assetRoot.exists()) check(assetRoot.deleteRecursively()) {
            "Unable to replace generated compiler classpath: $assetRoot"
        }
        val classpathRoot = assetRoot.resolve("compiler-classpath")
        check(classpathRoot.mkdirs()) { "Unable to create compiler classpath: $classpathRoot" }
        sdkAndroidJar.copyTo(classpathRoot.resolve("android.jar"), overwrite = false)
        fun writeJar(name: String, entries: Map<String, ByteArray>) {
            JarOutputStream(FileOutputStream(classpathRoot.resolve(name)).buffered()).use { output ->
                entries.keys.sorted().forEach { entryName ->
                    output.putNextEntry(JarEntry(entryName).apply { time = 0L })
                    output.write(checkNotNull(entries[entryName]))
                    output.closeEntry()
                }
            }
        }
        writeJar("entry-api.jar", selectedClasses)
        writeJar("core-library-stubs.jar", coreLibraryStubEntries)
        writeJar("d8-java-api30-stubs.jar", d8JavaStubEntries)
        FileOutputStream(classpathRoot.resolve("desugar.json")).use { output ->
            output.write(desugarConfigurationBytes)
            output.fd.sync()
        }
    }
}

tasks.matching { task ->
    task.name.startsWith("compile") ||
        task.name.startsWith("assemble") ||
        task.name.startsWith("bundle")
}.configureEach {
    dependsOn(verifyPinnedInputs)
}

tasks.matching { task ->
    task.name.startsWith("merge") && task.name.endsWith("Assets") ||
        task.name.contains("lint", ignoreCase = true)
}.configureEach {
    dependsOn(prepareJvmSourceCompilerClasspath)
}

// User DEX is compiled after the provider APK, so AGP's trace-based L8 shrinker cannot discover
// its j$ references. Keep the pinned desugared library complete and name-stable in every variant.
tasks.withType<L8DexDesugarLibTask>().configureEach {
    keepRulesConfigurations.addAll("-dontshrink", "-dontoptimize", "-dontobfuscate")
    if (name.contains("Release")) {
        doLast {
            val combinedDexText = desugarLibDex.asFileTree.files
                .sortedBy(File::getName)
                .joinToString(separator = "") { file ->
                    file.readBytes().toString(Charsets.ISO_8859_1)
                }
            check("Lj\$/time/LocalDate;" in combinedDexText) {
                "Release L8 output removed or renamed j$.time.LocalDate"
            }
            check("Lj\$/util/stream/Stream\$-EL;" in combinedDexText) {
                "Release L8 output removed or renamed the enhanced Stream dispatch class"
            }
        }
    }
}

versions.handleIfNeeded(project, "", listOf("debug", "release"))
