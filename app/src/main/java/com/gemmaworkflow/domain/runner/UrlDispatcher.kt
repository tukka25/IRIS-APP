package com.gemmaworkflow.domain.runner

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gemmaworkflow.domain.model.ExecutionResult

/**
 * Dispatches URL-based actions: browser URLs, maps places, Spotify search.
 */
class UrlDispatcher(private val context: Context) {

    fun openUrl(url: String): ExecutionResult {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.fold(
            onSuccess = {
                ExecutionResult(
                    stepId = "browser.open_url",
                    success = true,
                    message = "Opened $url"
                )
            },
            onFailure = { e ->
                ExecutionResult(
                    stepId = "browser.open_url",
                    success = false,
                    message = e.message ?: "Failed to open URL"
                )
            }
        )
    }

    fun openMaps(query: String): ExecutionResult {
        return runCatching {
            val encoded = Uri.encode(query)
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("geo:0,0?q=$encoded")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.fold(
            onSuccess = {
                ExecutionResult(
                    stepId = "maps.open_place",
                    success = true,
                    message = "Opened Maps for '$query'"
                )
            },
            onFailure = { e ->
                ExecutionResult(
                    stepId = "maps.open_place",
                    success = false,
                    message = e.message ?: "Failed to open Maps"
                )
            }
        )
    }

    fun openSpotifySearch(query: String): ExecutionResult {
        return runCatching {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("spotify:search:$query")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.fold(
            onSuccess = {
                ExecutionResult(
                    stepId = "spotify.play_search",
                    success = true,
                    message = "Opened Spotify search for '$query'"
                )
            },
            onFailure = { e ->
                ExecutionResult(
                    stepId = "spotify.play_search",
                    success = false,
                    message = e.message ?: "Spotify may not be installed"
                )
            }
        )
    }
}
