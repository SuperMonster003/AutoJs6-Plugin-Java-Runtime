import groovy.json.JsonSlurper
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
)
val generatedCompilerClasspathAssets = layout.buildDirectory.dir("generated/assets/compilerClasspath")
val entryApiClassEntries = linkedSetOf(
    "org/autojs/plugin/jvmsource/api/AutoJsJvmEntry.class",
    "org/autojs/plugin/jvmsource/api/JvmScriptContext.class",
    "org/autojs/plugin/jvmsource/api/JvmScriptContext\$DefaultImpls.class",
    "org/autojs/plugin/jvmsource/api/JvmAppApi.class",
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
        resValue("string", "plugin_requires_host_version", "5276")
        resValue(
            "string",
            "plugin_runtime_component",
            "io.github.supermonster003.autojs6.plugin.java.runtime/org.autojs.plugin.jvmsource.java.service.JavaSourceCompilerService"
        )
        resValue("string", "plugin_protocol_api_min", "1.1")
        resValue("string", "plugin_protocol_api_max", "1.1")
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

dependencies {
    implementation(files(protocolArtifacts))
    implementation(libs.androidx.annotation)
    implementation(libs.kotlin.stdlib)
    implementation(libs.ecj)
    implementation(libs.r8)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

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
        check(versions["REQUIRED_HOST_VERSION_CODE"] == "5276") {
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

val prepareJvmSourceCompilerClasspath = tasks.register("prepareJvmSourceCompilerClasspath") {
    group = "build"
    description = "Packages controlled API 24 and entry-only compiler classpath assets."
    dependsOn(verifyPinnedInputs)
    inputs.file(sdkAndroidJar)
    inputs.file(jvmSourceApiAar)
    outputs.dir(generatedCompilerClasspathAssets)

    doLast {
        check(sdkAndroidJar.isFile) {
            "Missing required API $compilerStubApi android.jar: $sdkAndroidJar"
        }
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

        val assetRoot = generatedCompilerClasspathAssets.get().asFile
        if (assetRoot.exists()) check(assetRoot.deleteRecursively()) {
            "Unable to replace generated compiler classpath: $assetRoot"
        }
        val classpathRoot = assetRoot.resolve("compiler-classpath")
        check(classpathRoot.mkdirs()) { "Unable to create compiler classpath: $classpathRoot" }
        sdkAndroidJar.copyTo(classpathRoot.resolve("android.jar"), overwrite = false)
        JarOutputStream(
            FileOutputStream(classpathRoot.resolve("entry-api.jar")).buffered(),
        ).use { output ->
            entryApiClassEntries.forEach { name ->
                output.putNextEntry(JarEntry(name).apply { time = 0L })
                output.write(checkNotNull(selectedClasses[name]))
                output.closeEntry()
            }
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

versions.handleIfNeeded(project, "", listOf("debug", "release"))
