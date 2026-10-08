# Changelog [^1]


## [Unreleased]

### Changed
- `useJdkRelease` no longer limits test code: JVM test compilations keep the JVM target's bytecode but see the compile JDK's full API, so tests can cover code paths that a runtime check enables only on newer JDKs. Main code is limited as before.

### Added
- Benchmark modules need no setup beyond the plugin: a module applying `org.jetbrains.kotlinx.benchmark` gets its JVM target and the build host's native target registered, all-open for JMH's `@State`, and `kotlinx-benchmark-runtime` at the plugin's version (a version you declare wins). Any module applying kotlinx-benchmark or `androidx.benchmark` ignores inherited publication, API validation and explicit API settings and skips Dependency Guard, as it ships nothing; settings made in the module itself still apply.
- Multi-release jars for KMP JVM targets: code in `src/jvm<N>Main` (N ≥ 9) compiles for JVM N, sees `jvmMain` including its `internal` declarations, and goes into `META-INF/versions/N` of the JVM jar with the `Multi-Release` manifest entry. A `module-info.java` there (e.g. `src/jvm9Main/java`) makes the jar a Java module. The JVM target's tests then run against the jar, so each test JDK runs the code variant it would load in production. The directory alone turns it on; a `jvm8Main`-style directory below 9 warns, as Java ignores it.
- `TEST_JDK=<N>` (env var or Gradle property) runs every JVM `Test` task, Android unit tests included, on JDK N through Gradle toolchains, while the build itself and its compilation stay on one JDK. Run a CI leg per JDK your library supports to test code paths chosen by JDK version at runtime. A launcher you set on a task wins; a non-numeric value, or one below the JVM target of the test bytecode, fails the build with the fix.
- JS browser tests run with Kotlin's Playwright runner (Kotlin 2.4.20+, `browser { test { chromium() } }`), which needs no local Chrome. Without fluxo, they currently time out after 30 seconds: Kotlin's test page loads the newest Mocha, and Mocha 12 broke it. fluxo pins that page to Mocha 11 until Kotlin's page pins a version itself.
- A Kotlin JVM compile with the JDK API limit on now warns when a jar or class directory on its classpath contains `java.*` classes (e.g. `android.jar` added to shared JVM code): Kotlin then accepts JDK methods that jar declares, newer than the JVM target, and the build fails at runtime on that Java version. A `RELEASE=true` build fails instead. Jars from repositories (in the Gradle user home) are not checked.

### Fixed
- Detekt 2 tasks of several modules failed at random in parallel builds ("Key …BuiltinsVirtualFileProvider duplicated"): the Kotlin analysis they run in the Gradle daemon can't start twice at once. They now run one at a time within a build.


## [0.17.0] - 2026-10-07

### Changed
- Kotlin 2.2 and 2.3 now validate ABI with Kotlin's own engine, as 2.4+ already did, so no BCV plugin is needed. Its dumps are byte-identical to BCV's, so committed dumps keep passing. `apiDump`, `apiCheck` and the per-target names still work; `check` runs Kotlin's check (`checkLegacyAbi` before Kotlin 2.3.20, `checkKotlinAbi` from it). To keep BCV, apply it in the build.
- **breaking** KMP modules with no `apiValidation {}` block now also dump and check klib ABI (`<module>.klib.api`), with BCV and with Kotlin's own engine (which Kotlin 2.2 and 2.3 modules now get), as `klibValidationEnabled` (default `true`) always said. Run `apiDump` once and commit the new file, or set `apiValidation { klibValidationEnabled = false }`.
- **breaking** `commonCompileOnly(…)` now adds the dependency as `compileOnly`, so JVM and Android consumers of your library no longer get it at runtime; it used to be `implementation` everywhere. JS, Wasm and Native get it as `api` (Kotlin can't build them against a compile-only dependency). This also applies to the Compose runtime fluxo adds for `@Stable`/`@Immutable` in KMP modules with JetBrains Compose. If your JVM consumers need the dependency at runtime, declare it with `implementation`.
- On Kotlin 2.2+, BCV's task names (`apiCheck`, `klibApiCheck`, `jvmApiCheck`, `apiDump`, …) are aliases of `checkKotlinAbi` or `updateKotlinAbi`, so `-x apiCheck` skips nothing; their task descriptions now say so and name the task to exclude.
- With publication set up, a SNAPSHOT version gets the current commit (`1.2-abc1234-SNAPSHOT`) only in builds that run a publishing task (`publish*`, `upload*`, `deploy*`, `release*`, `ship*`, `distribute*`, `install*`); other builds keep the declared version, so their jars are named `lib-1.2-SNAPSHOT.jar`. Published coordinates are unchanged.

### Fixed
- `detektBaselineMerge` merged the leftover baseline file of a Detekt task fluxo had disabled (the experimental compilation's, which `detektMain` covers since 0.16.0, or a target `KMP_TARGETS` filters out), so the committed baseline kept findings for code that no longer exists. It now merges only the tasks that ran; run it once, without a target filter, to drop those entries.
- On AGP 8.4–8.7, a KMP module applying `com.android.kotlin.multiplatform.library` failed configuration with `NoClassDefFoundError: …KotlinMultiplatformAndroidLibraryTarget`: that AGP's plugin has an older target type. fluxo now leaves such a module's Android target to AGP's defaults and prints one warning naming AGP 8.8, from which fluxo sets it up as on AGP 9.
- With publication set up, every build ran git to read the current tag or commit; git's output is a configuration-cache input, so each new commit or tag discarded the configuration cache, even for `help`. Git now runs only when a SNAPSHOT version is published.
- A two-part SNAPSHOT version lost its minor part when stamped with the commit: `1.2-SNAPSHOT` was published as `1-abc1234-SNAPSHOT`. The commit replaces only a patch part now (`1.2.3-SNAPSHOT` → `1.2-abc1234-SNAPSHOT`, `1.2-SNAPSHOT` → `1.2-abc1234-SNAPSHOT`).
- With `setupCoroutines` on (the default), every test compilation, and the experimental one, printed four warnings `Opt-in requirement marker 'kotlinx.coroutines.…' is unresolved` when the module had no coroutines. The coroutines opt-ins now go only to compilations with `kotlinx-coroutines-core` among their dependencies, however it got there; shared test source sets (`commonTest`, …) keep them for the IDE. With `optInInternal`, main compilations got them too, and a module without coroutines failed to compile in CI and release builds (warnings are errors there); they now follow the same rule.

### Updated
- fluxo-bcv-js 1.3.0, applied for `tsApiChecks`: it dumps and checks the TypeScript API next to Kotlin's own ABI engine on Kotlin 2.2+.


## [0.16.2] - 2026-10-06

### Fixed
- On Kotlin 2.4+, every Wasm-JS executable compile printed `e: Flag is not supported by this version of the compiler: -Xplatform-arguments-in-main-function=process.argv`. fluxo asked KGP to pass process arguments to `main` for Wasm too, but since 2.4 the Wasm compiler doesn't accept that flag. fluxo now asks only for JS, and for Wasm on Kotlin before 2.4.
- On Kotlin 2.4+, a default `wasmWasi()` target crashed the compiler in `compileProductionExecutableKotlinWasmWasi` (so in `assemble` and `build`) with "Cannot access to js related std in wasi mode": fluxo requested TypeScript declarations for WASI, which has no JS host. It now requests them only for JS and Wasm-JS.


## [0.16.1] - 2026-10-06

### Fixed
- Builds with nested modules (e.g. `:benchmarks:jmh`) failed configuration. fluxo put KSP and plugin-publish on parent modules too, so a submodule declaring one with a version failed ("already on the classpath with an unknown version"), and a module without a build file failed ("…because no repositories are defined"). Now only modules without submodules get these plugins, resolved through your plugin repositories.
- Detekt 2 skipped `commonTest` (and other shared source sets) when `KMP_TARGETS` left out every JVM and Android target. Normally those sets are analysed inside the JVM/Android test tasks, which the filter disables.


## [0.16.0] - 2026-10-06

[//]: # (Sections: Removed, Added, Changed, Fixed, Updated. Common Changelog style.)
[//]: # (CONSUMER-FACING ONLY — see AGENTS.md "Conventions" for the strict scope rule.)

### Changed
- **breaking** A module without `jvmTarget` no longer compiles for whichever JDK runs Gradle (the same commit gave different bytecode on different machines). Libraries now default to JVM 17; applications to the newest target the JDK running Gradle and your Kotlin allow (with `gradle/gradle-daemon-jvm.properties`, that is the pinned daemon JDK). Android keeps AGP's own Java target. If a compile task fails in a module whose target was defaulted this way, the build ends with one warning naming the module and the fix: set `jvmTarget`.
- **breaking** Java sources are now limited to the JDK API of the module's JVM target (`javac --release`), as Kotlin already was: a Java call to JDK 21 API in a module targeting 17 now fails to compile instead of failing at runtime with `NoSuchMethodError`. Modules passing `--add-exports`, `--add-reads` or `--patch-module` stay unlimited (javac rejects them with `--release`). `useJdkRelease = false` turns both limits off.
- **breaking** Kotlin code in Android modules (AGP 9, and KMP Android targets on either AGP) no longer sees the JDK's own API, only `android.jar`, as AGP 8's `kotlin-android` plugin already did: a call to JDK-only API (e.g. `java.lang.constant`) used to compile and then fail on the device; now it fails to compile. Test code keeps the JDK. `useJdkRelease = false` turns it off.
- **breaking** KMP Android libraries on AGP 9 (`com.android.kotlin.multiplatform.library`) get host (unit) tests and Android resources by default, as on AGP 8: without `withHostTest {}` the plugin creates no test task, so `commonTest` tests silently stopped running on Android, and with resources off the AAR lost `res/`, assets and Compose resources. fluxo enables host tests unless your build calls `withHostTest {}` itself or sets `DISABLE_TESTS=true`; resources when the module has `src/androidMain/res` or `assets`, or `composeResources` dirs (`androidResources.enable = false` turns them off).
- **breaking** JS and Wasm-JS targets set up by fluxo now run their tests in a browser too, as fluxo always meant to: on Kotlin 2.0+ a wrong target check skipped the browser setup, so tests ran in Node only. `check` runs them with Kotlin's default runner (Karma, headless Chrome); where Karma finds no Chrome, the browser test task is skipped with a warning naming the fix (install Chrome or set `CHROME_BIN`), and Node tests still run. The test runner's npm packages change your `kotlin-js-store` lock files, so run `./gradlew kotlinUpgradeYarnLock kotlinWasmUpgradeYarnLock` once. JS tests in Node now time out after 10 s instead of Mocha's 2 s.
- **breaking** The highest JVM target now comes from your Kotlin Gradle plugin (JVM 26 on Kotlin 2.4), not from a table inside fluxo, so a newer Kotlin needs no fluxo release. An explicit `jvmTarget` above what your Kotlin supports now fails configuration with the limit and the fix, instead of silently compiling to a lower target. `latest`/`max`/`current` still mean the newest that works.
- **breaking** KMP target groups (`allDefaultTargets()`, `ios()`, `macos()`, `tvos()`, `watchos()`, `linux()`, `androidNative()`) add only the targets your Kotlin fully supports, read from your Kotlin at configuration time, and never `iosX64` (Compose Multiplatform dropped it). One warning per build lists the deprecated targets the groups skipped; call a target explicitly (e.g. `macosX64()`) to keep it. An explicit call to a target your Kotlin can no longer build fails with the target name and the fix.
- **breaking** Unset Android SDK levels: minSdk defaults to 23 (current AndroidX libraries require it; 21 failed the manifest merge), compileSdk to the newest your Android Gradle plugin supports (36 on AGP 8.13, 37 on AGP 9.4), and an application's targetSdk to its compileSdk. Previously 21/35/35 came from fluxo's bundled catalog. Your own catalog keys (`androidMinSdk`, `minSdk`, …) and the DSL still win.
- **breaking** A call above the module's minSdk (Android Lint `NewApi`) now fails `check`. Every module's Lint still runs first; `mergeLintSarif` then fails once, listing every such call with the fix (`SDK_INT` guard, `@RequiresApi`, or a higher minSdk). Accept a call with a Lint baseline (`updateLintBaseline`); turn the check off with `lint { disable += "NewApi" }`.
- **breaking** Modules whose Kotlin stdlib is 2.2 or newer now run Detekt 2 (`dev.detekt`, bundled at 2.0.0-alpha.6) when AGP is absent or 8.8.2+. Detekt 1 can't read stdlib 2.2+ metadata, so its type-resolving rules silently found nothing there. Detekt 1 baselines match nothing in Detekt 2 (run `detektBaselineMerge`), the `formatting` section of `detekt.yml` is now `ktlint`, some rules were renamed and config keys removed (e.g. `output-reports`; a Detekt 1 `detekt.yml` fails Detekt 2's config validation until migrated, see Detekt's migration guide), KMP task names put the compilation first (`detektMainAndroid`, was `detektAndroidMain`), and the rule packs without a Detekt 2 build (arrow, verify-implementation, Ivy explicit, hbmartin) are not added on that line. A failing Detekt 2 task ends the build with these points. Apply `id("io.gitlab.arturbosch.detekt")` in a module to keep Detekt 1 there; a module applying either line keeps it. The root `mergeDetektSarif` task is now fluxo's own SARIF merge, no longer Detekt 1's `ReportMergeTask`.
- The tool plugins fluxo bundles (Detekt 1, Spotless, gradle-versions, dependency-guard) now yield to a version you declare in your build, older or newer; before, fluxo's version silently replaced any older one you declared. Spotless older than 7.0 lacks API fluxo's formatting setup uses, so with such a version fluxo skips Spotless and warns once.
- On Kotlin 2.4+, ABI validation (`enableApiValidation`, on by default for Gradle plugins) uses the Kotlin Gradle plugin's own engine, so the BCV plugin no longer has to be declared. Dumps stay where and as they were (`api/`, byte-identical to BCV's with the same filters), `check` runs `checkKotlinAbi`, and BCV's task names (`apiDump`, `apiCheck`, per-target `jvmApiCheck`, `androidApiCheck`, `klibApiCheck`, …) remain as aliases; each runs the one check or update that covers every target. A module stays on BCV when the build applies BCV itself (not `apply false`) or sets `klibValidationEnabled = false` or `klibSignatureVersion`. In KMP modules built with only some targets (`KMP_TARGETS`, split targets) the check and update are skipped, since the dump covers every target.
- Plain `detekt` now runs every Detekt task of the module, as `check` does. In KMP modules it used to check no files and pass while `check` failed on the same code, so it is now slower and can fail.
- `check` analyses each file once: plain `detekt` skips its own pass while the type-resolving tasks (`detektMain`, `detektTest`, …) cover all its files (a `source` you add elsewhere still runs), and the `experimentalLatest` compilation gets no Detekt task, as it compiles the main sources again. Both used to repeat the same analysis.
- Gradle-plugin modules (`fkcSetupGradlePlugin`) without `kotlinLangVersion` now compile at the language/API version of the Kotlin embedded in the running Gradle, the rule Gradle's own `kotlin-dsl` uses. Before, Kotlin 2.4 on Gradle 9.0 built a plugin Gradle could not load. The plugin then loads on the Gradle that built it and newer; set `kotlinLangVersion` to support an older Gradle.
- `fkcSetupMultiplatform()` without a `kmp`, `kotlin` or `android` block now sets the module up, like every other `fkcSetup*()`. It used to return early, so the module got no fluxo setup and its `defaults {}` (its own or a parent's) created no targets.
- `fkcSetupIdeaPlugin` keeps an explicit `jvmTarget` below 17 instead of silently raising it to 17; an unset target gets the library default 17.
- fluxo itself is compiled at Kotlin language/API 2.2, so it loads only on Gradle whose embedded Kotlin is 2.2 or newer: Gradle 9.0+, the documented floor.
- **breaking** The documented AGP floor is now 8.4 (was 8.0). AGP 8.0–8.3 can't build on Gradle 9, which fluxo already requires: 8.0–8.1 fail configuration, 8.2–8.3 fail inside AGP.
- **breaking** `allDefaultTargets()` no longer adds the worker-thread Kotlin/Native test runs (`<target>BackgroundTest`, built with `-trw`): they roughly doubled native link and test time for every module. Turn them back on with `backgroundNativeTests = true` (any KMP module, inherited from the parent project) or `kotlin { setupBackgroundNativeTests() }`.
- `LOAD_KMM_CODE_COMPLETION` no longer applies the complete-kotlin plugin, and prints one line saying it can be removed. Every Kotlin/Native distribution since at least 2.1 already contains the platform libraries of all targets on every OS (e.g. iOS `platform.Foundation` on Linux), so the plugin downloaded nothing.
- **breaking** New strict compiler defaults, each with its own switch (per module, or `DISABLE_KOTLIN_DEFAULTS` name in brackets): Kotlin's extra warnings, `extraWarnings` (`wextra`); the unused return value checker on Kotlin 2.3+, `returnValueChecker` (`return-value-checker`); annotations on constructor properties also apply to the property below language version 2.4, `annotationDefaultTargetParamProperty` (`annotation-default-target`); a data class's `copy()` gets its constructor's visibility, `consistentDataClassCopyVisibility` (`consistent-data-class-copy-visibility`); and type-checking `when` compiled with `invokedynamic` at JVM target 21+ on Kotlin 2.2 and 2.3, `whenExpressionsIndy` (`when-expressions`). The new warnings fail warnings-as-errors builds (CI and release) until fixed or switched off. In a library, a data class with a non-public constructor loses its public `copy()`, an ABI change for its users.
- **breaking** Your own Kotlin and Android settings now beat fluxo's defaults, whether written before or after `fkcSetup*()`: before, fluxo set its values on every compile task, so e.g. `kotlin { compilerOptions { progressiveMode = false } }` was ignored. A flag you pass for the module or a target now replaces fluxo's flag of the same name. A namespace, SDK level or test runner set in `android {}` before `fkcSetup*()` is no longer overwritten (AGP 8, and non-KMP modules on AGP 9). Builds that relied on fluxo overriding their own values now get those values. Exceptions: the JVM target is still fluxo's `jvmTarget`, and the per-compilation exceptions (no warnings-as-errors for tests, JS and shared metadata; a separate test language version) are changed on the compile task.
- A module whose Kotlin language or API version is deprecated by its own Kotlin (2.1 on Kotlin 2.4) no longer fails warnings-as-errors builds on that warning alone; the build prints one warning naming the modules and the version to move to.
- A plain build prints one fluxo line: `fluxo-kmp-conf: Gradle …, JDK …, Kotlin …`, plus AGP and Compose versions when present, `CI`, `RELEASE`, an active `KMP_TARGETS` filter, the modes that change the build (`MAX_DEBUG`, `FLUXO_VERBOSE`, `USE_KOTLIN_DEBUG`, `DESUGARING`, `DISABLE_R8`) and why tests are off.
- The per-module setup lines (applied plugins, dependencies added, publications, POMs, signing, namespaces) are now info level; `FLUXO_VERBOSE`, `MAX_DEBUG` and `--info` still show them, and `FLUXO_EXPLAIN` also lists the Android namespace fluxo set. Now info instead of a warning or error: "Dynamically loaded plugin …" (every Gradle-plugin module printed it for sam-with-receiver), "Tests are disabled!", "… is enabled!", the Compose Desktop ProGuard replacement, and a failed `git` lookup for the SCM tag outside a git checkout.
- Warnings now name their cause and fix, e.g. a plugin fluxo has to load itself with the configuration cache on, `check` or `test` requested while tests are off, and a publication setup that fails in a build that doesn't publish (e.g. no project version), which used to be an error line.

### Added
- Settings plugin `io.github.fluxo-kt.fluxo-kmp-conf.settings`, now part of the default setup next to the root line (README). It puts the plugins fluxo applies by id — KSP (`setupKsp`), fluxo-bcv-js (`tsApiChecks`), plugin-publish (`fkcSetupGradlePlugin`) and dependency analysis (`buildHealth`, `projectHealth`, `reason`) — on each module's build classpath, so they need no `plugins {}` lines of their own and work with the configuration cache; a version you declare wins. In a multi-project build the root project gets only dependency analysis, as a root copy of a plugin breaks subprojects that declare it with a version. Without the settings line, the root plugin does the same for subprojects. Dependency-guard baselines of the root build classpath gain these plugins. Builds that can't resolve them (offline, blocked repository) skip them.
- `FLUXO_EXPLAIN=true` (environment variable or Gradle property) prints, at build end, one line per setting the plugin derived for each module (JVM target, JDK API limit, SDK levels, skipped KMP targets, Gradle-plugin language version) with the reason and how to change it, also on a configuration-cache hit.
- Every compiler default fluxo adds can be switched off: per module with `jsr305Strict`, `validateBytecode`, `emitJvmTypeAnnotations`, `dontWarnOnErrorSuppression`, `expectActualClasses`, `suppressVersionWarnings` and the five new strict defaults above (plus the existing `progressiveMode` and `useJdkRelease`), or for the whole build with `DISABLE_KOTLIN_DEFAULTS=<names>` (environment variable or Gradle property; names as the build log shows the flag, e.g. `-Xjsr305` or `validate-bytecode`, plus `progressive` and `jdk-release`). The build-wide switch wins over the module settings, and an unknown name fails the build with the closest valid one.
- A `kotlinCoreLibraries` version below the Kotlin compiler now works in KMP modules with JS or Wasm targets: JVM and Android keep that stdlib, while Kotlin/JS and Kotlin/Wasm compilations get the compiler's own `kotlin-stdlib` and `kotlin-test`, the only ones their compiler accepts (an older one fails with "… standard library has an older version …" or "… has the ABI version … that is not compatible"); Native keeps its bundled stdlib. `FLUXO_EXPLAIN` shows the split. `singleKotlinStdlibVersion = true` keeps one stdlib version everywhere and fails the build when a JS or Wasm target would need the split.
- `js(targetName, configure)` for the Kotlin/JS target.
- fluxo's configuration warnings (a plugin to declare, a tool too old or unsupported on your Gradle, JDK API not limited, Kotlin version adjustments, tests off for a requested `check`, a skipped setup step, a deprecated setting) are also reported to Gradle's Problems API, each with its fix, so they appear in Gradle's problems report (`build/reports/problems/problems-report.html`) and in IDEs that read it. The console line stays.
- `optIns` accepts a marker's short name (`ExperimentalUuidApi`) for the Kotlin stdlib, kotlinx coroutines, serialization and datetime, and Compose markers, and fails the build on an unknown short name, naming the closest one. A marker that exists only on some platforms (`ExperimentalPathApi` on the JVM, `ExperimentalForeignApi` on Native) is passed only to those platforms' compilations, so the others no longer warn that it is unresolved. The default opt-in to `kotlin.RequiresOptIn` is dropped: the compiler ignores it.
- Kotlin 2.5 support: fluxo 0.15 failed configuration on Kotlin 2.5 (`NoSuchMethodError … watchosArm32`) because Kotlin 2.5 removes that target.
- The Gradle Plugin Portal page declares configuration-cache support, and declares Isolated Projects unsupported.

### Deprecated
- `JsTarget.compilerType` and `js(compiler = …)`: Kotlin deprecates `KotlinJsCompilerType` (IR is the only compiler) and schedules it for removal in Kotlin 2.6. Use `js(targetName, configure)`.

### Fixed
- Passing your own `-Xjsr305` value (e.g. `-Xjsr305=warn` in `kotlin { compilerOptions }`) failed every Kotlin/JVM compilation with "Conflict duplicating -Xjsr305 value: warn, strict", because fluxo added its `-Xjsr305=strict` next to it. Your value now replaces fluxo's.
- `enableSpotless` failed `check` in builds without a root `.editorconfig` ("EditorConfig file does not exist"), and with the configuration cache on it failed whenever the ktlint version fluxo uses differed from Spotless's default ("Add a step … into the `spotlessPredeclare` block").
- `setupKsp = true` without KSP declared never applied KSP: fluxo fetched it without a version and the load failed with an error line while the build went on. Gradle-plugin modules without plugin-publish declared skipped Plugin Portal setup the same way ("unknown plugin class name"). Both are now on the module's build classpath (see the settings plugin above). Where fluxo still can't put KSP or dependency analysis (the root project of a multi-project build, or a single-module build without the settings line), configuration with the configuration cache on stops with the exact `plugins {}` line to add; plugin-publish only warns with it.
- Optional tools fluxo applies when you didn't declare them (task-tree, taskinfo, module-dependency-graph) are now resolved by Gradle itself, so its repositories, dependency verification and offline mode apply and the configuration cache stores and reuses those builds. Before, fluxo loaded them into its own class loader: without a repository in the root `buildscript {}` they failed to load (`taskTree` did not exist), and plugins that add tasks broke the configuration cache.
- TypeScript ABI checks (`tsApiChecks`, fluxo-bcv-js) now come from the module's build classpath, where the settings plugin puts them, so they work with the configuration cache; they apply only to modules with a JS or Wasm-JS target.
- `useDokka` needs Dokka on the module's build classpath (it reads the Kotlin plugin's model). Not declared, a configuration-cache build publishes plain Javadoc and warns with the `plugins {}` line to add; without the cache fluxo still loads it into its own class loader, as before, which the configuration cache can't store.
- `tiTree`, `tiJson` and `tiOrder` (taskinfo, applied when you run one of them) broke the configuration cache; Gradle now fetches taskinfo like the tools above. On Gradle 9.8, where taskinfo's newest release (3.0.2) fails with `NoSuchMethodError`, fluxo skips it and names `taskTree` or Gradle's `--task-graph` instead.
- `setupKapt` on AGP 9 with built-in Kotlin applied `org.jetbrains.kotlin.kapt`, which AGP rejects; the error was logged and the build went on without kapt, so annotation processors never ran. It now applies `com.android.legacy-kapt` (declare `id("com.android.legacy-kapt") version "<AGP version>" apply false` in the root `plugins {}`), and fails naming that line when it is missing.
- A `pinned` bundle in your version catalog never changed the root build classpath, which Gradle loads before any plugin runs, yet `buildEnvironment` and dependency-guard's `classpath.txt` showed the pinned versions while the older jars ran. fluxo now leaves that classpath alone, and when a pinned module runs there at an older version it prints, at build end, the root `buildscript { dependencies { constraints { … } } }` lines that do apply. Subproject pins work as before.
- A `KMP_TARGETS` filter made `check` fail before any task ran ("Could not determine the dependencies of task ':mergeDetektSarif'") when a filtered-out target still existed, such as the Android target AGP 9's KMP plugin creates.
- `KMP_TARGETS` disabled targets fluxo didn't create (AGP 9's `android`, targets declared directly in `kotlin {}`) only in verbose builds, so a filtered build still compiled them; in verbose builds it also disabled `wasmWasi` when asked for `WASM_WASI`, and printed an error line for each target it disabled.
- `fkcSetupGradlePlugin` dropped the requested plugin ID on Gradle 9.4+ and declared the plugin under its bare name, so composite builds failed with "plugin not found in included builds".
- KMP modules requesting the Android target with no Android Gradle plugin on the build classpath failed configuration with a bare `NullPointerException` (or `IllegalStateException`) instead of printing how to add AGP.
- On AGP 9's KMP plugin, `check` ran no Android Lint at all: AGP creates those Lint tasks only with `com.android.lint` applied, which fluxo now does.
- On AGP 9's KMP plugin, Detekt's Android tasks (`detektAndroidMain`, `detektAndroidHostTest`) failed at graph build ("cannot choose between the following variants"); they now use the classpath the Kotlin compiler used.
- On AGP 8 with Gradle 9, every module without unit tests failed `check` in `testDebugUnitTest` ("did not discover any tests"), caused by fluxo's Robolectric-friendly `isIncludeAndroidResources = true`.
- The merged Lint report (`lint-merged.sarif`) pointed findings from all but the first module at the wrong rule; code-scanning uploads showed wrong rule names.
- One `MAX_DEBUG`/`FLUXO_VERBOSE` build made every later build in the same Gradle daemon verbose, and once-per-build warnings appeared only once per daemon.
- When a plugin fluxo applies (the Kotlin plugin, kapt, KSP, publication tools) failed inside its own setup, fluxo printed an error line and the build went on without it, often green with the feature missing; the build now fails with the plugin's error. Example: Compose Multiplatform 1.11+ on Kotlin 2.1 ("Minimal supported Kotlin Gradle Plugin version is 2.2.0") configured a KMP module with no Kotlin plugin applied.
- Detekt tasks of KMP targets with a custom name (e.g. `jvm("desktop")`) were disabled with an error line and never ran; only targets removed by `KMP_TARGETS` now disable their Detekt tasks.
- `detektBaselineMerge` trimmed whitespace from baseline IDs, so findings whose ID ends in a space (ktlint `Indentation`) stayed reported however often the baseline was regenerated.
- Gradle-plugin modules loaded the sam-with-receiver Gradle plugin at fluxo's own Kotlin version instead of yours.
- The shrinker failed on Compose Multiplatform 1.12 desktop apps: two dependencies share the jar name `runtime-saveable-desktop-1.12.1.jar`.
- On Kotlin 2.4 every Kotlin/Wasm compile warned "Flag is not supported by this version of the compiler" for `-Xes-classes` and `-Xoptimize-generated-js`: fluxo passed JS flags to Wasm. fluxo also no longer passes flags that are already the compiler's default (`-Xlambdas=indy`, `-Xsam-conversions=indy`, `-Xuse-fast-jar-file-system`, `-Xoptimize-generated-js`), nor, in the latest-settings test compilation, flags newer Kotlin removed or deprecated (`-Xvalue-classes`, `-Xuse-fir-lt`) or that enable features already stable there (`-Xnew-inference`, `-Xenhance-type-parameter-types-to-def-not-null`, non-local `break`/`continue`, explicit backing fields from Kotlin 2.4).
- `useExperimentalFastJarFs = false` had no effect (the compiler uses its fast JAR file system by default); it now passes `-Xuse-fast-jar-file-system=false`.
- `setupDependencies = true` in a KMP module without JVM or Android targets made the Kotlin plugin warn "Unused Kotlin Source Sets" (`commonJvmMain`, `commonJvmTest`), which fluxo created for its JVM-only dependencies.
- A publishing build (`publish…`, `upload…`, `release…`, `install…` and similar tasks) whose publication setup failed, e.g. with publication enabled and no project version, printed an error line and went on, then stopped at "Task 'publishToMavenLocal' not found" or published a half-configured publication. It now fails with the setup's own error. Builds that don't publish only log it, as before.
- With publication enabled and the Vanniktech maven-publish plugin not applied, fluxo printed an error line and skipped publication setup: the project got no version, group or POM metadata from fluxo, and a Gradle plugin's marker was published as `unspecified`. fluxo now sets up publication with Gradle's `maven-publish` (only Vanniktech's extras, such as the Maven Central tasks, are missing), and `FLUXO_EXPLAIN` names the plugin to apply. A start task given by path (`:module:publishToMavenLocal`) now also counts as a publishing build.
- On Detekt 1, shared KMP test source sets (`commonTest`, `nativeTest`, `webTest`, …) were never analysed: Detekt 1's tasks cover each compilation's own source sets, and those belong to none directly. Detekt's plain `detekt` task (and `detektBaseline`) now takes them, without type resolution. Detekt 2 already covered them.
- Publication needed both `enablePublication = true` and `publicationConfig {}`; either alone silently set up nothing, although `publicationConfig {}` was documented to enable publication. Now a project's own `publicationConfig {}` enables it there (a config set only on a parent still publishes no module by itself), `enablePublication = true` alone publishes with the derived defaults, and `enablePublication = false` still turns it off.
- Published POMs: `<url>` held the release tag's link (`…/tree/v1.2.3`) instead of the project's (the tag link now goes to `<scm><url>`); the licence `<distribution>` held the licence URL, where only `repo` or `manual` are valid (now omitted); the SCM connection derived from `githubProject` used `git://`, which GitHub disabled in 2022 (now `scm:git:https://…`). Sources jars of Kotlin/JVM, Java and Gradle-plugin modules published without Vanniktech now include generated sources (e.g. build config), which were missing when their directory was added after `fkcSetup*()`.
- Publishing a release version without a signing key to a local `file:` Maven repository (e.g. one used by tests or local checks) was refused like an upload; only uploads (and the Plugin Portal and Maven Central tasks) now require signing.
- `resolveDependencies` failed with the configuration cache on ("cannot serialize object of type … DefaultProject"). Every project now has its own `resolveDependencies`, and the root one runs them all.
- A `KMP_TARGETS` filter without `JS` (e.g. `KMP_TARGETS=WASM_WASI`) disabled every Wasm test task, so `check` ran no Wasm tests and printed "Unexpected test task … Target should be disabled". Wasm tests now follow their own target.
- On AGP 8, Android applications never got fluxo's `targetSdk` or the kept locales (`androidResourceConfigurations`): the calls failed with `NoSuchMethodError`, which fluxo swallowed. On AGP before 8.8 the kept locales go into `resourceConfigurations`, and then fluxo leaves `generateLocaleConfig` off, since AGP rejects the two together.
- `fkcSetupAndroidApp` without a version code failed configuration ("versionCode is set to 0"); with none set, fluxo now sets none. `applicationId`, `versionCode` and `versionName` set in `android { defaultConfig {} }` are no longer overwritten.
- KMP modules with a JS target failed configuration on Kotlin 2.1 with `NoSuchMethodError` (`YarnRootExtension$Companion.getYARN()`).

### Updated
- Bundled tools: Detekt 1.x rule packs compose-rules 0.4.28 and faire 0.5.4 (may report new findings), Spotless 8.10.3, gradle-versions 0.64.0 (applied by its current id `io.github.ben-manes.versions`), KSP 2.3.12 when fluxo provisions it. That KSP needs Kotlin 2.2+, so on Kotlin 2.1 the build stops at configuration and names the Kotlin-tied KSP release to declare (`2.1.21-<ksp version>`), instead of failing inside `kspKotlin` with `NoSuchMethodError`.
- On Kotlin 2.4.20+ the yarn lock directory (`<root>/.kotlin-js-store`, unchanged) is set through Kotlin's Provider API, replacing the setter Kotlin 2.4.20 deprecates.


## [0.15.1] - 2026-06-23

### Fixed
- `dependencyGuard` no longer fails configuration under `-Dsplit_targets` when a module has zero guardable configurations (a target-restricted shard can leave none): the plugin is skipped on such modules instead of being applied with an empty config set, which throws "No configurations provided".
- BCV's `klibApiCheck` is disabled under `-Dsplit_targets`, where the built target set is a strict subset and the union KLib ABI check would always fail; the full-target (non-split) lane still enforces it.


## [0.15.0] - 2026-06-14

### Added
- Kotlin 2.3 support in the Kotlin → max-JVM-target table: Kotlin 2.3 now maps to JVM target 25, so consumers on Kotlin 2.3 requesting JVM 25 are no longer silently capped to a lower bytecode target.
- Binary-compatibility validation now also covers the KLib ABI and the AGP-9 KMP `android`-main API lane, not only the JVM/Android/JS API surfaces.
- Configuration-time guard that fails fast when the consumer's `kotlinCoreLibraries` (stdlib) version is newer than the `kotlin` compiler version. That skew otherwise surfaces as a fatal "runtime newer than compiler" diagnostic only under `allWarningsAsErrors` on CI/release, far from its root cause. Opt out with `-Pfluxo.allowKotlinStdlibSkew`.

### Changed
- The `commonCompileOnly(...)` DSL now applies the dependency to `commonMain` as `implementation` rather than `compileOnly` (so it reaches the runtime classpath). A common `compileOnly` dependency is not propagated to the JS/Wasm/Native platform compile classpaths and breaks their compilation; `implementation` is the only configuration valid for every target. Affects only direct callers of this function.

### Fixed
- API/binary-compatibility validation no longer crashes configuration on `klibApiCheck` (and other unmodelled API-compare tasks) for KMP consumers that enable validation with native targets while JVM or Android is filtered out. Unknown API-compare tasks are skipped instead of throwing.
- `jvmDefault` is now gated by the consumer's Kotlin Gradle plugin version — the typed `JvmDefaultMode` DSL on KGP ≥ 2.2 and the `-Xjvm-default=all` flag on KGP 2.1 — so consumers on either line no longer hit `NoSuchMethodError` or a silently-ignored setting.
- Apple-target consumers no longer fail `check` when the requested Xcode simulator runtime is not installed. The affected simulator test tasks are skipped via an `onlyIf` `simctl` availability probe.
- KMP modules no longer break when the `androidMain` ↔ `commonJvmMain` bridge has no intermediate source set to connect (e.g. android-only KMP); the bridge is a no-op in that case.
- `compileNativeMainKotlinMetadata` no longer fails under `allWarningsAsErrors` on the unsuppressable KLIB duplicate-`unique_name` resolver warning (KT-69310). `-Werror` is dropped for the shared-metadata (`common`) platform only; every leaf platform still recompiles the same common sources under `-Werror`, so genuine common-code warnings still fail the build.

### Updated
- The default ktlint version applied by `enableSpotless` advances 1.4.1 → 1.8.0. This affects only consumers that opt into `enableSpotless` without pinning `ktlint` in their own version catalog (an explicit `ktlint` catalog entry always wins); for them, newer ktlint rules may reformat or flag code that previously passed.


## [0.14.1] - 2026-05-17

### Fixed
- Android/JVM compatibility now follows the effective current-JRE target when `jvmTarget` is left unset. This keeps Java and Android compile compatibility aligned with Kotlin's implicit target and prevents AGP/KGP target-validation failures.
- KMP modules with every concrete target filtered out no longer emit Kotlin's "no applicable targets" warning as a normal warning. Explicit target-filter skips are reported only at verbose level, so filtered CI builds stay signal-rich.
- Filtered `check` builds no longer fail when optional local-publication tasks are absent, and KMP check setup no longer assumes JS/Wasm Yarn extensions exist when those targets are filtered out.
- Publication setup no longer warns about missing signing keys during non-publication task graphs. Unsigned non-snapshot remote publication still fails at the publication task boundary.
- Dynamic optional-plugin loading no longer resolves the buildscript classpath just to decide whether a plugin id is available, avoiding configuration-time resolution and improving configuration-cache hygiene.
- Android Lint no longer creates and then fails on a missing `lint-baseline.xml` during ordinary CI/release lint task runs. Baseline creation stays explicit through `updateLintBaseline`; existing committed baselines are still honoured.
- Removed stale `taskGraph.whenReady` task-graph hooks used for IDE-sync detection and unreachable-task disabling. Supported target/test filtering now relies on configuration-time wiring instead of mutating the realized execution graph.


## [0.14.0] - 2026-05-01

### Removed
- **breaking** `FluxoPublicationConfig.sonatypeHost` — Vanniktech 0.34.0 removed `SonatypeHost` and all OSSRH support; Sonatype Central Portal (via `publishToMavenCentral`) is the sole publish target since OSSRH retired 2025-06-30. The field had no remaining semantic content and a soft-deprecation no-op would silently swallow consumer values; a clean removal forces consumers to confront the migration. **Migration**: drop `sonatypeHost = …` from your `FluxoPublicationConfig {}` block and switch publish credentials to a [Central Portal user-token](https://central.sonatype.org/publish/publish-portal-gradle/).
- **breaking** dropped Compose-legacy fallback path. With the consumer floor at Kotlin 2.1, the JetBrains Kotlin Compose Gradle plugin (`org.jetbrains.kotlin.plugin.compose`) is the only configuration path. The previous `try/catch` fallback that injected `-P plugin:androidx.compose.compiler.plugins.kotlin:*` flags into `freeCompilerArgs` is gone, and the legacy `composeOptions.kotlinCompilerExtensionVersion = …` setter is no longer applied to AGP `composeOptions`. **Migration**: ensure your build resolves the Kotlin Compose plugin (it does automatically when `enableCompose = true` and consumer Kotlin ≥ 2.0).
- **breaking** `FluxoConfigurationExtensionKotlin.setupLegacyKotlinHierarchy` removed. With the consumer floor at KGP 2.0+, `applyDefaultHierarchyTemplate` (auto-applied since KGP 1.9.20) handles intermediate source-set wiring (`commonJvm`, `commonJs`, `native`, `apple`, etc.) unconditionally. The legacy manual path — gated on `pluginVersion < 1.9.20 && NO_MANUAL_HIERARCHY=false && setupLegacyKotlinHierarchy=true` — was permanently unreachable under the new floor. **Migration**: remove any `setupLegacyKotlinHierarchy = true` from your `fluxoConfiguration { }` blocks; the default KGP hierarchy already handles the same wiring.

### Changed
- **breaking** consumer-compat floor: Kotlin 1.9 → **2.1**, Gradle 8 → **9.0+**. Reasoning: Gradle 8.x daemons embed Kotlin 2.0.x at most; the build matrix in this release (Kotlin 2.2.21 + Gradle 9.3.1 + AGP 9.1.1) cannot guarantee 1.9 source-language compatibility across non-JVM KGP targets. The README badges, the example block in the Quick Start, and the `kotlinLangVersion`/`kotlinApiVersion`/`kotlinCoreLibraries` catalog defaults all advance accordingly.
- **breaking** `Provider<T?>.getValue(thisRef, property)` property-delegation operator removed. Kotlin 2.2's strict `T:Any` constraint on `Property<T>`/`Provider<T>` collapsed the nullable and non-null overloads to identical signatures; only the non-null variant survives. **Migration**: replace `val x: Foo? by fooProvider` with `val x: Foo? = fooProvider.orNull`.
- migrate publication archive permissions from the Gradle-9.0-removed `dirMode` / `fileMode` setters to `dirPermissions { unix("0755") }` / `filePermissions { unix("0644") }` (added in Gradle 8.3). Without this, consumers on Gradle 9 would hit `NoSuchMethodError` during publication archive setup.
- `FluxoPublicationConfig.repositoryUrl` defaults to `null` (was hard-coded to the now-retired Sonatype OSSRH staging/snapshot URLs). When unset, no extra Maven repository is registered — Vanniktech's Central Portal upload path is independent. Set explicitly when publishing to a custom mirror (Artifactory, internal Nexus, etc.).
- `androidBuildToolsVersion` now defaults to `null` unless explicitly set by the consumer or their version catalog. Modern AGP selects the compatible Build Tools version itself; keeping a bundled pin caused avoidable AGP-9 warnings as soon as the minimum accepted Build Tools version moved. Consumers that need a reproducible explicit pin can still set `androidBuildTools`, `buildToolsVersion`, or `androidBuildToolsVersion`.
- `fkcSetupIdeaPlugin`: `intellijVersion` now defaults to `""` (was a required parameter); existing callers that pass it explicitly continue to work. A deprecation warning is emitted at configuration time when the parameter is non-blank — in IntelliJ Platform Gradle Plugin v2 the IDE dependency is declared in `dependencies { intellijPlatform { intellijIdeaCommunity(version) } }` instead.

### Added
- recognise the AGP-9 KMP+Android plugin (`com.android.kotlin.multiplatform.library`, AGP `>= 8.8.0`, **required** from AGP `9.0`). The plugin replaces the legacy `com.android.library` + `kotlin("multiplatform")` co-application that AGP 9 hard-rejects, and auto-creates the `android` KMP target from `kotlin { android { } }`. Consumers can now drive AGP 9 KMP+Android modules end-to-end through `fkcSetupMultiplatform`.
- auto-apply `androidNamespace` / `androidCompileSdk` / `androidMinSdk` / `androidBuildToolsVersion` from `fluxoConfiguration { }` onto `KotlinMultiplatformAndroidLibraryExtension` when the AGP-9 KMP+Android plugin is on the classpath. Mirrors the behaviour the legacy `setupAndroidCommon` path already provided for `com.android.library` (the new extension does **not** extend `TestedExtension`, so the two paths are necessarily separate). Idempotent and defensive — explicit consumer-set values in `kotlin { android { } }` are never overwritten.
- Fluxo's preferred Android Lint configuration (sarif/html report toggles, CI-aware baseline, `lint.xml` discovery, `GradleDependency` / `NewerVersionAvailable` suppression in tests, lint-version reporting, `:mergeLintSarif` hookup) now applies to the AGP-9 KMP+Android plugin via `KotlinMultiplatformAndroidLibraryExtension.lint`. Previously this extension was unrecognised by the wrapper and consumers silently fell back to AGP defaults.
- `androidLibrary { }` inside `fkcSetupMultiplatform` is now AGP-version-aware: under AGP 8.x it applies `com.android.library` and runs the legacy configuration path (existing behaviour, untouched); under AGP 9.x it applies `com.android.kotlin.multiplatform.library` and skips the legacy `androidTarget()` create call (the AGP-9 plugin auto-creates the `android` KMP target, and post-extension config is delivered by the new `setupKmpAndroidExtension` / `setupKmpAndroidLint` paths). Empty `androidLibrary { }` blocks "just work" on both lines; non-empty `onAndroidExtension { }` blocks under AGP 9 are skipped with an explicit error-level log because their lambda receiver (`LibraryExtension`) is disjoint from the AGP-9 replacement type (`KotlinMultiplatformAndroidLibraryExtension`). `androidApp { }` retains a fail-fast under AGP 9+ — the AGP team has not shipped a KMP-aware application plugin, so there is no migration target.
- `setupRoom = true` now wires the project-level Room KSP args (`room.generateKotlin`, `room.incremental`, `room.schemaLocation`) under AGP-9 KMP+Android (`com.android.kotlin.multiplatform.library`), at parity with the existing AGP-8 `com.android.library` path. The legacy path's `sourceSets["androidTest"].assets.srcDir(roomSchemasDir)` cannot port — the AGP-9 KMP+Android extension drops the `NamedDomainObjectContainer<AndroidSourceSet>` collection, and KMP source sets (`androidDeviceTest`, etc.) expose `kotlin`/`resources` but not `assets`. If you run instrumented Room tests on this path, attach the schemas dir to the `androidDeviceTest` source set's resources manually (the schema directory location is logged at info level for that purpose).
- `fkcSetupAndroidLibrary` / `fkcSetupAndroidApp` (non-KMP entry points) keep working under AGP 9: `setupAndroidCommon`'s receiver is now the modern `com.android.build.api.dsl.CommonExtension` (was the legacy `com.android.build.gradle.TestedExtension`, which AGP-9 extension instances no longer implement at runtime). Subtype-aware dispatch routes `targetSdk` to `ApplicationExtension` only (AGP 9 dropped it from `LibraryBaseFlavor`), and the non-KMP path uses the AGP-9-built-in Kotlin support (`android.builtInKotlin = true` default) instead of the legacy `org.jetbrains.kotlin.android` plugin (which AGP 9 hard-rejects).
- `maxStackSize` task input on the bundled shrinker (`AbstractShrinkerTask`); defaults to `8m` (`-Xss8m`). Previous releases ran ProGuard with the worker-default thread stack, which overflowed deep optimisation passes on real-world Compose Multiplatform 1.10 inputs. Override per task when you need more headroom.
- self-warn (one-shot per JVM) at configuration time when the consumer's Kotlin plugin version is at or beyond the first untabulated minor in the Kotlin → max-JVM-target compatibility table. Surfaces silent JVM-target capping, which previously required maintainer attention to discover.
- self-warn (one-shot per JVM) at configuration time when the consumer's `kotlinLangVersion` exceeds Detekt's supported maximum, instead of silently clamping.
- KDoc for `FluxoPublicationConfig.projectName` explaining the Maven-artifact-ID character restriction.
- KDoc on `enableGradleDoctor` documents the consumer-side escape hatch: override Gradle Doctor defaults via the standard `doctor { ... }` extension on the root project (no wrapper API was added — the existing extension already covers it).
- README documents the four recognised version-catalog aliases (`androidx.compose.ui.tooling`, `square.leakcanary`, `square.plumber`, `pinned`) — previously a hidden consumer contract.
- `fkcSetupIdeaPlugin`: new `extension: (IntelliJPlatformExtension.() -> Unit)?` parameter for direct IntelliJ Platform extension configuration.

### Fixed
- `JRE_17` internal constant had the value `11` (copy-paste from `JRE_11 = 11`). Two consumer-visible effects: (1) when using `fkcSetupIdeaPlugin`, the plugin's JVM-target floor was enforced at JDK 11 instead of the required JDK 17, silently producing JDK-11-level bytecode in IDE-plugin builds that mandate JDK 17+; (2) the `-Xjdk-release` compiler flag was applied to JVM targets in the range `12..22` (should be `18..22`), meaning consumers requesting JVM target 12–17 received superfluous `-Xjdk-release` arguments that could alter codegen in unexpected ways.
- the Kotlin → max-JVM-target compatibility table was missing the Kotlin 2.1 (JVM 23) and Kotlin 2.2 (JVM 24) entries. Consumers on Kotlin 2.1+ requesting a JVM target of 23 or 24 were silently capped at JVM 22; they would see no error but the compiled bytecode targeted a lower JVM version than requested.
- `FluxoPublicationConfig.projectUrl` was never used as the POM URL fallback. `MavenPom.url` is a non-null `Property<String>` — calling `.get()` on an unset property returns `""`, which the `?: config.projectUrl` Elvis arm never reached. Changed to `.orNull` so the three-level fallback (`publicationUrl → existing POM url → projectUrl`) works as documented.
- shrinker API verification (`setupVerification = true`) would crash with `NoSuchMethodError` on Gradle 8.14+ and Gradle 9.x: the plugin used an internal `DefaultTestFailureDetails` constructor that Gradle 8.14 removed. Switched to the public `TestFailure.fromTestAssertionFailure` / `TestFailure.fromTestFrameworkFailure` factories (stable since Gradle 7.4).
- the Detekt `languageVersion` clamp parsed Kotlin language versions as `Float`, which silently misranks any future two-digit minor: `"1.10".toFloat() == 1.1f` (would rank below `1.9`), `"2.10".toFloat() == 2.1f` (would not trigger the clamp at all). Replaced with `kotlin.KotlinVersion`-based comparison. Latent today (no Kotlin 1.10/2.10 yet) but a correctness landmine for future minors.
- under AGP 9 + non-KMP `com.android.library`, applying the plugin caused `compileDebugKotlin` to fail at runtime with `NoSuchMethodError: BuildPerformanceMetrics.add$default(...)`. The plugin's published runtime classpath leaked `kotlin-compiler-embeddable` and 25 transitives (incl. detekt-core) onto the consumer's buildscript classpath; under AGP 9's built-in Kotlin (KGP 2.2.10 bundled) Gradle's `<latest>` resolution upgraded `kotlin-compiler-embeddable` to our pinned 2.2.21 while KGP itself stayed at 2.2.10, producing the inlined-helper signature mismatch. Both are now `compileOnly` — `KotlinToolingVersion` is provided transitively by KGP at the consumer-applied version, `BaselineProvider` by detekt-gradle-plugin, and the only direct shrinker call (`Lazy.getValueOrNull`) was inlined. KGP's own warning ("please remove kotlin-compiler-embeddable from the build classpath alongside KGP") no longer fires for our plugin.
- Compose setup no longer adds the `OptimizeNonSkippingGroups` compiler feature flag explicitly. Kotlin 2.2 enables it by default, so the wrapper kept behaviour while producing an avoidable Compose compiler warning in consumer builds.
- `fkcSetupIdeaPlugin`: `sinceBuild` parameter was a no-op since the IJ Platform v1 → v2 migration (the v2 configuration block was commented out). It now correctly wires to `IntelliJPlatformExtension.pluginConfiguration.ideaVersion.sinceBuild`, setting the `since-build` attribute in the patched `plugin.xml`.

### Updated
- bump build Kotlin **2.0.21 → 2.2.21** (matches the Kotlin embedded in Gradle 9.3.1's daemon). KSP follows: **2.0.21-1.0.28 → 2.2.21-2.0.5**. Consumers gain access to the Kotlin 2.2 compiler features through the wrap layer (the plugin's own DSL is still backwards-compatible to the new 2.1 floor).
- bump Gradle wrapper **8.11 → [9.3.1](https://docs.gradle.org/9.3.1/release-notes.html)**. Strict CC is now Gradle's preferred mode; the plugin's own CC compatibility is preserved.
- bump **AGP 8.7.2 → [9.1.1](https://developer.android.com/studio/releases/gradle-plugin#9-1-0)**. AGP 9 hard-requires Gradle 9.x. Internal: `CommonExtension`'s six type parameters collapsed to a single non-parameterised type; the plugin's `the<AndroidComponentsExtension<…>>()` lookups and the `AndroidCommonExtension` typealias have been adapted accordingly.
- bump **Compose Multiplatform 1.7.1 → [1.10.3](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.10.3)** — common `@Preview` annotation, Navigation 3, stable Compose Hot Reload. The wrapper no longer sets Compose compiler options that Kotlin 2.2 already enables by default.
- bump **Spotless 6.25 → [8.4.0](https://github.com/diffplug/spotless/blob/main/plugin-gradle/CHANGES.md)** — major-line bump. The plugin previously re-used Spotless internal classes (`FileSignature`, `NoLambda`); both were replaced by local equivalents in the 0.13.4 prep work, so the bump is a clean catalog change here.
- bump **Detekt 1.23.7 → [1.23.8](https://github.com/detekt/detekt/releases/tag/v1.23.8)**. Stays on the 1.23.x line because Detekt 2.0 stable still lacks AGP-9 compat (issue #8908).
- bump **IntelliJ Platform Gradle plugin 2.1.0 → [2.16.0](https://github.com/JetBrains/intellij-platform-gradle-plugin/releases)**. JDK 21 is required when targeting IntelliJ 2026.* — set `javaLangTarget = "21"` on consumer modules that target the latest IntelliJ.
- bump **kotlinx-binary-compatibility-validator 0.16.3 → [0.18.1](https://github.com/Kotlin/binary-compatibility-validator/releases)**. Standalone tool retained — Kotlin 2.x's built-in `kotlin.abiValidation { }` migration is deferred to a future release because routing the customisable `apiDumpDirectory` value through to ShrinkerKeepRulesFromApiTask + SetupArtifactsProcessing as a CC-safe task input is its own non-trivial refactor.
- bump **Vanniktech [0.30.0 → 0.36.0](https://github.com/vanniktech/gradle-maven-publish-plugin/releases/tag/0.36.0)**. 0.34.0 removed `SonatypeHost` and OSSRH support entirely (see `sonatypeHost` removal in **Removed**); 0.36.0 ships Kotlin 2.2 binary metadata, requiring the build-Kotlin bump above. The previously-deprecated `KotlinMultiplatform(JavadocJar, Boolean, …)` constructor is replaced by the explicit `SourcesJar.Sources()` form.
- bump bundled Dokka 2.0.0-Beta → [2.2.0](https://github.com/Kotlin/dokka/releases/tag/v2.2.0) (stable line) and migrate the wrapped publication path to DGPv2 task names (`dokkaGeneratePublicationHtml` / `dokkaGeneratePublicationJavadoc`). Dokka 2.1+ deprecated and 2.2 removes the v1 task graph (`dokkaHtml` / `dokkaJavadoc`); without the migration, consumers' `javadocJar` would fail at task graph construction.
- bump `com.gradle.plugin-publish` 1.3.0 → _2.1.1_, which is the current Gradle-9-compatible line used by the release workflow's `publishPlugins` task.
- bump bundled shrinker stack: **ProGuard 7.6.0 → [7.9.1](https://github.com/Guardsquare/proguard/releases/tag/v7.9.1)** + **proguard-core 9.1.6 → 9.3.2**. Adds Kotlin 2.3 metadata + Java 26 bytecode support — required by the Kotlin / AGP / Compose bumps in this release. Pair with the new `maxStackSize` default and the `!class/merging/horizontal` optimisation toggle (documented in `pg/rules.pro`) when shrinking Compose Multiplatform 1.10 outputs, where horizontal class merging has a known cycle-in-hierarchy regression.
- bump pinned-bundle and internal dependencies to current stable: `org.bouncycastle:bcprov-jdk18on` _1.79 → 1.84_ (security), `com.squareup.okio:okio` _3.9.1 → 3.17.0_, `org.json:json` _20240303 → 20251224_ (security), `com.google.guava:guava` _33.3.1-jre → 33.6.0-jre_, `org.apache.commons:commons-compress` _1.27.1 → 1.28.0_, `org.jetbrains:annotations` _26.0.1 → 26.1.0_, `org.ow2.asm:asm` _9.7.1 → 9.9.1_. Pinned-bundle members propagate to consumers that opt in via the `pinned` bundle alias.


## [0.13.2] - 2024-11-26

### Fixed
- fix publication config by handling SonatypeHost in the FluxoPublicationConfig dynamically.
- don't try to setup browser target for Wasm WASI.

### Added
- update README with configuration steps and examples.

### Updated
- bump dependency-analysis to _2.5.0_.
- bump mrmans0n's Detekt Compose Rules to [_0.4.19_](https://github.com/mrmans0n/compose-rules/releases/tag/v0.4.19).
- bump BuildConfig plugin from to _5.5.1_.


## [0.13.1] - 2024-11-25

### Added
- add JVM compatibility and Kotlin options flags to disable a corresponding autoconfiguration.

### Fixed
-  pin `kotlin-compiler-embeddable` dependency in support for Kotlin 2.1 ([more details](https://kotlinlang.slack.com/archives/C0KLZSCHF/p1729256644747559?thread_ts=1729151089.194689&cid=C0KLZSCHF))


## [0.13.0] - 2024-11-18

### Added
- allow not setting up `fluxo-kmp-conf` containers and use the default KMP hierarchy instead.

### Fixed
- return support for Kotlin's `-Xjdk-release=18+` in JDK 23+.
- fix bundled shrinker loading.

### Changed
- use `vanniktech/gradle-maven-publish-plugin` for publication.
- migrate to the new Develocity plugin for the build scans.
- prefer bundled R8 if it’s newer by default.
- change default values and logic for some configuration options for safety and convenience in the new projects (experimental and/or complicated options should be opt-in)
  - enableApiValidation = `false`
  - setupVerification = `false`
  - enableGenericAndroidLint = `false`
  - enableGradleDoctorProp = `false`
  - latestSettingsForTests = `false`
  - BinaryCompatibilityValidatorConfig.tsApiChecks = `false`

### Updated
- bump Kotlin to _2.0.21_!
- bump KSP to [_1.0.28_](https://github.com/google/ksp/releases/tag/2.0.21-1.0.28).
- bump JetBrains Compose to [_1.7.1_](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.7.1).
- bump Android Gradle Plugin to _8.7.2_ (compile-only dependency).
- bump a lot of other dependencies to the latest versions.


## [0.12.1] - 2024-10-10

### Changed
- ⚠ removed context-receivers support (dropped in Kotlin 2.1).

### Updated
- bump Kotlin to _2.0.20_!
- bump Jetbrains Compose to _1.7.0-rc01_ (compile-only dependency).
- bump Android Gradle Plugin to _8.7.0_ (compile-only dependency).
- bump binary-compatibility-validator to _0.16.3_.
- bump a lot of other dependencies to the latest versions.


## [0.12.0] - 2024-06-29

### Fixed
- fix the broken test-main dependencies in the intermediate KMP source sets.

### Added
- set `unitTest` source set tree for Android to the usual `test` tree.

### Changed
- add more granular control over targets in the `allDefaultTargets` helper.
- allow `bytestring` named dependencies in the DependencyGuard plugin despite the `test` substring.

### Updated
- bump task-tree to [_4.0.0_](https://github.com/dorongold/gradle-task-tree/releases/tag/4.0.0).
- bump Android Gradle Plugin to _8.6.0-alpha08_ (compile-only dependency).


## [0.11.0] - 2024-06-10

### Changed
- rename `jsApiChecks` property to `tsApiChecks`. **BREAKING CHANGE!**

### Updated
- bump [binary-compatibility-validator-js](https://github.com/fluxo-kt/fluxo-bcv-js) from _0.3.0_ to [_1.0.0_](https://github.com/fluxo-kt/fluxo-bcv-js/releases/tag/v1.0.0).


## [0.10.2] - 2024-06-08

### Updated
- bump jetbrains-compose to [_1.6.11_](https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.6.11).
- bump KSP to [_1.0.22_](https://github.com/google/ksp/releases/tag/2.0.0-1.0.22).
- bump Android Gradle Plugin to _8.6.0-alpha05_ (compile-only dependency).
- bump KtLint from _1.2.1_ to [_1.3.0_](https://github.com/pinterest/ktlint/releases/tag/1.3.0).
- bump mrmans0n's Detekt Compose Rules to [_0.4.4_](https://github.com/mrmans0n/compose-rules/releases/tag/v0.4.4).
- bump kotlin-compile-testing (kctfork) to [_0.5.0_](https://github.com/ZacSweers/kotlin-compile-testing/releases/tag/0.5.0) (test-only dependency).


## [0.10.1] - 2024-06-02

### Added
- allow disabling Android Lint when no Android plugin is used.
- detect when the project is a child of a composite build and has no startup tasks.
- configure Compose with the new Kotlin compiler plugin.
- use the Gradle Doctor plugin.
- support and use more Detekt rulesets.

### Changed
- revise the hierarchy of the source sets using both the new `KotlinHierarchyTemplate` and the old way. **Can be a BREAKING CHANGE!**

### Fixed
- don't apply Android Lint when an old Gradle is used (min supported Gradle is 8.7).

### Updated
- bump Android Gradle Plugin to _8.6.0-alpha04_ (compile-only dependency).
- bump Gradle from _8.8-rc-1_ to _8.8_.


## [0.10.0] - 2024-05-26

### Updated
- **bump Kotlin to _2.0.0_.**
- bump Android Gradle Plugin from _8.4.1_ to _8.6.0-alpha03_ (compile-only dependency).
- update the list of pinned build-time dependencies.

### Fixed
- fix the dependency pinning logic for buildscript dependencies.


## [0.9.1] - 2024-05-25

_**0.9.0** was skipped due to the release publication issues!_

### Removed
- delete deprecated Kotlin/Native targets, removed in Kotlin 2.0.

### Added
- auto downgrade Kotlin API version when it's greater than the language version (and warn about it).
- set up `validatePlugins` task for Gradle plugins. **BREAKING CHANGE!**
- enable AndroidLint checks for all projects by default.
- configure `updateLintBaseline` task for re-creating the baseline files.

### Changed
- prepare for Kotlin 2.0.
- rename `Unix` common source set to `Nix`. **BREAKING CHANGE!**
- parse fallback toml catalog for enabled plugins.

### Updated
- bump Kotlin from _1.9.23_ to _1.9.24_.
- bump jetbrains-compose to _1.6.10_.
- bump Gradle from _8.6_ to _8.8-rc-1_.
- bump Android Gradle Plugin from _8.4.0_ to _8.4.1_ (compile-only dependency).
- bump Android Lint to _8.6.0-alpha03_ (compile-only dependency).
- bump com.mikepenz.aboutlibraries to _11.1.4_.
- bump dependency-analysis to _1.32.0_.
- bump Guava to _33.2.0-jre_ (build-only dependency).
- bump [proguard-core](https://github.com/Guardsquare/proguard-core) from _9.1.3_ to _9.1.4_.


## [0.8.1] - 2024-05-05

### Added
- allow using JRE 21 as target, already supported in tooling.

### Changed
- tune Kotlin compilation configuration a bit.

### Fixed
- auto-disable `-Xjdk-release` for the broken configurations (_JRE 18..21_).
- fix Detekt BaselineProvider loading and restore `detektBaselineMerge` usage.

### Updated
- bump Android Gradle Plugin from _8.3.2_ to _8.4.0_ (compile-only dependency) in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/48.
- bump [io.nlopez.compose.rules:detekt](https://github.com/mrmans0n/compose-rules) from _0.3.15_ to _0.3.20_ in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/53.
- bump [binary-compatibility-validator-js](https://github.com/fluxo-kt/fluxo-bcv-js) from _0.2.0_ to _0.3.0_ in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/52.
- bump [proguard-core](https://github.com/Guardsquare/proguard-core) from _9.1.2_ to _9.1.3_ in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/49.


## [0.8.0] - 2024-04-22

__Public API is CHANGED!__ <br>
_You need to replace `setup*` calls to `fkcSetup*` ones like this: <br>
`setupMultiplatform` => `fkcSetupMultiplatform`._

### Added
- add Compose Desktop setup support and a test project for it.
- bundle toml version catalog with the plugin as a resolving fallback.
- add logging on auto changed yarn dependenciew for Kotlin/JS.
- enable `androidResources.generateLocaleConfig` in android apps by default.

### Changed
- rewise public APIs for ease of use.
- disable Dokka by default.
- unify `setupKotlin` API.

### Fixed
- connect Gmazzo's `BuildConfigTask` to `prepareKotlinBuildScriptModel`.
- do not use `-Xjdk-release` when compiled against the current JDK version.

### Updated
- bump dependency-analysis to _1.31.0_.
- bump task-tree to [_3.0.0_](https://github.com/dorongold/gradle-task-tree/releases/tag/3.0.0).
- bump KSP to [_1.0.20_](https://github.com/google/ksp/releases/tag/1.9.23-1.0.20).
- bump proguard-core to [_9.1.2_](https://github.com/Guardsquare/proguard-core/releases/tag/v9.1.2).
- bump io.nlopez.compose.rules:detekt to [_0.3.15_](https://github.com/mrmans0n/compose-rules/compare/v0.3.11...v0.3.15).
- bump Guava to [_33.1.0-jre_](https://github.com/google/guava/releases/tag/v33.1.0) (build only dependency).
- bump Okio to [_3.9.0_](https://github.com/square/okio/blob/master/CHANGELOG.md#version-390) (build only dependency).
- bump Android Gradle Plugin to _8.3.2_ (compile-only dependency).
- bump com.mikepenz.aboutlibraries to _11.1.3_.
- bump jetbrains-compose to _1.6.2_.


## [0.7.0] - 2024-03-31

### Changed
- update compatibility methods `NamedDomainObjectSet<T>.named*` for Gradle 8.6+ and older.
- output relative paths for the merged report files in the log.
- replace shrinking setup with full-powered processing chains setup.
- update Kotlin compiler settings for _Kotlin 2.0_ and `-Xjdk-release`.

### Added
- self-apply the plugin to itself immediately with included build.
- support double-shrinking with both R8 and ProGuard.
- invalidate jar task when the artifact version changes (e.g. for git HEAD-based snapshots).
- use the local repository publication as one of the project checks.
- verify shrunken artifacts for all public declarations.

### Fixed
- prevent double escaping of cli arguments.
- fail build when shrinker fails to save size for release artifact.
- prevent double calculation of scmTag with GIT commands execution.

### Updated
- bump Android Gradle Plugin from _8.2.2_ to _8.3.1_ (compile-only dependency).
- bump Kotlin from _1.9.22_ to _1.9.23_.
- bump [KSP](https://github.com/google/ksp) from _1.0.17_ to _1.0.19_.
- bump [KtLint](https://github.com/pinterest/ktlint) from _1.1.1_ to _1.2.1_.
- bump `org.json:json` from _20231013_ to _20240205_ in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/37
- bump `com.mikepenz.aboutlibraries.plugin` from _10.10.0_ to _11.1.0_.
- bump Dokka from _1.9.10_ to _1.9.20_.
- bump R8 to _8.3.37_.
- bump Detekt to _1.23.6_.
- bump `gradle-intellij-plugin` to _1.17.3_.
- bump kctfork to _0.4.1_ (test dependency).


## [0.7.0-alpha2] - 2024-02-22

### Added
- log memory info on Fluxo context start.
- log R8 compatibility mode (full vs. compat).

### Fixed
- don't run in-memory shrinking if there is not enough memory available.
- don't mark the project as in IDE sync mode when no tasks where called and no composite build detected.
- remove invalid checks for composite mode.
- improve and document composite builds detection.
- properly quote and escape CLI arguments for external tool runner.
- fix R8 external run for Ubuntu and macOS (non-Windows systems).

### Changed
- bump gradle-intellij-plugin to 1.17.2.
- bump dependency-guard to 0.5.0.
- bump dependency-analysis to 1.30.0.
- bump R8 to 8.2.47.
- use R8 as a default shrinker (safer and more stable).
- make default publication configuration lazy (use Gradle Provider in `setupGradleProjectPublication`).


## [0.7.0-alpha1] - 2024-02-02

### Added
- allow switching on/off the R8 full mode, also called "non-compat mode." Disabled by default.
- add `FLUXO_VERBOSE` flag to enable verbose output without enabling the `MAX_DEBUG` mode.
- report a version of the bundled/classpath ProGuard version.
- report `includedBuilds` number during the composite build.
- add some documentation and to-do notes.
- create infrastructure for automated R8 and ProGuard shrinkers testing in [0ee74ca](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/0ee74cad8bb6d84a610cefdbd40dbb6213f9ad68).
- add tests for R8 and ProGuard in [7181a82...226a05b](https://github.com/fluxo-kt/fluxo-kmp-conf/compare/7181a82...226a05b).
- shrink plugin artifact with R8 (saved 35.227%, 293.3 KB).
- control keep rule modifiers for all auto-kept classes (in auto-generated keep rules) in [8c8630c7](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/8c8630c7).
- support R8 or ProgGuard available in the classpath (bundled) and support loading in the classpath as a more stable alternative to external run in [07af4372](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/07af4372).

### Fixed
- Fix `TestReportResult` Gradle compatibility in [1923b815](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/1923b815).

### Changed
- `DISABLE_R8` now disables all shrinking altogether.
- improve logging output in [4357abd7](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/4357abd7ebb5192b2252758aeb9d52181904a500).
- improve error reporting for `ExternalToolRunner` in [34ffc208](https://github.com/fluxo-kt/fluxo-kmp-conf/commit/34ffc208).
- bump kotlinx-metadata-jvm to 0.6.2 for ProGuard (used only for ProGuard in a separate classloader or process).
- bump gradle.enterprise to 3.16.2.
- bump jetbrains-compose to 1.5.12.
- bump android-gradle-plugin to 8.2.2.
- bump binary-compatibility-validator to 0.14.0.
- bump detekt to 1.23.5.
- bump spotless to 6.25.0.
- bump proguard to 7.4.2.
- bump ben-manes.versions to 0.51.0.
- bump gradle.taskinfo to 2.2.0.
- bump compose detekt rules to 0.3.11.


## [0.6.0] - 2024-01-05

_Important release that adds advanced shrinking functionality!_

### Added
- **support for shrinking artifacts with ProGuard and/or R8 (ProGuard is used as a default optimal choice)!**
- **auto-generate R8/ProGuard keep rules from `BinaryCompatibilityValidator` API reports!**
- **support for replacing the original artifact with a shrunken one!**
- highlight publication setup in logs.
- verify that publication artifact version is set.
- both WasmWasi and WasmJS can be used together since Kotlin 2.0.
- register `depsAll` task as a rememberable alias for `allDeps`.
- save and show the reason, why the project is in IDE sync mode.
- add Gradle file and I/O utils.
- add util methods for detached dependencies in Gradle.
- add `ExternalToolRunner` and `AbstractExternalFluxoTask` for external tooling.
- add `JvmFiles` and `JvmFilesProvider` classes for easier universal JVM targets manupulations.
- add compatibility method `TaskCollection<T>.named {}` for Gradle 8.6+ and older.
- add minor improvements for `BinaryCompatibilityValidator` configuration safety.

### Fixed
- move `kotlinConfig` computed property to the project-level configuration extension from the root-level context.
- remove MemoizedProvider incompatibility with Gradle 8.6, prevent crashes on future usage, but log the errors.
- correct apiDump/apiCheck tasks dependency and finalize API reports generation with keep rules generation.
- aligh `iosSimulatorArm64` parameter name with all others (_action_ => _configure_).
- correct default JS/WASM targets setup for Kotlin 2.0.

### Changed
- remove tests & checks from the `release` CI workflow.
- remove explicit gradle plugin configuration, which isn't needed anymore.
- simplify logging, remove custom log markers completely.

### Updated
- bump Kotlin from _1.9.21_ to _1.9.22_.
- pin OkHttp (_4.12.0_), Guava (_33.0.0-jre_), and Json (_20231013_) versions due to the Security Advisories.
- bump github/codeql-action from 2 to 3 by @dependabot in https://github.com/fluxo-kt/fluxo-kmp-conf/pull/23
- bump BuildConfig plugin from _5.1.0_ to _5.3.2_.
- bump Android Gradle Plugin from _8.2.0_ to _8.4.0-alpha02_ (compile-only dependency).


## [0.5.0] - 2023-12-24

### Fixed
- correct publication configuration.
- workaround Gradle 8+ problems with publication.
- correct the Gradle Versions Plugin setup.

### Updated
- pin Okio version to 3.7.0 due to the Security Advisory [CVE-2023-3635](https://github.com/advisories/GHSA-w33c-445m-f8w7).


## [0.4.0] - 2023-12-20

### Fixed
- correct search for non-available extensions, handle more edge-cases overall.
- correct setup for the Binary Compatibility Validator.
- configure the Gradle plugin eagerly to avoid issues with composite builds.
- fix release workflow permissions.

### Changed
- log all configured dependencies.
- cleanup code, fix some Detekt warnings.
- use the plugin to configure and build itself.

### Updated
- build-config gradle plugin 5.1.0
- KtLint 1.1.0


## [0.3.0] - 2023-12-15

_Stabilization release._

### Changed
- setup artifacts publication.
- setup BinaryCompatibilityValidator.
- stabilize Detekt configuration.

### Removed
- remove deprecated API surface parts.

### Updated
- KSP 1.0.16
- Android Gradle Plugin 8.2.0
- Spotless 6.23.3
- Gradle Enterprise 3.16.1
- Dependency Analysis 1.28.0


## [0.2.0] - 2023-12-10

🌱 _Initial pre-release in the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/io.github.fluxo-kt.fluxo-kmp-conf)._


## Notes

[0.17.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.17.0
[0.16.2]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.16.2
[0.16.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.16.1
[0.16.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.16.0
[0.15.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.15.1
[0.15.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.15.0
[0.14.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.14.1
[0.14.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.14.0
[0.13.2]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.13.2
[0.13.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.13.1
[0.13.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.13.0
[0.12.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.12.1
[0.12.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.12.0
[0.11.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.11.0
[0.10.2]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.10.2
[0.10.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.10.1
[0.10.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.10.0
[0.9.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.9.1
[0.8.1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.8.1
[0.8.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.8.0
[0.7.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.7.0
[0.7.0-alpha2]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.7.0-alpha2
[0.7.0-alpha1]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.7.0-alpha1
[0.6.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.6.0
[0.5.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.5.0
[0.4.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.4.0
[0.3.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.3.0
[0.2.0]: https://github.com/fluxo-kt/fluxo-kmp-conf/releases/tag/v0.2.0

[^1]: Uses [Common Changelog style](https://common-changelog.org/) [^2]
[^2]: https://github.com/vweevers/common-changelog#readme
