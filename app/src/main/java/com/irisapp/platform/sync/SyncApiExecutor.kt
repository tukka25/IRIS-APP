package com.irisapp.platform.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.util.Log
import com.irisapp.domain.model.ExecutionResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Toggles automatic sync for a Google account and authority via [ContentResolver.setSyncAutomatically].
 *
 * Requires [android.Manifest.permission.WRITE_SYNC_SETTINGS] — normal apps cannot hold this
 * permission; it is reserved for system apps and apps signed with the platform key.
 * The [AccountManager] is used to resolve account_type to the actual [Account] object.
 *
 * @param context Android context.
 */
class SyncApiExecutor(private val context: Context) {

    companion object {
        private const val TAG = "SyncApiExecutor"
    }

    fun execute(params: JsonObject): ExecutionResult {
        val accountType = params["account_type"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (accountType.isNullOrBlank()) {
            return ExecutionResult(
                stepId = "sync.toggle",
                success = false,
                message = "Missing required parameter: account_type (e.g. com.google)"
            )
        }

        val authority = params["authority"]?.let { (it as? JsonPrimitive)?.contentOrNull }
        if (authority.isNullOrBlank()) {
            return ExecutionResult(
                stepId = "sync.toggle",
                success = false,
                message = "Missing required parameter: authority (e.g. com.google.android.gms.calendar)"
            )
        }

        val enable = params["enable"]?.let {
            (it as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()
        } ?: run {
            return ExecutionResult(
                stepId = "sync.toggle",
                success = false,
                message = "Missing required parameter: enable (boolean)"
            )
        }

        Log.i(TAG, "Toggling sync: account_type=$accountType authority=$authority enable=$enable")

        val account = resolveAccount(accountType)
        if (account == null) {
            val msg = "No account found for type '$accountType'. Ensure the account is present on the device."
            Log.w(TAG, msg)
            return ExecutionResult(stepId = "sync.toggle", success = false, message = msg)
        }

        return setSyncAutomatically(account, authority, enable)
    }

    /**
     * Resolve account_type string to an [Account] object using [AccountManager].
     * Returns null if no matching account is found.
     */
    private fun resolveAccount(accountType: String): Account? {
        return try {
            val accountManager = AccountManager.get(context)
            val accounts = accountManager.getAccountsByType(accountType)
            accounts.firstOrNull()?.also {
                Log.d(TAG, "Resolved account: ${it.name} ($accountType)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve account type '$accountType'", e)
            null
        }
    }

    /**
     * Enable or disable automatic sync for the given account and authority.
     */
    private fun setSyncAutomatically(account: Account, authority: String, enable: Boolean): ExecutionResult {
        return try {
            ContentResolver.setSyncAutomatically(account, authority, enable)
            val state = if (enable) "enabled" else "disabled"
            val msg = "Sync $state for ${account.name} / $authority"
            Log.i(TAG, msg)
            ExecutionResult(stepId = "sync.toggle", success = true, message = msg)
        } catch (e: Exception) {
            val msg = "Failed to toggle sync: ${e.message}"
            Log.e(TAG, msg, e)
            ExecutionResult(stepId = "sync.toggle", success = false, message = msg)
        }
    }
}