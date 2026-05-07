package com.gemmaworkflow.data.local.storage

import android.content.Context
import java.io.File

/**
 * Generic JSON file-based CRUD storage for hackathon use.
 *
 * Each entity is stored as a named JSON file in a subdirectory under
 * the app's internal files directory. No Room, no SQL — just files.
 *
 * Based on Easer's AbstractDataStorage + JsonDataStorageBackend pattern.
 * Easer reference: /mnt/d/Easer/app/src/main/java/ryey/easer/core/data/storage/AbstractDataStorage.java
 */
class JsonFileStorage<T : Any>(
    private val context: Context,
    private val subdirectory: String,
    private val serializer: (T) -> String,
    private val parser: (String) -> T
) {
    private val dir: File
        get() = File(context.filesDir, subdirectory).also { it.mkdirs() }

    /** List all entity names (sorted). */
    fun list(): List<String> =
        dir.listFiles()
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    /** Load an entity by name. Returns null if not found. */
    fun get(name: String): T? {
        val file = fileFor(name)
        if (!file.exists()) return null
        return runCatching { parser(file.readText()) }.getOrNull()
    }

    /** Save (create or update) an entity. */
    fun save(name: String, item: T) {
        fileFor(name).writeText(serializer(item))
    }

    /** Delete an entity. Returns true if it existed. */
    fun delete(name: String): Boolean =
        fileFor(name).delete()

    /** Check if an entity exists. */
    fun exists(name: String): Boolean =
        fileFor(name).exists()

    private fun fileFor(name: String): File =
        File(dir, "$name.json")
}
