# 05 — Collect → KotlinCollect data-collection parity test suite

Purpose: give a single, defensible answer to the question **"If KotlinCollect passes these
tests, am I confident the data collection is correct?"** ODK Collect is the oracle: whatever
Collect *shows* (the journey) and *produces* (the submission XML) is defined as 100% correct. The
suite is authored and locked against Collect on a real device, then copied to KotlinCollect and
re-run; any divergence is a KotlinCollect defect.

This extends the engine-level differential test in **kotlinrosa**
(`org.javarosa.differential.OdkReferenceFormDifferentialTest`, commit `d69358f`), which proves
JavaRosa ↔ KotlinRosa serialize identical values for every ODK operator/function. That test is
headless, single-journey, golden-valued. This suite lifts the same idea to the **app**: real
widgets, real appearances, and — critically — **branching journeys**, because a form with
relevance is not one path but many.

## 0. The two things every scenario asserts

1. **The journey** — which questions are present / absent / blocked, via the Page API
   (`assertQuestion`, `assertNoQuestion`, constraint/required enforcement).
2. **The output** — the submission XML captured by `StubOpenRosaServer.submissions`, parsed with
   `XFormParser`, asserted field-by-field (exact for deterministic fields, shape for the rest).

A form that branches on relevance ships **≥2 named journeys**, each with its own present/absent
list and its own expected output (including *pruned* nodes).

## 1. Harness binding (Collect side)

- Fixtures live in `test-forms/src/main/resources/forms/parity_*.xml` (+ `media/`), served by
  `StubOpenRosaServer` and copied into the demo project exactly like Collect's own tests.
- Tests live in `collect_app/src/androidTest/java/org/odk/collect/android/feature/parity/`.
- Bootstrap: `TestDependencies()` + `CollectTestRule(useDemoProject=false)` +
  `TestRuleChain.chain(testDependencies).around(rule)`.
- Fill + finalize + send + capture (the canonical shape, from `PartialSubmissionTest`):

  ```kotlin
  rule.withProject(testDependencies.server.url)
      .copyForm("parity_relevance_outside_repeat.xml", testDependencies.server.hostName)
      .startBlankForm("Parity Relevance Outside Repeat")
      // ... journey: answerQuestion / swipeToNextQuestion / assertQuestion / assertNoQuestion ...
      .fillOutAndFinalize(...)             // or swipeToEndScreen().clickFinalize()
      .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

  val xml = testDependencies.server.submissions[0].readText()   // the produced data
  ```

- Output assertions use `ParitySubmission` (this suite's helper): `valueOf(xml, tag)`,
  `assertExact`, `assertAbsentOrEmpty`, `assertShape` — mirroring the kotlinrosa reference helpers.

## 2. Determinism pinning (required for stable golden)

The kotlinrosa test pinned **UTC**. On-device we pin the same axes so date/number goldens are
stable and reproducible:

- **Timezone** pinned (UTC) for the run.
- **Locale** pinned (en-US) — number/date formatting is locale-sensitive.
- Non-deterministic fields (uuid, `now()`/`today()`/`start`/`end`, `random()`, `deviceid`,
  `username`, captured media binaries, GPS coordinates) are asserted by **shape**, never value —
  exactly as `nonDeterministicFields_haveExpectedFormat` does.

Cross-locale / cross-device parity is explicitly **out of scope** (single-device run); noted as a
residual risk in §6.

## 3. Golden-locking protocol (Collect is the oracle)

Computed expectations are **derived** while authoring, marked `// LOCK` in the test, then
**confirmed on the first Collect device run**. If Collect disagrees with a derived value, the
golden is changed to match Collect — never the reverse. Sequence:

1. Author fixture + journey + derived golden (offline, no device).
2. **[waits for phone]** Run on Collect on the device.
3. Capture `submissions[0]`; replace every `// LOCK` value with Collect's actual output.
4. Re-run on Collect → all green = golden locked.
5. Copy fixtures + tests to KotlinCollect; run; diff. Divergence = KotlinCollect defect.

Journey present/absent assertions are derivable offline (pure XForm relevance semantics) and do
not need locking; only *values* do.

## 4. Coverage ledger

Status: ⏳ planned · 🔨 authored (offline) · 🔒 locked on device · ✅ ported to KotlinCollect.

| # | Pillar | Fixtures | Journeys | Status |
|---|--------|----------|----------|--------|
| 1 | Functions & operators (reuse `odk_all_features.xml`) | 1 | 1 golden + 1 shape | ⏳ |
| 2 | Every widget × every appearance | ~10 (per family) | value round-trip each | ⏳ |
| 3 | Structure & nesting | 2–3 | nesting/paths | ⏳ |
| 4 | Repeats (fixed/var/user/nested/field-list; recount; fns) | 3–4 | ≥2 each | 🔨 (in progress) |
| 5 | Relevance & branching (incl. relevance from var **outside** repeat; cascading; choice-filter; constraint & required enforcement) | 3–4 | ≥2 each | 🔨 (in progress) |
| 6 | Defaults / dynamics / readonly / triggers | 2–3 | value + update | ⏳ |
| 7 | Metadata & submission envelope | 1 | shape | ⏳ |
| L | Lifecycle: savepoint, draft resume, **edit finalized instance**, process-death | 3–4 | data preserved | ⏳ |
| A | Adversarial/edge: div-by-zero, date overflow, huge/deep repeats, long/Unicode text, constraint-on-count, empty selects | 4–6 | Collect-as-oracle | ⏳ |

### Determinism policy per widget family (the honest part)
- **Exact value:** text, integer, decimal, url, range, rating, select-one (all appearances),
  select-multiple (value + order), rank, trigger, note/label, date/time/datetime (stored ISO),
  **barcode** (injected via the fake scanner in `TestDependencies`).
- **Exact via seeding:** geopoint/geotrace/geoshape — known coordinates fed via default/calculate
  so value + geo-functions (`distance`, `area`, `enclosed-area`) are exact.
- **Presence + populated only (NOT bytes):** image, selfie, draw, annotate, signature, audio,
  video, file — non-deterministic binary; assert the node is populated + relevance/required behave.
- **Non-Gregorian calendars:** the stored value is ISO; assert value round-trip, not picker pixels.

## 5. Fixture / naming conventions
- `parity_<pillar>_<variant>.xml`, form `id` = filename stem, human title = "Parity <…>".
- One test class per pillar: `Parity<Pillar>Test.kt`; one `@Test` per journey (named for the branch).
- Every fixture is XML-well-formedness-checked offline before commit; semantic correctness is
  established by the Collect device run (step 3).

## 6. Reflection — what passing does and does not buy (see conversation for full text)
**High confidence:** form-logic engine values; relevance/constraint/required semantics & node
pruning; repeat semantics (counts, add/remove, nesting, recount, repeat functions); the serialized
submission for deterministic fields.
**Moderate:** journey/branching behavior (logical presence, not pixel-faithful rendering).
**Low / untested unless extended:** hardware-capture binary fidelity; app-lifecycle persistence
(addressed by pillar L); locale/device variance (single-device); transport/encryption; adversarial
robustness (addressed by pillar A).
Net: passing ⇒ **high confidence in form logic and produced data**, **moderate in journey UI**,
and **explicit, documented gaps** elsewhere — which is why pillars L and A are in scope.

## 7b. GOLDEN LOCKED on device (2026-07-10)

Ran `:collect_app:connectedDebugAndroidTest` (package `feature.parity`) on a **Pixel 8a, Android 16
(SDK 36), America/New_York, en-US**. Final: **36 tests → 35 passed, 0 failed, 1 skipped.**
This is the locked baseline; port the fixtures + tests to KotlinCollect and diff against it.

Values corrected to Collect's real output during locking (Collect is the oracle):
- Date/time functions serialize with the **device-local offset**, not UTC `Z`:
  `fn_date`=`2026-01-15T00:00:00.000-05:00`, `fn_date_time`=`…10:30:00.000-05:00`,
  `fn_decimal_date_time`=`20468.208333333332`, `fn_decimal_time`=`0.2916666666678793`. Stable for
  America/New_York (fixed Jan/EST dates); KotlinCollect must run under the same TZ to compare.
- `time_default`=`15:30:00.000-04:00` (EDT, DST-sensitive — noted in the test).
- Adversarial math: `div_by_zero`=`Infinity`, `big_mult`=`9999999800000000`, `date_diff`=`2192`,
  `zero_div_zero` and `sqrt_neg` serialize **empty**.
- geopoint round-trips as `38.25 -77.0 0.0 0.0`; select-multiple stores in **selection order** (`c a`).

Follow-up additions (2026-07-11), both green on device:
- **External-CSV `instance()` now exercised end-to-end**: `cities.csv` (3 rows) attached via
  `copyForm("parity_functions.xml", listOf("cities.csv"), …)`; `fn_instance` locked to `3`
  (was the earlier `0` gap). `fn_current`=`0` over the loaded CSV.
- **`regex()` inside a constraint** added — `parity_constraint_regex.xml` (`^[A-Z]{2}[0-9]{3}$`) +
  `ParityEnforcementTest.regexConstraint_blocksInvalid_thenValidProceedsAndStores`. (Prior coverage
  already had `regex()` as a calculate `fn_regex`, `jr:choice-name()` → `fn_jr_choice_name`, and
  `indexed-repeat()` → `fn_indexed_repeat` + `parity_repeat_functions.first_val`.)
  Suite is now **37 @Tests**.
- **Full expression cross-check** against `odk_expressions.json` (80 functions + 5 operator classes):
  **78/80 functions + all operator classes now exercised.** Added `pulldata()` over the external CSV
  (`parity_collect_extensions.xml` + `ParityCollectExtensionsTest`, gated on an answered field to
  dodge the async CSV-load race; locked `pull_label=Tokyo`, `pull_id=3`).
  Also added **`intersects(geometry)`** — the SINGLE-arg SELF-intersection test (registered by the
  geo module's `IntersectsFunctionHandler`; input MUST be `geotrace`/`geoshape` — a `string` throws
  `XPathTypeMismatchException`, and passing two args also throws). Locked `fn_selfcross=1` (crossing
  trace), `fn_simple=0` (non-crossing); boolean serializes as `1`/`0`. My first attempt wrongly used
  two args + `type="string"` and mistakenly concluded intersects was disabled — it IS enabled; the
  usage was wrong.
  **Genuinely not enabled in this build**: `replace()` and the **union `|`** operator.
  Suite total: **38 @Tests**.
- **Labels — Markdown + embedded output()** (`parity_labels.xml` + `ParityLabelsTest`): verified
  on-device that question labels support (a) `<output value="/data/num"/>` embedding a **variable**
  ("Value is 10"), (b) `<output value="/data/num * 2"/>` embedding a **function/expression**
  ("Doubled is 20"), and (c) **Markdown** — bold `**kg**`→"kg", italic `_name_`, link
  `[ODK](url)`→"ODK" (markers stripped from the rendered label; not present in the submission — this
  is a rendering assertion, not a data one). Suite total: **39 @Tests**.
- **Geography functions** (`parity_geo.xml` + `ParityGeoTest`, device-locked): `area()`=1239188.516…,
  `enclosed-area()`=same (alias), `distance()`=2226.376…, `geofence(point,polygon)`=1 inside / 0
  outside. Deterministic; golden computed offline with the bundled JavaRosa then confirmed on device.
  (Also exercised inside parity_functions; this is the readable, dedicated version.) Suite total:
  **40 @Tests**.
- **Open-ended repeat popup navigation** (`parity_repeat_popup.xml` +
  `ParityRepeatTest.openEndedRepeatPopup_…`): a user repeat FOLLOWED BY another question. Asserts the
  "Add Item?" popup is shown at the end of each instance, that **yes** lands on the new instance's
  first question (`Item name`), and that **no** lands on the question AFTER the repeat
  (`After repeat`) — not the end screen. Suite total: **41 @Tests**.
- **Nested (4-level) open-ended repeat popup navigation** (`parity_repeat_nested_popup.xml` +
  `ParityNestedRepeatPopupTest`, 3 journeys). Shape `R1[Q1a,R2[Q2a,R3[Q3a,R4[Q4a],Q3b],Q2b],Q1b]`,
  Final. Journeys: (1) yes inward to each deeper question then no outward to each level's
  after-question; (2) yes at the deepest level R4 opens a new innermost instance; (3) yes at the
  outer level R1 (after declining inner) opens a new outer instance. All green. Suite total:
  **44 @Tests**.
- **Relevance INSIDE a field-list group** (`parity_fieldlist_relevant.xml` +
  `ParityFieldListRelevanceTest`, 3 journeys): a dependent question with `relevant` in an
  appearance="field-list" group appears/disappears **live on the same screen** as the controlling
  select changes (no swipe): (1) trigger=no → hidden + pruned in output; (2) trigger=yes → appears
  live + stored; (3) yes→no→yes toggles show/hide without leaving the screen. Complements the
  cross-screen relevance (ParityRelevanceTest) and whole-group relevance (parity_group_relevance).
  Suite total: **47 @Tests**.
- **Select from external datasets — GeoJSON + XML** (`parity_select_external.xml` +
  `parity_places.geojson` + `parity_fruits.xml` + `ParitySelectExternalTest`): `select_one_from_file`
  over a **GeoJSON** file (value = top-level `id`→`fs87b`, label = `title` property; the feature
  `geometry` is read via `instance('places')/root/item[id=…]/geometry` → `46.5841618 7.0801379 0 0`)
  and `select_multiple_from_file` over an **XML** file (value = `name`, label = `label`) → `apple cherry`.
  Files attached via `copyForm(…, listOf("parity_places.geojson","parity_fruits.xml"))`. Suite total:
  **48 @Tests**.
- **Geospatial capture widgets** (`parity_geo_widgets.xml` + `ParityGeoWidgetsTest`): geopoint,
  geotrace, geoshape all rendered as widgets and navigated; since GPS/map capture is
  non-deterministic, each is SEEDED with a default and the stored value round-trips (locking the
  widget's normalization: `0 0`→`0.0 0.0`, `; `→`;`) — gp=`38.25 -77.0 0.0 0.0`,
  gt=`…;38.26 -77.0 0.0 0.0`, gs=closed polygon. Actual capture UI is out of scope (non-deterministic).
  Suite total: **49 @Tests**.

Skipped / remaining known gaps (documented, not silently passed):
- `ParityLifecycleSavepointTest.answersSurviveProcessDeath` — `@Ignore`d: `RecentAppsRule` is
  unsupported on this API level. Process-death parity must run on a supported device.
- `nonDeterministicFields` navigates via `clickGoToArrow`, which is intermittently flaky in the
  harness ("click sometimes becomes a long press"); passed on the certification run.

Real defects fixed while locking (were bugs in the authored suite, not Collect): field-list group
bound to a non-existent node; `jr:count` literal (must be a node path); user/nested repeat entry
choreography (zero-instance repeat shows the add-dialog first); functions form identity-prompt +
required-trigger; and a double-`valueOf` in the nested per-household assertion.

Tooling built for offline verification (reusable): `scratchpad/ParseForm.java` (JavaRosa parse
check) and `DumpForm.java` (serialize instance) run against the bundled
`javarosa-5.2.0-31ab1af-SNAPSHOT` jar — validate fixtures and compute deterministic golden without a
device cycle.

## 7. Authoring status (offline authoring COMPLETE)

26 fixtures (`parity_*.xml`, all XML-well-formed) + `media/cities.csv`; 14 test classes + helper;
**36 `@Test` journeys**. All harness symbols and navigation return-type chains verified against the
real Page/rule classes. Nothing run on device yet.

| Test class | Journeys | Fixtures |
|---|---|---|
| ParityFunctionsTest | 2 | parity_functions (+cities.csv) |
| ParityRelevanceTest | 5 | parity_relevance_basic, parity_relevance_outside_repeat |
| ParityEnforcementTest | 2 | parity_constraint |
| ParityCascadingSelectTest | 2 | parity_cascading_select |
| ParityRepeatTest | 4 | parity_repeat_{fixed,user,nested,functions} |
| ParityStructureTest | 4 | parity_groups_nested, parity_fieldlist, parity_group_relevance |
| ParityWidgetsTextNumericTest | 2 | parity_widgets_{text,numeric} |
| ParityWidgetsRangeDateTest | 2 | parity_widgets_{range,datetime} |
| ParityWidgetsSelectTest | 3 | parity_widgets_{selectone,selectmulti,rank_trigger_note} |
| ParityWidgetsMediaGeoTest | 1 | parity_widgets_media_geo |
| ParityDefaultsTest | 2 | parity_defaults |
| ParityMetadataTest | 1 | parity_metadata |
| ParityLifecycleTest (+SavepointTest) | 3 | parity_lifecycle |
| ParityAdversarialTest | 3 | parity_edge_{math,text,repeat} |

### Consolidated `// LOCK` ledger (values to confirm/overwrite on the first Collect run)
Collect is the oracle: if Collect's output differs, the golden is corrected to Collect, never the
reverse.
- **Functions:** whole 93-value golden block + 8 locked checks (from the kotlinrosa golden map).
- **Repeats:** item_count=2; total_members=3; per-household counts 2/1; sum_vals=6; count_vals=3;
  first_val=1; pos_join="1,2,3".
- **Relevance:** summary_count=0/2; names_join="Ana, Beto"; (basic-relevance journeys are exact).
- **Cascading select:** region cleared on parent change (assertAbsentOrEmpty).
- **Defaults:** live_calc=5/14; ro_calc=6/40; txt_dyn shape `id-\d+`.
- **Widgets:** int_thousands=12345 (display≠stored); range seeds 5/2.5/7/3, rating=4;
  date/time/dateTime ISO seeds incl. `-05:00` offsets (**timezone-sensitive → most likely to move**);
  select-multiple order "a c"; rank "a b c" (document order only — drag not scriptable);
  geo "38.25 -77.0 0 0" + geo_copy.
- **Adversarial math (offline guesses, expect changes):** div_by_zero=Infinity; zero_div_zero=NaN;
  big_mult=9999999800000000 (beyond 2^53 — genuinely uncertain); date_diff=2192; sqrt_neg=NaN.

### Known reconciliation risks (resolve on device, not defects)
1. **Timezone/locale**: date/time seeds carry `-05:00`; pin device TZ+locale (UTC/en-US) or these
   LOCKs shift. This is the determinism-pinning step from §2 — must be applied before locking.
2. **NaN/Infinity/overflow serialization** (adversarial math): Collect may emit those literals, a
   number, or empty. Overwrite from real output.
3. **`username`/`phonenumber` preload** may be empty if the demo project sets none — metadata test
   asserts only `deviceid` present + shapes; seed a username on device if you want it asserted.
4. **Navigation specifics**: `parity_functions` exact swipe path to the required trigger; select
   `quick`/autoadvance stepping; count-driven repeat empty-leaf serialization — all confirmed on
   first run.
5. **Rank reorder** and **media capture bytes** are intentionally NOT asserted (not scriptable /
   non-deterministic) — presence/document-order only.

### Device-run protocol (when phone connected)
1. Pin device timezone=UTC, locale=en-US; ensure `/dev/kvm`-free real device via ADB.
2. Build + install androidTest; run `feature.parity` package only.
3. For each red assertion that is a `// LOCK`: replace expected with Collect's actual output; for
   navigation reds: adjust swipe steps. Re-run until green = **golden locked**.
4. Copy `parity_*.xml` + the test classes to KotlinCollect; rebind the driver; run; diff.
   Divergence = KotlinCollect defect.
