# lru-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Flru--kotlin-blue.svg)](https://github.com/KotlinMania/lru-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/lru-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/lru-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/lru-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/lru-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`jeromefroe/lru-rs`](https://github.com/jeromefroe/lru-rs.git).

**Original Project:** This port is based on [`jeromefroe/lru-rs`](https://github.com/jeromefroe/lru-rs.git). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `jeromefroe/lru-rs`

> The text below is reproduced and lightly edited from [`https://github.com/jeromefroe/lru-rs.git`](https://github.com/jeromefroe/lru-rs.git). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs so they continue to resolve from this repository.

## LRU Cache

[![Build Badge]][build status]
[![crates.io Badge]][crates.io package]
[![docs.rs Badge]][docs.rs documentation]
[![License Badge]][license]

[Documentation]

An implementation of a LRU cache. The cache supports `put`, `get`, `get_mut` and `pop` operations,
all of which are O(1). This crate was heavily influenced by the [LRU Cache implementation in an
earlier version of Rust's std::collections crate].

The MSRV for this crate is 1.85.0.

## Example

Below is a simple example of how to instantiate and use a LRU cache.

```rust,no_run
extern crate lru;

use lru::LruCache;
use std::num::NonZeroUsize;

fn main() {
    let mut cache = LruCache::new(NonZeroUsize::new(2).unwrap());
    cache.put("apple", 3);
    cache.put("banana", 2);

    assert_eq!(*cache.get(&"apple").unwrap(), 3);
    assert_eq!(*cache.get(&"banana").unwrap(), 2);
    assert!(cache.get(&"pear").is_none());

    assert_eq!(cache.put("banana", 4), Some(2));
    assert_eq!(cache.put("pear", 5), None);

    assert_eq!(*cache.get(&"pear").unwrap(), 5);
    assert_eq!(*cache.get(&"banana").unwrap(), 4);
    assert!(cache.get(&"apple").is_none());

    {
        let v = cache.get_mut(&"banana").unwrap();
        *v = 6;
    }

    assert_eq!(*cache.get(&"banana").unwrap(), 6);
}
```

[build badge]: https://github.com/jeromefroe/lru-rs/actions/workflows/main.yml/badge.svg
[build status]: https://github.com/jeromefroe/lru-rs/actions/workflows/main.yml
[crates.io badge]: https://img.shields.io/crates/v/lru.svg
[crates.io package]: https://crates.io/crates/lru/
[documentation]: https://docs.rs/lru/
[docs.rs badge]: https://docs.rs/lru/badge.svg
[docs.rs documentation]: https://docs.rs/lru/
[license badge]: https://img.shields.io/badge/license-MIT-blue.svg
[license]: https://raw.githubusercontent.com/jeromefroe/lru-rs/master/LICENSE
[lru cache implementation in an earlier version of rust's std::collections crate]: https://doc.rust-lang.org/0.12.0/std/collections/lru_cache/struct.LruCache.html

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:lru-kotlin:0.1.0")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same MIT license as the upstream [`jeromefroe/lru-rs`](https://github.com/jeromefroe/lru-rs.git). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the lru-rs authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`jeromefroe/lru-rs`](https://github.com/jeromefroe/lru-rs.git) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
