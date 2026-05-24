# Immediate Actions - High-Value Files

Based on AST analysis, here are the concrete next steps.

## Summary

- **Files Present:** 1/1 (100.0%)
- **Function parity:** 91/111 matched (target 113) — 82.0%
- **Class/type parity:** 5/11 matched (target 9) — 45.5%
- **Combined symbol parity:** 96/122 matched (target 122) — 78.7%
- **Average inline-code cosine:** 0.00 (function body across 0 matched files)
- **Average documentation cosine:** 0.00 (doc text across 0 matched files)
- **Cheat-zeroed Files:** 1
- **Critical Issues:** 1 files with <0.60 function similarity

## Priority 1: Fix Incomplete High-Dependency Files

No incomplete high-dependency files detected.

## Priority 2: Port Missing High-Value Files

Critical missing files (>10 dependencies):

No missing high-value files detected.

## Detailed Work Items

Every matched file is listed below with function and type symbol parity.

### 1. lib

- **Target:** `lru.LruCache [STUB] [PROVENANCE-FALLBACK]`
- **Similarity:** 0.00
- **Dependents:** 0
- **Priority Score:** 272210.0
- **Functions:** 91/111 matched (target 113)
- **Missing functions:** `hash`, `eq`, `from_ref`, `borrow`, `new`, `with_hasher`, `unbounded_with_hasher`, `construct`, `drop`, `into_iter`, `fmt`, `assert_opt_eq`, `assert_opt_eq_mut`, `assert_opt_eq_tuple`, `assert_opt_eq_mut_tuple`, `test_with_hasher`, `test_send`, `test_multiple_threads`, `test_iter_multiple_threads`, `_test_lifetimes`
- **Types:** 5/11 matched (target 9)
- **Missing types:** `KeyRef`, `KeyWrapper`, `DefaultHasher`, `Item`, `DropCounter`, `KeyDropCounter`
- **Tests:** 44/52 matched
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `src/lib.rs` vs expected `lib.rs`
- **Provenance warning:** port-lint provenance header matched only after fallback normalization: `src/lib.rs` vs expected `lib.rs`
- **Proposed provenance header:** `// port-lint: source lib.rs` (current: `// port-lint: source src/lib.rs`)
- **Proposed provenance header:** `// port-lint: source lib.rs` (current: `// port-lint: source src/lib.rs`)
- **Lint issues:** 2

## Success Criteria

For each file to be considered "complete":
- **Similarity ≥ 0.85** (Excellent threshold)
- All public APIs ported
- All tests ported
- Documentation ported
- port-lint header present
