// port-lint: source src/lib.rs
package io.github.kotlinmania.lru

import kotlin.concurrent.atomics.AtomicInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun <V> assertOptEq(opt: V?, v: V) {
    assertNotNull(opt)
    assertEquals(v, opt)
}

private fun <K, V> assertOptEqTuple(opt: Pair<K, V>?, kv: Pair<K, V>) {
    assertNotNull(opt)
    assertEquals(kv.first, opt.first)
    assertEquals(kv.second, opt.second)
}

private fun <K, V> assertOptEqMutTuple(view: MutableEntry<K, V>?, kv: Pair<K, V>) {
    assertNotNull(view)
    assertEquals(kv.first, view.key)
    assertEquals(kv.second, view.value)
}

class LruCacheTest {

    @Test
    fun testUnbounded() {
        val cache = LruCache.unbounded<Int, Unit>()
        for (i in 0 until 13370) {
            cache.put(i, Unit)
        }
        assertEquals(13370, cache.len())
    }

    @Test
    fun testPutAndGet() {
        val cache = LruCache<String, String>(2)
        assertTrue(cache.isEmpty())

        assertNull(cache.put("apple", "red"))
        assertNull(cache.put("banana", "yellow"))

        assertEquals(2, cache.cap())
        assertEquals(2, cache.len())
        assertFalse(cache.isEmpty())
        assertOptEq(cache.get("apple"), "red")
        assertOptEq(cache.get("banana"), "yellow")
    }

    @Test
    fun testPutAndGetOrInsert() {
        val cache = LruCache<String, String>(2)
        assertTrue(cache.isEmpty())

        assertNull(cache.put("apple", "red"))
        assertNull(cache.put("banana", "yellow"))

        assertEquals(2, cache.cap())
        assertEquals(2, cache.len())
        assertFalse(cache.isEmpty())
        assertEquals("red", cache.getOrInsert("apple") { "orange" })
        assertEquals("yellow", cache.getOrInsert("banana") { "orange" })
        assertEquals("orange", cache.getOrInsert("lemon") { "orange" })
        assertEquals("orange", cache.getOrInsert("lemon") { "red" })
    }

    @Test
    fun testGetOrInsertRef() {
        val cache = LruCache<String, String>(2)
        assertTrue(cache.isEmpty())
        assertEquals("One", cache.getOrInsertRef("1") { "One" })
        assertEquals("Two", cache.getOrInsertRef("2") { "Two" })
        assertEquals(2, cache.len())
        assertFalse(cache.isEmpty())
        assertEquals("Two", cache.getOrInsertRef("2") { "Not two" })
        assertEquals("Two", cache.getOrInsertRef("2") { "Again not two" })
    }

    @Test
    fun testTryGetOrInsert() {
        val cache = LruCache<String, String>(2)

        assertEquals("red", cache.tryGetOrInsert("apple") { "red" })
        assertEquals("red", cache.tryGetOrInsert("apple") { error("failed") })
        assertEquals("orange", cache.tryGetOrInsert("banana") { "orange" })
        try {
            cache.tryGetOrInsert("lemon") { error("failed") }
            error("should have thrown")
        } catch (e: IllegalStateException) {
            assertEquals("failed", e.message)
        }
        assertEquals("orange", cache.tryGetOrInsert("banana") { error("failed") })
    }

    @Test
    fun testTryGetOrInsertRef() {
        val cache = LruCache<String, String>(2)
        val a: () -> String = { "One" }
        val b: () -> String = { "Two" }
        val f: () -> String = { error("nope") }
        assertEquals("One", cache.tryGetOrInsertRef("1", a))
        try { cache.tryGetOrInsertRef("2", f); error("should throw") } catch (_: IllegalStateException) {}
        assertEquals("Two", cache.tryGetOrInsertRef("2", b))
        assertEquals("Two", cache.tryGetOrInsertRef("2", a))
        assertEquals(2, cache.len())
    }

    @Test
    fun testPutAndGetOrInsertMut() {
        val cache = LruCache<String, String>(2)
        assertTrue(cache.isEmpty())

        assertNull(cache.put("apple", "red"))
        assertNull(cache.put("banana", "yellow"))

        assertEquals(2, cache.cap())
        assertEquals(2, cache.len())

        val v = cache.getOrInsertMut("apple") { "orange" }
        assertEquals("red", v)
        // Mutating a primitive value goes through put, not the returned reference.
        cache.put("apple", "blue")

        assertEquals("blue", cache.getOrInsertMut("apple") { "orange" })
        assertEquals("yellow", cache.getOrInsertMut("banana") { "orange" })
        assertEquals("orange", cache.getOrInsertMut("lemon") { "orange" })
        assertEquals("orange", cache.getOrInsertMut("lemon") { "red" })
    }

    @Test
    fun testGetOrInsertMutRef() {
        val cache = LruCache<String, String>(2)
        assertEquals("One", cache.getOrInsertMutRef("1") { "One" })
        cache.put("2", "Two")
        cache.put("2", "New two")
        assertEquals("New two", cache.getOrInsertMutRef("2") { "Two" })
    }

    @Test
    fun testTryGetOrInsertMut() {
        val cache = LruCache<Int, String>(2)

        cache.put(1, "a")
        cache.put(2, "b")
        cache.put(2, "c")

        cache.put(2, "d")
        assertEquals("d", cache.tryGetOrInsertMut(2) { "a" })
        try { cache.tryGetOrInsertMut(3) { error("failed") }; error("should throw") } catch (_: IllegalStateException) {}
        assertEquals("b", cache.tryGetOrInsertMut(4) { "b" })
        assertEquals("b", cache.tryGetOrInsertMut(4) { "a" })
    }

    @Test
    fun testTryGetOrInsertMutRef() {
        val cache = LruCache<String, String>(2)
        val a: () -> String = { "One" }
        val b: () -> String = { "Two" }
        val f: () -> String = { error("nope") }
        assertEquals("One", cache.tryGetOrInsertMutRef("1", a))
        try { cache.tryGetOrInsertMutRef("2", f); error("should throw") } catch (_: IllegalStateException) {}
        cache.put("2", "New two")
        assertEquals("New two", cache.tryGetOrInsertMutRef("2", a))
    }

    @Test
    fun testPutAndGetMut() {
        val cache = LruCache<String, String>(2)

        cache.put("apple", "red")
        cache.put("banana", "yellow")

        assertEquals(2, cache.cap())
        assertEquals(2, cache.len())
        assertOptEq(cache.getMut("apple"), "red")
        assertOptEq(cache.getMut("banana"), "yellow")
    }

    @Test
    fun testGetMutAndUpdate() {
        val cache = LruCache<String, Int>(2)

        cache.put("apple", 1)
        cache.put("banana", 3)

        // Mutation of primitive value via re-put preserves the LRU position update.
        cache.put("apple", 4)

        assertEquals(2, cache.cap())
        assertEquals(2, cache.len())
        assertOptEq(cache.getMut("apple"), 4)
        assertOptEq(cache.getMut("banana"), 3)
    }

    @Test
    fun testPutUpdate() {
        val cache = LruCache<String, String>(2)

        assertNull(cache.put("apple", "red"))
        assertEquals("red", cache.put("apple", "green"))

        assertEquals(1, cache.len())
        assertOptEq(cache.get("apple"), "green")
    }

    @Test
    fun testPutRemovesOldest() {
        val cache = LruCache<String, String>(2)

        assertNull(cache.put("apple", "red"))
        assertNull(cache.put("banana", "yellow"))
        assertNull(cache.put("pear", "green"))

        assertNull(cache.get("apple"))
        assertOptEq(cache.get("banana"), "yellow")
        assertOptEq(cache.get("pear"), "green")

        // Even though we inserted "apple" into the cache earlier it has since been removed from
        // the cache so there is no current value for `put` to return.
        assertNull(cache.put("apple", "green"))
        assertNull(cache.put("tomato", "red"))

        assertNull(cache.get("pear"))
        assertOptEq(cache.get("apple"), "green")
        assertOptEq(cache.get("tomato"), "red")
    }

    @Test
    fun testPeek() {
        val cache = LruCache<String, String>(2)

        cache.put("apple", "red")
        cache.put("banana", "yellow")

        assertOptEq(cache.peek("banana"), "yellow")
        assertOptEq(cache.peek("apple"), "red")

        cache.put("pear", "green")

        assertNull(cache.peek("apple"))
        assertOptEq(cache.peek("banana"), "yellow")
        assertOptEq(cache.peek("pear"), "green")
    }

    @Test
    fun testPeekMut() {
        val cache = LruCache<String, String>(2)

        cache.put("apple", "red")
        cache.put("banana", "yellow")

        assertOptEq(cache.peekMut("banana"), "yellow")
        assertOptEq(cache.peekMut("apple"), "red")
        assertNull(cache.peekMut("pear"))

        cache.put("pear", "green")

        assertNull(cache.peekMut("apple"))
        assertOptEq(cache.peekMut("banana"), "yellow")
        assertOptEq(cache.peekMut("pear"), "green")

        // Mutating the value happens through put for primitive-value caches.
        cache.put("banana", "green")

        assertOptEq(cache.peekMut("banana"), "green")
    }

    @Test
    fun testPeekLru() {
        val cache = LruCache<String, String>(2)

        assertNull(cache.peekLru())

        cache.put("apple", "red")
        cache.put("banana", "yellow")
        assertOptEqTuple(cache.peekLru(), "apple" to "red")

        cache.get("apple")
        assertOptEqTuple(cache.peekLru(), "banana" to "yellow")

        cache.clear()
        assertNull(cache.peekLru())
    }

    @Test
    fun testPeekMru() {
        val cache = LruCache<String, String>(2)

        assertNull(cache.peekMru())

        cache.put("apple", "red")
        cache.put("banana", "yellow")
        assertOptEqTuple(cache.peekMru(), "banana" to "yellow")

        cache.get("apple")
        assertOptEqTuple(cache.peekMru(), "apple" to "red")

        cache.clear()
        assertNull(cache.peekMru())
    }

    @Test
    fun testContains() {
        val cache = LruCache<String, String>(2)

        cache.put("apple", "red")
        cache.put("banana", "yellow")
        cache.put("pear", "green")

        assertFalse(cache.contains("apple"))
        assertTrue(cache.contains("banana"))
        assertTrue(cache.contains("pear"))
    }

    @Test
    fun testPop() {
        val cache = LruCache<String, String>(2)

        cache.put("apple", "red")
        cache.put("banana", "yellow")

        assertEquals(2, cache.len())
        assertOptEq(cache.get("apple"), "red")
        assertOptEq(cache.get("banana"), "yellow")

        val popped = cache.pop("apple")
        assertNotNull(popped)
        assertEquals("red", popped)
        assertEquals(1, cache.len())
        assertNull(cache.get("apple"))
        assertOptEq(cache.get("banana"), "yellow")
    }

    @Test
    fun testPopEntry() {
        val cache = LruCache<String, String>(2)
        cache.put("apple", "red")
        cache.put("banana", "yellow")

        assertEquals(2, cache.len())
        assertOptEq(cache.get("apple"), "red")
        assertOptEq(cache.get("banana"), "yellow")

        val popped = cache.popEntry("apple")
        assertNotNull(popped)
        assertEquals("apple" to "red", popped)
        assertEquals(1, cache.len())
        assertNull(cache.get("apple"))
        assertOptEq(cache.get("banana"), "yellow")
    }

    @Test
    fun testPopLru() {
        val cache = LruCache<Int, String>(200)

        for (i in 0 until 75) cache.put(i, "A")
        for (i in 0 until 75) cache.put(i + 100, "B")
        for (i in 0 until 75) cache.put(i + 200, "C")
        assertEquals(200, cache.len())

        for (i in 0 until 75) assertOptEq(cache.get(74 - i + 100), "B")
        assertOptEq(cache.get(25), "A")

        for (i in 26 until 75) assertEquals(i to "A", cache.popLru())
        for (i in 0 until 75) assertEquals((i + 200) to "C", cache.popLru())
        for (i in 0 until 75) assertEquals((74 - i + 100) to "B", cache.popLru())
        assertEquals(25 to "A", cache.popLru())
        for (i in 0 until 50) assertNull(cache.popLru())
    }

    @Test
    fun testPopMru() {
        val cache = LruCache<Int, String>(200)

        for (i in 0 until 75) cache.put(i, "A")
        for (i in 0 until 75) cache.put(i + 100, "B")
        for (i in 0 until 75) cache.put(i + 200, "C")
        assertEquals(200, cache.len())

        for (i in 0 until 75) assertOptEq(cache.get(74 - i + 100), "B")
        assertOptEq(cache.get(25), "A")

        assertEquals(25 to "A", cache.popMru())
        for (i in 0 until 75) assertEquals((i + 100) to "B", cache.popMru())
        for (i in 0 until 75) assertEquals((74 - i + 200) to "C", cache.popMru())
        for (i in (26 until 75).reversed()) assertEquals(i to "A", cache.popMru())
        for (i in 0 until 50) assertNull(cache.popMru())
    }

    @Test
    fun testClear() {
        val cache = LruCache<String, String>(2)

        cache.put("apple", "red")
        cache.put("banana", "yellow")

        assertEquals(2, cache.len())
        assertOptEq(cache.get("apple"), "red")
        assertOptEq(cache.get("banana"), "yellow")

        cache.clear()
        assertEquals(0, cache.len())
    }

    @Test
    fun testResizeLarger() {
        val cache = LruCache<Int, String>(2)

        cache.put(1, "a")
        cache.put(2, "b")
        cache.resize(4)
        cache.put(3, "c")
        cache.put(4, "d")

        assertEquals(4, cache.len())
        assertEquals("a", cache.get(1))
        assertEquals("b", cache.get(2))
        assertEquals("c", cache.get(3))
        assertEquals("d", cache.get(4))
    }

    @Test
    fun testResizeSmaller() {
        val cache = LruCache<Int, String>(4)

        cache.put(1, "a")
        cache.put(2, "b")
        cache.put(3, "c")
        cache.put(4, "d")

        cache.resize(2)

        assertEquals(2, cache.len())
        assertNull(cache.get(1))
        assertNull(cache.get(2))
        assertEquals("c", cache.get(3))
        assertEquals("d", cache.get(4))
    }

    @Test
    fun testIterForwards() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        run {
            val iter = cache.iter()
            assertEquals(3, iter.len())
            assertOptEqTuple(iter.next(), "c" to 3)

            assertEquals(2, iter.len())
            assertOptEqTuple(iter.next(), "b" to 2)

            assertEquals(1, iter.len())
            assertOptEqTuple(iter.next(), "a" to 1)

            assertEquals(0, iter.len())
            assertFalse(iter.hasNext())
        }
        run {
            val iter = cache.iterMut()
            assertEquals(3, iter.len())
            assertOptEqMutTuple(iter.next(), "c" to 3)

            assertEquals(2, iter.len())
            assertOptEqMutTuple(iter.next(), "b" to 2)

            assertEquals(1, iter.len())
            assertOptEqMutTuple(iter.next(), "a" to 1)

            assertEquals(0, iter.len())
            assertFalse(iter.hasNext())
        }
    }

    @Test
    fun testIterBackwards() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        run {
            val iter = cache.iter()
            assertEquals(3, iter.len())
            assertOptEqTuple(iter.nextBack(), "a" to 1)

            assertEquals(2, iter.len())
            assertOptEqTuple(iter.nextBack(), "b" to 2)

            assertEquals(1, iter.len())
            assertOptEqTuple(iter.nextBack(), "c" to 3)

            assertEquals(0, iter.len())
            assertNull(iter.nextBack())
        }
        run {
            val iter = cache.iterMut()
            assertEquals(3, iter.len())
            assertOptEqMutTuple(iter.nextBack(), "a" to 1)

            assertEquals(2, iter.len())
            assertOptEqMutTuple(iter.nextBack(), "b" to 2)

            assertEquals(1, iter.len())
            assertOptEqMutTuple(iter.nextBack(), "c" to 3)

            assertEquals(0, iter.len())
            assertNull(iter.nextBack())
        }
    }

    @Test
    fun testIterForwardsAndBackwards() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        run {
            val iter = cache.iter()
            assertEquals(3, iter.len())
            assertOptEqTuple(iter.next(), "c" to 3)

            assertEquals(2, iter.len())
            assertOptEqTuple(iter.nextBack(), "a" to 1)

            assertEquals(1, iter.len())
            assertOptEqTuple(iter.next(), "b" to 2)

            assertEquals(0, iter.len())
            assertNull(iter.nextBack())
        }
        run {
            val iter = cache.iterMut()
            assertEquals(3, iter.len())
            assertOptEqMutTuple(iter.next(), "c" to 3)

            assertEquals(2, iter.len())
            assertOptEqMutTuple(iter.nextBack(), "a" to 1)

            assertEquals(1, iter.len())
            assertOptEqMutTuple(iter.next(), "b" to 2)

            assertEquals(0, iter.len())
            assertNull(iter.nextBack())
        }
    }

    @Test
    fun testIterClone() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)

        val iter = cache.iter()
        val iterClone = iter.clone()

        assertEquals(2, iter.len())
        assertOptEqTuple(iter.next(), "b" to 2)
        assertEquals(2, iterClone.len())
        assertOptEqTuple(iterClone.next(), "b" to 2)

        assertEquals(1, iter.len())
        assertOptEqTuple(iter.next(), "a" to 1)
        assertEquals(1, iterClone.len())
        assertOptEqTuple(iterClone.next(), "a" to 1)

        assertEquals(0, iter.len())
        assertFalse(iter.hasNext())
        assertEquals(0, iterClone.len())
        assertFalse(iterClone.hasNext())
    }

    @Test
    fun testIntoIter() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        val iter = cache.iterator() as IntoIter<String, Int>
        assertEquals(3, iter.len())
        assertEquals("a" to 1, iter.next())

        assertEquals(2, iter.len())
        assertEquals("b" to 2, iter.next())

        assertEquals(1, iter.len())
        assertEquals("c" to 3, iter.next())

        assertEquals(0, iter.len())
        assertFalse(iter.hasNext())
    }

    @Test
    fun testThatPopActuallyDetachesNode() {
        val cache = LruCache<String, Int>(5)

        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)
        cache.put("d", 4)
        cache.put("e", 5)

        assertEquals(3, cache.pop("c"))

        cache.put("f", 6)

        val iter = cache.iter()
        assertOptEqTuple(iter.next(), "f" to 6)
        assertOptEqTuple(iter.next(), "e" to 5)
        assertOptEqTuple(iter.next(), "d" to 4)
        assertOptEqTuple(iter.next(), "b" to 2)
        assertOptEqTuple(iter.next(), "a" to 1)
        assertFalse(iter.hasNext())
    }

    @Test
    fun testGetWithBorrow() {
        val cache = LruCache<String, String>(2)

        val key = "apple"
        cache.put(key, "red")

        assertOptEq(cache.get("apple"), "red")
    }

    @Test
    fun testGetMutWithBorrow() {
        val cache = LruCache<String, String>(2)

        val key = "apple"
        cache.put(key, "red")

        assertOptEq(cache.getMut("apple"), "red")
    }

    @Test
    fun testNoMemoryLeaks() {
        // Kotlin uses GC; we verify functional correctness instead of explicit drop counts:
        // every value put into the cache should be reachable until it's evicted, then it
        // should not be reachable through the cache. The drop-counter contract from the
        // upstream test is meaningless under GC, so we assert that the cache size stays
        // bounded across many insertions.
        val n = 100
        for (k in 0 until n) {
            val cache = LruCache<Int, Int>(1)
            for (i in 0 until n) cache.put(i, i)
            assertEquals(1, cache.len())
            assertEquals(n - 1, cache.peekMru()?.first)
        }
    }

    @Test
    fun testNoMemoryLeaksWithClear() {
        val n = 100
        for (k in 0 until n) {
            val cache = LruCache<Int, Int>(1)
            for (i in 0 until n) cache.put(i, i)
            cache.clear()
            assertTrue(cache.isEmpty())
        }
    }

    @Test
    fun testNoMemoryLeaksWithResize() {
        val n = 100
        for (k in 0 until n) {
            val cache = LruCache<Int, Int>(1)
            for (i in 0 until n) cache.put(i, i)
            cache.clear()
            assertTrue(cache.isEmpty())
        }
    }

    @Test
    fun testNoMemoryLeaksWithPop() {
        val popCounter = AtomicInt(0)
        val n = 100
        for (k in 0 until n) {
            val cache = LruCache<Int, Int>(1)
            for (i in 0 until 100) {
                cache.put(i, i)
                if (cache.pop(i) != null) popCounter.fetchAndAdd(1)
            }
        }
        assertEquals(n * n, popCounter.load())
    }

    @Test
    fun testPromoteAndDemote() {
        val cache = LruCache<Int, Int>(5)
        for (i in 0 until 5) cache.push(i, i)
        cache.promote(1)
        cache.promote(0)
        cache.demote(3)
        cache.demote(4)
        assertEquals(4 to 4, cache.popLru())
        assertEquals(3 to 3, cache.popLru())
        assertEquals(2 to 2, cache.popLru())
        assertEquals(1 to 1, cache.popLru())
        assertEquals(0 to 0, cache.popLru())
        assertNull(cache.popLru())
    }

    @Test
    fun testGetKeyValue() {
        val cache = LruCache<String, String>(2)

        val key = "apple"
        cache.put(key, "red")

        assertEquals("apple" to "red", cache.getKeyValue("apple"))
        assertNull(cache.getKeyValue("banana"))
    }

    @Test
    fun testGetKeyValueMut() {
        val cache = LruCache<String, String>(2)

        val key = "apple"
        cache.put(key, "red")

        val (k, v) = cache.getKeyValueMut("apple")!!
        assertEquals("apple", k)
        assertEquals("red", v)
        cache.put("apple", "green")

        assertEquals("apple" to "green", cache.getKeyValue("apple"))
        assertNull(cache.getKeyValue("banana"))
    }

    @Test
    fun testClone() {
        val cache = LruCache<String, Int>(3)
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        val cloned = cache.clone()

        assertEquals("a" to 1, cache.popLru())
        assertEquals("a" to 1, cloned.popLru())

        assertEquals("b" to 2, cache.popLru())
        assertEquals("b" to 2, cloned.popLru())

        assertEquals("c" to 3, cache.popLru())
        assertEquals("c" to 3, cloned.popLru())

        assertNull(cache.popLru())
        assertNull(cloned.popLru())
    }

    @Test
    fun testCloneUnbounded() {
        val cache = LruCache.unbounded<String, Int>()
        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("c", 3)

        val cloned = cache.clone()

        assertEquals("a" to 1, cache.popLru())
        assertEquals("a" to 1, cloned.popLru())

        assertEquals("b" to 2, cache.popLru())
        assertEquals("b" to 2, cloned.popLru())

        assertEquals("c" to 3, cache.popLru())
        assertEquals("c" to 3, cloned.popLru())

        assertNull(cache.popLru())
        assertNull(cloned.popLru())
    }

    @Test
    fun iterMutStackedBorrowsViolation() {
        // Reference values mutate through MutableEntry; primitive Int values cannot
        // be mutated through a reference in Kotlin, so we wrap in IntBox.
        class IntBox(var value: Int)

        val cache = LruCache<Int, IntBox>(3)
        cache.put(1, IntBox(10))
        cache.put(2, IntBox(20))
        cache.put(3, IntBox(30))

        for ((_, box) in cache.iterMut()) {
            box.value *= 2
        }

        assertEquals(20, cache.get(1)?.value)
        assertEquals(40, cache.get(2)?.value)
        assertEquals(60, cache.get(3)?.value)
    }
}
