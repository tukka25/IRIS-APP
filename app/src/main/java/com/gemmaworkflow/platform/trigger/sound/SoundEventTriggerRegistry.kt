package com.gemmaworkflow.platform.trigger.sound

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Maps YAMNet AudioSet class names → saved workflow names.
 *
 * Persisted via SharedPreferences so sound→workflow mappings survive app restarts and reboots.
 *
 * Example registry:
 * ```
 * "Dog bark"  → "Notify when dog barks"
 * "Glass breaking" → "Alert on glass break"
 * ```
 *
 * The [SoundEventTriggerService] reads this registry on startup to know which
 * sounds to watch for. When a matching sound is detected, it fires the
 * associated workflow via [com.gemmaworkflow.platform.trigger.TriggerRegistry].
 */
object SoundEventTriggerRegistry {

    private const val TAG = "SoundEventReg"
    private const val PREFS_NAME = "sound_triggers"

    // Key prefix for sound class → workflow mappings
    private const val KEY_PREFIX = "sound_trigger_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Associate a YAMNet sound class with a saved workflow.
     * If [workflowName] is `null`, removes the registration.
     */
    fun register(context: Context, soundClass: String, workflowName: String?) {
        val p = prefs(context)
        if (workflowName == null) {
            p.edit().remove(KEY_PREFIX + soundClass).apply()
            Log.i(TAG, "Unregistered sound trigger: '$soundClass'")
        } else {
            p.edit().putString(KEY_PREFIX + soundClass, workflowName).apply()
            Log.i(TAG, "Registered sound trigger: '$soundClass' → '$workflowName'")
        }
    }

    /**
     * Get the workflow name associated with a sound class, or `null` if none.
     */
    fun getWorkflow(context: Context, soundClass: String): String? {
        return prefs(context).getString(KEY_PREFIX + soundClass, null)
    }

    /**
     * All currently registered sound→workflow mappings.
     */
    fun registeredMappings(context: Context): Map<String, String> {
        return prefs(context).all
            .filterKeys { it.startsWith(KEY_PREFIX) }
            .mapKeys { it.key.removePrefix(KEY_PREFIX) }
            .mapValues { it.value as? String ?: "" }
            .filterValues { it.isNotBlank() }
    }

    /**
     * Flow of all registered sound→workflow mappings.
     * (SharedPreferences has no native Flow, so this returns a snapshot.)
     */
    fun registeredMappingsFlow(context: Context): Flow<Map<String, String>> {
        return object : Flow<Map<String, String>> {
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<Map<String, String>>) {
                // SharedPreferences doesn't emit — emit once with current state
                collector.emit(registeredMappings(context))
            }
        }
    }

    /**
     * Remove all sound trigger registrations.
     */
    fun clearAll(context: Context) {
        val p = prefs(context)
        val keys = p.all.keys.filter { it.startsWith(KEY_PREFIX) }
        val editor = p.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
        Log.i(TAG, "Cleared all sound trigger registrations")
    }
}
