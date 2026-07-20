# ODK Collect — `collect_app` Test Catalog

_One paragraph per test file describing, in general terms, what its test methods achieve — i.e.
the behaviour each test pins down. Intended as a behavioural specification to guide an iOS
re-implementation. Covers all `collect_app` tests: 272 unit (`src/test`) + 97 instrumented
(`src/androidTest`) = 369 files._

Built in batches. Sections marked ✅ are complete.

Legend: **(U)** = unit/Robolectric test · **(I)** = instrumented/Espresso test.

---
## Unit tests (`src/test`)

### `activities/` ✅
- **CrashHandlerActivityTest (U)** — The crash-handler screen: pressing back dismisses the crash view, and the activity finishes itself if there is no crash to show. (Behaviour of the post-crash recovery UI.)
- **FirstLaunchActivityTest (U)** — The first-launch/onboarding screen: shows the ODK logo and app name+version; the "Configure with QR code" and "Configure manually" buttons open the respective project-creation dialogs; adding the demo project shows a progress dialog. (Defines the entry point for setting up a project.)
- **FormFillingActivityTest (U)** — Process-death resilience of the main form-filling activity: after the OS kills and restores the process it returns the user to the correct question (or to the open form hierarchy), does not wrongly restore a transient dialog, and can still receive data it was waiting on from an external app; shows a fatal error if the form no longer exists. (Specifies save/restore semantics of an in-progress form.)
- **FormHierarchyFragmentHostActivityTest (U)** — The host for the form-hierarchy (question tree) screen finishes itself if the form has not finished loading. (Guards against showing the hierarchy before a form is ready.)

### `application/` ✅
- **CollectSettingsChangeHandlerTest (U)** — Reaction to settings changes: updates the PropertyManager (form metadata) when settings change; reschedules background form-update work when form-update-mode / periodic-check keys change; clears cached forms data when the server URL changes; and does nothing for unrelated single-setting changes. (Specifies which settings trigger which side effects.)
- **initialization/AnalyticsInitializerTest (U)** — Analytics opt-in logic at startup: enabled for beta builds (even if the setting is off), otherwise follows the user's analytics setting. (Privacy-relevant default behaviour.)
- **initialization/CachedFormsCleanerTest (U)** — On every app upgrade, the cached-forms cleaner runs and removes cached parsed forms across all projects. (Cache-invalidation on upgrade.)
- **initialization/ExistingSettingsMigratorTest (U)** — Migrates both unprotected (general) and protected (admin) settings for each project to current formats; tolerates a null key. (Settings schema migration.)
- **initialization/GoogleDriveProjectsDeleterTest (U)** — Removal of the deprecated Google Drive projects: only GD projects are considered; depending on the deletion outcome (last/inactive/current project) it does not attempt to convert them to the ODK protocol. (Sunsetting of the GD integration.)
- **initialization/SavepointsImporterTest (U)** — Importing legacy savepoint files for blank and saved forms: a savepoint is imported only when it is valid — newer than the form, the form exists and is not soft-deleted, file name/suffix matches (escaped as literal text to avoid regex errors); handles multiple forms and multiple versions of the same form. (Defines what makes a recovery savepoint valid.)
- **initialization/ScheduledWorkUpgradeTest (U)** — On upgrade, cancels all existing background jobs and reschedules form-update and auto-submit work for every project. (Background-work migration.)
- **initialization/upgrade/BeforeProjectsInstallDetectorTest (U)** — Detects a pre-"projects" legacy installation by checking whether legacy general/admin prefs or the legacy metadata dir are non-empty. (Decides whether the projects-era migration must run.)

### `audio/` ✅
- **AudioButtonTest (U)** — The play/stop audio button reflects playback state: shows the play icon when not playing and the playing icon when playing. (Audio-prompt button visual state.)
- **AudioControllerViewTest (U)** — The audio player widget (used for audio prompts/clips): shows duration and current position as mm:ss; the seek bar tracks position; dragging the seek bar skips to a position (pausing while dragging if playing, then resuming); notifies the swipeable parent so form swipes don't fire mid-scrub; the remove button is safe with no listener. (Defines audio playback UX.)
- **AudioRecordingControllerFragmentTest (U)** — The in-form audio recorder controls: live timecode + waveform updates; pause/resume toggling with matching icon and status changes; the pause button is hidden below API 24; background-recording forms hide the manual controls and expose a help dialog; error states show an error dialog; and "recording disabled" messaging appears only for background-recording forms. (Specifies the audio-recording control surface, incl. background/auto recording.)
- **AudioRecordingFormErrorDialogFragmentTest (U)** — The recording-error dialog: OK dismisses it and consumes the one-shot error event. 
- **BackgroundAudioHelpDialogFragmentTest (U)** — The background-audio help dialog has an OK button.

### `backgroundwork/` ✅
- **AutoUpdateTaskSpecTest (U)** — The auto form-update background task calls `checkForUpdates` for the project carried in the work tag and does not cap retries. (Background form-update job contract.)
- **FormUpdateAndInstanceSubmitSchedulerTest (U)** — Scheduling/cancelling of background work: schedules update checks (previously-downloaded-only vs match-exactly) per project, schedules auto-send using the configured network type (wifi/cellular), does nothing if auto-send is off, and cancels update/submit work per project. (Defines when/how background sync is scheduled.)
- **SendFormsTaskSpecTest (U)** — The send-forms background task returns success/failure according to whether instance submission succeeded. 
- **SyncFormsTaskSpecTest (U)** — The form-sync task: calls `synchronize` with notify=true only on the last unique execution, forwards the stop signal, returns the updater's result, caps retries, and requires a project id. (Background form-list sync contract.)

### `configure/qr/` ✅
- **QRCodeActivityResultDelegateTest (U)** — Importing settings from a QR code image picked from the gallery: success shows a success toast; failures (invalid settings, Google-Drive-protocol settings, undecodable image, no QR found, null/cancelled result) each show the appropriate toast or do nothing. (Defines QR settings-import outcomes.)
- **QRCodeMenuProviderTest (U)** — The QR menu: "import QR code" launches an external image picker (toast if none available); "share" does nothing until the QR is generated, then launches a share intent. 
- **QRCodeScannerFragmentTest (U)** — The live QR scanner: valid settings stop scanning and navigate to the main menu; invalid settings or Google-Drive-project settings show an error and keep scanning. 
- **QRCodeViewModelTest (U)** — The QR generator view model: builds a QR from the selected setting keys; surfaces a security warning that depends on whether the server password and/or admin password are included. (Controls what sensitive data a shared QR contains.)

### `database/` ✅
- **DatabaseFormsRepositoryTest (U)** — CRUD/query contract for the forms repository backed by SQLite (inherits the shared `FormsRepository` test suite): add/update/delete/soft-delete, lookups by id/md5/version, etc. (Storage contract for blank forms.)
- **DatabaseInstancesRepositoryTest (U)** — CRUD/query contract for the instances (saved/filled forms) repository against SQLite (inherits the shared `InstancesRepository` suite): status transitions, lookups, deletion. (Storage contract for submissions.)
- **DatabaseSavepointsRepositoryTest (U)** — Storage contract for savepoints (in-progress auto-saved form state) against SQLite. 
- **database/entities/EntitiesDatabaseMigratorTest (U)** — Schema upgrades of the Entities database across versions 1→2→3 preserve/transform data correctly. 
- **FormDatabaseMigratorTest (U)** — Forms-database schema migrations from each historical version (7–13) upgrade correctly and never reuse database ids. (Backward-compatible storage upgrades — important to replicate for data import on iOS.)
- **InstanceDatabaseMigratorTest (U)** — Instances-database migrations (v6–9), including deriving the finalization date from the last status-change date for finalized instances while leaving it empty for non-finalized ones. 

### `dependencies/` ✅
- **BikramSambatTest (U)** — Correctness of Gregorian↔Bikram Sambat (Nepali) calendar conversion. (Backs the Nepali date picker.)
- **PersianCalendarTest (U)** — Correctness of Gregorian↔Persian (Jalali) calendar conversion. (Backs the Persian date picker.)

### `dynamicpreload/` ✅  _(the `pulldata()`/external-CSV "dynamic preload" feature)_
- **DynamicPreloadExtraTest (U)** — The DynamicPreloadExtra (per-form marker that the form uses dynamic preload) can be externalized/serialized. 
- **DynamicPreloadParseProcessorTest (U)** — At form-parse time, detects whether dynamic preload is needed: present when an XPath uses `pulldata` or a question appearance contains `search`, absent otherwise. (Decides whether CSV-backed lookups must be wired up.)
- **ExternalDataReaderTest (U)** — Importing an external CSV into a SQLite side-database: creates data + metadata tables; never modifies the original CSV; stores an md5 in metadata; re-imports when the db file/metadata is missing or the CSV changed, and skips import when unchanged. (Defines CSV→db caching for `pulldata`/`search`.)
- **ExternalDataUseCasesTest (U)** — Orchestration: creates the db file only when the form actually uses dynamic preload (FormDef has a DynamicPreloadExtra), otherwise does nothing; leaves the original CSV untouched. 
- **ExternalDataUtilTest (U)** — Sanitizes CSV column names into safe SQL column names. 

### `entities/` ✅  _(Entities / offline datasets feature)_
- **DatabaseEntitiesRepositoryTest (U)** — The SQLite-backed entities repository (runs the shared `EntitiesRepository` suite) plus support for entity properties whose names collide with db column names. 
- **EntitiesRepositoryTest (U)** — The full behavioural contract of an entities repository (abstract suite): listing lists; creating/updating entities by id and by id+version; online/offline state rules; adding and updating properties (incl. names with dots/dashes); not clearing the label with a null; insert-order indexing that stays contiguous from 0 after deletes; counts and lookups by index; multi-entity saves. (This is the core spec of the Entities datastore — re-implement carefully.)
- **InMemEntitiesRepositoryTest (U)** — The in-memory implementation must satisfy the same `EntitiesRepository` contract (used as a reference/oracle). 

### `external/` ✅  _(content providers + external form-launch URIs — Android integration surface)_
- **FormsProviderTest (U)** — The `forms` ContentProvider read API: insert/update are disabled (return null/0); delete removes the form row and its files (with selection support); query returns the expected columns, honours projection/selection/sort, and defaults to the first project when no project id is given; getType returns the form MIME types. (External read access to blank forms.)
- **FormUriActivityTest (U)** — The deep-link entry point for opening a form by URI from another app: extensive validation — missing/mismatched project, null/invalid URI, non-existent blank/saved form or missing files, encrypted forms, multiple/zero form definitions, editing-disabled → view-only, finalized/submitted/failed → view-only — each shows the right alert or starts the form; idempotent across activity recreation; shows a savepoint-recovery dialog (incl. for older form versions). (Critical, security-sensitive integration contract.)
- **InstanceProviderTest (U)** — The `instances` ContentProvider: insert adds an instance (rejects a submission URI, returns the new URI); update disabled; delete removes the instance/dir, but submitted instances are soft-deleted (files removed, geometry cleared); query column/projection/selection/sort behaviour; default project; getType MIME types. (External read access to submissions.)

### `formhierarchy/` ✅  _(the question-tree / "go to" hierarchy screen)_
- **HierarchyListItemViewTest (U)** — Rendering of hierarchy rows: question rows show primary+secondary text (secondary hidden when unset/blank, HTML styled); group/repeatable-group/repeat-instance rows show the right label, text and icon. 
- **QuestionAnswerProcessorTest (U)** — How answers are summarized in the hierarchy: printer widgets show no answer; text types with the "masked" appearance show nothing when empty and masked dots when set; non-text / non-input control types show the real answer. (Privacy-masking rules in the overview.)

### `formlists/` ✅  _(blank-form and saved-form list screens)_
- **blankformlist/BlankFormListItemTest (U)** — Mapping a stored Form to a list item: correct fields, empty strings for unset version/geometry xpath, and `dateOfLastUsage` derived from saved instances matching both formId and version (0 when none). 
- **blankformlist/BlankFormListItemViewTest (U)** — The row shows form id and version, hiding version when blank. 
- **blankformlist/BlankFormListMenuProviderTest (U)** — The blank-form-list menu: sync vs error-sync icon by sync state; refresh enabled only when not loading; offline refresh shows an error and skips sync; sort opens the sort dialog; search wires filter text and hides refresh/sort appropriately (depending on match-exactly), clearing filter text on menu create. 
- **blankformlist/BlankFormListViewModelTest (U)** — The list's data logic: loads forms, reflects match-exactly and out-of-sync/auth-required state, ignores deleted forms, optionally hides old versions, and sorts by name/date/last-saved (with correct handling of versions and forms without saved instances), plus text filtering combined with sorting. (Defines the blank-form list behaviour.)
- **DeleteBlankFormFragmentTest (U)** — The delete-blank-forms screen: delete-selected confirms then deletes (cancel does nothing), selection toggling, and provides the blank-form menu. 
- **savedformlist/DeleteSavedFormFragmentTest (U)** — Delete-saved-forms: confirm/cancel, selection toggling, progress while deleting, and edit-aware display (only latest edit when all unsent; latest + submitted edits otherwise). 
- **savedformlist/SavedFormListListMenuProviderTest (U)** — Saved-form-list menu: search wires filter text and toggles the sort affordance. 
- **savedformlist/SavedFormListViewModelTest (U)** — Saved-form list logic: excludes deleted forms; case-insensitive filtering; sort by name/date asc/desc with order retained across view models; deleting returns the count removed (0 on failure); filtering/sorting take edit numbers into account. 

### `fragments/dialogs/` ✅
- **FormsDownloadResultDialogTest (U)** — The form-download result dialog: dismissible by back/outside/OK; OK and (on error) the negative button invoke the close callback and "show details" appears only when there were errors; correct messages; survives recreation. 
- **SelectMinimalDialogTest (U)** — The compact select dialog closes on back/up and shows a search bar when the autocomplete appearance is used. 
- **SelectMultiMinimalDialogTest (U)** — The minimal multi-select dialog saves the answer on close only if it actually changed. 
- **SelectOneMinimalDialogTest (U)** — The minimal single-select dialog saves the answer on close only if it changed. 

### `formmanagement/` ✅  _(downloading, updating, matching & deleting blank forms; server sync)_
- **download/FormDownloadExceptionMapperTest (U)** — Maps form-download failures (no hash, parse error, save error, invalid submission) to user-facing messages. 
- **DownloadMediaFilesServerFormUseCasesTest (U)** — Media-file download rules: doesn't redownload an unchanged/older existing copy or an already-downloaded entity list; carries forward the newest matching "last-saved" file from a previous form version. 
- **download/ServerFormDownloaderTest (U)** — The core blank-form downloader: downloads and saves a form + its media; treats updates as new versions (or replaces same id+version); validates the manifest hash; skips unchanged media but fetches changed-hash media; reports progress; un-deletes soft-deleted forms; is cancellable (throws and saves nothing); errors on fetch/disk problems without partial saves; and correctly marks forms as "uses entities" based on entity attachments/blocks. (The download spec — central to server interop.)
- **DownloadUpdatesServerFormUseCasesTest (U)** — When an update batch is cancelled, returns the downloads that already completed. 
- **drafts/DraftsMenuProviderTest (U)** — "Finalize all" appears only once the draft count has loaded and is non-zero. 
- **FetchFormDetailsServerFormUseCasesTest (U)** — Classifying each server form against local state: new / updated-version / updated-hash / updated-media / already-on-device, with the many edge cases (soft-deleted locally, null manifest hash, no manifest, media-only changes). (Drives the update-available logic.)
- **FilterFormsToAddTest (U)** — Gracefully handles empty/null server form lists. 
- **FormFillingIntentFactoryTest (U)** — Builds the intents that launch FormUriActivity for filling a new instance vs editing an existing one. 
- **formmap/FormMapViewModelTest (U)** — The "forms on a map" screen: counts and maps instances with geometry, assigning each instance the correct map icon, tap action, status and info text by state (valid/invalid draft, new edit, finalized, deleted, submitted, failed, view-only after completion); loading state; edited finalized instances show an edit number. 
- **FormsDataServiceTest (U)** — The service coordinating form sync: downloads updates only when auto-download is on and the change-lock is free; match-with-server respects the lock, updates project sync/error state, and notifies (or not) per the notify/isStopped flags; error state isn't wrongly cleared; counts ignore soft-deleted forms. (Concurrency + state model for form sync.)
- **FormSourceExceptionMapperTest (U)** — Maps server/transport errors (fetch, unreachable host, security, server error, parse, not-OpenRosa) to messages. 
- **LocalFormUseCasesTest (U)** — Cleanup of local forms when instances are deleted: deletes the form when its (or another version's) instances are gone; soft- vs hard-delete rules around null versions and non-unique id+version combinations. 
- **metadata/FormMetadataParserTest (U)** — Parsing a form's metadata from its XML: title/id/version/submission/base64key and the geometry XPath used for mapping — handling attributes/namespaces/comments, optional fields → null, empty version → null, and the rules for picking the first usable geopoint (top-level vs in groups vs excluding repeats, and set-geopoint actions). (Form parsing spec.)
- **ServerFormsSynchronizerTest (U)** — Full sync: downloads new and updated forms, deletes local forms no longer on the server, skips unchanged forms, and on errors keeps going with the other forms while surfacing the error. 
- **ShouldAddFormFileTest (U)** — Which files in the forms dir are treated as forms: rejects ignored files and non-form types, accepts `.xml`/`.xhtml`. 

### `instancemanagement/` ✅  _(saved-instance lifecycle, sending, auto-send)_
- **autosend/AutoSendSettingsProviderTest (U)** — Whether auto-send should run given the setting (off / wifi_only / cellular_only / wifi_and_cellular) and the current network type/availability — the full truth table. (Defines auto-submit network policy.)
- **autosend/FormExtTest (U)** — Per-form auto-send override combined with the global setting decides `shouldFormBeSentAutomatically`; parsing the form's `auto-send` attribute into NEUTRAL/FORCED/OPT_OUT, tolerating bad casing/whitespace. 
- **autosend/InstanceAutoSendFetcherTest (U)** — Selects which finalized instances to auto-send based on the form-level auto-send flag. 
- **InstanceDeleterTest (U)** — Deleting an instance: submitted ones are soft-deleted; the parent blank form is deleted only when it is itself soft-deleted and has no other (non-deleted) instances of that version. (Cascade-delete rules.)
- **InstanceExtKtTest (U)** — Status description text and `isDeletable` logic (depends on the "can delete before send" setting and the instance's finalized/sent status). 
- **InstanceListItemViewTest (U)** — The saved-form row shows an error chip for invalid/incomplete statuses and none for valid/new-edit/complete. 
- **InstancesDataServiceTest (U)** — The instances service: respects the db lock for deletes; `sendInstances` returns/notifies correctly for none/failed sends; reset keeps undeletable instances but handles forms with edits; update refreshes instances and counts. 
- **LocalInstancesUseCasesTest (U)** — Creating an instance file (directory named from sanitized form name + timestamp, in the instances dir) and editing an instance (copies dir + db row, returns the newest edit if editing an outdated one). 
- **send/ReadyToSendBannerTest (U)** — The main-menu "ready to send" banner: shown only when there are both sent and ready-to-send instances; reacts to data changes; shows "last sent N seconds/minutes/hours/days ago" and the ready-to-send count. 
- **send/ReadyToSendViewModelTest (U)** — Backs that banner: counts submitted vs ready-to-send (complete/failed) instances and computes the last-sent time (0 if none). 

### `geo/` ✅
- **MapFragmentFactoryImplTest (U)** — Picks the map engine from settings: OSMDroid for any OSM basemap option, Google Maps when selected (and as the fallback for unsupported stored values). 
- **geo/javarosa/IntersectsFunctionHandlerTest (U)** — The custom XPath `intersects()` form function: returns false for empty input, true when two geo traces intersect, and throws for non-geo strings or the wrong argument count. (A geo-aware form-calculation function — part of the form expression language.)

### `javarosawrapper/` ✅
- **FormControllerTest (U)** — The wrapper around the JavaRosa form engine: answer validation can optionally move the form index to the first invalid question; "jump to new repeat" navigates to the correct (possibly nested) repeat prompt; exposes an audit-event logger and the submission metadata (top-level meta when several exist); and reports whether a question is in a field-list group. (Thin but central adapter to the form engine — the engine itself is reused JavaRosa.)

### `location/client/` ✅
- **MaxAccuracyWithinTimeoutLocationClientWrapperTest (U)** — A location client that collects fixes until a timeout, keeping only increasingly-accurate fixes: emits updates only for more-accurate fixes, ignores fixes without accuracy after one with accuracy, resets highest-accuracy/timeout on re-request, and stops after the timeout; no updates without permission. (GPS acquisition policy for geo widgets.)

### `mainmenu/` ✅
- **CurrentProjectViewModelTest (U)** — Tracks the current project: initial value set, `setCurrentProject` updates it, `hasCurrentProject` false when none. 
- **MainMenuActivityTest (U)** — The main menu: title/icon show the current project; each button (Fill Blank, Edit Saved, Send Finalized, View Sent, Get Blank, Delete Saved) has the right text and opens the right screen; buttons appear/hide per settings. (The app's home screen contract.)
- **MainMenuButtonTest (U)** — The reusable menu button: icon/name attributes, and `setNumberOfForms` showing/bolding the count (per "highlightable" attr) and blanking it when <1. 
- **MainMenuViewModelTest (U)** — Version-string formatting (release/beta/dirty/tag variants and commit description) and the per-saved-form message+action shown after filling, across the matrix of draft/finalized × editing-enabled × encryption × auto-send. 
- **MinSdkDeprecationBannerTest (U)** — A "your Android is too old" banner shown only below API 26, dismissible, with "Learn More" opening the forum thread. 
- **PermissionsDialogFragmentTest (U)** — The permissions request dialog: OK asks for permissions and fires the callback; not dismissible by back/outside; doesn't re-request on recreation. 
- **RequestPermissionsViewModelTest (U)** — Decides whether to prompt for permissions (e.g. notifications on newer APIs) and records that they were requested. 

### `notifications/` ✅
- **NotificationManagerNotifierTest (U)** — System notifications: clears the sync notification on success, notifies on sync-stopped, and notifies about available form updates only once per new hash/manifest-hash (not repeatedly for already-seen updates). 

### `projects/` ✅  _(multi-project: create, switch, migrate, reset, delete)_
- **ExistingProjectMigratorTest (U)** — One-time migration of a pre-projects install into a "project": creates it from the server URL, moves files from the root (but not the cache dir), copies settings, tolerates missing dirs, and skips if projects already existed. 
- **ManualProjectCreatorDialogTest (U)** — Manual "add project by URL": password field masked; Add disabled while URL blank; toast if URL lacks a protocol; creating triggers server-project creation and goes to the main menu; dismissible. 
- **ProjectCreatorImplTest (U)** — Creating a project from imported settings: returns SUCCESS / INVALID_SETTINGS / GD_PROJECT appropriately, deletes the half-created project and clears prefs on failure, and switches (or not) to the new project per flag. 
- **ProjectDeleterTest (U)** — Deleting a project: removes it from the repository, cancels its background work, clears its settings and directory, and handles last/current/other-project cases for choosing the next current project. 
- **ProjectIconViewTest (U)** — Renders the project icon (letter) over its colour. 
- **ProjectListItemViewTest (U)** — The project row shows name, icon+colour, username and URL (each shown only when set; passes through unparseable URLs). 
- **ProjectResetterTest (U)** — The "reset application" feature scoped to the current project only: independently clears settings/forms/instances+savepoints/layers/cache for the current project while never touching other projects; respects the instances db lock; reloads the property manager. 
- **ProjectsDataServiceTest (U)** — Current-project access: `requireCurrentProject` returns it or throws when none/invalid (falls back to first when none set), persists to meta settings, and re-initializes analytics on switch. 
- **ProjectSettingsDialogTest (U)** — The project menu/sheet: opens project settings / about / add-project; notifies on switch; no duplicate list entries on update; dismissible. 
- **QrCodeProjectCreatorDialogTest (U)** — QR-based "add project": requires camera permission (re-requested on return), import-from-file option, switch to manual mode, success → main menu, and handling of duplicate/invalid/Google-Drive QR codes (toast + keep scanning). 
- **SettingsConnectionMatcherImplTest (U)** — Detects whether imported settings match an existing project by URL (+username), returning the matching project uuid or null — used to avoid duplicate projects. 

### `savepoints/` ✅
- **SavepointUseCasesTest (U)** — Retrieving the right savepoint for a form: matches savepoints to the correct form version (old vs new) and to blank vs saved forms; returns null when the savepoint file is missing or the instance file was modified after it; picks the savepoint belonging to the specific saved form. (Auto-recovery correctness.)

### `storage/` ✅
- **StoragePathProviderTest (U)** — The directory layout: returns (and creates) the storage root, per-project root (sanitized name), and the forms/instances/metadata/cache/layers/settings + shared-layers directories for the current project. (Defines the on-disk file structure to replicate.)

### `tasks/` ✅
- **SaveFormIndexTaskTest (U)** — Persists and restores the current form position (index). 

### `version/` ✅
- **VersionInformationTest (U)** — Parsing the build's version description into a semantic version and detecting release vs non-release. 

### `preferences/` ✅  _(general/admin settings, the "protected settings" visibility model, QR/JSON config)_
- **AppConfigurationGeneratorTest (U)** — Builds the settings JSON used for QR/file sharing: includes admin/user passwords only when explicitly opted in; includes only non-default saved settings; can embed server details. (Defines the shareable config format.)
- **dialogs/AdminPasswordDialogFragmentTest (U)** — The "enter admin password" gate: correct password unlocks (sets Unlocked in the view model), incorrect does nothing; OK/Cancel/back dismiss; show-password toggle; state retained on rotation. 
- **dialogs/ChangeAdminPasswordDialogTest (U)** — Setting/removing the admin password persists (or not, on cancel) and sets Unlocked/NotProtected state; show-password; survives rotation. 
- **dialogs/DeleteProjectDialogTest (U)** — Delete-project confirmation: loading spinner disables UI; the Delete button enables only after typing "Delete"; calls ProjectDeleter; message shows counts of blank/sent/unsent+draft forms. 
- **dialogs/ResetProgressDialogTest (U)** — The reset-in-progress dialog is non-cancellable and shows the right content. 
- **dialogs/ServerAuthDialogFragmentTest (U)** — The server credentials dialog prefills username/password and saves them to general prefs on OK. 
- **ProjectPreferencesViewModelTest (U)** — The Locked/Unlocked/NotProtected admin state machine: correct initial state from whether an admin password is set, plus setters/getters. (Underpins all the protected-settings visibility tests below.)
- **screens/FormEntryAccessPreferencesFragmentTest (U)** — "Save as draft" and "Finalize" options are mutually dependent — you can't disable both; disabling one disables the other. (Prevents a no-exit form-entry config.)
- **screens/FormManagementPreferencesFragmentTest (U)** — Form-update settings interactions: each update mode (Manual / Previously-Downloaded-Only / Match-Exactly) enables/disables related prefs; Automatic-Download checked-state and reset behaviour; visibility across admin Locked/Unlocked/NotProtected modes; the whole category hides when empty. 
- **screens/FormMetadataPreferencesFragmentTest (U)** — Metadata preference summaries shown only when the metadata values are set. 
- **screens/IdentityPreferencesFragmentTest (U)** — Form-metadata and anonymous-usage options' visibility across the three admin modes, plus the usage-data checkbox reflecting settings. 
- **screens/MainMenuAccessPreferencesTest (U)** — Toggles controlling which main-menu buttons are enabled: checked-state from settings, click toggling, persistence across recreation, and disabling Edit-Saved when editing is off. 
- **screens/MapsPreferencesFragmentTest (U)** — Saved reference-layer preference shows the layer name, or "none" if it no longer exists. 
- **screens/ProjectDisplayPreferencesFragmentTest (U)** — Project Name/Icon/Colour preferences are present with correct titles/summaries. 
- **screens/ProjectPreferencesFragmentTest (U)** — The top settings screen rebuilds when protected settings change, and Server / Project-display / UI categories appear or hide per admin mode. 
- **screens/UserInterfacePreferencesFragmentTest (U)** — UI preferences' visibility across the three admin modes. 
- **ServerPreferencesAdderTest (U)** — Dynamically adding server preferences returns true, or false + a toast if a preference has an incorrect type. 
- **source/SettingsStoreTest (U)** — The settings store reads/writes all supported types (string/boolean/long/int/float/string-set). 
- **source/SharedPreferencesSettingsProviderTest (U)** — Per-project settings isolation: meta settings are global; unprotected/protected settings are stable per project and differ across project ids. 
- **source/SharedPreferencesSettingsTest (U)** — Default-value semantics: returns custom default, then built-in default, then saved value, for every type. 

### `views/` ✅
- **ChoicesRecyclerViewTest (U)** — The select-question choice list: grid vs flexbox layout by appearance; dividers only for single-column non-flex; correct filtered values across select-one/multi × buttons/no-buttons modes; click selection semantics (single-select replaces, multi-select accumulates). 
- **helpers/PromptAutoplayerTest (U)** — Auto-playing a question's audio when the prompt requests it (case-insensitive), including ordered playback of select-choice audio, fallbacks when there's no URI or choice audio, and not auto-playing for video/none. 
- **TrackingTouchSliderTest (U)** — A slider that suppresses the form's fling/swipe gesture while being dragged. 

### `utilities/` ✅  _(form/appearance/file/CSV/date/image helpers — reusable logic worth porting)_
- **AdminPasswordProviderTest (U)** — `isAdminPasswordSet`/`getAdminPassword` treat empty and null as "not set", returning the real value only when properly set. 
- **AppearancesTest (U)** — Parsing XForms `appearance` hints: sanitizing to lowercase (ignoring `search()`), checking for a given appearance, and computing the number of columns (1 default/invalid; explicit number; or screen-size-based 2/3/4 for the `columns` appearance). (Drives widget layout — port carefully.)
- **ArrayUtilsTest (U)** — boxed↔primitive long-array conversion, null→empty, NPE on null elements. 
- **ChangeLockProviderTest (U)** — Per-project form and instance locks: stable per project, distinct across projects, and form vs instance locks differ. (The concurrency primitive guarding form/instance mutation.)
- **CSVUtilsTest (U)** — CSV cell escaping: passes through plain text/null; quotes-escaped-and-wrapped; commas/newlines wrapped in quotes. 
- **ExternalAppIntentProviderTest (U)** — Builds the intent for the "external app" widget/launch-intent: parsing the package/action (with bracket syntax), adding params as extras, treating a `uri` param as intent data, and tolerating parentheses/mixed appearances. (External-app integration spec.)
- **ExternalAppUtilsTest (U)** — Extracting the intent name and parameters from the spec, and coercing returned values to string/integer/decimal answer data. 
- **FileUtilsTest (U)** — Media dir naming, simplifying scoped-storage paths, and safe directory listing (empty array for null/missing). 
- **FormNameUtilsTest (U)** — Normalizing a form name and building a safe filename from it. 
- **FormsDownloadResultInterpreterTest (U)** — Interpreting a batch form-download result: list/count of failures and "all succeeded" flag. 
- **FormsRepositoryProviderTest (U)** — The forms repository is created against the passed project's directory. 
- **FormsUploadResultInterpreterTest (U)** — Same as download interpreter but for uploads (failures list/count, all-succeeded). 
- **HtmlUtilsTest (U)** — Text/Markdown→HTML: null→empty, trimming, ignoring invalid styles, escaping backslash/`<`, and supporting embedded HTML. (Question-label rendering.)
- **ImageCompressionControllerTest (U)** — Image-attachment downscaling rules: the form-level `max-pixels` takes precedence over the app's image-size setting (very_small/small/medium/large → 640/1024/2048/3072 px), with "original" meaning no compression unless `max-pixels` is set; invalid `max-pixels` falls back to the setting. (Photo capture sizing.)
- **InstanceAutoDeleteCheckerTest (U)** — Whether a submitted instance is auto-deleted: form-level `auto-delete` overrides the project setting (case-insensitive; unsupported values ignored); only matching form versions are affected. 
- **InstancesRepositoryProviderTest (U)** — Instances repository created against the passed project's directory. 
- **InstanceUploaderUtilsTest (U)** — Building the upload-result message, including edit numbers. 
- **MediaUtilsTest (U)** — Opening a media file: toast when the file is missing or the URI is null; otherwise launches via the intent launcher with the right params. 
- **MyanmarDateUtilsTest (U)** — Gregorian↔Myanmar calendar conversion (backs the Myanmar date picker). 
- **QuestionFontSizeUtilsTest (U)** — Returns the default or the user-selected question font size. 
- **WebCredentialsUtilsTest (U)** — Saving new server credentials persists them and reloads the property manager. 

### `formentry/` ✅  _(the form-filling engine wrapper, saving, audit logging, background audio/location)_
- **AppStateFormSessionRepositoryTest (U)** / **InMemoryFormSessionRepositoryTest (U)** / **FormSessionRepositoryTest (U)** — The form-session store that holds the live form-controller for an open form: creating returns a new unique id, get-before-set is empty, set/get/clear work per id. (Lets the UI survive config changes mid-form.)
- **AudioVideoImageTextLabelTest (U)** — The composite question/choice label (text + optional audio/video/image): hides text when null/blank; shows text+audio button; highlights text while its audio plays; stop button stops audio; clicking either the label or its image selects the option (single vs multi semantics); shows a message when the image file is missing. 
- **AudioVideoImageTextLabelVisibilityTest (U)** — The media label becomes visible only when a media URI is present. 
- **audit/AsyncTaskAuditEventWriterTest (U)** — Writing the audit log CSV: records location, tracking-changes, user and change-reason rows; escapes commas/quotes; updates the header when the app was updated between instances. (The audit-trail feature — a compliance spec.)
- **audit/AuditConfigTest (U)** — Parsing the form's audit config: parameters, logging location only when all location params are set, and event priorities. 
- **audit/AuditEventCSVLineTest (U)** — Serializing an audit event to a CSV line: comma/newline/quote escaping and the many column combinations (location, tracking-changes, null values) plus event-type strings. 
- **audit/AuditEventLoggerTest (U)** — The audit logger: no-ops when unconfigured; uses the most accurate location and expires locations older than 60s; de-duplicates identical consecutive events; attaches user and change-reason; correct event types. 
- **audit/AuditEventTest (U)** — Mapping JavaRosa form-engine event types to audit event types. 
- **audit/FormSaveViewModelTest (U)** — Saving a form: returns a Saving state, prevents concurrent saves, requires a change-reason when configured (incl. while background-audio is recording on exit), and on completion sets Saved/SaveError and logs the right sequence of save/exit/finalize audit events after flushing. (Central save state machine.)
- **audit/IdentityPromptViewModelTest (U)** — The "who are you" identity prompt sets the user on the audit logger when done. 
- **BackgroundAudioPermissionDialogFragmentTest (U)** — The background-audio permission dialog is non-cancellable; OK grants permission (and finishes the activity if granting throws). 
- **BackgroundAudioViewModelTest (U)** — Background (covert) audio recording driven by a form action: picks codec by quality (voice-only→AMR, low→AAC, missing→AMR); starts on permission grant (using the first action's quality); logs enable/disable to the audit log; cleans up listeners; errors if granting without a prior check. 
- **backgroundlocation/BackgroundLocationManagerTest (U)** — Background location for location-auditing forms: requests permission when the form needs it and preconditions are met; logs permission grant/deny once; returns/logs warnings when Play Services unavailable, the preference is off, no provider is available, or all preconditions met (location-tracking warning). 
- **FormEndViewModelTest (U)** — The end-of-form screen logic: exposes whether save-as-draft and finalize are enabled, and whether the form should auto-send. 
- **FormEndViewTest (U)** — The end-of-form screen UI: title; Save-as-draft/Finalize buttons shown per settings and firing `onSaveClicked(false/true)`; Finalize vs Send button depending on auto-send; correct info text for edit-after-finalize forms. 
- **FormEntryMenuProviderTest (U)** — The in-form menu: shows Add-Repeat only inside a repeat; Record-Audio only for background-recording forms (checked when recording); Save only when mid-form saving is enabled; Change-Language only when the form has multiple languages. 
- **FormEntryUseCasesTest (U)** — Core use-cases: loading the right form version for an instance (null if form/file missing); loading a draft (null if the instance file is gone); finalizing a draft marks validation errors, can create partial submissions, and updates the instance name. (Key behaviours to reproduce.)
- **FormEntryViewModelTest (U)** — Navigation/repeat view model: steps forward from beginning-of-form (sets error on failure); add-repeat steps to the next screen (errors handled with/without cause); cancel-repeat-prompt steps forward rather than jumping back. 
- **FormLoadingDialogFragmentTest (U)** / **SaveFormProgressDialogFragmentTest (U)** / **RefreshFormListDialogFragmentTest (U)** — Progress dialogs are non-cancellable; the refresh dialog's Cancel calls the cancel-loading callback. 
- **PrinterWidgetViewModelTest (U)** — HTML-to-print processing for the printer widget: doesn't crash on empty/broken HTML; wraps partial HTML; keeps CSS; rewrites image `src` to absolute paths (leaving missing images alone); converts `<qrcode>` tags to images. (Backs label-printing.)
- **QuitFormDialogTest (U)** — The "quit form" dialog: cancellable; Discard exits; Keep-Editing dismisses; messaging/buttons differ by whether save-as-draft is enabled and whether the form can be fully discarded (shows last-saved time when not), warning title when drafts are disabled. 
- **RecordingHandlerTest (U)** — Saving background recordings into the form: for M4A/AMR, saves a new answer, or appends to the already-saved reference recording (re-saving if the referenced file vanished), deleting the redundant new file. 
- **repeats/DeleteRepeatDialogFragmentTest (U)** — The delete-repeat dialog: non-cancellable, correct message, Cancel dismisses, Remove dismisses + sets a result + calls deleteRepeat. 

### `widgets/` ✅  _(one test per question-type UI control — each widget = a question type to rebuild on iOS)_

_Common behaviours verified across nearly all widgets: `getAnswer` returns the stored answer (null when empty), `clearAnswer` clears it, read-only / read-only-override disables all interactive elements, and capture/replace buttons vs answer display toggle based on whether an answer exists._

**Top-level widgets:**
- **AnnotateWidgetTest (U)** — Image + draw-on-top ("annotate") widget: capture/choose buttons launch the right camera/gallery intents (incl. a custom package), blocked when permissions denied, disabled when read-only.
- **ArbitraryFileWidgetTest (U)** — Generic file attachment: shows the answer file name, button opens the system file picker, tapping the answer opens a viewer, clear hides it.
- **AudioWidgetTest (U)** — Audio capture/choose: shows capture+choose buttons with no answer, an audio player when answered; read-only hides capture; choose-sound hidden for new widget.
- **BarcodeWidgetTest (U)** — Barcode/QR scan: scan button hidden read-only, "Replace Barcode" + answer shown when present, clear updates button title.
- **BearingWidgetTest (U)** — Compass bearing capture: get/replace bearing buttons and answer text by state; button hidden read-only.
- **CounterWidgetTest (U)** — Increment/decrement counter: minus disabled at 0/no-answer, plus disabled at 999,999,999, both enabled in between; read-only disables both.
- **DecimalWidgetTest (U)** — Decimal number input: formatting of integer/decimal/negative values and up to 15 digits without losing precision.
- **DrawWidgetTest (U)** — Free-draw/sketch: shows the drawn image (or hides view + error when it can't load), default answer shown; read-only disables.
- **ExArbitraryFileWidgetTest / ExAudioWidgetTest / ExDecimalWidgetTest / ExImageWidgetTest / ExIntegerWidgetTest / ExStringWidgetTest / ExVideoWidgetTest (U)** — The "external app" (`ex:`) variants of the file/audio/number/image/string/video widgets: a launch button (with custom label/font) invokes an external app to produce the value; answer display appears once returned; numeric variants truncate to their digit limit and add thousands separators; string variant supports masked/hidden-answer appearances.
- **GeoPointMapWidgetTest / GeoPointWidgetTest (U)** — Geopoint capture (map-based and plain): answer text formatting, invalid values ignored, capture vs "view"/"replace" button by read-only + answer state.
- **GeoShapeWidgetTest / GeoTraceWidgetTest (U)** — Polygon (geoshape) and polyline (geotrace) capture: answer display and capture/view button states.
- **ImageWidgetTest (U)** — Photo capture/choose: launches camera/gallery intents (incl. custom package), blocked when permission denied, disabled read-only.
- **IntegerWidgetTest / StringNumberWidgetTest (U)** — Integer input (truncates beyond 9 digits, separators, numeric input type) and the unbounded numeric-string variant.
- **OSMWidgetTest (U)** — OpenStreetMap-editor geopoint widget: answer display and capture/recapture button states.
- **PrinterWidgetTest (U)** — Label-printer trigger: button prints the HTML answer (nothing if no answer), context-menu registered, clear removes the answer, read-only disables.
- **QuestionWidgetTest (U)** — Base behaviour: a question's audio button uses the question index as its audio clip id.
- **RatingWidgetTest (U)** — Star-rating: renders the right number of stars across one/multiple lines; read-only disables.
- **SignatureWidgetTest (U)** — Signature capture (draw): shows/hides the signature image + error and default answer; read-only disables.
- **StringWidgetTest (U)** — Text input: input type, masked appearance (answers shown/hidden), and top-start gravity regardless of row count.
- **TriggerWidgetTest (U)** — "OK"/acknowledge trigger: checkbox checked when answered, get/clear answer, read-only disables.
- **UrlWidgetTest (U)** — URL launcher: opens the URI on click (toast + no-op when empty); clear is blocked with a "read-only" toast (the URL is not user-editable).
- **VideoWidgetTest (U)** — Video capture/choose: launches the right intents, blocked when permission denied, disabled read-only.
- **WidgetFactoryTest (U)** — The factory that maps a form question (type + appearance) to the correct widget class — the central question-type→widget dispatch (select-one minimal/list/label/no-label, likert, etc.). (Key mapping to reproduce.)

**`widgets/base/` — shared widget base-class contracts (U):**
- **WidgetTest / QuestionWidgetTest / BinaryWidgetTest / FileWidgetTest / SelectWidgetTest / GeneralStringWidgetTest / GeneralExStringWidgetTest / GeneralSelectOneWidgetTest / GeneralSelectMultiWidgetTest** — Abstract test suites that every concrete widget (or family) must satisfy: long-press/context-menu and "remove answer", answer get/set/clear, read-only handling, file/binary answer storage, and select-one/select-multi answer semantics. These define the common widget contract the per-type tests above build on.

**`widgets/datetime/` — date/time question types & non-Gregorian calendars (U):**
- **DateWidgetTest / TimeWidgetTest / DateTimeWidgetTest** — Date, time, and combined date-time pickers: button hidden when read-only, correct button text, `getAnswer` null when empty / returns the date/time (date-time returns date + current time).
- **DateTimeUtilsTest / DateTimeWidgetUtilsTest** — Helpers that read current/selected date-time, convert any calendar's selection to Gregorian, derive picker details from the form's appearance, and launch the correct picker dialog per calendar type (Gregorian fixed, Ethiopian, etc.).
- **DaylightSavingTest** — Date/date-time answers stay correct across timezones/DST (EST, EAT). (Important correctness edge case.)
- **pickers/{BikramSambat,Buddhist,Coptic,Ethiopian,Islamic,Myanmar,Persian}DatePickerDialogTest** — Each localized calendar picker dialog is cancellable and shows the correct date in full/year/month modes. (7 calendar systems to support.)

**`widgets/items/` — select-one / select-multiple variants (U):**
- **SelectOneWidgetTest / SelectMultiWidgetTest** — The standard select lists: grid vs flexbox layout by appearance (`columns-pack`), and buttons vs no-buttons item views; read-only disables.
- **SelectOneMinimalWidgetTest / SelectMultiMinimalWidgetTest** — The compact "minimal" (dropdown-style) selects: default text when empty, selected choices shown and updated on change, non-editable answer view; read-only disables.
- **ListWidgetTest / ListMultiWidgetTest** — "List" appearance selects (label beside control), incl. the spaces-in-underlying-values warning for multi.
- **LikertWidgetTest** — Likert-scale select-one; read-only disables.
- **RankingWidgetTest** — Drag-to-rank widget: value-change listener fired, spaces-in-values warning, read-only disables.
- **SelectOneImageMapWidgetTest / SelectMultiImageMapWidgetTest / SelectImageMapWidgetTest** — "Image map" selects (tap regions of an SVG/image to choose): selecting a region fires the value-change listener; spaces-in-values warning.
- **SelectOneFromMapWidgetTest / SelectOneFromMapDialogFragmentTest / SelectChoicesMapDataTest** — "Select one from map" (choices have geometry): the button opens a map dialog (blocked if location denied, uses app font), shows the answer; the dialog wires up the selection-map fragment with the right data and selected index; the data mapper builds mappable items from geotrace/geoshape/point choice geometries (choices without geometry excluded; extra columns become properties).

**`widgets/range/` — slider/range question types (U):**
- **RangeIntegerWidgetTest / RangeDecimalWidgetTest** — Integer/decimal sliders: changing the slider shows the value label + thumb and fires the listener; clearing hides them.
- **RangePickerIntegerWidgetTest / RangePickerDecimalWidgetTest** — The picker-style (non-slider) range variants: answer get/clear and listener.
- **RangeSliderTest / RangeSliderStateTest** — The slider control + state mapping: start/end/current/step labels; thumb shown only with an answer/placeholder; maps real values for ascending/descending ranges; null when answer out-of-range or range invalid.
- **RangePickerWidgetUtilsTest** — Generating the list of selectable numbers from start/end/step (handles equal start/end, step > range, ascending/descending).

**`widgets/utilities/` — capture-request plumbing shared by media/geo widgets (U):**
- **ActivityGeoDataRequesterTest** — Requesting geopoint/geotrace/geoshape: no intent/dialog without location permission; with permission, launches the right intent and marks the widget "waiting for data".
- **GeoPolyDialogFragmentTest / GeoWidgetUtilsTest** — Configuring the geo-poly capture fragment (read-only + geoshape/geotrace output mode; throws otherwise); formatting geo answers for display and into degree format.
- **AudioRecorderRecordingStatusHandlerTest / InternalRecordingRequesterTest / ExternalAppRecordingRequesterTest / RecordingRequesterProviderTest** — Audio-recording requesters: internal recorder picks codec by quality (AAC/AMR/AAC-low), external uses an intent; both no-op without permission/intent and toggle "waiting for data"; the provider chooses internal vs external per quality + the "prefer external" setting; the status handler tracks the recording session (always "recording" for background-audio forms).
- **FileRequesterImplTest / GetContentAudioFileRequesterTest / StringRequesterImplTest** — File/audio/string external-app requesters: launch the right chooser/intent and toggle waiting-for-data; show a toast / call `onError` when the intent can't be built.
- **RangeWidgetUtilsTest / StringWidgetUtilsTest** — Range picker button text ("no value selected" vs answer; hidden read-only); coercing answer data to integer/decimal values.

**`widgets/viewmodels/` (U):**
- **DateTimeViewModelTest** — Holds the selected date/time across the picker dialogs and updates on set-listeners (clearing focus on time set).
- **QuestionViewModelTest** — Running answer validation updates the exposed constraint-validation result.

**`widgets/warnings/` (U):**
- **SpacesInUnderlyingValuesTest / SpacesInUnderlyingValuesWarningTest** — Detects select choices whose underlying values contain spaces (a common form-authoring error) and renders a warning when present. 

---
## Instrumented / Espresso tests (`src/androidTest`)

_These are end-to-end UI tests that drive the real app through whole user journeys against real form
fixtures. They are the most complete behavioural specification — each describes a user-visible feature
working start to finish. (I) throughout._

### `feature/entitymanagement/`
- **ViewEntitiesTest (I)** — Browsing the local Entities datasets a project has downloaded (the offline "datasets" browser): lists, properties, and per-entity detail.

### `feature/external/`  _(integration via intents/URIs from other apps — the public API surface)_
- **AndroidShortcutsTest (I)** — Creating Android home-screen shortcuts that deep-link to specific forms.
- **FormDownloadActionTest (I)** — The external "download a form" action/intent triggers a form download.
- **FormEditActionTest / FormPickActionTest (I)** — External intents to edit a specific form, and to pick a form from a list, returning the result to the caller.
- **InstanceEditActionTest / InstancePickActionTest / InstanceUploadActionTest (I)** — External intents to edit, pick, and upload a saved instance. (Together these define the inter-app API another iOS app integration would need to mirror.)

### `feature/formentry/`  _(the heart of the app — filling forms end-to-end)_
- **AddRepeatTest / DeletingRepeatGroupsTest / FillBlankFormWithRepeatGroupTest (I)** — Adding repeat-group instances (incl. via the prompt and the menu) and deleting them; filling a form containing repeats.
- **AudioAutoplayTest / AudioRecordingTest / BackgroundAudioRecordingTest / ExternalAudioRecordingTest (I)** — Question audio auto-play; recording audio answers in-app, covertly in the background (driven by a form action), and via an external recorder app.
- **audit/AuditTest / audit/IdentifyUserTest / audit/TrackChangesReasonTest (I)** — The audit-log feature end-to-end: events are logged; the user-identity prompt; and requiring/recording a "reason for change" when editing finalized data.
- **backgroundlocation/LocationTrackingAuditTest / backgroundlocation/SetGeopointActionTest (I)** — Background GPS location auditing during form entry, and the `setgeopoint` form action that captures location without a widget.
- **CascadingSelectTest (I)** — Cascading/filtered selects (choices in one question filtered by earlier answers).
- **CatchFormDesignExceptionsTest / InvalidFormTest (I)** — Form-author errors are caught and shown as friendly errors rather than crashing.
- **ContextMenuTest (I)** — Long-press context menu on questions (e.g. remove answer).
- **dynamicpreload/DynamicPreLoadedDataPullTest (I)** — `pulldata()` and search()-appearance lookups from CSV/secondary data work during entry.
- **EncryptedFormTest (I)** — Filling and saving encrypted forms (submission encryption).
- **entities/EntityFormCreateUpdateTest / EntityFormEditTest / EntityFormApprovalTest / EntityFormLockingTest / EntityFormSpecVersionTest / EntityListSyncTest (I)** — The Entities feature end-to-end: forms that create/update entities, editing them, the approval workflow, list locking, entity-spec version handling, and syncing entity lists. (Major feature — re-implement against these.)
- **ExternalSecondaryInstanceTest / ExternalSelectsTest (I)** — Selects backed by external secondary instances (external CSV/XML choice lists).
- **FieldListUpdateTest (I)** — "Field-list" groups where relevance/values of questions update live on the same screen as you answer.
- **FormEndTest (I)** — The end-of-form screen: save-as-draft vs finalize vs send behaviour.
- **FormHierarchyTest (I)** — The form hierarchy/"go to question" navigator, including jumping and editing within repeats.
- **FormLanguageTest / SettingLanguageTest-equivalent (I)** — Switching a form's language updates labels/audio/media.
- **FormMediaTest / ImageLoadingTest (I)** — Question images/media load and display (incl. big-image handling).
- **FormMetadataTest (I)** — Form metadata (instanceID, deviceID, username, etc.) is preloaded/recorded.
- **FormNavigationTest (I)** — Moving forward/back through questions, swipe vs buttons, navigation settings.
- **FormSaveTest / SaveIncompleteTest / QuickSaveTest (I)** — Saving a form: as a finalized submission, as an incomplete draft, and the quick-save action mid-form.
- **FormStylingTest / GuidanceTest / LikertTest (I)** — Question styling/appearances; guidance-hint display; the Likert widget end-to-end.
- **IntentGroupTest / NestedIntentGroupTest (I)** — "Intent groups" that launch an external app to fill a group of fields (incl. nested).
- **RankingWidgetWithCSVTest (I)** — The ranking widget populated from a CSV choice list.
- **RequiredAndConstraintQuestionTest (I)** — Required-question and constraint validation block progress/finalization with the right messages.
- **SavePointTest (I)** — Auto-savepoints created when the app is killed mid-form, and recovery from them. _(This is the file responsible for 6 of the 9 emulator flakes — timing-sensitive process-kill simulation.)_
- **SearchAppearancesTest (I)** — The `search()` appearance (autocomplete/external search selects).
- **FormEndTest**, **QuittingFormTest** — Quitting a form (discard vs keep editing vs save draft) end-to-end.

### `feature/formmanagement/`  _(getting, updating, deleting blank forms — server interaction)_
- **GetBlankFormsTest (I)** — Manually downloading blank forms from the server ("Get Blank Form").
- **FormUpdateTest / MatchExactlyTest / PreviouslyDownloadedOnlyTest / ManualUpdatesTest (I)** — The form-update modes end-to-end: automatic updates, "match exactly" (mirror the server's list, adding/removing), "previously-downloaded only", and fully manual. (Defines the form-sync UX/policies.)
- **HideOldVersionsTest (I)** — Showing only the newest version of each form vs all versions.
- **BulkFinalizationTest (I)** — "Finalize all" drafts in one action.
- **DeleteBlankFormTest (I)** — Deleting blank forms.
- **GetBlankFormsTest**, **FormsAdbTest (I)** — `FormsAdbTest` seeds/inspects forms via adb for test setup (a harness/utility test rather than a user feature).

### `feature/instancemanagement/`  _(saved-form lifecycle: edit, send, delete)_
- **EditSavedFormTest (I)** — Editing a saved draft/finalized instance (incl. edit-after-finalize rules).
- **SendFinalizedFormTest / AutoSendTest / PartialSubmissionTest (I)** — Submitting finalized instances to the server manually, automatically (per network policy), and partial submissions.
- **DeleteSavedFormTest (I)** — Deleting saved instances.
- **InstancesAdbTest (I)** — adb-driven instance seeding/inspection for test setup (harness utility).

### `feature/maps/`
- **FormMapTest (I)** — The "forms on a map" screen: showing instances with geometry, tapping markers to view/edit, status icons. (Backs the geo-data overview.)

### `feature/projects/`  _(multi-project management end-to-end)_
- **AddNewProjectTest (I)** — Adding a project manually and via QR.
- **SwitchProjectTest / UpdateProjectTest / DeleteProjectTest (I)** — Switching the current project, editing project display (name/icon/colour), and deleting a project.
- **LaunchScreenTest (I)** — The first-launch / project-setup screen.
- **MobileDeviceManagementTest (I)** — MDM-driven managed configuration of projects/settings.
- **GoogleDriveDeprecationTest (I)** — Behaviour for the deprecated Google Drive projects (warnings/removal).
- **ProjectsAdbTest (I)** — adb-driven project setup (harness utility).

### `feature/settings/`
- **ServerSettingsTest (I)** — Configuring the server connection (URL, credentials, protocol).
- **ConfigureWithQRCodeTest (I)** — Importing settings from a QR code end-to-end.
- **FormEntrySettingsTest / FormManagementSettingsTest / FormMetadataSettingsTest (I)** — Form-entry, form-management and metadata settings actually change app behaviour. _(FormMetadataSettings accounts for 2 of the 9 emulator flakes.)_
- **MovingBackwardsTest (I)** — The "moving backwards in a form" admin restriction.
- **ResetProjectTest (I)** — The "reset application" feature (clearing forms/instances/settings/etc.).
- **SettingLanguageTest (I)** — Changing the app (UI) language.

### `feature/smoke/`  _(the quick-confidence suite CI runs first)_
- **AllWidgetsFormTest (I)** — Opens a form exercising **every** widget type — the broadest single smoke test. _(1 of the 9 emulator flakes.)_
- **GetAndSubmitFormTest (I)** — The core happy path: download a blank form, fill it in, and submit it.
- **BadServerTest (I)** — Graceful handling of malformed server responses (e.g. a form list missing hashes).

### `instrumented/`  _(lower-level device tests needing a real runtime)_
- **forms/FormUtilsTest (I)** — Form file utilities against the real filesystem.
- **tasks/FormLoaderTaskTest (I)** — Loading/parsing a form on-device (the FormLoaderTask) including real JavaRosa parsing.
- **utilities/CustomSQLiteQueryExecutionTest (I)** — Executing custom SQLite queries against the real Android SQLite.
- **utilities/DateTimeUtilsTest (I)** — Date/time utilities against the device's real locale/timezone APIs.

### `benchmark/`  _(performance benchmarks — CI EXCLUDES these from normal runs; excluded from our coverage run too)_
- **EntitiesBenchmarkTest (I)** — Performance with very large entity lists (e.g. 100,000 entities).
- **FormsUpdateBenchmarkTest (I)** — Performance of bulk form updates.
- **SearchBenchmarkTest (I)** — Performance of `search()`/large choice lists.

---

_Catalog complete: all 272 unit + 97 instrumented = 369 `collect_app` test files covered._
