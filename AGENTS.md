# stateful

Kotlin Multiplatform library for observable state: a value you can read, subscribe to, and
derive other values from. Published as `dev.deftu:stateful`, LGPL-3.0.

**Zero runtime dependencies is a hard invariant.** `commonMain` declares none, and neither
does any platform source set. A consumer takes this library and nothing else.

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

### Targets

| Group    | Targets                                                          |
| -------- | ---------------------------------------------------------------- |
| JVM      | `jvm` (desktop, Android, server)                                  |
| Web      | `js(IR)` (browser, Node.js), `wasmJs` (browser only)              |
| Desktop  | `linuxX64`, `mingwX64`, `macosX64`, `macosArm64`                  |
| Apple    | `iosArm64`, `iosSimulatorArm64`, `tvosArm64`, `tvosX64`, `tvosSimulatorArm64`, `watchosArm64`, `watchosX64`, `watchosSimulatorArm64` |

Kit's multiplatform convention brings up the JVM, web and desktop rows from the `kit.kmp.*`
properties in `gradle.properties`. The Apple row is declared by hand in `build.gradle.kts`,
because the convention's native set stops at desktop.

Both JS targets generate TypeScript definitions, so a change to a public signature is a
change to the emitted `.d.ts` as well.

### Source layout

| Path                                | Contents                                                      |
| ----------------------------------- | ------------------------------------------------------------- |
| `commonMain/.../stateful`           | The API surface: `State`, `MutableState`, `StateListener`, `Subscription`, `Disposable` |
| `commonMain/.../stateful/impl`      | Concrete states: `SimpleState`, `SimpleMutableState`, `MappedState`, `ZippedState`, `FlatMappedState` |
| `commonMain/.../stateful/dsl`       | Factories (`stateOf`, `mutableStateOf`, `zippedStateOf`, `combineStateOf`) and property delegates |
| `commonMain/.../stateful/ext`       | Extensions per value type: `Booleans`, `Strings`, `Pairs`, `Triples` |
| `jvmMain/.../stateful/ext`          | `Colors` — the one extension that needs a JVM type (`java.awt.Color`) |
| `commonTest`                        | All tests, flat, no package                                    |

Only the base classes live in the root package. An implementation goes in `impl/`, a
factory in `dsl/`, an extension on a value type in `ext/`.

## Commands

From the root:

- **Build and test everything:** `./gradlew build`
- **Tests only, every target:** `./gradlew allTests`
- **One target:** `./gradlew jvmTest` · `./gradlew jsTest` · `./gradlew mingwX64Test`
- **One test:** `./gradlew jvmTest --tests "SubscriptionTest.subscribeReceivesEveryChange"`
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

- `State` owns its own subscriber list. Subscribing hands back a `Subscription`, and
  disposing it is the only way to stop listening — there is no `unsubscribe(listener)`,
  because identity comparison on lambdas is not something a caller can rely on.
- `set` compares against the current value and returns early when they are equal. Listeners
  fire on change, not on assignment.
- `notifyCurrent` iterates a snapshot of the subscriber list, so a listener may subscribe
  or dispose during dispatch without a concurrent-modification failure. A listener that
  throws does not starve the rest: the first failure is rethrown after the loop with the
  others attached via `addSuppressed`.
- Derived states (`MappedState`, `ZippedState`, `FlatMappedState`) subscribe to their
  sources on construction, which means they hold their sources alive. They are `Disposable`
  for that reason — a caller that drops one without disposing it leaks the subscription.
- `FlatMappedState` swaps its inner subscription each time the outer value changes. The
  inner state is transient; the outer one is not.
- `MappedState.rebind` and `ZippedState.rebindFirst`/`rebindSecond` re-point a derived state
  at a new source in place, so a long-lived binding does not have to be torn down and
  rebuilt by every consumer.
- `FlatMappedState.isDisposed` reports only the source subscription, where `ZippedState` ANDs
  both of its own. Already known — fix it or leave it, but do not re-report it.

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
- The README documents setup only and carries a notice saying it is outdated. Update it or
  leave the notice; do not delete the notice alone.

## Code Guidelines

### Comments

Permitted comments, exhaustively:

- `/** */` KDoc on public items
- *Why* a non-obvious line is the way it is: a platform quirk, a build-tool gap, a measured
  tradeoff, an invariant the types can't express, a link to the upstream issue
- `TODO(owner):`

Everything else is noise. Specifically, never write:

- Narration of the next line — `// Add the subscription`, `// Loop over listeners`
- Restatements of the signature — `// Returns the current value`
- Section headings — `// === Helpers ===`, `// --- Setup ---`
- Changelog commentary — `// now uses a snapshot instead of the live list`,
  `// added to fix the dispose test`. That belongs in the commit message.
- References to external documents, design tools or note systems. A comment must stand on
  its own for a reader who has only the repository.
- Comments inside test bodies. The test name is the sentence stating the property.

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
