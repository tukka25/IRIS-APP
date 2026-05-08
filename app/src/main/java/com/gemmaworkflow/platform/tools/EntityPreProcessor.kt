package com.gemmaworkflow.platform.tools

import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import android.Manifest

/**
 * Pre-processes user requests BEFORE the SLM sees them.
 *
 * Extracts named entities (contacts, phone numbers, dates)
 * and resolves them against device data so the SLM doesn't
 * need to call get_contact for every name.
 *
 * This solves the "Maya" problem: the model sees "Maya" and
 * doesn't know whether to call get_contact. With EntityPreProcessor,
 * "Maya" is resolved BEFORE the prompt is built and injected as:
 *
 *   CONTACTS RESOLVED: Maya = +971556778792 (from your contacts)
 *
 * The SLM can then use the phone number directly without
 * any tool call.
 */
object EntityPreProcessor {

    /**
     * Extract and resolve entities from a user request.
     */
    fun resolveEntities(
        userRequest: String,
        context: Context
    ): ResolvedEntities {
        val contacts = mutableMapOf<String, String>()
        val phoneNumbers = mutableListOf<String>()
        val dateExpressions = mutableListOf<String>()

        // 1. Extract proper names (capitalized words that might be contacts)
        val properNames = extractProperNames(userRequest)

        // 2. Look up each name in ContactsContract
        if (hasContactsPermission(context)) {
            for (name in properNames) {
                val contact = lookupContact(name, context)
                if (contact != null) {
                    contacts[name] = contact
                }
            }
        }

        // 3. Extract raw phone numbers from request
        val phonePattern = Regex("""\+?\d[\d\s\-().]{6,}""")
        phoneNumbers.addAll(phonePattern.findAll(userRequest).map { it.value.trim() }.toList())

        // 4. Extract date expressions (for logging — still resolved by SLM tool)
        val datePatterns = listOf(
            Regex("""(tomorrow|today|yesterday|tonight)""", RegexOption.IGNORE_CASE),
            Regex("""(next|this|every)\s+(week|month|year|monday|tuesday|wednesday|thursday|friday|saturday|sunday)""", RegexOption.IGNORE_CASE),
            Regex("""at\s+\d+(\s*[:.]\s*\d+)?\s*(am|pm|o'clock|oclock)?""", RegexOption.IGNORE_CASE),
            Regex("""\d+\s*(am|pm)""", RegexOption.IGNORE_CASE)
        )
        datePatterns.forEach { pattern ->
            dateExpressions.addAll(pattern.findAll(userRequest).map { it.value.trim() }.toList())
        }

        return ResolvedEntities(
            contactLookups = contacts,
            phoneNumbers = phoneNumbers,
            dateExpressions = dateExpressions
        )
    }

    /**
     * Extract capitalized words that look like proper names.
     * Filters out common English words and known app names.
     */
    private fun extractProperNames(text: String): Set<String> {
        val commonWords = setOf(
            "I", "Me", "My", "You", "Your", "He", "She", "It", "We", "They",
            "The", "A", "An", "This", "That", "These", "Those",
            "Is", "Are", "Was", "Were", "Be", "Been", "Being",
            "Have", "Has", "Had", "Do", "Does", "Did", "Will", "Would",
            "Can", "Could", "Should", "May", "Might", "Must",
            "To", "From", "In", "On", "At", "By", "For", "With",
            "And", "Or", "But", "If", "So", "Then", "Now",
            "When", "Where", "Why", "How", "What", "Which", "Who",
            "Just", "Only", "Also", "Very", "Really", "About",
            "Not", "No", "Yes", "Ok", "Okay",
            "Hi", "Hello", "Hey", "Saying",
            "Friday", "Monday", "Tuesday", "Wednesday", "Thursday", "Saturday", "Sunday",
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December",
            "GemmaWorkflow", "Android", "Google"
        )

        // Find capitalized words not in the common list
        val namePattern = Regex("""\b([A-Z][a-z]{1,20})\b""")
        return namePattern.findAll(text)
            .map { it.groupValues[1] }
            .filter { it !in commonWords }
            .toSet()
    }

    /**
     * Look up a contact name in the device address book.
     * Returns a display string like "Maya Chen | +15550101001" or null.
     */
    private fun lookupContact(name: String, context: Context): String? {
        return runCatching {
            val cursor = context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME),
                "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ?",
                arrayOf("%$name%"),
                null
            )

            cursor?.use { c ->
                val nameIdx = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                val idIdx = c.getColumnIndex(ContactsContract.Contacts._ID)
                if (c.moveToFirst()) {
                    val contactId = c.getString(idIdx)
                    val displayName = c.getString(nameIdx)
                    val phone = getPhone(contactId, context)
                    if (phone != null) {
                        "$displayName | $phone"
                    } else {
                        displayName
                    }
                } else null
            }
        }.getOrNull()
    }

    private fun getPhone(contactId: String, context: Context): String? {
        return runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                arrayOf(contactId),
                null
            )?.use { c ->
                val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (c.moveToFirst()) c.getString(numIdx) else null
            }
        }.getOrNull()
    }

    private fun hasContactsPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED
    }

    data class ResolvedEntities(
        val contactLookups: Map<String, String>,
        val phoneNumbers: List<String>,
        val dateExpressions: List<String>
    )
}
