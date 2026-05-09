// port-lint: source src/lib.rs
package io.github.kotlinmania.lru

// MIT License

// Copyright (c) 2016 Jerome Froelich

// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:

// The above copyright notice and this permission notice shall be included in all
// copies or substantial portions of the Software.

// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
// SOFTWARE.

/**
 * An implementation of a LRU cache. The cache supports `get`, `getMut`, `put`,
 * and `pop` operations, all of which are O(1). This crate was heavily influenced
 * by the [LRU Cache implementation in an earlier version of Rust's std::collections crate](https://doc.rust-lang.org/0.12.0/std/collections/lru_cache/struct.LruCache.html).
 *
 * ## Example
 *
 * ```kotlin
 * val cache = LruCache<String, Int>(2)
 * cache.put("apple", 3)
 * cache.put("banana", 2)
 *
 * check(cache.get("apple") == 3)
 * check(cache.get("banana") == 2)
 * check(cache.get("pear") == null)
 *
 * check(cache.put("banana", 4) == 2)
 * check(cache.put("pear", 5) == null)
 *
 * check(cache.get("pear") == 5)
 * check(cache.get("banana") == 4)
 * check(cache.get("apple") == null)
 *
 * val v = cache.getMut("banana")!!
 * // mutate the value through the reference
 * cache.put("banana", 6)
 *
 * check(cache.get("banana") == 6)
 * ```
 */

// Struct used to hold a key value pair. Also contains references to previous and next entries
// so we can maintain the entries in a linked list ordered by their use.
internal class LruEntry<K, V>(
    var key: K?,
    var value: V?,
) {
    var prev: LruEntry<K, V>? = null
    var next: LruEntry<K, V>? = null

    companion object {
        fun <K, V> newSigil(): LruEntry<K, V> = LruEntry(null, null)
    }
}

/** A mutable view of a key/value entry produced by [LruCache.iterMut]. */
interface MutableEntry<K, V> {
    val key: K
    var value: V
    operator fun component1(): K = key
    operator fun component2(): V = value
}

/** An LRU Cache */
class LruCache<K : Any, V : Any> private constructor(
    private var capInternal: Int,
    private val map: HashMap<K, LruEntry<K, V>>,
) {
    // head and tail are sigil nodes to facilitate inserting entries
    private val head: LruEntry<K, V> = LruEntry.newSigil()
    private val tail: LruEntry<K, V> = LruEntry.newSigil()

    init {
        head.next = tail
        tail.prev = head
    }

    /**
     * Creates a new LRU Cache that holds at most `cap` items.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Long, String>(10)
     * ```
     */
    constructor(cap: Int) : this(requirePositive(cap), HashMap(cap))

    /** Whether this LRU cache is unbounded. */
    private fun isUnbounded(): Boolean = cap() == Int.MAX_VALUE

    /**
     * Puts a key-value pair into cache. If the key already exists in the cache, then it updates
     * the key's value and returns the old value. Otherwise, `null` is returned.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * check(cache.put(1, "a") == null)
     * check(cache.put(2, "b") == null)
     * check(cache.put(2, "beta") == "b")
     *
     * check(cache.get(1) == "a")
     * check(cache.get(2) == "beta")
     * ```
     */
    fun put(k: K, v: V): V? = capturingPut(k, v, capture = false)?.second

    /**
     * Pushes a key-value pair into the cache. If an entry with key `k` already exists in
     * the cache or another cache entry is removed (due to the lru's capacity),
     * then it returns the old entry's key-value pair. Otherwise, returns `null`.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * check(cache.push(1, "a") == null)
     * check(cache.push(2, "b") == null)
     *
     * // This push call returns (2, "b") because that was previously 2's entry in the cache.
     * check(cache.push(2, "beta") == 2 to "b")
     *
     * // This push call returns (1, "a") because the cache is at capacity and 1's entry was the lru entry.
     * check(cache.push(3, "alpha") == 1 to "a")
     *
     * check(cache.get(1) == null)
     * check(cache.get(2) == "beta")
     * check(cache.get(3) == "alpha")
     * ```
     */
    fun push(k: K, v: V): Pair<K, V>? = capturingPut(k, v, capture = true)

    // Used internally by `put` and `push` to add a new entry to the lru.
    // Takes ownership of and returns entries replaced due to the cache's capacity
    // when `capture` is true.
    private fun capturingPut(k: K, v: V, capture: Boolean): Pair<K, V>? {
        val node = map[k]
        return if (node != null) {
            // if the key is already in the cache just update its value and move it to the
            // front of the list
            val oldVal = node.value!!
            node.value = v
            detach(node)
            attach(node)
            k to oldVal
        } else {
            val (replaced, newNode) = replaceOrCreateNode(k, v)
            attach(newNode)
            map[newNode.key!!] = newNode
            if (capture) replaced else null
        }
    }

    // Used internally to swap out a node if the cache is full or to create a new node if space
    // is available. Shared between `put`, `push`, `getOrInsert`, and `getOrInsertMut`.
    private fun replaceOrCreateNode(k: K, v: V): Pair<Pair<K, V>?, LruEntry<K, V>> {
        return if (len() == cap()) {
            // if the cache is full, remove the last entry so we can use it for the new key
            val oldKey = tail.prev!!.key!!
            val oldNode = map.remove(oldKey)!!

            // read out the node's old key and value and then replace it
            val replacedKey = oldNode.key!!
            val replacedVal = oldNode.value!!
            oldNode.key = k
            oldNode.value = v

            detach(oldNode)

            (replacedKey to replacedVal) to oldNode
        } else {
            // if the cache is not full allocate a new LruEntry
            null to LruEntry(k, v)
        }
    }

    /**
     * Returns a reference to the value of the key in the cache or `null` if it is not
     * present in the cache. Moves the key to the head of the LRU list if it exists.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     * cache.put(2, "c")
     * cache.put(3, "d")
     *
     * check(cache.get(1) == null)
     * check(cache.get(2) == "c")
     * check(cache.get(3) == "d")
     * ```
     */
    fun get(k: K): V? {
        val node = map[k] ?: return null
        detach(node)
        attach(node)
        return node.value
    }

    /**
     * Returns a reference to the value of the key in the cache or `null` if it
     * is not present in the cache. Moves the key to the head of the LRU list if it exists.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<String, Int>(2)
     *
     * cache.put("apple", 8)
     * cache.put("banana", 4)
     * cache.put("banana", 6)
     * cache.put("pear", 2)
     *
     * check(cache.getMut("apple") == null)
     * check(cache.getMut("banana") == 6)
     * check(cache.getMut("pear") == 2)
     * ```
     */
    fun getMut(k: K): V? = get(k)

    /**
     * Returns a key-value reference pair of the key in the cache or `null` if it is not
     * present in the cache. Moves the key to the head of the LRU list if it exists.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<String, String>(2)
     *
     * cache.put("1", "a")
     * cache.put("2", "b")
     * cache.put("2", "c")
     * cache.put("3", "d")
     *
     * check(cache.getKeyValue("1") == null)
     * check(cache.getKeyValue("2") == ("2" to "c"))
     * check(cache.getKeyValue("3") == ("3" to "d"))
     * ```
     */
    fun getKeyValue(k: K): Pair<K, V>? {
        val node = map[k] ?: return null
        detach(node)
        attach(node)
        return node.key!! to node.value!!
    }

    /**
     * Returns a key-value reference pair of the key in the cache or `null` if it is not
     * present in the cache. The reference to the value of the key is mutable. Moves the key to
     * the head of the LRU list if it exists.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     * cache.put(1, "a")
     * cache.put(2, "b")
     * val (k, v) = cache.getKeyValueMut(1)!!
     * check(k == 1)
     * check(v == "a")
     * cache.put(1, "aa")
     * cache.put(3, "c")
     * check(cache.getKeyValue(2) == null)
     * check(cache.getKeyValue(1) == (1 to "aa"))
     * check(cache.getKeyValue(3) == (3 to "c"))
     * ```
     */
    fun getKeyValueMut(k: K): Pair<K, V>? = getKeyValue(k)

    /**
     * Returns a reference to the value of the key in the cache if it is
     * present in the cache and moves the key to the head of the LRU list.
     * If the key does not exist the provided lambda is used to populate
     * the list and a reference is returned.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     * cache.put(2, "c")
     * cache.put(3, "d")
     *
     * check(cache.getOrInsert(2) { "a" } == "c")
     * check(cache.getOrInsert(3) { "a" } == "d")
     * check(cache.getOrInsert(1) { "a" } == "a")
     * check(cache.getOrInsert(1) { "b" } == "a")
     * ```
     */
    fun getOrInsert(k: K, f: () -> V): V {
        val node = map[k]
        if (node != null) {
            detach(node)
            attach(node)
            return node.value!!
        }
        val v = f()
        val (_, newNode) = replaceOrCreateNode(k, v)
        attach(newNode)
        map[newNode.key!!] = newNode
        return newNode.value!!
    }

    /**
     * Returns a reference to the value of the key in the cache if it is
     * present in the cache and moves the key to the head of the LRU list.
     * If the key does not exist the provided lambda is used to populate
     * the list and a reference is returned. The key is only inserted into the
     * cache if it doesn't exist.
     *
     * Equivalent to [getOrInsert] in Kotlin since reference identity is preserved
     * for any [K] without explicit cloning.
     */
    fun getOrInsertRef(k: K, f: () -> V): V = getOrInsert(k, f)

    /**
     * Returns a reference to the value of the key in the cache if it is
     * present in the cache and moves the key to the head of the LRU list.
     * If the key does not exist the provided lambda is used to populate
     * the list and a reference is returned. If the lambda throws, the
     * exception propagates and the cache is left unchanged.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     * cache.put(2, "c")
     * cache.put(3, "d")
     *
     * val f: () -> String = { error("failed") }
     * val a: () -> String = { "a" }
     * val b: () -> String = { "b" }
     * check(cache.tryGetOrInsert(2, a) == "c")
     * check(cache.tryGetOrInsert(3, a) == "d")
     * runCatching { cache.tryGetOrInsert(4, f) }
     * check(cache.tryGetOrInsert(5, b) == "b")
     * check(cache.tryGetOrInsert(5, a) == "b")
     * ```
     */
    fun tryGetOrInsert(k: K, f: () -> V): V {
        val node = map[k]
        if (node != null) {
            detach(node)
            attach(node)
            return node.value!!
        }
        val v = f()
        val (_, newNode) = replaceOrCreateNode(k, v)
        attach(newNode)
        map[newNode.key!!] = newNode
        return newNode.value!!
    }

    /**
     * Like [tryGetOrInsert]. Equivalent in Kotlin because there is no Rust-style
     * `to_owned()` distinction.
     */
    fun tryGetOrInsertRef(k: K, f: () -> V): V = tryGetOrInsert(k, f)

    /**
     * Returns a mutable reference to the value of the key in the cache if it is
     * present in the cache and moves the key to the head of the LRU list.
     * If the key does not exist the provided lambda is used to populate
     * the list and a mutable reference is returned.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     *
     * val v = cache.getOrInsertMut(2) { "c" }
     * check(v == "b")
     * cache.put(2, "d")
     * check(cache.getOrInsertMut(2) { "e" } == "d")
     * check(cache.getOrInsertMut(3) { "f" } == "f")
     * check(cache.getOrInsertMut(3) { "e" } == "f")
     * ```
     */
    fun getOrInsertMut(k: K, f: () -> V): V = getOrInsert(k, f)

    /**
     * Returns a mutable reference to the value of the key in the cache if it is
     * present in the cache and moves the key to the head of the LRU list.
     * If the key does not exist the provided lambda is used to populate
     * the list and a mutable reference is returned. The key is only inserted
     * into the cache if it doesn't exist.
     *
     * Equivalent to [getOrInsertMut] in Kotlin.
     */
    fun getOrInsertMutRef(k: K, f: () -> V): V = getOrInsertMut(k, f)

    /**
     * Returns a mutable reference to the value of the key in the cache if it is
     * present in the cache and moves the key to the head of the LRU list.
     * If the key does not exist the provided lambda is used to populate
     * the list and a mutable reference is returned. If the lambda throws,
     * the exception propagates and the cache is left unchanged.
     */
    fun tryGetOrInsertMut(k: K, f: () -> V): V = tryGetOrInsert(k, f)

    /**
     * Like [tryGetOrInsertMut]. Equivalent in Kotlin because there is no Rust-style
     * `to_owned()` distinction.
     */
    fun tryGetOrInsertMutRef(k: K, f: () -> V): V = tryGetOrInsert(k, f)

    /**
     * Returns a reference to the value corresponding to the key in the cache or `null` if it is
     * not present in the cache. Unlike [get], [peek] does not update the LRU list so the key's
     * position will be unchanged.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     *
     * check(cache.peek(1) == "a")
     * check(cache.peek(2) == "b")
     * ```
     */
    fun peek(k: K): V? = map[k]?.value

    /**
     * Returns a mutable reference to the value corresponding to the key in the cache or `null`
     * if it is not present in the cache. Unlike [getMut], [peekMut] does not update the LRU
     * list so the key's position will be unchanged.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     *
     * check(cache.peekMut(1) == "a")
     * check(cache.peekMut(2) == "b")
     * ```
     */
    fun peekMut(k: K): V? = peek(k)

    /**
     * Returns the value corresponding to the least recently used item or `null` if the
     * cache is empty. Like [peek], [peekLru] does not update the LRU list so the item's
     * position will be unchanged.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     *
     * check(cache.peekLru() == (1 to "a"))
     * ```
     */
    fun peekLru(): Pair<K, V>? {
        if (isEmpty()) return null
        val node = tail.prev!!
        return node.key!! to node.value!!
    }

    /**
     * Returns the value corresponding to the most recently used item or `null` if the
     * cache is empty. Like [peek], [peekMru] does not update the LRU list so the item's
     * position will be unchanged.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     *
     * check(cache.peekMru() == (2 to "b"))
     * ```
     */
    fun peekMru(): Pair<K, V>? {
        if (isEmpty()) return null
        val node = head.next!!
        return node.key!! to node.value!!
    }

    /**
     * Returns a `Boolean` indicating whether the given key is in the cache. Does not update the
     * LRU list.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     * cache.put(3, "c")
     *
     * check(!cache.contains(1))
     * check(cache.contains(2))
     * check(cache.contains(3))
     * ```
     */
    fun contains(k: K): Boolean = map.containsKey(k)

    /**
     * Removes and returns the value corresponding to the key from the cache or
     * `null` if it does not exist.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(2, "a")
     *
     * check(cache.pop(1) == null)
     * check(cache.pop(2) == "a")
     * check(cache.pop(2) == null)
     * check(cache.len() == 0)
     * ```
     */
    fun pop(k: K): V? {
        val oldNode = map.remove(k) ?: return null
        detach(oldNode)
        return oldNode.value
    }

    /**
     * Removes and returns the key and the value corresponding to the key from the cache or
     * `null` if it does not exist.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "a")
     *
     * check(cache.pop(1) == "a")
     * check(cache.popEntry(2) == (2 to "a"))
     * check(cache.pop(1) == null)
     * check(cache.popEntry(2) == null)
     * check(cache.len() == 0)
     * ```
     */
    fun popEntry(k: K): Pair<K, V>? {
        val oldNode = map.remove(k) ?: return null
        detach(oldNode)
        return oldNode.key!! to oldNode.value!!
    }

    /**
     * Removes and returns the key and value corresponding to the least recently
     * used item or `null` if the cache is empty.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(2, "a")
     * cache.put(3, "b")
     * cache.put(4, "c")
     * cache.get(3)
     *
     * check(cache.popLru() == (4 to "c"))
     * check(cache.popLru() == (3 to "b"))
     * check(cache.popLru() == null)
     * check(cache.len() == 0)
     * ```
     */
    fun popLru(): Pair<K, V>? {
        val node = removeLast() ?: return null
        return node.key!! to node.value!!
    }

    /**
     * Removes and returns the key and value corresponding to the most recently
     * used item or `null` if the cache is empty.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     *
     * cache.put(2, "a")
     * cache.put(3, "b")
     * cache.put(4, "c")
     * cache.get(3)
     *
     * check(cache.popMru() == (3 to "b"))
     * check(cache.popMru() == (4 to "c"))
     * check(cache.popMru() == null)
     * check(cache.len() == 0)
     * ```
     */
    fun popMru(): Pair<K, V>? {
        val node = removeFirst() ?: return null
        return node.key!! to node.value!!
    }

    /**
     * Marks the key as the most recently used one. Returns true if the key
     * was promoted because it exists in the cache, false otherwise.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(3)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     * cache.put(3, "c")
     * cache.get(1)
     * cache.get(2)
     *
     * // If we do `popLru` now, we would pop 3.
     * // check(cache.popLru() == (3 to "c"))
     *
     * // By promoting 3, we make sure it isn't popped.
     * check(cache.promote(3))
     * check(cache.popLru() == (1 to "a"))
     *
     * // Promoting an entry that doesn't exist doesn't do anything.
     * check(!cache.promote(4))
     * ```
     */
    fun promote(k: K): Boolean {
        val node = map[k] ?: return false
        detach(node)
        attach(node)
        return true
    }

    /**
     * Marks the key as the least recently used one. Returns true if the key was demoted
     * because it exists in the cache, false otherwise.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(3)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     * cache.put(3, "c")
     * cache.get(1)
     * cache.get(2)
     *
     * // If we do `popLru` now, we would pop 3.
     * // check(cache.popLru() == (3 to "c"))
     *
     * // By demoting 1 and 2, we make sure those are popped first.
     * check(cache.demote(2))
     * check(cache.demote(1))
     * check(cache.popLru() == (1 to "a"))
     * check(cache.popLru() == (2 to "b"))
     *
     * // Demoting a key that doesn't exist does nothing.
     * check(!cache.demote(4))
     * ```
     */
    fun demote(k: K): Boolean {
        val node = map[k] ?: return false
        detach(node)
        attachLast(node)
        return true
    }

    /**
     * Returns the number of key-value pairs that are currently in the cache.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     * check(cache.len() == 0)
     *
     * cache.put(1, "a")
     * check(cache.len() == 1)
     *
     * cache.put(2, "b")
     * check(cache.len() == 2)
     *
     * cache.put(3, "c")
     * check(cache.len() == 2)
     * ```
     */
    fun len(): Int = map.size

    /**
     * Returns a `Boolean` indicating whether the cache is empty or not.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Int, String>(2)
     * check(cache.isEmpty())
     *
     * cache.put(1, "a")
     * check(!cache.isEmpty())
     * ```
     */
    fun isEmpty(): Boolean = map.isEmpty()

    /**
     * Returns the maximum number of key-value pairs the cache can hold.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Long, String>(2)
     * check(cache.cap() == 2)
     * ```
     */
    fun cap(): Int = capInternal

    /**
     * Resizes the cache. If the new capacity is smaller than the size of the current
     * cache any entries past the new capacity are discarded.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Long, String>(2)
     *
     * cache.put(1, "a")
     * cache.put(2, "b")
     * cache.resize(4)
     * cache.put(3, "c")
     * cache.put(4, "d")
     *
     * check(cache.len() == 4)
     * check(cache.get(1) == "a")
     * check(cache.get(2) == "b")
     * check(cache.get(3) == "c")
     * check(cache.get(4) == "d")
     * ```
     */
    fun resize(cap: Int) {
        requirePositive(cap)
        // return early if capacity doesn't change
        if (cap == capInternal) return

        while (map.size > cap) {
            popLru()
        }

        capInternal = cap
    }

    /**
     * Clears the contents of the cache.
     *
     * ## Example
     *
     * ```kotlin
     * val cache = LruCache<Long, String>(2)
     * check(cache.len() == 0)
     *
     * cache.put(1, "a")
     * check(cache.len() == 1)
     *
     * cache.put(2, "b")
     * check(cache.len() == 2)
     *
     * cache.clear()
     * check(cache.len() == 0)
     * ```
     */
    fun clear() {
        while (popLru() != null) { }
    }

    /**
     * An iterator visiting all entries in most-recently used order. The iterator element type is
     * `Pair<K, V>`.
     *
     * ## Examples
     *
     * ```kotlin
     * val cache = LruCache<String, Int>(3)
     * cache.put("a", 1)
     * cache.put("b", 2)
     * cache.put("c", 3)
     *
     * for ((key, value) in cache.iter()) {
     *     println("key: $key value: $value")
     * }
     * ```
     */
    fun iter(): Iter<K, V> = Iter(
        len = len(),
        ptr = head.next,
        end = tail.prev,
    )

    /**
     * An iterator visiting all entries in most-recently-used order, giving a mutable view on
     * V. The iterator element type is `MutableEntry<K, V>`.
     *
     * ## Examples
     *
     * ```kotlin
     * class HddBlock(var dirty: Boolean, val data: ByteArray)
     *
     * val cache = LruCache<Int, HddBlock>(3)
     * cache.put(0, HddBlock(dirty = false, data = ByteArray(512) { 0x00 }))
     * cache.put(1, HddBlock(dirty = true,  data = ByteArray(512) { 0x55 }))
     * cache.put(2, HddBlock(dirty = true,  data = ByteArray(512) { 0x77 }))
     *
     * // write dirty blocks to disk.
     * for (entry in cache.iterMut()) {
     *     val block = entry.value
     *     if (block.dirty) {
     *         // write block to disk
     *         block.dirty = false
     *     }
     * }
     * ```
     */
    fun iterMut(): IterMut<K, V> = IterMut(
        len = len(),
        ptr = head.next,
        end = tail.prev,
    )

    /**
     * Drains the cache from least-recently-used to most-recently-used, returning ownership
     * of the keys and values to the caller.
     */
    operator fun iterator(): Iterator<Pair<K, V>> = IntoIter(this)

    private fun removeFirst(): LruEntry<K, V>? {
        val next = head.next
        return if (next !== tail) {
            val oldKey = next!!.key!!
            val oldNode = map.remove(oldKey)!!
            detach(oldNode)
            oldNode
        } else {
            null
        }
    }

    private fun removeLast(): LruEntry<K, V>? {
        val prev = tail.prev
        return if (prev !== head) {
            val oldKey = prev!!.key!!
            val oldNode = map.remove(oldKey)!!
            detach(oldNode)
            oldNode
        } else {
            null
        }
    }

    private fun detach(node: LruEntry<K, V>) {
        node.prev!!.next = node.next
        node.next!!.prev = node.prev
    }

    // Attaches `node` after the sigil `head` node.
    private fun attach(node: LruEntry<K, V>) {
        node.next = head.next
        node.prev = head
        head.next = node
        node.next!!.prev = node
    }

    // Attaches `node` before the sigil `tail` node.
    private fun attachLast(node: LruEntry<K, V>) {
        node.next = tail
        node.prev = tail.prev
        tail.prev = node
        node.prev!!.next = node
    }

    /**
     * Returns a shallow copy of the cache. Keys and values are not deep-cloned;
     * the new cache holds the same references in the same LRU order.
     */
    fun clone(): LruCache<K, V> {
        val mapCap = if (isUnbounded()) len() else cap()
        val newLru = LruCache<K, V>(capInternal, HashMap(mapCap))
        // Iterate from least-recently used to most-recently used so that the
        // resulting cache preserves the relative LRU ordering.
        var current: LruEntry<K, V>? = tail.prev
        while (current != null && current !== head) {
            newLru.push(current.key!!, current.value!!)
            current = current.prev
        }
        return newLru
    }

    override fun toString(): String = "LruCache { len: ${len()}, cap: ${cap()} }"

    companion object {
        /**
         * Creates a new LRU Cache that never automatically evicts items.
         *
         * ## Example
         *
         * ```kotlin
         * val cache = LruCache.unbounded<Long, String>()
         * ```
         */
        fun <K : Any, V : Any> unbounded(): LruCache<K, V> =
            LruCache(Int.MAX_VALUE, HashMap())

        private fun requirePositive(cap: Int): Int {
            require(cap > 0) { "capacity must be > 0, got $cap" }
            return cap
        }
    }
}

/**
 * An iterator over the entries of a [LruCache].
 *
 * This class is created by the [LruCache.iter] method on [LruCache]. See its
 * documentation for more.
 */
class Iter<K : Any, V : Any> internal constructor(
    private var len: Int,
    private var ptr: LruEntry<K, V>?,
    private var end: LruEntry<K, V>?,
) : Iterator<Pair<K, V>> {

    /** Number of elements remaining in the iterator. */
    fun len(): Int = len

    override fun hasNext(): Boolean = len > 0

    override fun next(): Pair<K, V> {
        if (len == 0) throw NoSuchElementException()
        val node = ptr!!
        val key = node.key!!
        val value = node.value!!
        len -= 1
        ptr = node.next
        return key to value
    }

    /** Pulls the next element from the back of the iterator. */
    fun nextBack(): Pair<K, V>? {
        if (len == 0) return null
        val node = end!!
        val key = node.key!!
        val value = node.value!!
        len -= 1
        end = node.prev
        return key to value
    }

    /** Returns the total count of elements remaining without consuming them. */
    fun count(): Int = len

    /** Returns a copy of this iterator pointing at the same entries with the same remaining length. */
    fun clone(): Iter<K, V> = Iter(len, ptr, end)
}

/**
 * An iterator over mutable entries of a [LruCache].
 *
 * This class is created by the [LruCache.iterMut] method on [LruCache]. See its
 * documentation for more.
 */
class IterMut<K : Any, V : Any> internal constructor(
    private var len: Int,
    private var ptr: LruEntry<K, V>?,
    private var end: LruEntry<K, V>?,
) : Iterator<MutableEntry<K, V>> {

    /** Number of elements remaining in the iterator. */
    fun len(): Int = len

    override fun hasNext(): Boolean = len > 0

    override fun next(): MutableEntry<K, V> {
        if (len == 0) throw NoSuchElementException()
        val node = ptr!!
        len -= 1
        ptr = node.next
        return EntryView(node)
    }

    /** Pulls the next element from the back of the iterator. */
    fun nextBack(): MutableEntry<K, V>? {
        if (len == 0) return null
        val node = end!!
        len -= 1
        end = node.prev
        return EntryView(node)
    }

    /** Returns the total count of elements remaining without consuming them. */
    fun count(): Int = len

    private class EntryView<K : Any, V : Any>(
        private val node: LruEntry<K, V>,
    ) : MutableEntry<K, V> {
        override val key: K
            get() = node.key!!
        override var value: V
            get() = node.value!!
            set(v) { node.value = v }
    }
}

/**
 * An iterator that drains a [LruCache] in least-recently-used order.
 */
class IntoIter<K : Any, V : Any> internal constructor(
    private val cache: LruCache<K, V>,
) : Iterator<Pair<K, V>> {

    /** Number of elements remaining in the iterator. */
    fun len(): Int = cache.len()

    override fun hasNext(): Boolean = !cache.isEmpty()

    override fun next(): Pair<K, V> = cache.popLru() ?: throw NoSuchElementException()

    /** Returns the total count of elements remaining without consuming them. */
    fun count(): Int = cache.len()
}
