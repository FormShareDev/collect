package org.odk.collect.android.feature.parity

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.odk.collect.android.feature.parity.ParitySubmission.assertAbsentOrEmpty
import org.odk.collect.android.feature.parity.ParitySubmission.assertExact
import org.odk.collect.android.feature.parity.ParitySubmission.assertRepeatInstanceCount
import org.odk.collect.android.feature.parity.ParitySubmission.assertShape
import org.odk.collect.android.support.TestDependencies
import org.odk.collect.android.support.rules.CollectTestRule
import org.odk.collect.android.support.rules.TestRuleChain

/**
 * Pillar 5 — relevance & branching journeys.
 *
 * Each form branches on user input, so one form yields several journeys. Every journey asserts
 * BOTH the journey (which questions are present/absent — proven here by the fact that the
 * skipped questions never appear between the answered ones and the end screen) AND the produced
 * submission (relevance-pruned nodes are absent/empty; computed repeat functions match).
 *
 * ODK Collect is the oracle. Values marked `// LOCK` are derived offline and confirmed/overwritten
 * on the first Collect device run before this test is ported to KotlinCollect.
 */
@RunWith(AndroidJUnit4::class)
class ParityRelevanceTest {

    private val testDependencies = TestDependencies()
    private val rule = CollectTestRule(useDemoProject = false)

    @get:Rule
    val chain: RuleChain = TestRuleChain.chain(testDependencies).around(rule)

    private val uuid = "uuid:[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"

    // ---- Fixture A: basic + cascading relevance -------------------------------------------------

    @Test
    fun relevanceNo_hidesDetailAndLevel2() {
        openBasic()
            .answerQuestion("Start question", "hi")
            .swipeToNextQuestion("Do you want to add details?")
            .clickOnText("No")
            // detail_text / level2 / level2_text are all non-relevant → next stop is the end screen
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "q_start", "hi")
        assertExact(xml, "has_details", "no")
        assertAbsentOrEmpty(xml, "detail_text")
        assertAbsentOrEmpty(xml, "level2")
        assertAbsentOrEmpty(xml, "level2_text")
        assertShape(xml, "instanceID", uuid)
    }

    @Test
    fun relevanceYes_level2No_showsDetailOnly() {
        openBasic()
            .answerQuestion("Start question", "hi")
            .swipeToNextQuestion("Do you want to add details?")
            .clickOnText("Yes")
            .swipeToNextQuestion("Detail text")
            .answerQuestion("Detail text", "some detail")
            .swipeToNextQuestion("Add second level?")
            .clickOnText("No")
            // level2_text non-relevant → end screen
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "has_details", "yes")
        assertExact(xml, "detail_text", "some detail")
        assertExact(xml, "level2", "no")
        assertAbsentOrEmpty(xml, "level2_text")
    }

    @Test
    fun relevanceYes_level2Yes_showsFullChain() {
        openBasic()
            .answerQuestion("Start question", "hi")
            .swipeToNextQuestion("Do you want to add details?")
            .clickOnText("Yes")
            .swipeToNextQuestion("Detail text")
            .answerQuestion("Detail text", "some detail")
            .swipeToNextQuestion("Add second level?")
            .clickOnText("Yes")
            .swipeToNextQuestion("Second level text")
            .answerQuestion("Second level text", "deep value")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "has_details", "yes")
        assertExact(xml, "detail_text", "some detail")
        assertExact(xml, "level2", "yes")
        assertExact(xml, "level2_text", "deep value")
    }

    // ---- Fixture B: relevance driven by a variable OUTSIDE the repeat ---------------------------

    @Test
    fun noKids_repeatAbsent_countZero() {
        openOutsideRepeat()
            .clickOnText("No")
            // num_kids non-relevant AND the whole kids repeat non-relevant → straight to end
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "has_kids", "no")
        assertAbsentOrEmpty(xml, "num_kids")
        assertRepeatInstanceCount(xml, "child_name", 0)
        assertExact(xml, "summary_count", "0")   // LOCK: count() over zero instances
        assertAbsentOrEmpty(xml, "names_join")   // LOCK: join over empty node-set
    }

    @Test
    fun twoKids_repeatPopulated_countTwo() {
        openOutsideRepeat()
            .clickOnText("Yes")
            .swipeToNextQuestion("How many children?")
            .answerQuestion("How many children?", "2")
            // num_kids drives jr:count → repeat opens with exactly two instances
            .swipeToNextQuestion("Child name")
            .answerQuestion("Child name", "Ana")
            .swipeToNextQuestion("Child age")
            .answerQuestion("Child age", "5")
            .swipeToNextQuestion("Child name")
            .answerQuestion("Child name", "Beto")
            .swipeToNextQuestion("Child age")
            .answerQuestion("Child age", "7")
            .swipeToEndScreen()
            .clickFinalize()
            .clickSendFinalizedForm(1).clickSelectAll().clickSendSelected()

        val xml = ParitySubmission.submissionXml(testDependencies.server.submissions)
        assertExact(xml, "has_kids", "yes")
        assertExact(xml, "num_kids", "2")
        assertRepeatInstanceCount(xml, "child_name", 2)
        assertRepeatInstanceCount(xml, "child_age", 2)
        assertExact(xml, "summary_count", "2")            // LOCK: repeat function count()
        assertExact(xml, "names_join", "Ana, Beto")       // LOCK: join() order + separator
    }

    // ---- openers -------------------------------------------------------------------------------

    private fun openBasic() =
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_relevance_basic.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Relevance Basic")

    private fun openOutsideRepeat() =
        rule.withProject(testDependencies.server.url)
            .copyForm("parity_relevance_outside_repeat.xml", testDependencies.server.hostName)
            .startBlankForm("Parity Relevance Outside Repeat")
}
