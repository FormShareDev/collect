# Parity suite authoring spec (READ FIRST)

Shared contract so every pillar binds to Collect's harness identically. Follow it exactly; do not
invent Page methods. Reference implementations already in the repo:
- Test: `collect_app/src/androidTest/java/org/odk/collect/android/feature/parity/ParityRelevanceTest.kt`
- Helper: `collect_app/src/androidTest/java/org/odk/collect/android/feature/parity/ParitySubmission.kt`
- Fixtures: `test-forms/src/main/resources/forms/parity_relevance_basic.xml`,
  `parity_relevance_outside_repeat.xml`

## Locations & naming
- Fixtures: `test-forms/src/main/resources/forms/parity_<pillar>_<variant>.xml`. Form `id` = file
  stem; `<h:title>` = human title (this is what `startBlankForm(title)` uses).
- Tests: `collect_app/src/androidTest/java/org/odk/collect/android/feature/parity/Parity<Pillar>Test.kt`,
  package `org.odk.collect.android.feature.parity`.
- One `@Test` per journey; name it for the branch/behaviour.

## Canonical test skeleton
```kotlin
@RunWith(AndroidJUnit4::class)
class ParityXxxTest {
    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)
    @get:Rule val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    @Test fun journeyName() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_xxx.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Xxx")
            // ---- journey ----
            .answerQuestion("Label", "value")
            .swipeToNextQuestion("Next label")
            // ...
            .swipeToEndScreen()
            .clickFinalize()                       // -> MainMenuPage
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        ParitySubmission.assertExact(xml, "field", "value")
    }
}
```
Imports: `androidx.test.ext.junit.runners.AndroidJUnit4`, `org.junit.{Rule,Test}`,
`org.junit.rules.RuleChain`, `org.junit.runner.RunWith`,
`org.odk.collect.android.support.{TestDependencies}`,
`org.odk.collect.android.support.rules.{CollectTestRule,TestRuleChain}`,
`org.odk.collect.android.feature.parity.ParitySubmission`.

## FormEntryPage methods you may use (exact signatures — do not invent others)
- `answerQuestion(question: String, answer: String)` / `answerQuestion(question, isRequired: Boolean, answer)`
- `swipeToNextQuestion(text: String)` / `swipeToNextQuestion(text, isRequired: Boolean)`
- `swipeToPreviousQuestion(text[, isRequired])`
- `clickOnText(text: String)` (base Page — for select options / buttons; returns the page)
- `assertQuestion(text[, isRequired])` / `assertNoQuestion(text[, isRequired])`
- `assertText(text)` / `assertTextDoesNotExist(text)` (base Page)
- `assertAnswer(question, answer[, readOnly])`
- `swipeToEndScreen(): FormEndPage` / `swipeToEndScreen(instanceName)`
- `clickForwardButton(): FormEntryPage` / `clickBackwardButton()`
- Repeats: `clickPlus(repeatName): AddNewRepeatDialog`;
  `swipeToNextQuestionWithRepeatGroup(repeatName): AddNewRepeatDialog`;
  `swipeToNextRepeat(repeatLabel, repeatNumber)`.
  `AddNewRepeatDialog.clickOnAdd(destination)` / `.clickOnDoNotAdd(destination)` — pass e.g.
  `new FormEntryPage("Title")` or an `EndOfFormPage`/`FormEndPage` as destination.
- Constraint: `swipeToNextQuestionWithConstraintViolation(msg: String)` — stays on the question and
  asserts the message toast/text.
- Selects (minimal/dialog appearance): `openSelectMinimalDialog([index]): SelectMinimalDialogPage`.
- Rank: `clickRankingButton()`. Rating: `setRating(value: Float)`.
- Media/external button: `clickWidgetButton()`.
- Required/finalize errors: `FormEndPage.clickFinalizeWithError(msg)`;
  `FormEntryPage.clickSaveWithError(msg)`.
FormEndPage: `clickFinalize(): MainMenuPage`, `clickSaveAsDraft(): MainMenuPage`,
`clickFinalizeWithError(msg)`, `swipeToPreviousQuestion(text)`, `clickGoToArrow()`.
MainMenuPage: `clickSendFinalizedForm(n): SendFinalizedFormPage` → `.clickSelectAll().clickSendSelected()`.

## Output assertions (ParitySubmission)
- `assertExact(xml, tag, expected)` — deterministic leaf value.
- `assertAbsentOrEmpty(xml, tag)` — relevance-pruned / never-answered node.
- `assertShape(xml, tag, regexPattern)` — non-deterministic (uuid/date/now/deviceid/random).
- `assertRepeatInstanceCount(xml, childTag, n)` — count repeat instances by a child element name.
- `valueOf(xml, tag)` / `occurrences(xml, tag)` for custom checks.
UUID pattern: `uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}`.

## `// LOCK` convention
Any value COMPUTED by the engine (calculate/repeat function/date math) or otherwise not directly
typed by the journey MUST be marked `// LOCK` on its assertion line. These are derived offline and
confirmed/overwritten against Collect's real device output later. Values the journey types directly
(text/int/select) are not LOCK.

## Determinism policy per widget family
- Exact value: text, integer, decimal, url, range, rating, select-one (all appearances),
  select-multiple (value + order), rank, trigger, note/label, date/time/datetime (stored ISO).
- Barcode: value injected via the fake scanner — treat as exact if you drive it; otherwise presence.
- Geo (point/trace/shape): seed known coordinates via `default`/`calculate` in the fixture so the
  stored value + geo-functions are exact; do NOT rely on GPS capture.
- Media (image/selfie/draw/annotate/signature/audio/video/file): NON-deterministic binary. Assert
  the widget appears and (if you provide input) the node is populated — NEVER assert bytes.
- Non-Gregorian calendars: stored value is ISO; assert value round-trip, not picker interaction.

## XForm fixture template
```xml
<?xml version="1.0" encoding="UTF-8"?>
<h:html xmlns="http://www.w3.org/2002/xforms"
        xmlns:h="http://www.w3.org/1999/xhtml"
        xmlns:jr="http://openrosa.org/javarosa"
        xmlns:orx="http://openrosa.org/xforms">
  <h:head>
    <h:title>Parity Xxx</h:title>
    <model>
      <instance>
        <data id="parity_xxx" orx:version="1">
          <field/>
          <meta><instanceID/></meta>
        </data>
      </instance>
      <bind nodeset="/data/field" type="string"/>
      <bind nodeset="/data/meta/instanceID" type="string" jr:preload="uid"/>
    </model>
  </h:head>
  <h:body>
    <input ref="/data/field"><label>Field label</label></input>
  </h:body>
</h:html>
```
Repeats: wrap `<repeat nodeset="/data/rep" [jr:count="/data/n"]>` in a `<group ref="/data/rep"><label>Rep</label>…</group>`;
add `<rep jr:template="">…</rep>` in the instance. Select items inline:
`<item><label>Yes</label><value>yes</value></item>`.

## Hard rules
- DO NOT run gradle, the emulator, or any connected/device test. Authoring is OFFLINE ONLY.
- Before finishing, validate every fixture you wrote is well-formed XML with:
  `python3 -c "import xml.dom.minidom,sys;[xml.dom.minidom.parse(f) for f in sys.argv[1:]]" <files>`
- Report: files created, journeys per form, and every `// LOCK` value you introduced.
