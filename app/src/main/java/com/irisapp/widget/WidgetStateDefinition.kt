package com.irisapp.widget

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Serializer
import androidx.glance.state.GlanceStateDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Hooks [IrisWidgetState] into Glance's per-widget DataStore system.
 *
 * Glance calls [getDataStore] once per widget instance, passing a [fileKey]
 * derived from the GlanceId.  This means each widget on the home screen gets
 * its own isolated DataStore file — state for widget A cannot clobber widget B.
 *
 * The state persists across:
 *   ✓  Process death (DataStore is disk-backed)
 *   ✓  Device reboots (files survive in filesDir)
 *   ✓  Launcher evictions (not memory-only)
 *
 * To update state from outside the widget (e.g. from a Service or ViewModel),
 * use [IrisWidgetStateRepository] which calls [updateAppWidgetState] on each GlanceId.
 */
object WidgetStateDefinition : GlanceStateDefinition<IrisWidgetState> {

    private const val DATASTORE_DIR  = "glance_datastore"
    private const val FILE_STEM      = "iris_widget_state"

    /**
     * Returns a [DataStore] backed by [IrisWidgetStateSerializer].
     * [fileKey] is the string form of the widget's GlanceId — guaranteed unique.
     */
    override suspend fun getDataStore(
        context: Context,
        fileKey: String
    ): DataStore<IrisWidgetState> = DataStoreFactory.create(
        serializer    = IrisWidgetStateSerializer,
        produceFile   = { getLocation(context, fileKey) }
    )

    /**
     * Physical file location for this widget's DataStore.
     * Placed under [Context.filesDir]/glance_datastore/ to avoid cluttering root.
     */
    override fun getLocation(context: Context, fileKey: String): File {
        val dir = File(context.applicationContext.filesDir, DATASTORE_DIR)
        if (!dir.exists()) dir.mkdirs()
        // Sanitise fileKey to a safe filename component
        val safeKey = fileKey.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
        return File(dir, "${FILE_STEM}_$safeKey.json")
    }
}

// ── Serializer ─────────────────────────────────────────────────────────────────

/**
 * Reads and writes [IrisWidgetState] as compact JSON using kotlinx.serialization.
 *
 * JSON is chosen over Proto because:
 *   • No .proto schema file required
 *   • kotlinx.serialization plugin is already in the project
 *   • Human-readable on-disk format eases debugging
 *
 * [ignoreUnknownKeys] ensures old DataStore files don't crash after new fields
 * are added to [IrisWidgetState] in future app versions.
 */
private object IrisWidgetStateSerializer : Serializer<IrisWidgetState> {

    override val defaultValue: IrisWidgetState = IrisWidgetState()

    private val json = Json {
        ignoreUnknownKeys = true   // forward-compat: safe to add new fields later
        encodeDefaults    = true   // always write all fields so defaultValue round-trips
    }

    override suspend fun readFrom(input: InputStream): IrisWidgetState =
        withContext(Dispatchers.IO) {
            try {
                val raw = input.readBytes().decodeToString()
                json.decodeFromString(IrisWidgetState.serializer(), raw)
            } catch (e: SerializationException) {
                // Corrupted or incompatible JSON — reset to defaults silently.
                defaultValue
            } catch (e: Exception) {
                defaultValue
            }
        }

    override suspend fun writeTo(t: IrisWidgetState, output: OutputStream) =
        withContext(Dispatchers.IO) {
            val bytes = json
                .encodeToString(IrisWidgetState.serializer(), t)
                .encodeToByteArray()
            output.write(bytes)
        }
}
