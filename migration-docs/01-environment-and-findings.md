# ODK Collect — Build, Test & Coverage Findings

_Context: ODK Collect (branch `v2026.2.x`) is being evaluated for re-implementation on iOS.
Because most of this Android/Kotlin/Java code cannot be ported directly, the tests are the
primary executable specification of required behaviour. This document records how the project
was built and tested on Linux, the coverage we measured, and where the migration risk sits._

Generated 2026-06-02 on Ubuntu 25.04 (kernel 6.14), Intel Core Ultra 7 155H, 22 cores, 62 GB RAM.

---

## 1. What was installed (and where)

Nothing here was system-wide / root-installed except enabling KVM. Everything lives under the
user's home so it is easy to remove.

| Component | Version | Location | Notes |
|---|---|---|---|
| JDK (full, with `javac`) | Temurin **21.0.5+11** | `~/jdks/jdk-21.0.5+11` | System only had JREs (no compiler). JDK 24 is too new for AGP 8.13 / Gradle 9. Set as `JAVA_HOME`. |
| Android SDK cmdline-tools | latest (11076708) | `~/Android/Sdk/cmdline-tools/latest` | Installed via Google's zip. |
| Android Platform | **android-36** (+ android-34) | `~/Android/Sdk/platforms` | 36 = compileSdk; 34 added for the emulator. |
| Build-tools | **36.0.0** (+ 35.0.0) | `~/Android/Sdk/build-tools` | |
| Platform-tools (adb) | 37.0.0 | `~/Android/Sdk/platform-tools` | |
| Emulator | 36.6.11 | `~/Android/Sdk/emulator` | Used for instrumented coverage. |
| System image | `android-34;google_apis;x86_64` | `~/Android/Sdk/system-images` | API 34 chosen deliberately — see §5. |
| JaCoCo CLI | 0.8.12 (nodeps) | `/tmp/jacococli.jar` | For merging/reporting coverage. |
| `local.properties` | — | repo root | `sdk.dir=/home/cquiros/Android/Sdk` |

**KVM (root, one-time):** the only privileged step. `sudo modprobe kvm_intel` + grant access to
`/dev/kvm`. VirtualBox and KVM contend for VT-x, so one must be unloaded
(`~/fix_virtualbox.sh` unloads KVM; re-`modprobe` to restore).

Build toolchain the project itself pins: **Gradle 9.0.0**, **Android Gradle Plugin 8.13.0**,
Kotlin 2.2.20, `compileSdk 36`, `minSdk 21`, `targetSdk 35`.

### Reproduce a build
```bash
export JAVA_HOME=~/jdks/jdk-21.0.5+11 ANDROID_HOME=~/Android/Sdk
./download-robolectric-deps.sh        # one-time: fetch Robolectric android-all jars
./gradlew assembleDebug                # -> collect_app/build/outputs/apk/debug/ODK-Collect-debug.apk
```

---

## 2. Unit tests (JVM / Robolectric)

Run all module unit tests. Android modules use `testDebugUnitTest`; the two pure-JVM modules
(`shared`, `forms-test`) use a custom `testDebug` task:
```bash
./gradlew testDebugUnitTest test --continue -Ptest.heap.max=4g
```

**Result: 4,119 tests — 0 failures, 0 errors, 6 skipped.** (`collect_app` alone: 2,658.)
Source: 416 unit test files across the repo, 272 of them in `collect_app`.

> ⚠️ **Single-machine gotcha:** CI *shards* `collect_app`'s 272 test classes across runners.
> Running them all in one JVM at the default `test.heap.max=1g` hangs in a GC death spiral
> (Robolectric leaks classloaders/threads). Fixes: raise heap (`-Ptest.heap.max=4g`) and recycle
> the test JVM (`forkEvery`). Both were applied via an (uncommitted) `local-test.init.gradle`.

---

## 3. Instrumented tests (Espresso, on a device/emulator)

The instrumented suite is **97 androidTest files** in `collect_app`: 90 under `feature/`
(the Espresso UI tests), 4 under `instrumented/`, 3 under `benchmark/` (heavy; CI excludes them).

- On a **physical Pixel 8a (Android 16 / API 36)** the *non-coverage* suite installs and runs, but
  the **JaCoCo-instrumented coverage build fails on launch** — `NoActivityResumedException` on
  essentially every test (the offline-instrumented classes trip the app's debug StrictMode /
  activity launch on API 36). Also, a real phone already running ODK Collect blocks install
  (same package name + two hardcoded provider authorities). Not viable for coverage.
- On an **API-34 emulator** (KVM-accelerated, the config ODK's CI targets) it runs clean.

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :collect_app:createDebugCoverageReport \
  -Pandroid.testInstrumentationRunnerArguments.notPackage=org.odk.collect.android.benchmark \
  --continue
```

**Result on the API-34 emulator: 385/397 passed, 9 failed, 1 skipped** (~2h40m, sequential).
The 9 failures are emulator-timing flakes, not product bugs:
`SavePointTest` ×6 (process-kill/savepoint timing), `FormMetadataSettingsTest` ×2,
`AllWidgetsFormTest.testActivityOpen` ×1.

---

## 4. Coverage

Measured with JaCoCo. Unit coverage via AGP's `enableUnitTestCoverage`; instrumented via
`enableAndroidTestCoverage` on the emulator; merged with the JaCoCo CLI against every module's
compiled classes (generated code — R/BuildConfig/DataBinding/Dagger/Hilt — filtered out).

### Headline — the App as a whole

| Metric | **Combined (unit + instrumented)** | Unit only |
|---|---|---|
| **Line** | **63.0%** (22,524 / 35,730) | 15.2% |
| Instruction | 62.0% | 15.2% |
| Branch | 49.1% | 13.5% |
| Method | 61.5% | 15.1% |
| Class | 75.0% | 19.2% |

**`collect_app` (the core module, ~64% of all lines): 7% unit → ~71% combined**
(instrumented-only 69.5%). The Espresso suite is the missing half of the safety net.

> Merge gotcha: copy `coverage.ec` out of its `collect_test(AVD) - 14/` folder before feeding it
> to the JaCoCo CLI — spaces/parens in that path make the CLI silently skip it (you'll get the
> 15% unit-only number back).

### Per-module line coverage (unit-only unless noted)

The instrumented `.ec` credits only `collect_app`'s own classes, so **library modules below are
unit-only even though Espresso tests exercise them** — their real coverage is somewhat higher,
but they remain the relatively weak spots.

| Module | Line % | Module | Line % |
|---|---|---|---|
| **collect_app** | **~71% (combined)** | db | 22% |
| forms-test | 92% | audio-recorder | 18% |
| upgrade | 86% | strings | 15% |
| metadata | 85% | lists | 14% |
| settings | 84% | forms | 9% |
| open-rosa | 71% | location | 9% |
| entities | 65% | androidshared | 7% |
| shared | 63% | geo | 5% |
| permissions | 50% | async | 4% |
| projects | 46% | maps | 3% |
| | | audio-clips / crash-handler / draw / errors / material / mobile-device-management / qr-code / selfie-camera / web-page | ~0% |

Modules with production code but **no tests at all**: `google-maps`, `timedgrid`, `osmdroid`,
`image-loader`, `printer`, `analytics`, plus test-helper modules.

---

## 5. Areas of risk for the iOS re-implementation

Ranked by how blind you'd be re-implementing without a behavioural spec from tests.

**Lowest risk — strong executable spec exists (lean on these tests heavily):**
- Core app flows in `collect_app` (~71%): form filling, the widgets, form management/download,
  instance management/submission, settings, projects — all have dense Espresso + unit tests.
- Data/logic modules: `forms-test`, `metadata`, `settings`, `open-rosa` (OpenRosa protocol —
  critical for server interop), `entities` (the Entities/datasets feature), `shared`.

**Medium risk:**
- `permissions` (50%), `projects` (46%), `db` (22%), `audio-recorder` (18%) — partial specs;
  read tests for the covered paths, expect gaps.

**Highest risk — little/no automated spec; behaviour must be reverse-engineered from code/UX:**
- **Mapping/geo**: `geo` (5%), `maps` (3%), `google-maps`/`osmdroid` (0%). Map interaction,
  geopoint/geoshape/geotrace capture. Large surface, thin tests.
- **Media capture/widgets at the edges**: `draw` (signature/annotate), `selfie-camera`,
  `audio-clips`, `qr-code`, `printer` — ~0%.
- **No-test modules**: `timedgrid`, `image-loader`, `analytics`.
- **Anything platform-specific that won't carry over anyway**: background work/WorkManager,
  Android storage/scoped-storage, content providers, notifications — these are Android-shaped
  and will need iOS-native redesign regardless of test coverage.

**Cross-cutting note:** the two hardcoded content-provider authorities
(`org.odk.collect.android.provider.odk.{forms,instances}`) are an external API other apps depend
on; on iOS the equivalent integration surface needs a deliberate replacement design.

---

## 6. Next: per-test behavioural catalog

Because porting is a re-implementation, each `collect_app` test is effectively a behaviour spec.
The catalog of **what every test asserts** is tracked separately — see `02-collect_app-test-catalog.md`
(scope/approach pending). Inventory to cover: **272 unit + 97 instrumented = 369 test files.**

Unit test files by area (collect_app): widgets 91, formentry 27, utilities 21, preferences 20,
formmanagement 15, projects 11, instancemanagement 10, formlists 8, application 8, mainmenu 7,
database 6, audio 5, dynamicpreload 5, activities 4, backgroundwork 4, configure 4, fragments 4,
external 3, entities 3, views 3, + ~12 singletons.
