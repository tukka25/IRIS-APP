package com.irisapp.data.repository

import android.content.Context
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Firebase Realtime Database repository for workflow sharing.
 *
 * Structure:
 *   /shared_workflows/{pushId} → workflow JSON (name, summary, trigger, actions, exportedAt)
 *
 * No auth required — anyone with the push ID can read, anyone can write.
 */
object WorkflowShareRepository {

    private val database = FirebaseDatabase.getInstance(
        "https://iris-23288-default-rtdb.asia-southeast1.firebasedatabase.app/"
    ).getReference("shared_workflows")

    /**
     * Saves a workflow export to Firebase RTDB. Returns the push key (share ID) via [onComplete].
     * Fire-and-forget — does not await the write completion.
     */
    fun upload(workflow: Map<String, Any>, onComplete: (String?) -> Unit = {}) {
        val ref = database.push()
        ref.setValue(workflow) { error, _ ->
            onComplete(if (error == null) ref.key else null)
        }
    }

    /**
     * Fetches a shared workflow by its push ID. Returns null if not found or on network error.
     */
    suspend fun fetch(shareId: String): Map<String, Any>? = suspendCancellableCoroutine { cont ->
        val ref = database.child(shareId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                @Suppress("UNCHECKED_CAST")
                cont.resume(snapshot.value as? Map<String, Any>)
            }

            override fun onCancelled(error: DatabaseError) {
                cont.resume(null)
            }
        }
        ref.addListenerForSingleValueEvent(listener)
        cont.invokeOnCancellation { ref.removeEventListener(listener) }
    }
}