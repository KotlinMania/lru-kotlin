import Testing
import Lru

@Suite("Lru Swift Export Tests")
struct LruExportTests {
    @Test("StringLruCache put, get, eviction, and peek operations behave identically across Swift Export")
    func putGetAndEvict() {
        let cache = StringLruCache(cap: 2)
        #expect(cache.isEmpty())
        #expect(cache.cap() == 2)

        #expect(cache.put(key: "apple", value: "3") == nil)
        #expect(cache.put(key: "banana", value: "2") == nil)
        #expect(cache.len() == 2)
        #expect(cache.get(key: "apple") == "3")
        #expect(cache.get(key: "banana") == "2")
        #expect(cache.get(key: "pear") == nil)

        #expect(cache.put(key: "banana", value: "4") == "2")
        let evicted = cache.push(key: "pear", value: "5")
        #expect(evicted?.key == "apple")
        #expect(evicted?.value == "3")
        #expect(cache.contains(key: "apple") == false)
        #expect(cache.peekLru()?.key == "banana")
        #expect(cache.peekMru()?.key == "pear")
    }

    @Test("StringLruCache pop, promote, demote, resize, clone, and unbounded")
    func popPromoteResizeAndUnbounded() {
        let cache = StringLruCache(cap: 3)
        _ = cache.put(key: "a", value: "1")
        _ = cache.put(key: "b", value: "2")
        _ = cache.put(key: "c", value: "3")

        #expect(cache.promote(key: "a") == true)
        #expect(cache.peekMru()?.key == "a")
        #expect(cache.demote(key: "a") == true)
        #expect(cache.peekLru()?.key == "a")

        let cloned = cache.clone()
        #expect(cloned.entriesMruToLru().map(\.key) == ["c", "b", "a"])

        cache.resize(cap: 1)
        #expect(cache.cap() == 1)
        #expect(cache.len() == 1)
        #expect(cache.pop(key: "c") == "3")
        #expect(cache.isEmpty())

        let unbounded = StringLruCache.Companion.shared.unbounded()
        _ = unbounded.put(key: "k", value: "v")
        #expect(unbounded.get(key: "k") == "v")
        unbounded.clear()
        #expect(unbounded.isEmpty())
    }
}
