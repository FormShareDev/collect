# Collect → iOS Migration — Audit Playbook

> **Purpose.** A step-by-step plan for auditing whether every class in ODK
> Collect (Android) has been re-implemented in the iOS app, and whether each
> class's **functions** are implemented. This file tells the auditor *what order
> to work in and how*. It is meant to be **appended to** — record findings in
> §6 as you go.

## 0. Inputs & how the three files fit together

| File | What it holds | You use it to… |
|---|---|---|
| `codebase-map.json` | Every class + its **functions** (name, line), per module/file | Look up a class's function list to check what must exist in iOS |
| `migration-audit-tree.json` | The **dependency graph**: per class `depends_on` / `depended_by`, `level`, `cycle`, `audit_index` | Navigate up/down the graph; get the exact audit order |
| `migration-audit-tree.md` | Human-readable **checklist** (deepest-first) + cycle clusters | Tick classes off as you audit |
| **this file** | The **strategy** to run the audit and the **findings ledger** | Drive the whole audit; append results |

**The one rule that makes this work:** edges are `depends_on` (A → B means A
*uses* B). Audit **bottom-up** — a class only after everything it depends on.
The audit order in the tree is a verified topological sort, so this holds
automatically (0 ordering violations across 3,295 cross-cluster edges).

**The core question, asked for every class:**
1. **Where is this class in the iOS app?** (Swift type + file, or *NOT FOUND*.)
2. **Are its functions implemented?** (Compare against the function list in
   `codebase-map.json`: all / partial / none — list the gaps.)

---

## 1. Shape of the codebase (why the plan looks like this)

- **1,345 classes**, **4,431 internal dependency edges**, across **36 modules**, in **10 audit levels** (L0 = deepest).
- **496 leaves** (depend on nothing internal — safe to audit first) · **174 roots** (nothing depends on them — audit last).
- **18 dependency cycles.** 17 are tiny (2–9 classes). **One is huge: `C1` = 306 classes, all in `collect_app`** — the UI/controller/DI tangle. It cannot be linearised; audit it as a block.

Two populations dominate:

| Population | Classes | Character | Audit difficulty |
|---|---|---|---|
| **Library modules** (35 modules, everything except `collect_app`) | **628** | Cleanly layered (module DAG ML0→ML5), few/tiny cycles, reusable | Easy — mostly leaf-first, module by module |
| **`collect_app`** | **717** | 401 cleanly-ordered (L0–L7) + **306 in cycle C1 (L8)** + 10 on top (L9); 311 total in cycles | Hard core is the C1 cluster |

---

## 2. The practical audit shape — four phases

Work the phases in order. Phases 1–2 are mechanical (strict dependency order).
Phase 3 is the hard cluster (audit by architectural wave, not by dependency).
Phase 4 is the thin top.

> **Two valid orderings — pick one.** The tree's `audit_order` is a *strict global
> level* order that interleaves modules (L0 mixes `collect_app` and libraries).
> The phases below instead group **all 35 library modules first, then
> `collect_app`**. Both are dependency-safe — `collect_app` is the top module
> (`ML6`) and nothing internal depends on it, so finishing every library before
> touching the app never violates a dependency. The module-grouped order is
> usually better for migration because each library ≈ one iOS package you can
> confirm as a unit; use the strict level order only if you prefer a single flat
> list. Either way, **within** a module/phase go deepest-level-first.

```
Phase 1  Library foundation      628 classes   35 modules, ML0 → ML5     mechanical, leaf-first
Phase 2  collect_app foundation  401 classes   collect_app L0 → L7       mechanical, leaf-first
Phase 3  The C1 cluster          306 classes   collect_app L8            audit as a block, in 3 waves
Phase 4  Top-of-app               10 classes   collect_app L9            entry points, audit last
```

### Phase 1 — Library foundation (628 classes, module by module)

The 35 library modules form a **clean dependency DAG** (no module-level cycles).
Audit them in this module order — a module only after the modules it depends on.
Each module is a natural unit and typically maps to one iOS package/framework.
Within a module, follow the class order in `migration-audit-tree.md` (deepest
level first).

| Order | Modules (audit in this band) | Depends on | Role / what to look for in iOS |
|---|---|---|---|
| **ML0** | `analytics`, `crash-handler`, `external-app`, `forms`, `image-loader`, `permissions`, `printer`, `shared`, `strings`, `timedgrid`, `web-page` | — (nothing internal) | Pure models & utilities. `forms` = the form/instance data model (high value). `shared` = generic helpers. |
| **ML1** | `async`, `db`, `errors`, `projects`, `upgrade` | ML0 | `async` = scheduling/concurrency (→ Swift concurrency). `db` = persistence (→ SQLite/GRDB/CoreData). `projects` = ODK "projects". |
| **ML2** | `androidshared`, `settings` | ML0–ML1 | `androidshared` = shared UI/data plumbing (heavily reused). `settings` = preferences store. |
| **ML3** | `audio-clips`, `audio-recorder`, `draw`, `lists`, `location`, `material`, `metadata`, `mobile-device-management`, `qr-code`, `selfie-camera` | ML0–ML2 | Feature libraries: media capture, drawing, lists UI, geolocation, QR, selfie. |
| **ML4** | `entities`, `maps` | ML0–ML3 | `entities` = ODK Entities. `maps` = the **map abstraction** (providers plug in above). |
| **ML5** | `geo`, `google-maps`, `mapbox`, `open-rosa`, `osmdroid` | ML0–ML4 | Map provider implementations + `open-rosa` (server protocol / XForms transport). |

> Because these are libraries, many will map to **Swift packages/frameworks**. A
> whole module being "not started" is a valid, fast finding — record it once at
> the module level and move on, rather than class-by-class.

**Tiny cycles inside Phase 1** (audit each pair/group together): `C2` geo (9),
`C3` timedgrid (5), `C4` draw (5), `C5` audio-recorder (4), `C6` google-maps (4),
`C7` entities (4), `C9` selfie-camera (3), `C10` osmdroid (3), `C11` location (3),
`C12`/`C13` forms (2 each), `C14` image-loader (2), `C15` maps (2), `C16`
google-maps (2), `C18` mapbox (2). See the cluster list in
`migration-audit-tree.md`.

### Phase 2 — collect_app foundation (401 classes, L0 → L7)

The `collect_app` classes that are **not** in the C1 tangle. These are cleanly
dependency-ordered; audit deepest-level first using the checklist (Levels 0→7,
`collect_app` rows). Includes two tiny cycles: `C8` (3 classes, L1) and `C17`
(2 classes, L4) — audit each together. Expect utilities, adapters, small
view-models, data holders, and exception types here.

### Phase 3 — The C1 cluster (306 classes, L8) — audit as a block, in 3 waves

These 306 `collect_app` classes are **mutually recursive**; dependency order
cannot separate them, so do **not** try to go strictly leaf-first inside the
cluster. Instead audit by **architectural role**, from infrastructure outward.
The cluster breaks down roughly as:

```
widgets 82 · preferences 28 · formentry 21 · utilities 19 · dynamicpreload 14
activities 14 · injection(DI) 11 · application 10 · adapters 10 · configure 9
projects 8 · fragments 8 · tasks 7 · instancemanagement 7 · formmanagement 7
formlists 7 · notifications 6 · external 5 · …
```

Recommended sub-order — three waves (use each class's `depended_by` count from
`migration-audit-tree.json` to prioritise within a wave; high count = load-bearing):

- **Wave 3a — Cluster infrastructure (do first).** The load-bearing plumbing the
  rest of the cluster stands on. Highest `used-by` in C1:
  `injection.DaggerUtils` (58), `application.Collect` (41),
  `projects.ProjectsDataService` (38), `utilities.Appearances` (30),
  `utilities.FileUtils` (24), `storage.StoragePathProvider` (24),
  `utilities.FormsRepositoryProvider` (22), `utilities.InstancesRepositoryProvider` (22),
  `instancemanagement.InstancesDataService` (21), `utilities.MediaUtils` (17).
  → In iOS this is the DI container, the app/session object, storage paths, and
  the repository/data-service layer. If these are missing, most of the app is.
- **Wave 3b — Form engine & widgets.** `widgets.QuestionWidget` (base, used-by 49)
  then the **82 question widgets**, plus `formentry` (21), `dynamicpreload` (14),
  `adapters` (10). This is the form-rendering heart. Cross-check against
  `03-widget-layout-map.md` if present. Per widget: does the iOS app render this
  question type, and are its answer/validation functions implemented?
- **Wave 3c — Screens & flows.** `activities` (14), `fragments` (8),
  `preferences` (28), `configure` (9), `formlists` (7), `formmanagement` (7),
  `instancemanagement` (7), `tasks` (7), `notifications` (6), `external` (5).
  The user-facing screens and background flows built on 3a/3b.

### Phase 4 — Top-of-app (10 classes, L9)

The thin layer that sits on top of the cluster — audit last:
`ServerFormDownloader`, `FormMediaUtils`, `ChoicesRecyclerView`,
`CustomNumberPicker`, `BarcodeWidgetScannerFragment`, and several
`*ViewModel.Factory` classes (`QRCodeViewModel.Factory`,
`FormHierarchyViewModel.Factory`, `BlankFormListViewModel.Factory`,
`SelectMinimalViewModel.Factory`, `RangeSliderState.Companion`).

---

## 3. Per-class audit procedure

For each class, in audit order:

1. **Pull the contract.** Find the class by `qualified_name` in
   `codebase-map.json` → read its `functions` (names + count). That list is what
   "fully implemented" must satisfy.
2. **Locate in iOS.** Search the iOS codebase for the equivalent type. Record the
   Swift type name + file, or `NOT FOUND`.
3. **Compare functions.** Map each Android function to an iOS method. Classify:
   - `ported` — all functions present (semantically, not name-for-name).
   - `partial` — type exists, some functions missing → **list the missing ones**.
   - `missing` — no iOS equivalent.
   - `n/a` — Android-only plumbing with no iOS counterpart (DI wiring, `Companion`
     factories, Android framework glue). Justify briefly.
4. **Sanity-check dependencies.** Every class in this class's `depends_on` should
   already be audited (lower level). If a dependency is `missing`, this class
   cannot be truly `ported` — note the blockage.
5. **Record** a row in §6.

**Status legend:** ✅ ported · 🟡 partial · ❌ missing · ➖ n/a · ⏳ todo

---

## 4. Handling cycles (the `[C#]` tags)

A class tagged `[C#]` is in a dependency cycle — its dependencies are not all
"below" it. Rules:

- Audit the **whole cluster as a unit**; don't block on intra-cluster
  dependencies being "done first" (they're circular).
- The order within a cluster (in the checklist / `cycles[].classes`) is a
  heuristic, not a guarantee — treat it as a reasonable reading order.
- For the big one (`C1`), use the **Phase 3 wave order** above, not the raw list.
- A cluster is only truly done when **all** its members are resolved; mark
  partial clusters clearly.

---

## 5. Suggested batching for an agent run

If this audit is run by Claude in batches, good batch boundaries are:

- **One batch per library module** (Phase 1) — 35 batches, each self-contained.
- **collect_app L0–L7 in level-sized batches** (Phase 2).
- **C1 in three wave-batches** (3a / 3b / 3c), 3b optionally split (widgets is 82).
- **One batch for L9** (Phase 4).

After each batch: append findings to §6 and update the totals in §6.0.

---

## 6. Findings ledger  *(append here — this is the living output)*

> Record one row per class (or one summary row per module when an entire module
> is `missing`/`n/a`). Keep rows grouped by phase. Update §6.0 after each batch.

### 6.0 Running totals

| Metric | Count |
|---|---|
| Classes audited | 0 / 1345 |
| ✅ ported | 0 |
| 🟡 partial | 0 |
| ❌ missing | 0 |
| ➖ n/a | 0 |
| Modules fully audited | 0 / 36 |

### 6.1 Phase 1 — Library foundation

| Class (`qualified_name`) | Module | iOS type / file | Funcs (impl/total) | Status | Gaps / notes |
|---|---|---|---|---|---|
| _…append…_ | | | | ⏳ | |

### 6.2 Phase 2 — collect_app foundation

| Class (`qualified_name`) | iOS type / file | Funcs (impl/total) | Status | Gaps / notes |
|---|---|---|---|---|
| _…append…_ | | | ⏳ | |

### 6.3 Phase 3 — C1 cluster (3a infra / 3b form+widgets / 3c screens)

| Class (`qualified_name`) | Wave | iOS type / file | Funcs (impl/total) | Status | Gaps / notes |
|---|---|---|---|---|---|
| _…append…_ | 3a | | | ⏳ | |

### 6.4 Phase 4 — Top-of-app

| Class (`qualified_name`) | iOS type / file | Funcs (impl/total) | Status | Gaps / notes |
|---|---|---|---|---|
| _…append…_ | | | ⏳ | |

### 6.5 Blockers & cross-cutting gaps

- _Record here any missing infrastructure that blocks many classes (e.g. a
  repository or the DI container not existing in iOS), and any Android concepts
  with no clean iOS analog._
