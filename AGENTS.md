# stateful

Kotlin Multiplatform library for observable state: a value you can read, subscribe to, and
derive other values from. Published as `dev.deftu:stateful`, LGPL-3.0.

**One runtime dependency, and no more.** `commonMain` declares `kotlinx-atomicfu`, which provides
the graph lock and degrades to a no-op on JS. Nothing else, on any platform. Adapters ship
separately so a consumer never pays for one they do not use.

## The `.agents` directory

This project reserves **`.agents/`** at the repository root for local agent tooling: working
notes, plans, progress tracking, scratch analysis, and anything else an agent needs to keep
across sessions but which is not part of the shipped project.

It is gitignored. Nothing in it is guaranteed to exist in a given clone, so **never reference
a path inside `.agents/` from committed code, comments, or documentation** — that includes this
file. Treat it as per-machine state.

Anything that belongs to the project rather than to the work of building it — the README, the
licence, build configuration — stays out of `.agents/` and gets committed normally.

## Architecture

- **Build:** Gradle 9.7.1, Kotlin DSL, Kit (`dev.deftu.kit.*`) 0.6.0. The build JDK is pinned
  to 21 by `gradle/gradle-daemon-jvm.properties`, so the daemon runs on 21 no matter what
  `JAVA_HOME` points at
- **Java:** release 8, set via `kit.java.version`
- **API mode:** `explicitApi()` — every public declaration needs an explicit visibility
  modifier and an explicit return type. The compiler fails the build otherwise
- **Indentation:** 4 spaces, LF everywhere. `.editorconfig` and `.gitattributes` pin both
- **Whitespace:** no blank line immediately after an opening brace, and none immediately
  before a closing brace. This applies to every block — class bodies, functions,
  `kotlin { }`, `tasks { }`. Blank lines separate things; a brace already does that

### Naming

Follow the conventions the sibling libraries already use — Evocation, krossbar, outcome, textile.
They are consistent with each other and this project should not be the exception.

- **Name the thing, with its domain word.** `EventBus`, `EventBroker`, `EventSubscriber`,
  `TextRenderer`, `DispatchStrategy`, `Outcome`. Here: `StateGraph`, `StateNode`, `StateWarnings`,
  `StateListener`, `Scheduler`, `Owner`.
- **No generic infrastructure nouns.** `Runtime`, `Diagnostics`, `Manager`, `Helper`, `Util`,
  `Context` and `Handler` say nothing about what the type is for, and every project has one. A
  reader should be able to guess what a class does from its name alone.
- **Machinery lives in `internal/`**, matching Evocation. Public API sits at the package root or in
  a purposeful subpackage — `dsl/`, `ext/`, `collections/`.
- **Operators on `State` itself live in `<Type>Extensions.kt`** — `StateExtensions.kt`, matching
  outcome's `OutcomeExtensions.kt`. Extensions grouped by the *value* type they apply to keep that
  type's name instead: `ext/Booleans.kt`, `ext/Strings.kt`, `ext/Colors.kt`.
- **Platform actuals are `<File>.<target>.kt`** — `StateTracking.jvm.kt`, `StateTracking.js.kt` —
  matching Evocation's `EventBus.jvm.kt`.
- **Modules are `<library>-<adapter>`**, published as `dev.deftu:stateful-coroutines` and so on.

### Targets

| Group    | Targets                                                          |
| -------- | ---------------------------------------------------------------- |
| JVM      | `jvm` (desktop, Android, server)                                  |
| Web      | `js(IR)` (browser, Node.js), `wasmJs` (browser only)              |
| Desktop  | `linuxX64`, `mingwX64`, `macosX64`, `macosArm64`                  |
| Apple    | `iosArm64`, `iosSimulatorArm64`, `tvosArm64`, `tvosX64`, `tvosSimulatorArm64`, `watchosArm64`, `watchosX64`, `watchosSimulatorArm64` |

**Every module declares its own targets.** `kit.kmp.native` is off, because it was global and
Kit was adding a desktop target to an adapter whose dependency does not publish for it. Adapters
cover less than core where their own dependency does — `stateful-compose` has no Intel macOS,
tvOS or watchOS, and needs Java 11 rather than 8.

Both JS targets generate TypeScript definitions, so a change to a public signature is a change
to the emitted `.d.ts` as well.

### Source layout

| Path                                | Contents                                                      |
| ----------------------------------- | ------------------------------------------------------------- |
| `commonMain/.../stateful`           | The API surface: `State`, `MutableState`, `Owner`, `Scheduler`, `Equality`, `StateListener`, `Subscription`, `Disposable`, `StateWarnings`, `StateExtensions`, `Snapshot` |
| `commonMain/.../stateful/internal`  | The machinery: `StateGraph` (lock, queue, flush), `StateNode` (the graph node), `StateTracking` (thread-local context), `Warnings` |
| `commonMain/.../stateful/dsl`       | Factories and delegates: `mutableStateOf`, `memo`, `derivedStateOf`, `effect`, `createRoot`, `createOwner`, `runWithOwner`, `onCleanup`, `batch`, `untracked` |
| `commonMain/.../stateful/collections` | `ReactiveList`, `ReactiveMap`, `ReactiveSet`, `ListChange`, `mapKeyed` |
| `commonMain/.../stateful/ext`       | Extensions per value type: `Booleans`, `Strings`, `Pairs`, `Triples` |
| `jvmMain/.../stateful/ext`          | `Colors` — the one extension needing a JVM type (`java.awt.Color`) |
| `commonTest`                        | Tests, flat, no package                                        |
| `jvmTest`                           | JVM-only tests, currently the concurrency stress suite         |

Adapters are separate modules: `coroutines/`, `elementa/`, `compose/`, `svelte/`, `react/`.
`benchmarks/` is a JMH module, not published and not wired into `build`.
## Commands

From the root:

- **Build and test everything:** `./gradlew build`
- **Tests only, every target:** `./gradlew allTests`
- **One target:** `./gradlew jvmTest` · `./gradlew jsTest` · `./gradlew mingwX64Test`
- **One test:** `./gradlew jvmTest --tests "SubscriptionTest.subscribeReceivesEveryChange"`
- **Benchmarks:** `./gradlew :benchmarks:jmh` — not wired into `build`, run deliberately. Takes
  several minutes: three forks, because one cannot separate a real change from JVM-to-JVM variance
  and the differences that matter are single-digit nanoseconds
- **Publish:** `./gradlew publishAllPublicationsToDeftuSnapshotsRepository` (or
  `...DeftuReleasesRepository`) — normally run from the Release workflow, not by hand

Publishing credentials come from `kit.maven.username` / `kit.maven.password`, which Kit also
reads from `KIT_MAVEN_USERNAME` / `KIT_MAVEN_PASSWORD`. Absent them Kit never registers the
Deftu repositories, so the publish task does not exist rather than failing for credentials.

Native targets only link and test on a matching host: a Windows or Linux machine compiles the
Apple targets but skips their link and test tasks. CI runs `macos-latest`, so a green local
build is not proof the Apple targets link.

A toolchain bump fails `kotlinStoreYarnLock` until you run `./gradlew kotlinUpgradeYarnLock`.

## Design notes

The model is pull-based. A write marks; a read resolves. That one sentence explains most of what
follows.

- **`state()` tracks, `state.value` does not.** A tracked read mutates the dependency graph, so
  it is a function; an untracked read is plain data access, so it is a property. The rule extends
  to terminal reads: `getOrDefault` and friends are functions, so they track.
- **Compose inverts this.** Compose's `.value` is the tracked read. A habit carried across
  compiles cleanly and silently never updates. Say so wherever someone might arrive.
- **Nodes have four states** — Clean, Check, Dirty, Disposed — and `mark` only ever raises. Check
  is what stops a write recomputing a whole subgraph: the closure is marked cheaply, and only
  paths whose values actually changed recompute.
- **Only effects are queued, and on Check as well as Dirty.** An effect is where a pull starts.
  Memos are never queued, which is what makes an unobserved memo cost nothing.
- **Memo bodies run under the lock; effect bodies do not.** A memo must be pure, cheap, and take
  no other lock — a foreign lock under the graph lock is a real deadlock, not a slowdown.
- **Effects are dispatched through a `Scheduler`,** every run including the first, so an adapter
  can put them on the thread it needs. Dispatch coalesces, and coalescing happens *before* the
  dirty state is consumed — the other order loses writes outright.
- **The flush re-entrancy guard is per-thread.** Shared, one thread's flush cancels another's and
  strands its effects. This was measured: fifty writes produced thirty-three runs.
- **Lifetimes are explicit, not garbage-collected.** Every effect belongs to an `Owner`; disposing
  one tears down its subtree depth-first with cleanups in reverse creation order. Elementa uses
  weak references for this and it does not port — common Kotlin has none.
- **An effect may read state that outlives it, never state that dies first.** Not enforceable;
  checked in review.
- **Reactive list slots are positional,** and values shift through them, so `list[i]` means
  whatever is at `i` now. An indexed read registers on the structure signal too, so a structural
  edit wakes every positional reader — `mapKeyed` is the escape for list-shaped UI.
## Conventions

- No dependencies in `commonMain`. If something seems to need one, it belongs in a consumer,
  not here.
- Code goes in `commonMain` unless it genuinely cannot. `jvmMain/ext/Colors.kt` is the only
  platform-specific source, and it exists solely because `java.awt.Color` has no common
  equivalent. A second one needs a reason of the same kind.
- `explicitApi()` is on. Public declarations carry an explicit visibility modifier and an
  explicit return type — not an inferred one — because the inferred type is part of the
  published ABI whether or not you wrote it down.
- Tests live in `commonTest` so every target runs them. A test only moves to a platform
  source set when the thing it tests does.
- **Every code sample in a document has a mirroring test.** The README, the usage guide, the
  specification's worked examples and each adapter README are all executed, and the tests assert
  what the samples' *comments* claim rather than only that they compile. This is not tidiness: an
  uncompiled sample drifted through four milestones, the same wrong sentence lived in two documents
  at once, and compiling one of them exposed a real double-run bug in the graph. A sample nobody
  runs is an untested claim, and some claims are about behaviour.
- **Test first, implement after.** Write the test, watch it fail for the right reason, then
  make it pass. A test written after the code tends to assert what the code happens to do
  rather than what it should do, which is a test that can never fail. Each test states a
  property that would hold for any correct implementation.
- Test names are sentences stating the property, in camelCase:
  `subscribeOnceDisposesItselfAfterFiring`, not `testSubscribeOnce`.
- A change to a public signature is a breaking change for every target at once, including
  the generated TypeScript. Bump `project.version` in `gradle.properties` accordingly.
- The build scripts carry comments on the places Kit 0.6.0 needs working around, each saying
  what breaks without it. Leave them until the Kit side is fixed.
- The README, `MIGRATING.md` and `docs/` are public surface. A change to a public signature
  changes them too, and every sample in them is compiled — see the sample rule above.

## Code Guidelines

### Comments

Permitted comments, exhaustively:

- `/** */` KDoc on public items — on **every** one, saying what the signature cannot. For the
  `ext/` operators that is the equality cutoff each buys, not what it returns; a doc restating
  the name satisfies the letter of this and is worth nothing. If there is no such thing to say,
  the declaration probably should not be public — but where it genuinely must stay public and
  genuinely has nothing to add, a plain one-line KDoc is correct. "Required and banned" is
  resolved in favour of required.
- *Why* a non-obvious line is the way it is: a platform quirk, a build-tool gap, a measured
  tradeoff, an invariant the types can't express, a link to the upstream issue
- `TODO(owner):`

Everything else is noise. Specifically, never write:

- Narration of the next line — `// Add the subscription`, `// Loop over listeners`
- Restatements of the signature — `// Returns the current value`
- Section headings — `// === Helpers ===`, `// --- Setup ---`, and undecorated labels over a
  block such as `// Desktop native`. The ban is on the function, not the punctuation: a blank
  line already separates groups.
- Changelog commentary — `// now uses a snapshot instead of the live list`,
  `// added to fix the dispose test`. That belongs in the commit message.
- References to documents a reader of the clone does not have. The test is whether the document
  is **tracked in git**: naming `MIGRATING.md` or a file under `docs/` is fine, and so is
  `[StateNode.mark]`. Naming anything under `.agents/` is not, because it is gitignored and
  per-machine. Neither is a citation by number — "see D42", "per section 4" — since the
  reader cannot resolve it. State the reason inline instead. Design tools and note systems are
  never citable.
- Comments inside test bodies. The test name is the sentence stating the property. KDoc *above*
  a test is allowed only for something the name cannot carry — why an assertion is loose, or a
  nondeterminism the reader would otherwise take for a bug.

If a comment describes *what* the code does, delete it and fix the naming, or extract a
named function. A comment is not a substitute for a binding with a good name.

Before reporting a change as finished, re-read your own diff and delete every comment
that is not on the permitted list. Re-adding them on a later edit is the same violation.

## Bash Guidelines

### Output handling

- DO NOT pipe output through `head`, `tail`, `less`, or `more`
- NEVER use `| head -n X` or `| tail -n X` to truncate output
- IMPORTANT: Run commands directly without pipes when possible
- IMPORTANT: If you need to limit output, use command-specific flags (e.g. `git log -n 10`
  instead of `git log | head -10`)
- ALWAYS read the full output — never pipe through filters

### General

- Do not create new non-source code files (e.g. Bash scripts, SQL scripts) unless explicitly
  prompted to
- When provided problems, do not say "I didn't introduce these problems" (shifting the
  blame/effort) - just fix them.
