package com.gemmaworkflow.platform.tools.reto

import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Phase 0 — Logical Task Decomposition.
 *
 * NO tools. NO action catalog. Pure NLU.
 *
 * The SLM identifies what the user LOGICALLY wants to do,
 * without knowing what the app supports. This produces a
 * clean task list that Phase 1 then binds to capabilities.
 */
object TaskDecomposer {

    private const val TAG = "TaskDecomposer"
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    data class LogicalTask(
        val id: String,           // "t1", "t2"
        val description: String,  // "Send a message to Maya"
        val target: String,       // "Maya", "next Friday", null
        val action: String,       // "send_message", "create_event", "set_reminder"
        val appHint: String = "",
        val entityMentions: List<EntityMention> = emptyList(),
        val timeMentions: List<String> = emptyList()
    )

    data class EntityMention(
        val text: String,
        val kind: String
    )

    data class DecompositionResult(
        val tasks: List<LogicalTask>,
        val rawOutput: String
    )

    /**
     * Decompose a user request into logical tasks.
     * No action catalog — just what the user wants.
     */
    suspend fun decompose(engine: Engine, userRequest: String): DecompositionResult {
        val prompt = buildString {
            appendLine("You are a task decomposer for an Android phone assistant.")
            appendLine()
            appendLine("Given a user request, identify what the user LOGICALLY wants to do.")
            appendLine("Do NOT think about whether the phone can actually do it.")
            appendLine("Do NOT think about which apps exist.")
            appendLine("Just describe the tasks in plain logical terms.")
            appendLine()
            appendLine("User request: \"$userRequest\"")
            appendLine()
            appendLine("Output JSON with a list of logical tasks:")
            appendLine("""{
  "tasks": [
    {
      "id": "t1",
      "description": "what the user wants to do in one sentence",
      "target": "the person, app, thing, or time being acted upon",
      "action": "send_message | make_call | create_event | set_reminder | set_alarm | open_app | search | share | navigate | play_media | open_file | take_note | check_notification | get_info | other",
      "app_hint": "explicit app name if user mentioned one, otherwise null",
      "entity_mentions": [{ "text": "Maya", "kind": "contact | place | file | media | note | app | other" }],
      "time_mentions": ["next Friday at 6"]
    }
  ]
}""")
            appendLine()
            appendLine("Rules:")
            appendLine("- Split multi-step requests into separate tasks logically don't stop on characters like , or . read the whole sentence and decompose it")
            appendLine("- action: pick the closest category from the list above")
            appendLine("- target: extract the key person/thing/time being referenced")
            appendLine("- app_hint: only fill when the user explicitly names an app like WhatsApp, Spotify, Calendar, Gmail, Maps, Chrome")
            appendLine("- entity_mentions: raw mentions only; do not resolve contacts, files, places, or apps")
            appendLine("- time_mentions: raw time/date phrases only; do not convert them")
            appendLine("- Output ONLY valid JSON, no markdown")
            appendLine("- Make sure you don't duplicate a task only if its clear from the user")
        }

        Log.d(TAG, "Phase 0 prompt: ${prompt.length} chars")

        val rawOutput = engine.createConversation(
            ConversationConfig(
                samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.3)
            )
        ).use { conv -> conv.sendMessage(prompt).toString() }

        Log.d(TAG, "Phase 0 raw: ${rawOutput.take(500)}")

        val tasks = parseTasks(rawOutput)
        Log.i(TAG, "Decomposed into ${tasks.size} tasks: ${tasks.joinToString { "${it.id}=${it.action}" }}")

        return DecompositionResult(tasks = tasks, rawOutput = rawOutput)
    }

    private fun parseTasks(raw: String): List<LogicalTask> {
        return runCatching {
            val extracted = extractJsonBlock(raw)
            val root = json.parseToJsonElement(extracted).jsonObject
            root["tasks"]?.jsonArray?.mapNotNull { element ->
                val obj = element.jsonObject
                LogicalTask(
                    id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                    description = obj["description"]?.jsonPrimitive?.content ?: "",
                    target = obj["target"]?.jsonPrimitive?.content ?: "",
                    action = obj["action"]?.jsonPrimitive?.content ?: "other",
                    appHint = obj["app_hint"]?.jsonPrimitive?.content ?: "",
                    entityMentions = obj["entity_mentions"]?.jsonArray?.mapNotNull { mention ->
                        val mentionObj = runCatching { mention.jsonObject }.getOrNull() ?: return@mapNotNull null
                        EntityMention(
                            text = mentionObj["text"]?.jsonPrimitive?.content ?: return@mapNotNull null,
                            kind = mentionObj["kind"]?.jsonPrimitive?.content ?: "other"
                        )
                    }.orEmpty(),
                    timeMentions = obj["time_mentions"]?.jsonArray?.mapNotNull {
                        runCatching { it.jsonPrimitive.content }.getOrNull()
                    }.orEmpty()
                )
            } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    private fun extractJsonBlock(text: String): String {
        val start = text.indexOf('{')
        if (start == -1) throw IllegalArgumentException("No JSON found")
        var depth = 0
        for (i in start until text.length) {
            when (text[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        throw IllegalArgumentException("Unclosed JSON")
    }
}
