package com.gemmaworkflow.platform.capability

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Scans the device to determine which catalog actions are actually resolvable.
 * Some actions (e.g. Spotify) only work if the target app is installed.
 */
class PackageCapabilityScanner(private val context: Context) {

    /**
     * Returns the set of action IDs that are resolvable on this device.
     */
    fun resolvableActions(actionIds: Set<String>): Set<String> {
        return actionIds.filter { isResolvable(it) }.toSet()
    }

    private fun isResolvable(actionId: String): Boolean = when (actionId) {
        "browser.open_url" -> true  // Always available via ACTION_VIEW
        "maps.open_place" -> true   // Always available via geo: or maps.google.com
        "share.share_text" -> true  // Always available via ACTION_SEND
        "share.share_image" -> true // Always available via ACTION_SEND
        "notes.save_text" -> true   // Always available via share sheet
        "spotify.play_search" -> isSpotifyInstalled()
        else -> false
    }

    private fun isSpotifyInstalled(): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = android.net.Uri.parse("spotify:search:test")
        }
        val activities = if (android.os.Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }
        return activities.isNotEmpty()
    }
}
