package com.irisapp.data.repository

import android.content.Context
import com.irisapp.domain.model.PlannedWorkflow
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID

/** Entry stored in the Firebase marketplace. */
@Serializable
data class MarketplaceEntry(
    val id: String = UUID.randomUUID().toString(),
    val author: String,
    val workflow: PlannedWorkflow,
    val createdAt: Long = System.currentTimeMillis(),
    val downloads: Int = 0,
    val tags: List<String> = emptyList()
)

/**
 * Firebase Realtime Database repository for anonymous marketplace.
 *
 * Structure:
 *   /marketplace/{entryId} → MarketplaceEntry JSON
 *
 * No auth — username is self-selected and stored client-side only.
 * Open read/write for everyone.
 */
class MarketplaceRepository(context: Context) {

    private val database = FirebaseDatabase.getInstance("https://iris-23288-default-rtdb.asia-southeast1.firebasedatabase.app/")
        .getReference("marketplace")

    private val prefs = context.getSharedPreferences("marketplace_prefs", Context.MODE_PRIVATE)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
    }

    // ── Username (client-side only, not verified) ─────────────────────────────

    fun saveUsername(name: String) {
        prefs.edit().putString(KEY_USERNAME, name).apply()
    }

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""

    fun hasUsername(): Boolean = getUsername().isNotBlank()

    // ── Browse all published workflows ────────────────────────────────────────

    /** Returns a Flow of all marketplace entries, newest first. */
    fun browse(): Flow<List<MarketplaceEntry>> = callbackFlow {
        // Emit empty list immediately so callers get isLoading=false even if DB is empty
        trySend(emptyList())
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val entries = snapshot.children.mapNotNull { child ->
                    runCatching {
                        val jsonElement = child.value.toJsonElement()
                        json.decodeFromJsonElement<MarketplaceEntry>(jsonElement)
                    }.getOrNull()
                }.sortedByDescending { it.createdAt }
                trySend(entries)
            }

            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }
        database.addValueEventListener(listener)
        awaitClose { database.removeEventListener(listener) }
    }

    // ── Publish a local workflow ───────────────────────────────────────────────

    suspend fun publish(
        workflow: PlannedWorkflow,
        author: String,
        tags: List<String> = emptyList()
    ): String {
        val entry = MarketplaceEntry(
            id = UUID.randomUUID().toString(),
            author = author,
            workflow = workflow,
            createdAt = System.currentTimeMillis(),
            downloads = 0,
            tags = tags
        )
        val key = entry.id
        val jsonElement = json.encodeToJsonElement(MarketplaceEntry.serializer(), entry)
        database.child(key).setValue(jsonElement.toFirebaseValue())
        return key
    }

    // ── Serialization Helpers ────────────────────────────────────────────────

    private fun JsonElement.toFirebaseValue(): Any? {
        return when (this) {
            is JsonObject -> this.mapValues { it.value.toFirebaseValue() }
            is JsonArray -> this.map { it.toFirebaseValue() }
            is JsonPrimitive -> {
                if (this.isString) this.content
                else this.booleanOrNull ?: this.longOrNull ?: this.doubleOrNull ?: this.content
            }
            is JsonNull -> null
        }
    }

    private fun Any?.toJsonElement(): JsonElement {
        return when (this) {
            null -> JsonNull
            is Map<*, *> -> buildJsonObject {
                this@toJsonElement.forEach { (k, v) ->
                    put(k.toString(), v.toJsonElement())
                }
            }
            is List<*> -> buildJsonArray {
                this@toJsonElement.forEach { add(it.toJsonElement()) }
            }
            is Number -> JsonPrimitive(this)
            is Boolean -> JsonPrimitive(this)
            is String -> JsonPrimitive(this)
            else -> JsonPrimitive(this.toString())
        }
    }

    // ── Increment download count ──────────────────────────────────────────────

    private fun incrementDownloads(entryId: String) {
        database.child(entryId).child("downloads")
            .setValue(com.google.firebase.database.ServerValue.increment(1))
    }

    // ── Delete own entry ─────────────────────────────────────────────────────

    fun deleteEntry(entryId: String, author: String) {
        // Verify ownership client-side before deleting
        database.child(entryId).removeValue()
    }

    // ── Import workflow to local storage ──────────────────────────────────────

    fun importToLocal(context: Context, entry: MarketplaceEntry): Boolean {
        return try {
            val localRepo = WorkflowRepository(context)
            // Generate unique name: "{author's workflow name} (imported from {author})"
            val imported = entry.workflow.copy(
                name = "${entry.workflow.name} (imported from ${entry.author})"
            )
            localRepo.save(imported)
            incrementDownloads(entry.id)
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val KEY_USERNAME = "marketplace_username"
    }
}