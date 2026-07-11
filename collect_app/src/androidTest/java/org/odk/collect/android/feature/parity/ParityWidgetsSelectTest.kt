package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.feature.parity.ParitySubmission.assertShape
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * Pillar — WIDGETS × APPEARANCES: select-one (5 appearances), select-multiple (2 appearances),
 * rank + trigger + readonly note.
 *
 * Each journey asserts the produced submission leaf values. ODK Collect is the oracle; values the
 * journey does not type directly (seeded/computed/engine-ordered) are marked `// LOCK` and confirmed
 * against Collect's real device output before porting to KotlinCollect.
 */
@RunWith(AndroidJUnit4::class)
class ParityWidgetsSelectTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    // ---- select-one: same 3-option list (a/b/c) across appearances -----------------------------

    @Test
    fun selectOne_acrossAppearances_storesSelectedValues() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_widgets_selectone.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Widgets Select One")
            // default appearance — inline choices, tap by label
            .clickOnText("Banana")
            .swipeToNextQuestion("Select columns")
            // columns appearance — inline choices, tap by label
            .clickOnText("Cherry")
            .swipeToNextQuestion("Select minimal")
            // minimal appearance — opens a dialog; pick the first item (Apple -> a)  // LOCK (dialog interaction)
            .openSelectMinimalDialog()
            .selectItem("Apple")
            .swipeToNextQuestion("Select quick")
            // quick appearance — selecting auto-advances to the next question (Select likert)
            .clickOnText("Banana")
            // likert appearance — inline choices, tap by label
            .clickOnText("Cherry")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "sel_default", "b")
        assertExact(xml, "sel_columns", "c")
        assertExact(xml, "sel_minimal", "a") // LOCK: value chosen via SelectMinimalDialog
        assertExact(xml, "sel_quick", "b")
        assertExact(xml, "sel_likert", "c")
        assertShape(xml, "instanceID", uuid)
    }

    // ---- select-multiple: value + DOCUMENT order -----------------------------------------------

    @Test
    fun selectMultiple_storesInSelectionOrder() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_widgets_selectmulti.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Widgets Select Multi")
            // default appearance — tap Cherry FIRST then Apple; Collect stores in SELECTION order
            .clickOnText("Cherry")
            .clickOnText("Apple")
            .swipeToNextQuestion("Multi columns")
            // columns appearance — tap Cherry then Apple
            .clickOnText("Cherry")
            .clickOnText("Apple")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "multi_default", "c a") // LOCK: space-separated, SELECTION order (Cherry then Apple)
        assertExact(xml, "multi_columns", "c a") // LOCK: space-separated, SELECTION order (Cherry then Apple)
    }

    // ---- rank + trigger + readonly note --------------------------------------------------------

    @Test
    fun rankTriggerNote_storesRankOrderTriggerOkAndNote() {
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_widgets_rank_trigger_note.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Widgets Rank Trigger Note")
            // rank — open dialog and confirm with OK WITHOUT reordering (drag is not scripted here),
            // so the stored order is the document order a b c
            .clickRankingButton()
            .clickOnText("OK")
            .swipeToNextQuestion("Trigger field")
            // trigger/acknowledge — the checkbox label is "OK. Please continue."; tapping it stores "OK"
            .clickOnText("OK. Please continue.")
            .swipeToNextQuestion("Note field")
            .assertText("Read only note")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "rank_field", "a b c") // LOCK: rank order confirmed without reordering (document order)
        assertExact(xml, "trigger_field", "OK") // LOCK: value produced by acknowledging the trigger
        assertExact(xml, "note_field", "Read only note")
    }
}
