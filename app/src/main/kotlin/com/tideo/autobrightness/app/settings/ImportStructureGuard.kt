package com.tideo.autobrightness.app.settings

/** DA-036: cheap, non-recursive limits applied before kotlinx.serialization sees untrusted JSON. */
internal object ImportStructureGuard {
    const val MAX_DEPTH = 32
    const val MAX_CONTAINER_ENTRIES = 256
    const val MAX_STRING_CHARS = 16_384

    fun requireBoundedJson(raw: String) {
        data class Frame(
            val objectLike: Boolean,
            var entries: Int = 1,
            var expectingKey: Boolean = objectLike,
            val keys: MutableSet<String> = mutableSetOf(),
        )

        val stack = ArrayDeque<Frame>()
        var inString = false
        var escaped = false
        var stringChars = 0
        var stringIsKey = false
        val key = StringBuilder()
        raw.forEach { character ->
            if (inString) {
                when {
                    escaped -> {
                        if (stringIsKey) throw IllegalArgumentException("Escaped JSON field names are not accepted")
                        escaped = false
                    }
                    character == '\\' -> escaped = true
                    character == '"' -> {
                        inString = false
                        if (stringIsKey && !stack.last().keys.add(key.toString())) {
                            throw IllegalArgumentException("Duplicate JSON field")
                        }
                    }
                    else -> {
                        if (stringIsKey) key.append(character)
                        if (++stringChars > MAX_STRING_CHARS) {
                            throw IllegalArgumentException("JSON string is too long")
                        }
                    }
                }
            } else {
                when (character) {
                    '"' -> {
                        inString = true
                        stringChars = 0
                        stringIsKey = stack.lastOrNull()?.let { it.objectLike && it.expectingKey } == true
                        key.clear()
                    }
                    '{', '[' -> {
                        if (stack.size >= MAX_DEPTH) throw IllegalArgumentException("JSON nesting is too deep")
                        stack.addLast(Frame(objectLike = character == '{'))
                    }
                    '}', ']' -> if (stack.isNotEmpty()) stack.removeLast()
                    ':' -> stack.lastOrNull()?.let { if (it.objectLike) it.expectingKey = false }
                    ',' -> stack.lastOrNull()?.let {
                        if (++it.entries > MAX_CONTAINER_ENTRIES) {
                            throw IllegalArgumentException("JSON container has too many entries")
                        }
                        if (it.objectLike) it.expectingKey = true
                    }
                }
            }
        }
    }
}
