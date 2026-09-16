package com.realtek.chat.storage

data class Contact(
    val id: Int,
    val name: String,
    val subtitle: String,
    val provider: String,
    val model: String,
    val corePersona: String,
    val styleRules: String,
    val avatarPath: String?,
    val backgroundPath: String?,
    val proactiveEnabled: Boolean,
    val lastProactiveAt: Long?
)

data class ChatMessage(
    val id: Long,
    val contactId: Int,
    val role: String,
    val type: String,
    val text: String,
    val mediaPath: String?,
    val mimeType: String?,
    val createdAt: Long
)

data class MemoryItem(
    val id: Long,
    val contactId: Int,
    val type: String,
    val content: String,
    val tags: String,
    val importance: Double,
    val confidence: Double,
    val dueAt: Long?,
    val createdAt: Long
)

data class SummaryItem(
    val contactId: Int,
    val summary: String,
    val updatedAt: Long
)

data class GeneratedPersona(
    val subtitle: String,
    val persona: String,
    val style: String
)


data class SocialState(
    val contactId: Int,
    val mood: String,
    val conversationEnergy: Double,
    val socialDrive: Double,
    val currentTopic: String,
    val phase: String,
    val familiarity: Double,
    val trust: Double,
    val humorComfort: Double,
    val updatedAt: Long
)

data class SelfTopic(
    val id: Long,
    val contactId: Int,
    val topic: String,
    val createdAt: Long
)
