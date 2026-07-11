package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.feature.parity.ParitySubmission.assertRepeatInstanceCount
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.pages.FormEntryPage
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * The open-ended-repeat popup navigation from ParityRepeatTest, extended to 4 nested levels.
 * Form shape: R1[ Q1a, R2[ Q2a, R3[ Q3a, R4[ Q4a ], Q3b ], Q2b ], Q1b ], then Final.
 * Every repeat is user-controlled (no jr:count) and each level has a question AFTER its child
 * repeat, so "no" at level N must land on that level's after-question — not the end screen and not
 * a wrong level. Asserts the popup at each level and the yes/no navigation targets.
 */
@RunWith(AndroidJUnit4::class)
class ParityNestedRepeatPopupTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val title = "Parity Repeat Nested Popup"
    private fun page() = FormEntryPage(title)

    private fun open() =
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_repeat_nested_popup.xml", testDependencies.server.hostName)
            .startBlankForm(title)
            .answerQuestion("Intro", "go")

    /** Dive in adding one instance at every level (yes -> deeper question), then decline outward
     *  (no -> the question after each level's repeat). */
    @Test
    fun nested4_yesInward_thenNoOutward_hitsCorrectQuestions() {
        open()
            .swipeToNextQuestionWithRepeatGroup("R1").clickOnAdd(page()).assertQuestion("Q1a").answerQuestion("Q1a", "1a")
            .swipeToNextQuestionWithRepeatGroup("R2").clickOnAdd(page()).assertQuestion("Q2a").answerQuestion("Q2a", "2a")
            .swipeToNextQuestionWithRepeatGroup("R3").clickOnAdd(page()).assertQuestion("Q3a").answerQuestion("Q3a", "3a")
            .swipeToNextQuestionWithRepeatGroup("R4").clickOnAdd(page()).assertQuestion("Q4a").answerQuestion("Q4a", "4a")
            // end of R4 instance -> R4 popup -> NO -> Q3b (after R4, inside R3)
            .swipeToNextQuestionWithRepeatGroup("R4").clickOnDoNotAdd(page()).assertQuestion("Q3b").answerQuestion("Q3b", "3b")
            // end of R3 instance -> R3 popup -> NO -> Q2b (after R3, inside R2)
            .swipeToNextQuestionWithRepeatGroup("R3").clickOnDoNotAdd(page()).assertQuestion("Q2b").answerQuestion("Q2b", "2b")
            // end of R2 instance -> R2 popup -> NO -> Q1b (after R2, inside R1)
            .swipeToNextQuestionWithRepeatGroup("R2").clickOnDoNotAdd(page()).assertQuestion("Q1b").answerQuestion("Q1b", "1b")
            // end of R1 instance -> R1 popup -> NO -> Final (after R1)
            .swipeToNextQuestionWithRepeatGroup("R1").clickOnDoNotAdd(page()).assertQuestion("Final").answerQuestion("Final", "done")
            .swipeToEndScreen().clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "intro", "go")
        assertExact(xml, "q1a", "1a")
        assertExact(xml, "q2a", "2a")
        assertExact(xml, "q3a", "3a")
        assertExact(xml, "q4a", "4a")
        assertExact(xml, "q3b", "3b")
        assertExact(xml, "q2b", "2b")
        assertExact(xml, "q1b", "1b")
        assertExact(xml, "final", "done")
        listOf("q1a", "q2a", "q3a", "q4a").forEach { assertRepeatInstanceCount(xml, it, 1) }
    }

    /** At the deepest level (R4), "yes" must open a NEW R4 instance's first question. */
    @Test
    fun nested4_yesAtDeepestLevel_opensNewInnermostInstance() {
        open()
            .swipeToNextQuestionWithRepeatGroup("R1").clickOnAdd(page()).assertQuestion("Q1a").answerQuestion("Q1a", "1a")
            .swipeToNextQuestionWithRepeatGroup("R2").clickOnAdd(page()).assertQuestion("Q2a").answerQuestion("Q2a", "2a")
            .swipeToNextQuestionWithRepeatGroup("R3").clickOnAdd(page()).assertQuestion("Q3a").answerQuestion("Q3a", "3a")
            .swipeToNextQuestionWithRepeatGroup("R4").clickOnAdd(page()).assertQuestion("Q4a").answerQuestion("Q4a", "4a-1")
            // R4 popup -> YES -> new R4 instance's Q4a
            .swipeToNextQuestionWithRepeatGroup("R4").clickOnAdd(page()).assertQuestion("Q4a").answerQuestion("Q4a", "4a-2")
            // now decline outward
            .swipeToNextQuestionWithRepeatGroup("R4").clickOnDoNotAdd(page()).assertQuestion("Q3b").answerQuestion("Q3b", "3b")
            .swipeToNextQuestionWithRepeatGroup("R3").clickOnDoNotAdd(page()).assertQuestion("Q2b").answerQuestion("Q2b", "2b")
            .swipeToNextQuestionWithRepeatGroup("R2").clickOnDoNotAdd(page()).assertQuestion("Q1b").answerQuestion("Q1b", "1b")
            .swipeToNextQuestionWithRepeatGroup("R1").clickOnDoNotAdd(page()).assertQuestion("Final").answerQuestion("Final", "done")
            .swipeToEndScreen().clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertRepeatInstanceCount(xml, "q4a", 2) // two innermost instances
        assertRepeatInstanceCount(xml, "q3a", 1)
        assertExact(xml, "final", "done")
    }

    /** At the outer level (R1, after declining its inner R2), "yes" must open a NEW R1 instance. */
    @Test
    fun nested4_yesAtOuterLevel_opensNewOuterInstance() {
        open()
            .swipeToNextQuestionWithRepeatGroup("R1").clickOnAdd(page()).assertQuestion("Q1a").answerQuestion("Q1a", "A1a")
            // decline the inner R2 immediately -> Q1b
            .swipeToNextQuestionWithRepeatGroup("R2").clickOnDoNotAdd(page()).assertQuestion("Q1b").answerQuestion("Q1b", "A1b")
            // end of R1 instance 1 -> R1 popup -> YES -> new R1 instance's Q1a
            .swipeToNextQuestionWithRepeatGroup("R1").clickOnAdd(page()).assertQuestion("Q1a").answerQuestion("Q1a", "B1a")
            .swipeToNextQuestionWithRepeatGroup("R2").clickOnDoNotAdd(page()).assertQuestion("Q1b").answerQuestion("Q1b", "B1b")
            // end of R1 instance 2 -> R1 popup -> NO -> Final
            .swipeToNextQuestionWithRepeatGroup("R1").clickOnDoNotAdd(page()).assertQuestion("Final").answerQuestion("Final", "done")
            .swipeToEndScreen().clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertRepeatInstanceCount(xml, "q1a", 2) // two outer instances
        assertRepeatInstanceCount(xml, "q1b", 2)
        assertRepeatInstanceCount(xml, "q2a", 0) // R2 declined in both -> no inner instances
        assertExact(xml, "final", "done")
    }
}
