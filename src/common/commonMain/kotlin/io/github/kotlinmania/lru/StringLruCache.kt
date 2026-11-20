package io.github.kotlinmania.lru

/**
 * Concrete key-value entry returned by [StringLruCache] eviction and peek operations.
 */
data class StringLruEntry(
    val key: String,
    val value: String,
)

/**
 * Strongly-typed `String` specialization of [LruCache] with Swift Export parity.
 */
class StringLruCache private constructor(
    private val inner: LruCache<String, String>,
) {
    /** Creates a bounded LRU cache holding at most [cap] entries. */
    constructor(cap: Int) : this(LruCache(cap))

    /** Puts [key] and [value], returning the previously stored value if [key] existed. */
    fun put(
        key: String,
        value: String,
    ): String? = inner.put(key, value)

    /** Pushes [key] and [value], returning any replaced or evicted entry. */
    fun push(
        key: String,
        value: String,
    ): StringLruEntry? = inner.push(key, value)?.toEntry()

    /** Returns the value for [key] and promotes it to most-recently used, or null if absent. */
    fun get(key: String): String? = inner.get(key)

    /** Returns `(key, value)` if present and promotes the entry to most-recently used. */
    fun getKeyValue(key: String): StringLruEntry? = inner.getKeyValue(key)?.toEntry()

    /** Returns the value for [key] without modifying LRU ordering, or null if absent. */
    fun peek(key: String): String? = inner.peek(key)

    /** Returns the least-recently used entry without removing or promoting it. */
    fun peekLru(): StringLruEntry? = inner.peekLru()?.toEntry()

    /** Returns the most-recently used entry without removing or modifying it. */
    fun peekMru(): StringLruEntry? = inner.peekMru()?.toEntry()

    /** Returns true if [key] is present without changing LRU ordering. */
    fun contains(key: String): Boolean = inner.contains(key)

    /** Removes [key] and returns its value if present. */
    fun pop(key: String): String? = inner.pop(key)

    /** Removes [key] and returns its entry if present. */
    fun popEntry(key: String): StringLruEntry? = inner.popEntry(key)?.toEntry()

    /** Removes and returns the least-recently used entry, or null if empty. */
    fun popLru(): StringLruEntry? = inner.popLru()?.toEntry()

    /** Removes and returns the most-recently used entry, or null if empty. */
    fun popMru(): StringLruEntry? = inner.popMru()?.toEntry()

    /** Moves [key] to the most-recently used position if present. */
    fun promote(key: String): Boolean = inner.promote(key)

    /** Moves [key] to the least-recently used position if present. */
    fun demote(key: String): Boolean = inner.demote(key)

    /** Number of entries currently stored. */
    fun len(): Int = inner.len()

    /** Returns true when no entries are stored. */
    fun isEmpty(): Boolean = inner.isEmpty()

    /** Maximum number of entries before eviction (`Int.MAX_VALUE` when unbounded). */
    fun cap(): Int = inner.cap()

    /** Resizes capacity to [cap], evicting least-recently used entries if shrinking. */
    fun resize(cap: Int) {
        inner.resize(cap)
    }

    /** Removes all entries from the cache. */
    fun clear() {
        inner.clear()
    }

    /** Returns a shallow clone preserving entry order. */
    fun clone(): StringLruCache = StringLruCache(inner.clone())

    /** Lists all entries from most-recently used to least-recently used. */
    fun entriesMruToLru(): List<StringLruEntry> =
        inner
            .iter()
            .asSequence()
            .map { it.toEntry() }
            .toList()

    override fun toString(): String = inner.toString()

    companion object {
        /** Creates an unbounded [StringLruCache] that never automatically evicts entries. */
        fun unbounded(): StringLruCache = StringLruCache(LruCache.unbounded())
    }
}

private fun Pair<String, String>.toEntry(): StringLruEntry = StringLruEntry(first, second)
