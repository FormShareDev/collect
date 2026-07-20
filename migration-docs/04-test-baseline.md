# 04 — Full test-suite baseline (unit + instrumented)

Complete pass/fail baseline of the ODK Collect test suite, produced so that another session can
verify whether a different implementation of Collect produces the same results. The per-test
record (one row per test) is in the companion file **`04-test-baseline-full.tsv`**; this document
explains the environment, the headline numbers, every failure and skip, and how to interpret a
diff against the TSV.

## Headline result

| Suite | Tests | Passed | Failed | Skipped |
|---|---|---|---|---|
| Unit (JVM/Robolectric, 30 modules) | 3,760 | 3,755 | **0** | 5 |
| Instrumented (Espresso on emulator) | 415 | 404 | **10** → 9 flaky + 1 environmental | 1 |
| **Total** | **4,175** | 4,159 | see below | 6 |

**There are zero product-bug failures.** Of the 10 first-run instrumented failures, 9 passed when
re-run in isolation (flakes of the long sequential run) and 1 fails deterministically only because
a headless emulator has no usable camera (detail below).

## Environment (run of 2026-06-11)

- **Host**: Ubuntu 25.04, kernel 6.14, x86_64, 22 CPUs, 62 GB RAM, KVM enabled.
- **Toolchain**: Temurin JDK 21.0.5+11, Android SDK (compileSdk 36, build-tools 36.0.0), AGP 8.13.0, Gradle 9.0.0.
- **Device**: Android **emulator, API 34** (`system-images;android-34;google_apis;x86_64`), pixel_6 profile, 4 GB RAM, headless (`-no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect`). Animations disabled (`window/transition/animator_duration_scale = 0`). This API-34 image is deliberate: it is the configuration previously verified to run the suite cleanly, whereas API-36 had activity-launch problems under instrumentation.

### Emulator setup (reproducible from scratch)

Prerequisites: KVM must be available to the user (`/dev/kvm` readable/writable — load with
`sudo modprobe kvm_intel`; note VirtualBox and KVM conflict, only one can be loaded), and the SDK
needs the `emulator` and `platform-tools` packages plus the system image:

```bash
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager \
  "emulator" "platform-tools" "system-images;android-34;google_apis;x86_64"
```

Create the AVD (the `echo no` declines the custom-hardware-profile prompt) and give it more RAM
and data space than the pixel_6 defaults:

```bash
echo no | ~/Android/Sdk/cmdline-tools/latest/bin/avdmanager create avd \
  -n collect_test -k "system-images;android-34;google_apis;x86_64" -d pixel_6 --force
echo "hw.ramSize=4096"                  >> ~/.android/avd/collect_test.avd/config.ini
echo "disk.dataPartition.size=6442450944" >> ~/.android/avd/collect_test.avd/config.ini   # 6 GB
```

Boot headless and wait for full boot (~55 s on this host):

```bash
~/Android/Sdk/emulator/emulator -avd collect_test \
  -no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect -port 5554 &

# block until booted
until [ "$(~/Android/Sdk/platform-tools/adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 5; done
```

Flag rationale: `-no-window/-no-audio` = headless; `-gpu swiftshader_indirect` = software
rendering (no GPU needed, fully deterministic); `-no-snapshot` = cold, reproducible state every
run; `-no-boot-anim` shaves boot time.

Then standard Espresso prep — disable all animations and keep the screen on (animations left
enabled are a major source of additional flakes):

```bash
ADB="~/Android/Sdk/platform-tools/adb -s emulator-5554"
$ADB shell settings put global window_animation_scale 0
$ADB shell settings put global transition_animation_scale 0
$ADB shell settings put global animator_duration_scale 0
$ADB shell svc power stayon true
```

Operational notes:
- AGP 8 / UTP prints nothing per-test to the console; the only live progress signal is
  `adb logcat -s TestRunner` (`started:`/`finished:`/`failed:` lines). The logcat ring buffer
  evicts within minutes, so start a persistent capture at launch if you want timing data.
- `gradle.properties` already sets `android.injected.androidTest.leaveApksInstalledAfterRun=true`,
  which speeds up consecutive runs (e.g. the flaky-retry pass) by skipping reinstalls.
- Tear down with `adb -s emulator-5554 emu kill`.

### Exact commands

```bash
# Unit (all modules; --continue so one failure doesn't stop the rest). 6m 32s on a warm cache.
JAVA_HOME=~/jdks/jdk-21.0.5+11 ANDROID_HOME=~/Android/Sdk \
  ./gradlew testDebugUnitTest test --continue \
  -Ptest.heap.max=4g --init-script local-test.init.gradle

# Instrumented (collect_app + androidshared, benchmarks excluded). 2h 44m 44s sequential.
ANDROID_SERIAL=emulator-5554 JAVA_HOME=~/jdks/jdk-21.0.5+11 ANDROID_HOME=~/Android/Sdk \
  ./gradlew :collect_app:connectedDebugAndroidTest :androidshared:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notPackage=org.odk.collect.android.benchmark \
  --continue
```

`local-test.init.gradle` (repo root, untracked) sets `forkEvery=40` / `maxParallelForks=2` on test
tasks. Without it plus `-Ptest.heap.max=4g`, the 2,658-test `collect_app` unit suite hangs in a GC
death spiral on a single machine (CI shards it instead; Robolectric leaks classloaders).

## The 10 instrumented failures, classified

A retry of the 4 affected classes was run on the same emulator immediately afterwards
(`-Pandroid.testInstrumentationRunnerArguments.class=...SavePointTest,...AuditTest,...FormMetadataSettingsTest,...AllWidgetsFormTest`).

### Flaky — failed in the 2h45m sequential run, passed on isolated retry (9)

All nine share one symptom family: Espresso timing around app-kill/recovery and dialog
transitions on a slow software-rendered emulator (`NoMatchingViewException` waiting for a screen,
or a recovery `DialogTitle` still present when not expected).

| # | Test | First-run error |
|---|---|---|
| 1 | `feature.formentry.SavePointTest.blankFormSavePointIsNotUsedWhenEditingInstance` | NoMatchingViewException: "Two Question…" not found |
| 2 | `feature.formentry.SavePointTest.editedInstanceSavePointIsNotUsedWhenFillingBlankFormOfTheSameForm` | recovery DialogTitle unexpectedly present |
| 3 | `feature.formentry.SavePointTest.savePointIsCreatedWhenLeavingTheApp` | NoMatchingViewException: "Two Question…" not found |
| 4 | `feature.formentry.SavePointTest.savePointIsCreatedWhenMovingForwardInForm` | NoMatchingViewException: "Two Question…" not found |
| 5 | `feature.formentry.SavePointTest.savepoint_doesNotPruneNonRelevantNodes` | NoMatchingViewException: "One Question…" not found |
| 6 | `feature.formentry.SavePointTest.whenEditing_savePointIsCreatedWhenLeavingTheApp` | NoMatchingViewException: "Two Question…" not found |
| 7 | `feature.formentry.SavePointTest.whenEditing_savePointIsCreatedWhenMovingForwardInForm` | NoMatchingViewException: "Two Question…" not found |
| 8 | `feature.formentry.audit.AuditTest.navigatingBackToTheFormAfterKillingTheAppWhenMovingBackwardsIsDisabled_savesFormResumeEventToAuditLog` | recovery DialogTitle unexpectedly present |
| 9 | `feature.settings.FormMetadataSettingsTest.metadataProperties_shouldBeReloadedAfterSwitchingProjects` | NoMatchingViewException: "demo@getod…" not found |

`SavePointTest` is systematically fragile in long runs on this hardware: 7/8 of its tests failed
in-sequence yet 8/8 pass in isolation (a June-2 run with coverage instrumentation showed the same
pattern, 6/8). The savepoint feature itself works; the tests' process-kill choreography is
timing-sensitive.

### Deterministic in this environment — failed both runs identically (1)

**`feature.smoke.AllWidgetsFormTest.testActivityOpen`**

```
androidx.test.espresso.NoActivityResumedException: No activities in stage RESUMED.
  at androidx.test.espresso.Espresso.pressBack(Espresso.java:237)
  at org.odk.collect.android.support.pages.Page.pressBack(Page.kt:103)
  at org.odk.collect.android.feature.smoke.AllWidgetsFormTest.testActivityOpen(AllWidgetsFormTest.kt:47)
```

The smoke test swipes through every widget of the all-widgets form; at the **Selfie widget** it
taps "Take Picture" and then presses back. On a headless emulator the camera capture activity
never reaches RESUMED (no usable camera under swiftshader), so `pressBack` finds no resumed
activity. Same stack both runs; also seen in the June-2 run. **Expected to pass on a real device
or Firebase Test Lab; expected to fail on a headless emulator.** Not a product bug.

## Skipped tests (6) — all deliberate upstream `@Ignore`s

| Suite | Test | Reason |
|---|---|---|
| unit | `entities.DatabaseEntitiesRepositoryTest` — 2 × `#query …property that doesn't exist` | `@Ignore("https://github.com/getodk/collect/issues/6615")` |
| unit | `entities.InMemEntitiesRepositoryTest` — same 2 tests | same issue 6615 (shared base class) |
| unit | `openrosa.parse.Kxml2OpenRosaResponseParserTest.#parseManifest returns null if a media file with type entityList is missing integrityUrl` | `@Ignore("This would break servers that had implemented type before integrityUrl was added to the spec …")` |
| instrumented | `feature.formentry.DeletingRepeatGroupsTest` (whole class, appears as 1 entry) | class-level `@Ignore`, no reason given upstream |

## What is NOT in this baseline

- **Benchmarks** (`org.odk.collect.android.benchmark` androidTest package): excluded via
  `notPackage` — they measure performance, need realistic hardware, and have no stable pass/fail.
- **Release-variant unit tests** (`testReleaseUnitTest`): the bare `test` task also ran 359 of
  them; all passed. They are byte-for-byte duplicates of debug-variant tests, so the TSV keeps
  only the debug row (one row per unique test). Beware when comparing aggregate Gradle counts:
  a naive XML count gives 4,119+ because of these duplicates.
- **`fragments-test` and `androidtest` modules**: test *helper* libraries, `src/main` only — they
  contain no tests. (Their `compileReleaseKotlin` fails upstream; irrelevant to tests.)
- **`google-maps` and `timedgrid`**: have test source dirs but zero tests; Gradle 9's
  `failOnNoDiscoveredTests` flags them as task failures. Not test failures — these modules simply
  have no unit tests (likewise no-test modules: osmdroid, image-loader, printer, analytics, icons…).

## How to compare another implementation against this baseline

1. Diff per-test against `04-test-baseline-full.tsv` (tab-separated:
   `suite, module, class, test, status, time_s, message`; 4,175 rows + header; sorted).
2. **Unit suite must match exactly**: 3,755 passed / 5 skipped / 0 failed. Any unit-test
   discrepancy is a real behavioral difference worth investigating.
3. **Instrumented**: treat the 9 flaky tests above as nondeterministic — pass or fail, neither is
   a meaningful discrepancy on emulator hardware. `AllWidgetsFormTest.testActivityOpen` is
   expected to fail on headless emulators and pass on devices with a camera.
4. A status of `skipped` must match the 6 documented `@Ignore`s; new skips mean the suites
   diverged.
5. Test names containing `#` or backticks are Kotlin backtick-named tests; compare them verbatim.

## Per-module unit-test counts (for quick structural comparison)

| Module | Tests | | Module | Tests | | Module | Tests |
|---|---|---|---|---|---|---|---|
| collect_app | 2,658 | | geo | 162 | | entities | 100 |
| open-rosa | 93 | | maps | 89 | | settings | 82 |
| audio-recorder | 78 | | shared | 74 | | forms-test | 63 |
| androidshared | 61 | | location | 44 | | audio-clips | 35 |
| lists | 31 | | permissions | 30 | | db | 27 |
| projects | 22 | | crash-handler | 15 | | mobile-device-management | 15 |
| material | 12 | | async | 11 | | qr-code | 11 |
| upgrade | 10 | | metadata | 9 | | draw | 8 |
| selfie-camera | 6 | | external-app | 5 | | strings | 4 |
| errors | 2 | | forms | 1 | | shadows | 1 |
| web-page | 1 | | | | | **Total** | **3,760** |

Instrumented: collect_app 397 + androidshared 18 = 415.
