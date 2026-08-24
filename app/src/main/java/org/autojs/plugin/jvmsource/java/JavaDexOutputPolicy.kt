package org.autojs.plugin.jvmsource.java

/** R4 admits one bounded, contiguous classesN.dex sequence and rejects every ambiguous layout. */
internal object JavaDexOutputPolicy {
    const val MAX_DEX_FILES = 4

    fun requireDexFiles(files: Collection<java.io.File>): List<java.io.File> {
        val dexEntries = files.filter { it.extension == "dex" }
        try {
            require(dexEntries.all(java.io.File::isFile)) { "DEX output is not an ordinary file" }
            val dexFiles = dexEntries.sortedBy(java.io.File::getName)
            requireCanonicalDexNames(dexFiles.map(java.io.File::getName))
            return dexFiles
        } catch (error: IllegalArgumentException) {
            throw JavaProviderFailure(
                org.autojs.plugin.jvmsource.api.JvmSourceErrorCode.DEXING_FAILED,
                org.autojs.plugin.jvmsource.api.JvmSourceFailurePhase.DEXING,
                "The Java R4 profile requires one to four contiguous classesN.dex outputs",
                error,
            )
        }
    }

    fun requireCanonicalDexNames(names: List<String>): List<String> {
        require(names.size in 1..MAX_DEX_FILES) { "DEX artifact count is outside the R4 limit" }
        require(names == expectedDexNames(names.size)) { "DEX artifact names are missing, reordered, or non-canonical" }
        return names
    }

    fun expectedDexNames(count: Int): List<String> {
        require(count in 1..MAX_DEX_FILES)
        return (1..count).map { index -> if (index == 1) "classes.dex" else "classes$index.dex" }
    }
}
