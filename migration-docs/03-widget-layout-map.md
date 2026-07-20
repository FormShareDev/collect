# ODK Collect — Widget Layout & Event Map (for SwiftUI re-implementation)

_A per-widget map of the visual structure and event behaviour of every question widget in
`collect_app`, normalized so each can be re-built as a native SwiftUI view. Source of truth:
`collect_app/src/main/java/org/odk/collect/android/widgets/**` and `res/layout/*widget*`._

## How to read this map

Every widget is an Android `View` that ultimately extends **`QuestionWidget`** (a `FrameLayout`).
The widget classes obtain their answer UI in one of two ways — the map normalizes both into a
**view tree**:

- **ViewBinding / XML** — inflates a layout file (e.g. `DateWidget` → `date_widget_answer.xml`). The
  view tree is taken directly from that XML.
- **`<programmatic>`** — builds views in Kotlin/Java (e.g. `SelectOneWidget` creates `RadioButton`s,
  `StringWidget` creates a `WidgetAnswerText`). The view tree is reconstructed from the code.

Each widget only supplies the **answer region**; the question label, hint, guidance and error UI
come from the shared shell (Part A). So a SwiftUI port is: **one container component (Part A) + the
shared sub-components (Part B) + one small "answer view" per widget (Part D), dispatched by the rules
in Part C.**

Answer values are JavaRosa `IAnswerData` subclasses (`StringData`, `IntegerData`, `SelectOneData`,
`GeoPointData`, …). Each card lists the exact type — that is the model your SwiftUI binding reads/writes.

---

## Part A — The shared shell → SwiftUI `QuestionView` container

**Source:** `QuestionWidget` + `res/layout/question_widget.xml` + `AudioVideoImageTextLabel`
(`res/layout/audio_video_image_text_label.xml`).

`question_widget.xml` is a vertical `LinearLayout`:

```
LinearLayout (vertical)                          → VStack(alignment: .leading)
├─ AudioVideoImageTextLabel  (id=question_label)   → QuestionLabelHeader   (Part B.1)
├─ FrameLayout (id=help_text) → help_layout        → hint + expandable guidance (Part B.2)
├─ RelativeLayout (id=answer_container)  «EMPTY»    → the per-widget Answer View  (Part D)  ← slot
└─ include question_error_layout                    → inline error text (red)
```

**SwiftUI container shape:**
```swift
struct QuestionView<Answer: View>: View {
    let prompt: FormPrompt          // label, hint, guidance, media, required, readOnly
    @Binding var error: String?
    @ViewBuilder var answer: () -> Answer
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            QuestionLabelHeader(prompt)         // text + media + audio/video buttons
            if let hint = prompt.hint { HintView(hint) }
            if let g = prompt.guidance { ExpandableGuidance(g) }   // collapsed by default, tap to expand
            answer()                            // ← the widget-specific region
            if let error { ErrorText(error) }
        }
    }
}
```

**Shared behaviours every widget inherits (implement once on the container / answer protocol):**

| Android behaviour (`QuestionWidget`) | What it does | SwiftUI equivalent |
|---|---|---|
| `getAnswer(): IAnswerData?` | current answer or null/empty | `var answer: AnswerData?` (binding) |
| `clearAnswer()` | wipe the answer + view | `answer = nil` |
| `widgetValueChanged()` | notify engine answer changed (re-evaluates relevance/constraints/calculations live) | `onChange(of: answer)` → call form engine |
| **Long-press anywhere → "Remove answer" menu** (`registerToClearAnswerOnLongPress`) | context menu to clear | `.contextMenu { Button("Remove answer"){ answer = nil } }` |
| `isReadOnly()` | disables all interactive elements; capture/launch buttons hidden | `.disabled(prompt.readOnly)` + hide capture buttons |
| `setFocus` / `softKeyboard` | focus + keyboard for text | `@FocusState` |
| guidance hint expand/collapse | animated reveal of extra help | `DisclosureGroup` |
| `shouldSuppressFlingGesture` | block form swipe while dragging a control (sliders, signature) | gate the paging gesture while editing |

> **Form-engine coupling (critical):** `widgetValueChanged()` drives JavaRosa to recompute relevance
> (show/hide), constraints (validation) and calculations across the whole form. In SwiftUI, every
> answer mutation must call into the (reused) form engine and the screen must re-render from its
> output. This is the single most important non-visual behaviour to preserve.

---

## Part B — Shared sub-components → SwiftUI

**B.1 `QuestionLabelHeader`** (`AudioVideoImageTextLabel`, `audio_video_image_text_label.xml`):
```
RelativeLayout
├─ LinearLayout (image_text_label_container)
│   ├─ FrameLayout(text_container) → MaterialTextView(text_label)   // HTML/markdown label
│   ├─ ImageView(imageView)                                          // optional question image
│   └─ TextView(missingImage)                                        // shown if image fails to load
└─ LinearLayout(media_buttons)
    ├─ AudioButton(audioButton)                                      // play/stop question audio
    └─ MultiClickSafeMaterialButton(videoButton)                     // play question video
```
→ SwiftUI: `HStack { VStack { Text(label, html) ; AsyncImage(or "missing image") } ; AudioButton ; VideoButton }`.
Label text is HTML/markdown (see `HtmlUtilsTest`) — render via an attributed-string view.

**B.2 Hint + guidance** — `HintView` (plain secondary text) and `ExpandableGuidance`
(`DisclosureGroup`, collapsed unless the form sets guidance to "expanded").

**B.3 `WidgetAnswerText`** (`views/WidgetAnswerText`, `res/layout/widget_answer_text.xml`) — the
reusable text answer used by all text/number widgets. It overlays **two** views and swaps them by
read-only state:
```
FrameLayout
├─ TextInputLayout > TextInputEditText (id=edit_text)   // editable mode
└─ MaterialTextView (id=text_view)                       // read-only display mode
```
→ SwiftUI: `if readOnly { Text(value) } else { TextField(...) }` inside a bordered field; supports an
error label and a "masked" mode (secure entry). Wire `onChange` → `widgetValueChanged`.

**B.4 Capture/launch button row** — used by all media/geo/external widgets: one or two
`MultiClickSafeMaterialButton`s ("Capture"/"Choose"/"Replace"/"View"/"Launch") whose label and
visibility depend on whether an answer exists and read-only state. → a reusable `CaptureButtonRow`.

---

## Part C — Master dispatch table (from `WidgetFactory`)

Which widget renders a question = f(XForms **control type**, **data type**, **appearance**). This is
the table to reproduce as your SwiftUI `@ViewBuilder` switch.

| Control / Data type | Appearance | Widget | Answer (`IAnswerData`) |
|---|---|---|---|
| input / **date-time** | — | `DateTimeWidget` | `DateTimeData` |
| input / **date** | (+calendar appearances) | `DateWidget` | `DateData` |
| input / **time** | — | `TimeWidget` | `TimeData` |
| input / **decimal** | — / `ex:` / `bearing` | `DecimalWidget` / `ExDecimalWidget` / `BearingWidget` | `DecimalData` (bearing → `StringData`) |
| input / **integer** | — / `counter` / `ex:` | `IntegerWidget` / `CounterWidget` / `ExIntegerWidget` | `IntegerData` |
| input / **geopoint** | — / `placement-map`,`maps` | `GeoPointWidget` / `GeoPointMapWidget` | `GeoPointData` |
| input / **geoshape** | — | `GeoShapeWidget` | `GeoShapeData` (→ `StringData`) |
| input / **geotrace** | — | `GeoTraceWidget` | `GeoTraceData` (→ `StringData`) |
| input / **barcode** | — | `BarcodeWidget` | `StringData` |
| input / **text** | `printer` | `PrinterWidget` | `StringData` |
| input / **text** | `ex:` | `ExStringWidget` | `StringData` |
| input / **text** | `numbers` | `StringNumberWidget` | `StringData` |
| input / **text** | `url` | `UrlWidget` | `StringData` |
| input / **text** | else | `StringWidget` | `StringData` |
| **file capture** | — / `ex:` | `ArbitraryFileWidget` / `ExArbitraryFileWidget` | `StringData` (filename) |
| **image choose** | `signature`/`annotate`/`draw`/`ex:`/— | `SignatureWidget` / `AnnotateWidget` / `DrawWidget` / `ExImageWidget` / `ImageWidget` | `StringData` (filename) |
| **osm capture** | — | `OSMWidget` | `StringData` |
| **audio capture** | — / `ex:` | `AudioWidget` / `ExAudioWidget` | `StringData` (filename) |
| **video capture** | — / `ex:` | `VideoWidget` / `ExVideoWidget` | `StringData` (filename) |
| **select one** | minimal/likert/list-nolabel/list/label/image-map/map/— | `SelectOneMinimalWidget` / `LikertWidget` / `ListWidget` / `BaseSelectListWidget` / `SelectOneImageMapWidget` / `SelectOneFromMapWidget` / `SelectOneWidget` | `SelectOneData` |
| **select multi** | minimal/list-nolabel/list/label/image-map/timed-grid/— | `SelectMultiMinimalWidget` / `ListMultiWidget` / `BaseSelectListWidget` / `SelectMultiImageMapWidget` / `TimedGridWidget` / `SelectMultiWidget` | `SelectMultiData` |
| **rank** | — | `RankingWidget` | `SelectMultiData` (ordered) |
| **trigger** | — | `TriggerWidget` | `StringData` ("OK") |
| **range / integer** | `rating` / slider / picker | `RatingWidget` / `RangeIntegerWidget` / `RangePickerIntegerWidget` | `IntegerData` |
| **range / decimal** | slider / picker | `RangeDecimalWidget` / `RangePickerDecimalWidget` | `DecimalData` |

_Appearance parsing: `Appearances` / `AppearancesTest` (Part 02). `ex:` = external-app variant;
`columns`/`columns-pack` control select grid layout; `no-buttons`/`compact` change item style._

---

## Part D — Per-widget cards

> Format per card: **Layout** (source → view tree) · **Events** · **Answer** · **SwiftUI**.
> Label / hint / guidance / error and long-press-clear come from Part A and are not repeated.

### D.1 Text & number family

All extend `StringWidget` and use the shared **`WidgetAnswerText`** (Part B.3) in `answer_container`.

**StringWidget** — `<programmatic>` (`WidgetAnswerText`)
- **Layout:** `answer_container` → `WidgetAnswerText` (editable `TextInputEditText` ⊕ read-only `MaterialTextView`). Multi-line unless `inputType` overridden; honours `masked` appearance (secure text) and `rows` appearance (min lines); top-start gravity.
- **Events:** `TextWatcher` on the field → `widgetValueChanged()` (live). Long-press → clear.
- **Answer:** `StringData(text)` or `null` when empty.
- **SwiftUI:** `TextField`/`TextEditor` (multiline), `.roundedBorder`; secure variant for `masked`; `@State text` bound to `StringData`.

**StringNumberWidget** — `<programmatic>` (extends StringWidget)
- **Layout:** same `WidgetAnswerText`, numeric keyboard; thousands separators added when enabled; digits **not** length-limited.
- **Answer:** `StringData` (kept as text to preserve big/precise numbers).
- **SwiftUI:** `TextField` + `.keyboardType(.numbersAndPunctuation)`, custom separator formatting.

**IntegerWidget** — `<programmatic>` (extends StringNumberWidget)
- **Layout:** numeric field, integer input type, **truncates beyond 9 digits**, optional separators.
- **Answer:** `IntegerData`.
- **SwiftUI:** `TextField` + `.keyboardType(.numberPad)`, parse → `Int`.

**DecimalWidget** — `<programmatic>` (extends StringNumberWidget)
- **Layout:** decimal field, up to 15 digits preserved, optional separators, no forced precision.
- **Answer:** `DecimalData`.
- **SwiftUI:** `TextField` + `.keyboardType(.decimalPad)`, parse → `Double`/`Decimal`.

**ExStringWidget / ExDecimalWidget / ExIntegerWidget** — `ExStringQuestionTypeBinding` / `<programmatic>`
- **Layout:** a **launch button** (label + custom font) + a read-only `WidgetAnswerText` display. String variant honours `masked` and `hidden-answer` appearances; numeric variants truncate/format like their non-`ex` siblings.
- **Events:** button → launch external app via intent (`ExternalAppIntentProvider`); on result, fill the answer display; toast/error if the app is missing; read-only / no-intent → disabled.
- **Answer:** `StringData` / `DecimalData` / `IntegerData` from the returned value.
- **SwiftUI:** `Button("Launch")` opening an external app / custom sheet + read-only value display. (External-app interop needs an iOS-native redesign — findings §5.)

**UrlWidget** — `UrlWidgetAnswerBinding` (`url_widget_answer.xml`)
- **Layout:** read-only text showing the URL + an "Open" button.
- **Events:** button → open the URI in a browser (toast + no-op if empty). Value is **not** user-editable; `clearAnswer` shows a "read-only" toast and keeps the value.
- **Answer:** `StringData` (URL supplied by the form).
- **SwiftUI:** `Link`/`Button` → `openURL`; display-only text.

**PrinterWidget** — `R.layout.printer_widget`
- **Layout:** a "Print" button (no text field).
- **Events:** button → print the HTML answer via a label printer (no-op if no answer); read-only disables.
- **Answer:** `StringData` (HTML to print, provided by the form/calculation).
- **SwiftUI:** `Button("Print")` → printing service; HTML processed per `PrinterWidgetViewModelTest` (wrap partial HTML, absolute image paths, `<qrcode>`→`<img>`). iOS: AirPrint / `UIPrintInteractionController`.

### D.2 Date & time family

**DateWidget** — `DateWidgetAnswerBinding` (`date_widget_answer.xml`)
- **Layout:** `LinearLayout` → `MultiClickSafeMaterialButton(date_button)` ("Select date") + `MaterialTextView(date_answer_text)` (formatted date). Button hidden when read-only.
- **Events:** button → opens a date-picker dialog (Gregorian or one of 7 calendar systems per appearance — see `DateTimeWidgetUtils` / the `pickers/*` dialogs); on set → `widgetValueChanged()`, updates the text.
- **Answer:** `DateData` (null when no date). DST/timezone-safe (`DaylightSavingTest`).
- **SwiftUI:** `Button` → `DatePicker`(`.date`) in a sheet; localized calendar pickers for Ethiopian/Islamic/Persian/Coptic/Buddhist/Bikram-Sambat/Myanmar need custom pickers (the Gregorian↔calendar conversions are in reusable logic — Part 02 `dependencies/*`, `MyanmarDateUtils`).

**TimeWidget** — `TimeWidgetAnswerBinding` (`time_widget_answer.xml`)
- **Layout:** `Button(time_button)` + `MaterialTextView(time_answer_text)`.
- **Events:** button → time-picker dialog; on set → `new TimeData`, `widgetValueChanged()`.
- **Answer:** `TimeData`.
- **SwiftUI:** `Button` → `DatePicker`(`.hourAndMinute`).

**DateTimeWidget** — `DateTimeWidgetAnswerBinding` (`date_time_widget_answer.xml`)
- **Layout:** combines the date and time rows (date button+text, time button+text).
- **Events:** two pickers; on set → `new DateTimeData` (date + time; if only date set, current time used per `DateTimeWidgetTest`).
- **Answer:** `DateTimeData`.
- **SwiftUI:** `DatePicker`(`[.date, .hourAndMinute]`).

_(The 7 calendar **picker dialogs** — `BikramSambat/Buddhist/Coptic/Ethiopian/Islamic/Myanmar/Persian DatePickerDialog` — are full-screen spinners over the converted calendar; each is a separate SwiftUI picker fed by the corresponding conversion utility.)_

### D.3 Geo family

All five share the **button + answer-text** pattern and launch a separate map/capture screen (the
map engine itself — Google/OSM — is selected by `MapFragmentFactory`; see the `geo`/`maps`/`osmdroid`
modules, which are their own large port).

**GeoPointWidget** — `GeopointQuestionBinding` (`geopoint_question.xml`)
- **Layout:** `LinearLayout` → `MultiClickSafeMaterialButton(simple_button)` ("Start GeoPoint"/"View"/"Change location") + `MaterialTextView(geo_answer_text)` (lat/long/alt/accuracy). Button hidden read-only; "view" vs "capture" label by answer + read-only.
- **Events:** button → requests location permission then launches the geopoint-capture activity (`ActivityGeoDataRequester`), marks widget "waiting for data"; result → answer text. Invalid stored values are ignored.
- **Answer:** `GeoPointData` (lat, lon, alt, accuracy).
- **SwiftUI:** `Button` → CoreLocation capture screen (`MKMapView`/`Map`), permission via `CLLocationManager`; display formatted coordinates.

**GeoPointMapWidget** — `GeopointQuestionBinding` — same as above but uses the on-screen **map**
placement appearance (`placement-map`/`maps`); same answer/events, map-first capture.

**GeoShapeWidget** — `GeoshapeQuestionBinding` (`geoshape_question.xml`) — button + answer text;
launches polygon capture. **Answer:** `GeoShapeData` (serialized as `StringData`; list of points forming a closed polygon).

**GeoTraceWidget** — `GeotraceQuestionBinding` (`geotrace_question.xml`) — button + answer text;
launches polyline capture. **Answer:** `GeoTraceData` (ordered points; line). `GeoPolyDialogFragment`
configures shape vs trace output mode.

**OSMWidget** — `OsmWidgetAnswerBinding` (`osm_widget_answer.xml`) — button (capture/recapture) + answer
text; launches the OpenStreetMap editor (OSM tags). **Answer:** `StringData` (OSM path).

**BearingWidget** — `BearingWidgetAnswerBinding` (`bearing_widget_answer.xml`) (compass; listed here as geo-adjacent)
- **Layout:** `Button(bearing_button)` + `WidgetAnswerText(widget_answer_text)`.
- **Events:** button → launches a bearing/compass capture (sensor `Intent`); result → answer. Button hidden read-only.
- **Answer:** `StringData` (degrees).
- **SwiftUI:** `Button` → `CMMotionManager`/`CLHeading` capture screen.

### D.4 Range, rating, counter, trigger

**RangeIntegerWidget / RangeDecimalWidget** — `<programmatic>` (extend abstract `RangeWidget`; inflate `range_widget_horizontal.xml` or `range_widget_vertical.xml`)
- **Layout:** `LinearLayout` → `TextView(current_value)` + `FrameLayout(slider)` holding a Material **`Slider`** + `min_value`/`max_value` labels (+ a tick `View`). Orientation per appearance (`vertical`).
- **Events:** `Slider.addOnChangeListener` → shows current value + thumb, `widgetValueChanged()`; `clearAnswer` hides the thumb/label. Suppresses the form fling-gesture while dragging.
- **Answer:** `IntegerData` / `DecimalData`, computed from start/end/step (`RangeWidgetUtils`, `RangeSliderState` — maps slider position to the real value, ascending or descending; null when out of range).
- **SwiftUI:** `Slider(value:in:step:)` with min/max labels and a current-value readout; hide value until first interaction.

**RangePickerIntegerWidget / RangePickerDecimalWidget** — `RangePickerWidgetAnswerBinding` (`range_picker_widget_answer.xml`)
- **Layout:** `Button(widget_button)` ("Select value"/value) + `MaterialTextView(widget_answer_text)`.
- **Events:** button → a number-picker dialog over the list of allowed values (`RangePickerWidgetUtils` builds the list from start/end/step); selection → answer. Button hidden read-only.
- **Answer:** `IntegerData` / `DecimalData`.
- **SwiftUI:** `Button` → `Picker`(wheel) over the generated values.

**RatingWidget** — `RatingWidgetAnswerBinding` (`rating_widget_answer.xml`)
- **Layout:** one or two `RatingBar`s (`rating_bar1`, `rating_bar2`) — wraps to a second row when there are many stars.
- **Events:** `onRatingChanged` → `widgetValueChanged()`; read-only disables.
- **Answer:** `IntegerData` (number of stars).
- **SwiftUI:** a row of tappable star `Image`s (or two rows when count is large).

**CounterWidget** — `CounterWidgetBinding` (`counter_widget.xml`)
- **Layout:** `ImageView(minus_button)` + `TextView(value)` + `ImageView(plus_button)`.
- **Events:** plus/minus `setOnClickListener` → increment/decrement, `widgetValueChanged()`. Minus disabled at 0/empty; plus disabled at 999,999,999; read-only disables both.
- **Answer:** `IntegerData`.
- **SwiftUI:** `Stepper` or a custom `HStack { Button("−"); Text(value); Button("+") }` with the same bounds.

**TriggerWidget** — `R.layout.trigger_widget_answer`
- **Layout:** a single checkbox ("OK"/acknowledge).
- **Events:** check/uncheck → `widgetValueChanged()`; checked → `new StringData("OK")`, unchecked → null.
- **Answer:** `StringData` ("OK") or null.
- **SwiftUI:** `Toggle` styled as a checkbox.

### D.5 Selection family (`select_one` / `select_multi` / rank)

Choices come from the form's `SelectChoice` list (static, or from an external/secondary instance —
see `ExternalSelects`, `dynamicpreload`). Answers are `SelectOneData` (one choice) or `SelectMultiData`
(set of choices). Each choice label is itself an `AudioVideoImageTextLabel` (text + optional image/audio).

**SelectOneWidget / SelectMultiWidget** — `<programmatic>` over `BaseSelectListWidget` (`SelectListWidgetAnswerBinding` → `select_list_widget_answer.xml`)
- **Layout:** `TextInputEditText(choices_search_box)` (filter box, autocomplete appearance) + **`ChoicesRecyclerView(choices_recycler_view)`** — a list of rows, each a `RadioButton` (one) or `CheckBox` (multi) beside the choice's `AudioVideoImageTextLabel`. Layout manager: **grid** by default, **flexbox** for `columns-pack`; column count from the `columns` appearance (`AppearancesTest`); `no-buttons`/`compact` → tappable cards without the radio/checkbox.
- **Events:** row tap / `onItemClick` → select; single-select replaces the previous selection, multi accumulates; search box filters; each → `widgetValueChanged()`. `RadioButton`s are mutually exclusive.
- **Answer:** `new SelectOneData(choice)` / `new SelectMultiData(choices)`.
- **SwiftUI:** a `List`/`LazyVGrid` of selectable rows (radio = single-selection, checkbox = multi); a search `TextField` for the autocomplete appearance; "no-buttons" = tappable cards. Bind selection → `SelectOneData`/`SelectMultiData`.

**SelectOneMinimalWidget / SelectMultiMinimalWidget** — `<programmatic>` (`SelectMinimalWidgetAnswerBinding` → `select_minimal_widget_answer.xml`)
- **Layout:** a single read-only `MultiClickSafeTextInputEditText(answer)` showing the selected label(s) (or default text).
- **Events:** tap → opens a `SelectMinimalDialog` (bottom sheet list with optional search); on dismiss, saves the answer **only if it changed** (`SelectOneMinimalDialogTest`); updates the label.
- **Answer:** `SelectOneData` / `SelectMultiData`.
- **SwiftUI:** a `Menu`/`Picker` (single) or a sheet with multi-select (multi); show selected labels in a field.

**ListWidget / ListMultiWidget** — `<programmatic>` over `BaseSelectListWidget` using `R.layout.label_widget` (label-left / answer-right layout)
- **Layout:** the `label_widget.xml` arrangement (question label on the left, choices column on the right) with a `ChoicesRecyclerView` of radio/checkbox rows — the "list"/"label" appearance.
- **Events / Answer / SwiftUI:** as SelectOne/Multi, but laid out beside the label; multi shows the spaces-in-underlying-values warning.

**LikertWidget** — `<programmatic>`
- **Layout:** a horizontal scale of `RadioButton`s with labels (a Likert row).
- **Events:** select one point → `widgetValueChanged()`; read-only disables.
- **Answer:** `SelectOneData`.
- **SwiftUI:** a horizontal `HStack` of selectable points (segmented-style).

**RankingWidget** — `RankingWidgetBinding` (`ranking_widget.xml`)
- **Layout:** `MultiClickSafeMaterialButton(rank_items_button)` ("Rank items") + `MaterialTextView(answer)` showing the current order.
- **Events:** button → a drag-to-reorder dialog; on confirm → `new SelectMultiData` in the chosen order; `widgetValueChanged()`. Value-change listener fires (`RankingWidgetTest`).
- **Answer:** `SelectMultiData` (ordered).
- **SwiftUI:** a `Button` → an `.onMove`-enabled `List` (edit mode) in a sheet; store the ordered set.

**SelectOneImageMapWidget / SelectMultiImageMapWidget / SelectImageMapWidget** — `<programmatic>` (`SelectImageMapWidgetAnswerBinding` → `select_image_map_widget_answer.xml`)
- **Layout:** **`CustomWebView(image_map)`** rendering an SVG/image whose regions map to choices + `MaterialTextView(selected_elements)` listing the selected region labels.
- **Events:** tapping a region (JS bridge from the WebView) selects/deselects that choice → `widgetValueChanged()`.
- **Answer:** `SelectOneData` / `SelectMultiData`.
- **SwiftUI:** render the SVG (e.g. a `WKWebView` bridge, or native SVG with tappable paths); map taps to choices.

**SelectOneFromMapWidget** — `SelectOneFromMapWidgetAnswerBinding` (`select_one_from_map_widget_answer.xml`)
- **Layout:** `MultiClickSafeMaterialButton(button)` + `MaterialTextView(answer)`.
- **Events:** button → opens `SelectOneFromMapDialogFragment` (a map of choices that have geometry; tap a marker to choose); blocked without location permission; uses the app font. Selection → answer (`SelectChoicesMapData` builds mappable items from choice geometry).
- **Answer:** `SelectOneData`.
- **SwiftUI:** `Button` → a `Map` of choice annotations; tap to select.

**TimedGridWidget** — `<programmatic>` (the `timedgrid` module)
- **Layout:** a grid of choices with a per-cell timing behaviour (a specialized select-multi grid).
- **Answer:** `SelectMultiData`.
- **SwiftUI:** a `LazyVGrid` of toggleable cells. (Niche; low test coverage — verify behaviour against the module.)

### D.6 Media-capture family (image / audio / video / file / barcode)

All produce a **`StringData` holding the saved file's name** (the file lives in the instance's media
dir). They share the capture/choose **button row** (Part B.4) and launch a capture activity or external
app via `Intent`. Read-only hides capture; permission-denied blocks the launch (toast).

**ImageWidget** — `ImageWidgetBinding` (`image_widget.xml`); base `BaseImageWidget`
- **Layout:** `MultiClickSafeMaterialButton(capture_button)` ("Take Picture") + `MultiClickSafeMaterialButton(choose_button)` ("Choose Image") + `MaterialTextView(error_message)` + an `ImageView` thumbnail (in the base) of the captured photo.
- **Events:** capture → camera `Intent` (`MediaStore`, optional custom package); choose → gallery picker `Intent`; result saved + compressed per `ImageCompressionController` (form `max-pixels` vs image-size setting); thumbnail shown.
- **Answer:** `StringData` (image filename).
- **SwiftUI:** buttons → `UIImagePickerController`/`PHPickerViewController`/camera; show thumbnail; downscale per the same size rules.

**AnnotateWidget** — `AnnotateWidgetBinding` (`annotate_widget.xml`); extends image
- **Layout:** capture + choose + **`annotate_button`** ("Markup") + thumbnail. Lets the user draw on top of a photo.
- **Events:** as ImageWidget, plus annotate → opens the drawing canvas over the image.
- **Answer:** `StringData` (annotated image filename).
- **SwiftUI:** image picker + a PencilKit/`Canvas` overlay editor.

**DrawWidget** — `DrawWidgetBinding` (`draw_widget.xml`); **SignatureWidget** — `SignatureWidgetBinding` (`signature_widget.xml`)
- **Layout:** a single button (`draw_button` / `sign_button` — "Draw"/"Sign") + `ImageView(image)` showing the result + `error_message` (when the image can't load). Default answer shown if present.
- **Events:** button → opens a full-screen drawing canvas (finger/stylus); saved as an image.
- **Answer:** `StringData` (image filename).
- **SwiftUI:** `Button` → a PencilKit `PKCanvasView` / `Canvas` capture screen; show the result image.

**AudioWidget** — `AudioWidgetAnswerBinding` (`audio_widget_answer.xml`); **ExAudioWidget** — `ExAudioWidgetAnswerBinding` (`ex_audio_widget_answer.xml`)
- **Layout (Audio):** `record_audio_button` + `choose_audio_button` + an **`AudioControllerView(audio_player)`** (play/scrub/duration — Part 02 `audio/AudioControllerViewTest`), shown once there's an answer. ExAudio replaces the two buttons with a single `launch_external_app_button`.
- **Events:** record → in-app recorder (codec by quality — `RecordingRequesterProvider`); choose → audio file picker; ExAudio → external recorder app. Result → answer + player.
- **Answer:** `StringData` (audio filename).
- **SwiftUI:** record (`AVAudioRecorder`) / pick (file importer) / external; a custom audio player view (play/seek/duration).

**VideoWidget** — `<programmatic>`; **ExVideoWidget** — `<programmatic>`; **ExImageWidget** — `ExImageWidgetBinding` (`ex_image_widget_answer.xml`)
- **Layout:** capture/choose (or `launch_external_app_button` for the `ex` variants) + a `VideoView`/`ImageView` preview shown once answered.
- **Events:** capture → camera-video `Intent` / choose → gallery; `ex` → external app. Result → answer + preview.
- **Answer:** `StringData` (filename).
- **SwiftUI:** camera/video picker; `VideoPlayer` / image preview.

**ArbitraryFileWidget** — `<programmatic>`; **ExArbitraryFileWidget** — `<programmatic>` (base `FileWidget`)
- **Layout:** a "Choose File" button + a `MaterialTextView` answer (file name); tapping the name opens a viewer.
- **Events:** button → system file picker (`ExArbitraryFile` → external app); tap answer → open file in a viewer; clear hides it.
- **Answer:** `StringData` (filename).
- **SwiftUI:** `.fileImporter`; tap → `QuickLook` preview.

**BarcodeWidget** — `<programmatic>`
- **Layout:** a "Scan" button (hidden read-only) + a `MaterialTextView` answer; "Replace Barcode" once scanned.
- **Events:** button → launches the barcode/QR scanner (`qr-code` module); result → answer; clear updates the button title.
- **Answer:** `StringData` (scanned text).
- **SwiftUI:** `Button` → `AVCaptureSession`/`DataScannerViewController` scanner.

---

## Part E — Abstract bases & capture screens (not question widgets themselves)

These are not directly rendered but must exist to support the cards above:

- **Abstract base classes** (shared behaviour, no UI of their own): `Widget` (interface), `QuestionWidget` (the shell, Part A), `StringWidget` (text base, B.3), `BaseImageWidget` / `MediaWidget` / `FileWidget` (capture/file bases), `BaseSelectListWidget` (recycler-list select base), `MultiChoiceWidget`, `RangeWidget`, `LabelWidget`, `RenderIntoQuestionWidget`. → In SwiftUI these become shared protocols/containers, not screens.
- **Capture screens behind the buttons** — each "launch" button opens a separate full activity you must also re-build natively (these are *not* in the widget layouts): the **map capture** screens (geopoint/geoshape/geotrace/OSM — `geo`/`maps`/`osmdroid`/`google-maps` modules; a large port of their own), the **drawing canvas** (draw/annotate/signature — `draw` module), the **audio recorder** (`audio-recorder` module), the **barcode scanner** (`qr-code` module), the **select-from-map** and **select-image-map** surfaces, and the calendar **date-picker dialogs**. The findings doc (§5) flags geo/maps/draw/qr-code/audio as the highest-risk areas because they are both large and lightly unit-tested.

---

## Coverage of this map

All ~57 `*Widget` classes from `WidgetFactory` are covered: text/number (D.1), date/time (D.2),
geo (D.3), range/rating/counter/trigger (D.4), selection/rank (D.5), media capture (D.6), and the
abstract bases + capture screens (Part E). Cross-reference the per-test behaviours in
`02-collect_app-test-catalog.md` (the `widgets/**` section) — each widget's test there is the
acceptance spec for its SwiftUI re-implementation.
