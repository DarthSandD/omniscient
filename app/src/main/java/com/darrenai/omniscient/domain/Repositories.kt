package com.darrenai.omniscient.domain

/** Repository boundaries. Data layer implements these; UI only sees these. */

interface ConversationRepo {
    fun list(): List<Conversation>
    fun load(id: Long): Conversation?
    fun save(conv: Conversation): Conversation
    fun new(): Conversation
    fun delete(id: Long)
}

interface MemoryRepo {
    fun facts(): List<String>
    fun remember(fact: String): String
    fun forget(fact: String): String
}

/** Raw LLM transport: one request round, no loop, no tools executed. */
interface ChatService {
    /** Non-null when the request must not even be attempted (e.g. missing key). */
    fun authError(): String?
    suspend fun post(transcript: List<WireMsg>, toolsJson: String?): LlmRound
}

/** Tool specs + system prompt. Implemented by the data layer's ToolCatalog. */
interface ToolRegistry {
    /** JSON array string of OpenAI-compatible function specs. */
    fun specsJson(): String
    fun systemPrompt(facts: List<String>): String
}
