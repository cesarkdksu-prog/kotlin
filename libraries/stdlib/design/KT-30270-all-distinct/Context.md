# Context

I am working on [KT-30270 — Provide `allDistinct()` and `allDistinctBy{}` for iterables](https://youtrack.jetbrains.com/issue/KT-30270), adding an `allDistinct`-family of extension functions to the Kotlin standard library for `Iterable`, `Sequence`, object arrays, primitive arrays, and unsigned arrays. This is the dual of the `allEqual` family ([KT-10380](https://youtrack.jetbrains.com/issue/KT-10380)): `allEqual` asks whether every element is the same, `allDistinct` asks whether every element is different.

Nothing is implemented yet — only design and use-case research exist; the sections below lay out the planned implementation by analogy with `allEqual`.

## Design source & research already in place

**Full API specification** — [KT-85976 — Add `allDistinct` and `allDistinctBy` extensions to the standard library](https://youtrack.jetbrains.com/issue/KT-85976). Marked a duplicate of KT-30270, but it carries the complete spec: 2 functions × 15 receiver families = 30 signatures; naming rationale; the decision to ship **no** `allDistinctWith` (an O(n²) comparator-based variant); short-circuit implementation notes; floating-point behavior (`NaN == NaN`, `-0.0 != 0.0`, matching `distinct()` / `toSet()`); and out-of-scope items.

**Use-case research** — `Use-case-research.md` in this same folder: a 39-repo field survey. It found roughly 90 genuine hand-rolled uniqueness checks, about 40 of them in the exact `allDistinct()` / `allDistinctBy {}` shape.

## Design precedent: the `allEqual` family

The `allDistinct` work mirrors the `allEqual` work. Read the sibling design folder `../KT-10380-all-equal/` before finalizing the API — naming and semantics decisions made there are inherited:

- `../KT-10380-all-equal/Context.md` — the orientation document this brief is modeled on.
- `../KT-10380-all-equal/allEqual-floating-point-semantics-(and-implications-for-allDistinct).md` — the floating-point equality decision; it explicitly covers `allDistinct`.
- `../KT-10380-all-equal/Removing-allEqualWith-from-the-allEqual-family.md` — why the family ships only `()` and `By(selector)` variants and drops the `With(...)` variant; the same conclusion applies to `allDistinctWith`.

## Planned implementation (mirrors `allEqual`)

**Extension-method generators** — add to `libraries/tools/kotlin-stdlib-gen/src/templates/Aggregates.kt`, alongside the existing `f_allEqual` / `f_allEqualBy`, two new template definitions:
- `f_allDistinct`
- `f_allDistinctBy`

**Generated extension methods** (output of the templates above) for `Iterable`, `Sequence`, and arrays — these files will gain the new functions:
- `libraries/stdlib/common/src/generated/_Collections.kt`
- `libraries/stdlib/common/src/generated/_Sequences.kt`
- `libraries/stdlib/common/src/generated/_Arrays.kt`
- `libraries/stdlib/common/src/generated/_UArrays.kt`

**Samples**:
- Generator (new): `libraries/tools/kotlin-stdlib-gen/src/generators/test/AllDistinctSampleGenerator.kt`
- Generated samples (new): `libraries/stdlib/samples/test/samples/generated/alldistinct`

**Unit tests**:
- Generator (new): `libraries/tools/kotlin-stdlib-gen/src/generators/test/AllDistinctTestGenerator.kt`
- Generated tests (new): `libraries/stdlib/test/generated/alldistinct`

The new sample and test generators must be registered in `main()` of `libraries/tools/kotlin-stdlib-gen/src/generators/GenerateStandardLibTests.kt`, next to the existing `AllEqual*` calls.

## How to regenerate and run tests

Regenerate generated extension files (output of the `Aggregates.kt` templates):

```bash
./gradlew :tools:kotlin-stdlib-gen:run
```

Regenerate tests and samples:

```bash
./gradlew :tools:kotlin-stdlib-gen:generateStdlibTests
```

**Note**: this command also regenerates files under `libraries/stdlib/test/generated/minmax/` via `MinMaxTestGenerator` (the only change there is a copyright-year bump unrelated to KT-30270). Revert any `MinMax*` files immediately after regeneration.

Run all `allDistinct` tests (every class under `libraries/stdlib/test/generated/alldistinct/`):

```bash
./gradlew :kotlin-stdlib:jvmTest --tests "test.generated.alldistinct.*"
```

Run all `allDistinct` samples (every class under `libraries/stdlib/samples/test/samples/generated/alldistinct/`):

```bash
./gradlew :kotlin-stdlib:samples:test --tests "samples.generated.alldistinct.*"
```

## Related issues

- [KT-85976 — Add `allDistinct` and `allDistinctBy` extensions to the standard library](https://youtrack.jetbrains.com/issue/KT-85976) — duplicate of KT-30270, carries the full API spec.
- [KT-10380 — `allEqual` function for `Iterable`](https://youtrack.jetbrains.com/issue/KT-10380) — the sibling family whose naming and semantics decisions `allDistinct` inherits.
- [KT-78499 — `isSorted` / `isSortedBy` / `isSortedWith`](https://youtrack.jetbrains.com/issue/KT-78499) — a related predicate family built with the same `kotlin-stdlib-gen` template infrastructure.
