# Isolated Projects in fluxo-kmp-conf

fluxo does not support Gradle's Isolated Projects yet: the root plugin configures every module from the root, which Isolated Projects forbid. This file lists what blocks it, and the design that removes those blockers while keeping today's DSL for builds that don't turn Isolated Projects on.

Until that design lands, new code must not add cross-project access (AGENTS.md). Each item below is one more site to move later.

## What fails today

Measured on Gradle 9.8.0 with `-Dorg.gradle.unsafe.isolated-projects=true --configuration-cache` (read 2026-10-06). Consumers were a single-module and a two-module Kotlin/JVM build on Kotlin 2.4.20, using fluxo's settings plugin plus the root plugin.

| Build | First failure | Next one |
|---|---|---|
| single module | `FluxoKmpConfPlugin.addToolsWithoutSettingsPlugin` reads `gradle.extensions` ("Project ':' cannot access Gradle.extensions") | none: with that read bypassed, `build` (which includes `check`) passes with no Isolated Projects problem |
| two modules | the same read | root `allprojects { extensions.create(fluxoConfiguration) }` ("cannot access 'Project.extensions' functionality on subprojects via 'allprojects'") |
| `checks/kmp` | the same read, in the included root build | not measured further: every later step depends on the root configuring its subprojects |

Isolated Projects stop the build at the first violation of this kind, even with `--configuration-cache-problems=warn`. So everything after the root's `allprojects` block is known from the code, not from a run. The inventory below comes from that reading.

Kotlin's own support: KGP supports Isolated Projects for JVM, Android and multiplatform builds since 2.1.20. JS and Wasm targets are not supported yet ([KT-80311](https://youtrack.jetbrains.com/issue/KT-80311), linked from [KT-54105](https://youtrack.jetbrains.com/issue/KT-54105)). A fluxo module with JS or Wasm targets therefore can't use Isolated Projects whatever fluxo does.

## Blockers, by kind

1. **Extension creation from the root.** `FluxoKmpConfPlugin` creates `fluxoConfiguration` in every project through `allprojects`. That block also runs root-only `afterEvaluate` work.
2. **Parent inheritance of the DSL.** `FluxoConfigurationExtensionImpl.parent` and the defaults walk read the parent project's `fluxoConfiguration`. Under Isolated Projects, projects configure in parallel, so a parent's values aren't even guaranteed to be set yet.
3. **One shared context object.** `FluxoKmpConfContext` is created on the root and used by every module. It holds mutable per-build state (`firstInBuild`, the in-sync flag, the `BuildEndReport` warnings, decisions and failure hints), and it hands out `rootProject`, which modules call into (`rootProject.tasks`, `.plugins`, `.logger`, `.reportProblem`, `.provider`).
4. **Root aggregation driven by modules.** Modules configure root tasks: `mergeDetektSarif` and `mergeLintSarif` (`SetupDetekt`, `SetupAndroidLint`), the root `check` wiring (`SetupVerification`) and the merged test report (`SetupTestsReport`).
5. **Whole-build hooks run by the root.** These use `allprojects` or `subprojects`:
   - `DependencyUpdatesPlugin`
   - `KotlinSetupDiagnosticTasks`
   - `DependencyPinningBundle` (build-script pins)
   - `DependencyGuardPlugin`
   - `DependencyAnalysisTasks` (`allDeps`, `resolveDependencies`, which also lists subproject task paths)
   - `FluxoCache` (clean hook)

   The root-only tool hook (`gradle.beforeProject`, which reads the parent's build-script class loader) belongs here too. So does the settings-plugin marker read through `gradle.extensions`.
6. **Root reads that only need a path or a name.** These sites read the root's directory, files or name:
   - `SetupCompose`
   - `SetupSpotless` (`.editorconfig`)
   - `SetupAndroidSigning`
   - `SetupPublication` (local repo)
   - `MergeDetektBaselinesTask`
   - `SetupDetekt`, `SetupAndroidLint` (`%SRCROOT%`)
   - `FluxoConfigurationExtensionPublicationImpl` (root name)

   They also read the root build-script repositories (`LoadAndApplyPluginIfNotApplied`).
7. **Property lookup.** `InternalUtils.stringPropValue` uses `findProperty`, which walks up the parent projects.

## Design

The settings plugin becomes the hub for Isolated Projects builds, and the root plugin keeps today's behaviour for builds that don't enable them. Everything Isolated-Projects-safe that the hub uses exists on the oldest supported Gradle (9.0.0 API jar, read 2026-10-06): `Project.getIsolated()`, `IsolatedProject` (name, path, project directory, root project) and `gradle.lifecycle.beforeProject`/`afterProject`.

| Blocker | Isolated Projects replacement | What users see |
|---|---|---|
| 1 extension creation | the settings plugin's `gradle.lifecycle.beforeProject` creates `fluxoConfiguration` in every project; the root plugin then creates it only on the root | nothing: `fkcSetup*()` and `fluxoConfiguration {}` work as today |
| 2 parent inheritance | defaults move to a `fluxoKmpConf {}` block in `settings.gradle.kts`, passed to each project as plain values; with Isolated Projects off, the root's `fluxoConfiguration {}` keeps working as the defaults | builds that enable Isolated Projects move their shared root settings into the settings block; nobody else changes anything |
| 3 shared context | a build service, registered by the settings plugin, holds the per-build state; modules read only their own project, and paths/names through `isolated` | nothing |
| 4, 5 aggregation and whole-build hooks | each module publishes its SARIF/test-report files as an outgoing variant, and root tasks resolve them through a configuration that depends on every project path known to settings; per-module hooks (pins, dependency-guard, `resolveDependencies`, the clean hook) run in that module's own `beforeProject` action | nothing; root task names stay |
| 6 root reads | `isolated.rootProject.projectDirectory`, `rootDir` and `isolated.rootProject.name` | nothing |
| 7 property lookup | the module's own property plus the same name in settings-level defaults; no parent walk | a flag set only in a parent module's `gradle.properties` stops reaching its children under Isolated Projects |

Root-only setup (no settings line) can't be made Isolated-Projects-safe, because only settings can reach every project without touching another project. Under Isolated Projects, fluxo should fail at the root with the settings line to add. The root-only hook (D6) and blocker 5's root branches stay for builds without Isolated Projects.

The single-module case needs only the settings-plugin marker read replaced. The settings plugin could record its presence where a project can read it without `gradle.extensions`, for example a build service registered under a fixed name. Whether a project may look that up under Isolated Projects is not measured.

Options considered and rejected:

- **Gradle's shared model defaults (`Settings.defaults {}`).** They serve declarative project types, not plugin extensions such as `fluxoConfiguration`.
- **Keeping parent inheritance under Isolated Projects by forcing parents to configure first.** That would forbid parallel configuration, which Isolated Projects exist to enable.
- **Moving every root task into each module.** It loses the one merged report CI uploads.
