# Use-case research: `allDistinct()` / `allDistinctBy {}`

## 1. Context and the question

[KT-30270 — Provide `allDistinct()` and `allDistinctBy{}` for iterables](https://youtrack.jetbrains.com/issue/KT-30270) proposes adding an `allDistinct` family to the standard library — a predicate that returns `true` iff all elements of a collection / array / sequence are pairwise distinct (no duplicates). It is the dual of the `allEqual` family from [KT-10380](https://youtrack.jetbrains.com/issue/KT-10380). The detailed duplicate [KT-85976](https://youtrack.jetbrains.com/issue/KT-85976) already carries a near-complete spec:

```kotlin
public fun <T> Iterable<T>.allDistinct(): Boolean
public inline fun <T, K> Iterable<T>.allDistinctBy(selector: (T) -> K): Boolean
```

— analogously for `Sequence`, `Array<out T>`, every primitive array, and every unsigned array (2 functions × 15 receiver families = 30 signatures), with `allDistinctWith` and a referential-identity variant explicitly **out of scope**.

What the proposal lacked was documented field evidence. This document supplies it. It is the mirror image of the [`allEqualWith` use-case survey](../KT-10380-all-equal/Removing-allEqualWith-from-the-allEqual-family.md#use-case-investigation), run over the same corpus — that survey found the use-case bar **unmet** (4 genuine hits) and `allEqualWith` was dropped. The question here:

> Do real Kotlin codebases hand-roll "all elements distinct" checks often enough, and in what shapes, to justify `allDistinct()` / `allDistinctBy {}` — and what do those shapes say about the proposed API surface?

Because `allEqual*` and `allDistinct*` are designed jointly, §5 also folds the findings back into the `allEqual` family.

## 2. Methodology

**Corpus.** The same 39 major Kotlin repositories as the [`allEqualWith` survey](../KT-10380-all-equal/Removing-allEqualWith-from-the-allEqual-family.md#use-case-investigation) (`androidx`, `intellij-community`, `dokka`, `Exposed`, `detekt`, `kmath`, `multik`, `lets-plot`, `dataframe`, `arrow`, `ktor`, `kotlinx.coroutines`, `kotlinx.serialization`, `kotest`, `mockk`, `sqldelight`, `ktlint`, `ksp`, `compose-multiplatform`, `apollo-kotlin`, `workflow-kotlin`, `okhttp`, `okio`, `kotlinx-io`, `Anki-Android`, `mihon`, `Signal-Android`, `bitwarden-android`, `element-x-android`, `firefox-android`, `turbine`, `koin`, `gradle`, `wire`, `moshi`, `http4k`, `leakcanary`, `Android`, `familie-ba-sak`), snapshot May 2026.

**Search.** `ripgrep`, build/output dirs excluded:

```bash
rg --type-add 'kt:*.{kt,kts}' -t kt -nH -g '!**/build/**' -g '!**/out/**' \
    -e '\.(distinct|toSet|toHashSet|toMutableSet|toSortedSet)\(\)\s*\.\s*(size|count)\b' \
    -e 'distinctBy\s*\{[^{}]*\}\s*\.\s*(size|count)\b' \
    -e '\.eachCount\(' \
    -e 'value\.size\s*[<>=!]' \
    -e '\.(all|none)\s*\{[^{}]*\.add\(' \
    -e 'if\s*\(\s*!\s*[A-Za-z_][\w.]*\.add\(' \
    -e '(require|check)\s*\(.*(distinct|duplicate|unique)' \
    -e 'distinctWith' -e 'IdentityHashMap|identityHashCode'
```

**Triage.** *Kept:* a boolean check that **one** collection / array / sequence has no duplicate elements (either polarity — "all distinct" or "has duplicates"). *Discarded:* comparisons of two different collections; `distinct()` / `toSet()` used only to deduplicate for downstream use; cycle / visited bookkeeping in graph traversal; per-element pair self-checks; grouping used for plain aggregation.

**Critical triage rule — separate `allDistinct` from `allEqual`.** The sub-expression `xs.distinct().size` / `xs.toSet().size` is the substrate of **both** families, disambiguated only by the right-hand side of the comparison:

| Comparison | Meaning | Family |
|---|---|---|
| `deduped.size == 1` / `<= 1` / `> 1` / `!= 1` | "at most one distinct value" | **`allEqual`** |
| `deduped.size == xs.size` / `!= xs.size` | "as many distinct values as elements" | **`allDistinct`** |

A regex over `.distinct().size` surfaces both; only the second row is in scope here. (The first row is exactly the `allEqual` "Pattern B" from the [FP-semantics doc](../KT-10380-all-equal/allEqual-floating-point-semantics-%28and-implications-for-allDistinct%29.md#3-consistency-with-what-users-actually-hand-roll-today).) Roughly 35 `… == 1`-family hits were triaged out as `allEqual`, not `allDistinct` — see §5 for why this overlap matters.

## 3. Findings

| Idiom family | Genuine sites | Notes |
|---|---|---|
| **Size-comparison** `deduped.size {==,!=} originalSize` | **~40** | 30 no-selector, 10 by-selector; ~29 production, ~11 test |
| **Grouping** `groupingBy{}.eachCount()` / `groupBy{}` duplicate detection | **~30** | mostly production; many also need the *identity* of the duplicates |
| **`Set.add` / `Map.put` uniqueness guards** | **~25** | distinct from ~165 graph-traversal `visited.add` uses |
| O(n²) hand-roll (`count { it == x } == 1` per element) | 1 | test |
| User-defined `isDistinct()` helper | 1 | production, on a primitive-element list |

Total: **on the order of 90 genuine hand-rolled uniqueness checks across 20+ of the 39 repositories** — versus **4** for `allEqualWith`. Demand is not in question; the rest of this section is about *shape*.

### 3.1 `allDistinct()` — the no-selector idioms (~30 sites)

The canonical form is **`deduped.size` compared against the original `size`**, in both polarities:

```kotlin
// detekt/detekt-rules-performance/.../UnnecessaryPartOfBinaryExpression.kt:45  [production]
if (expressions.size != expressions.distinct().size) { /* report */ }

// intellij-community/platform/build-scripts/.../XmlDependencyUpdater.kt:261-262  [production]
val hasDuplicateModules = moduleNames.size != moduleNames.distinct().size
val hasDuplicatePlugins = pluginIds.size != pluginIds.distinct().size

// intellij-community/platform/workspace/storage/.../RefsTable.kt:192  [production]
if (newChildrenIds.size != newChildrenIds.toSet().size) { error("Children have duplicates: …") }

// lets-plot/.../PlotFacets.kt:154  [production]
require(varNames.size == varNames.distinct().size) { "Facet variables must be distinct, were: $varNames." }

// multik/.../ndarray/data/NDArray.kt:225  [production]
require(axes.toSet().size == axes.size) { "The specified dimensions must be unique." }

// Exposed/exposed-core/.../SchemaUtilityApi.kt:62  [production]
require(target.toHashSet().size == target.size) { "Not all referenced columns of $this are unique" }

// androidx/camera/camera-core/.../SessionConfig.kt:172  [production]
require(preferredFeatureGroup.distinct().size == preferredFeatureGroup.size) { … }

// Anki-Android/.../ChangeNoteTypeViewModel.kt:124  [production]
require(it.distinct().size == it.size) { "$ARG_NOTE_IDS was not distinct" }
```

Variations on the same shape:

- **`.count()` instead of `.size`** for `Map.values` and sequences — `androidx`’s three near-identical `Swipeable` files (`compose-material/.../Swipeable.kt:587`, `constraintlayout/.../CarouselSwipeable.kt:569`, `wear/.../Swipeable.kt:552`): `require(anchors.values.distinct().count() == anchors.size)`.
- **The assertion-helper form**, which a comparison-operator regex misses entirely — `assertEquals(size, deduped.size)` / `assertThat(size, equalTo(deduped.size))`:

```kotlin
// androidx/xr/.../SubspaceNodeKindTest.kt:61                    assertEquals(masks.size, masks.toSet().size)
// kmath/.../misc/PermSortTest.kt:87,98                          assertEquals(indices.toSet().size, indices.size)
// intellij-community/.../python/.../SystemPythonServiceShowCaseTest.kt:71
//                                                               assertEquals(pythons.distinct().size, pythons.size, "Duplicates found")
// Anki-Android/.../PreferenceUpgradeServiceTest.kt:86
//                          assertThat("all version IDs should be distinct", codes.size, equalTo(codes.distinct().size))
```

Further production hits: `androidx/room3/.../RemoveUnusedColumnQueryRewriter.kt:45`, `apollo-kotlin/.../IrOperationsBuilder.kt:741`, `compose-multiplatform/.../PrepareComposeResources.kt:230` (`"Duplicated key '$key'."`), `intellij-community/.../ComposeResourcesGenerateAccessors.kt:208`, `…/ReportGenerationStep.kt:121`, `…/VisualizedTextPopupUtil.kt:215`, `familie-ba-sak/.../TilkjentYtelseValideringService.kt:92`. Further test hits: `Exposed/.../TransactionStackCorruptionTest.kt:52` (+ the r2dbc copy `:43`), `intellij-community/.../ArtifactsPropertyTest.kt:180`, `…/GenerateRecursiveSequenceTest.kt:51`, `kotlinx.coroutines/.../WorkQueueStressTest.kt:109`.

A representative *combined* example — `intellij-community/platform/ml-api/.../LogDrivenModelInference.kt:126` checks the boolean with a size comparison and then uses `groupBy` only to name the offenders in the error message:

```kotlin
require(maybeDuplicatedTaskTiers.size == taskTiers.size) {
    "There are duplicated tiers in the declaration: ${maybeDuplicatedTaskTiers.groupBy { it }.filter { it.value.size > 1 }.keys}"
}
```

### 3.2 `allDistinctBy {}` — the selector idioms (~10 size-comparison sites, plus most of §3.3)

`distinctBy { sel }.size == size` and `map { sel }.toSet().size == size`:

```kotlin
// intellij-community/.../lineMarkers/shared/Markers.kt:200  [production] — note the function name
private fun Collection<KtDeclaration>.hasUniqueModuleNames() =
    distinctBy { it.getKaModule(it.project, useSiteModule = null).nameForTooltip() }.size == size

// intellij-community/.../introduceTypeAlias/introduceTypeAliasImpl.kt:161  [production] (+ the K2 copy :172)
if (typeParameters.distinctBy { it.name }.size != typeParameters.size) { … }

// Signal-Android/.../migration/V266_UniqueThreadPinOrder.kt:60  [production]
if (pinnedThreads.distinctBy { it.pinned }.size != pinnedThreads.size) { Log.w(TAG, "There's a duplicate pinned value! …") }

// intellij-community/.../gitlab/snippets/GitLabSnippetService.kt:258  [production] — explicit comment
files.map { file -> extractor(file) }.toSet().size == files.size // Check that there are no duplicates when mapped

// intellij-community/.../OasExportUtils.kt:65  [production]
… && operations.distinctBy { it.method }.size == operations.size

// Anki-Android/.../AlarmManagerServiceTest.kt:175  [test]
require(testCases.map { it.reminder.time }.toSet().size == testCases.size) { … }
```

Also `intellij-community/.../JvmClassIntentionActionGroup.kt:63`, `…/SuperClassNotInitialized.kt:116`, `…/BuiltinMembersConversion.kt:1236`. The selector form is clearly load-bearing — and the grouping family below is *predominantly* by-selector (`groupingBy { it.name }`, `groupBy { it.roomId }`). `allDistinctBy` is well justified.

### 3.3 Grouping duplicate-detection, preconditions, and the test use case

**Grouping (~30 sites).** `groupingBy { sel }.eachCount()` then `.filter { it.value > 1 }`, or `groupBy { sel }.filter { it.value.size > 1 }`:

```kotlin
// intellij-community/platform/build-scripts/.../pipeline/Pipeline.kt:115-116  [production]
val duplicates = ids.groupingBy { it }.eachCount().filter { it.value > 1 }
require(duplicates.isEmpty()) { … }

// intellij-community/plugins/evaluation-plugin/.../EvalData.kt:17-18,22-23  [production]
val duplicateNames = data.groupingBy { it.name }.eachCount().filter { it.value > 1 }
check(duplicateNames.isEmpty()) { … }

// element-x-android/.../roomlist/RoomSummaryListProcessor.kt:124  [production]
val duplicates = mutableRoomSummaries.groupingBy { it.roomId }.eachCount().filter { it.value > 1 }

// androidx/room3/.../processor/DatabaseProcessor.kt:292  [production]
.filter { it.value.size > 1 } // get the ones with duplicate names
```

Further: `androidx/.../WearWidgetProviderInfo.kt:120`, `core-api/.../FileSizeLimit.kt:135`, `…/InlineCompletionLogs.kt:82`, `…/KotlinPlugin.kt:104`, `…/AgentSessionProjectPresentation.kt:45`, `Anki-Android/.../Hamcrest.kt:44` (test); `groupBy`-form duplicate detection in `Signal-Android/.../DuplicateE164MigrationJob.kt:85`, `apollo-kotlin/.../ExtensionsMerger.kt` (×3) and `…/ValidationCommon.kt:299`, `androidx/xr/.../VertexLayout.kt:170`, several more `androidx/room3` processors, `intellij-community/.../PluginModelValidator.kt:235`, `…/ScriptErrorReporter.kt:124`, `dataframe/.../DuplicateColumnNamesException.kt:9`.

**Important nuance:** a large share of the grouping hits *extract the identities of the duplicates* (`.filter { it.value.size > 1 }.keys`) to put them in an error message — they need more than a boolean. `allDistinct()` / `allDistinctBy {}` cleanly replace only the boolean-only subset (compute → `.isEmpty()` / `require(... .isEmpty())`); the report-which-ones cases still want `groupBy`. This is a genuine, honest limit on the API's reach and should be acknowledged in the KDoc by example.

**Preconditions.** Roughly half of all hits sit inside `require` / `check` / `assert*` with messages that name the intent verbatim — `"… must be unique"`, `"Duplicated key"`, `"was not distinct"`, `"Facet variables must be distinct"`, `"Labels must be unique."`. This is the validation use case and it is pervasive.

A *sibling* pattern that is **not** replaced by `allDistinct()` — incremental uniqueness assertion while a map/set is being built element by element:

```kotlin
// intellij-community/.../RegistryUsageTest.kt:70,108     require(result.put(key, …) == null) { "Duplicated key $key" }
// kotlinx.serialization/.../SerialDescriptors.kt:336     require(uniqueNames.add(elementName)) { "Element with name '$elementName' is already registered …" }
// intellij-community/.../PackagingContentChecker.kt:855  check(seen.add(name)) { "Duplicate packaging $kind: $name" }
```

~25 such guards exist. They check uniqueness *during construction*, when no finished collection exists to call `allDistinct()` on — so they stay as-is. They are still strong evidence that "is this set of things unique?" is a constant concern.

**The test use case.** KT-30270's reporter states the primary motivation as *"assert generated collections in tests"*. The corpus bears this out: roughly **one third of all hits are test code**, and the clearest example is `Android/app/src/test/.../PixelNameTest.kt` — five separate `if (!existingNames.add(it.pixelName))` loops, each asserting a generated name list has no duplicates.

### 3.4 Short-circuit value

KT-85976 argues `allDistinct()` should short-circuit on the first duplicate and is therefore *"strictly better than the current `toSet().size == size` idiom, which always scans the whole input."* The corpus strongly supports this:

- The elegant short-circuiting form `xs.all { set.add(it) }` — which is also the reference implementation suggested on KT-30270 — appears **once** in 39 repositories (`familie-ba-sak/.../SkjemaBuilder.kt:40`). The `none { !set.add(it) }` form: **zero**.
- The non-short-circuiting `toSet()/distinct().size == size` dominates instead — it materializes a full `HashSet` *and* always traverses the entire input.
- `androidx`'s own hand-rolled helper makes the point precisely (`androidx/wear/compose/compose-foundation/.../TransformingLazyColumnMeasureResult.kt:112`):

```kotlin
private fun FloatList.isDistinct(): Boolean =
    size == fold(mutableFloatSetOf()) { acc, value -> acc.add(value); acc }.size
```

It `fold`s the **entire** list into a set before comparing sizes — no early exit. A developer who wants short-circuiting has to write the explicit loop by hand; almost nobody does. A stdlib `allDistinct()` delivers it for free.

### 3.5 Excluded variants — corpus evidence

KT-85976 ships only `allDistinct()` / `allDistinctBy {}` and puts two variants out of scope. The corpus independently corroborates both exclusions.

**`allDistinctWith(predicate)` — no demand.** `distinctWith` has **zero** occurrences across all 39 repositories — nobody defines or calls such a thing. Every single distinctness idiom found goes through `equals` / `hashCode` (`Set`, `distinct`, `distinctBy`, `groupingBy`, `groupBy`); not one uses a custom binary *equivalence predicate*. The only hand-rolled O(n²) check found — `element-x-android/.../TimelineItemActionComparatorTest.kt:18`, `require(sut.orderedList.count { item -> item == it } == 1)` evaluated per element — still uses plain `==`, not a custom predicate; it is evidence that even the *plain* `allDistinct()` is occasionally hand-rolled quadratically, not evidence for a predicate overload. This matches KT-85976's reasoning (a binary predicate carries no `hashCode`, forcing O(n²)) and the standing stdlib precedent: `distinct()` / `distinctBy()` exist, `distinctWith()` does not.

**Referential-identity (`allDistinctByIdentity`) — no demand.** `IdentityHashMap` and `identityHashCode` do occur (~86 sites), but exclusively as identity-based `hashCode()` implementations, identity caches, and weak maps — never as a "this collection contains no two referentially-equal elements" check. Deferring the `===` variant to its own ticket is well supported.

## 4. Edge cases observed in the wild

- **Empty / singleton.** Hand-rolled `deduped.size == size` forms already return `true` for empty and singleton inputs; `allDistinct()` returning `true` there (KT-85976) matches existing behavior — no migration surprise.
- **Floating point.** `androidx`'s `FloatList.isDistinct()` builds a `mutableFloatSetOf()`, i.e. it already uses `equals`-equivalence semantics (`NaN == NaN`, `-0.0 != 0.0`). This is exactly the model KT-85976 and the [FP-semantics doc](../KT-10380-all-equal/allEqual-floating-point-semantics-%28and-implications-for-allDistinct%29.md#what-this-means-for-alldistinct--alldistinctby) prescribe for the primitive `allDistinct()` overloads — the field idiom and the proposed API agree.
- **Primitive-element receivers.** Beyond `FloatList.isDistinct()`, `toSet().size == size` is used on primitive-element data; primitive arrays are less common than `List` but real. The `FloatList` example is a direct argument for the primitive-array overloads.
- **Sequences.** `asSequence().distinct().count()` and `asSequence().distinctBy { it.type }.count()` appear (`intellij-community/.../AbstractCallChainHintsProvider.kt:67`, `…/KtCallChainHintsProvider.kt:60`) — the terminal `Sequence` overload is exercised.
- **Nullable elements.** Selectors that produce `null` (`distinctBy { it == null }`, `distinctBy { if (…) null else it }`) appear; nulls are treated as ordinary elements, matching KT-85976 ("two `null` values count as duplicates").

## 5. Verdict

**The use-case bar is met decisively.** ~90 genuine hand-rolled uniqueness checks across 20+ of the 39 repositories, ~40 of them the exact whole-collection `allDistinct()` / `allDistinctBy {}` shape — against **4** for `allEqualWith`. The answer to the question in §1 is an unambiguous *yes*: checking that a collection has no duplicates is a common, cross-domain operation (compilers, IDE tooling, UI, databases, schema/IR validation, property tests).

**The evidence backs KT-85976's proposal, point by point:**

| KT-85976 claim | Corpus evidence |
|---|---|
| "Checking … no duplicates is a common operation" | ~90 hits, 20+ repos, production-weighted (~⅔) |
| Needs a selector form (`allDistinctBy`) | ~10 by-selector size-comparison hits + the grouping family is mostly by-selector |
| Worth offering on arrays / sequences / primitives | `FloatList.isDistinct()`; `asSequence().distinct*().count()`; `toSet().size == size` on primitive data |
| Short-circuiting is "strictly better" | Confirmed — the non-short-circuiting form dominates; the short-circuit form appears once |
| No `allDistinctWith` | Confirmed — zero `distinctWith`; zero custom-equivalence-predicate idioms |
| Referential `===` variant deferred | Confirmed — `identityHashCode` never used for collection-distinctness checks |
| Primary motivation: assert generated collections in tests | Confirmed — ~⅓ of hits are test code (e.g. `PixelNameTest` ×5) |

One honest caveat (§3.3): a sizeable share of grouping hits need the *identity* of the duplicates, not just a boolean — `allDistinct*` replaces only the boolean-only subset. This is a scope clarification, not a counter-argument; it should be reflected in the KDoc.

**Implications for the `allEqual` family.** The survey reinforces the joint design:

1. **The shared substrate confirms the family pairing.** `xs.distinct().size` / `xs.toSet().size` is read as `allEqual` when compared to `1` and as `allDistinct` when compared to `xs.size` (§2). The same expression, opposite questions — exactly KT-85976's framing ("`allEqual` ⇔ at most one distinct value; `allDistinct` ⇔ exactly `size` distinct values"). Named functions remove the at-a-glance ambiguity that the raw idiom carries, and shipping the pair together is the right call.
2. **Both families converge on `()` + `By(selector)`, and the field agrees.** `allEqualWith` was dropped after its survey found 4 hits and an equivalence-relation misuse vector; `allDistinctWith` is excluded for an O(n²) / no-`distinctWith`-precedent reason. The reasons differ, but this survey shows the *outcome* is independently correct for distinctness too: **zero** custom-predicate distinctness idioms exist in 39 repositories. No custom-predicate overload is warranted in either family.
3. **Shared floating-point semantics are validated by real code**, not just by analogy — see §4.

**Prior art** agrees with the chosen surface: Rust's [`itertools::all_unique`](https://docs.rs/itertools/latest/itertools/trait.Itertools.html#method.all_unique) is a short-circuiting boolean with no predicate variant; Python's [`more_itertools.all_unique(iterable, key=None)`](https://more-itertools.readthedocs.io/en/stable/api.html#more_itertools.all_unique) takes an optional `key=` selector (the `allDistinctBy` shape) and, again, no binary predicate. KT-85976's choice of the name `allDistinct` over `allUnique` is a Kotlin-stdlib-consistency call (`distinct` / `distinctBy` already exist); the surface itself — `()` plus `By(selector)` — is the cross-ecosystem norm.

### Bottom line

Ship `allDistinct()` and `allDistinctBy {}` as proposed in KT-85976. The corpus shows strong, broad, production-weighted demand; confirms the selector form, the short-circuit value, and the floating-point model; and independently justifies excluding `allDistinctWith` and the referential-identity variant.
